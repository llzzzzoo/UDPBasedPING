package com.lrp.UDPBasedPING.web;

import com.lrp.UDPBasedPING.message.*;
import com.lrp.UDPBasedPING.thread.ReadThread;
import com.lrp.UDPBasedPING.thread.SupervisedThread;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Thread.sleep;

public class PingClient extends WebApp implements WebOperation {
    private DatagramChannel channel; // UDP的套接字通道
    private InetSocketAddress serverAddress; // 服务器的Socket地址
    private InetAddress host;
    private LocalResource lr; // 每个端都有自己对应的本地资源
    private final ArrayList<Long> recordTime = new ArrayList<>(); // 记录RTT的数组
    private final int SEND_NUM = 20;

    public static void main(String[] args) {
        if(args.length != 2){
            showErrorAndUsageAndExit("");
        }
        String host = null;
        int port = 0;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            showErrorAndUsageAndExit("PortException: invalid port number");
        }
        try {
            PingClient pingClient = new PingClient(host, port);
            pingClient.execute();
            pingClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PingClient() {
    }

    public PingClient(String host, int port) {
        try {
            this.host = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            showErrorAndUsageAndExit("InvalidHostException: Invalid host name");
        }
        if(port < 1024 || port > 65535){
            showErrorAndUsageAndExit("InvalidPortNumberException: Please " +
                    "enter a number greater than 1023 and less than 65536");
        }
        serverAddress = new InetSocketAddress(this.host, port);
        // 创建lr对象的同时，创建线程
        // 将读线程与当前服务器实例绑定
        this.lr = new LocalResource(new ReadThread(this),
                new MessageBuffer(ByteBuffer.allocate(ECHOMAX), ByteBuffer.allocate(ECHOMAX)));
    }

    @Override
    public LocalResource getLr() {
        return lr;
    }

    @Override
    public ArrayList<Long> getRecordTime() {
        return recordTime;
    }

    /**
     *  总共执行send操作10次，每发送一个报文就计算时间
     *  这里考虑的情况是发送第一个后，可能丢失，所以要依靠序号做判断
     *  发送报文后，在通道那里等待，如果一秒后还没有接收到，就算做丢失
     *  即便接收到了也要检查下序号
     */
    public void execute() throws Exception {
        System.out.println("---- PingClient Start ----");
        // 打开通道
        channel = DatagramChannel.open();
        // 调用静态工厂方法open一个selector
        Selector selector = Selector.open();
        // 只有一个通道一个键
        // 设置线程的lr
        lr.getRt().setLr(lr);
        MessageBuffer mb = lr.getMb();
        ByteBuffer bufferForSend = mb.getBufferForSend();
        ConcurrentLinkedQueue<Message> queueForReceive = lr.getQueueForReceive();
        ConcurrentLinkedQueue<Message> queueForSend = lr.getQueueForSend();
        ReadThread rt = lr.getRt();
        rt.setFLAG_FIELD((short) 0);
        rt.setDaemon(true); // 让这个线程成为守护线程，主线程结束，rt也结束
        // 非阻塞模式
        channel.configureBlocking(false);
        rt.setChannel(channel);
        // 注册到选择器，interest为read，绑定一个自定义对象，可以通过方法获取
        channel.register(selector, SelectionKey.OP_WRITE);
        // start thread
        rt.setRUN_Status(1);
        startReadThread(rt);
        while (true) {
            // 当有收到了发送的十个包的结果后，结束循环
            if(responseNum == SEND_NUM && supervisedThreadNum == 0) break;
            // selector所在的线程查找是否有选择键ready集合变化，并返回变化的键的数目
            // 为0则继续循环
            if (selector.select(TIMEOUT) == 0) {
                // The guy point is a genius
                System.out.print(".");
                continue;
            }
            // 得到一个选择键集合迭代器
            // 其实就一个选择键罢了
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            // 遍历迭代器的每一个选择键元素
            SelectionKey key = null;
            if (it.hasNext()) {
                // 强制转型
                key = it.next();
                it.remove();
            }
            if(key == null){
                showErrorAndUsageAndExit("KeySetException: Code error");
                break;
            }
            if (key.isValid()) {
//                // 将接收消息队列的信息全部复制到发送消息队列去
//                while (!queueForReceive.isEmpty()) {
//                    Message message = queueForReceive.peek();
//                    if(message != null) message.printReceivedMessage(message.getContent());
//                    addToSendQueue(queueForReceive, queueForSend);
//                }

                // 写就绪
                // Client这里就由我手动发消息了
                if (key.isWritable() && responseNum == sentPackage && sentPackage != SEND_NUM) {
                    short sentPackageNum = (short) (sentPackage + 1);
                    // 发送十次到服务器
                    ICMPMessage message = new ICMPMessage(sentPackageNum);
                    // 创建一个监督线程
                    SupervisedThread st = new SupervisedThread(this, queueForReceive, sentPackageNum);
                    st.setDaemon(true); // 守护线程
                    supervisedThreadNum++;
                    message.setDestinationAddress(serverAddress); // 设置服务器地址
                    message.setContent(message.getPacket());
                    queueForSend.add(message);
                    sendToBuffer(mb, bufferForSend, queueForSend);
                    sendDataToSocket(key, mb.getAddressToSend(), bufferForSend, st);
                } else {
                    sleep(50);
                }
            }

        }
        // 打印全部ICMP包的统计信息
        printRTTResult();
        // 标志离开，结束线程
        rt.setRUN_Status(-1);
    }

    private void printRTTResult() {
        if (!recordTime.isEmpty()) {
            long maxRTT = Collections.max(recordTime);
            long minRTT = Collections.min(recordTime);
            String avgRTT = Message.round((double) Message.getSum(recordTime) / (double) receivedPackage);
            int LostPackage = sentPackage - receivedPackage;
            int lossRate = (int) (LostPackage * 100.0 / sentPackage);
            System.out.println("----- Ping Statistics Result -----");
            System.out.println(sentPackage + " packets transmitted, " + receivedPackage + " packets received, " +
                    LostPackage + " packets lost. lossRate: " + lossRate + "%");
            System.out.println("RTT(ms): MIN = " + minRTT + ", AVG = " + avgRTT + ", MAX = " + maxRTT);
        } else {
            System.out.println("----- Ping Statistics Result -----");
            System.out.println(sentPackage + " packets transmitted, " + 0 + " packets received, " +
                    sentPackage + " packets lost. lossRate: " + 100 + "%");
            System.out.println("RTT(ms): MIN = " + 0 + ", AVG = " + 0 + ", MAX = " + 0);
        }
    }

    private void addToSendQueue(ConcurrentLinkedQueue<Message> queueForReceive,
                                ConcurrentLinkedQueue<Message> queueForSend){
        // 克隆一个ICMP数据报，这里是浅克隆，因为lan
        // 并给发送消息队列添加一个Message
        ICMPMessage sendICMP = (ICMPMessage) queueForReceive.poll();
        if(sendICMP == null) return;
        // 修改一下sendICMP的内容再发回去
        // 将收到的客户端数据报的类型字段改为0，表明回复Ping类型的ICMP报文
        byte[] sendContent = sendICMP.getContent();
        sendContent[0] = 0;
        // 设置校验和为0
        sendContent[2] = 0;
        sendContent[3] = 0;
        // 重新改变校验和
        long sum = Message.getCheckSum(sendContent);
        byte[] sumByte = Message.toBytes(sum, 2);
        System.arraycopy(sumByte, 0, sendContent, 2, sumByte.length);
        sendICMP.setContent(sendContent);
        queueForSend.offer(sendICMP);
    }

    /**
     * 发送信息到sendBuffer
     */
    private void sendToBuffer(MessageBuffer mb, ByteBuffer bufferForSend,
                              ConcurrentLinkedQueue<Message>queueForSend){
        // 如果buffer有数据就不发送了，以免覆盖数据或者影响buffer中数据的发送
        if (bufferForSend.position() != 0) return;
        // 让发送消息队列发送到buffer去
        Message head;
        if(!queueForSend.isEmpty()){
            head = queueForSend.poll();
        } else {
            return;
        }
        byte[] data = head.getContent();
        // 发送到buffer同时设置buffer的地址，即目的地址
        mb.setAddressToSend(head.getDestinationAddress());
        bufferForSend.put(data);
    }

    /**
     *  完成从Channel读到buffer的过程
     */
    public SocketAddress readDataFromSocket(SelectionKey key, LocalResource lr)
            throws Exception {
        ByteBuffer bufferForReceive = lr.getMb().getBufferForReceive();
        // 不必判断buffer为空，因为当数据报读到buffer再读到队列后
        // 会自动清空，如果判断buffer为空返回null的话，根本接受不了数据啊
        // 那就判满咯，总要判断一下吧
        if (!bufferForReceive.hasRemaining()) return null;
        // 不在此处清空缓冲区，因为下面的操作要读缓冲区
        // nonblocking，没收到则返回null
        // 如果buffer满了，就会阻塞，直到消息变少
        // 一次只能收到一个。。。吧
        SocketAddress clientAddress = channel.receive(bufferForReceive);
        if (clientAddress != null) {
            // 将地址放入sr的目的地址去
            lr.getMb().setReceivedAddress(clientAddress);
        }
        return clientAddress;
    }

    /**
     *  从buffer读到Channel
     */
    public void sendDataToSocket(SelectionKey key, SocketAddress clientAddress, ByteBuffer bufferForSend,
                                 SupervisedThread st) throws IOException {
        // 将buffer做好写入的准备
        bufferForSend.flip();
        // 只要bufferForSend不为空就可以发送，不像receive还要看channel脸色
        int bytesSent = channel.send(bufferForSend, clientAddress);
        if (bytesSent != 0) {
            sentPackage++;
            // 清空写buffer，每次发送完后都要清空
            bufferForSend.clear();
            // 存在计时器，则开启计时器
            if(st != null) st.start();
        }
    }

    public void close() throws IOException{
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}
package com.lrp.UDPBasedPING.web;

import com.lrp.UDPBasedPING.message.*;
import com.lrp.UDPBasedPING.thread.ReadThread;
import com.lrp.UDPBasedPING.thread.SupervisedThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *  需求：
 *  1.服务器端Server功能
 *  1.1 可以并发地为多个用户服务
 *  1.2 显示用户通过客户端发送来的消息内容(包含头部和payload)；
 *  1.3 模拟分组的丢失；模拟分组传输延迟；
 *  1.4 将用户发送来的请求request在延迟一段随机选择的时间(小于1s)后返回给客户端，作为收到请求的响应reply；
 *  1.5 通过如下命令行启动服务器：java PingServer port。port为PingServer的工作端口号
 */
public class PingServer extends WebApp implements WebOperation {
    private DatagramChannel channel; // UDP的套接字通道
    private InetSocketAddress serverAddress; // 服务器的IP地址
    private int PORT;
    private LocalResource lr; // 每个端都有自己对应的本地资源
    private final double lossRate = 0.5; // 丢失率


    public static void main(String[] args) {
        if(args.length != 1){
            showErrorAndUsageAndExit("");
        }
        int port = 0;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            showErrorAndUsageAndExit("PortException: invalid port number");
        }
        PingServer pingServer;
        try {
            pingServer = new PingServer(port);
            pingServer.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PingServer() {
    }

    public PingServer(int port) {
        if(port < 1024 || port > 65535){
            showErrorAndUsageAndExit("InvalidPortNumberException: Please " +
                    "enter a number greater than 1023 and less than 65536");
        }
        PORT = port;
        // 创造一个socket地址，IP为通配符地址，端口为port
        serverAddress = new InetSocketAddress(PORT);
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
        return null;
    }

    public void execute() throws Exception {
        System.out.println("---- PingServer Start ----");
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
        rt.setFLAG_FIELD((short) 8);
        rt.setDaemon(true); // 让这个线程成为守护线程，主线程结束，rt也结束
        try {
            // 绑定一个SocketAddress
            channel.bind(serverAddress);
        } catch (Exception e){
            showErrorAndUsageAndExit("InvalidPortException: Binding" +
                    " port "+ PORT + " failed, maybe it's been used, please choose another one");
        }
        // 非阻塞模式
        channel.configureBlocking(false);
        rt.setChannel(channel);
        // 注册到选择器，interest为read，绑定一个自定义对象，可以通过方法获取
        channel.register(selector, SelectionKey.OP_WRITE);
        // start thread
        rt.setRUN_Status(1);
        startReadThread(rt);
        while (true) {
            // 让读线程停止可读
            // selector所在的线程查找是否有选择键ready集合变化，并返回变化的键的数目
            // 为0则继续循环
            if (selector.select(TIMEOUT) == 0) {
                // The guy point is a genius
                System.out.print(".");
                continue;
            }

            // 这个键是表明有事件发生的键
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
                // 将接收消息队列的信息全部复制到发送消息队列去
                while (!queueForReceive.isEmpty()){
                    // 打印信息
                    Message message = queueForReceive.peek();
                    System.out.println();
                    message.printReceivedMessage(message.getContent());
                    addToSendQueue(queueForReceive, queueForSend);
                }

                // 写就绪
                // 并且把发送队列的消息全发送出去
                if (key.isWritable() && !queueForSend.isEmpty()) {
                    sentPackage++;
                    sendToBuffer(mb, bufferForSend, queueForSend);
                    sendDataToSocket(key, mb.getAddressToSend(), bufferForSend, null);
                }
            }

        }
        // 标志离开，结束线程
//        rt.setRUN_Status(-1);
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
     * 发送一个数据报到sendBuffer，然后发送到Socket去
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
     *  完成单个数据报从Channel读到buffer的过程
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
        SocketAddress clientAddress = channel.receive(bufferForReceive);
        if (clientAddress != null) {
            // 用于模拟分组丢失，如果大于1000，则不处理接收到的socket
            int randomLoss = (int) (Math.random() * 1000 * (1 + lossRate));
            if (randomLoss > 400) {
//                sleep(100); // 模拟分组延迟
                // 将地址放入sr的目的地址去
                lr.getMb().setReceivedAddress(clientAddress);
            } else {
                bufferForReceive.clear(); // 需要清除缓存
                clientAddress = null;
            }
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
            // 清空写buffer，每次发送完后都要清空
            bufferForSend.clear();
            // 存在计时器，则开启计时器
            if(st != null) st.start();
        }
    }
}
package com.lrp.UDPBasedPING.thread;

import com.lrp.UDPBasedPING.message.ICMPMessage;
import com.lrp.UDPBasedPING.message.LocalResource;
import com.lrp.UDPBasedPING.message.Message;
import com.lrp.UDPBasedPING.message.MessageBuffer;
import com.lrp.UDPBasedPING.web.WebApp;
import com.lrp.UDPBasedPING.web.WebOperation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;

import static com.lrp.UDPBasedPING.web.WebApp.TIMEOUT;
import static com.lrp.UDPBasedPING.web.WebApp.showErrorAndUsageAndExit;

public class ReadThread extends Thread{
    private WebApp wa = null; // 跟服务器或者客户端绑定
    private DatagramChannel channel; // 选择器
    private LocalResource lr;
    private int RUN_Status = 0; // 运行标志位，为0表明此线程需要位于阻塞状态，为1表明线程需要为与运行状态，为-1表明杀死线程
    private short FLAG_FIELD; // 标志字段

    public ReadThread() {
    }

    public ReadThread(WebApp wa) {
        this.wa = wa;
    }

    public void setRUN_Status(int RUN_Status) {
        this.RUN_Status = RUN_Status;
    }

    public void setLr(LocalResource lr) {
        this.lr = lr;
    }

    public void setFLAG_FIELD(short FLAG_FIELD) {
        this.FLAG_FIELD = FLAG_FIELD;
    }

    public void setChannel(DatagramChannel channel) {
        this.channel = channel;
    }

    @Override
    public void run() {
        // 调用静态工厂方法open一个selector
        Selector selector = null;
        try {
            selector = Selector.open();
            // 注册到选择器，interest为read，绑定一个自定义对象，可以通过方法获取
            channel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (selector == null) return;
        while (true) {
            try {
                if (selector.select(TIMEOUT) == 0) {
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

                if(key.isValid()){
                    // 不会一直访问通道，因为只有通道的选择键为可读就绪才能读取到缓存区
                    // 即唤醒才能访问
                    if (key.isReadable() && RUN_Status == 1) {
                        if (wa != null) {
                            // 转型调用子类的方法
                            WebOperation subclass = (WebOperation) wa;
                            try {
                                if (subclass.readDataFromSocket(key, lr) != null) {
                                    // 设置收到的包加1
                                    wa.setReceivedPackage((short) (wa.getReceivedPackage() + 1));
                                    LocalResource lr = subclass.getLr();
                                    writeToReadQueue(lr.getMb(), lr.getMb().getBufferForReceive(),
                                            lr.getQueueForReceive());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                // 标志位标记结束了就不在循环
                if(RUN_Status == -1) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *  将buffer的内容写到队列中
     */
    private void writeToReadQueue(MessageBuffer mb, ByteBuffer bufferForReceive,
                                     ConcurrentLinkedQueue<Message> queueForReceive){
        // 判断buffer是否有数据
        if (bufferForReceive.hasRemaining()) {
            // 将buffer的内容转为Message
            ICMPMessage receivedICMP = createICMP(mb);
            // 读完后清空buffer
            // 每次读取完后都要清空，防止读到重复的
            bufferForReceive.clear();
            if (receivedICMP == null) {
                return;
            }
            // 把这个Message放入接收消息队列
            queueForReceive.offer(receivedICMP);
        }
    }

    private ICMPMessage createICMP(MessageBuffer mb){
        ICMPMessage receivedICMP = new ICMPMessage();
        if(receivedICMP.readFromBuffer(mb) != null){
            receivedICMP.setDestinationAddress(mb.getReceivedAddress());
            // 判断数报格式
            if (!receivedICMP.judgeError(receivedICMP.getContent(), FLAG_FIELD)) return null;
        } else {
            receivedICMP = null; // 这样GC就会把那个对象清楚了，不会浪费空间
        }
        return receivedICMP;
    }
}

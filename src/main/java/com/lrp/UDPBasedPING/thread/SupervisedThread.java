package com.lrp.UDPBasedPING.thread;

import com.lrp.UDPBasedPING.message.ICMPMessage;
import com.lrp.UDPBasedPING.message.Message;
import com.lrp.UDPBasedPING.web.WebApp;
import com.lrp.UDPBasedPING.web.WebOperation;

import java.util.concurrent.ConcurrentLinkedQueue;

public class SupervisedThread extends Thread{
    private final WebApp wa; // 对应的终端
    private ConcurrentLinkedQueue<Message> queueForReceive; // 接收消息队列
    private short seqNumber; // 发送消息的序号
    private static final long timeout = 1000; // 1s限额

    public SupervisedThread() {
        this.wa = null;
    }

    public SupervisedThread(WebApp wa, ConcurrentLinkedQueue<Message> listForReceive, short seqNumber) {
        this.wa = wa;
        this.queueForReceive = listForReceive;
        this.seqNumber = seqNumber;
    }

    /**
     * 监督是否超时，不用在意list的线程安全问题，因为前面我已经设置它为线程安全的了
     */
    @Override
    public void run() {
        // 遍历接收消息队列
        long start_time = System.currentTimeMillis();
        long used_time;
        int notTimeOut = 0; // 为1表明未超时
        // 在一秒内不停遍历队列
        if (wa == null) return;
        while ((used_time = System.currentTimeMillis() - start_time) < timeout) {
            // 啊哈我还以为down casting会花很多时间呢。其实compile的时候就搞定了
            if (queueForReceive.isEmpty()) {
                try {
                    // 休眠20毫秒再迭代，不然感觉也太浪费CPU资源了吧
                    // 这点误差影响不大
                    sleep(timeout / 50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            ICMPMessage im = (ICMPMessage) queueForReceive.poll();
            if(im == null) continue;
            //判断收到的回复的确是本次需要的请求的Ping回复
            if (!isValidResponse(im, seqNumber)) {
                WebApp.showErrorAndUsageAndExit("ServerErrorException: Please wait, the server is" +
                        " undergoing urgent repairs");
                System.exit(1);
            }
            notTimeOut = 1;
            break;
        }
        if (notTimeOut == 1) {
            if(used_time == 0){
                used_time = 1;
            }
            // 打印信息
            printResult(seqNumber, used_time);
            wa.setResponseNum((short) (wa.getResponseNum() + 1));
            // 转型调用子类的方法
            WebOperation subclass = (WebOperation) wa;
            subclass.getRecordTime().add(used_time);
        } else{
            wa.setResponseNum((short) (wa.getResponseNum() + 1));
            // 超时，打印信息
            printTimeOut(seqNumber);
        }
        // 此监督线程结束，端的监督线程数量需要减1
        wa.setSupervisedThreadNum(wa.getSupervisedThreadNum() - 1);
    }

    private boolean isValidResponse(Message message, short seq) {
        byte[] received = message.getContent();
        if (received[0] != 0 || received[1] != 0) {
            return false;
        }
        // 确保这个数据报对应的请求是本程序发出的且是本次请求的回复
        return !(Message.bytes2Num(received, 4, 2) != message.getPid() ||
                Message.bytes2Num(received, 6, 2) != seq);
    }

    public void printResult(int seq, long used_time){
        System.out.println(" PING request " + seq +" RTT(ms): " + used_time);
    }

    public void printTimeOut(int seq){
        System.out.println(" PING request " + seq + " time out.");
    }
}
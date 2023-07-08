package com.lrp.UDPBasedPING.message;

import com.lrp.UDPBasedPING.thread.ReadThread;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LocalResource {
    private ReadThread rt; // 读线程
    private MessageBuffer mb; // 读/写缓冲区
    private final ConcurrentLinkedQueue<Message> queueForReceive = new ConcurrentLinkedQueue<>(); // 接收消息队列，线程安全的
    private final ConcurrentLinkedQueue<Message> queueForSend = new ConcurrentLinkedQueue<>(); // 发送消息队列，线程安全的

    public LocalResource() {
    }

    public LocalResource(ReadThread rt, MessageBuffer mb) {
        this.rt = rt;
        this.mb = mb;
    }

    public ReadThread getRt() {
        return rt;
    }

    public MessageBuffer getMb() {
        return mb;
    }

    public ConcurrentLinkedQueue<Message> getQueueForReceive() {
        return queueForReceive;
    }

    public ConcurrentLinkedQueue<Message> getQueueForSend() {
        return queueForSend;
    }
}

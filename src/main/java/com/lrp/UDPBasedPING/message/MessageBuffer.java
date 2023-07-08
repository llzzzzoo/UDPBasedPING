package com.lrp.UDPBasedPING.message;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class MessageBuffer {
    private final ByteBuffer bufferForReceive; // 仅仅只有一个线程把bufferForReceive读到消息队列，不必担心线程问题
    private final ByteBuffer bufferForSend; // 仅仅只有一个线程把消息队列的信息读到bufferForSend，不必担心线程问题
    private SocketAddress receivedAddress; // 接收到的数据报的地址
    private SocketAddress addressToSend; // 将要发送的地址

    public MessageBuffer() {
        bufferForReceive = null;
        bufferForSend = null;
    }

    public MessageBuffer(ByteBuffer bufferForReceive, ByteBuffer bufferForSend) {
        this.bufferForReceive = bufferForReceive;
        this.bufferForSend = bufferForSend;
    }

    public ByteBuffer getBufferForReceive() {
        return bufferForReceive;
    }

    public ByteBuffer getBufferForSend() {
        return bufferForSend;
    }

    public SocketAddress getReceivedAddress() {
        return receivedAddress;
    }

    public void setReceivedAddress(SocketAddress receivedAddress) {
        this.receivedAddress = receivedAddress;
    }

    public SocketAddress getAddressToSend() {
        return addressToSend;
    }

    public void setAddressToSend(SocketAddress addressToSend) {
        this.addressToSend = addressToSend;
    }
}

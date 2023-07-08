package com.lrp.UDPBasedPING.web;

import com.lrp.UDPBasedPING.message.LocalResource;
import com.lrp.UDPBasedPING.thread.SupervisedThread;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;

public interface WebOperation {
    LocalResource getLr() throws Exception;
    void sendDataToSocket(SelectionKey key, SocketAddress clientAddress,
                     ByteBuffer bufferForSend, SupervisedThread st) throws IOException;
    SocketAddress readDataFromSocket(SelectionKey key, LocalResource Lr) throws Exception;
    ArrayList<Long> getRecordTime();
}

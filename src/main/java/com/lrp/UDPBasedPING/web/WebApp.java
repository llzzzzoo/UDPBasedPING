package com.lrp.UDPBasedPING.web;

import com.lrp.UDPBasedPING.exception.PingException;
import com.lrp.UDPBasedPING.thread.ReadThread;

public abstract class WebApp {
    public static int TIMEOUT = 3000; // 等待超时时间(毫秒)
    public static int ECHOMAX = 1024; // 前1024个字节为缓冲区的最大字节数，最后一个字节用来标记结束，即limit
    public int supervisedThreadNum = 0; // 本线程开启的监督线程的数量
    public short sentPackage = 0; // 发送的包
    public short receivedPackage = 0; // 收到的包，即未超时或丢失的包
    public short responseNum = 0; // 已响应的包，包括超时的

    protected void startReadThread(ReadThread rt){
        rt.setName("ReadThread");
        // 就绪吧线程君
        rt.start();
    }

    public int getSupervisedThreadNum() {
        return supervisedThreadNum;
    }

    public void setSupervisedThreadNum(int supervisedThreadNum) {
        this.supervisedThreadNum = supervisedThreadNum;
    }

    public short getReceivedPackage() {
        return receivedPackage;
    }

    public void setReceivedPackage(short receivedPackage) {
        this.receivedPackage = receivedPackage;
    }

    public short getResponseNum() {
        return responseNum;
    }

    public void setResponseNum(short responseNum) {
        this.responseNum = responseNum;
    }

    /**
     * 当错误信息存在时，打印错误信息，然后打印用法并结束
     * @param errorMessage 错误的信息
     */
    public static void showErrorAndUsageAndExit(String errorMessage){
        // 存在错误信息时就打印
        if(errorMessage != null && errorMessage.length() != 0){
            PingException error = new PingException(errorMessage);
            error.printStackTrace();
        }
        System.out.println("Usage: java PingServer [port]");
        System.exit(1); // 结束
    }
}
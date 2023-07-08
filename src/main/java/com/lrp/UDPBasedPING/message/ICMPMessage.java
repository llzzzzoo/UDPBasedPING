package com.lrp.UDPBasedPING.message;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ICMPMessage extends Message{
    private final String crlf = System.lineSeparator(); // 考虑到不同系统之间的换行符
    private short sqeNumber; // 报文序号

    public ICMPMessage() {
    }

    public ICMPMessage(short sqeNumber) {
        this.sqeNumber = sqeNumber;
    }

    public short getSqeNumber() {
        return sqeNumber;
    }

    /**
     * 从buffer里面读取数据
     * @param buffer 用以接收的buffer
     * @return 返回receivedMessage
     */
    public Message readFromBuffer(MessageBuffer buffer) {
        ByteBuffer bufferForReceive = buffer.getBufferForReceive();
        // 没有数据不能read
        if(bufferForReceive.position() == 0) return null;
        bufferForReceive.flip();
        byte[] data = new byte[bufferForReceive.limit()];
        byte[] wholeBuffer = bufferForReceive.array();
        // 把buffer中的有效信息放到data数组去
        System.arraycopy(wholeBuffer, 0, data, 0, data.length);
        // 设置收到的数据报的内容，这里是地址赋值
        this.content = data;
        return this; // 向上转型
    }

    /**
     *  先看前四个字节，判断数据报的格式以及检验和是否有误
     *  Type：8，Code：0：表示回显请求(ping请求)
     * @param num 当前报文标志字段应该设置的值
     */
    public boolean judgeError(byte[] data, short num){
        if (data[0] == (byte) num && data[1] == (byte) 0) {
            return getCheckSum(data) == 0;
        }
        return false;
    }

    /** 接收的报文格式：类型(8) |  代码(0)  | 校验和
     *              标识符(pid)   |   序号
     *              时间戳前32bit
     *              时间戳后32bit
     *              剩余有效信息字节数 |
     *              "PingUDP"+"CRLF"+填充字节
     * Note: 采用big-ending顺序，即JVM与网络都使用的字节序
     */
    public void printReceivedMessage(byte[] data){
        StringBuilder builder = new StringBuilder();
        builder.append("Header: ").append(crlf);
        builder.append("\tType: 8").append(crlf);
        builder.append("\tCode: 0").append(crlf);
        builder.append("\tCheckSum: ").append(bytes2Num(data, 2, 2)).append("(").
                append(Long.toBinaryString(bytes2Num(data, 2, 2))).append(")").append(crlf);
        //以上为header部分，以下为Payload部分
        builder.append("Payload: ").append(crlf);
        builder.append("\tClient-PID: ").append(bytes2Num(data, 4, 2)).append(crlf);
        builder.append("\tRequest-Seq: ").append(bytes2Num(data, 6, 2)).append(crlf);;
        long time = bytes2Num(data, 8, 8);
        String date = new SimpleDateFormat("yyyy/dd/MM HH:mm:ss").format(new Date(time));
        builder.append("\tTimeStamp: ").append(time).append(" (").append(date).append(")").append(crlf);
        // 计算出除了填充字节还有多少字节是组成剩下的payload的
        int byteNum = (int) bytes2Num(data, 16, 2);
        builder.append("\tOther: ").append(new String(data, 18, byteNum));
        System.out.print(builder);
        System.out.println();
    }

    /**
     * 发送的报文格式：类型(8) |  代码(0)  | 校验和
     *              标识符(pid)   |   序号
     *              时间戳前32bit
     *              时间戳后32bit
     *              剩余有效信息字节数 |
     *              "PingUDP"+"CRLF"+填充字节
     * Note: 采用big-ending顺序，即JVM与网络都使用的字节序
     * @return 组装好的ICMP数据报
     */
    public byte[] getPacket() {
        long timeStamp = System.currentTimeMillis();
        byte[] firstLine = new byte[]{8, 0, 0, 0};
        byte[] seqBs = toBytes(sqeNumber, 2); // 序号字节数组
        byte[] tsBs = toBytes(timeStamp, 8); // 时间戳字节数组
        byte[] pidBs = toBytes(getPid(), 2); // 标识符字节数组
        String tmp = "PingUDP" + System.lineSeparator();
        byte[] tmpbs = tmp.getBytes();
        byte[] validBytes = toBytes(tmpbs.length, 2);
        int toAdd; // 需要填充的字节数
        byte[] other; // "PingUDP"+"CRLF"+填充字节
        if ((toAdd = tmpbs.length % 4) != 0) { //如果不是四的整数倍的字节
            other = new byte[tmpbs.length + toAdd];
            System.arraycopy(tmpbs, 0, other, 0, tmpbs.length);
        } else {
            other = tmpbs;
        }
        byte[] packet = new byte[18 + other.length];
        //将除了校验和的其他字段复制到packet中
        copyArray(packet, firstLine, pidBs, seqBs, tsBs, validBytes, other);
        //计算校验和
        long checkSum = getCheckSum(packet);
        //填充packet的checksum字段
        byte[] cs = toBytes(checkSum, 2);
        System.arraycopy(cs, 0, packet, 2, cs.length);
        return packet;
    }
}
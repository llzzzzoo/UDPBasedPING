package com.lrp.UDPBasedPING.message;

import java.lang.management.ManagementFactory;
import java.net.SocketAddress;
import java.text.DecimalFormat;
import java.util.List;

public abstract class Message {
    protected SocketAddress destinationAddress; // 本类可以继承
    protected byte[] content; // 数据报中的内容

    public SocketAddress getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(SocketAddress destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    /**
     * 将byte数组的一部分byte转换成long
     * @param b 数组
     * @param begin 开始字节的索引
     * @param len 转换的字节数
     * @return 得到的转换值
     */
    public static long bytes2Num(byte[] b, int begin, int len) {
        int left=len;
        long sum = 0;
        for (int i = begin; i < begin + len; i++) {
            long n = ((int) b[i]) & 0xff;
            n <<= (--left) * 8;
            sum += n;
        }
        return sum;
    }

    /**
     *
     * @return 当前JVM的进程id
     */
    public short getPid() {
        // 获取代表当前JVM的字符串
        String name = ManagementFactory.getRuntimeMXBean().getName();
        // 获得pid
        String pid = name.split("@")[0];
        try {
            return Short.parseShort(pid);
        } catch (NumberFormatException e){
            // JVM的id太大了就返回short类型的极限值咯
            return 32767;
        }
    }

    /**
     * 由byte数组得到其校验和
     * @param buf 数据
     * @return 返回校验和，在long型返回值的低16位
     */
    public static long getCheckSum(byte[] buf) {
        int i = 0;
        int length = buf.length;
        long sum = 0; // checksum只占sum的低16位
        // 由于sum为long型，下面的加法运算都不会导致符号位改变，等价于无符号加法
        while (length > 0) {
            sum += (buf[i++] & 0xff) << 8; // 与checksum的高8位相加
            if ((--length) == 0) break; // 如果buf的byte个数不为偶数
            sum += (buf[i++] & 0xff); // 与checksum的低8位相加
            --length;
        }
        // 处理溢出，将sum的从右往左第二个16位和其第一个16位(最右的16位)相加并取反
        return (~((sum & 0xFFFF) + (sum >> 16))) & 0xFFFF;
    }

    /**
     * 将多个byte[]的所有数据复制到target中
     * @param srcs 来源数组
     * @param target 目标
     */
    public static void copyArray(byte[] target,byte[] ... srcs) {
        int hasCopied=0;
        for (byte[] src : srcs) {
            System.arraycopy(src, 0, target, hasCopied, src.length);
            hasCopied += src.length;
        }
    }

    /**
     * 取value的后几个字节放入byte[]中
     * @param value 值
     * @param len 指定value的后len个字节
     * @return 返回byte数组
     */
    public static byte[] toBytes(long value, int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            b[len - i - 1] = (byte) (value >> 8 * i);
        }
        return b;
    }

    /**
     * @param x 参数
     * @return 将参数x保留小数点后两位
     */
    public static String round(double x) {
        DecimalFormat d = new DecimalFormat("#.00");
        return d.format(x);
    }

    /**
     * 计算指定集合的元素之和，元素为long类型
     * @param list 指定集合
     * @return 将参数x保留小数点后两位
     */
    public static long getSum(List<Long> list) {
        return list.stream().reduce(Long::sum).orElse(0L);
    }

    public void printReceivedMessage(byte[] data){
        System.out.println("打印出错！");
    }
}

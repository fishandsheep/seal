package org.fisheep.util;

import java.util.Arrays;

/**
 * 字节数组处理工具类
 */
public class ByteArrayUtils {
    
    // MySQL协议标识
    public static final byte[] LOWER_SELECT_FLAG = {3, 0, 1, 83, 69, 76, 69, 67, 84};
    public static final byte[] UPPER_SELECT_FLAG = {3, 0, 1, 115, 101, 108, 101, 99, 116};
    public static final byte[] LONG_SQL_PRE_FLAG = {0x01, 0x01, 0x08, 0x0a};
    
    /**
     * 检查字节数组是否以指定前缀开头
     * @param data 字节数组
     * @param prefix 前缀
     * @param offset 偏移量
     * @return 是否匹配
     */
    public static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (data == null || prefix == null || data.length < offset + prefix.length) {
            return false;
        }
        
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 查找字节数组中第一次出现指定模式的位置
     * @param data 字节数组
     * @param pattern 模式
     * @param fromIndex 起始位置
     * @return 匹配位置，未找到返回-1
     */
    public static int indexOf(byte[] data, byte[] pattern, int fromIndex) {
        if (data == null || pattern == null || data.length < pattern.length) {
            return -1;
        }
        
        for (int i = fromIndex; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 截取字节数组
     * @param data 原数组
     * @param start 起始位置
     * @param end 结束位置
     * @return 截取后的数组
     */
    public static byte[] subarray(byte[] data, int start, int end) {
        if (data == null) {
            return null;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > data.length) {
            end = data.length;
        }
        if (start > end) {
            return new byte[0];
        }
        
        return Arrays.copyOfRange(data, start, end);
    }
    
    /**
     * 连接两个字节数组
     * @param first 第一个数组
     * @param second 第二个数组
     * @return 连接后的数组
     */
    public static byte[] concat(byte[] first, byte[] second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
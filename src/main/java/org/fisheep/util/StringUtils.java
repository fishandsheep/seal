package org.fisheep.util;

import java.util.regex.Pattern;

/**
 * 字符串处理工具类
 */
public class StringUtils {
    
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("(\\r?\\n)+");
    
    /**
     * 将多行字符串转换为单行
     * @param str 输入字符串
     * @return 单行字符串
     */
    public static String singleLine(String str) {
        if (str == null) {
            return "";
        }
        return LINE_BREAK_PATTERN.matcher(str).replaceAll(" ").replaceAll("`", "");
    }
    
    /**
     * 检查字符串是否为空或null
     * @param str 输入字符串
     * @return 是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 检查字符串是否不为空
     * @param str 输入字符串
     * @return 是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
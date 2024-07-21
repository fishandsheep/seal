package org.fisheep.util;


public class ByteArrayUtil {

    /**
     * 数组中元素未找到的下标，值为-1
     */
    public static final int INDEX_NOT_FOUND = -1;

    public static int indexOfSub(byte[] array, byte[] subArray) {
        return indexOfSub(array, 0, subArray);
    }


    public static int indexOfSub(byte[] array, int beginInclude, byte[] subArray) {
        if (isEmpty(array) || isEmpty(subArray) || subArray.length > array.length) {
            return INDEX_NOT_FOUND;
        }
        int firstIndex = indexOf(array, subArray[0], beginInclude);
        if (firstIndex < 0 || firstIndex + subArray.length > array.length) {
            return INDEX_NOT_FOUND;
        }

        for (int i = 0; i < subArray.length; i++) {
            if (false == (array[i + firstIndex] == subArray[i])) {
                return indexOfSub(array, firstIndex + 1, subArray);
            }
        }

        return firstIndex;
    }

    public static int indexOf(byte[] array, byte value, int beginIndexInclude) {
        return matchIndex((obj) -> value == obj, beginIndexInclude, array);
    }

    public static int matchIndex(Matcher<Byte> matcher, int beginIndexInclude, byte[] array) {
        for (int i = beginIndexInclude; i < array.length; i++) {
            if (matcher.match(array[i])) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

}

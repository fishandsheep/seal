package org.fisheep.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Struct {

    /**
     * 规则代码
     */
    private String Item;

    /**
     * 危险等级L[0-8]
     */
    private String Severity;

    /**
     * 规则摘要
     */
    private String Summary;

    /**
     * 规则解释
     */
    private String Content;

    /**
     * SQL示例
     */
    private String Case;

    /**
     * 建议所处SQL字符位置，默认0表示全局建议
     */
    private String Position;


}

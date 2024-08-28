package org.fisheep.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Struct {

    /**
     * 规则代码
     */
    @JsonProperty("Item")
    private String item;

    /**
     * 危险等级L[0-8]
     */
    @JsonProperty("Severity")
    private String severity;

    /**
     * 规则摘要
     */
    @JsonProperty("Summary")
    private String summary;

    /**
     * 规则解释
     */
    @JsonProperty("Content")
    private String content;

    /**
     * SQL示例
     */
    @JsonProperty("Case")
    private String example;

    /**
     * 建议所处SQL字符位置，默认0表示全局建议
     */
    @JsonProperty("Position")
    private String position;


}

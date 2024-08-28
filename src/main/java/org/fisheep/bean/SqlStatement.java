package org.fisheep.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BigOrange
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlStatement {

    private String content;

    private String score;

    private long count;

    /**
     * tcp层面的sql耗时，统计一次完整的sql请求（响应的第一个tcp包 - 请求的最后一个tcp包） 的耗时
     * 若耗时小于 1ms,结果为 0 最大耗时
     */
    private long maxTakeTime;

    private String explain;

    private String errorMessage;

}

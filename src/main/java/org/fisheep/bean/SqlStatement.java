package org.fisheep.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BigOrange
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlStatement {

    private String content;

    /**
     * 是否是全表查询 0-否，1-是，2-解析失败
     */
    private int isFullTableSearch;

    /**
     * 是否是全表扫描 0-否，1-是，2-解析失败
     */
    private int isFullTableScan;

    private String tableScanInfo;

    private int risk;

    private long count;

    /**
     * tcp层面的sql耗时，统计一次完整的sql请求（响应的第一个tcp包 - 请求的最后一个tcp包） 的耗时
     * 若耗时小于 1ms,结果为 0 最大耗时
     * TODO 不准确，获取准确的sql耗时，需通过数据库日志统计
     */
    private long maxTakeTime;

    private Struct explainPlan;

    private List<Struct> explainRisk;

}

package org.fisheep.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL语句类
 * 用于存储SQL语句及其分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlStatement {

    /**
     * SQL语句内容
     */
    private String sql;

    /**
     * SQL语句内容（兼容字段名）
     */
    private String content;

    /**
     * 风险评分
     */
    private double score;

    /**
     * 风险评分（兼容字段名）
     */
    private String scoreStr;

    /**
     * 执行次数
     */
    private long count;

    /**
     * 最大执行时间
     */
    private long maxTime;

    /**
     * tcp层面的sql耗时，统计一次完整的sql请求（响应的第一个tcp包 - 请求的最后一个tcp包） 的耗时
     * 若耗时小于 1ms,结果为 0 最大耗时
     */
    private long maxTakeTime;

    /**
     * 执行时间
     */
    private long executeTime;

    /**
     * 分析结果说明
     */
    private String explain;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 获取SQL语句内容（优先使用sql字段）
     */
    public String getSql() {
        return sql != null ? sql : content;
    }

    /**
     * 设置SQL语句内容
     */
    public void setSql(String sql) {
        this.sql = sql;
        this.content = sql;
    }

    /**
     * 获取风险评分（返回double类型）
     */
    public double getScore() {
        if (scoreStr != null) {
            try {
                return Double.parseDouble(scoreStr);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return score;
    }

    /**
     * 设置风险评分
     */
    public void setScore(double score) {
        this.score = score;
        this.scoreStr = String.valueOf(score);
    }

    /**
     * 获取最大执行时间（优先使用maxTime字段）
     */
    public long getMaxTime() {
        return maxTime > 0 ? maxTime : maxTakeTime;
    }

    /**
     * 设置最大执行时间
     */
    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
        this.maxTakeTime = maxTime;
    }

}

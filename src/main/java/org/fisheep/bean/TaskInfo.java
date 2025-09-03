package org.fisheep.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务信息类
 * 用于跟踪SQL分析任务的状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskInfo {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务状态：pending, processing, completed, failed
     */
    private String status;
    
    /**
     * 创建时间
     */
    private long createTime;
    
    /**
     * 完成时间
     */
    private long completeTime;
    
    /**
     * SQL语句数量
     */
    private int sqlCount;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 数据库ID
     */
    private int id;
    
    /**
     * 任务是否完成
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }
    
    /**
     * 任务是否失败
     */
    public boolean isFailed() {
        return "failed".equals(status);
    }
    
    /**
     * 任务是否正在处理
     */
    public boolean isProcessing() {
        return "processing".equals(status);
    }
    
    /**
     * 任务是否等待处理
     */
    public boolean isPending() {
        return "pending".equals(status);
    }
}
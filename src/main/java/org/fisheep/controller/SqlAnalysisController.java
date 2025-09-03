package org.fisheep.controller;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import lombok.extern.slf4j.Slf4j;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.manager.SqlAnalysisManager;
import java.util.Map;

/**
 * SQL分析控制器
 * 处理SQL分析相关的HTTP请求
 */
@Slf4j
public class SqlAnalysisController {
    
    private final SqlAnalysisManager sqlAnalysisManager;
    
    public SqlAnalysisController() {
        this.sqlAnalysisManager = new SqlAnalysisManager();
    }
    
    /**
     * 上传PCAP文件
     */
    public void uploadPcapFile(Context ctx) {
        try {
            sqlAnalysisManager.uploadPcapFile(ctx);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 获取任务状态
     */
    public void getTaskStatus(Context ctx) {
        try {
            String taskId = ctx.pathParam("taskId");
            Result result = sqlAnalysisManager.getTaskStatus(taskId);
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 获取分析结果（分页）
     */
    public void getAnalysisResults(Context ctx) {
        try {
            String taskId = ctx.formParam("taskId");
            int page = Integer.parseInt(ctx.formParam("page") != null ? ctx.formParam("page") : "1");
            int size = Integer.parseInt(ctx.formParam("size") != null ? ctx.formParam("size") : "10");
            
            Result result = sqlAnalysisManager.getAnalysisResults(taskId, page, size);
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 获取数据库和时间戳信息
     */
    public void getDbAndTimestamp(Context ctx) {
        try {
            Result result = sqlAnalysisManager.getDbAndTimestamp();
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 导出分析结果
     */
    public void exportResults(Context ctx) {
        try {
            String taskId = ctx.formParam("taskId");
            String format = ctx.formParam("format") != null ? ctx.formParam("format") : "json";
            
            Result result = sqlAnalysisManager.exportResults(taskId, format);
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 发送任务状态（SSE）
     */
    public void sendTaskStatus(SseClient sseClient) {
        try {
            String taskId = sseClient.ctx().pathParam("taskId");
            sseClient.sendEvent("status", "Connected");
            // 定期发送任务状态
            while (true) {
                try {
                    Result result = sqlAnalysisManager.getTaskStatus(taskId);
                    sseClient.sendEvent("status", result.getResult() != null ? result.getResult().toString() : "{}");
                    Thread.sleep(1000); // 每秒发送一次
                    
                    // 如果任务完成，关闭连接
                    if (result.getResult() != null) {
                        String statusStr = result.getResult().toString();
                        if (statusStr.contains("completed") || statusStr.contains("failed")) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error sending SSE status", e);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error in SSE connection", e);
        }
    }
    
    /**
     * 统一异常处理
     */
    private void handleException(Context ctx, Exception e) {
        if (e instanceof SealException) {
            SealException se = (SealException) e;
            ctx.json(new Result(se.getCode(), se.getMsg()));
        } else {
            log.error("Unexpected error in SqlAnalysisController", e);
            ctx.json(new Result(500, "Internal server error"));
        }
    }
}
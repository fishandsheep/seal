package org.fisheep.manager;

import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.fisheep.bean.SqlStatement;
import org.fisheep.bean.TaskInfo;
import org.fisheep.bean.data.ExplainResults;
import org.fisheep.bean.data.Statuses;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.config.AppConfig;
import org.fisheep.util.PcapParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQL分析管理器
 * 负责PCAP文件上传、解析和SQL风险分析
 */
@Slf4j
public class SqlAnalysisManager extends AbstractManager<TaskInfo> {
    
    private final AppConfig config;
    private final ExecutorService executorService;
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();
    
    public SqlAnalysisManager() {
        this.config = AppConfig.getInstance();
        this.executorService = Executors.newFixedThreadPool(4);
    }
    
    /**
     * 上传PCAP文件进行分析
     */
    public void uploadPcapFile(Context ctx) throws SealException {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "No file uploaded");
        }
        
        String taskId = generateTaskId();
        String dstPortStr = ctx.formParam("dstPort");
        int dstPort = dstPortStr != null ? Integer.parseInt(dstPortStr) : 3306;
        
        // 创建任务
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskId(taskId);
        taskInfo.setStatus("pending");
        taskInfo.setCreateTime(System.currentTimeMillis());
        taskMap.put(taskId, taskInfo);
        
        // 异步处理文件
        executorService.submit(() -> {
            try {
                processPcapFile(taskInfo, file, dstPort);
            } catch (Exception e) {
                log.error("Failed to process PCAP file", e);
                taskInfo.setStatus("failed");
                taskInfo.setError(e.getMessage());
            }
        });
        
        ctx.json(new Result(taskId));
    }
    
    /**
     * 处理PCAP文件
     */
    private void processPcapFile(TaskInfo taskInfo, UploadedFile file, int dstPort) throws IOException {
        taskInfo.setStatus("processing");
        
        try {
            // 1. 解析PCAP文件
            byte[] fileData = file.content().readAllBytes();
            List<SqlStatement> sqlStatements = PcapParser.parsePcapFile(fileData, dstPort);
            
            if (sqlStatements.isEmpty()) {
                taskInfo.setStatus("completed");
                taskInfo.setMessage("No SQL statements found");
                return;
            }
            
            // 2. 分析SQL语句
            analyzeSqlStatements(taskInfo, sqlStatements);
            
            taskInfo.setStatus("completed");
            taskInfo.setSqlCount(sqlStatements.size());
            
        } catch (Exception e) {
            taskInfo.setStatus("failed");
            taskInfo.setError(e.getMessage());
            throw e;
        }
    }
    
    /**
     * 分析SQL语句
     */
    private void analyzeSqlStatements(TaskInfo taskInfo, List<SqlStatement> sqlStatements) {
        String soarPath = config.getSoarPath();
        
        for (SqlStatement sqlStatement : sqlStatements) {
            try {
                // 使用SOAR工具分析SQL
                String analysisResult = analyzeSqlWithSoar(soarPath, sqlStatement.getSql());
                
                // 解析分析结果
                double score = parseSqlScore(analysisResult);
                sqlStatement.setScore(score);
                
                // 保存分析结果
                saveAnalysisResult(taskInfo.getTaskId(), sqlStatement, analysisResult);
                
            } catch (Exception e) {
                log.warn("Failed to analyze SQL: {}", sqlStatement.getSql(), e);
                sqlStatement.setScore(0.0); // 分析失败时设置默认分数
            }
        }
    }
    
    /**
     * 使用SOAR工具分析SQL
     */
    private String analyzeSqlWithSoar(String soarPath, String sql) throws IOException {
        CommandLine commandLine = new CommandLine(soarPath);
        commandLine.addArgument("-query");
        commandLine.addArgument(sql);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(outputStream));
        
        int exitCode = executor.execute(commandLine);
        if (exitCode != 0) {
            throw new IOException("SOAR analysis failed with exit code: " + exitCode);
        }
        
        return outputStream.toString(StandardCharsets.UTF_8);
    }
    
    /**
     * 解析SQL分数
     */
    private double parseSqlScore(String analysisResult) {
        try {
            // 这里需要根据SOAR的实际输出格式来解析分数
            // 假设输出格式为：{"score": 85.5, ...}
            String[] lines = analysisResult.split("\n");
            for (String line : lines) {
                if (line.contains("score")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        return Double.parseDouble(parts[1].trim().replace(",", "").replace("}", ""));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SQL score from: {}", analysisResult, e);
        }
        return 0.0;
    }
    
    /**
     * 保存分析结果
     */
    private void saveAnalysisResult(String taskId, SqlStatement sqlStatement, String analysisResult) {
        ExplainResults explainResults = getStorage().explainResults();
        explainResults.add(taskId, sqlStatement, analysisResult);
    }
    
    /**
     * 获取任务状态
     */
    public Result getTaskStatus(String taskId) {
        TaskInfo taskInfo = taskMap.get(taskId);
        if (taskInfo == null) {
            throw new SealException(ErrorEnum.TASK_NOT_FOUND);
        }
        
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("status", taskInfo.getStatus());
        status.put("message", taskInfo.getMessage());
        status.put("error", taskInfo.getError());
        status.put("sqlCount", taskInfo.getSqlCount());
        status.put("createTime", taskInfo.getCreateTime());
        
        return new Result(status);
    }
    
    /**
     * 获取分析结果（分页）
     */
    public Result getAnalysisResults(String taskId, int page, int size) {
        TaskInfo taskInfo = taskMap.get(taskId);
        if (taskInfo == null) {
            throw new SealException(ErrorEnum.TASK_NOT_FOUND);
        }
        
        if (!"completed".equals(taskInfo.getStatus())) {
            throw new SealException(ErrorEnum.TASK_NOT_EXIST, "Task is not completed yet");
        }
        
        ExplainResults explainResults = getStorage().explainResults();
        List<SqlStatement> statements = explainResults.getByTaskId(taskId);
        
        // 分页处理
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, statements.size());
        
        if (fromIndex >= statements.size()) {
            return new Result(new org.fisheep.common.PageResult(List.of(), page, size));
        }
        
        List<SqlStatement> pageData = statements.subList(fromIndex, toIndex);
        
        return new Result(new org.fisheep.common.PageResult(statements, page, size));
    }
    
    /**
     * 获取数据库和时间戳信息
     */
    public Result getDbAndTimestamp() {
        Statuses statuses = getStorage().statuses();
        Map<String, List<String>> result = statuses.dbAndTimestamp();
        
        if (result.isEmpty()) {
            throw new SealException(ErrorEnum.TASK_NOT_EXIST);
        }
        
        return new Result(result);
    }
    
    /**
     * 导出分析结果
     */
    public Result exportResults(String taskId, String format) {
        TaskInfo taskInfo = taskMap.get(taskId);
        if (taskInfo == null) {
            throw new SealException(ErrorEnum.TASK_NOT_FOUND);
        }
        
        if (!"completed".equals(taskInfo.getStatus())) {
            throw new SealException(ErrorEnum.TASK_NOT_EXIST, "Task is not completed yet");
        }
        
        ExplainResults explainResults = getStorage().explainResults();
        List<SqlStatement> statements = explainResults.getByTaskId(taskId);
        
        // 根据格式导出
        String exportData;
        switch (format.toLowerCase()) {
            case "json":
                exportData = exportToJson(statements);
                break;
            case "csv":
                exportData = exportToCsv(statements);
                break;
            default:
                throw new SealException(ErrorEnum.INVALID_PARAMETER, "Unsupported export format: " + format);
        }
        
        return new Result(exportData);
    }
    
    /**
     * 导出为JSON格式
     */
    private String exportToJson(List<SqlStatement> statements) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < statements.size(); i++) {
            if (i > 0) json.append(",");
            json.append(String.format(
                "{\"sql\":\"%s\",\"score\":%.2f,\"count\":%d,\"maxTime\":%d}",
                escapeJson(statements.get(i).getSql()),
                statements.get(i).getScore(),
                statements.get(i).getCount(),
                statements.get(i).getMaxTime()
            ));
        }
        json.append("]");
        return json.toString();
    }
    
    /**
     * 导出为CSV格式
     */
    private String exportToCsv(List<SqlStatement> statements) {
        StringBuilder csv = new StringBuilder("SQL,Score,Count,MaxTime\n");
        for (SqlStatement statement : statements) {
            csv.append(String.format("\"%s\",%.2f,%d,%d\n",
                escapeCsv(statement.getSql()),
                statement.getScore(),
                statement.getCount(),
                statement.getMaxTime()
            ));
        }
        return csv.toString();
    }
    
    /**
     * 转义JSON字符串
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 转义CSV字符串
     */
    private String escapeCsv(String text) {
        return text.replace("\"", "\"\"");
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    /**
     * 清理已完成的任务
     */
    public void cleanupCompletedTasks() {
        long cleanupTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24小时前
        taskMap.entrySet().removeIf(entry -> {
            TaskInfo task = entry.getValue();
            return "completed".equals(task.getStatus()) && task.getCreateTime() < cleanupTime;
        });
    }
    
    // 以下是AbstractManager的抽象方法实现
    @Override
    protected TaskInfo doAdd(TaskInfo entity) {
        taskMap.put(entity.getTaskId(), entity);
        return entity;
    }
    
    @Override
    protected boolean doDelete(int id) {
        // 对于任务，我们使用taskId作为key
        return taskMap.values().removeIf(task -> task.getId() == id);
    }
    
    @Override
    protected List<TaskInfo> doGetAll() {
        return List.copyOf(taskMap.values());
    }
    
    @Override
    protected TaskInfo doGetById(int id) {
        return taskMap.values().stream()
                .filter(task -> task.getId() == id)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    protected TaskInfo doUpdate(TaskInfo entity) {
        taskMap.put(entity.getTaskId(), entity);
        return entity;
    }
    
    @Override
    protected void validateEntity(TaskInfo entity) throws SealException {
        if (entity == null) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Task info cannot be null");
        }
        if (entity.getTaskId() == null || entity.getTaskId().trim().isEmpty()) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Task ID cannot be empty");
        }
    }
    
    @Override
    protected boolean exists(TaskInfo entity) {
        return taskMap.containsKey(entity.getTaskId());
    }
}
package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.SqlStatement;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析结果数据类
 * 用于存储SQL分析结果
 */
public class ExplainResults extends ReadWriteLocked {

    private final Map<String, List<SqlStatement>> explainResults = new HashMap<>();
    private final Map<String, String> analysisDetails = new HashMap<>();

    /**
     * 添加分析结果
     * @param taskId 任务ID
     * @param sqlStatement SQL语句
     * @param analysisDetail 分析详情
     */
    public void add(String taskId, SqlStatement sqlStatement, String analysisDetail) {
        this.add(taskId, sqlStatement, analysisDetail, StorageManagerFactory.getInstance());
    }

    /**
     * 根据任务ID获取分析结果
     * @param taskId 任务ID
     * @return SQL语句列表
     */
    public List<SqlStatement> getByTaskId(String taskId) {
        return this.read(() -> this.explainResults.getOrDefault(taskId, new ArrayList<>()));
    }

    /**
     * 获取分析详情
     * @param taskId 任务ID
     * @return 分析详情
     */
    public String getAnalysisDetail(String taskId) {
        return this.read(() -> this.analysisDetails.get(taskId));
    }

    /**
     * 删除任务的分析结果
     * @param taskId 任务ID
     */
    public void delete(String taskId) {
        this.delete(taskId, StorageManagerFactory.getInstance());
    }

    /**
     * 获取所有任务ID
     * @return 任务ID列表
     */
    public List<String> getAllTaskIds() {
        return this.read(() -> new ArrayList<>(this.explainResults.keySet()));
    }

    /**
     * 检查任务是否存在
     * @param taskId 任务ID
     * @return 是否存在
     */
    public boolean containsTask(String taskId) {
        return this.read(() -> this.explainResults.containsKey(taskId));
    }

    /**
     * 获取任务数量
     * @return 任务数量
     */
    public int getTaskCount() {
        return this.read(() -> this.explainResults.size());
    }

    /**
     * 清空所有分析结果
     */
    public void clear() {
        this.clear(StorageManagerFactory.getInstance());
    }

    private void add(String taskId, SqlStatement sqlStatement, String analysisDetail, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.explainResults.computeIfAbsent(taskId, k -> new ArrayList<>()).add(sqlStatement);
            this.analysisDetails.put(taskId, analysisDetail);
            persistenceStoring.store(this.explainResults);
            persistenceStoring.store(this.analysisDetails);
        });
    }

    private void delete(String taskId, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.explainResults.remove(taskId);
            this.analysisDetails.remove(taskId);
            persistenceStoring.store(this.explainResults);
            persistenceStoring.store(this.analysisDetails);
        });
    }

    private void clear(PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.explainResults.clear();
            this.analysisDetails.clear();
            persistenceStoring.store(this.explainResults);
            persistenceStoring.store(this.analysisDetails);
        });
    }
}
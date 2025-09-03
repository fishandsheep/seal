package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.Status;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Statuses extends ReadWriteLocked {

    private final HashMap<String, Status> statuses = new HashMap<>();

    public void put(String explainId, Status status) {
        this.put(explainId, status, StorageManagerFactory.getInstance());
    }

    public void delete(String explainId) {
        this.delete(explainId, StorageManagerFactory.getInstance());
    }

    public Status one(String explainId) {
        return this.read(() -> this.statuses.get(explainId));
    }

    /**
     * 获取数据库和时间戳信息
     * @return 数据库ID到时间戳列表的映射
     */
    public Map<String, List<String>> dbAndTimestamp() {
        return this.read(() -> {
            return statuses.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> List.of(String.valueOf(entry.getValue().getProcessTime()))
                ));
        });
    }

    /**
     * 获取所有状态
     * @return 所有状态映射
     */
    public Map<String, Status> getAll() {
        return this.read(() -> new HashMap<>(statuses));
    }

    /**
     * 获取状态数量
     * @return 状态数量
     */
    public int size() {
        return this.read(() -> statuses.size());
    }

    /**
     * 检查是否包含指定任务ID
     * @param explainId 任务ID
     * @return 是否包含
     */
    public boolean containsKey(String explainId) {
        return this.read(() -> statuses.containsKey(explainId));
    }

    private void put(String explainId, Status status, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.statuses.put(explainId, status);
            persistenceStoring.store(this.statuses);
        });
    }

    private void delete(String explainId, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.statuses.remove(explainId);
            persistenceStoring.store(this.statuses);
        });
    }
}

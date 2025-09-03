package org.fisheep.manager;

/**
 * Manager接口
 * 定义管理器的基本操作
 */
public interface Manager<T> {
    
    /**
     * 添加实体
     * @param entity 实体对象
     * @return 添加后的实体
     */
    T add(T entity);
    
    /**
     * 根据ID删除实体
     * @param id 实体ID
     * @return 是否删除成功
     */
    boolean delete(int id);
    
    /**
     * 获取所有实体
     * @return 所有实体列表
     */
    java.util.List<T> getAll();
    
    /**
     * 根据ID获取实体
     * @param id 实体ID
     * @return 实体对象，如果不存在返回null
     */
    T getById(int id);
    
    /**
     * 更新实体
     * @param entity 实体对象
     * @return 更新后的实体
     */
    T update(T entity);
}
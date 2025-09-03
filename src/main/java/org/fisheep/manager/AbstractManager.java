package org.fisheep.manager;

import lombok.extern.slf4j.Slf4j;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.SealException;

/**
 * 抽象管理器基类
 * 提供通用的管理器功能
 */
@Slf4j
public abstract class AbstractManager<T> implements Manager<T> {
    
    /**
     * 获取数据存储根对象
     * @return 数据存储根对象
     */
    protected org.fisheep.bean.data.Data getStorage() {
        return StorageManagerFactory.data();
    }
    
    /**
     * 验证实体是否有效
     * @param entity 实体对象
     * @throws SealException 如果实体无效
     */
    protected abstract void validateEntity(T entity) throws SealException;
    
    /**
     * 检查实体是否已存在
     * @param entity 实体对象
     * @return 如果存在返回true，否则返回false
     */
    protected abstract boolean exists(T entity);
    
    @Override
    public T add(T entity) {
        validateEntity(entity);
        if (exists(entity)) {
            throw new SealException(org.fisheep.common.ErrorEnum.ENTITY_ALREADY_EXISTS);
        }
        return doAdd(entity);
    }
    
    /**
     * 执行添加操作
     * @param entity 实体对象
     * @return 添加后的实体
     */
    protected abstract T doAdd(T entity);
    
    @Override
    public boolean delete(int id) {
        return doDelete(id);
    }
    
    /**
     * 执行删除操作
     * @param id 实体ID
     * @return 是否删除成功
     */
    protected abstract boolean doDelete(int id);
    
    @Override
    public java.util.List<T> getAll() {
        return doGetAll();
    }
    
    /**
     * 执行获取所有实体操作
     * @return 所有实体列表
     */
    protected abstract java.util.List<T> doGetAll();
    
    @Override
    public T getById(int id) {
        return doGetById(id);
    }
    
    /**
     * 执行根据ID获取实体操作
     * @param id 实体ID
     * @return 实体对象
     */
    protected abstract T doGetById(int id);
    
    @Override
    public T update(T entity) {
        validateEntity(entity);
        return doUpdate(entity);
    }
    
    /**
     * 执行更新操作
     * @param entity 实体对象
     * @return 更新后的实体
     */
    protected abstract T doUpdate(T entity);
    
    /**
     * 处理异常
     * @param e 异常对象
     * @param errorMessage 错误消息
     * @throws SealException 封装后的异常
     */
    protected void handleException(Exception e, String errorMessage) throws SealException {
        log.error(errorMessage, e);
        throw new SealException(org.fisheep.common.ErrorEnum.INTERNAL_ERROR, errorMessage);
    }
}
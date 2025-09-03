package org.fisheep.controller;

import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.fisheep.bean.Db;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.manager.DatabaseManager;

/**
 * 数据库控制器
 * 处理数据库连接相关的HTTP请求
 */
@Slf4j
public class DatabaseController {
    
    private final DatabaseManager databaseManager;
    
    public DatabaseController() {
        this.databaseManager = new DatabaseManager();
    }
    
    /**
     * 获取所有数据库连接
     */
    public void getAllDatabases(Context ctx) {
        try {
            Result result = databaseManager.getAllDatabaseConnections();
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 添加数据库连接
     */
    public void addDatabase(Context ctx) {
        try {
            Db db = ctx.bodyAsClass(Db.class);
            Result result = databaseManager.addDatabaseConnection(db);
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
        }
    }
    
    /**
     * 删除数据库连接
     */
    public void deleteDatabase(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Result result = databaseManager.deleteDatabaseConnection(id);
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
            var result = databaseManager.getDbAndTimestamp();
            ctx.json(result);
        } catch (Exception e) {
            handleException(ctx, e);
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
            log.error("Unexpected error in DatabaseController", e);
            ctx.json(new Result(500, "Internal server error"));
        }
    }
}
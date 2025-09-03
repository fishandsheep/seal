package org.fisheep;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.util.NaiveRateLimit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.ThreadFactory;
import org.fisheep.config.AppConfig;
import org.fisheep.controller.DatabaseController;
import org.fisheep.controller.SqlAnalysisController;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * Seal应用程序主类
 * SQL风险分析工具的入口点
 */
@Slf4j
public class SealApplication {

    private static final DatabaseController databaseController = new DatabaseController();
    private static final SqlAnalysisController sqlAnalysisController = new SqlAnalysisController();
    private static final AppConfig config = AppConfig.getInstance();

    public static void main(String[] args) {
        initializeApplication();
        startWebServer();
    }

    /**
     * 初始化应用程序
     */
    private static void initializeApplication() {
        // 初始化懒加载引用管理器
        LazyReferenceManager.get().stop().clear();
        LazyReferenceManager.set(LazyReferenceManager.New(
            Lazy.Checker(
                Duration.ofMinutes(config.getAppConfig().getLazyCacheDurationMinutes()).toMillis(), 
                config.getAppConfig().getLazyCacheThreshold()
            )
        ));
        
        log.info("Seal application initialized successfully");
    }

    /**
     * 启动Web服务器
     */
    private static void startWebServer() {
        var app = Javalin.create(config -> {
            // 配置静态文件服务
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.http.defaultContentType = "text/plain; charset=utf-8";
            config.router.contextPath = AppConfig.getInstance().getAppConfig().getContextPath();

            // 配置请求日志
            config.requestLogger.http((ctx, ms) ->
                log.debug("{}接口耗时：{}ms", ctx.path(), ms)
            );

        });

        // 配置API路由
        configureRoutes(app);

        // 配置文件上传接口（独立路由）
        configureFileUpload(app);

        // 配置SSE接口
        configureSseEndpoints(app);

        // 配置异常处理
        configureExceptionHandling(app);

        // 启动服务器
        int port = AppConfig.getInstance().getAppConfig().getServerPort();
        app.start(port);
        
        log.info("Seal server started on port {} with context path '{}'", 
                port, AppConfig.getInstance().getAppConfig().getContextPath());

        // 配置关闭钩子
        configureShutdownHooks(app);
    }

    /**
     * 配置API路由
     */
    private static void configureRoutes(Javalin app) {
        // 数据库管理API
        app.get("/api/v1/databases", databaseController::getAllDatabases);
        app.post("/api/v1/databases", databaseController::addDatabase);
        app.delete("/api/v1/databases/{id}", databaseController::deleteDatabase);
        
        // SQL分析API
        app.get("/api/v1/analysis/db-timestamp", databaseController::getDbAndTimestamp);
        app.post("/api/v1/analysis/results", sqlAnalysisController::getAnalysisResults);
        app.post("/api/v1/analysis/status", sqlAnalysisController::getTaskStatus);
        app.post("/api/v1/analysis/export", sqlAnalysisController::exportResults);
    }

    /**
     * 配置文件上传接口
     */
    private static void configureFileUpload(Javalin app) {
        app.post("/upload", ctx -> {
            // 应用速率限制
            applyRateLimit(ctx);
            sqlAnalysisController.uploadPcapFile(ctx);
        });
    }

    /**
     * 配置SSE端点
     */
    private static void configureSseEndpoints(Javalin app) {
        app.sse("/analysis/status/{taskId}", sqlAnalysisController::sendTaskStatus);
    }

    /**
     * 配置异常处理
     */
    private static void configureExceptionHandling(Javalin app) {
        app.exception(SealException.class, (e, ctx) -> {
            log.warn("Business exception: {}", e.getErrorDetails());
            ctx.json(new Result(e.getCode(), e.getMsg()));
        });

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unexpected error occurred", e);
            ctx.json(new Result(500, "Internal server error"));
        });
    }

    /**
     * 应用速率限制
     */
    private static void applyRateLimit(io.javalin.http.Context ctx) {
        var appConfig = config.getAppConfig();
        NaiveRateLimit.requestPerTimeUnit(ctx, appConfig.getRateLimitPerMinute(), TimeUnit.MINUTES);
        NaiveRateLimit.requestPerTimeUnit(ctx, appConfig.getRateLimitPerSecond(), TimeUnit.SECONDS);
    }

    /**
     * 配置关闭钩子
     */
    private static void configureShutdownHooks(Javalin app) {
        Runtime.getRuntime().addShutdownHook(new Thread(ThreadFactory::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }
}
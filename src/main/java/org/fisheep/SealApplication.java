package org.fisheep;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.util.NaiveRateLimit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.ThreadFactory;
import org.fisheep.manager.DbManager;
import org.fisheep.manager.ExplainManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.javalin.apibuilder.ApiBuilder.*;

@Slf4j
public class SealApplication {

    public static void main(String[] args) {

        LazyReferenceManager.get().stop().clear();
        LazyReferenceManager.set(LazyReferenceManager.New(Lazy.Checker(Duration.ofMinutes(1).toMillis(), 0.75)));

        var app = Javalin.create(config -> {

                    config.staticFiles.add("/public", Location.CLASSPATH);
                    config.http.defaultContentType = "text/plain; charset=utf-8";
                    config.router.contextPath = "/seal";

                    config.requestLogger.http((ctx, ms) ->
                            log.debug("{}接口耗时：{}ms", ctx.path(), ms)
                    );

                    config.router
                            // 数据连接相关接口
                            .apiBuilder(() -> path("/db", () -> {
                                get(DbManager::all); //1. 查询全部连接
                                post(DbManager::add); //2. 添加连接
                                path("/{id}", () -> delete(DbManager::delete)); // 3. 根据下标删除连接
                            }))
                            // TODO 导出
                            .apiBuilder(() -> path("/export", () -> post(ExplainManager::export)))
                            // 风险扫描相关接口
                            .apiBuilder(() -> path("/explain", () -> {
                                get(DbManager::dbAndTimestamp); // 4. 获取最近风险扫描的时间戳记录
                                path("/result", () -> post(ExplainManager::pageOne));// 5. 分页查询风险SQL
                                path("/status", () -> post(ExplainManager::status));// 6. 获取上传sql二进制文件任务的状态
                            }));
                }).post("/upload", ctx -> { // 7. 上传sql二进制文件
                    NaiveRateLimit.requestPerTimeUnit(ctx, 10, TimeUnit.MINUTES);
                    NaiveRateLimit.requestPerTimeUnit(ctx, 1, TimeUnit.SECONDS);
                    ExplainManager.upload(ctx);
                })
                .exception(SealException.class, (e, ctx) -> ctx.json(new Result(e.getCode(), e.getMsg()))).start(7070);

        app.sse("/explain/status/{explainId}", ExplainManager::sendStatus);

        Runtime.getRuntime().addShutdownHook(new Thread(ThreadFactory::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }
}
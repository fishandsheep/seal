package org.fisheep.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.fisheep.bean.SqlStatement;
import org.fisheep.bean.Status;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.util.PcapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ExplainManager {

    private static final ExecutorService customThreadPool = new ThreadPoolExecutor(
            10,                      // 核心线程数
            20,                      // 最大线程数
            60L,                     // 超过核心线程数时，线程最大空闲时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),  // 任务队列容量
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );

    public static void one(Context ctx) {
        String explainId = ctx.formParam("explainId");
        ctx.json(new Result(StorageManagerFactory.data().sqlStatements().one(explainId)));
    }

    public static void status(Context ctx) {
        String explainId = ctx.formParam("explainId");
        ctx.json(new Result(StorageManagerFactory.data().status().one(explainId)));
    }

    public static void dbAndTimestamp(Context ctx) {
        ctx.json(StorageManagerFactory.data().sqlStatements().dbAndTimestamp());
    }

    //todo
    public static void upload(Context ctx) throws SealException {
        var uploadedFile = ctx.uploadedFile("file");
        if (uploadedFile == null) {
            throw new SealException(ErrorEnum.FILE_IS_EMPTY);
        }
        var id = Integer.parseInt(ctx.formParam("id"));
        var data = StorageManagerFactory.data();
        var timestamp = data.dbs().addTimestamp(id);
        var db = data.dbs().one(id);
        //再测试下连接
        DbManager.getDbVersion(db);

        var results = PcapUtil.parseLogFile(uploadedFile, db.getPort());
        var explainId = db.getId() + "|" + timestamp;
        data.sqlStatements().add(explainId, results);
        data.status().put(explainId, new Status(0, results.size(), 0, 0, null, 0, null));

        ctx.async(() -> {
            ctx.result(new ObjectMapper().writeValueAsString(new Result("the file is read successfully and is being parsed")));
            // 使用CompletableFuture异步处理任务
            List<SqlStatement> sqlStatements = data.sqlStatements().one(explainId);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            sqlStatements.forEach(sqlStatement -> {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // TODO: 解析 SQL
                    } catch (Exception e) {
                        //todo 解析异常
                    }
                }, customThreadPool);
                futures.add(future);

                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allOf.thenRun(() -> {
                    //todo
                }).exceptionally(ex -> {
                    //todo 异常
                    return null;
                });
            });
            shutdown();
        });
    }

    public static void shutdown() {
        customThreadPool.shutdown();
        try {
            if (!customThreadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                customThreadPool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            customThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

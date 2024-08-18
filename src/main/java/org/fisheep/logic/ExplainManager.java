package org.fisheep.logic;

import io.javalin.http.Context;
import org.fisheep.bean.SqlStatement;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.util.PcapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExplainManager {

    public static void one(Context ctx) {
        final String id = ctx.formParam("id");
        var data = StorageManagerFactory.data();
        List<SqlStatement> one = data.sqlStatements().one(id);
        ctx.json(new Result(one));
    }

    //todo
    public static void upload(Context ctx) throws SealException {
        var uploadedFile = ctx.uploadedFile("file");
        var id = Integer.parseInt(ctx.formParam("id"));
        var data = StorageManagerFactory.data();
        String timestamp = data.dbs().addTimestamp(id);
        var db = data.dbs().one(id);
        //再测试下连接
        DbManager.getDbVersion(db);
        data.sqlStatements().add(db.getId() + timestamp, new ArrayList<>());
        ctx.async(() -> {
            ctx.result("Task received and is being processed");
            // 使用CompletableFuture异步处理任务
            CompletableFuture.runAsync(() -> {
                try {
                    var results = PcapUtil.parseLogFile(uploadedFile, db.getPort());
                    data.sqlStatements().add(db.getId() + timestamp, results);
                } catch (SealException e) {
                    //todo 加一个状态map
                    throw new RuntimeException(e);
                }
            });
            List<SqlStatement> all = data.sqlStatements().one(db.getId() + timestamp);
            all.forEach(sqlStatement ->
                    CompletableFuture.runAsync(() -> {
                        //todo 解析SQL
                    }));
        });
    }
}

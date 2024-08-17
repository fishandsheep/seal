package org.fisheep.logic;

import io.javalin.http.Context;
import org.fisheep.bean.Db;
import org.fisheep.bean.SqlStatement;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.util.PcapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author BigOrange
 */
public class DbManager {

    public static void add(Context ctx) throws SealException {
        var db = ctx.bodyAsClass(Db.class);
        var dbs = StorageManagerFactory.data().dbs();
        boolean b = dbs.all().stream().anyMatch(db1 -> db1.getId().equals(db.getId()));
        if (b) {
            throw new SealException(ErrorEnum.MYSQL_CONNECTION_EXIST);
        }
        DbFactory.getDbVersion(db);
        dbs.add(db);
        var all = dbs.all();
        all.forEach(db1 -> db1.setPassword(null));
        ctx.json(new Result(all));
    }

    public static void delete(Context ctx) {
        var id = Integer.parseInt(ctx.pathParam("id"));
        var dbs = StorageManagerFactory.data().dbs();
        dbs.delete(id);
        var all = dbs.all();
        all.forEach(db -> db.setPassword(null));
        ctx.json(new Result(all));
    }

    public static void all(Context ctx) {
        var all = StorageManagerFactory.data().dbs().all();
        all.forEach(db -> db.setPassword(null));
        ctx.json(new Result(all));
    }

    //todo
    public static void upload(Context ctx) {
        var uploadedFile = ctx.uploadedFile("file");
        var id = Integer.parseInt(ctx.formParam("id"));
        var data = StorageManagerFactory.data();
        String timestamp = data.dbs().addTimestamp(id);
        var db = data.dbs().one(id);
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
            List<SqlStatement> all = data.sqlStatements().all(db.getId() + timestamp);
            all.forEach(sqlStatement ->
                    CompletableFuture.runAsync(() -> {
                        //todo 解析SQL
                    }));
        });

    }
}

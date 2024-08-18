package org.fisheep;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.logic.DbManager;
import org.fisheep.logic.ExplainManager;

import java.time.Duration;

import static io.javalin.apibuilder.ApiBuilder.*;

public class SealApplication {

    public static void main(String[] args) {
        LazyReferenceManager.set(LazyReferenceManager.New(
                Lazy.Checker(
                        Duration.ofMinutes(1).toMillis(),
                        0.75
                )
        ));

        var app = Javalin
                .create(config -> {
                    config.staticFiles.add("/public", Location.CLASSPATH);
                    config.requestLogger.http((ctx, ms) ->
                            System.out.println(ctx.path() + "接口耗时："+ ms + "ms"));
                    config.router
                            .apiBuilder(() -> path("/db", () -> {
                                get(DbManager::all);
                                post(DbManager::add);
                                path("/{id}", () -> {
                                    delete(DbManager::delete);
                                });
                            }))
                            .apiBuilder(() -> path("/upload", () ->
                                post(ExplainManager::upload)))
                            .apiBuilder(() -> path("/sql", () ->
                                post(ExplainManager::one)));
                })
                .exception(SealException.class, (e, ctx) -> {
                    ctx.json(new Result(e.getCode(), e.getMsg()));
                })
                .start(7070);

        //上传文件
//        app.post("/upload", ctx -> {
//            var uploadedFile = ctx.uploadedFile("file");
//            var id = ctx.formParam("id");
//            var db = DbFactory.dbConfigs.get(id);
//            var results = PcapUtil.parseLogFile(uploadedFile, db.getPort());
//            data.sqlStatements().add(id, results);
//            var sqlStatements = data.sqlStatements().all(id);
//            sqlStatements.forEach(sqlStatement -> System.out.println(sqlStatement.getContent() + ";"));
//            PageResult<SqlStatement> pageResult = new PageResult<>(sqlStatements, Integer.parseInt(ctx.formParam("currentPage")), Integer.parseInt(ctx.formParam("pageSize")));
//            //TODO 异步？
////            List<SqlStatement> subList = sqlStatements.subList(0, 10);
//            //TODO 增加风险解析方法
//            ctx.json(pageResult);
//        });

        app.post("/test", ctx -> {
            Main.main(null);
            ctx.html(null);
        });

    }
}
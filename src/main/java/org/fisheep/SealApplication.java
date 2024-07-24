package org.fisheep;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import org.fisheep.bean.Db;
import org.fisheep.bean.DbFactory;
import org.fisheep.common.PageResult;
import org.fisheep.bean.SqlStatement;
import org.fisheep.util.PcapUtil;

import java.util.List;

public class SealApplication {

    public static void main(String[] args) {
        var app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
        }).start(7070);

        app.post("/connect", ctx -> {
            Db db = ctx.bodyAsClass(Db.class);
            DbFactory.getDbVersion(db);
            db.setPassword(null);
            ctx.json(db);
        });

        app.post("/upload", ctx -> {
            UploadedFile uploadedFile = ctx.uploadedFile("file");
            Db db = DbFactory.dbConfigs.get(ctx.formParam("id"));
            List<SqlStatement> results = PcapUtil.parseLogFile(uploadedFile, db.getPort());
            PageResult<SqlStatement> pageResult = new PageResult<>(results, Integer.parseInt(ctx.formParam("currentPage")), Integer.parseInt(ctx.formParam("pageSize")));
//            pageResult.saveResult();
//            PageResult<SqlStatement> pageResults = pageResult.results(Integer.parseInt(ctx.formParam("currentPage")), Integer.parseInt(ctx.formParam("pageSize")));
//            List<SqlStatement> sqlStatements = PcapUtil.parseLogFile(uploadedFile, db.getPort());
            //TODO 异步？
//            List<SqlStatement> subList = sqlStatements.subList(0, 10);
            //TODO 增加风险解析方法
            ctx.json(pageResults);
        });

    }
}
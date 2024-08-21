package org.fisheep;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.ThreadFactory;
import org.fisheep.manager.DbManager;
import org.fisheep.manager.ExplainManager;

import java.time.Duration;

import static io.javalin.apibuilder.ApiBuilder.*;

public class SealApplication {

    public static void main(String[] args) {
        LazyReferenceManager.get().stop().clear();
        LazyReferenceManager.set(LazyReferenceManager.New(
                Lazy.Checker(
                        Duration.ofMinutes(1).toMillis(),
                        0.75
                )
        ));

        var app = Javalin
                .create(config -> {
                    config.staticFiles.add("/public", Location.CLASSPATH);
                    config.http.defaultContentType = "text/plain; charset=utf-8";
                    config.router.contextPath = "/seal";
                    config.requestLogger.http((ctx, ms) ->
                            System.out.println(ctx.path() + "接口耗时：" + ms + "ms"));
                    config.router
                            .apiBuilder(() -> path("/db", () -> {
                                get(DbManager::all);
                                post(DbManager::add);
                                path("/{id}", () -> delete(DbManager::delete));
                            }))
                            .apiBuilder(() -> path("/upload", () ->
                                    post(ExplainManager::upload)))
                            .apiBuilder(() -> path("/explain", () -> {
                                        get(DbManager::dbAndTimestamp);
                                path("/result", () -> post(ExplainManager::pageOne));
                                        path("/status", () -> post(ExplainManager::status));
                                    }
                            ));
                })
                .exception(SealException.class, (e, ctx) -> ctx.json(new Result(e.getCode(), e.getMsg())))
                .start(7070);

        app.post("/test", ctx -> {
            Main.main(null);
            ctx.html(null);
        });
        
        Runtime.getRuntime().addShutdownHook(new Thread(ThreadFactory::shutdown)
        );
    }
}
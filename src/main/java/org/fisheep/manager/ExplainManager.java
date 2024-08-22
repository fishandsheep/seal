package org.fisheep.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageConnection;
import org.eclipse.store.storage.types.StorageEntityTypeExportStatistics;
import org.fisheep.bean.Db;
import org.fisheep.bean.SqlStatement;
import org.fisheep.bean.Status;
import org.fisheep.common.*;
import org.fisheep.util.PcapUtil;
import org.fisheep.util.RegexUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author BigOrange
 */
@Slf4j
public class ExplainManager {

    public static void pageOne(Context ctx) throws SealException {
        var explainId = ctx.formParam("explainId");
        var currentPage = Integer.parseInt(Objects.requireNonNull(ctx.formParam("currentPage")));
        var pageSize = Integer.parseInt(Objects.requireNonNull(ctx.formParam("pageSize")));
        var one = StorageManagerFactory.data().sqlStatements().one(explainId);
        if (one == null) {
            throw new SealException(ErrorEnum.TASK_NOT_FOUND);
        }
        ctx.json(new PageResult<>(one, currentPage, pageSize));
    }

    public static void status(Context ctx) throws SealException {
        var explainId = ctx.formParam("explainId");
        var one = StorageManagerFactory.data().status().one(explainId);
        if (one == null) {
            throw new SealException(ErrorEnum.TASK_NOT_FOUND);
        }
        ctx.json(new Result(one));
    }

    public static void sendStatus(SseClient sseClient) {
        var ctx = sseClient.ctx();
        var explainId = ctx.pathParam("explainId");
        CompletableFuture.runAsync(() -> {
            while (!isTaskCompleted(explainId)) {
                sseClient.sendEvent("message", "task ~~~~~~~loading");
                try {
                    Thread.sleep(1000); // 每秒发送一次状态更新
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            sseClient.sendEvent("message", "task: " + explainId + " is complete");
            sseClient.close();
        });
    }

    public static void export(Context ctx) {
        NioFileSystem fileSystem = NioFileSystem.New();
        EmbeddedStorageManager storage = EmbeddedStorage.start(fileSystem.ensureDirectoryPath("storage"));
        StorageConnection connection = storage.createConnection();
        StorageEntityTypeExportStatistics exportResult = connection.exportTypes(fileSystem.ensureDirectoryPath("export-dir"));
    }

    public static void upload(Context ctx) throws SealException {
        var uploadedFile = ctx.uploadedFile("file");
        if (uploadedFile == null) {
            throw new SealException(ErrorEnum.FILE_IS_EMPTY);
        }
        var id = Integer.parseInt(Objects.requireNonNull(ctx.formParam("id")));
        var data = StorageManagerFactory.data();
        var timestamp = data.dbs().addTimestamp(id);
        var db = data.dbs().one(id);
        //再测试下连接
        DbManager.getDbVersion(db);

        var results = PcapUtil.parseLogFile(uploadedFile, db.getPort());
        var explainId = db.getId() + "|" + timestamp;
        //data.sqlStatements().add(explainId, results);
        data.status().put(explainId, new Status());

        ctx.async(() -> {
            ctx.result(new ObjectMapper().writeValueAsString(new Result("connection id: " + db.getId() + " , task timestamp: " + timestamp + " . the file is read successfully and is being parsed")));
            List<SqlStatement> sqlStatements = data.sqlStatements().one(explainId);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            sqlStatements.forEach(sqlStatement -> {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String explain = explain(sqlStatement.getContent(), db);
                        sqlStatement.setExplain(explain);
                        sqlStatement.setScore(RegexUtil.parseScore(explain));
                    } catch (Exception e) {
                        if (e instanceof SealException) {
                            SealException se = (SealException) e;
                            sqlStatement.setExplain(se.getMsg());
                        } else {
                            sqlStatement.setExplain(e.getMessage());
                        }
                        sqlStatement.setScore(RegexUtil.parseScore("-1"));
                    }
                }, ThreadFactory.getThreadPool());
                futures.add(future);
            });
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.thenRun(() -> {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
                String timestampString = now.format(formatter);
                var time = calculateDifferenceInSeconds(timestamp, timestampString);
                data.sqlStatements().add(explainId, sqlStatements);
                data.status().put(explainId, new Status(1, time));
            });
        });
    }

    private static String explain(String sql, Db db) {
        final Process process = getProcess(sql, db);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            StringBuilder jsonBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line + "\n");
            }
            log.debug("soar执行结果：{}", jsonBuilder);
            return jsonBuilder.toString();
        } catch (Exception e) {
            throw new SealException("获取soar结果异常", 500, e);
        } finally {
            process.destroy();
        }
    }

    private static Process getProcess(String sql, Db db) {
        try {
            List<String> command = new ArrayList<>();
            //-Dsoar.path=
            String soarPath = System.getProperty("soar.path");
            command.add(soarPath);
            command.add("-query");
            command.add(sql);
            command.add("-test-dsn=" + db.getUsername() + ":" + db.getPassword() + "@" + db.getUrl() + ":" + db.getPort() + "/" + db.getSchema());
            command.add("-allow-online-as-test=true");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            return processBuilder.start();
        } catch (IOException e) {
            throw new SealException("执行soar命令失败", 500, e);
        }
    }

    public static long calculateDifferenceInSeconds(String timestamp1, String timestamp2) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");

        LocalDateTime dateTime1 = LocalDateTime.parse(timestamp1, formatter);
        LocalDateTime dateTime2 = LocalDateTime.parse(timestamp2, formatter);

        Duration duration = Duration.between(dateTime1, dateTime2);

        return duration.getSeconds();
    }

    private static boolean isTaskCompleted(String explainId) {
        var one = StorageManagerFactory.data().status().one(explainId);
        return one.getStatus() == 1;
    }
}

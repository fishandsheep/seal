package org.fisheep.manager;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.fisheep.bean.Struct;
import org.fisheep.common.*;
import org.fisheep.util.PcapUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
        data.sqlStatements().add(explainId, results);
        data.status().put(explainId, new Status(0, results.size(), 0, 0, 0));

        ctx.async(() -> {
            ctx.result(new ObjectMapper().writeValueAsString(new Result("connection id: + " + db.getId() + "task timestamp: " + timestamp + ". the file is read successfully and is being parsed")));
            List<SqlStatement> sqlStatements = data.sqlStatements().one(explainId);
            List<SqlStatement> explainSqlStatements = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            sqlStatements.forEach(sqlStatement -> {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    ArrayList<Struct> structs = new ArrayList<>();
                    try {
                        Map<String, Map<String, Struct>> explain = explain(sqlStatement.getContent(), db);
                        explain.values().forEach(structMap -> structMap.values().forEach(struct -> {
                            if ("EXP.000".equals(struct.getItem())) {
                                sqlStatement.setExplainPlan(struct);
                            } else if (struct.getItem().startsWith("ERR.")) {
                                sqlStatement.setErrorMessage(struct.getSummary());
                            } else {
                                structs.add(struct);
                            }
                        }));
                        sqlStatement.setExplainRisk(structs);
                        sqlStatement.setRisk(structs.size());
                        explainSqlStatements.add(sqlStatement);
                        successCount.incrementAndGet();
                    } catch (IOException e) {
                        sqlStatement.setErrorMessage(e.getMessage());
                        explainSqlStatements.add(sqlStatement);
                        failureCount.incrementAndGet();
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
                data.sqlStatements().add(explainId, explainSqlStatements);
                data.status().put(explainId, new Status(1, sqlStatements.size(), successCount.get(), failureCount.get(), time));
            }).exceptionally((e) -> {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
                var timestampString = now.format(formatter);
                var time = calculateDifferenceInSeconds(timestamp, timestampString);
                if (failureCount.get() != sqlStatements.size()) {
                    data.status().put(explainId, new Status(2, sqlStatements.size(), 0, failureCount.get(), time));
                }
                return null;
            });
        });
    }

    private static Map<String, Map<String, Struct>> explain(String sql, Db db) throws IOException {
        final Process process = getProcess(sql, db);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            StringBuilder jsonBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            log.debug("soar执行结果：{}", jsonBuilder);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonBuilder.toString(), new TypeReference<>() {
            });
        } finally {
            process.destroy();
        }
    }

    private static Process getProcess(String sql, Db db) throws IOException {
        List<String> command = new ArrayList<>();
        //-Dsoar.path=
        String soarPath = System.getProperty("soar.path");
        command.add(soarPath);
        command.add("-query");
        command.add(sql);
        command.add("-test-dsn=" + db.getUsername() + ":" + db.getPassword() + "@" + db.getUrl() + ":" + db.getPort() + "/" + db.getSchema());
        command.add("-allow-online-as-test=true");
        command.add("-report-type=json");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
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

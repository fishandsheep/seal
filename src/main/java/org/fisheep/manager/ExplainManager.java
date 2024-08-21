package org.fisheep.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ExplainManager {

    public static void pageOne(Context ctx) {
        String explainId = ctx.formParam("explainId");
        int currentPage = Integer.parseInt(ctx.formParam("currentPage"));
        int pageSize = Integer.parseInt(ctx.formParam("pageSize"));
        ctx.json(new Result(StorageManagerFactory.data().sqlStatements().pageOne(explainId, currentPage, pageSize)));
    }

    public static void status(Context ctx) {
        String explainId = ctx.formParam("explainId");
        ctx.json(new Result(StorageManagerFactory.data().status().one(explainId)));
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
            ctx.result(new ObjectMapper().writeValueAsString(new Result("the file is read successfully and is being parsed" + explainId)));
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
                long time = calculateDifferenceInSeconds(timestamp, timestampString);
                data.sqlStatements().add(explainId, explainSqlStatements);
                data.status().put(explainId, new Status(1, sqlStatements.size(), successCount.get(), failureCount.get(), time));
            }).exceptionally((e) -> {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
                String timestampString = now.format(formatter);
                long time = calculateDifferenceInSeconds(timestamp, timestampString);
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
            Map<String, Struct> explainMap = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonBuilder.toString(), new TypeReference<Map<String, Map<String, Struct>>>() {
            });
        } finally {
            process.destroy();
        }
    }

    private static Process getProcess(String sql, Db db) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("/home/lighthouse/soar/soar");
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
}

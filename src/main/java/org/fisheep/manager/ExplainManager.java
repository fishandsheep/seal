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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ExplainManager {

    public static void one(Context ctx) {
        String explainId = ctx.formParam("explainId");
        ctx.json(new Result(StorageManagerFactory.data().sqlStatements().one(explainId)));
    }

    public static void status(Context ctx) {
        String explainId = ctx.formParam("explainId");
        ctx.json(new Result(StorageManagerFactory.data().status().one(explainId)));
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
        data.status().put(explainId, new Status(0, results.size(), 0, 0, null, null));

        ctx.async(() -> {
            ctx.result(new ObjectMapper().writeValueAsString(new Result("the file is read successfully and is being parsed")));
            List<SqlStatement> sqlStatements = data.sqlStatements().one(explainId);
            List<SqlStatement> explainSqlStatements = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            sqlStatements.forEach(sqlStatement -> {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    ArrayList<Struct> structs = new ArrayList<>();
                    try {
                        Map<String, Struct> explain = explain(sqlStatement.getContent(), db);
                        explain.values().forEach(struct -> {
                            if (explain.containsKey("EXPLAIN")) {
                                sqlStatement.setExplainPlan(struct);
                            } else {
                                structs.add(struct);
                            }
                        });
                        sqlStatement.setExplainRisk(structs);
                        explainSqlStatements.add(sqlStatement);
                        successCount.incrementAndGet();
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.out.println(e.getMessage());
                        failureCount.incrementAndGet();
                    }
                }, ThreadFactory.getThreadPool());
                futures.add(future);

                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allOf.thenRun(() -> {
                    data.sqlStatements().add(explainId, explainSqlStatements);
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
                    String timestampString = now.format(formatter);
                    data.status().put(explainId, new Status( 1, sqlStatements.size(), successCount.get(), failureCount.get(), null,timestampString));
                }).exceptionally((e) -> {
                    if (failureCount.get() != sqlStatements.size()){
                        LocalDateTime now = LocalDateTime.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
                        String timestampString = now.format(formatter);
                        data.status().put(explainId, new Status(2, sqlStatements.size(), 0, failureCount.get(), null,timestampString));
                    }
                    return null;
                });
            });
        });
    }

    private static Map<String, Struct> explain(String sql, Db db) throws IOException {
        final Process process = getProcess(sql, db);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder jsonBuilder = new StringBuilder();
            Map<String, Struct> explainMap = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Map<String, Struct>> map = objectMapper.readValue(jsonBuilder.toString(), new TypeReference<Map<String, Map<String, Struct>>>() {});
            map.values().forEach(structMap -> structMap.values().forEach(struct -> {
                if ("EXP.000".equals(struct.getItem())) {
                    explainMap.put("EXPLAIN", struct);
                } else {
                    explainMap.put("RISK", struct);
                }
            }));
            return explainMap;
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

}

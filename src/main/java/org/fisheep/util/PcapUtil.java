package org.fisheep.util;


import io.javalin.http.UploadedFile;
import io.kaitai.struct.ByteBufferKaitaiStream;
import org.apache.commons.lang3.ArrayUtils;
import org.fisheep.bean.SqlStatement;
import org.fisheep.bean.TcpSqlInfo;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.SealException;
import org.fisheep.kaitai.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PcapUtil {

    static Pattern lineBreakPattern = Pattern.compile("(\\r?\\n)+");

    public static String singleLine(String str) {
        return lineBreakPattern.matcher(str).replaceAll(" ");
    }

    private static final byte[] LOWER_SELECT_FLAG = {3, 0, 1, 83, 69, 76, 69, 67, 84};
    private static final byte[] UPPER_SELECT_FLAG = {3, 0, 1, 115, 101, 108, 101, 99, 116};
    private static final byte[] LONG_SQL_PRE_FLAG = {0x01, 0x01, 0x08, 0x0a};

    public static List<SqlStatement> parseLogFile(UploadedFile file, int dstPort) throws SealException {
        List<SqlStatement> sqlStatements = new ArrayList<>();
        byte[] bytes;
        try {
            bytes = file.content().readAllBytes();
        } catch (IOException e) {
            throw new SealException(ErrorEnum.FILE_READ_FAIL);
        }
        Pcap pcap = new Pcap(new ByteBufferKaitaiStream(bytes));
        ArrayList<Pcap.Packet> packets = pcap.packets();
        if (packets.isEmpty()) {
            return sqlStatements;
        }

        Map<Long, TcpSqlInfo> tcpSqlInfoMap = new HashMap<>();
        for (Pcap.Packet packet : packets) {
            try {
                EthernetFrame ethernetFrame = (EthernetFrame) packet.body();
                Ipv4Packet ipv4Packet = (Ipv4Packet) ethernetFrame.body();
                ProtocolBody protocolBody = ipv4Packet.body();
                TcpSegment tcpSegment = (TcpSegment) protocolBody.body();

                //返回报文
                if (tcpSegment.srcPort() == dstPort) {

                    long seqNum = tcpSegment.seqNum();
                    TcpSqlInfo tcpSqlInfo = tcpSqlInfoMap.get(seqNum);
                    if (tcpSqlInfo == null) {
                        continue;
                    }

                    byte[] body = tcpSegment.body();
                    if (body.length <= 12) {
                        continue;
                    }

                    String sql = new String(tcpSqlInfo.getSql(), StandardCharsets.UTF_8);
                    //过滤select语句中没有from的sql
                    if (sql.contains("from") || sql.contains("FROM")) {
                        SqlStatement sqlStatement = new SqlStatement();

                        long respTimestamp = packet.tsSec() * 1000 + packet.tsUsec() / 1000;
                        //获取tcp请求耗时
                        long reqTimestamp = tcpSqlInfo.getTimestamp();

                        sqlStatement.setContent(singleLine(sql));
                        sqlStatement.setMaxTakeTime(respTimestamp - reqTimestamp);
                        sqlStatements.add(sqlStatement);

                        //清除 tcpSqlInfoMap 中的数据
                        tcpSqlInfoMap.remove(seqNum);
                    }
                }

                //请求报文
                if (tcpSegment.dstPort() == dstPort) {
                    //1. 不一致,将上次 sql 放到list中，set pre sql
                    long ackNum = tcpSegment.ackNum();
                    TcpSqlInfo tcpSqlInfo = tcpSqlInfoMap.get(ackNum);

                    byte[] body = tcpSegment.body();
                    long timestamp = packet.tsSec() * 1000 + packet.tsUsec() / 1000;
                    if (tcpSqlInfo == null) {
                        //解析当前的byte数组
                        int indexOfSub = getIndexOfSub(body);
                        if (indexOfSub == -1) {
                            //当前tcp包中没有sql select 语句
                            continue;
                        }
                        tcpSqlInfo = new TcpSqlInfo();

                        byte[] sqlBytes = ArrayUtils.subarray(body, indexOfSub + 3, body.length);
                        tcpSqlInfo.setSql(sqlBytes);
                        tcpSqlInfo.setTimestamp(timestamp);

                        tcpSqlInfoMap.put(ackNum, tcpSqlInfo);

                    } else {
                        byte[] preSql = tcpSqlInfo.getSql();
                        int longSqlPreIndex = ByteArrayUtil.indexOfSub(body, LONG_SQL_PRE_FLAG);
                        if (longSqlPreIndex == 0) {
                            body = ArrayUtils.subarray(body, 12, body.length);
                        }
                        byte[] append = ArrayUtils.addAll(preSql, body);
                        tcpSqlInfo.setSql(append);
                        tcpSqlInfo.setTimestamp(timestamp);
                    }
                }


            } catch (Exception e) {
                //TODO
            }

        }
        return countSqlStatements(sqlStatements);
    }

    private static List<SqlStatement> countSqlStatements(List<SqlStatement> sqlStatements) {
        Map<String, LongSummaryStatistics> summaryStats = sqlStatements.stream()
                .collect(Collectors.groupingBy(
                        SqlStatement::getContent, // 分组依据ID
                        Collectors.summarizingLong(SqlStatement::getMaxTakeTime) // 每个组收集takeTime的统计信息
                ));

        // 创建新的List，包含ID、最大耗时和执行次数
        return summaryStats.entrySet().stream()
                .map(entry -> SqlStatement.builder()
                        .content(entry.getKey())
                        .maxTakeTime(entry.getValue().getMax())
                        .count(entry.getValue().getCount())
                        .build())
                .sorted(Comparator.comparing(SqlStatement::getCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取当前 byte数组中 select 开始的位置
     *
     * @param body
     * @return
     */
    private static int getIndexOfSub(byte[] body) {
        int indexOfSub = ByteArrayUtil.indexOfSub(body, LOWER_SELECT_FLAG);
        if (indexOfSub == -1) {
            indexOfSub = ByteArrayUtil.indexOfSub(body, UPPER_SELECT_FLAG);
        }
        return indexOfSub;
    }


}

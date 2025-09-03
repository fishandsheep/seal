package org.fisheep.util;

import io.kaitai.struct.ByteBufferKaitaiStream;
import org.fisheep.bean.SqlStatement;
import org.fisheep.bean.TcpSqlInfo;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.SealException;
import org.fisheep.kaitai.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * PCAP文件解析器
 * 负责解析PCAP文件中的MySQL协议数据包
 */
public class PcapParser {
    
    /**
     * 解析PCAP文件，提取SQL语句
     * @param fileData PCAP文件数据
     * @param dstPort 目标端口
     * @return SQL语句列表
     * @throws SealException 解析失败时抛出异常
     */
    public static List<SqlStatement> parsePcapFile(byte[] fileData, int dstPort) throws SealException {
        List<SqlStatement> sqlStatements = new ArrayList<>();
        
        if (fileData == null || fileData.length == 0) {
            throw new SealException(ErrorEnum.FILE_IS_EMPTY);
        }
        
        try {
            Pcap pcap = new Pcap(new ByteBufferKaitaiStream(fileData));
            List<Pcap.Packet> packets = pcap.packets();
            
            if (packets.isEmpty()) {
                return sqlStatements;
            }
            
            Map<Long, TcpSqlInfo> tcpSqlInfoMap = new HashMap<>();
            
            for (Pcap.Packet packet : packets) {
                processPacket(packet, dstPort, tcpSqlInfoMap);
            }
            
            // 处理完整的SQL语句
            return processSqlStatements(tcpSqlInfoMap);
            
        } catch (Exception e) {
            throw new SealException(ErrorEnum.INVALID_PCAP_FILE, "Failed to parse PCAP file", e);
        }
    }
    
    /**
     * 处理单个数据包
     */
    private static void processPacket(Pcap.Packet packet, int dstPort, Map<Long, TcpSqlInfo> tcpSqlInfoMap) {
        try {
            EthernetFrame ethernetFrame = (EthernetFrame) packet.body();
            Ipv4Packet ipv4Packet = (Ipv4Packet) ethernetFrame.body();
            ProtocolBody protocolBody = ipv4Packet.body();
            
            if (protocolBody == null || protocolBody.body() == null || !(protocolBody.body() instanceof TcpSegment)) {
                return;
            }
            
            TcpSegment tcpSegment = (TcpSegment) protocolBody.body();
            
            // 检查是否为目标端口的MySQL流量
            if (tcpSegment.srcPort() == dstPort || tcpSegment.dstPort() == dstPort) {
                analyzeTcpSegment(tcpSegment, tcpSqlInfoMap);
            }
            
        } catch (Exception e) {
            // 忽略单个数据包的解析错误，继续处理其他数据包
        }
    }
    
    /**
     * 分析TCP段，提取SQL信息
     */
    private static void analyzeTcpSegment(TcpSegment tcpSegment, Map<Long, TcpSqlInfo> tcpSqlInfoMap) {
        byte[] payload = tcpSegment.body();
        if (payload == null || payload.length == 0) {
            return;
        }
        
        long srcPort = tcpSegment.srcPort();
        long dstPort = tcpSegment.dstPort();
        long key = srcPort << 16 | dstPort;
        
        TcpSqlInfo tcpSqlInfo = tcpSqlInfoMap.computeIfAbsent(key, k -> new TcpSqlInfo());
        
        // 记录时间戳
        tcpSqlInfo.setTimestamp(System.currentTimeMillis());
        
        // 处理不同类型的MySQL数据包
        if (isSelectStatement(payload)) {
            processSelectStatement(payload, tcpSqlInfo);
        } else if (isLongSqlStatement(payload)) {
            processLongSqlStatement(payload, tcpSqlInfo);
        } else {
            // 其他类型的SQL数据包
            processOtherSqlPacket(payload, tcpSqlInfo);
        }
    }
    
    /**
     * 检查是否为SELECT语句
     */
    private static boolean isSelectStatement(byte[] payload) {
        return ByteArrayUtils.startsWith(payload, ByteArrayUtils.LOWER_SELECT_FLAG, 0) ||
               ByteArrayUtils.startsWith(payload, ByteArrayUtils.UPPER_SELECT_FLAG, 0);
    }
    
    /**
     * 检查是否为长SQL语句
     */
    private static boolean isLongSqlStatement(byte[] payload) {
        return ByteArrayUtils.startsWith(payload, ByteArrayUtils.LONG_SQL_PRE_FLAG, 0);
    }
    
    /**
     * 处理SELECT语句
     */
    private static void processSelectStatement(byte[] payload, TcpSqlInfo tcpSqlInfo) {
        try {
            // 跳过协议头，提取SQL语句
            byte[] sqlBytes = ByteArrayUtils.subarray(payload, 9, payload.length);
            String sql = new String(sqlBytes, StandardCharsets.UTF_8);
            
            SqlStatement sqlStatement = new SqlStatement();
            sqlStatement.setSql(StringUtils.singleLine(sql));
            sqlStatement.setExecuteTime(System.currentTimeMillis());
            
            tcpSqlInfo.getSqlStatements().add(sqlStatement);
            
        } catch (Exception e) {
            // 忽略SQL解析错误
        }
    }
    
    /**
     * 处理长SQL语句
     */
    private static void processLongSqlStatement(byte[] payload, TcpSqlInfo tcpSqlInfo) {
        try {
            // 长SQL语句的特殊处理逻辑
            byte[] sqlBytes = ByteArrayUtils.subarray(payload, 4, payload.length);
            String sql = new String(sqlBytes, StandardCharsets.UTF_8);
            
            SqlStatement sqlStatement = new SqlStatement();
            sqlStatement.setSql(StringUtils.singleLine(sql));
            sqlStatement.setExecuteTime(System.currentTimeMillis());
            
            tcpSqlInfo.getSqlStatements().add(sqlStatement);
            
        } catch (Exception e) {
            // 忽略SQL解析错误
        }
    }
    
    /**
     * 处理其他类型的SQL数据包
     */
    private static void processOtherSqlPacket(byte[] payload, TcpSqlInfo tcpSqlInfo) {
        // 这里可以处理其他类型的MySQL数据包
        // 例如：INSERT, UPDATE, DELETE等
    }
    
    /**
     * 处理完整的SQL语句
     */
    private static List<SqlStatement> processSqlStatements(Map<Long, TcpSqlInfo> tcpSqlInfoMap) {
        List<SqlStatement> allStatements = new ArrayList<>();
        
        for (TcpSqlInfo tcpSqlInfo : tcpSqlInfoMap.values()) {
            List<SqlStatement> statements = tcpSqlInfo.getSqlStatements();
            
            // 去重和处理重复的SQL语句
            Map<String, SqlStatement> uniqueStatements = new HashMap<>();
            for (SqlStatement statement : statements) {
                String sql = statement.getSql();
                if (StringUtils.isNotEmpty(sql)) {
                    SqlStatement existing = uniqueStatements.get(sql);
                    if (existing == null) {
                        uniqueStatements.put(sql, statement);
                    } else {
                        // 更新执行次数
                        existing.setCount(existing.getCount() + 1);
                        // 更新最大执行时间
                        if (statement.getExecuteTime() > existing.getMaxTime()) {
                            existing.setMaxTime(statement.getExecuteTime());
                        }
                    }
                }
            }
            
            allStatements.addAll(uniqueStatements.values());
        }
        
        return allStatements;
    }
}
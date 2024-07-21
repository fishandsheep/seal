package org.fisheep.bean;

import lombok.Data;

@Data
public class TcpSqlInfo {

    private byte[] sql;

    private long timestamp;

}

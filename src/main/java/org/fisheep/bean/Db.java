package org.fisheep.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * BigOrange
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Db implements Serializable {

    private String url;

    private int port;

    private String schema;

    private String username;

    private String password;

    private String version;

    public String getId() {
        return this.url + ":" + this.port + ":" + this.schema;
    }

    private List<String> timestamps;
}
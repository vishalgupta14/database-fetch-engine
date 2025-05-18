package com.platform.engine.dto;

import lombok.Data;

@Data
public class DirectDatabaseConfig {
    private String dbType;     // POSTGRES, MYSQL, etc.
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String database;
    private String schema;
}

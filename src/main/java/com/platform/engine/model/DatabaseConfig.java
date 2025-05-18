package com.platform.engine.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "database_config")
public class DatabaseConfig {

    @Id
    private String id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String database;
    private String schema;
}




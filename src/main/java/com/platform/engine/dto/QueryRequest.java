package com.platform.engine.dto;

import com.platform.engine.enums.OrderDirection;
import lombok.Data;

import java.util.List;

@Data
public class QueryRequest {
    private String configId;
    private DirectDatabaseConfig directConfig;
    private String table;
    private String alias;
    private List<String> selectFields;
    private List<Search> filters;
    private List<JoinRequest> joins;

    private Integer limit;
    private Integer offset;

    private String orderBy;
    private OrderDirection orderDirection;
    private boolean distinct = false;
    private boolean pretty = false;
}


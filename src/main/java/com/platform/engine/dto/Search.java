package com.platform.engine.dto;

import com.platform.engine.enums.DataFilterOperator;
import com.platform.engine.enums.LogicalOperator;
import lombok.Data;

@Data
public class Search {
    private String column;
    private Object value;
    private DataFilterOperator filterOperator = DataFilterOperator.EQUALS;
    private LogicalOperator logicalOperator = LogicalOperator.AND;
    private Integer limit;
    private Integer offset;

    private String castType;
    private String castFormat;
}

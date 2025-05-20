package com.platform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EntityMetadata {
    private String fieldName;
    private String fieldType;
    private String columnName;
    private String columnType;
}

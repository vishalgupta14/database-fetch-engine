package com.platform.engine.dto;

import org.jooq.DataType;

public record TypedValue(Object value, DataType<?> sqlType) {}

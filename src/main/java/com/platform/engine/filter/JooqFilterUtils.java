package com.platform.engine.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.engine.dto.Search;
import com.platform.engine.dto.TypedValue;
import com.platform.engine.enums.LogicalOperator;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.util.CollectionUtils.isEmpty;

public class JooqFilterUtils {

    private JooqFilterUtils() {}

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Condition buildConditionFromSearchList(List<Search> searchList, Map<String, Field<?>> columnFieldMap) {
        if (isEmpty(searchList)) {
            return DSL.noCondition();
        }

        if (searchList.size() == 1) {
            return buildSingleCondition(searchList.get(0), columnFieldMap);
        }

        Condition combined = combineConditions(
                buildSingleCondition(searchList.get(0), columnFieldMap),
                buildSingleCondition(searchList.get(1), columnFieldMap),
                searchList.get(0).getLogicalOperator()
        );

        for (int i = 2; i < searchList.size(); i++) {
            combined = combineConditions(
                    combined,
                    buildSingleCondition(searchList.get(i), columnFieldMap),
                    searchList.get(i - 1).getLogicalOperator()
            );
        }

        return combined;
    }

    private static Condition buildSingleCondition(Search search, Map<String, Field<?>> columnFieldMap) {
        Field<?> originalField = columnFieldMap.get(search.getColumn());

        if (originalField == null) {
            throw new IllegalArgumentException("Unknown column: " + search.getColumn());
        }

        DataType<Object> sqlDataType;
        Field<Object> field;

        if (search.getCastType() != null) {
            sqlDataType = mapStringToSqlDataType(search.getCastType());
            Field<?> rawField = DSL.field(DSL.name(search.getColumn()));
            field = rawField.cast(sqlDataType);
        } else {
            field = (Field<Object>) originalField;
            sqlDataType = field.getDataType().getSQLDataType();
        }

        TypedValue typed = inferTypedValue(search.getValue(), sqlDataType, search.getCastFormat());

        return switch (search.getFilterOperator()) {
            case EQUALS -> {
                if (typed.value() instanceof LocalDateTime dt) {
                    LocalDateTime truncated = dt.truncatedTo(ChronoUnit.SECONDS);
                    LocalDateTime upper = truncated.plusSeconds(1);
                    yield field.between(DSL.val(truncated, SQLDataType.LOCALDATETIME),
                            DSL.val(upper, SQLDataType.LOCALDATETIME));
                } else {
                    yield field.eq(DSL.val(typed.value(), sqlDataType));
                }
            }
            case NOT_EQUALS -> {
                if (typed.value() instanceof LocalDateTime dt) {
                    LocalDateTime truncated = dt.truncatedTo(ChronoUnit.SECONDS);
                    LocalDateTime upper = truncated.plusSeconds(1);
                    yield field.notBetween(DSL.val(truncated, SQLDataType.LOCALDATETIME),
                            DSL.val(upper, SQLDataType.LOCALDATETIME));
                } else {
                    yield field.ne(DSL.val(typed.value(), sqlDataType));
                }
            }
            case GREATER_THAN -> {
                if (typed.value() instanceof LocalDateTime dt) {
                    yield field.gt(DSL.val(dt.truncatedTo(ChronoUnit.SECONDS), SQLDataType.LOCALDATETIME));
                } else {
                    yield field.gt(typed.value());
                }
            }
            case GREATER_THAN_EQUAL -> {
                if (typed.value() instanceof LocalDateTime dt) {
                    yield field.ge(DSL.val(dt.truncatedTo(ChronoUnit.SECONDS), SQLDataType.LOCALDATETIME));
                } else {
                    yield field.ge(typed.value());
                }
            }
            case LESS_THAN -> {
                if (typed.value() instanceof LocalDateTime dt) {
                    yield field.lt(DSL.val(dt.truncatedTo(ChronoUnit.SECONDS), SQLDataType.LOCALDATETIME));
                } else {
                    yield field.lt(typed.value());
                }
            }
            case LESS_THAN_EQUAL -> {
                if (typed.value() instanceof LocalDateTime dt) {
                    yield field.le(DSL.val(dt.truncatedTo(ChronoUnit.SECONDS), SQLDataType.LOCALDATETIME));
                } else {
                    yield field.le(typed.value());
                }
            }
            case LIKE -> field.like("%" + typed.value() + "%");
            case IN -> field.in(castToList(search.getValue(), field, search.getCastFormat()));
            case NOT_IN -> field.notIn(castToList(search.getValue(), field, search.getCastFormat()));
            case BETWEEN -> {
                List<Object> values = castToList(search.getValue(), field, search.getCastFormat());
                if (values.size() != 2) throw new IllegalArgumentException("BETWEEN needs exactly 2 values");
                yield field.between(values.get(0), values.get(1));
            }
            case AND, OR -> DSL.noCondition();
        };
    }

    @SuppressWarnings("unchecked")
    private static TypedValue inferTypedValue(Object raw, DataType<?> sqlType, String formatOverride) {
        if (raw == null) {
            return new TypedValue(null, sqlType);
        }

        // Handle list/collection case
        if (raw instanceof Collection<?> rawList) {
            List<Object> parsedList = new ArrayList<>();
            for (Object item : rawList) {
                TypedValue singleValue = inferTypedValue(item, sqlType, formatOverride);
                parsedList.add(singleValue.value());
            }
            return new TypedValue(parsedList, sqlType);
        }

        Object parsed;
        Class<?> targetType = sqlType.getType();

        try {
            if (targetType == String.class) {
                parsed = raw.toString();
            } else if (targetType == Integer.class) {
                parsed = Integer.parseInt(raw.toString());
            } else if (targetType == Long.class) {
                parsed = Long.parseLong(raw.toString());
            } else if (targetType == Double.class) {
                parsed = Double.parseDouble(raw.toString());
            } else if (targetType == Float.class) {
                parsed = Float.parseFloat(raw.toString());
            } else if (targetType == BigDecimal.class) {
                parsed = new BigDecimal(raw.toString());
            } else if (targetType == Boolean.class) {
                parsed = Boolean.parseBoolean(raw.toString());
            } else if (targetType == LocalDate.class) {
                DateTimeFormatter fmt = formatOverride != null ? DateTimeFormatter.ofPattern(formatOverride) : DATE_FORMAT;
                parsed = LocalDate.parse(raw.toString(), fmt);
            } else if (targetType == LocalDateTime.class) {
                DateTimeFormatter fmt = formatOverride != null ? DateTimeFormatter.ofPattern(formatOverride) : DATETIME_FORMAT;
                parsed = LocalDateTime.parse(raw.toString(), fmt);
            } else if (targetType == LocalTime.class) {
                DateTimeFormatter fmt = formatOverride != null ? DateTimeFormatter.ofPattern(formatOverride) : TIME_FORMAT;
                parsed = LocalTime.parse(raw.toString(), fmt);
            } else if (targetType == Date.class) {
                DateTimeFormatter fmt = formatOverride != null ? DateTimeFormatter.ofPattern(formatOverride) : DATE_FORMAT;
                LocalDate date = LocalDate.parse(raw.toString(), fmt);
                parsed = Date.valueOf(date);
            } else {
                parsed = raw;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "❌ Failed to parse value '" + raw + "' as " + targetType.getSimpleName(), e
            );
        }

        return new TypedValue(parsed, sqlType);
    }


    private static Condition combineConditions(Condition first, Condition second, LogicalOperator logicalOperator) {
        return switch (logicalOperator) {
            case OR -> first.or(second);
            case AND -> first.and(second);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castToList(Object value, Field<?> field, String formatOverride) {
        if (field == null) throw new IllegalArgumentException("Missing field info for casting");

        DataType<?> dataType = field.getDataType();
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(v -> inferTypedValue(v, dataType, formatOverride).value())
                    .collect(Collectors.toList());
        } else {
            return List.of(inferTypedValue(value, dataType, formatOverride).value());
        }
    }

    private static DataType mapStringToSqlDataType(String type) {
        return switch (type.toUpperCase()) {
            case "STRING", "VARCHAR", "TEXT" -> SQLDataType.VARCHAR;
            case "INTEGER", "INT" -> SQLDataType.INTEGER;
            case "BIGINT", "LONG" -> SQLDataType.BIGINT;
            case "DECIMAL", "NUMERIC", "DOUBLE" -> SQLDataType.DECIMAL;
            case "BOOLEAN" -> SQLDataType.BOOLEAN;
            case "DATE" -> SQLDataType.LOCALDATE;
            case "TIME" -> SQLDataType.LOCALTIME;
            case "DATETIME", "TIMESTAMP" -> SQLDataType.LOCALDATETIME;
            case "UUID" -> SQLDataType.UUID;
            case "JSON" -> SQLDataType.JSON;
            case "JSONB" -> SQLDataType.JSONB;
            case "CHAR" -> SQLDataType.CHAR;
            default -> throw new IllegalArgumentException("Unsupported cast type: " + type);
        };
    }

    public static Field<Object> resolveFieldFromPath(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 2) {
            return DSL.field(DSL.name(parts[0], parts[1]));
        }
        throw new IllegalArgumentException("Invalid field path: " + path + ". Expected format 'table.column'");
    }


}

package com.platform.engine.filter;

import com.platform.engine.dto.Search;
import com.platform.engine.enums.FilterOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jooq.Field;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.platform.engine.enums.DataFilterOperator.*;

public class CriteriaFilterUtils {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static Predicate buildCombinedPredicate(CriteriaBuilder cb, Root<?> root, Map<String, Join<?, ?>> joins, List<Search> filters, Map<String, Field> columnFieldMap) {
        if (filters == null || filters.isEmpty()) return null;

        List<Predicate> predicates = new ArrayList<>();
        for (Search search : filters) {
            Path<?> path;
            if (search.getColumn().contains(".")) {
                String[] parts = search.getColumn().split("\\.");
                path = joins.get(parts[0]).get(parts[1]);
            } else {
                path = root.get(search.getColumn());
            }

            Class<?> targetType = columnFieldMap.containsKey(search.getColumn())
                    ? columnFieldMap.get(search.getColumn()).getType()
                    : path.getJavaType();
            Object value = convertValue(search.getValue(), targetType);

            FilterOperator operator = search.getFilterOperator();
            switch (operator) {
                case EQUALS -> {
                    if (value instanceof LocalDateTime dt) {
                        LocalDateTime truncated = dt.truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.between(path.as(LocalDateTime.class), truncated, upper));
                    } else {
                        predicates.add(cb.equal(path, value));
                    }
                }
                case NOT_EQUALS -> {
                    if (value instanceof LocalDateTime dt) {
                        LocalDateTime truncated = dt.truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.or(cb.lessThan(path.as(LocalDateTime.class), truncated),
                                             cb.greaterThan(path.as(LocalDateTime.class), upper)));
                    } else {
                        predicates.add(cb.notEqual(path, value));
                    }
                }
                case GREATER_THAN -> {
                    if (value instanceof LocalDateTime dt) {
                        predicates.add(cb.greaterThan(path.as(LocalDateTime.class), dt.truncatedTo(ChronoUnit.SECONDS)));
                    } else {
                        predicates.add(cb.greaterThan(path.as(Comparable.class), (Comparable) value));
                    }
                }
                case GREATER_THAN_EQUAL -> {
                    if (value instanceof LocalDateTime dt) {
                        predicates.add(cb.greaterThanOrEqualTo(path.as(LocalDateTime.class), dt.truncatedTo(ChronoUnit.SECONDS)));
                    } else {
                        predicates.add(cb.greaterThanOrEqualTo(path.as(Comparable.class), (Comparable) value));
                    }
                }
                case LESS_THAN -> {
                    if (value instanceof LocalDateTime dt) {
                        predicates.add(cb.lessThan(path.as(LocalDateTime.class), dt.truncatedTo(ChronoUnit.SECONDS)));
                    } else {
                        predicates.add(cb.lessThan(path.as(Comparable.class), (Comparable) value));
                    }
                }
                case LESS_THAN_EQUAL -> {
                    if (value instanceof LocalDateTime dt) {
                        predicates.add(cb.lessThanOrEqualTo(path.as(LocalDateTime.class), dt.truncatedTo(ChronoUnit.SECONDS)));
                    } else {
                        predicates.add(cb.lessThanOrEqualTo(path.as(Comparable.class), (Comparable) value));
                    }
                }
                case LIKE -> predicates.add(cb.like(path.as(String.class), "%" + value + "%"));
                case IN -> predicates.add(path.in((List<?>) value));
                case NOT_IN -> predicates.add(cb.not(path.in((List<?>) value)));
                case BETWEEN -> {
                    List<?> range = (List<?>) value;
                    Object lower = convertValue(range.get(0), targetType);
                    Object upper = convertValue(range.get(1), targetType);
                    predicates.add(cb.between(path.as(Comparable.class), (Comparable) lower, (Comparable) upper));
                }
                default -> {}
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private static Object convertValue(Object raw, Class<?> targetType) {
        if (raw == null) return null;

        try {
            if (targetType.isArray()) {
                Class<?> componentType = targetType.getComponentType();
                List<?> list = raw instanceof Collection ? (List<?>) raw : List.of(raw);
                Object array = Array.newInstance(componentType, list.size());
                for (int i = 0; i < list.size(); i++) {
                    Array.set(array, i, convertValue(list.get(i), componentType));
                }
                return array;
            }

            if (Collection.class.isAssignableFrom(targetType)) {
                Collection<Object> collection = targetType.isAssignableFrom(Set.class) ? new HashSet<>() : new ArrayList<>();
                if (raw instanceof Collection<?>) {
                    for (Object item : (Collection<?>) raw) {
                        collection.add(convertValue(item, Object.class));
                    }
                } else {
                    collection.add(convertValue(raw, Object.class));
                }
                return collection;
            }

            String str = raw.toString();
            if (targetType == String.class) return str;
            if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(str);
            if (targetType == Long.class || targetType == long.class) return Long.parseLong(str);
            if (targetType == Short.class || targetType == short.class) return Short.parseShort(str);
            if (targetType == Byte.class || targetType == byte.class) return Byte.parseByte(str);
            if (targetType == Float.class || targetType == float.class) return Float.parseFloat(str);
            if (targetType == Double.class || targetType == double.class) return Double.parseDouble(str);
            if (targetType == Boolean.class || targetType == boolean.class) return Boolean.parseBoolean(str);
            if (targetType == Character.class || targetType == char.class) return str.charAt(0);
            if (targetType == java.math.BigDecimal.class) return new java.math.BigDecimal(str);
            if (targetType == java.math.BigInteger.class) return new java.math.BigInteger(str);
            if (targetType == java.util.UUID.class) return UUID.fromString(str);
            if (targetType == LocalDate.class) return LocalDate.parse(str, DATE_FORMAT);
            if (targetType == LocalDateTime.class) return LocalDateTime.parse(str, DATETIME_FORMAT);
            if (targetType == LocalTime.class) return LocalTime.parse(str, TIME_FORMAT);
            if (targetType == java.util.Date.class) return java.sql.Timestamp.valueOf(LocalDateTime.parse(str, DATETIME_FORMAT));
            if (targetType.isEnum()) return Enum.valueOf((Class<Enum>) targetType, str);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert value: " + raw + " to type: " + targetType.getSimpleName(), e);
        }

        return raw;
    }
}

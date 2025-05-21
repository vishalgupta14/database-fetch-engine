package com.platform.engine.filter;

import com.platform.engine.dto.Search;
import com.platform.engine.enums.DataFilterOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CriteriaFilterUtils {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");


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

            DataFilterOperator operator = search.getFilterOperator();
            Class<?> finalTargetType = targetType;
            switch (operator) {
                case EQUALS -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (convertedValue instanceof LocalDateTime dt) {
                        LocalDateTime truncated = dt.truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.between(path.as(LocalDateTime.class), truncated, upper));
                    } else if (convertedValue instanceof LocalTime time) {
                        LocalTime truncated = time.truncatedTo(ChronoUnit.SECONDS);
                        LocalTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.between(path.as(LocalTime.class), truncated, upper));
                    } else if (convertedValue instanceof LocalDate date) {
                        LocalDateTime start = date.atStartOfDay().truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime end = start.plusDays(1);
                        predicates.add(cb.between(path.as(LocalDateTime.class), start, end));
                    } else if (convertedValue instanceof Date date) {
                        LocalDateTime truncated = new java.sql.Timestamp(date.getTime())
                                .toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.between(path.as(LocalDateTime.class), truncated, upper));
                    } else if (convertedValue instanceof Enum<?> enumValue) {
                        predicates.add(cb.equal(path.as(String.class), enumValue.name()));
                    } else {
                        predicates.add(cb.equal(path.as(targetType), convertedValue));
                    }
                }
                case NOT_EQUALS -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (convertedValue instanceof LocalDateTime dt) {
                        LocalDateTime truncated = dt.truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.or(
                                cb.lessThan(path.as(LocalDateTime.class), truncated),
                                cb.greaterThan(path.as(LocalDateTime.class), upper)
                        ));
                    } else if (convertedValue instanceof LocalTime time) {
                        LocalTime truncated = time.truncatedTo(ChronoUnit.SECONDS);
                        LocalTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.or(
                                cb.lessThan(path.as(LocalTime.class), truncated),
                                cb.greaterThan(path.as(LocalTime.class), upper)
                        ));
                    } else if (convertedValue instanceof LocalDate date) {
                        LocalDateTime start = date.atStartOfDay().truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime end = start.plusDays(1);
                        predicates.add(cb.or(
                                cb.lessThan(path.as(LocalDateTime.class), start),
                                cb.greaterThan(path.as(LocalDateTime.class), end)
                        ));
                    } else if (convertedValue instanceof Date date) {
                        LocalDateTime truncated = new java.sql.Timestamp(date.getTime())
                                .toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        LocalDateTime upper = truncated.plusSeconds(1);
                        predicates.add(cb.or(
                                cb.lessThan(path.as(LocalDateTime.class), truncated),
                                cb.greaterThan(path.as(LocalDateTime.class), upper)
                        ));
                    } else if (convertedValue instanceof Enum<?> enumValue) {
                        predicates.add(cb.notEqual(path.as(String.class), enumValue.name()));
                    } else {
                        predicates.add(cb.notEqual(path.as(targetType), convertedValue));
                    }
                }
                case GREATER_THAN -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (convertedValue instanceof LocalDateTime dt) {
                        convertedValue = dt.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalTime time) {
                        convertedValue = time.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalDate date) {
                        convertedValue = date.atStartOfDay().truncatedTo(ChronoUnit.SECONDS).toLocalDate();
                    } else if (convertedValue instanceof Date date) {
                        convertedValue = new java.sql.Timestamp(date.getTime()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        targetType = LocalDateTime.class;
                    } else if (convertedValue instanceof Enum<?> enumValue) {
                        convertedValue = enumValue.name();
                        targetType = String.class;
                    }

                    if (convertedValue instanceof Comparable && Comparable.class.isAssignableFrom(targetType)) {
                        predicates.add(cb.greaterThan(
                                path.as((Class<? extends Comparable>) targetType),
                                (Comparable) convertedValue
                        ));
                    } else {
                        throw new IllegalArgumentException("Unsupported type for GREATER_THAN: " + convertedValue.getClass());
                    }
                }
                case GREATER_THAN_EQUAL -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (convertedValue instanceof LocalDateTime dt) {
                        convertedValue = dt.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalTime time) {
                        convertedValue = time.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalDate date) {
                        convertedValue = date.atStartOfDay().truncatedTo(ChronoUnit.SECONDS).toLocalDate();
                    } else if (convertedValue instanceof Date date) {
                        convertedValue = new java.sql.Timestamp(date.getTime()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        targetType = LocalDateTime.class;
                    } else if (convertedValue instanceof Enum<?> enumValue) {
                        convertedValue = enumValue.name();
                        targetType = String.class;
                    }

                    if (convertedValue instanceof Comparable && Comparable.class.isAssignableFrom(targetType)) {
                        predicates.add(cb.greaterThanOrEqualTo(
                                path.as((Class<? extends Comparable>) targetType),
                                (Comparable) convertedValue
                        ));
                    } else {
                        throw new IllegalArgumentException("Unsupported type for GREATER_THAN_EQUAL: " + convertedValue.getClass());
                    }
                }
                case LESS_THAN -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (convertedValue instanceof LocalDateTime dt) {
                        convertedValue = dt.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalTime time) {
                        convertedValue = time.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalDate date) {
                        convertedValue = date.atStartOfDay().truncatedTo(ChronoUnit.SECONDS).toLocalDate();
                    } else if (convertedValue instanceof Date date) {
                        convertedValue = new java.sql.Timestamp(date.getTime()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        targetType = LocalDateTime.class;
                    } else if (convertedValue instanceof Enum<?> enumValue) {
                        convertedValue = enumValue.name();
                        targetType = String.class;
                    }

                    if (convertedValue instanceof Comparable && Comparable.class.isAssignableFrom(targetType)) {
                        predicates.add(cb.lessThan(
                                path.as((Class<? extends Comparable>) targetType),
                                (Comparable) convertedValue
                        ));
                    } else {
                        throw new IllegalArgumentException("Unsupported type for LESS_THAN: " + convertedValue.getClass());
                    }
                }
                case LESS_THAN_EQUAL -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (convertedValue instanceof LocalDateTime dt) {
                        convertedValue = dt.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalTime time) {
                        convertedValue = time.truncatedTo(ChronoUnit.SECONDS);
                    } else if (convertedValue instanceof LocalDate date) {
                        convertedValue = date.atStartOfDay().truncatedTo(ChronoUnit.SECONDS).toLocalDate();
                    } else if (convertedValue instanceof Date date) {
                        convertedValue = new java.sql.Timestamp(date.getTime()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        targetType = LocalDateTime.class;
                    } else if (convertedValue instanceof Enum<?> enumValue) {
                        convertedValue = enumValue.name();
                        targetType = String.class;
                    }

                    if (convertedValue instanceof Comparable && Comparable.class.isAssignableFrom(targetType)) {
                        predicates.add(cb.lessThanOrEqualTo(
                                path.as((Class<? extends Comparable>) targetType),
                                (Comparable) convertedValue
                        ));
                    } else {
                        throw new IllegalArgumentException("Unsupported type for LESS_THAN_EQUAL: " + convertedValue.getClass());
                    }
                }
                case LIKE -> {
                    Object convertedValue = convertValue(value, targetType);

                    if (!(convertedValue instanceof String str)) {
                        throw new IllegalArgumentException("LIKE operator requires a string value, but got: " + convertedValue.getClass());
                    }

                    predicates.add(cb.like(path.as(String.class), "%" + str.trim() + "%"));
                }

                case IN -> {
                    List<?> rawList = (List<?>) value;
                    List<Object> convertedList = rawList.stream()
                            .map(item -> convertValue(item, finalTargetType))
                            .collect(Collectors.toList());

                    predicates.add(path.as(targetType).in(convertedList));
                }

                case NOT_IN -> {
                    List<?> rawList = (List<?>) value;
                    List<Object> convertedList = rawList.stream()
                            .map(item -> convertValue(item, finalTargetType))
                            .collect(Collectors.toList());

                    predicates.add(cb.not(path.as(targetType).in(convertedList)));
                }
                case BETWEEN -> {
                    List<?> range = (List<?>) value;

                    if (range.size() != 2) {
                        throw new IllegalArgumentException("BETWEEN operator requires exactly two values.");
                    }

                    Object lower = convertValue(range.get(0), targetType);
                    Object upper = convertValue(range.get(1), targetType);

                    // Apply truncation and normalization
                    if (lower instanceof LocalDateTime ldt1 && upper instanceof LocalDateTime ldt2) {
                        lower = ldt1.truncatedTo(ChronoUnit.SECONDS);
                        upper = ldt2.truncatedTo(ChronoUnit.SECONDS);
                    } else if (lower instanceof LocalTime lt1 && upper instanceof LocalTime lt2) {
                        lower = lt1.truncatedTo(ChronoUnit.SECONDS);
                        upper = lt2.truncatedTo(ChronoUnit.SECONDS);
                    } else if (lower instanceof LocalDate ld1 && upper instanceof LocalDate ld2) {
                        lower = ld1.atStartOfDay().truncatedTo(ChronoUnit.SECONDS).toLocalDate();
                        upper = ld2.atStartOfDay().truncatedTo(ChronoUnit.SECONDS).toLocalDate();
                    } else if (lower instanceof Date d1 && upper instanceof Date d2) {
                        lower = new java.sql.Timestamp(d1.getTime()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        upper = new java.sql.Timestamp(d2.getTime()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
                        targetType = LocalDateTime.class;
                    } else if (lower instanceof Enum<?> enum1 && upper instanceof Enum<?> enum2) {
                        lower = enum1.name();
                        upper = enum2.name();
                        targetType = String.class;
                    }

                    if (lower instanceof Comparable && upper instanceof Comparable && Comparable.class.isAssignableFrom(targetType)) {
                        predicates.add(cb.between(
                                path.as((Class<? extends Comparable>) targetType),
                                (Comparable) lower,
                                (Comparable) upper
                        ));
                    } else {
                        throw new IllegalArgumentException("BETWEEN bounds must be Comparable: " + lower + ", " + upper);
                    }
                }
                default -> {}
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private static Object convertValue(Object raw, Class<?> targetType) {
        if (raw == null) return null;

        try {
            // Handle list to array
            if (targetType.isArray()) {
                Class<?> componentType = targetType.getComponentType();
                List<?> list = raw instanceof Collection ? (List<?>) raw : List.of(raw);
                Object array = Array.newInstance(componentType, list.size());
                for (int i = 0; i < list.size(); i++) {
                    Array.set(array, i, convertValue(list.get(i), componentType));
                }
                return array;
            }

            // Handle collections
            if (Collection.class.isAssignableFrom(targetType)) {
                Collection<Object> collection = targetType.isAssignableFrom(Set.class) ? new HashSet<>() : new ArrayList<>();
                if (raw instanceof Collection<?>) {
                    for (Object item : (Collection<?>) raw) {
                        collection.add(convertValue(item, Object.class)); // You may want a stricter type here
                    }
                } else {
                    collection.add(convertValue(raw, Object.class));
                }
                return collection;
            }

            // Fix: if raw is a list but targetType is scalar, just return list of converted values
            if (raw instanceof List<?> list && !Collection.class.isAssignableFrom(targetType) && !targetType.isArray()) {
                return list.stream()
                        .map(item -> convertValue(item, targetType))
                        .collect(Collectors.toList());
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
            if (targetType == LocalDate.class && DATE_PATTERN.matcher(str).matches()) {
                return LocalDate.parse(str, DATE_FORMAT);
            }
            if (targetType == LocalDateTime.class && DATETIME_PATTERN.matcher(str).matches()) {
                return LocalDateTime.parse(str, DATETIME_FORMAT);
            }
            if (targetType == LocalTime.class && TIME_PATTERN.matcher(str).matches()) {
                return LocalTime.parse(str, TIME_FORMAT);
            }
            if (targetType == java.util.Date.class && DATETIME_PATTERN.matcher(str).matches()) {
                return java.sql.Timestamp.valueOf(LocalDateTime.parse(str, DATETIME_FORMAT));
            }if (targetType.isEnum()) return Enum.valueOf((Class<Enum>) targetType, str);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert value: " + raw + " to type: " + targetType.getSimpleName(), e);
        }

        return raw;
    }
}

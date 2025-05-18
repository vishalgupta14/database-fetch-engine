package com.platform.engine.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.engine.dto.TypedValue;
import com.platform.engine.utils.UUIDUtils;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Utility for guessing SQLDataType from raw input strings.
 * Helps convert raw string values (typically from user input) into typed values
 * with the most appropriate jOOQ SQLDataType.
 */
public class SqlTypeGuesser {

    public static TypedValue convertValue(Object raw, Map<String, Field<?>> columnFieldMap) {
        if (!(raw instanceof String str)) {
            return new TypedValue(raw, SQLDataType.OTHER);
        }

        try {
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                return new TypedValue(Boolean.parseBoolean(str), SQLDataType.BOOLEAN);
            }

            // Try Integer types
            try {
                return new TypedValue(Byte.parseByte(str), SQLDataType.TINYINT);
            } catch (NumberFormatException ignored) {}

            try {
                return new TypedValue(Short.parseShort(str), SQLDataType.SMALLINT);
            } catch (NumberFormatException ignored) {}

            try {
                return new TypedValue(Integer.parseInt(str), SQLDataType.INTEGER);
            } catch (NumberFormatException ignored) {}

            try {
                return new TypedValue(Long.parseLong(str), SQLDataType.BIGINT);
            } catch (NumberFormatException ignored) {}

            try {
                return new TypedValue(new BigInteger(str), SQLDataType.DECIMAL_INTEGER);
            } catch (NumberFormatException ignored) {}

            // Floating point types
            try {
                return new TypedValue(Float.parseFloat(str), SQLDataType.REAL);
            } catch (NumberFormatException ignored) {}

            try {
                return new TypedValue(Double.parseDouble(str), SQLDataType.DOUBLE);
            } catch (NumberFormatException ignored) {}

            try {
                return new TypedValue(new BigDecimal(str), SQLDataType.DECIMAL);
            } catch (NumberFormatException ignored) {}

            // Date and time
            if (isSimpleDate(str)) {
                try {
                    return new TypedValue(LocalDate.parse(str, DATE_FORMAT), SQLDataType.LOCALDATE);
                } catch (Exception ignored) {}
            }

            if (isSimpleTime(str)) {
                try {
                    return new TypedValue(LocalTime.parse(str, TIME_FORMAT), SQLDataType.LOCALTIME);
                } catch (Exception ignored) {}
            }

            if (isSimpleDateTime(str)) {
                try {
                    return new TypedValue(LocalDateTime.parse(str, DATETIME_FORMAT), SQLDataType.LOCALDATETIME);
                } catch (Exception ignored) {}
            }

            // UUID
            if (UUIDUtils.isValidUUID(str)) {
                return new TypedValue(UUID.fromString(str), SQLDataType.UUID);
            }

            // JSON
            if (isJson(str)) {
                return new TypedValue(str, SQLDataType.JSONB);
            }

            // Default fallback
            return new TypedValue(raw, SQLDataType.OTHER);

        } catch (Exception e) {
            return new TypedValue(raw, SQLDataType.OTHER);
        }
    }

    private static boolean isJson(String str) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");



    private static boolean isSimpleDate(String str) {
        return str != null && str.length() == 10 &&
                str.charAt(4) == '-' && str.charAt(7) == '-';
    }

    private static boolean isSimpleTime(String str) {
        return str != null && str.length() == 8 &&
                str.charAt(2) == ':' && str.charAt(5) == ':';
    }


    private static boolean isSimpleDateTime(String str) {
        return str != null && str.length() == 19 &&
                str.charAt(4) == '-' && str.charAt(7) == '-' && str.charAt(10) == 'T';
    }

    private SqlTypeGuesser() {}
}

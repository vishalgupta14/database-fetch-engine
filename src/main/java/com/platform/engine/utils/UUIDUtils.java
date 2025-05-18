package com.platform.engine.utils;

import java.util.UUID;

public class UUIDUtils {
    public static boolean isValidUUID(String value) {
        if (value == null) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

package com.platform.engine.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean isJson(String str) {
        try {
            JsonNode node = objectMapper.readTree(str);
            return node.isObject() || node.isArray();
        } catch (Exception e) {
            return false;
        }
    }

}

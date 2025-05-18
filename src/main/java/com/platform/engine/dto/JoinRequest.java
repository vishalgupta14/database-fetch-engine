package com.platform.engine.dto;

import lombok.Data;

import java.util.List;

@Data
public class JoinRequest {
    private String joinType;      // INNER, LEFT, RIGHT
    private String table;         // The table to join
    private List<String> onLeft;  // e.g. ["users.id", "users.tenant_id"]
    private List<String> onRight; // e.g. ["orders.user_id", "orders.tenant_id"]
    private String alias;
}

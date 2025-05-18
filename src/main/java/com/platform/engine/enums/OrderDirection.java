package com.platform.engine.enums;

public enum OrderDirection {
    ASC,
    DESC;

    public boolean isAsc() {
        return this == ASC;
    }

    public boolean isDesc() {
        return this == DESC;
    }
}

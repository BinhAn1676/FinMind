package com.finance.financeservice.constant;

import lombok.Data;
import lombok.Getter;

@Getter
public enum AccountStatus {
    ACTIVE("1"),   // Can manage room, invite/remove members
    LOCKED("0");

    private final String code;

    AccountStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public static AccountStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AccountStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AccountStatus code: " + code);
    }
}
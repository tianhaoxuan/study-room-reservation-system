package com.smartstudy.studyroom.common;

import java.util.Locale;

public enum UserRole {

    USER,
    ADMIN;

    public static UserRole from(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

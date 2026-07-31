package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.UserRole;

public record AuthenticatedUser(Long userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}

package com.dfwl.fleet.system.dto.response;

import java.time.LocalDateTime;

public record UserResponse(
        long id,
        String phone,
        long roleId,
        String roleCode,
        String roleName,
        int status,
        LocalDateTime lastLoginAt,
        Long createdBy,
        LocalDateTime createdAt,
        Long updatedBy,
        LocalDateTime updatedAt
) {
}

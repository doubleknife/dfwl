package com.dfwl.fleet.auth.domain;

import java.time.LocalDateTime;
import java.util.Set;

public record UserAccount(
        Long id,
        String phone,
        String passwordHash,
        Long roleId,
        String roleCode,
        String roleName,
        int status,
        LocalDateTime lastLoginAt,
        Set<String> permissions
) {
    public boolean enabled() {
        return status == 1;
    }
}

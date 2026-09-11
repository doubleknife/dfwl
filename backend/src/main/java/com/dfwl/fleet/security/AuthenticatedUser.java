package com.dfwl.fleet.security;

import java.util.Set;

public record AuthenticatedUser(
        Long id,
        String phone,
        Long roleId,
        String roleCode,
        String roleName,
        Set<String> permissions
) {
}

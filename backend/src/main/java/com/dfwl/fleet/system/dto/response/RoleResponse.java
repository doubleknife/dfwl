package com.dfwl.fleet.system.dto.response;

import java.util.List;

public record RoleResponse(
        long id,
        String roleCode,
        String roleName,
        boolean systemFixed,
        int status,
        List<PermissionResponse> permissions
) {
}

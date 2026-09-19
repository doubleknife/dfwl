package com.dfwl.fleet.system.dto.response;

public record PermissionResponse(
        long id,
        String permissionCode,
        String permissionName,
        String permissionType,
        int status
) {
}

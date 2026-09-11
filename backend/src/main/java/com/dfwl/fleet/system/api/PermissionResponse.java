package com.dfwl.fleet.system.api;

public record PermissionResponse(
        long id,
        String permissionCode,
        String permissionName,
        String permissionType,
        int status
) {
}

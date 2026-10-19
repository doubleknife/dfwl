package com.dfwl.fleet.system.dto.response;

public record PermissionResponse(
        long id,
        String permissionCode,
        String permissionName,
        String permissionType,
        int status,
        String domain,
        String group,
        String pageKey,
        String parentCode,
        String dataScope
) {
    public PermissionResponse(long id, String permissionCode, String permissionName, String permissionType, int status) {
        this(id, permissionCode, permissionName, permissionType, status, null, null, null, null, null);
    }
}

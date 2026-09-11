package com.dfwl.fleet.system.api;

import java.util.List;

public record RolePermissionRequest(
        List<String> permissionCodes
) {
}

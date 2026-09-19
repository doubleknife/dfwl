package com.dfwl.fleet.system.dto.request;

import java.util.List;

public record RolePermissionRequest(
        List<String> permissionCodes
) {
}

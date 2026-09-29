package com.dfwl.fleet.master.dto.response;

public record ProductResponse(
        long id,
        String name,
        boolean enabled,
        int version
) {
}

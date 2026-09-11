package com.dfwl.fleet.master.api;

public record ProductResponse(
        long id,
        String productCode,
        String productName,
        int status,
        String remark
) {
}

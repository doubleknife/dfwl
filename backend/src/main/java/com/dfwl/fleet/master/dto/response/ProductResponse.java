package com.dfwl.fleet.master.dto.response;

public record ProductResponse(
        long id,
        String productCode,
        String productName,
        int status,
        String remark
) {
}

package com.dfwl.fleet.master.dto.response;

public record CustomerResponse(
        long id,
        String customerCode,
        String customerName,
        int status,
        String remark
) {
}

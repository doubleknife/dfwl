package com.dfwl.fleet.master.api;

public record CustomerResponse(
        long id,
        String customerCode,
        String customerName,
        int status,
        String remark
) {
}

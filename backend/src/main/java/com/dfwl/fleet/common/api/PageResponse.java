package com.dfwl.fleet.common.api;

import java.util.List;

public record PageResponse<T>(
        long pageNo,
        long pageSize,
        long total,
        List<T> records
) {
}


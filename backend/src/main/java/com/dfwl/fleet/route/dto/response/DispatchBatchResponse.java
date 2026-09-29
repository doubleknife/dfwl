package com.dfwl.fleet.route.dto.response;

import java.util.List;

public record DispatchBatchResponse(long batchId, String requestKey, String status, List<Row> rows) {
    public record Row(int rowNo, long taskId, String taskNo) {}
}

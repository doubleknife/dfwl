package com.dfwl.fleet.approval.api;

public record ApprovalFlowNodeResponse(
        long id,
        long flowId,
        int nodeOrder,
        String nodeName,
        Long positionId,
        long approverUserId,
        boolean allowReturn
) {
}

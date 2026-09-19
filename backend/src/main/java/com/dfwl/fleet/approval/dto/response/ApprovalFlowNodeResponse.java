package com.dfwl.fleet.approval.dto.response;

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

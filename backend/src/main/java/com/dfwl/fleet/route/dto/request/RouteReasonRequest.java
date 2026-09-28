package com.dfwl.fleet.route.dto.request;

public record RouteReasonRequest(
        String reason,
        Boolean acknowledgeBusinessImpact
) {
    public RouteReasonRequest(String reason) {
        this(reason, null);
    }
}

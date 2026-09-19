package com.dfwl.fleet.auth.dto.response;

public record LoginResponse(
        String tokenType,
        String accessToken,
        CurrentUserResponse user
) {
}

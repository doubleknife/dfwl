package com.dfwl.fleet.auth.api;

public record LoginResponse(
        String tokenType,
        String accessToken,
        CurrentUserResponse user
) {
}

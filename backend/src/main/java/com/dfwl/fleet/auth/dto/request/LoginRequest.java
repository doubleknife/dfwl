package com.dfwl.fleet.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank String phone,
        @NotBlank String password,
        @Pattern(regexp = "WEB|MINI") String terminal
) {
    public LoginRequest(String phone, String password) { this(phone, password, "WEB"); }

    public String effectiveTerminal() { return terminal == null ? "WEB" : terminal; }
}

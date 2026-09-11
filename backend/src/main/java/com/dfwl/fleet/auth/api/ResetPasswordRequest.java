package com.dfwl.fleet.auth.api;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String newPassword
) {
}

package com.dfwl.fleet.tire.api;

import jakarta.validation.constraints.NotBlank;

public record OcrConfirmRequest(@NotBlank String confirmedText) {
}

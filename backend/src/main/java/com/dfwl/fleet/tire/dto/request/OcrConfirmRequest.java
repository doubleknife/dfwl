package com.dfwl.fleet.tire.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OcrConfirmRequest(@NotBlank String confirmedText) {
}

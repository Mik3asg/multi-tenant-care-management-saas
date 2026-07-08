package com.meridian.care.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCareLogRequest(
        @NotBlank @Size(max = 64) String category,
        @NotBlank @Size(max = 4000) String note) {
}

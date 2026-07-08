package com.meridian.care.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCareLogRequest(
        @NotBlank String category,
        @NotBlank String note
) {}

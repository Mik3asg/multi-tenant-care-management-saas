package com.meridian.care.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateResidentRequest(
        @NotBlank String fullName,
        @NotNull LocalDate dateOfBirth,
        String room
) {}

package com.meridian.care.web.dto;

import com.meridian.care.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStaffRequest(
        @NotBlank String displayName,
        @Email @NotBlank String email,
        @NotNull Role role
) {}

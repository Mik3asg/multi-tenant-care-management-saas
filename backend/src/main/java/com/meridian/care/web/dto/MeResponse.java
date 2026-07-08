package com.meridian.care.web.dto;

import com.meridian.care.security.AppUserPrincipal;

import java.util.UUID;

public record MeResponse(
        UUID userId,
        String email,
        String displayName,
        String role,
        CareHomeDto careHome) {

    public record CareHomeDto(UUID id, String name, String slug) {
    }

    public static MeResponse from(AppUserPrincipal p) {
        return new MeResponse(
                p.getUserId(),
                p.getUsername(),
                p.getDisplayName(),
                p.getRole().name(),
                new CareHomeDto(p.getCareHomeId(), p.getCareHomeName(), p.getCareHomeSlug()));
    }
}

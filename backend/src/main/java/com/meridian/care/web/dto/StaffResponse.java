package com.meridian.care.web.dto;

import com.meridian.care.domain.AppUser;
import com.meridian.care.domain.Role;

import java.util.UUID;

public record StaffResponse(
        UUID id,
        String displayName,
        String email,
        Role role,
        boolean linked
) {
    // linked = true means the user has an auth0_sub and can log in
    public static StaffResponse from(AppUser u) {
        return new StaffResponse(
                u.getId(),
                u.getDisplayName(),
                u.getEmail(),
                u.getRole(),
                u.getAuth0Sub() != null);
    }
}

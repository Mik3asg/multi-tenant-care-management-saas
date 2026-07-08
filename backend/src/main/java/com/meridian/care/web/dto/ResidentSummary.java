package com.meridian.care.web.dto;

import com.meridian.care.domain.Resident;

import java.time.LocalDate;
import java.util.UUID;

public record ResidentSummary(
        UUID id,
        String fullName,
        LocalDate dateOfBirth,
        String room) {

    public static ResidentSummary from(Resident r) {
        return new ResidentSummary(r.getId(), r.getFullName(), r.getDateOfBirth(), r.getRoom());
    }
}

package com.meridian.care.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResidentDetail(
        UUID id,
        String fullName,
        LocalDate dateOfBirth,
        String room,
        List<CareLogDto> careLog) {
}

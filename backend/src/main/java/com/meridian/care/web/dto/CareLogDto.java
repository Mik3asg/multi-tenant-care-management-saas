package com.meridian.care.web.dto;

import com.meridian.care.domain.CareLogEntry;

import java.time.Instant;
import java.util.UUID;

public record CareLogDto(
        UUID id,
        Instant createdAt,
        String category,
        String note,
        String authorName) {

    public static CareLogDto from(CareLogEntry e, String authorName) {
        return new CareLogDto(e.getId(), e.getCreatedAt(), e.getCategory(), e.getNote(), authorName);
    }
}

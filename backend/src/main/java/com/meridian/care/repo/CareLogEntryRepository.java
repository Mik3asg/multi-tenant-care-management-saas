package com.meridian.care.repo;

import com.meridian.care.domain.CareLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareLogEntryRepository extends JpaRepository<CareLogEntry, UUID> {

    List<CareLogEntry> findByResidentIdAndCareHomeIdOrderByCreatedAtDesc(UUID residentId, UUID careHomeId);

    // Tenant-scoped lookup — returns empty if the entry belongs to a different care home
    Optional<CareLogEntry> findByIdAndCareHomeId(UUID id, UUID careHomeId);
}

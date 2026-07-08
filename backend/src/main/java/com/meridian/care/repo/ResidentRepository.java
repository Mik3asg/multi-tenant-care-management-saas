package com.meridian.care.repo;

import com.meridian.care.domain.Resident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResidentRepository extends JpaRepository<Resident, UUID> {

    // Tenant-scoped reads. Every lookup carries care_home_id so a record
    // belonging to another tenant is simply "not found".
    List<Resident> findByCareHomeIdOrderByFullNameAsc(UUID careHomeId);

    Optional<Resident> findByIdAndCareHomeId(UUID id, UUID careHomeId);
}

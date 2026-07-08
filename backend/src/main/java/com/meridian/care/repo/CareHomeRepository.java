package com.meridian.care.repo;

import com.meridian.care.domain.CareHome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CareHomeRepository extends JpaRepository<CareHome, UUID> {
    Optional<CareHome> findBySlug(String slug);
}

package com.meridian.care.repo;

import com.meridian.care.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);

    // Looks up a user by their Auth0 subject claim — used at OIDC login to resolve care_home_id
    Optional<AppUser> findByAuth0Sub(String auth0Sub);

    // Returns all staff members for a given care home — used by StaffService
    List<AppUser> findByCareHomeId(UUID careHomeId);
}

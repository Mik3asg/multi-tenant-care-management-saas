package com.meridian.care.service;

import com.meridian.care.domain.AppUser;
import com.meridian.care.domain.CareHome;
import com.meridian.care.repo.AppUserRepository;
import com.meridian.care.repo.CareHomeRepository;
import com.meridian.care.security.Auth0ManagementService;
import com.meridian.care.web.NotFoundException;
import com.meridian.care.web.dto.CreateStaffRequest;
import com.meridian.care.web.dto.StaffResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private final AppUserRepository users;
    private final CareHomeRepository careHomes;
    private final Auth0ManagementService auth0;

    public StaffService(AppUserRepository users, CareHomeRepository careHomes, Auth0ManagementService auth0) {
        this.users = users;
        this.careHomes = careHomes;
        this.auth0 = auth0;
    }

    // Returns all staff members belonging to the given care home
    public List<StaffResponse> list(UUID careHomeId) {
        return users.findByCareHomeId(careHomeId).stream()
                .map(StaffResponse::from)
                .toList();
    }

    @Transactional
    public StaffResponse create(UUID careHomeId, CreateStaffRequest req) {
        CareHome careHome = careHomes.findById(careHomeId)
                .orElseThrow(() -> new NotFoundException("Care home not found"));

        // Create the user in Auth0 first — they receive a "set your password" email
        String auth0Sub = auth0.createUser(req.email(), req.displayName());

        // Create the local app_user row linked to the care home and the Auth0 sub
        AppUser user = new AppUser(careHome, req.email(), "", req.displayName(), req.role());
        user.setAuth0Sub(auth0Sub);
        return StaffResponse.from(users.save(user));
    }
}

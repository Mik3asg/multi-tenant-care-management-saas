package com.meridian.care.web;

import com.meridian.care.security.AppUserPrincipal;
import com.meridian.care.service.StaffService;
import com.meridian.care.web.dto.CreateStaffRequest;
import com.meridian.care.web.dto.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    // Any authenticated user can list staff in their care home
    @GetMapping
    public List<StaffResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return staffService.list(principal.getCareHomeId());
    }

    // Only admins can create new staff members
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffResponse create(@AuthenticationPrincipal AppUserPrincipal principal,
                                @Valid @RequestBody CreateStaffRequest request) {
        return staffService.create(principal.getCareHomeId(), request);
    }
}

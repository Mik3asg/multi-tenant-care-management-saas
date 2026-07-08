package com.meridian.care.web;

import com.meridian.care.security.AppUserPrincipal;
import com.meridian.care.service.ResidentService;
import com.meridian.care.web.dto.CareLogDto;
import com.meridian.care.web.dto.CreateCareLogRequest;
import com.meridian.care.web.dto.CreateResidentRequest;
import com.meridian.care.web.dto.ResidentDetail;
import com.meridian.care.web.dto.ResidentSummary;
import com.meridian.care.web.dto.UpdateCareLogRequest;
import com.meridian.care.web.dto.UpdateResidentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/residents")
public class ResidentController {

    private final ResidentService residents;

    public ResidentController(ResidentService residents) {
        this.residents = residents;
    }

    @GetMapping
    public List<ResidentSummary> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return residents.list(principal.getCareHomeId());
    }

    // Admin only — add a new resident to this care home
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ResidentSummary create(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @Valid @RequestBody CreateResidentRequest request) {
        return residents.create(principal.getCareHomeId(), request);
    }

    // Admin only — edit a resident's details
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResidentSummary update(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateResidentRequest request) {
        return residents.update(principal.getCareHomeId(), id, request);
    }

    // Admin only — delete a resident and all their care log entries
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal,
                       @PathVariable UUID id) {
        residents.delete(principal.getCareHomeId(), id);
    }

    @GetMapping("/{id}")
    public ResidentDetail get(@AuthenticationPrincipal AppUserPrincipal principal,
                              @PathVariable UUID id) {
        return residents.detail(principal.getCareHomeId(), id);
    }

    @PostMapping("/{id}/care-log")
    @ResponseStatus(HttpStatus.CREATED)
    public CareLogDto addCareLog(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody CreateCareLogRequest request) {
        return residents.addLog(principal.getCareHomeId(), principal.getUserId(), id, request);
    }

    // Admin only — edit category and note of an existing care log entry
    @PutMapping("/{residentId}/care-log/{logId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CareLogDto editCareLog(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable UUID residentId,
                                  @PathVariable UUID logId,
                                  @Valid @RequestBody UpdateCareLogRequest request) {
        return residents.editLog(principal.getCareHomeId(), logId, request);
    }

    // Admin only — delete a care log entry
    @DeleteMapping("/{residentId}/care-log/{logId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCareLog(@AuthenticationPrincipal AppUserPrincipal principal,
                               @PathVariable UUID residentId,
                               @PathVariable UUID logId) {
        residents.deleteLog(principal.getCareHomeId(), logId);
    }
}

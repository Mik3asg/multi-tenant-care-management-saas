package com.meridian.care.service;

import com.meridian.care.domain.AppUser;
import com.meridian.care.domain.CareLogEntry;
import com.meridian.care.domain.Resident;
import com.meridian.care.repo.AppUserRepository;
import com.meridian.care.repo.CareLogEntryRepository;
import com.meridian.care.repo.ResidentRepository;
import com.meridian.care.web.NotFoundException;
import com.meridian.care.web.dto.CareLogDto;
import com.meridian.care.web.dto.CreateCareLogRequest;
import com.meridian.care.web.dto.CreateResidentRequest;
import com.meridian.care.web.dto.ResidentDetail;
import com.meridian.care.web.dto.ResidentSummary;
import com.meridian.care.web.dto.UpdateCareLogRequest;
import com.meridian.care.web.dto.UpdateResidentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * All reads and writes are scoped by careHomeId, which is always supplied
 * by the controller from the authenticated principal — never from the client.
 */
@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository residents;
    private final CareLogEntryRepository careLog;
    private final AppUserRepository users;

    public ResidentService(ResidentRepository residents, CareLogEntryRepository careLog, AppUserRepository users) {
        this.residents = residents;
        this.careLog = careLog;
        this.users = users;
    }

    public List<ResidentSummary> list(UUID careHomeId) {
        return residents.findByCareHomeIdOrderByFullNameAsc(careHomeId).stream()
                .map(ResidentSummary::from)
                .toList();
    }

    public ResidentDetail detail(UUID careHomeId, UUID residentId) {
        Resident resident = requireResident(careHomeId, residentId);
        List<CareLogEntry> entries =
                careLog.findByResidentIdAndCareHomeIdOrderByCreatedAtDesc(residentId, careHomeId);
        Map<UUID, String> authorNames = authorNames(entries);
        List<CareLogDto> log = entries.stream()
                .map(e -> CareLogDto.from(e, authorNames.getOrDefault(e.getAuthorUserId(), "Unknown")))
                .toList();
        return new ResidentDetail(
                resident.getId(),
                resident.getFullName(),
                resident.getDateOfBirth(),
                resident.getRoom(),
                log);
    }

    @Transactional
    public ResidentSummary create(UUID careHomeId, CreateResidentRequest req) {
        Resident resident = new Resident(careHomeId, req.fullName(), req.dateOfBirth(), req.room());
        return ResidentSummary.from(residents.save(resident));
    }

    @Transactional
    public ResidentSummary update(UUID careHomeId, UUID residentId, UpdateResidentRequest req) {
        Resident resident = requireResident(careHomeId, residentId);
        resident.setFullName(req.fullName());
        resident.setDateOfBirth(req.dateOfBirth());
        resident.setRoom(req.room());
        return ResidentSummary.from(residents.save(resident));
    }

    @Transactional
    public void delete(UUID careHomeId, UUID residentId) {
        Resident resident = requireResident(careHomeId, residentId);
        careLog.deleteAll(
                careLog.findByResidentIdAndCareHomeIdOrderByCreatedAtDesc(residentId, careHomeId));
        residents.delete(resident);
    }

    @Transactional
    public CareLogDto addLog(UUID careHomeId, UUID authorUserId, UUID residentId, CreateCareLogRequest req) {
        // Confirms the resident belongs to this tenant before writing.
        requireResident(careHomeId, residentId);
        CareLogEntry entry = new CareLogEntry(
                careHomeId,
                residentId,
                authorUserId,
                Instant.now(),
                req.category(),
                req.note());
        CareLogEntry saved = careLog.save(entry);
        String authorName = users.findById(authorUserId)
                .map(AppUser::getDisplayName)
                .orElse("Unknown");
        return CareLogDto.from(saved, authorName);
    }

    @Transactional
    public CareLogDto editLog(UUID careHomeId, UUID logId, UpdateCareLogRequest req) {
        CareLogEntry entry = careLog.findByIdAndCareHomeId(logId, careHomeId)
                .orElseThrow(() -> new NotFoundException("Care log entry not found"));
        entry.setCategory(req.category());
        entry.setNote(req.note());
        CareLogEntry saved = careLog.save(entry);
        String authorName = users.findById(saved.getAuthorUserId())
                .map(AppUser::getDisplayName)
                .orElse("Unknown");
        return CareLogDto.from(saved, authorName);
    }

    @Transactional
    public void deleteLog(UUID careHomeId, UUID logId) {
        CareLogEntry entry = careLog.findByIdAndCareHomeId(logId, careHomeId)
                .orElseThrow(() -> new NotFoundException("Care log entry not found"));
        careLog.delete(entry);
    }

    private Resident requireResident(UUID careHomeId, UUID residentId) {
        return residents.findByIdAndCareHomeId(residentId, careHomeId)
                .orElseThrow(() -> new NotFoundException("Resident not found"));
    }

    private Map<UUID, String> authorNames(List<CareLogEntry> entries) {
        Set<UUID> authorIds = entries.stream()
                .map(CareLogEntry::getAuthorUserId)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (a, b) -> a));
    }
}

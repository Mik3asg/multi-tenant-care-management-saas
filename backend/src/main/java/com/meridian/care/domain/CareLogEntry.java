package com.meridian.care.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "care_log_entry")
public class CareLogEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "care_home_id", nullable = false)
    private UUID careHomeId;

    @Column(name = "resident_id", nullable = false)
    private UUID residentId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 4000)
    private String note;

    protected CareLogEntry() {
    }

    public CareLogEntry(UUID careHomeId, UUID residentId, UUID authorUserId,
                        Instant createdAt, String category, String note) {
        this.careHomeId = careHomeId;
        this.residentId = residentId;
        this.authorUserId = authorUserId;
        this.createdAt = createdAt;
        this.category = category;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCareHomeId() {
        return careHomeId;
    }

    public UUID getResidentId() {
        return residentId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

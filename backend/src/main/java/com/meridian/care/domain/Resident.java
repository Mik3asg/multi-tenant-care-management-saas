package com.meridian.care.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "resident")
public class Resident {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "care_home_id", nullable = false)
    private UUID careHomeId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column
    private String room;

    protected Resident() {
    }

    public Resident(UUID careHomeId, String fullName, LocalDate dateOfBirth, String room) {
        this.careHomeId = careHomeId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.room = room;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCareHomeId() {
        return careHomeId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getRoom() {
        return room;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}

package com.meridian.care.config;

import com.meridian.care.domain.AppUser;
import com.meridian.care.domain.CareHome;
import com.meridian.care.domain.CareLogEntry;
import com.meridian.care.domain.Resident;
import com.meridian.care.domain.Role;
import com.meridian.care.repo.AppUserRepository;
import com.meridian.care.repo.CareHomeRepository;
import com.meridian.care.repo.CareLogEntryRepository;
import com.meridian.care.repo.ResidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Idempotent seeding: only runs when the database is empty, so IDs stay
 * stable across restarts. Login credentials are printed on every startup.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Shared demo password for all seeded accounts. */
    static final String DEMO_PASSWORD = "Password123!";

    private final CareHomeRepository careHomes;
    private final AppUserRepository users;
    private final ResidentRepository residents;
    private final CareLogEntryRepository careLog;
    private final PasswordEncoder encoder;

    public DataSeeder(CareHomeRepository careHomes,
                      AppUserRepository users,
                      ResidentRepository residents,
                      CareLogEntryRepository careLog,
                      PasswordEncoder encoder) {
        this.careHomes = careHomes;
        this.users = users;
        this.residents = residents;
        this.careLog = careLog;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (careHomes.count() == 0) {
            seedHome("Jupiter House", "care-home-jupiter");
            seedHome("Mars Lodge", "care-home-mars");
        }
        printCredentials();
    }

    private void seedHome(String name, String slug) {
        CareHome home = careHomes.save(new CareHome(name, slug));
        UUID homeId = home.getId();

        AppUser admin = users.save(new AppUser(
                home, slug + ".admin@example.com",
                encoder.encode(DEMO_PASSWORD),
                name + " Admin", Role.ADMIN));
        AppUser carer = users.save(new AppUser(
                home, slug + ".carer@example.com",
                encoder.encode(DEMO_PASSWORD),
                name + " Carer", Role.CARER));

        Resident r1 = residents.save(new Resident(
                homeId, name + " — Alice Bennett", LocalDate.of(1939, 3, 14), "101"));
        Resident r2 = residents.save(new Resident(
                homeId, name + " — Frank Osei", LocalDate.of(1945, 11, 2), "102"));

        Instant now = Instant.now();
        careLog.save(new CareLogEntry(homeId, r1.getId(), carer.getId(),
                now.minus(6, ChronoUnit.HOURS), "MEDICATION",
                "Morning medication administered and tolerated well."));
        careLog.save(new CareLogEntry(homeId, r1.getId(), carer.getId(),
                now.minus(3, ChronoUnit.HOURS), "MEAL",
                "Ate most of lunch, good fluid intake."));
        careLog.save(new CareLogEntry(homeId, r2.getId(), admin.getId(),
                now.minus(5, ChronoUnit.HOURS), "OBSERVATION",
                "Mobility steady with frame, no falls overnight."));

        log.info("Seeded care home '{}' ({}) with 2 users, 2 residents, 3 log entries.", name, slug);
    }

    private void printCredentials() {
        log.info("");
        log.info("==================== SEEDED LOGIN CREDENTIALS ====================");
        log.info("  All accounts use password: {}", DEMO_PASSWORD);
        log.info("  ---------------------------------------------------------------");
        log.info("  Jupiter House (care-home-jupiter)");
        log.info("    ADMIN  -> care-home-jupiter.admin@example.com");
        log.info("    CARER  -> care-home-jupiter.carer@example.com");
        log.info("  Mars Lodge (care-home-mars)");
        log.info("    ADMIN  -> care-home-mars.admin@example.com");
        log.info("    CARER  -> care-home-mars.carer@example.com");
        log.info("=================================================================");
        log.info("");
    }
}

package com.meridian.care;

import com.meridian.care.domain.CareHome;
import com.meridian.care.domain.Resident;
import com.meridian.care.repo.CareHomeRepository;
import com.meridian.care.repo.ResidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the core multi-tenancy boundary end-to-end (controller + Spring
 * Security + repository scoping) against real Postgres and Redis containers.
 *
 * Requires Docker to be available on the machine running the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TenantIsolationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("care")
                    .withUsername("care")
                    .withPassword("care");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CareHomeRepository careHomes;

    @Autowired
    ResidentRepository residents;

    private UUID residentIdInHome(String slug) {
        CareHome home = careHomes.findBySlug(slug).orElseThrow();
        Resident resident = residents.findByCareHomeIdOrderByFullNameAsc(home.getId())
                .stream().findFirst().orElseThrow();
        return resident.getId();
    }

    @Test
    @WithUserDetails("care-home-jupiter.carer@example.com")
    void jupiterUserCannotReadMarsResident() throws Exception {
        UUID marsResidentId = residentIdInHome("care-home-mars");

        // Cross-tenant access returns 404 — we never reveal the record exists.
        mockMvc.perform(get("/api/residents/{id}", marsResidentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("care-home-jupiter.carer@example.com")
    void jupiterUserCanReadOwnResident() throws Exception {
        UUID jupiterResidentId = residentIdInHome("care-home-jupiter");

        mockMvc.perform(get("/api/residents/{id}", jupiterResidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jupiterResidentId.toString()));
    }

    @Test
    @WithUserDetails("care-home-jupiter.carer@example.com")
    void residentListIsScopedToOwnHome() throws Exception {
        // Total seeded residents = 4 (2 per home). Seeing exactly 2, all belonging
        // to Jupiter, proves the list is tenant-scoped.
        mockMvc.perform(get("/api/residents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].fullName", everyItem(containsString("Jupiter House"))));
    }
}

package com.meridian.care.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies Postgres Row-Level Security policies to tenant-scoped tables on
 * every startup. All statements are idempotent: ENABLE/FORCE RLS is a no-op
 * if already set; the DO block only creates the policy if it does not exist.
 *
 * Fires after ApplicationReadyEvent so DataSeeder has already run.
 * On a fresh database this means seeding happens before RLS is active,
 * avoiding a chicken-and-egg problem with the NULL-bypass policy.
 *
 * The NULL-bypass clause (GUC not set → all rows visible) covers the rare
 * case where a read happens without an authenticated principal, e.g. internal
 * admin queries. This is documented as a known trade-off in ADR 0002.
 */
@Component
public class RlsInitializer {

    private static final String[] TENANT_TABLES = {"resident", "care_log_entry"};

    private static final String CREATE_POLICY_SQL = """
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_policies
                             WHERE policyname = 'tenant_isolation'
                             AND   tablename  = '%s') THEN
                CREATE POLICY tenant_isolation ON %s
                  USING (
                    nullif(current_setting('app.care_home_id', true), '') IS NULL
                    OR care_home_id = nullif(current_setting('app.care_home_id', true), '')::uuid
                  );
              END IF;
            END $$
            """;

    private final JdbcTemplate jdbc;

    public RlsInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyRls() {
        for (String table : TENANT_TABLES) {
            jdbc.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
            jdbc.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
            jdbc.execute(CREATE_POLICY_SQL.formatted(table, table));
        }
    }
}

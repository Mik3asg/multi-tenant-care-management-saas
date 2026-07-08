package com.meridian.care.config;

import com.meridian.care.security.AppUserPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Sets the Postgres GUC app.care_home_id at the start of every @Service
 * transaction. This activates the RLS policies on resident and care_log_entry.
 *
 * Order 1 runs just inside the @EnableTransactionManagement(order = 0)
 * interceptor, so the transaction is already open when the GUC is set.
 * set_config(..., true) means LOCAL: the GUC resets automatically when the
 * transaction ends — no manual cleanup needed.
 *
 * DataSeeder is @Component, not @Service, so it is not intercepted.
 * The RLS NULL-bypass policy handles that case at the DB layer.
 */
@Aspect
@Component
@Order(1)
public class TenantGucAspect {

    private final JdbcTemplate jdbc;

    public TenantGucAspect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Before("@within(org.springframework.stereotype.Service)")
    public void applyTenantGuc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal p)) return;
        jdbc.queryForObject(
                "SELECT set_config('app.care_home_id', ?, true)",
                String.class,
                p.getCareHomeId().toString());
    }
}

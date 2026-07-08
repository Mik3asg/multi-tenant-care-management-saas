package com.meridian.care.security;

import com.meridian.care.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The authenticated principal stored in the Redis session.
 * Implements OidcUser so Spring accepts it from Auth0OidcUserService.
 * Also implements UserDetails so AppUserDetailsService (used in tests) still works.
 *
 * oidcDelegate is transient — excluded from Redis serialisation.
 * The OIDC token methods are only needed during the auth flow, not on subsequent requests.
 */
public class AppUserPrincipal implements OidcUser, UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    // Holds the raw OIDC user from Auth0 — null after deserialisation from Redis, and for DAO-auth (tests)
    private transient OidcUser oidcDelegate;

    private final UUID userId;
    private final UUID careHomeId;
    private final String careHomeName;
    private final String careHomeSlug;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Role role;

    // Used by AppUserDetailsService (test path — @WithUserDetails, TenantIsolationTest)
    public AppUserPrincipal(UUID userId, UUID careHomeId, String careHomeName, String careHomeSlug,
                            String email, String displayName, String passwordHash, Role role) {
        this.oidcDelegate = null;
        this.userId = userId;
        this.careHomeId = careHomeId;
        this.careHomeName = careHomeName;
        this.careHomeSlug = careHomeSlug;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // Used by Auth0OidcUserService (OIDC login path)
    public AppUserPrincipal(OidcUser oidcUser, UUID userId, UUID careHomeId, String careHomeName,
                            String careHomeSlug, String email, String displayName, String passwordHash, Role role) {
        this(userId, careHomeId, careHomeName, careHomeSlug, email, displayName, passwordHash, role);
        this.oidcDelegate = oidcUser;
    }

    public UUID getUserId()       { return userId; }
    public UUID getCareHomeId()   { return careHomeId; }
    public String getCareHomeName() { return careHomeName; }
    public String getCareHomeSlug() { return careHomeSlug; }
    public String getDisplayName()  { return displayName; }
    public Role getRole()           { return role; }

    // --- UserDetails ---

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword()  { return passwordHash; }
    @Override public String getUsername()  { return email; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }

    // --- OidcUser / OAuth2User — delegate to oidcDelegate when present ---

    @Override public Map<String, Object> getClaims()     { return oidcDelegate != null ? oidcDelegate.getClaims()     : Map.of(); }
    @Override public OidcUserInfo getUserInfo()          { return oidcDelegate != null ? oidcDelegate.getUserInfo()   : null; }
    @Override public OidcIdToken getIdToken()            { return oidcDelegate != null ? oidcDelegate.getIdToken()    : null; }
    @Override public Map<String, Object> getAttributes() { return oidcDelegate != null ? oidcDelegate.getAttributes() : Map.of(); }
    @Override public String getName()                    { return email; }
}

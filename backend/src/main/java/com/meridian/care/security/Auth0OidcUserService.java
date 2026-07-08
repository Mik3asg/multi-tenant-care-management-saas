package com.meridian.care.security;

import com.meridian.care.domain.AppUser;
import com.meridian.care.repo.AppUserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Called by Spring after Auth0 authenticates a user.
 * Resolves the Auth0 sub claim to a local AppUser and returns an AppUserPrincipal
 * so the rest of the app (controllers, RLS aspect) works exactly as before.
 */
@Service
public class Auth0OidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    // Spring's default OIDC loader — fetches the ID token and user info from Auth0
    private final OidcUserService delegate = new OidcUserService();

    private final AppUserRepository users;

    public Auth0OidcUserService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        // Load the raw OIDC user from Auth0 first
        OidcUser oidcUser = delegate.loadUser(request);

        String sub = oidcUser.getSubject();

        // Look up the local AppUser by auth0_sub to get the care_home_id
        AppUser user = users.findByAuth0Sub(sub)
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("user_not_linked"),
                        "Auth0 user '" + sub + "' has no linked care home. Set auth0_sub on the AppUser row."));

        return new AppUserPrincipal(
                oidcUser,
                user.getId(),
                user.getCareHome().getId(),
                user.getCareHome().getName(),
                user.getCareHome().getSlug(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPasswordHash(),
                user.getRole());
    }
}

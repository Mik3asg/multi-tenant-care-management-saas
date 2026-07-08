package com.meridian.care.config;

// Removed: Auth0 owns credential verification, not the app
//import com.meridian.care.security.AppUserDetailsService;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.ProviderManager;
//import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;

import com.meridian.care.security.Auth0OidcUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Auth0 issuer URI — used to build the /v2/logout redirect
    @Value("${AUTH0_ISSUER_URI:https://dev-6fuyzwr3byt5nsce.us.auth0.com/}")
    private String issuerUri;

    // Auth0 client ID — appended to the logout URL so Auth0 knows which app is logging out
    @Value("${AUTH0_CLIENT_ID:rgdIViqVzp78tbmrtWIuFlsPPOjxaYL9}")
    private String clientId;

    // Frontend URL — where to redirect after login/logout. Override via APP_FRONTEND_URL for containerised deployments.
    @Value("${APP_FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    // Resolves the Auth0 sub claim to a local AppUserPrincipal with care_home_id
    private final Auth0OidcUserService auth0OidcUserService;

    public SecurityConfig(Auth0OidcUserService auth0OidcUserService) {
        this.auth0OidcUserService = auth0OidcUserService;
    }

    // Kept: DataSeeder still hashes demo passwords with BCrypt during seeding.
    // Not used for authentication — Auth0 owns that. Can be removed once DataSeeder no longer seeds passwords.
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    // Removed: DAO auth manager — local credential check replaced by OIDC flow
    // @Bean
    // public AuthenticationManager authenticationManager(AppUserDetailsService uds, PasswordEncoder encoder) {
    //     DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    //     provider.setUserDetailsService(uds);
    //     provider.setPasswordEncoder(encoder);
    //     return new ProviderManager(provider);
    // }

    /**
     * Security context is stored in the HttpSession, which Spring Session
     * persists to Redis. This is what makes logins survive a backend restart.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Removed: /api/auth/login — local login endpoint no longer used
                        // Allow OAuth2 redirect endpoints required by the OIDC flow
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated())
                .securityContext(sc -> sc.securityContextRepository(contextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Wire our custom user service — resolves Auth0 sub to AppUserPrincipal with care_home_id
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(u -> u.oidcUserService(auth0OidcUserService))
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(frontendUrl)))
                // Auth0 logout is non-standard — uses /v2/logout instead of OIDC end_session.
                // LogoutHandler invalidates the local session first, then redirects to Auth0.
                .logout(logout -> logout.addLogoutHandler(auth0LogoutHandler()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    // Builds the Auth0 logout URL dynamically from the current request's base URL
    private LogoutHandler auth0LogoutHandler() {
        return (request, response, authentication) -> {
            try {
                String returnTo = frontendUrl;
                response.sendRedirect(issuerUri + "v2/logout?client_id=" + clientId + "&returnTo=" + returnTo);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}

package com.meridian.care.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Calls the Auth0 Management API on behalf of the application.
 * Uses the client_credentials flow with the M2M app credentials to get a token,
 * then creates users in Auth0 so care home admins never need dashboard access.
 */
@Service
public class Auth0ManagementService {

    @Value("${auth0.mgmt.client-id}")
    private String mgmtClientId;

    @Value("${auth0.mgmt.client-secret}")
    private String mgmtClientSecret;

    @Value("${auth0.mgmt.issuer-uri}")
    private String issuerUri;

    // The regular (non-M2M) OIDC client — needed to call the public
    // dbconnections/change_password endpoint below.
    @Value("${spring.security.oauth2.client.registration.auth0.client-id}")
    private String oidcClientId;

    private final RestTemplate rest = new RestTemplate();

    // Fetches a short-lived Management API token via client_credentials grant
    private String getManagementToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "client_id", mgmtClientId,
                "client_secret", mgmtClientSecret,
                "audience", issuerUri + "api/v2/");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject(
                issuerUri + "oauth/token",
                new HttpEntity<>(body, headers),
                Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain Auth0 Management API token");
        }
        return (String) response.get("access_token");
    }

    /**
     * Creates a user in Auth0 and returns their sub (e.g. "auth0|abc123").
     * Sends a real "set your password" email via dbconnections/change_password —
     * verify_email alone (below) only triggers an email-address verification
     * email, which does NOT let the user set a usable password.
     */
    public String createUser(String email, String displayName) {
        String token = getManagementToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = Map.of(
                "email", email,
                "name", displayName,
                "connection", "Username-Password-Authentication",
                // Random, unguessable, and never used — the user sets their real
                // password via the change-password email triggered below.
                "password", "Temp-" + java.util.UUID.randomUUID() + "!",
                "verify_email", true,
                "email_verified", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject(
                issuerUri + "api/v2/users",
                new HttpEntity<>(body, headers),
                Map.class);

        if (response == null || !response.containsKey("user_id")) {
            throw new IllegalStateException("Auth0 user creation did not return a user_id");
        }

        sendPasswordChangeEmail(email);

        return (String) response.get("user_id");
    }

    /**
     * Triggers Auth0's built-in "Change Password" email via the public
     * Authentication API (not Management API — no bearer token, uses the
     * regular OIDC client-id). This is the actual mechanism that lets a
     * newly-created staff member set a real, usable password.
     */
    private void sendPasswordChangeEmail(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "client_id", oidcClientId,
                "email", email,
                "connection", "Username-Password-Authentication");

        rest.postForObject(
                issuerUri + "dbconnections/change_password",
                new HttpEntity<>(body, headers),
                String.class);
    }
}

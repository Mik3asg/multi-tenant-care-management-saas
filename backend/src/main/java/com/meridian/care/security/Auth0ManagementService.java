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
     * Auth0 sends them a "verify email / set password" email automatically.
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
                // Temporary random password — user will reset via the email Auth0 sends
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
        return (String) response.get("user_id");
    }
}

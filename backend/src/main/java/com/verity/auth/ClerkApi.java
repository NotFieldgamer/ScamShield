package com.verity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The thin slice of Clerk's Backend API this application needs: the email address behind a user id.
 *
 * <p>Clerk's default session token carries no email claim, and we will not take the address from the
 * browser — a client-supplied email is a claim about identity, and identity is exactly what we are
 * verifying. So the address is read server-to-server, once, when the local row is provisioned, then
 * stored. There is no per-request call here.
 */
@Component
public class ClerkApi {

    private final RestClient http;

    public ClerkApi(
            @Value("${app.clerk.secret-key}") String secretKey,
            @Value("${app.clerk.api-base:https://api.clerk.com/v1}") String apiBase) {
        this.http = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .build();
    }

    /**
     * The user's primary email address.
     *
     * @throws IllegalStateException if Clerk has no primary address for the user — provisioning
     *     cannot proceed, because {@code users.email} is NOT NULL and carries a unique constraint.
     */
    public String primaryEmail(String clerkUserId) {
        JsonNode user = http.get()
                .uri("/users/{id}", clerkUserId)
                .retrieve()
                .body(JsonNode.class);
        if (user == null) {
            throw new IllegalStateException("Clerk returned no body for user " + clerkUserId);
        }
        String primaryId = user.path("primary_email_address_id").asText(null);
        for (JsonNode address : user.path("email_addresses")) {
            if (address.path("id").asText("").equals(primaryId)) {
                return address.path("email_address").asText();
            }
        }
        throw new IllegalStateException("Clerk user " + clerkUserId + " has no primary email address");
    }
}

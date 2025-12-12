package com.habeshago.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for validating Google OAuth ID tokens.
 * Verifies tokens issued by Google Sign-In and extracts user information.
 *
 * @see <a href="https://developers.google.com/identity/gsi/web/guides/verify-google-id-token">Google Identity Documentation</a>
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private final GoogleIdTokenVerifier verifier;
    private final boolean devMode;

    public GoogleAuthService(
            @Value("${habeshago.google.client-id:REPLACE_ME}") String clientId) {

        this.devMode = "REPLACE_ME".equals(clientId);

        if (devMode) {
            log.warn("Google client ID not configured - running in DEV MODE. Token validation is DISABLED.");
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        }
    }

    /**
     * Validates a Google ID token and extracts user information.
     *
     * @param idTokenString The Google ID token received from the frontend
     * @return GoogleUserData containing the user's information
     * @throws IllegalArgumentException if the token is null or empty
     * @throws SecurityException if the token is invalid or verification fails
     */
    public GoogleUserData validateAndParseIdToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("Google ID token is required");
        }

        // In dev mode, create mock data for testing
        if (devMode) {
            log.warn("DEV MODE: Skipping Google token validation. Using mock data.");
            // For dev mode, extract a simple user ID from the token string
            // In production this would never happen
            return new GoogleUserData(
                    "dev-user-" + Math.abs(idTokenString.hashCode()),
                    "dev@example.com",
                    "Dev",
                    "User"
            );
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new SecurityException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Verify the token is from our app
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());

            if (!emailVerified) {
                log.warn("Google user {} has unverified email", googleId);
                // We still allow sign-in but log it
            }

            String givenName = (String) payload.get("given_name");
            String familyName = (String) payload.get("family_name");

            return new GoogleUserData(
                    googleId,
                    email,
                    givenName,
                    familyName
            );
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Google ID token", e);
            throw new SecurityException("Failed to verify Google ID token: " + e.getMessage(), e);
        }
    }

    /**
     * Represents user data extracted from a verified Google ID token.
     */
    public record GoogleUserData(
            String googleId,
            String email,
            String givenName,
            String familyName
    ) {}
}

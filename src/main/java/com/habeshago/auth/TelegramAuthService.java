package com.habeshago.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshago.auth.dto.TelegramWebAuthRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service for validating Telegram authentication.
 * Supports both Mini App initData and Login Widget authentication.
 *
 * Mini App: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 * Login Widget: https://core.telegram.org/widgets/login
 */
@Service
public class TelegramAuthService {

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_AUTH_AGE_SECONDS = 86400; // 24 hours

    private final String botToken;
    private final ObjectMapper objectMapper;
    private final boolean devMode;

    public TelegramAuthService(
            @Value("${habeshago.telegram.bot-token}") String botToken,
            ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.objectMapper = objectMapper;
        this.devMode = "REPLACE_ME".equals(botToken);

        if (devMode) {
            log.warn("Telegram bot token not configured - running in DEV MODE. initData validation is DISABLED.");
        }
    }

    /**
     * Validates and parses Telegram initData string.
     * Returns the parsed user data if valid, throws exception if invalid.
     */
    public TelegramUserData validateAndParseInitData(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new IllegalArgumentException("initData is required");
        }

        // Parse the query string
        Map<String, String> params = parseQueryString(initData);

        String hash = params.get("hash");
        if (hash == null && !devMode) {
            throw new IllegalArgumentException("hash is required in initData");
        }

        // In dev mode, skip signature validation
        if (!devMode) {
            // Verify the signature
            if (!verifySignature(params, hash)) {
                throw new SecurityException("Invalid initData signature");
            }
        }

        // Parse user data
        String userJson = params.get("user");
        if (userJson == null) {
            throw new IllegalArgumentException("user data is required in initData");
        }

        try {
            JsonNode userNode = objectMapper.readTree(userJson);
            return new TelegramUserData(
                    userNode.get("id").asLong(),
                    userNode.has("first_name") ? userNode.get("first_name").asText() : null,
                    userNode.has("last_name") ? userNode.get("last_name").asText() : null,
                    userNode.has("username") ? userNode.get("username").asText() : null,
                    userNode.has("language_code") ? userNode.get("language_code").asText() : "en"
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse user data: " + e.getMessage(), e);
        }
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new TreeMap<>();
        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }

        return params;
    }

    private boolean verifySignature(Map<String, String> params, String hash) {
        try {
            // Build the data-check-string (sorted alphabetically, excluding hash)
            StringBuilder dataCheckString = new StringBuilder();
            params.entrySet().stream()
                    .filter(e -> !"hash".equals(e.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        if (dataCheckString.length() > 0) {
                            dataCheckString.append("\n");
                        }
                        dataCheckString.append(e.getKey()).append("=").append(e.getValue());
                    });

            // Generate secret_key = HMAC_SHA256(bot_token, "WebAppData")
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    "WebAppData".getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            hmac.init(secretKeySpec);
            byte[] secretKey = hmac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            // Calculate HMAC_SHA256(data_check_string, secret_key)
            hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            byte[] calculatedHash = hmac.doFinal(dataCheckString.toString().getBytes(StandardCharsets.UTF_8));

            // Compare hashes
            String calculatedHashHex = bytesToHex(calculatedHash);
            return MessageDigest.isEqual(
                    calculatedHashHex.getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify Telegram signature", e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    // ========== Telegram Login Widget Methods ==========

    /**
     * Validates and parses Telegram Login Widget authentication data.
     * The Login Widget uses a different hash algorithm than the Mini App.
     *
     * Algorithm:
     * 1. Build data-check-string (sorted key=value pairs, newline separated)
     * 2. secret_key = SHA256(bot_token)
     * 3. hash = HMAC-SHA256(data-check-string, secret_key)
     *
     * @param request The request containing Telegram Login Widget data
     * @return TelegramUserData if valid
     * @throws IllegalArgumentException if data is invalid
     * @throws SecurityException if signature verification fails
     */
    public TelegramUserData validateAndParseWebLogin(TelegramWebAuthRequest request) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("Telegram user ID is required");
        }

        // In dev mode, skip signature validation
        if (!devMode) {
            // Check if auth_date is recent (within 24 hours)
            long currentTime = System.currentTimeMillis() / 1000;
            if (currentTime - request.authDate() > MAX_AUTH_AGE_SECONDS) {
                throw new SecurityException("Telegram authentication data has expired");
            }

            // Verify the signature
            if (!verifyWidgetSignature(request)) {
                throw new SecurityException("Invalid Telegram Login Widget signature");
            }
        }

        return new TelegramUserData(
                request.id(),
                request.firstName(),
                request.lastName(),
                request.username(),
                "en" // Login widget doesn't provide language code
        );
    }

    /**
     * Verifies the Telegram Login Widget signature.
     *
     * The algorithm is different from Mini App:
     * - Mini App: secret_key = HMAC_SHA256(bot_token, "WebAppData")
     * - Widget: secret_key = SHA256(bot_token)
     */
    private boolean verifyWidgetSignature(TelegramWebAuthRequest request) {
        try {
            // Build the data-check-string (sorted alphabetically, excluding hash)
            TreeMap<String, String> params = new TreeMap<>();
            params.put("id", String.valueOf(request.id()));
            params.put("auth_date", String.valueOf(request.authDate()));

            if (request.firstName() != null) {
                params.put("first_name", request.firstName());
            }
            if (request.lastName() != null) {
                params.put("last_name", request.lastName());
            }
            if (request.username() != null) {
                params.put("username", request.username());
            }
            if (request.photoUrl() != null) {
                params.put("photo_url", request.photoUrl());
            }

            StringBuilder dataCheckString = new StringBuilder();
            params.forEach((key, value) -> {
                if (dataCheckString.length() > 0) {
                    dataCheckString.append("\n");
                }
                dataCheckString.append(key).append("=").append(value);
            });

            // secret_key = SHA256(bot_token)
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = sha256.digest(botToken.getBytes(StandardCharsets.UTF_8));

            // hash = HMAC_SHA256(data_check_string, secret_key)
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            hmac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            byte[] calculatedHash = hmac.doFinal(dataCheckString.toString().getBytes(StandardCharsets.UTF_8));

            // Compare hashes
            String calculatedHashHex = bytesToHex(calculatedHash);
            return MessageDigest.isEqual(
                    calculatedHashHex.getBytes(StandardCharsets.UTF_8),
                    request.hash().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify Telegram Login Widget signature", e);
            return false;
        }
    }

    public record TelegramUserData(
            Long telegramUserId,
            String firstName,
            String lastName,
            String username,
            String languageCode
    ) {}
}

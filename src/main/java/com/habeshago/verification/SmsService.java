package com.habeshago.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final int OTP_LENGTH = 6;
    private static final long OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final long COOLDOWN_SECONDS = 60; // 1 minute between sends

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final boolean devMode;
    private final SecureRandom random = new SecureRandom();

    // In-memory OTP storage (use Redis in production for multi-instance)
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public SmsService(
            @Value("${habeshago.twilio.account-sid:}") String accountSid,
            @Value("${habeshago.twilio.auth-token:}") String authToken,
            @Value("${habeshago.twilio.from-number:}") String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.devMode = accountSid == null || accountSid.isBlank();

        if (devMode) {
            log.warn("Twilio not configured - running in DEV MODE. OTPs will be logged instead of sent.");
        }
    }

    /**
     * Send an OTP to the given phone number.
     * Returns the OTP in dev mode (for testing), null in production.
     */
    public String sendOtp(String phoneNumber) {
        // Check cooldown
        OtpEntry existing = otpStore.get(phoneNumber);
        if (existing != null && existing.cannotResendYet()) {
            long remainingSeconds = existing.cooldownRemaining();
            throw new CooldownException("Please wait " + remainingSeconds + " seconds before requesting a new code");
        }

        String otp = generateOtp();
        String message = "Your HabeshaGo verification code is: " + otp + ". Valid for 5 minutes.";

        if (devMode) {
            log.info("DEV MODE - OTP for {}: {}", phoneNumber, otp);
        } else {
            sendSms(phoneNumber, message);
        }

        // Store OTP
        otpStore.put(phoneNumber, new OtpEntry(otp, Instant.now()));
        log.info("Sent OTP to phone number ending in {}", phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));

        return devMode ? otp : null;
    }

    /**
     * Verify an OTP for the given phone number.
     */
    public boolean verifyOtp(String phoneNumber, String otp) {
        OtpEntry entry = otpStore.get(phoneNumber);
        if (entry == null) {
            log.warn("No OTP found for phone number ending in {}", phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
            return false;
        }

        if (entry.isExpired()) {
            otpStore.remove(phoneNumber);
            log.warn("OTP expired for phone number ending in {}", phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
            return false;
        }

        if (entry.otp.equals(otp)) {
            otpStore.remove(phoneNumber);
            log.info("OTP verified for phone number ending in {}", phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
            return true;
        }

        log.warn("Invalid OTP attempt for phone number ending in {}", phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
        return false;
    }

    /**
     * Get remaining cooldown time in seconds.
     */
    public long getCooldownRemaining(String phoneNumber) {
        OtpEntry entry = otpStore.get(phoneNumber);
        return entry != null ? entry.cooldownRemaining() : 0;
    }

    private void sendSms(String to, String message) {
        try {
            // Using Twilio REST API directly to avoid SDK dependency issues
            // In production, use the Twilio SDK
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

            String body = "To=" + java.net.URLEncoder.encode(to, "UTF-8") +
                    "&From=" + java.net.URLEncoder.encode(fromNumber, "UTF-8") +
                    "&Body=" + java.net.URLEncoder.encode(message, "UTF-8");

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                            .encodeToString((accountSid + ":" + authToken).getBytes()))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("SMS sent successfully to {}", to);
            } else {
                log.error("Failed to send SMS: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to send SMS: " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error sending SMS to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send SMS", e);
        }
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    private record OtpEntry(String otp, Instant createdAt) {
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(OTP_EXPIRY_SECONDS));
        }

        boolean cannotResendYet() {
            return cooldownRemaining() > 0;
        }

        long cooldownRemaining() {
            long elapsed = Instant.now().getEpochSecond() - createdAt.getEpochSecond();
            return Math.max(0, COOLDOWN_SECONDS - elapsed);
        }
    }

    public static class CooldownException extends RuntimeException {
        public CooldownException(String message) {
            super(message);
        }
    }
}

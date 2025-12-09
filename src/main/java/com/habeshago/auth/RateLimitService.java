package com.habeshago.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting service to prevent brute force attacks on auth endpoints.
 * Uses in-memory Caffeine cache with sliding window approach.
 */
@Service
public class RateLimitService {

    // Login attempts: 5 per minute per IP
    private final Cache<String, AtomicInteger> loginAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10000)
            .build();

    // Registration attempts: 3 per minute per IP
    private final Cache<String, AtomicInteger> registerAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10000)
            .build();

    // Failed login lockout: track IPs that exceeded limit
    private final Cache<String, Long> lockedOutIps = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10000)
            .build();

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_REGISTER_ATTEMPTS = 3;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    /**
     * Check if login is allowed for the given IP address
     * @return true if allowed, false if rate limited
     */
    public boolean isLoginAllowed(String ipAddress) {
        // Check if IP is locked out
        if (lockedOutIps.getIfPresent(ipAddress) != null) {
            return false;
        }

        AtomicInteger attempts = loginAttempts.get(ipAddress, k -> new AtomicInteger(0));
        return attempts.get() < MAX_LOGIN_ATTEMPTS;
    }

    /**
     * Record a login attempt
     */
    public void recordLoginAttempt(String ipAddress) {
        AtomicInteger attempts = loginAttempts.get(ipAddress, k -> new AtomicInteger(0));
        if (attempts.incrementAndGet() >= MAX_LOGIN_ATTEMPTS) {
            // Lock out the IP
            lockedOutIps.put(ipAddress, System.currentTimeMillis());
        }
    }

    /**
     * Clear login attempts on successful login
     */
    public void clearLoginAttempts(String ipAddress) {
        loginAttempts.invalidate(ipAddress);
        lockedOutIps.invalidate(ipAddress);
    }

    /**
     * Check if registration is allowed for the given IP address
     * @return true if allowed, false if rate limited
     */
    public boolean isRegistrationAllowed(String ipAddress) {
        AtomicInteger attempts = registerAttempts.get(ipAddress, k -> new AtomicInteger(0));
        return attempts.get() < MAX_REGISTER_ATTEMPTS;
    }

    /**
     * Record a registration attempt
     */
    public void recordRegistrationAttempt(String ipAddress) {
        AtomicInteger attempts = registerAttempts.get(ipAddress, k -> new AtomicInteger(0));
        attempts.incrementAndGet();
    }

    /**
     * Get remaining seconds until lockout expires
     */
    public int getLockoutRemainingSeconds(String ipAddress) {
        Long lockoutTime = lockedOutIps.getIfPresent(ipAddress);
        if (lockoutTime == null) {
            return 0;
        }
        long elapsedMs = System.currentTimeMillis() - lockoutTime;
        long remainingMs = (LOCKOUT_DURATION_MINUTES * 60 * 1000) - elapsedMs;
        return remainingMs > 0 ? (int) (remainingMs / 1000) : 0;
    }

    /**
     * Exception thrown when rate limit is exceeded
     */
    public static class RateLimitExceededException extends RuntimeException {
        private final int retryAfterSeconds;

        public RateLimitExceededException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}

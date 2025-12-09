package com.habeshago.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Security audit logger for tracking authentication and authorization events.
 * Logs to a separate security-audit.log file for compliance and forensics.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        REGISTRATION_SUCCESS,
        REGISTRATION_FAILURE,
        RATE_LIMIT_EXCEEDED,
        RATE_LIMIT_LOCKOUT,
        TOKEN_INVALID,
        TOKEN_EXPIRED,
        AUTHORIZATION_FAILURE,
        PASSWORD_RESET_REQUEST,
        PASSWORD_RESET_SUCCESS,
        ACCOUNT_LOCKED,
        SUSPICIOUS_ACTIVITY
    }

    /**
     * Log a security event with full context
     */
    public void log(EventType eventType, String ipAddress, String userId, String email, String details) {
        try {
            MDC.put("eventType", eventType.name());
            MDC.put("ip", ipAddress != null ? ipAddress : "unknown");
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("email", maskEmail(email));

            String message = buildMessage(eventType, ipAddress, userId, email, details);

            // Use appropriate log level based on event severity
            switch (eventType) {
                case LOGIN_FAILURE, TOKEN_INVALID, TOKEN_EXPIRED, AUTHORIZATION_FAILURE ->
                    securityLog.warn(message);
                case RATE_LIMIT_EXCEEDED, RATE_LIMIT_LOCKOUT, ACCOUNT_LOCKED, SUSPICIOUS_ACTIVITY ->
                    securityLog.error(message);
                default ->
                    securityLog.info(message);
            }
        } finally {
            MDC.remove("eventType");
            MDC.remove("ip");
            MDC.remove("userId");
            MDC.remove("email");
        }
    }

    /**
     * Convenience method for login success
     */
    public void logLoginSuccess(String ipAddress, String userId, String email) {
        log(EventType.LOGIN_SUCCESS, ipAddress, userId, email, null);
    }

    /**
     * Convenience method for login failure
     */
    public void logLoginFailure(String ipAddress, String email, String reason) {
        log(EventType.LOGIN_FAILURE, ipAddress, null, email, reason);
    }

    /**
     * Convenience method for registration success
     */
    public void logRegistrationSuccess(String ipAddress, String userId, String email) {
        log(EventType.REGISTRATION_SUCCESS, ipAddress, userId, email, null);
    }

    /**
     * Convenience method for registration failure
     */
    public void logRegistrationFailure(String ipAddress, String email, String reason) {
        log(EventType.REGISTRATION_FAILURE, ipAddress, null, email, reason);
    }

    /**
     * Convenience method for rate limit exceeded
     */
    public void logRateLimitExceeded(String ipAddress, String endpoint, int retryAfterSeconds) {
        log(EventType.RATE_LIMIT_EXCEEDED, ipAddress, null, null,
            "endpoint=" + endpoint + ", retryAfter=" + retryAfterSeconds + "s");
    }

    /**
     * Convenience method for rate limit lockout
     */
    public void logRateLimitLockout(String ipAddress, int lockoutMinutes) {
        log(EventType.RATE_LIMIT_LOCKOUT, ipAddress, null, null,
            "lockoutDuration=" + lockoutMinutes + "min");
    }

    /**
     * Convenience method for authorization failure
     */
    public void logAuthorizationFailure(String ipAddress, String userId, String resource, String action) {
        log(EventType.AUTHORIZATION_FAILURE, ipAddress, userId, null,
            "resource=" + resource + ", action=" + action);
    }

    private String buildMessage(EventType eventType, String ipAddress, String userId, String email, String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("SECURITY_EVENT: ").append(eventType.name());
        sb.append(" | ip=").append(ipAddress != null ? ipAddress : "unknown");

        if (userId != null) {
            sb.append(" | userId=").append(userId);
        }
        if (email != null) {
            sb.append(" | email=").append(maskEmail(email));
        }
        if (details != null) {
            sb.append(" | ").append(details);
        }

        return sb.toString();
    }

    /**
     * Mask email for privacy (show first 2 chars and domain)
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 4) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}

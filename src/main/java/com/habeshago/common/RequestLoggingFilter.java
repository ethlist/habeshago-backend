package com.habeshago.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that logs all HTTP requests with timing, status codes, and request IDs.
 * Adds requestId to MDC for correlation across log entries.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip logging for health checks to reduce noise
        if (isHealthCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = generateRequestId();
        long startTime = System.currentTimeMillis();

        try {
            // Add context to MDC for all log entries during this request
            MDC.put("requestId", requestId);
            MDC.put("ip", getClientIpAddress(request));
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());

            // Add request ID to response header for client-side correlation
            response.setHeader("X-Request-Id", requestId);

            filterChain.doFilter(request, response);

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            // Log the completed request
            String logMessage = String.format("%s %s | status=%d | duration=%dms | ip=%s",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    duration,
                    getClientIpAddress(request));

            // Use appropriate log level based on status code
            if (status >= 500) {
                log.error(logMessage);
            } else if (status >= 400) {
                log.warn(logMessage);
            } else {
                log.info(logMessage);
            }

            // Clean up MDC
            MDC.remove("requestId");
            MDC.remove("ip");
            MDC.remove("method");
            MDC.remove("path");
        }
    }

    private boolean isHealthCheck(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") ||
               path.equals("/actuator/info");
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

package com.habeshago.auth;

import com.habeshago.user.User;
import com.habeshago.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Production-ready auth interceptor supporting:
 * 1. JWT Bearer token authentication (always enabled)
 * 2. X-Demo-Telegram-UserId header (ONLY in dev profile - disabled in production)
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    public AuthInterceptor(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public static final String CURRENT_USER_ATTR = "currentUser";

    private boolean isDevelopmentMode() {
        return "dev".equalsIgnoreCase(activeProfile) || "development".equalsIgnoreCase(activeProfile);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();

        // Skip auth for CORS preflight requests (OPTIONS)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Skip auth for public endpoints
        if (isPublicEndpoint(path)) {
            return true;
        }

        // Try JWT auth first (Authorization: Bearer <token>)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.isTokenValid(token)) {
                    Long userId = jwtService.getUserIdFromToken(token);
                    Optional<User> userOpt = userRepository.findById(userId);
                    if (userOpt.isPresent()) {
                        request.setAttribute(CURRENT_USER_ATTR, userOpt.get());
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        }

        // SECURITY: Demo header ONLY allowed in development mode
        if (isDevelopmentMode()) {
            String demoHeader = request.getHeader("X-Demo-Telegram-UserId");
            if (demoHeader != null && !demoHeader.isBlank()) {
                log.warn("DEVELOPMENT MODE: Using X-Demo-Telegram-UserId header for auth. This is disabled in production.");
                try {
                    Long tgId = Long.parseLong(demoHeader);
                    Optional<User> userOpt = userRepository.findByTelegramUserId(tgId);
                    User user = userOpt.orElseGet(() -> {
                        User u = new User();
                        u.setTelegramUserId(tgId);
                        u.setPreferredLanguage("en");
                        return userRepository.save(u);
                    });
                    request.setAttribute(CURRENT_USER_ATTR, user);
                    return true;
                } catch (NumberFormatException ignored) {}
            }
        }

        // SECURITY: No valid authentication - reject request
        log.debug("Authentication required for path: {}", path);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Authentication required\",\"code\":401}");
        return false;
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/") ||
               path.equals("/api/trips/search") ||
               path.startsWith("/api/travelers/") ||
               path.equals("/api/health") ||
               path.equals("/actuator/health") ||
               path.startsWith("/actuator/") ||
               path.equals("/h2-console") ||
               path.startsWith("/h2-console/");
    }

    public static User getCurrentUser(HttpServletRequest request) {
        Object u = request.getAttribute(CURRENT_USER_ATTR);
        if (u instanceof User user) {
            return user;
        }
        return null;
    }
}

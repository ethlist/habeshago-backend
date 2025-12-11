package com.habeshago.auth;

import com.habeshago.auth.dto.AuthResponse;
import com.habeshago.auth.dto.TelegramAuthRequest;
import com.habeshago.auth.dto.TelegramWebAuthRequest;
import com.habeshago.auth.dto.WebLoginRequest;
import com.habeshago.auth.dto.WebRegisterRequest;
import com.habeshago.common.SecurityAuditLogger;
import com.habeshago.user.User;
import com.habeshago.user.UserDto;
import com.habeshago.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WebAuthService webAuthService;
    private final RateLimitService rateLimitService;
    private final SecurityAuditLogger securityAuditLogger;

    public AuthController(
            TelegramAuthService telegramAuthService,
            JwtService jwtService,
            UserRepository userRepository,
            WebAuthService webAuthService,
            RateLimitService rateLimitService,
            SecurityAuditLogger securityAuditLogger) {
        this.telegramAuthService = telegramAuthService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.webAuthService = webAuthService;
        this.rateLimitService = rateLimitService;
        this.securityAuditLogger = securityAuditLogger;
    }

    @PostMapping("/telegram")
    public ResponseEntity<AuthResponse> authenticateWithTelegram(
            @Valid @RequestBody TelegramAuthRequest request) {

        // Validate and parse Telegram initData
        TelegramAuthService.TelegramUserData telegramUser =
                telegramAuthService.validateAndParseInitData(request.initData());

        // Find or create user
        User user = userRepository.findByTelegramUserId(telegramUser.telegramUserId())
                .orElseGet(() -> createNewUser(telegramUser));

        // Update user info if changed
        updateUserInfo(user, telegramUser);

        // Generate JWT token
        String token = jwtService.generateToken(user.getId(), user.getTelegramUserId());

        return ResponseEntity.ok(new AuthResponse(token, UserDto.from(user)));
    }

    /**
     * Authenticate using Telegram Login Widget (for web users).
     * This is different from the Mini App authentication - the widget sends
     * individual fields and uses a different hash algorithm.
     */
    @PostMapping("/telegram-web")
    public ResponseEntity<AuthResponse> authenticateWithTelegramWeb(
            @Valid @RequestBody TelegramWebAuthRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIpAddress(httpRequest);

        // Use login rate limiting
        if (!rateLimitService.isLoginAllowed(ipAddress)) {
            int retryAfter = rateLimitService.getLockoutRemainingSeconds(ipAddress);
            securityAuditLogger.logRateLimitExceeded(ipAddress, "/api/auth/telegram-web", retryAfter);
            throw new RateLimitService.RateLimitExceededException(
                    "Too many login attempts. Please try again later.", retryAfter);
        }

        try {
            // Validate Telegram Login Widget data
            TelegramAuthService.TelegramUserData telegramUser =
                    telegramAuthService.validateAndParseWebLogin(request);

            // Find or create user
            User user = userRepository.findByTelegramUserId(telegramUser.telegramUserId())
                    .orElseGet(() -> createNewUser(telegramUser));

            // Update user info if changed
            updateUserInfo(user, telegramUser);

            // Generate JWT token
            String token = jwtService.generateToken(user.getId(), user.getTelegramUserId());

            // Clear rate limit attempts on success
            rateLimitService.clearLoginAttempts(ipAddress);
            securityAuditLogger.logLoginSuccess(ipAddress, String.valueOf(user.getId()), "telegram:" + telegramUser.telegramUserId());

            return ResponseEntity.ok(new AuthResponse(token, UserDto.from(user)));
        } catch (SecurityException e) {
            rateLimitService.recordLoginAttempt(ipAddress);
            securityAuditLogger.logLoginFailure(ipAddress, "telegram:" + request.id(), e.getMessage());
            throw new InvalidTelegramAuthException(e.getMessage());
        }
    }

    private User createNewUser(TelegramAuthService.TelegramUserData telegramUser) {
        User user = new User();
        user.setTelegramUserId(telegramUser.telegramUserId());
        user.setFirstName(telegramUser.firstName());
        user.setLastName(telegramUser.lastName());
        user.setUsername(telegramUser.username());
        user.setPreferredLanguage(mapLanguageCode(telegramUser.languageCode()));
        return userRepository.save(user);
    }

    private void updateUserInfo(User user, TelegramAuthService.TelegramUserData telegramUser) {
        boolean changed = false;

        // Only update firstName and lastName from Telegram if user hasn't manually edited their profile
        if (!Boolean.TRUE.equals(user.getProfileEditedByUser())) {
            if (telegramUser.firstName() != null &&
                    !telegramUser.firstName().equals(user.getFirstName())) {
                user.setFirstName(telegramUser.firstName());
                changed = true;
            }

            if (telegramUser.lastName() != null &&
                    !telegramUser.lastName().equals(user.getLastName())) {
                user.setLastName(telegramUser.lastName());
                changed = true;
            }
        }

        // Username is always synced from Telegram (not editable by user in the app)
        if (telegramUser.username() != null &&
                !telegramUser.username().equals(user.getUsername())) {
            user.setUsername(telegramUser.username());
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
        }
    }

    private String mapLanguageCode(String languageCode) {
        // Map Telegram language codes to our supported languages
        if (languageCode == null) {
            return "en";
        }
        // Support Amharic
        if (languageCode.startsWith("am")) {
            return "am";
        }
        // Default to English
        return "en";
    }

    // ----- Web Authentication Endpoints -----

    @PostMapping("/web/login")
    public ResponseEntity<AuthResponse> loginWithEmail(
            @Valid @RequestBody WebLoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);

        // Check rate limit
        if (!rateLimitService.isLoginAllowed(ipAddress)) {
            int retryAfter = rateLimitService.getLockoutRemainingSeconds(ipAddress);
            securityAuditLogger.logRateLimitExceeded(ipAddress, "/api/auth/web/login", retryAfter);
            throw new RateLimitService.RateLimitExceededException(
                    "Too many login attempts. Please try again later.", retryAfter);
        }

        try {
            AuthResponse response = webAuthService.login(request);
            // Clear attempts on successful login
            rateLimitService.clearLoginAttempts(ipAddress);
            securityAuditLogger.logLoginSuccess(ipAddress, response.user().id(), request.email());
            return ResponseEntity.ok(response);
        } catch (WebAuthService.InvalidCredentialsException e) {
            // Record failed attempt
            rateLimitService.recordLoginAttempt(ipAddress);
            securityAuditLogger.logLoginFailure(ipAddress, request.email(), "Invalid credentials");
            throw e;
        }
    }

    @PostMapping("/web/register")
    public ResponseEntity<AuthResponse> registerWithEmail(
            @Valid @RequestBody WebRegisterRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);

        // Check rate limit
        if (!rateLimitService.isRegistrationAllowed(ipAddress)) {
            securityAuditLogger.logRateLimitExceeded(ipAddress, "/api/auth/web/register", 60);
            throw new RateLimitService.RateLimitExceededException(
                    "Too many registration attempts. Please try again later.", 60);
        }

        // Record attempt before processing
        rateLimitService.recordRegistrationAttempt(ipAddress);

        try {
            AuthResponse response = webAuthService.register(request);
            securityAuditLogger.logRegistrationSuccess(ipAddress, response.user().id(), request.email());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (WebAuthService.EmailAlreadyExistsException e) {
            securityAuditLogger.logRegistrationFailure(ipAddress, request.email(), "Email already exists");
            throw e;
        }
    }

    /**
     * Extract client IP address securely for rate limiting.
     *
     * Security: X-Forwarded-For can be spoofed by clients. We prefer X-Real-IP
     * which is set by our nginx reverse proxy and cannot be manipulated.
     *
     * Priority: X-Real-IP (nginx) > Remote Address
     *
     * Note: X-Forwarded-For is intentionally NOT used for rate limiting as it
     * can be easily spoofed to bypass rate limits.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // X-Real-IP is set by nginx and cannot be spoofed by clients
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // Fall back to remote address (direct connection or local development)
        return request.getRemoteAddr();
    }

    // Exception handlers for web auth
    @ExceptionHandler(WebAuthService.EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(WebAuthService.EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, ex.getMessage()));
    }

    @ExceptionHandler(WebAuthService.InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(WebAuthService.InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, ex.getMessage()));
    }

    @ExceptionHandler(RateLimitService.RateLimitExceededException.class)
    public ResponseEntity<RateLimitErrorResponse> handleRateLimitExceeded(RateLimitService.RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new RateLimitErrorResponse(429, ex.getMessage(), ex.getRetryAfterSeconds()));
    }

    @ExceptionHandler(InvalidTelegramAuthException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTelegramAuth(InvalidTelegramAuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, ex.getMessage()));
    }

    public record ErrorResponse(int code, String message) {}
    public record RateLimitErrorResponse(int code, String message, int retryAfterSeconds) {}

    public static class InvalidTelegramAuthException extends RuntimeException {
        public InvalidTelegramAuthException(String message) {
            super(message);
        }
    }
}

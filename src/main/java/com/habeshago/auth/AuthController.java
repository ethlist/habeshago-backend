package com.habeshago.auth;

import com.habeshago.auth.dto.AuthResponse;
import com.habeshago.auth.dto.GoogleAuthRequest;
import com.habeshago.auth.dto.TelegramAuthRequest;
import com.habeshago.auth.dto.TelegramWebAuthRequest;
import com.habeshago.auth.dto.WebLoginRequest;
import com.habeshago.auth.dto.WebRegisterRequest;
import com.habeshago.common.NotFoundException;
import com.habeshago.common.SecurityAuditLogger;
import com.habeshago.request.ItemRequestRepository;
import com.habeshago.trip.TripRepository;
import com.habeshago.user.User;
import com.habeshago.user.UserDto;
import com.habeshago.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final TelegramAuthService telegramAuthService;
    private final GoogleAuthService googleAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final ItemRequestRepository itemRequestRepository;
    private final WebAuthService webAuthService;
    private final RateLimitService rateLimitService;
    private final SecurityAuditLogger securityAuditLogger;

    public AuthController(
            TelegramAuthService telegramAuthService,
            GoogleAuthService googleAuthService,
            JwtService jwtService,
            UserRepository userRepository,
            TripRepository tripRepository,
            ItemRequestRepository itemRequestRepository,
            WebAuthService webAuthService,
            RateLimitService rateLimitService,
            SecurityAuditLogger securityAuditLogger) {
        this.telegramAuthService = telegramAuthService;
        this.googleAuthService = googleAuthService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.itemRequestRepository = itemRequestRepository;
        this.webAuthService = webAuthService;
        this.rateLimitService = rateLimitService;
        this.securityAuditLogger = securityAuditLogger;
    }

    @PostMapping("/telegram")
    @Transactional
    public ResponseEntity<AuthResponse> authenticateWithTelegram(
            @Valid @RequestBody TelegramAuthRequest request) {

        // Validate and parse Telegram initData
        TelegramAuthService.TelegramUserData telegramUser =
                telegramAuthService.validateAndParseInitData(request.initData());

        // Find or create user
        User user = userRepository.findByTelegramUserId(telegramUser.telegramUserId())
                .orElseGet(() -> createNewUser(telegramUser));

        // Update user info if changed (including syncing contact values on trips/requests)
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
    @Transactional
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

    // ----- Google OAuth Authentication -----

    /**
     * Authenticate using Google Sign-In.
     * Creates a new account if user doesn't exist, otherwise logs in existing user.
     */
    @PostMapping("/google")
    @Transactional
    public ResponseEntity<AuthResponse> authenticateWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIpAddress(httpRequest);

        // Use login rate limiting
        if (!rateLimitService.isLoginAllowed(ipAddress)) {
            int retryAfter = rateLimitService.getLockoutRemainingSeconds(ipAddress);
            securityAuditLogger.logRateLimitExceeded(ipAddress, "/api/auth/google", retryAfter);
            throw new RateLimitService.RateLimitExceededException(
                    "Too many login attempts. Please try again later.", retryAfter);
        }

        try {
            // Validate Google ID token
            GoogleAuthService.GoogleUserData googleUser =
                    googleAuthService.validateAndParseIdToken(request.idToken());

            // Find or create user
            User user = userRepository.findByGoogleId(googleUser.googleId())
                    .orElseGet(() -> createNewGoogleUser(googleUser));

            // Update user info if changed
            updateGoogleUserInfo(user, googleUser);

            // Generate JWT token
            String token = jwtService.generateToken(user.getId(), user.getTelegramUserId());

            // Clear rate limit attempts on success
            rateLimitService.clearLoginAttempts(ipAddress);
            securityAuditLogger.logLoginSuccess(ipAddress, String.valueOf(user.getId()), "google:" + googleUser.googleId());

            return ResponseEntity.ok(new AuthResponse(token, UserDto.from(user)));
        } catch (SecurityException e) {
            rateLimitService.recordLoginAttempt(ipAddress);
            securityAuditLogger.logLoginFailure(ipAddress, "google:unknown", e.getMessage());
            throw new InvalidGoogleAuthException(e.getMessage());
        }
    }

    private User createNewGoogleUser(GoogleAuthService.GoogleUserData googleUser) {
        User user = new User();
        user.setGoogleId(googleUser.googleId());
        user.setGoogleEmail(googleUser.email());
        user.setFirstName(googleUser.givenName());
        user.setLastName(googleUser.familyName());
        user.setPreferredLanguage("en");

        // Google users are identity-verified
        user.setIdentityVerified(true);
        user.setIdentityProvider("GOOGLE");

        log.info("Created new user from Google sign-in: {} ({})", googleUser.email(), googleUser.googleId());
        return userRepository.save(user);
    }

    private void updateGoogleUserInfo(User user, GoogleAuthService.GoogleUserData googleUser) {
        boolean changed = false;

        // Update email if changed
        if (googleUser.email() != null && !googleUser.email().equals(user.getGoogleEmail())) {
            user.setGoogleEmail(googleUser.email());
            changed = true;
        }

        // Only update name from Google if user hasn't manually edited their profile
        if (!Boolean.TRUE.equals(user.getProfileEditedByUser())) {
            if (googleUser.givenName() != null && !googleUser.givenName().equals(user.getFirstName())) {
                user.setFirstName(googleUser.givenName());
                changed = true;
            }

            if (googleUser.familyName() != null && !googleUser.familyName().equals(user.getLastName())) {
                user.setLastName(googleUser.familyName());
                changed = true;
            }
        }

        // Ensure identity verified is set (for users created before this feature)
        if (!Boolean.TRUE.equals(user.getIdentityVerified())) {
            user.setIdentityVerified(true);
            user.setIdentityProvider("GOOGLE");
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
        }
    }

    private User createNewUser(TelegramAuthService.TelegramUserData telegramUser) {
        User user = new User();
        user.setTelegramUserId(telegramUser.telegramUserId());
        user.setFirstName(telegramUser.firstName());
        user.setLastName(telegramUser.lastName());
        user.setUsername(telegramUser.username());
        user.setPreferredLanguage(mapLanguageCode(telegramUser.languageCode()));

        // Telegram users are identity-verified
        user.setIdentityVerified(true);
        user.setIdentityProvider("TELEGRAM");

        // Auto-enable Telegram contact if username is available
        if (telegramUser.username() != null && !telegramUser.username().isBlank()) {
            user.setContactTelegramEnabled(true);
        }

        return userRepository.save(user);
    }

    private void updateUserInfo(User user, TelegramAuthService.TelegramUserData telegramUser) {
        boolean changed = false;
        boolean usernameChanged = false;
        String oldUsername = user.getUsername();

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
        // Use Objects.equals to handle null properly - if user removes their Telegram username, DB should reflect that
        if (!java.util.Objects.equals(telegramUser.username(), user.getUsername())) {
            user.setUsername(telegramUser.username());
            changed = true;
            usernameChanged = true;
        }

        if (changed) {
            userRepository.save(user);
        }

        // If username changed, update contact values on all trips and requests that use Telegram
        if (usernameChanged) {
            syncTelegramContactValues(user.getId(), telegramUser.username(), oldUsername);
        }
    }

    /**
     * Sync contact values on trips and item requests when user's Telegram username changes.
     * This ensures users can still be contacted via their current username.
     */
    private void syncTelegramContactValues(Long userId, String newUsername, String oldUsername) {
        int tripsUpdated = tripRepository.updateTelegramContactValue(userId, newUsername);
        int requestsUpdated = itemRequestRepository.updateSenderTelegramContactValue(userId, newUsername);

        if (tripsUpdated > 0 || requestsUpdated > 0) {
            log.info("Synced Telegram contact values for user {}: {} trips, {} requests updated (username: {} -> {})",
                    userId, tripsUpdated, requestsUpdated, oldUsername, newUsername);
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

    // ----- Account Linking Endpoints -----

    /**
     * Link a Telegram account to an existing user account.
     * User must be authenticated via JWT.
     */
    @PostMapping("/link-telegram")
    @Transactional
    public ResponseEntity<UserDto> linkTelegramAccount(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody TelegramWebAuthRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIpAddress(httpRequest);

        // Validate Telegram data
        TelegramAuthService.TelegramUserData telegramUser =
                telegramAuthService.validateAndParseWebLogin(request);

        // Check if this Telegram account is already linked to another user
        var existingUser = userRepository.findByTelegramUserId(telegramUser.telegramUserId());
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            throw new AccountLinkingException("This Telegram account is already linked to another user");
        }

        // Get the current user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if user already has a Telegram account linked
        if (user.getTelegramUserId() != null) {
            throw new AccountLinkingException("Your account already has a Telegram account linked");
        }

        // Link the Telegram account
        user.setTelegramUserId(telegramUser.telegramUserId());
        user.setUsername(telegramUser.username());

        // Auto-enable Telegram contact if username is available
        if (telegramUser.username() != null && !telegramUser.username().isBlank()) {
            user.setContactTelegramEnabled(true);
        }

        userRepository.save(user);
        log.info("Linked Telegram account {} to user {} (IP: {})",
                telegramUser.telegramUserId(), userId, ipAddress);

        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Link a Google account to an existing user account.
     * User must be authenticated via JWT.
     */
    @PostMapping("/link-google")
    @Transactional
    public ResponseEntity<UserDto> linkGoogleAccount(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIpAddress(httpRequest);

        // Validate Google token
        GoogleAuthService.GoogleUserData googleUser =
                googleAuthService.validateAndParseIdToken(request.idToken());

        // Check if this Google account is already linked to another user
        var existingUser = userRepository.findByGoogleId(googleUser.googleId());
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            throw new AccountLinkingException("This Google account is already linked to another user");
        }

        // Get the current user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if user already has a Google account linked
        if (user.getGoogleId() != null) {
            throw new AccountLinkingException("Your account already has a Google account linked");
        }

        // Link the Google account
        user.setGoogleId(googleUser.googleId());
        user.setGoogleEmail(googleUser.email());

        userRepository.save(user);
        log.info("Linked Google account {} to user {} (IP: {})",
                googleUser.googleId(), userId, ipAddress);

        return ResponseEntity.ok(UserDto.from(user));
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

    @ExceptionHandler(InvalidGoogleAuthException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGoogleAuth(InvalidGoogleAuthException ex) {
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

    public static class InvalidGoogleAuthException extends RuntimeException {
        public InvalidGoogleAuthException(String message) {
            super(message);
        }
    }

    public static class AccountLinkingException extends RuntimeException {
        public AccountLinkingException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(AccountLinkingException.class)
    public ResponseEntity<ErrorResponse> handleAccountLinking(AccountLinkingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, ex.getMessage()));
    }
}

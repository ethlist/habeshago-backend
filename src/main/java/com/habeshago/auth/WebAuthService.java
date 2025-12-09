package com.habeshago.auth;

import com.habeshago.auth.dto.AuthResponse;
import com.habeshago.auth.dto.WebLoginRequest;
import com.habeshago.auth.dto.WebRegisterRequest;
import com.habeshago.user.User;
import com.habeshago.user.UserDto;
import com.habeshago.user.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public WebAuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Register a new web user with email and password
     */
    @Transactional
    public AuthResponse register(WebRegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        // Create new user
        User user = new User();
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPreferredLanguage("en");

        User savedUser = userRepository.save(user);

        // Generate JWT token
        String token = jwtService.generateTokenForWebUser(savedUser.getId());

        return new AuthResponse(token, UserDto.from(savedUser));
    }

    /**
     * Login with email and password
     */
    @Transactional(readOnly = true)
    public AuthResponse login(WebLoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // Verify password
        if (user.getPasswordHash() == null ||
            !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Generate JWT token
        String token = jwtService.generateTokenForWebUser(user.getId());

        return new AuthResponse(token, UserDto.from(user));
    }

    /**
     * Exception for duplicate email registration
     */
    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String message) {
            super(message);
        }
    }

    /**
     * Exception for invalid login credentials
     */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }
}

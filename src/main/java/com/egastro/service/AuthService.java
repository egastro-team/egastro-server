package com.egastro.service;

import com.egastro.config.properties.JwtProperties;
import com.egastro.dto.request.LoginRequest;
import com.egastro.dto.request.RegisterRequest;
import com.egastro.dto.response.LoginResponse;
import com.egastro.dto.response.UserResponse;
import com.egastro.entity.User;
import com.egastro.enums.Role;
import com.egastro.exception.ApiException;
import com.egastro.mapper.UserMapper;
import com.egastro.repository.UserRepository;
import com.egastro.security.CurrentUser;
import com.egastro.security.JwtService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private static final String DUPLICATE_EMAIL = "A user with this email already exists";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final long expiresIn;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       UserMapper userMapper, JwtProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        expiresIn = properties.ttl().toSeconds();
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict(DUPLICATE_EMAIL);
        }
        // BCrypt has a 72-byte input limit, including for multi-byte UTF-8 passwords.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.validation("password", "Password must be at most 72 characters long");
        }
        var user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        try {
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())) {
                    throw ApiException.conflict(DUPLICATE_EMAIL);
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(AuthService::incorrectCredentials);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw incorrectCredentials();
        }
        return new LoginResponse(jwtService.generateToken(user), "Bearer", expiresIn, userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return userRepository.findById(CurrentUser.id()).map(userMapper::toResponse)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static ApiException incorrectCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
    }
}

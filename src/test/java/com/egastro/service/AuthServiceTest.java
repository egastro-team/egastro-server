package com.egastro.service;

import com.egastro.config.properties.JwtProperties;
import com.egastro.dto.request.RegisterRequest;
import com.egastro.entity.User;
import com.egastro.enums.Role;
import com.egastro.exception.ApiException;
import com.egastro.mapper.UserMapper;
import com.egastro.repository.UserRepository;
import com.egastro.security.JwtService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;

import static com.egastro.testData.TestFixtures.*;
import static com.egastro.support.TestAssertions.assertApiError;
import static com.egastro.testData.RequestTestData.*;
import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository repository;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtService jwtService;
    @Spy private UserMapper mapper;
    @Spy private JwtProperties properties = authJwtProperties();
    @InjectMocks private AuthService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNormalizeEmailAndHashUnchangedPassword_When_Registering() {
        // GIVEN
        var request = paddedRegisterRequest();
        when(encoder.encode(request.password())).thenReturn("hash");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> persistedUser(invocation.getArgument(0)));

        // WHEN
        var result = service.register(request);

        // THEN
        var captor = ArgumentCaptor.forClass(User.class);
        verify(repository, times(1)).saveAndFlush(captor.capture());
        assertAll(() -> assertEquals("user@example.com", result.email()),
                () -> assertEquals("USER", result.role()),
                () -> assertEquals(Role.USER, captor.getValue().getRole()),
                () -> assertEquals("hash", captor.getValue().getPasswordHash()));
        verify(encoder, times(1)).encode(" Password123 ");
    }

    @Test
    void shouldRejectDuplicate_When_EmailAlreadyExists() {
        // GIVEN
        when(repository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        // WHEN
        var error = assertThrows(ApiException.class,
                () -> service.register(registerRequest()));

        // THEN
        assertApiError(error, CONFLICT, "A user with this email already exists");
        verifyNoInteractions(encoder);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectLongUtf8Password_When_CharacterCountFitsButBytesExceedBcryptLimit() {
        // GIVEN
        var request = longUtf8RegisterRequest();

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.register(request));

        // THEN
        assertApiError(error, BAD_REQUEST, "Validation failed");
        assertEquals("password", error.getFieldErrors().getFirst().field());
        verifyNoInteractions(encoder);
    }

    @Test
    void shouldTranslateDuplicate_When_ConcurrentRegistrationHitsUniqueConstraint() {
        // GIVEN
        var cause = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"), "uk_users_email");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict", cause));

        // WHEN
        var error = assertThrows(ApiException.class,
                () -> service.register(registerRequest()));

        // THEN
        assertApiError(error, CONFLICT, "A user with this email already exists");
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldRethrowPersistenceError_When_ItIsNotDuplicateKey(boolean hasConstraintCause) {
        // GIVEN
        Throwable cause = hasConstraintCause
                ? new ConstraintViolationException("failure", new SQLException("failure", "23502"), "required_column")
                : new IllegalStateException("database failure");
        var expected = new DataIntegrityViolationException("failure", cause);
        when(repository.saveAndFlush(any())).thenThrow(expected);

        // WHEN
        var actual = assertThrows(DataIntegrityViolationException.class,
                () -> service.register(registerRequest()));

        // THEN
        assertSame(expected, actual);
    }

    @Test
    void shouldReturnTokenAndUser_When_CredentialsMatch() {
        // GIVEN
        var user = user();
        when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("Password123", user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("test-token");

        // WHEN
        var result = service.login(paddedLoginRequest());

        // THEN
        assertAll(() -> assertEquals("test-token", result.accessToken()),
                () -> assertEquals("Bearer", result.tokenType()),
                () -> assertEquals(86400, result.expiresIn()),
                () -> assertEquals(1L, result.user().id()));
    }

    @Test
    void shouldRejectLogin_When_UserDoesNotExist() {
        // GIVEN
        var request = missingUserLoginRequest();

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.login(request));

        // THEN
        assertApiError(error, UNAUTHORIZED, "Incorrect email or password");
        verifyNoInteractions(encoder, jwtService);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidLoginPasswords")
    void shouldRejectLogin_When_PasswordWrongOrTooLong(String variant) {
        // GIVEN
        when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user()));
        var request = wrongPasswordLoginRequest(variant);

        // WHEN
        var error = assertThrows(ApiException.class,
                () -> service.login(request));

        // THEN
        assertApiError(error, UNAUTHORIZED, "Incorrect email or password");
        verifyNoInteractions(jwtService);
        verify(encoder, times(variant.equals("too-long") ? 0 : 1)).matches(anyString(), anyString());
    }

    @Test
    void shouldReturnCurrentUser_When_UserStillExists() {
        // GIVEN
        authenticate();
        when(repository.findById(1L)).thenReturn(Optional.of(user()));

        // WHEN
        var result = service.me();

        // THEN
        assertEquals(1L, result.id());
        assertEquals("user@example.com", result.email());
    }

    @Test
    void shouldRejectMe_When_TokenUserWasDeleted() {
        // GIVEN
        authenticate();

        // WHEN
        var error = assertThrows(ApiException.class, service::me);

        // THEN
        assertApiError(error, NOT_FOUND, "User not found");
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                userAuthentication());
    }
}

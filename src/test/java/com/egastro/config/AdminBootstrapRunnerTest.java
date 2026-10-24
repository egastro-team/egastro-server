package com.egastro.config;

import com.egastro.config.properties.AdminProperties;
import com.egastro.entity.User;
import com.egastro.enums.Role;
import com.egastro.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {
    @Mock private UserRepository repository;
    @Mock private PasswordEncoder encoder;
    @Spy private AdminProperties properties = adminProperties();
    @InjectMocks private AdminBootstrapRunner runner;

    @Test
    void shouldCreateHashedAdmin_When_BootstrapAccountMissing() {
        // GIVEN
        when(encoder.encode("TestAdmin123")).thenReturn("hash");

        // WHEN
        runner.run(null);

        // THEN
        var user = ArgumentCaptor.forClass(User.class);
        verify(repository, times(1)).save(user.capture());
        assertEquals("admin@example.com", user.getValue().getEmail());
        assertEquals(Role.ADMIN, user.getValue().getRole());
        assertEquals("hash", user.getValue().getPasswordHash());
    }

    @Test
    void shouldLeaveCredentialsAlone_When_BootstrapAccountAlreadyExists() {
        // GIVEN
        when(repository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(true);

        // WHEN
        runner.run(null);

        // THEN
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }
}

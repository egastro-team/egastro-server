package com.egastro.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock private JwtService jwtService;
    @Mock private FilterChain chain;
    @InjectMocks private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#validBearerHeaders")
    void shouldAuthenticateAndContinue_When_BearerTokenValid(String header) throws Exception {
        // GIVEN
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", header);
        var principal = userPrincipal();
        when(jwtService.parse("token")).thenReturn(Optional.of(principal));

        // WHEN
        filter.doFilter(request, response, chain);

        // THEN
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertSame(principal, auth.getPrincipal());
        assertTrue(auth.isAuthenticated());
        assertEquals(List.of("ROLE_USER"), auth.getAuthorities().stream().map(Object::toString).toList());
        assertNull(auth.getCredentials());
        verify(chain, times(1)).doFilter(request, response);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#missingBearerHeaders")
    void shouldContinueAnonymously_When_BearerHeaderMissing(String header) throws Exception {
        // GIVEN
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        if (header != null) request.addHeader("Authorization", header);

        // WHEN
        filter.doFilter(request, response, chain);

        // THEN
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldContinueAnonymously_When_BearerInvalid() throws Exception {
        // GIVEN
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid");
        when(jwtService.parse("invalid")).thenReturn(Optional.empty());

        // WHEN
        filter.doFilter(request, response, chain);

        // THEN
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldPreserveAuthentication_When_ContextAlreadyAuthenticated() throws Exception {
        // GIVEN
        var existing = stringAuthentication("existing");
        SecurityContextHolder.getContext().setAuthentication(existing);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer token");

        // WHEN
        filter.doFilter(request, response, chain);

        // THEN
        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
        verify(chain, times(1)).doFilter(request, response);
    }
}

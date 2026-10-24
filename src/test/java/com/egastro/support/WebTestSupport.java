package com.egastro.support;

import com.egastro.config.SecurityConfig;
import com.egastro.config.properties.CorsProperties;
import com.egastro.security.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Optional;

import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
public abstract class WebTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @MockitoBean protected JwtService jwtService;

    @BeforeEach
    void configureTokens() {
        when(jwtService.parse(USER_TOKEN))
                .thenReturn(Optional.of(userPrincipal()));
        when(jwtService.parse(ADMIN_TOKEN))
                .thenReturn(Optional.of(adminPrincipal()));
    }

    protected MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + USER_TOKEN);
    }

    protected MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + ADMIN_TOKEN);
    }

    protected JsonNode body(MvcResult result, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, result.getResponse().getStatus());
        assertNotNull(result.getResponse().getContentType());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(result.getResponse().getContentType())));
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode tree(Object expected) throws Exception {
        return json.readTree(json.writeValueAsString(expected));
    }

    protected JsonNode error(MvcResult result, int status, String message, String path) throws Exception {
        var body = body(result, status);
        assertAll(() -> assertEquals(status, body.path("status").asInt()),
                () -> assertEquals(message, body.path("message").asText()),
                () -> assertEquals(path, body.path("path").asText()),
                () -> assertDoesNotThrow(() -> Instant.parse(body.path("timestamp").asText())),
                () -> assertFalse(body.has("trace")),
                () -> assertFalse(body.has("exception")));
        return body;
    }

    protected void fieldError(JsonNode error, String field, String message) {
        assertTrue(error.path("fieldErrors").isArray());
        assertTrue(error.path("fieldErrors").valueStream().anyMatch(item ->
                field.equals(item.path("field").asText()) && message.equals(item.path("message").asText())),
                () -> "Missing field error " + field + ": " + message + " in " + error);
    }
}

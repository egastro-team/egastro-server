package com.egastro.service.recommendation;

import com.egastro.config.RestClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.util.*;
import java.util.stream.LongStream;
import static com.egastro.testData.RecommendationTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class MlRecommendationProviderTest {
    private MockRestServiceServer server;
    private MlRecommendationProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new MlRecommendationProvider(builder.build(), properties());
    }

    @Test
    void shouldSendSnakeCaseAndSortDeduplicate_When_ResponseIsValid() {
        // GIVEN
        server.expect(request -> {
            assertEquals("http://localhost:8000/v1/recommendations", request.getURI().toString());
            assertEquals(HttpMethod.POST, request.getMethod());
            assertEquals(MediaType.APPLICATION_JSON, request.getHeaders().getContentType());
            var body = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertEquals(new ObjectMapper().readTree("{\"cart_item_ids\":[3],\"max_k\":2}"), body);
        }).andRespond(withSuccess(MODEL_JSON, MediaType.APPLICATION_JSON));
        // WHEN
        var result = provider.recommend(List.of(3L, 3L), 2);
        // THEN
        assertEquals(List.of(7L, 9L), result.stream().map(ScoredRecommendation::productId).toList());
        assertEquals(List.of(4.0, 4.0), result.stream().map(ScoredRecommendation::score).toList());
        assertNull(result.getFirst().metrics());
        assertEquals(0.4, result.get(1).metrics().confidence());
        assertNull(result.get(1).metrics().support());
        server.verify();
    }

    @Test
    void shouldCapRequestAndExcludeEntireCart_When_CartExceedsFiftyProducts() {
        // GIVEN
        server.expect(request -> {
            var body = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertEquals(50, body.path("cart_item_ids").size());
            assertEquals(1, body.path("cart_item_ids").get(0).asLong());
            assertEquals(50, body.path("cart_item_ids").get(49).asLong());
            assertEquals(50, body.path("max_k").asInt());
        }).andRespond(withSuccess(MODEL_JSON, MediaType.APPLICATION_JSON));
        // WHEN
        var result = provider.recommend(LongStream.rangeClosed(1, 60).boxed().toList(), 100);
        // THEN
        assertTrue(result.isEmpty());
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.RecommendationTestData#modelEntries")
    void shouldIgnoreInvalidEntries_When_ResponseContainsNoUsableProducts(String json) {
        // GIVEN
        server.expect(request -> {}).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        // WHEN
        var result = provider.recommend(List.of(3L), 5);
        // THEN
        assertTrue(result.isEmpty());
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.RecommendationTestData#invalidModelBodies")
    void shouldThrowUnavailable_When_BodyIsMalformedOrMissing(String json) {
        // GIVEN
        server.expect(request -> {}).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        // WHEN
        var exception = assertThrows(RecommendationUnavailableException.class, () -> provider.recommend(List.of(3L), 5));
        // THEN
        assertTrue(exception.getMessage().startsWith("Recommendation service unavailable"));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.RecommendationTestData#failureStatuses")
    void shouldThrowUnavailable_When_StatusIsNotSuccessful(int status) {
        // GIVEN
        server.expect(request -> {}).andRespond(withStatus(HttpStatus.valueOf(status))
                .body(MODEL_JSON).contentType(MediaType.APPLICATION_JSON));
        // WHEN
        var exception = assertThrows(RecommendationUnavailableException.class, () -> provider.recommend(List.of(3L), 5));
        // THEN
        assertFalse(exception.getMessage().contains("product_id"));
        server.verify();
    }

    @Test
    void shouldThrowUnavailable_When_ConnectionFails() {
        // GIVEN
        server.expect(request -> {}).andRespond(withException(new IOException("private payload")));
        // WHEN
        var exception = assertThrows(RecommendationUnavailableException.class, () -> provider.recommend(List.of(3L), 5));
        // THEN
        assertFalse(exception.getMessage().contains("private payload"));
        server.verify();
    }

    @Test
    void shouldSkipHttp_When_UrlIsBlank() {
        // GIVEN
        var disabled = new MlRecommendationProvider(RestClient.create(), properties("  "));
        // WHEN
        var exception = assertThrows(RecommendationUnavailableException.class, () -> disabled.recommend(List.of(3L), 5));
        // THEN
        assertEquals("Recommendation service unavailable (IllegalStateException)", exception.getMessage());
    }

    @Test
    void shouldSkipHttp_When_UrlIsNull() {
        // GIVEN
        var disabled = new MlRecommendationProvider(RestClient.create(), properties(null));
        // WHEN
        var exception = assertThrows(RecommendationUnavailableException.class, () -> disabled.recommend(List.of(3L), 5));
        // THEN
        assertEquals("Recommendation service unavailable (IllegalStateException)", exception.getMessage());
    }

    @Test
    void shouldThrowUnavailable_When_ReadTimesOut() throws Exception {
        // GIVEN
        var http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        try {
            http.createContext("/v1/recommendations", exchange -> {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            http.start();
            var settings = new com.egastro.config.properties.RecommendationProperties(
                    new com.egastro.config.properties.RecommendationProperties.Ml(
                            "http://127.0.0.1:" + http.getAddress().getPort(), java.time.Duration.ofMillis(50),
                            java.time.Duration.ofMillis(50)), 5, 20);
            var timed = new MlRecommendationProvider(new RestClientConfig().recommendationRestClient(settings), settings);
            // WHEN
            var exception = assertThrows(RecommendationUnavailableException.class, () -> timed.recommend(List.of(3L), 5));
            // THEN
            assertTrue(exception.getMessage().startsWith("Recommendation service unavailable"));
        } finally {
            http.stop(0);
        }
    }
}

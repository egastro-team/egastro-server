package com.egastro.testData;

import com.egastro.config.properties.RecommendationProperties;
import com.egastro.dto.response.*;
import com.egastro.entity.Product;
import com.egastro.service.CartService;
import com.egastro.service.recommendation.ScoredRecommendation;
import org.junit.jupiter.params.provider.Arguments;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

public final class RecommendationTestData {
    private RecommendationTestData() { }

    public static RecommendationProperties properties() { return properties("http://localhost:8000"); }
    public static RecommendationProperties properties(String url) {
        return new RecommendationProperties(new RecommendationProperties.Ml(url,
                Duration.ofMillis(500), Duration.ofSeconds(2)), 5, 20);
    }
    public static Product product(long id, boolean available) {
        var product = TestFixtures.product();
        product.setId(id);
        product.setAvailable(available);
        return product;
    }
    public static List<CartService.CartProduct> cartItems() {
        return List.of(new CartService.CartProduct(product(3, true), 2));
    }
    public static ScoredRecommendation scored(long id, double score) {
        return new ScoredRecommendation(id, score, new RuleMetrics(null, 0.4, null));
    }
    public static CartRecommendationsResponse response(String source) {
        return new CartRecommendationsResponse(source, List.of(new RecommendedProductResponse(
                ResponseTestData.productResponse(), source.equals("MODEL") ? 3.42 : null,
                source.equals("MODEL") ? new RuleMetrics(0.012, 0.41, 3.42) : null)));
    }
    public static Stream<Arguments> limits() {
        return Stream.of(Arguments.of(null, 10), Arguments.of(-1, 2), Arguments.of(0, 2),
                Arguments.of(1, 2), Arguments.of(20, 40), Arguments.of(Integer.MAX_VALUE, 40));
    }
    public static Stream<Arguments> invalidLimits() {
        return Stream.of(Arguments.of(0, 20), Arguments.of(-1, 20), Arguments.of(5, 4));
    }
    public static Stream<Arguments> invalidTimeouts() {
        var positive = Duration.ofSeconds(1);
        return Stream.of(Arguments.of(null, positive), Arguments.of(Duration.ZERO, positive),
                Arguments.of(Duration.ofSeconds(-1), positive), Arguments.of(positive, null),
                Arguments.of(positive, Duration.ZERO), Arguments.of(positive, Duration.ofSeconds(-1)));
    }
    public static Stream<Arguments> invalidModelBodies() {
        return Stream.of("", "{}", "{\"recommendations\":null}", "invalid", "{\"recommendations\":{}}")
                .map(Arguments::of);
    }
    public static Stream<Arguments> failureStatuses() {
        return Stream.of(302, 400, 500, 503).map(Arguments::of);
    }
    public static Stream<Arguments> modelEntries() {
        return Stream.of(
                Arguments.of("{\"recommendations\":[]}"),
                Arguments.of("{\"recommendations\":[null,{}, {\"product_id\":0,\"score\":1},"
                        + "{\"product_id\":-1,\"score\":1},{\"product_id\":3,\"score\":99},"
                        + "{\"product_id\":4,\"score\":null},{\"product_id\":5,\"score\":\"NaN\"},"
                        + "{\"product_id\":6,\"score\":\"Infinity\"}]}"));
    }
    public static final String MODEL_JSON = """
            {"recommendations":[
              {"product_id":7,"score":1},
              {"product_id":9,"score":4,"metrics":{"confidence":0.4}},
              {"product_id":7,"score":4},
              {"product_id":9,"score":2},
              {"product_id":8,"score":-1}]}
            """;

    public static com.egastro.entity.User newUser() {
        var user = TestFixtures.user();
        user.setId(null);
        user.setEmail("recommendation-" + java.util.UUID.randomUUID() + "@example.com");
        return user;
    }

    public static com.egastro.entity.Category newCategory() {
        var category = TestFixtures.category();
        category.setId(null);
        category.setName("Recommendations " + java.util.UUID.randomUUID());
        return category;
    }

    public static com.egastro.entity.Order newOrder(com.egastro.entity.User user,
            com.egastro.enums.OrderStatus status, java.util.Map<Long, Integer> quantities) {
        var order = OrderTestData.order(status);
        order.setId(null);
        order.setUser(user);
        order.getItems().clear();
        quantities.forEach((id, quantity) -> {
            var item = new com.egastro.entity.OrderItem();
            item.setOrder(order);
            item.setProductId(id);
            item.setProductName("Snapshot");
            item.setUnitPrice(java.math.BigDecimal.ONE);
            item.setQuantity(quantity);
            order.getItems().add(item);
        });
        return order;
    }
}

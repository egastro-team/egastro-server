package com.egastro.testData;

import com.egastro.dto.request.CreateOrderRequest;
import com.egastro.dto.request.UpdateOrderStatusRequest;
import com.egastro.dto.response.*;
import com.egastro.entity.Order;
import com.egastro.entity.OrderItem;
import com.egastro.enums.OrderStatus;
import com.egastro.repository.OrderItemRepository;
import com.egastro.service.CartService;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.egastro.testData.TestFixtures.*;

public final class OrderTestData {
    public static final Instant CREATED_AT = Instant.parse("2026-10-24T12:00:00Z");
    public static final String CHECKOUT_JSON = """
            {"deliveryAddress":"  10 Main Street  ","contactPhone":"+380 (67) 123-45-67","comment":"  Call first  "}
            """;
    public static final String CONFIRMED_JSON = "{\"status\":\"CONFIRMED\"}";

    private OrderTestData() {
    }

    public static CreateOrderRequest checkoutRequest() {
        return new CreateOrderRequest("10 Main Street", "+380671234567", "Call first");
    }

    public static UpdateOrderStatusRequest statusRequest(OrderStatus status) {
        return new UpdateOrderStatusRequest(status);
    }

    public static Order order(OrderStatus status) {
        var order = new Order();
        order.setId(6L);
        order.setUser(user());
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("4.98"));
        order.setDeliveryAddress("10 Main Street");
        order.setContactPhone("+380671234567");
        order.setComment("Call first");
        order.setCreatedAt(CREATED_AT);
        var item = new OrderItem();
        item.setId(7L);
        item.setOrder(order);
        item.setProductId(3L);
        item.setProductName("Milk");
        item.setUnitPrice(new BigDecimal("2.49"));
        item.setQuantity(2);
        order.getItems().add(item);
        return order;
    }

    public static List<CartService.CartProduct> checkoutItems() {
        var first = product();
        first.setPrice(new BigDecimal("0.10"));
        var second = product();
        second.setId(9L);
        second.setName("Cheese");
        return List.of(new CartService.CartProduct(first, 3), new CartService.CartProduct(second, 2));
    }

    public static OrderItemRepository.ItemCount itemCount(Long id, long count) {
        return new OrderItemRepository.ItemCount() {
            public Long getOrderId() { return id; }
            public Long getItemsCount() { return count; }
        };
    }

    public static List<OrderItemResponse> itemResponses() {
        return List.of(new OrderItemResponse(3L, "Milk", new BigDecimal("2.49"), 2, new BigDecimal("4.98")));
    }

    public static OrderResponse orderResponse(OrderStatus status) {
        return new OrderResponse(6L, status, new BigDecimal("4.98"), "10 Main Street",
                "+380671234567", "Call first", CREATED_AT, itemResponses());
    }

    public static AdminOrderResponse adminResponse(OrderStatus status) {
        return new AdminOrderResponse(6L, status, new BigDecimal("4.98"), "10 Main Street",
                "+380671234567", "Call first", CREATED_AT, itemResponses(), 1L, "user@example.com");
    }

    public static OrderSummaryResponse summaryResponse() {
        return new OrderSummaryResponse(6L, OrderStatus.NEW, new BigDecimal("4.98"), 2, CREATED_AT);
    }

    public static AdminOrderSummaryResponse adminSummaryResponse() {
        return new AdminOrderSummaryResponse(6L, OrderStatus.NEW, new BigDecimal("4.98"),
                2, CREATED_AT, 1L, "user@example.com");
    }

    public static PageResponse<OrderSummaryResponse> orderPage() {
        return new PageResponse<>(List.of(summaryResponse()), 0, 20, 1, 1);
    }

    public static PageResponse<AdminOrderSummaryResponse> adminPage() {
        return new PageResponse<>(List.of(adminSummaryResponse()), 0, 20, 1, 1);
    }

    public static Stream<Arguments> invalidCheckoutRequests() {
        return Stream.of(
                Arguments.of(new CreateOrderRequest(null, "1234567890", null), "deliveryAddress", "Delivery address is required"),
                Arguments.of(new CreateOrderRequest("   ", "1234567890", null), "deliveryAddress", "Delivery address is required"),
                Arguments.of(new CreateOrderRequest("a".repeat(501), "1234567890", null), "deliveryAddress", "Delivery address must be at most 500 characters long"),
                Arguments.of(new CreateOrderRequest("A", null, null), "contactPhone", "Invalid phone number"),
                Arguments.of(new CreateOrderRequest("A", "  ", null), "contactPhone", "Invalid phone number"),
                Arguments.of(new CreateOrderRequest("A", "123456789", null), "contactPhone", "Invalid phone number"),
                Arguments.of(new CreateOrderRequest("A", "1234567890123456", null), "contactPhone", "Invalid phone number"),
                Arguments.of(new CreateOrderRequest("A", "12345+67890", null), "contactPhone", "Invalid phone number"),
                Arguments.of(new CreateOrderRequest("A", "123456789a", null), "contactPhone", "Invalid phone number"),
                Arguments.of(new CreateOrderRequest("A", "1234567890", "x".repeat(1001)), "comment", "Comment must be at most 1000 characters long"));
    }

    public static Stream<Arguments> validCheckoutRequests() {
        return Stream.of(
                Arguments.of(new CreateOrderRequest(" A ", "1234567890", null)),
                Arguments.of(new CreateOrderRequest("a".repeat(500), "+123456789012345", "x".repeat(1000))),
                Arguments.of(new CreateOrderRequest("A", "123456789012345", "   ")));
    }

    public static Stream<Arguments> orderStatuses() {
        return Stream.of(OrderStatus.values()).map(Arguments::of);
    }

    public static Stream<Arguments> nonNewStatuses() {
        return Stream.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED, OrderStatus.CANCELLED).map(Arguments::of);
    }

    public static Stream<Arguments> statusTransitions() {
        return Stream.of(OrderStatus.values()).flatMap(from -> Stream.of(OrderStatus.values())
                .map(to -> Arguments.of(from, to)));
    }

    public static Stream<Arguments> ownOrderOperations() {
        return Stream.of(Arguments.of("get"), Arguments.of("cancel"));
    }

    public static Stream<Arguments> adminOrderOperations() {
        return Stream.of(Arguments.of("get"), Arguments.of("update"));
    }

    public static Stream<Arguments> customerEndpoints() {
        return Stream.of(Arguments.of("POST", "/api/orders", CHECKOUT_JSON),
                Arguments.of("GET", "/api/orders", ""),
                Arguments.of("GET", "/api/orders/6", ""),
                Arguments.of("POST", "/api/orders/6/cancel", ""));
    }

    public static Stream<Arguments> adminEndpoints() {
        return Stream.of(Arguments.of("GET", "/api/admin/orders", ""),
                Arguments.of("GET", "/api/admin/orders/6", ""),
                Arguments.of("PATCH", "/api/admin/orders/6/status", CONFIRMED_JSON));
    }

    public static Stream<Arguments> invalidStatusBodies() {
        return Stream.of(Arguments.of("{}", "Status is required"),
                Arguments.of("{\"status\":null}", "Status is required"));
    }

    public static Stream<Arguments> malformedStatusBodies() {
        return Stream.of(Arguments.of("{\"status\":\"INVALID\"}"),
                Arguments.of("{\"status\":\"new\"}"), Arguments.of("{"), Arguments.of("null"));
    }

    public static Stream<Arguments> checkoutConflicts() {
        return Stream.of(Arguments.of("Cart is empty"),
                Arguments.of("Some products are unavailable: Milk, Cheese"));
    }

    public static Stream<Arguments> statusConflicts() {
        return Stream.of(Arguments.of("Order already has status CONFIRMED"),
                Arguments.of("Cannot change status of a COMPLETED order"),
                Arguments.of("Cannot change status of a CANCELLED order"));
    }

    public static Stream<Arguments> customerBadParameters() {
        return Stream.of(Arguments.of("/api/orders?page=abc", "/api/orders", "page"),
                Arguments.of("/api/orders?size=abc", "/api/orders", "size"),
                Arguments.of("/api/orders/abc", "/api/orders/abc", "id"));
    }

    public static Stream<Arguments> adminBadParameters() {
        return Stream.of(Arguments.of("/api/admin/orders?status=INVALID", "/api/admin/orders", "status"),
                Arguments.of("/api/admin/orders?page=abc", "/api/admin/orders", "page"),
                Arguments.of("/api/admin/orders?size=abc", "/api/admin/orders", "size"),
                Arguments.of("/api/admin/orders/abc", "/api/admin/orders/abc", "id"));
    }
}

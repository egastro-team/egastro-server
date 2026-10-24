package com.egastro.service;

import com.egastro.dto.request.CreateOrderRequest;
import com.egastro.entity.Order;
import com.egastro.enums.OrderStatus;
import com.egastro.exception.ApiException;
import com.egastro.mapper.OrderMapper;
import com.egastro.repository.OrderItemRepository;
import com.egastro.repository.OrderRepository;
import com.egastro.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.egastro.testData.OrderTestData.*;
import static com.egastro.testData.TestFixtures.user;
import static com.egastro.support.TestAssertions.assertApiError;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CartService cartService;
    @Mock private UserRepository userRepository;
    @Spy private OrderMapper mapper;
    @InjectMocks private OrderService service;

    @Test
    void shouldSnapshotCurrentProductsAndClearCartAfterSaving_When_CheckoutSucceeds() {
        // GIVEN
        var items = checkoutItems();
        when(cartService.getItems(1L)).thenReturn(items);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(6L);
            return order;
        });
        Instant before = Instant.now();

        // WHEN
        var response = service.create(1L, checkoutRequest());
        items.getFirst().product().setName("Renamed");
        items.getFirst().product().setPrice(new BigDecimal("99.00"));

        // THEN
        var captor = ArgumentCaptor.forClass(Order.class);
        var sequence = inOrder(orderRepository, cartService);
        sequence.verify(cartService).getItems(1L);
        sequence.verify(orderRepository).save(captor.capture());
        sequence.verify(cartService).clearItems(1L);
        var saved = captor.getValue();
        assertAll(() -> assertEquals(6L, response.id()),
                () -> assertEquals(OrderStatus.NEW, response.status()),
                () -> assertEquals(new BigDecimal("5.28"), response.totalAmount()),
                () -> assertEquals(List.of(3L, 9L), response.items().stream().map(i -> i.productId()).toList()),
                () -> assertEquals(List.of(3, 2), response.items().stream().map(i -> i.quantity()).toList()),
                () -> assertEquals("Milk", response.items().getFirst().productName()),
                () -> assertEquals(new BigDecimal("0.10"), response.items().getFirst().unitPrice()),
                () -> assertEquals(new BigDecimal("0.30"), response.items().getFirst().lineTotal()),
                () -> assertEquals(checkoutRequest().deliveryAddress(), saved.getDeliveryAddress()),
                () -> assertEquals(checkoutRequest().contactPhone(), saved.getContactPhone()),
                () -> assertEquals(checkoutRequest().comment(), saved.getComment()),
                () -> assertEquals(1L, saved.getUser().getId()),
                () -> assertFalse(saved.getCreatedAt().isBefore(before)),
                () -> assertFalse(saved.getCreatedAt().isAfter(Instant.now())));
        saved.getItems().forEach(item -> assertSame(saved, item.getOrder()));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#invalidCheckoutRequests")
    void shouldRejectInvalidInputBeforeReadingCart_When_ServiceCalledDirectly(
            CreateOrderRequest request, String field, String message) {
        // GIVEN
        // Invalid normalized input is supplied by the shared provider.

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.create(1L, request));

        // THEN
        assertApiError(error, BAD_REQUEST, "Validation failed");
        assertEquals(field, error.getFieldErrors().getFirst().field());
        assertEquals(message, error.getFieldErrors().getFirst().message());
        verifyNoInteractions(cartService, orderRepository, orderItemRepository, userRepository);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#validCheckoutRequests")
    void shouldAcceptValidationBounds_When_CheckingOut(CreateOrderRequest request) {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(checkoutItems());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        // WHEN
        var response = service.create(1L, request);

        // THEN
        assertEquals(request.deliveryAddress(), response.deliveryAddress());
        assertEquals(request.contactPhone(), response.contactPhone());
        assertEquals(request.comment(), response.comment());
        verify(cartService, times(1)).clearItems(1L);
    }

    @Test
    void shouldRejectEmptyCart_When_CheckingOut() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(List.of());

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.create(1L, checkoutRequest()));

        // THEN
        assertApiError(error, CONFLICT, "Cart is empty");
        verifyNoInteractions(orderRepository, userRepository);
        verify(cartService, never()).clearItems(any());
    }

    @Test
    void shouldListAllUnavailableNamesAndPreserveCart_When_AnyProductsUnavailable() {
        // GIVEN
        var items = checkoutItems();
        items.forEach(item -> item.product().setAvailable(false));
        when(cartService.getItems(1L)).thenReturn(items);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.create(1L, checkoutRequest()));

        // THEN
        assertApiError(error, CONFLICT, "Some products are unavailable: Milk, Cheese");
        verifyNoInteractions(orderRepository, userRepository);
        verify(cartService, never()).clearItems(any());
    }

    @Test
    void shouldRejectMissingUserWithoutSaving_When_UserDisappearsBeforeCheckout() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(checkoutItems());

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.create(1L, checkoutRequest()));

        // THEN
        assertApiError(error, NOT_FOUND, "User not found");
        verifyNoInteractions(orderRepository);
        verify(cartService, never()).clearItems(any());
    }

    @Test
    void shouldPreserveCart_When_SavingOrderFails() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(checkoutItems());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        var failure = new IllegalStateException("Save failed");
        when(orderRepository.save(any())).thenThrow(failure);

        // WHEN
        var error = assertThrows(IllegalStateException.class, () -> service.create(1L, checkoutRequest()));

        // THEN
        assertSame(failure, error);
        verify(cartService, never()).clearItems(any());
    }

    @Test
    void shouldPropagateClearFailure_When_CartCannotBeCleared() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(checkoutItems());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        var failure = new IllegalStateException("Clear failed");
        doThrow(failure).when(cartService).clearItems(1L);

        // WHEN
        var error = assertThrows(IllegalStateException.class, () -> service.create(1L, checkoutRequest()));

        // THEN
        assertSame(failure, error);
        verify(orderRepository, times(1)).save(any());
        verify(mapper, never()).toResponse(any());
    }

    @Test
    void shouldBatchCountsAndPreservePageMetadata_When_ListingOwnOrders() {
        // GIVEN
        var first = order(OrderStatus.NEW);
        var second = order(OrderStatus.CONFIRMED);
        second.setId(8L);
        when(orderRepository.findByUserId(eq(1L), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(first, second), invocation.getArgument(1, Pageable.class), 5));
        when(orderItemRepository.countItemsByOrderIds(List.of(6L, 8L)))
                .thenReturn(List.of(itemCount(6L, 2)));

        // WHEN
        var response = service.list(1L, 1, 2);

        // THEN
        assertEquals(List.of(2, 0), response.content().stream().map(i -> i.itemsCount()).toList());
        assertEquals(List.of(6L, 8L), response.content().stream().map(i -> i.id()).toList());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserId(eq(1L), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "id"), pageable.getValue().getSort());
        verify(orderItemRepository, times(1)).countItemsByOrderIds(List.of(6L, 8L));
    }

    @Test
    void shouldSkipCountQueryAndClampPagination_When_PageEmpty() {
        // GIVEN
        when(orderRepository.findByUserId(eq(1L), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(), invocation.getArgument(1, Pageable.class), 0));

        // WHEN
        var response = service.list(1L, -1, 101);

        // THEN
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.page());
        assertEquals(100, response.size());
        assertEquals(0, response.totalElements());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void shouldReturnSnapshots_When_GettingOwnOrder() {
        // GIVEN
        when(orderRepository.findWithItemsByIdAndUserId(6L, 1L)).thenReturn(Optional.of(order(OrderStatus.NEW)));

        // WHEN
        var response = service.get(1L, 6L);

        // THEN
        assertEquals(orderResponse(OrderStatus.NEW), response);
        verify(orderRepository, times(1)).findWithItemsByIdAndUserId(6L, 1L);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#ownOrderOperations")
    void shouldHideMissingOrForeignOrder_When_LookupDoesNotMatchUser(String operation) {
        // GIVEN
        when(orderRepository.findWithItemsByIdAndUserId(6L, 1L)).thenReturn(Optional.empty());

        // WHEN
        var error = assertThrows(ApiException.class, () -> {
            if (operation.equals("get")) service.get(1L, 6L);
            else service.cancel(1L, 6L);
        });

        // THEN
        assertApiError(error, NOT_FOUND, "Order not found");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldCancelWithoutRestoringCart_When_OrderIsNew() {
        // GIVEN
        var order = order(OrderStatus.NEW);
        when(orderRepository.findWithItemsByIdAndUserId(6L, 1L)).thenReturn(Optional.of(order));

        // WHEN
        var response = service.cancel(1L, 6L);

        // THEN
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(orderResponse(OrderStatus.CANCELLED), response);
        verifyNoInteractions(cartService);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#nonNewStatuses")
    void shouldPreserveStatus_When_CustomerCancelsNonNewOrder(OrderStatus status) {
        // GIVEN
        var order = order(status);
        when(orderRepository.findWithItemsByIdAndUserId(6L, 1L)).thenReturn(Optional.of(order));

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.cancel(1L, 6L));

        // THEN
        assertApiError(error, CONFLICT, "Order can no longer be cancelled");
        assertEquals(status, order.getStatus());
        verifyNoInteractions(cartService);
    }
}

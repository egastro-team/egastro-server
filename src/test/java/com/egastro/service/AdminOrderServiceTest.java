package com.egastro.service;

import com.egastro.dto.response.AdminOrderSummaryResponse;
import com.egastro.enums.OrderStatus;
import com.egastro.exception.ApiException;
import com.egastro.mapper.OrderMapper;
import com.egastro.repository.OrderItemRepository;
import com.egastro.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static com.egastro.testData.OrderTestData.*;
import static com.egastro.support.TestAssertions.assertApiError;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Spy private OrderMapper mapper;
    @InjectMocks private AdminOrderService service;

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#statusTransitions")
    void shouldApplyTransitionRules_When_AdminSetsStatus(OrderStatus from, OrderStatus to) {
        // GIVEN
        var order = order(from);
        when(orderRepository.findWithUserAndItemsById(6L)).thenReturn(Optional.of(order));
        boolean allowed = from != to && (from == OrderStatus.NEW || from == OrderStatus.CONFIRMED);

        // WHEN
        var response = allowed ? service.updateStatus(6L, statusRequest(to)) : null;
        var error = allowed ? null : assertThrows(ApiException.class,
                () -> service.updateStatus(6L, statusRequest(to)));

        // THEN
        if (allowed) {
            assertEquals(to, order.getStatus());
            assertEquals(adminResponse(to), response);
        } else {
            assertApiError(error, CONFLICT, from == to ? "Order already has status " + from
                    : "Cannot change status of a " + from + " order");
            assertEquals(from, order.getStatus());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void shouldRejectNullStatusBeforeLookup_When_ServiceCalledDirectly() {
        // GIVEN
        var request = statusRequest(null);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.updateStatus(6L, request));

        // THEN
        assertApiError(error, BAD_REQUEST, "Validation failed");
        assertEquals("status", error.getFieldErrors().getFirst().field());
        assertEquals("Status is required", error.getFieldErrors().getFirst().message());
        verifyNoInteractions(orderRepository);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#adminOrderOperations")
    void shouldReturnNotFound_When_OrderMissing(String operation) {
        // GIVEN
        when(orderRepository.findWithUserAndItemsById(6L)).thenReturn(Optional.empty());

        // WHEN
        var error = assertThrows(ApiException.class, () -> {
            if (operation.equals("get")) service.get(6L);
            else service.updateStatus(6L, statusRequest(OrderStatus.CONFIRMED));
        });

        // THEN
        assertApiError(error, NOT_FOUND, "Order not found");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldIncludeCustomer_When_OrderExists() {
        // GIVEN
        when(orderRepository.findWithUserAndItemsById(6L)).thenReturn(Optional.of(order(OrderStatus.NEW)));

        // WHEN
        var response = service.get(6L);

        // THEN
        assertEquals(adminResponse(OrderStatus.NEW), response);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#orderStatuses")
    void shouldPassFilterAndBatchCounts_When_ListingOrders(OrderStatus status) {
        // GIVEN
        var first = order(status);
        var second = order(status);
        second.setId(8L);
        when(orderRepository.findForAdmin(eq(status), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(first, second), invocation.getArgument(1, Pageable.class), 5));
        when(orderItemRepository.countItemsByOrderIds(List.of(6L, 8L))).thenReturn(List.of(itemCount(6L, 2)));

        // WHEN
        var response = service.list(status, 1, 2);

        // THEN
        assertEquals(List.of(2, 0), response.content().stream().map(AdminOrderSummaryResponse::itemsCount).toList());
        assertEquals("user@example.com", response.content().getFirst().userEmail());
        assertEquals(1L, response.content().getFirst().userId());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findForAdmin(eq(status), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "id"), pageable.getValue().getSort());
        verify(orderItemRepository, times(1)).countItemsByOrderIds(List.of(6L, 8L));
    }

    @Test
    void shouldSkipCountsAndClampPagination_When_UnfilteredPageEmpty() {
        // GIVEN
        when(orderRepository.findForAdmin(isNull(), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(), invocation.getArgument(1, Pageable.class), 0));

        // WHEN
        var response = service.list(null, -1, 0);

        // THEN
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.page());
        assertEquals(1, response.size());
        assertEquals(0, response.totalElements());
        verifyNoInteractions(orderItemRepository);
    }
}

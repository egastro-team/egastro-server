package com.egastro.mapper;

import com.egastro.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static com.egastro.testData.OrderTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderMapperTest {
    private final OrderMapper mapper = new OrderMapper();

    @Test
    void shouldMapAllSnapshotFieldsAndLineTotals_When_OrderHasItems() {
        // GIVEN
        var order = order(OrderStatus.NEW);

        // WHEN
        var response = mapper.toResponse(order);
        var admin = mapper.toAdminResponse(order);

        // THEN
        assertEquals(orderResponse(OrderStatus.NEW), response);
        assertEquals(adminResponse(OrderStatus.NEW), admin);
    }

    @Test
    void shouldUseSuppliedCountWithoutLoadingItems_When_MappingSummaries() {
        // GIVEN
        var order = order(OrderStatus.NEW);
        order.setItems(null);

        // WHEN
        var response = mapper.toSummaryResponse(order, 2);
        var admin = mapper.toAdminSummaryResponse(order, 2);

        // THEN
        assertEquals(summaryResponse(), response);
        assertEquals(adminSummaryResponse(), admin);
    }

    @Test
    void shouldPreserveNullCommentAndEmptyItems_When_OptionalValuesAbsent() {
        // GIVEN
        var order = order(OrderStatus.NEW);
        order.setComment(null);
        order.getItems().clear();

        // WHEN
        var response = mapper.toResponse(order);

        // THEN
        assertNull(response.comment());
        assertTrue(response.items().isEmpty());
    }
}

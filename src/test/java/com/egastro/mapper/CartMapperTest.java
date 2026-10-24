package com.egastro.mapper;

import com.egastro.dto.response.CartItemResponse;
import com.egastro.enums.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.egastro.testData.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class CartMapperTest {
    private final CartMapper mapper = new CartMapper();

    @Test
    void shouldReturnZeroTotals_When_CartEmpty() {
        // GIVEN
        var items = List.<com.egastro.entity.CartItem>of();

        // WHEN
        var response = mapper.toResponse(items);

        // THEN
        assertTrue(response.items().isEmpty());
        assertEquals(0, response.totalQuantity());
        assertEquals(new BigDecimal("0.00"), response.totalAmount());
    }

    @Test
    void shouldUseCurrentProductValuesAndPreserveOrder_When_MappingMultipleItems() {
        // GIVEN
        var items = mixedCartItems();

        // WHEN
        var response = mapper.toResponse(items);

        // THEN
        assertAll(() -> assertEquals(5, response.totalQuantity()),
                () -> assertEquals(new BigDecimal("5.28"), response.totalAmount()),
                () -> assertEquals(List.of(3L, 9L), response.items().stream().map(CartItemResponse::productId).toList()),
                () -> assertEquals(new BigDecimal("0.30"), response.items().getFirst().lineTotal()),
                () -> assertEquals(new BigDecimal("0.10"), response.items().getFirst().unitPrice()),
                () -> assertEquals("Milk", response.items().getFirst().name()),
                () -> assertEquals(UnitOfMeasure.L, response.items().getFirst().unitOfMeasure()),
                () -> assertEquals("https://example.com/milk.png", response.items().getFirst().imageUrl()),
                () -> assertFalse(response.items().getLast().available()),
                () -> assertNull(response.items().getLast().imageUrl()));
    }
}

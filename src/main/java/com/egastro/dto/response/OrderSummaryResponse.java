package com.egastro.dto.response;

import com.egastro.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(Long id, OrderStatus status, BigDecimal totalAmount,
                                   int itemsCount, Instant createdAt) {
}

package com.egastro.dto.response;

import com.egastro.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderResponse(Long id, OrderStatus status, BigDecimal totalAmount, String deliveryAddress,
                                 String contactPhone, String comment, Instant createdAt,
                                 List<OrderItemResponse> items, Long userId, String userEmail) {
}

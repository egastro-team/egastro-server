package com.egastro.mapper;

import com.egastro.dto.response.AdminOrderResponse;
import com.egastro.dto.response.AdminOrderSummaryResponse;
import com.egastro.dto.response.OrderItemResponse;
import com.egastro.dto.response.OrderResponse;
import com.egastro.dto.response.OrderSummaryResponse;
import com.egastro.entity.Order;
import com.egastro.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderMapper {
    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                order.getDeliveryAddress(), order.getContactPhone(), order.getComment(), order.getCreatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList());
    }

    public OrderSummaryResponse toSummaryResponse(Order order, int itemsCount) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                itemsCount, order.getCreatedAt());
    }

    public AdminOrderResponse toAdminResponse(Order order) {
        var response = toResponse(order);
        return new AdminOrderResponse(response.id(), response.status(), response.totalAmount(),
                response.deliveryAddress(), response.contactPhone(), response.comment(), response.createdAt(),
                response.items(), order.getUser().getId(), order.getUser().getEmail());
    }

    public AdminOrderSummaryResponse toAdminSummaryResponse(Order order, int itemsCount) {
        return new AdminOrderSummaryResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                itemsCount, order.getCreatedAt(), order.getUser().getId(), order.getUser().getEmail());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(),
                item.getQuantity(), item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}

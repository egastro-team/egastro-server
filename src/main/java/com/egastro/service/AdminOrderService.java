package com.egastro.service;

import com.egastro.dto.request.UpdateOrderStatusRequest;
import com.egastro.dto.response.AdminOrderResponse;
import com.egastro.dto.response.AdminOrderSummaryResponse;
import com.egastro.dto.response.PageResponse;
import com.egastro.entity.Order;
import com.egastro.enums.OrderStatus;
import com.egastro.exception.ApiException;
import com.egastro.mapper.OrderMapper;
import com.egastro.repository.OrderItemRepository;
import com.egastro.repository.OrderRepository;
import com.egastro.util.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;

    public PageResponse<AdminOrderSummaryResponse> list(OrderStatus status, int page, int size) {
        var orders = orderRepository.findForAdmin(status,
                PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        if (orders.isEmpty()) {
            return PageResponse.from(orders.map(order -> orderMapper.toAdminSummaryResponse(order, 0)));
        }
        var counts = orderItemRepository.countItemsByOrderIds(orders.map(Order::getId).getContent()).stream()
                .collect(Collectors.toMap(OrderItemRepository.ItemCount::getOrderId,
                        count -> Math.toIntExact(count.getItemsCount())));
        return PageResponse.from(orders.map(order ->
                orderMapper.toAdminSummaryResponse(order, counts.getOrDefault(order.getId(), 0))));
    }

    public AdminOrderResponse get(Long id) {
        return orderMapper.toAdminResponse(findOrder(id));
    }

    @Transactional
    public AdminOrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        if (request.status() == null) {
            throw ApiException.validation("status", "Status is required");
        }
        var order = findOrder(id);
        if (order.getStatus() == request.status()) {
            throw ApiException.conflict("Order already has status " + order.getStatus());
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.conflict("Cannot change status of a " + order.getStatus() + " order");
        }
        order.setStatus(request.status());
        return orderMapper.toAdminResponse(order);
    }

    private Order findOrder(Long id) {
        return orderRepository.findWithUserAndItemsById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }
}

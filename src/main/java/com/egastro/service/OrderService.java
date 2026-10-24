package com.egastro.service;

import com.egastro.dto.request.CreateOrderRequest;
import com.egastro.dto.response.OrderResponse;
import com.egastro.dto.response.OrderSummaryResponse;
import com.egastro.dto.response.PageResponse;
import com.egastro.entity.Order;
import com.egastro.entity.OrderItem;
import com.egastro.entity.Product;
import com.egastro.enums.OrderStatus;
import com.egastro.exception.ApiException;
import com.egastro.mapper.OrderMapper;
import com.egastro.repository.OrderItemRepository;
import com.egastro.repository.OrderRepository;
import com.egastro.repository.UserRepository;
import com.egastro.util.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest request) {
        validate(request);
        var cartItems = cartService.getItems(userId);
        if (cartItems.isEmpty()) {
            throw ApiException.conflict("Cart is empty");
        }
        String unavailable = cartItems.stream().map(CartService.CartProduct::product)
                .filter(product -> !product.isAvailable()).map(Product::getName)
                .collect(Collectors.joining(", "));
        if (!unavailable.isEmpty()) {
            throw ApiException.conflict("Some products are unavailable: " + unavailable);
        }
        var order = new Order();
        order.setUser(userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found")));
        order.setStatus(OrderStatus.NEW);
        order.setDeliveryAddress(request.deliveryAddress());
        order.setContactPhone(request.contactPhone());
        order.setComment(request.comment());
        order.setCreatedAt(Instant.now());
        BigDecimal total = new BigDecimal("0.00");
        for (var cartItem : cartItems) {
            var product = cartItem.product();
            var item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(cartItem.quantity());
            order.getItems().add(item);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.setTotalAmount(total);
        orderRepository.save(order);
        cartService.clearItems(userId);
        return orderMapper.toResponse(order);
    }

    public PageResponse<OrderSummaryResponse> list(Long userId, int page, int size) {
        var orders = orderRepository.findByUserId(userId,
                PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        if (orders.isEmpty()) {
            return PageResponse.from(orders.map(order -> orderMapper.toSummaryResponse(order, 0)));
        }
        var counts = orderItemRepository.countItemsByOrderIds(orders.map(Order::getId).getContent()).stream()
                .collect(Collectors.toMap(OrderItemRepository.ItemCount::getOrderId,
                        count -> Math.toIntExact(count.getItemsCount())));
        return PageResponse.from(orders.map(order ->
                orderMapper.toSummaryResponse(order, counts.getOrDefault(order.getId(), 0))));
    }

    public OrderResponse get(Long userId, Long id) {
        return orderMapper.toResponse(findOwnOrder(userId, id));
    }

    @Transactional
    public OrderResponse cancel(Long userId, Long id) {
        var order = findOwnOrder(userId, id);
        if (order.getStatus() != OrderStatus.NEW) {
            throw ApiException.conflict("Order can no longer be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderMapper.toResponse(order);
    }

    private Order findOwnOrder(Long userId, Long id) {
        return orderRepository.findWithItemsByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private void validate(CreateOrderRequest request) {
        if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()) {
            throw ApiException.validation("deliveryAddress", "Delivery address is required");
        }
        if (request.deliveryAddress().length() > 500) {
            throw ApiException.validation("deliveryAddress", "Delivery address must be at most 500 characters long");
        }
        if (request.contactPhone() == null || !request.contactPhone().matches("^\\+?[0-9]{10,15}$")) {
            throw ApiException.validation("contactPhone", "Invalid phone number");
        }
        if (request.comment() != null && request.comment().length() > 1000) {
            throw ApiException.validation("comment", "Comment must be at most 1000 characters long");
        }
    }
}

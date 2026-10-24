package com.egastro.controller;

import com.egastro.dto.request.CreateOrderRequest;
import com.egastro.dto.response.OrderResponse;
import com.egastro.dto.response.OrderSummaryResponse;
import com.egastro.dto.response.PageResponse;
import com.egastro.security.CurrentUser;
import com.egastro.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Customer orders")
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Check out the current user's cart")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        var order = orderService.create(CurrentUser.id(), request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping
    @Operation(summary = "List the current user's orders")
    public PageResponse<OrderSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return orderService.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the current user's order details")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel the current user's new order")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(CurrentUser.id(), id);
    }
}

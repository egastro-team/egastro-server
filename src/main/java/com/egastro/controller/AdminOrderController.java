package com.egastro.controller;

import com.egastro.dto.request.UpdateOrderStatusRequest;
import com.egastro.dto.response.AdminOrderResponse;
import com.egastro.dto.response.AdminOrderSummaryResponse;
import com.egastro.dto.response.PageResponse;
import com.egastro.enums.OrderStatus;
import com.egastro.service.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin orders")
public class AdminOrderController {
    private final AdminOrderService adminOrderService;

    @GetMapping
    @Operation(summary = "List all orders with an optional status filter")
    public PageResponse<AdminOrderSummaryResponse> list(@RequestParam(required = false) OrderStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return adminOrderService.list(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order's details and customer")
    public AdminOrderResponse get(@PathVariable Long id) {
        return adminOrderService.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change an order's status")
    public AdminOrderResponse updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody UpdateOrderStatusRequest request) {
        return adminOrderService.updateStatus(id, request);
    }
}

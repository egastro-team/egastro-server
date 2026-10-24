package com.egastro.controller;

import com.egastro.dto.response.PageResponse;
import com.egastro.dto.response.ProductResponse;
import com.egastro.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {
    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Browse products with category, name filter and sorting")
    public PageResponse<ProductResponse> list(@RequestParam(required = false) Long category,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) String sort,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return productService.list(category, q, sort, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product details")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }
}

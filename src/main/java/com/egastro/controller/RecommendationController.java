package com.egastro.controller;

import com.egastro.dto.response.CartRecommendationsResponse;
import com.egastro.security.CurrentUser;
import com.egastro.service.SmartCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart/recommendations")
@RequiredArgsConstructor
@Tag(name = "Cart recommendations")
public class RecommendationController {
    private final SmartCartService smartCartService;

    @GetMapping
    @Operation(summary = "Get related products for your cart")
    public CartRecommendationsResponse recommend(@RequestParam(required = false) Integer limit) {
        return smartCartService.recommend(CurrentUser.id(), limit);
    }
}

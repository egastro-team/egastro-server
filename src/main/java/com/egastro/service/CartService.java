package com.egastro.service;

import com.egastro.dto.request.AddCartItemRequest;
import com.egastro.dto.request.UpdateCartItemRequest;
import com.egastro.dto.response.CartResponse;
import com.egastro.entity.Cart;
import com.egastro.entity.CartItem;
import com.egastro.entity.Product;
import com.egastro.event.ProductDeletedEvent;
import com.egastro.exception.ApiException;
import com.egastro.mapper.CartMapper;
import com.egastro.repository.CartItemRepository;
import com.egastro.repository.CartRepository;
import com.egastro.repository.ProductRepository;
import com.egastro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse get(Long userId) {
        getOrCreateCart(userId);
        return response(userId);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        validateQuantity(request.quantity());
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isAvailable()) {
            throw ApiException.conflict("Product is currently unavailable");
        }
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElseGet(() -> {
                    var newItem = new CartItem();
                    newItem.setCart(cart);
                    newItem.setProduct(product);
                    return newItem;
                });
        int quantity = item.getQuantity() + request.quantity();
        validateQuantity(quantity);
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return response(userId);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        validateQuantity(request.quantity());
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Item not found in cart"));
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("Item not found in cart"));
        item.setQuantity(request.quantity());
        return response(userId);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        cartRepository.findByUserId(userId).flatMap(cart ->
                cartItemRepository.findByCartIdAndProductId(cart.getId(), productId))
                .ifPresent(cartItemRepository::delete);
        return response(userId);
    }

    /** Returns current products and quantities without creating a cart; use within the caller's transaction. */
    public List<CartProduct> getItems(Long userId) {
        return cartItemRepository.findItemsWithProductsByUserId(userId).stream()
                .map(item -> new CartProduct(item.getProduct(), item.getQuantity())).toList();
    }

    @Transactional
    public void clearItems(Long userId) {
        cartRepository.findByUserId(userId)
                .ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    @EventListener
    @Transactional
    public void onProductDeleted(ProductDeletedEvent event) {
        cartItemRepository.deleteByProductId(event.productId());
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("User not found"));
            var cart = new Cart();
            cart.setUser(user);
            return cartRepository.save(cart);
        });
    }

    private CartResponse response(Long userId) {
        return cartMapper.toResponse(cartItemRepository.findItemsWithProductsByUserId(userId));
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null) {
            throw ApiException.validation("quantity", "Quantity is required");
        }
        if (quantity < 1) {
            throw ApiException.validation("quantity", "Quantity must be at least 1");
        }
        if (quantity > 99) {
            throw ApiException.validation("quantity", "Maximum quantity per product is 99");
        }
    }

    public record CartProduct(Product product, int quantity) {
    }
}

package com.egastro.testData;

import com.egastro.entity.*;
import com.egastro.enums.Role;
import com.egastro.enums.UnitOfMeasure;
import com.egastro.event.ProductDeletedEvent;

import java.math.BigDecimal;
import java.util.List;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static User user() {
        var user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded-password");
        user.setRole(Role.USER);
        return user;
    }

    public static Category category() {
        var category = new Category();
        category.setId(2L);
        category.setName("Dairy");
        return category;
    }

    public static Product product() {
        var product = new Product();
        product.setId(3L);
        product.setName("Milk");
        product.setDescription("Fresh milk");
        product.setCategory(category());
        product.setPrice(new BigDecimal("2.49"));
        product.setUnitOfMeasure(UnitOfMeasure.L);
        product.setImageUrl("https://example.com/milk.png");
        return product;
    }

    public static Cart cart() {
        var cart = new Cart();
        cart.setId(4L);
        cart.setUser(user());
        return cart;
    }

    public static CartItem item(int quantity) {
        var item = new CartItem();
        item.setId(5L);
        item.setCart(cart());
        item.setProduct(product());
        item.setQuantity(quantity);
        return item;
    }

    public static Product unavailableProduct() {
        var product = product();
        product.setAvailable(false);
        return product;
    }

    public static CartItem unavailableItem(int quantity) {
        var item = item(quantity);
        item.setProduct(unavailableProduct());
        return item;
    }

    public static List<CartItem> mixedCartItems() {
        var first = item(3);
        first.getProduct().setPrice(new BigDecimal("0.10"));
        var second = unavailableItem(2);
        second.setId(6L);
        second.getProduct().setId(9L);
        second.getProduct().setImageUrl(null);
        return List.of(first, second);
    }

    public static User userWithPasswordHash(String hash) {
        var user = user();
        user.setPasswordHash(hash);
        return user;
    }

    public static User persistedUser(User user) {
        user.setId(1L);
        return user;
    }

    public static ProductDeletedEvent productDeletedEvent() {
        return new ProductDeletedEvent(3L);
    }
}

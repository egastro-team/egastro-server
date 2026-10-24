package com.egastro.repository;

import com.egastro.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Query("select i from CartItem i join fetch i.product where i.cart.user.id = :userId order by i.id asc")
    List<CartItem> findItemsWithProductsByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem i where i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem i where i.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);
}

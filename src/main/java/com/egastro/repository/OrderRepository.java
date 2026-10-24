package com.egastro.repository;

import com.egastro.entity.Order;
import com.egastro.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("select o from CustomerOrder o where (:status is null or o.status = :status)")
    Page<Order> findForAdmin(@Param("status") OrderStatus status, Pageable pageable);

    @Query("select o from CustomerOrder o left join fetch o.items where o.id = :id and o.user.id = :userId")
    Optional<Order> findWithItemsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select o from CustomerOrder o join fetch o.user left join fetch o.items where o.id = :id")
    Optional<Order> findWithUserAndItemsById(@Param("id") Long id);
}

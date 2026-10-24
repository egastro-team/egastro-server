package com.egastro.repository;

import com.egastro.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("""
            select p.id from OrderItem i join i.order o, Product p
            where p.id = i.productId and p.available = true
              and o.status <> com.egastro.enums.OrderStatus.CANCELLED and p.id not in :excluded
            group by p.id order by sum(i.quantity) desc, p.id asc
            """)
    List<Long> findPopularProductIds(@Param("excluded") Collection<Long> excluded,
                                     Pageable pageable);

    @Query("select i.order.id as orderId, sum(i.quantity) as itemsCount from OrderItem i "
            + "where i.order.id in :orderIds group by i.order.id")
    List<ItemCount> countItemsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    interface ItemCount {
        Long getOrderId();
        Long getItemsCount();
    }
}

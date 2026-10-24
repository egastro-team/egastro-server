package com.egastro.repository;

import com.egastro.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("select i.order.id as orderId, sum(i.quantity) as itemsCount from OrderItem i "
            + "where i.order.id in :orderIds group by i.order.id")
    List<ItemCount> countItemsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    interface ItemCount {
        Long getOrderId();
        Long getItemsCount();
    }
}

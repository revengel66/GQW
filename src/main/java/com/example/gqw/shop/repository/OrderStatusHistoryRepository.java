package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.OrderStatusHistory;
import com.example.gqw.shop.entity.ShopOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderOrderByChangedAtAsc(ShopOrder order);

    List<OrderStatusHistory> findByOrderInOrderByChangedAtAsc(List<ShopOrder> orders);

    boolean existsByOrder(ShopOrder order);
}

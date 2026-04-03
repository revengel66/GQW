package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.ShopOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(ShopOrder order);
}


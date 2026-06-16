package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(ShopOrder order);

    void deleteByOrder(ShopOrder order);

    List<OrderItem> findByProductInOrderByOrder_CreatedAtDesc(List<Product> products);

    boolean existsByOrderUserAndProductAndOrderStatus(ShopUser user, Product product, OrderStatus status);

    boolean existsByOrderUserAndProductAndOrderStatusIn(ShopUser user, Product product, Collection<OrderStatus> statuses);

    boolean existsByProduct(Product product);
}


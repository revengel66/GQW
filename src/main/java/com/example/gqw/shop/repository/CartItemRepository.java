package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUser(ShopUser user);

    List<CartItem> findBySessionId(String sessionId);

    Optional<CartItem> findByUserAndProduct(ShopUser user, Product product);

    Optional<CartItem> findBySessionIdAndProduct(String sessionId, Product product);
}


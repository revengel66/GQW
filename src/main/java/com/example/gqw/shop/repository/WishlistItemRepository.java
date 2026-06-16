package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.WishlistItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUser(ShopUser user);

    Optional<WishlistItem> findByUserAndProduct(ShopUser user, Product product);

    Optional<WishlistItem> findByIdAndUser(Long id, ShopUser user);

    List<WishlistItem> findBySessionId(String sessionId);

    Optional<WishlistItem> findBySessionIdAndProduct(String sessionId, Product product);

    Optional<WishlistItem> findByIdAndSessionId(Long id, String sessionId);

    @Transactional
    long deleteByCreatedAtBeforeAndSessionIdIsNotNull(Instant threshold);
}


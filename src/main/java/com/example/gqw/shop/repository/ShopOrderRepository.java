package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {

    List<ShopOrder> findByUserOrderByCreatedAtDesc(ShopUser user);

    Optional<ShopOrder> findByIdAndUser(Long id, ShopUser user);
}


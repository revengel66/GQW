package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.ShopUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopUserRepository extends JpaRepository<ShopUser, Long> {

    Optional<ShopUser> findByUsername(String username);
}


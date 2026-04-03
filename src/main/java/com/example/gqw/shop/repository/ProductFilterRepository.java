package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.ProductFilter;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFilterRepository extends JpaRepository<ProductFilter, Long> {

    Optional<ProductFilter> findByCode(String code);
}


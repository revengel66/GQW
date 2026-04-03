package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCharacteristicRepository extends JpaRepository<ProductCharacteristic, Long> {

    List<ProductCharacteristic> findByProductOrderBySortOrderAsc(Product product);
}


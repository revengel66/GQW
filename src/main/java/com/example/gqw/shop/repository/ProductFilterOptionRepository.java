package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductFilterOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFilterOptionRepository extends JpaRepository<ProductFilterOption, Long> {

    List<ProductFilterOption> findByProduct(Product product);
}


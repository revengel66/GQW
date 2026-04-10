package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductFilterOption;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductFilterOptionRepository extends JpaRepository<ProductFilterOption, Long> {

    List<ProductFilterOption> findByProduct(Product product);

    List<ProductFilterOption> findByProductIn(List<Product> products);

    Optional<ProductFilterOption> findByProductAndFilterOption(Product product, FilterOption filterOption);

    boolean existsByProductAndFilterOption(Product product, FilterOption filterOption);

    @Modifying
    @Transactional
    @Query("""
        delete from ProductFilterOption pfo
        where pfo.product = :product
        """)
    void deleteByProduct(@Param("product") Product product);
}


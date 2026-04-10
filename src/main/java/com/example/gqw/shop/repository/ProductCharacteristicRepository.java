package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductCharacteristicRepository extends JpaRepository<ProductCharacteristic, Long> {

    List<ProductCharacteristic> findByProductOrderBySortOrderAsc(Product product);

    List<ProductCharacteristic> findByProductIdOrderBySortOrderAsc(Long productId);

    @Query("""
        select c
        from ProductCharacteristic c
        where c.product in :products
        order by c.product.id asc, c.sortOrder asc
        """)
    List<ProductCharacteristic> findForProductsOrdered(@Param("products") List<Product> products);

    @Modifying
    @Transactional
    @Query("""
        delete from ProductCharacteristic c
        where c.product = :product
        """)
    void deleteByProduct(@Param("product") Product product);
}


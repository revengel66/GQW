package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductOrderBySortOrderAscIdAsc(Product product);

    @Modifying
    @Transactional
    @Query("""
        delete from ProductImage i
        where i.product = :product
        """)
    void deleteByProduct(@Param("product") Product product);
}

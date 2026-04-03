package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    Page<Product> findDistinctByCategoriesIn(List<Category> categories, Pageable pageable);

    List<Product> findTop12ByOrderByCreatedAtDesc();
}


package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySlugAndIsPublishedTrue(String slug);

    Optional<Product> findByName(String name);

    Optional<Product> findByArticleIgnoreCase(String article);

    Page<Product> findDistinctByCategoriesIn(List<Category> categories, Pageable pageable);

    Page<Product> findDistinctByCategoriesInAndIsPublishedTrue(List<Category> categories, Pageable pageable);

    List<Product> findTop20ByOrderByCreatedAtDesc();

    List<Product> findTop20ByIsPublishedTrueOrderByCreatedAtDesc();
}


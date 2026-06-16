package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    Optional<Category> findBySlugAndIsPublishedTrue(String slug);

    Optional<Category> findByName(String name);

    List<Category> findByParentIsNullOrderByIdAsc();

    List<Category> findByParentIsNullAndIsPublishedTrueOrderByIdAsc();

    List<Category> findByParentOrderByIdAsc(Category parent);

    List<Category> findByParentAndIsPublishedTrueOrderByIdAsc(Category parent);
}


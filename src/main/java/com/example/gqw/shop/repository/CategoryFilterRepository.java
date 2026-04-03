package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.CategoryFilter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryFilterRepository extends JpaRepository<CategoryFilter, Long> {

    List<CategoryFilter> findByCategory(Category category);
}


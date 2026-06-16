package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.CategoryFilter;
import com.example.gqw.shop.entity.ProductFilter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CategoryFilterRepository extends JpaRepository<CategoryFilter, Long> {
	
    List<CategoryFilter> findByCategory(Category category);

    List<CategoryFilter> findByCategoryIn(List<Category> categories);

    List<CategoryFilter> findByFilter(ProductFilter filter);

    Optional<CategoryFilter> findByCategoryAndFilter(Category category, ProductFilter filter);

    @Modifying
    @Transactional
    @Query("""
        delete from CategoryFilter cf
        where cf.filter = :filter
        """)
    void deleteByFilter(@Param("filter") ProductFilter filter);
}


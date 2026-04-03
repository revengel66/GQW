package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.ProductFilter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilterOptionRepository extends JpaRepository<FilterOption, Long> {

    List<FilterOption> findByFilter(ProductFilter filter);
}


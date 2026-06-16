package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.ProductFilter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilterOptionRepository extends JpaRepository<FilterOption, Long> {

    List<FilterOption> findByFilter(ProductFilter filter);

    List<FilterOption> findByFilterIn(List<ProductFilter> filters);

    Optional<FilterOption> findByFilterAndCode(ProductFilter filter, String code);

    Optional<FilterOption> findByFilterAndValueIgnoreCase(ProductFilter filter, String value);
}


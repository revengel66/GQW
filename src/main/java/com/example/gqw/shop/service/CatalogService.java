package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import com.example.gqw.shop.entity.ProductFilterOption;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.repository.CategoryRepository;
import com.example.gqw.shop.repository.ProductCharacteristicRepository;
import com.example.gqw.shop.repository.ProductFilterOptionRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ReviewRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCharacteristicRepository characteristicRepository;
    private final ProductFilterOptionRepository productFilterOptionRepository;
    private final ReviewRepository reviewRepository;

    public CatalogService(
        CategoryRepository categoryRepository,
        ProductRepository productRepository,
        ProductCharacteristicRepository characteristicRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        ReviewRepository reviewRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.characteristicRepository = characteristicRepository;
        this.productFilterOptionRepository = productFilterOptionRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<Product> latestProducts() {
        return productRepository.findTop12ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<Product> catalog(int page, int size, String sortBy) {
        return productRepository.findAll(PageRequest.of(page, size, resolveSort(sortBy)));
    }

    @Transactional(readOnly = true)
    public Category categoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
            .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
    }

    @Transactional(readOnly = true)
    public Page<Product> productsByCategory(String categorySlug, int page, int size, String sortBy) {
        Category category = categoryBySlug(categorySlug);
        return productRepository.findDistinctByCategoriesIn(
            List.of(category),
            PageRequest.of(page, size, resolveSort(sortBy))
        );
    }

    @Transactional(readOnly = true)
    public Product productBySlug(String slug) {
        return productRepository.findBySlug(slug)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
    }

    @Transactional(readOnly = true)
    public Product productById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
    }

    @Transactional(readOnly = true)
    public List<ProductCharacteristic> characteristics(Product product) {
        return characteristicRepository.findByProductOrderBySortOrderAsc(product);
    }

    @Transactional(readOnly = true)
    public List<FilterOption> filterOptions(Product product) {
        return productFilterOptionRepository.findByProduct(product).stream()
            .map(ProductFilterOption::getFilterOption)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Review> approvedReviews(Product product) {
        return reviewRepository.findByProductAndApprovedTrueAndParentIsNullOrderByCreatedAtDesc(product);
    }

    private Sort resolveSort(String sortBy) {
        if ("price_asc".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.ASC, "price");
        }
        if ("price_desc".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "price");
        }
        if ("name_asc".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.ASC, "name");
        }
        if ("name_desc".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "name");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }
}

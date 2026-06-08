package com.example.gqw.shop.persistence;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.CategoryFilter;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import com.example.gqw.shop.entity.ProductFilterOption;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.repository.CategoryFilterRepository;
import com.example.gqw.shop.repository.CategoryRepository;
import com.example.gqw.shop.repository.ProductCharacteristicRepository;
import com.example.gqw.shop.repository.ProductFilterOptionRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ReviewRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class CatalogPersistence {

    private final CategoryRepository categoryRepository;
    private final CategoryFilterRepository categoryFilterRepository;
    private final ProductRepository productRepository;
    private final ProductCharacteristicRepository characteristicRepository;
    private final ProductFilterOptionRepository productFilterOptionRepository;
    private final ReviewRepository reviewRepository;

    public CatalogPersistence(
        CategoryRepository categoryRepository,
        CategoryFilterRepository categoryFilterRepository,
        ProductRepository productRepository,
        ProductCharacteristicRepository characteristicRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        ReviewRepository reviewRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryFilterRepository = categoryFilterRepository;
        this.productRepository = productRepository;
        this.characteristicRepository = characteristicRepository;
        this.productFilterOptionRepository = productFilterOptionRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<Category> categories(Sort sort) {
        return categoryRepository.findAll(sort);
    }

    public List<Category> topCategories() {
        return categoryRepository.findByParentIsNullAndIsPublishedTrueOrderByIdAsc();
    }

    public List<Category> subcategories(Category parent) {
        return categoryRepository.findByParentAndIsPublishedTrueOrderByIdAsc(parent);
    }

    public Optional<Category> categoryBySlug(String slug) {
        return categoryRepository.findBySlugAndIsPublishedTrue(slug);
    }

    public List<CategoryFilter> categoryFilters(Category category) {
        return categoryFilterRepository.findByCategory(category);
    }

    public List<Product> latestProducts() {
        return productRepository.findTop20ByIsPublishedTrueOrderByCreatedAtDesc();
    }

    public List<Product> products(Specification<Product> specification, Sort sort) {
        return productRepository.findAll(specification, sort);
    }

    public org.springframework.data.domain.Page<Product> products(
        Specification<Product> specification,
        Pageable pageable
    ) {
        return productRepository.findAll(specification, pageable);
    }

    public Optional<Product> productBySlug(String slug) {
        return productRepository.findBySlugAndIsPublishedTrue(slug);
    }

    public Optional<Product> productById(Long id) {
        return productRepository.findById(id);
    }

    public List<ProductCharacteristic> cardCharacteristics(List<Product> products) {
        return characteristicRepository.findForProductsOrdered(products);
    }

    public List<ProductCharacteristic> characteristics(Product product) {
        return characteristicRepository.findByProductOrderBySortOrderAsc(product);
    }

    public List<ProductFilterOption> productFilterOptions(Product product) {
        return productFilterOptionRepository.findByProduct(product);
    }

    public List<ProductFilterOption> productFilterOptions(List<Product> products) {
        return productFilterOptionRepository.findByProductIn(products);
    }

    public List<Review> approvedReviews(Product product) {
        return reviewRepository.findByProductAndApprovedTrueAndParentIsNullOrderByCreatedAtDesc(product);
    }

    public List<Product> relatedProducts(List<Category> categories, Pageable pageable) {
        return productRepository.findDistinctByCategoriesInAndIsPublishedTrue(categories, pageable).stream().toList();
    }
}

package com.example.gqw.shop.service;

import com.example.gqw.analytics.aop.TrackAnalyticsMetric;
import com.example.gqw.analytics.aop.TrackAnalyticsStageMetric;
import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.FilterOption;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    public record CategoryTreeNode(Category category, List<CategoryTreeNode> children) {
    }

    public record FilterFacetOption(Long id, String code, String value, long count, boolean selected, boolean disabled) {
    }

    public record FilterFacet(String code, String name, List<FilterFacetOption> options) {
    }

    public record CategoryCatalogData(Page<Product> pageData, List<FilterFacet> facets) {
    }

    public record PriceBounds(BigDecimal min, BigDecimal max) {
    }

    private final CategoryRepository categoryRepository;
    private final CategoryFilterRepository categoryFilterRepository;
    private final ProductRepository productRepository;
    private final ProductCharacteristicRepository characteristicRepository;
    private final ProductFilterOptionRepository productFilterOptionRepository;
    private final ReviewRepository reviewRepository;

    public CatalogService(
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

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<Category> topCategories() {
        return categoryRepository.findByParentIsNullAndIsPublishedTrueOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Category> featuredTopCategories(int limit) {
        List<Category> categories = topCategories();
        if (limit <= 0 || categories.size() <= limit) {
            return categories;
        }
        return categories.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public List<Category> subcategories(Category parent) {
        return categoryRepository.findByParentAndIsPublishedTrueOrderByIdAsc(parent);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Category>> subcategoriesByParentId(List<Category> parents) {
        Map<Long, List<Category>> map = new LinkedHashMap<>();
        for (Category parent : parents) {
            map.put(parent.getId(), subcategories(parent));
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeNode> categoryTree() {
        List<CategoryTreeNode> nodes = new ArrayList<>();
        for (Category root : topCategories()) {
            nodes.add(toTreeNode(root));
        }
        return nodes;
    }


  
    @Transactional(readOnly = true)
    public List<Product> latestProducts() {
        return productRepository.findTop20ByIsPublishedTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ProductCharacteristic>> cardCharacteristics(List<Product> products, int limit) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }
        int resolvedLimit = Math.max(1, Math.min(limit, 8));
        Map<Long, List<ProductCharacteristic>> result = new LinkedHashMap<>();
        for (Product product : products) {
            result.put(product.getId(), new ArrayList<>());
        }
        List<ProductCharacteristic> rows = characteristicRepository.findForProductsOrdered(products);
        for (ProductCharacteristic row : rows) {
            List<ProductCharacteristic> list = result.get(row.getProduct().getId());
            if (list != null && list.size() < resolvedLimit) {
                list.add(row);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Product> catalog(
        int page,
        int size,
        String sortBy,
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Long categoryId
    ) {
        return productRepository.findAll(
            buildFilterSpecification(query, minPrice, maxPrice, categoryId, false),
            PageRequest.of(Math.max(0, page), normalizePageSize(size), resolveSort(sortBy))
        );
    }

    @Transactional(readOnly = true)
    public Category categoryBySlug(String slug) {
        return categoryRepository.findBySlugAndIsPublishedTrue(slug)
            .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
    }

    @Transactional(readOnly = true)
    public Page<Product> productsByCategory(
        String categorySlug,
        int page,
        int size,
        String sortBy,
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice
    ) {
        Category category = categoryBySlug(categorySlug);
        return catalog(page, size, sortBy, query, minPrice, maxPrice, category.getId());
    }

    @Transactional(readOnly = true)
    public CategoryCatalogData categoryCatalogData(
        String categorySlug,
        int page,
        int size,
        String sortBy,
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<Long> selectedOptionIds,
        boolean inStockOnly
    ) {
        Category category = categoryBySlug(categorySlug);
        List<Product> baseProducts = productRepository.findAll(
            buildFilterSpecification(query, minPrice, maxPrice, category.getId(), inStockOnly),
            resolveSort(sortBy)
        );
        Set<Long> selectedIds = selectedOptionIds == null
            ? Set.of()
            : selectedOptionIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        Set<Long> allowedFilterIds = categoryFilterRepository.findByCategory(category).stream()
            .map(cf -> cf.getFilter() != null ? cf.getFilter().getId() : null)
            .filter(id -> id != null && id > 0)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        List<ProductFilterOption> relations = baseProducts.isEmpty()
            ? List.of()
            : productFilterOptionRepository.findByProductIn(baseProducts).stream()
                .filter(relation -> {
                    if (allowedFilterIds.isEmpty()) {
                        return true;
                    }
                    if (relation.getFilterOption() == null || relation.getFilterOption().getFilter() == null) {
                        return false;
                    }
                    Long filterId = relation.getFilterOption().getFilter().getId();
                    return filterId != null && allowedFilterIds.contains(filterId);
                })
                .toList();
        Set<Long> availableOptionIds = relations.stream()
            .map(ProductFilterOption::getFilterOption)
            .filter(option -> option != null && option.getId() != null)
            .map(FilterOption::getId)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!availableOptionIds.isEmpty()) {
            selectedIds = selectedIds.stream()
                .filter(availableOptionIds::contains)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        } else {
            selectedIds = Set.of();
        }
        Map<Long, Set<Long>> productOptionIdsByProduct = buildProductOptionIndex(relations);
        List<FilterFacet> facets = buildFacets(relations, selectedIds, productOptionIdsByProduct);
        List<Product> filteredProducts = applyOptionFilter(baseProducts, productOptionIdsByProduct, selectedIds);
        Page<Product> pageData = toPage(filteredProducts, page, size);
        return new CategoryCatalogData(pageData, facets);
    }

    @Transactional(readOnly = true)
    public PriceBounds categoryPriceBounds(String categorySlug, String query, List<Long> selectedOptionIds, boolean inStockOnly) {
        Category category = categoryBySlug(categorySlug);
        List<Product> baseProducts = productRepository.findAll(
            buildFilterSpecification(query, null, null, category.getId(), inStockOnly),
            Sort.by(Sort.Direction.ASC, "price")
        );

        if (baseProducts.isEmpty()) {
            return new PriceBounds(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Set<Long> selectedIds = selectedOptionIds == null
            ? Set.of()
            : selectedOptionIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        if (!selectedIds.isEmpty()) {
            Set<Long> allowedFilterIds = categoryFilterRepository.findByCategory(category).stream()
                .map(cf -> cf.getFilter() != null ? cf.getFilter().getId() : null)
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

            List<ProductFilterOption> relations = productFilterOptionRepository.findByProductIn(baseProducts).stream()
                .filter(relation -> {
                    if (allowedFilterIds.isEmpty()) {
                        return true;
                    }
                    if (relation.getFilterOption() == null || relation.getFilterOption().getFilter() == null) {
                        return false;
                    }
                    Long filterId = relation.getFilterOption().getFilter().getId();
                    return filterId != null && allowedFilterIds.contains(filterId);
                })
                .toList();

            Set<Long> availableOptionIds = relations.stream()
                .map(ProductFilterOption::getFilterOption)
                .filter(option -> option != null && option.getId() != null)
                .map(FilterOption::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

            if (!availableOptionIds.isEmpty()) {
                selectedIds = selectedIds.stream()
                    .filter(availableOptionIds::contains)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            } else {
                selectedIds = Set.of();
            }

            if (!selectedIds.isEmpty()) {
                Map<Long, Set<Long>> productOptionIdsByProduct = buildProductOptionIndex(relations);
                baseProducts = applyOptionFilter(baseProducts, productOptionIdsByProduct, selectedIds);
            }
        }

        BigDecimal min = null;
        BigDecimal max = null;
        for (Product product : baseProducts) {
            if (product.getPrice() == null) {
                continue;
            }
            if (min == null || product.getPrice().compareTo(min) < 0) {
                min = product.getPrice();
            }
            if (max == null || product.getPrice().compareTo(max) > 0) {
                max = product.getPrice();
            }
        }

        if (min == null || max == null) {
            return new PriceBounds(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new PriceBounds(min, max);
    }

    @Transactional(readOnly = true)
    public Product productBySlug(String slug) {
        return productRepository.findBySlugAndIsPublishedTrue(slug)
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

    @Transactional(readOnly = true)
    public List<Review> latestApprovedReviews(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 100));
        return reviewRepository.findByApprovedTrueAndParentIsNullOrderByCreatedAtDesc(PageRequest.of(0, resolvedLimit));
    }

    @Transactional(readOnly = true)
    public List<Product> relatedProducts(Product product, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 12));
        Set<Product> result = new LinkedHashSet<>();
        if (product == null) {
            return latestProducts().stream().limit(resolvedLimit).toList();
        }

        List<Category> currentCategories = product.getCategories() == null
            ? List.of()
            : product.getCategories().stream().toList();
        if (!currentCategories.isEmpty()) {
            productRepository.findDistinctByCategoriesInAndIsPublishedTrue(currentCategories, PageRequest.of(0, resolvedLimit * 3, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .filter(candidate -> !candidate.getId().equals(product.getId()))
                .forEach(result::add);
        }

        if (result.size() < resolvedLimit) {
            List<Category> parentCategories = new ArrayList<>();
            for (Category category : currentCategories) {
                if (category.getParent() != null) {
                    parentCategories.add(category.getParent());
                }
            }
            if (!parentCategories.isEmpty()) {
                productRepository.findDistinctByCategoriesInAndIsPublishedTrue(parentCategories, PageRequest.of(0, resolvedLimit * 3, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .stream()
                    .filter(candidate -> !candidate.getId().equals(product.getId()))
                    .forEach(result::add);
            }
        }

        return result.stream().limit(resolvedLimit).toList();
    }

    private Specification<Product> buildFilterSpecification(
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Long categoryId,
        boolean inStockOnly
    ) {
        return (root, selectQuery, cb) -> {
            selectQuery.distinct(true);
            var predicates = cb.conjunction();
            predicates = cb.and(predicates, cb.isTrue(root.get("isPublished")));
            if (inStockOnly) {
                predicates = cb.and(predicates, cb.isTrue(root.get("inStock")));
            }

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates = cb.and(
                    predicates,
                    cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("shortDescription")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                    )
                );
            }

            if (minPrice != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (categoryId != null) {
                predicates = cb.and(predicates, cb.equal(root.join("categories").get("id"), categoryId));
            }

            return predicates;
        };
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return 12;
        }
        return Math.min(size, 48);
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

    private CategoryTreeNode toTreeNode(Category category) {
        List<CategoryTreeNode> children = new ArrayList<>();
        for (Category subcategory : subcategories(category)) {
            children.add(toTreeNode(subcategory));
        }
        return new CategoryTreeNode(category, children);
    }

    private Page<Product> toPage(List<Product> products, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = normalizePageSize(size);
        int total = products.size();
        int from = Math.min(pageIndex * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Product> content = from < to ? products.subList(from, to) : List.of();
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize), total);
    }

    private List<Product> applyOptionFilter(
        List<Product> baseProducts,
        Map<Long, Set<Long>> productOptionIdsByProduct,
        Set<Long> selectedOptionIds
    ) {
        if (selectedOptionIds.isEmpty()) {
            return baseProducts;
        }
        List<Product> filtered = new ArrayList<>();
        for (Product product : baseProducts) {
            Set<Long> optionIds = productOptionIdsByProduct.getOrDefault(product.getId(), Set.of());
            if (optionIds.containsAll(selectedOptionIds)) {
                filtered.add(product);
            }
        }
        return filtered;
    }

    private List<FilterFacet> buildFacets(
        List<ProductFilterOption> relations,
        Set<Long> selectedOptionIds,
        Map<Long, Set<Long>> productOptionIdsByProduct
    ) {
        Map<String, FilterBucket> buckets = new LinkedHashMap<>();
        for (ProductFilterOption relation : relations) {
            if (relation.getFilterOption() == null || relation.getFilterOption().getFilter() == null || relation.getProduct() == null) {
                continue;
            }
            var option = relation.getFilterOption();
            var filter = option.getFilter();
            String filterCode = filter.getCode();
            if (filterCode == null || filterCode.isBlank()) {
                continue;
            }
            FilterBucket bucket = buckets.computeIfAbsent(
                filterCode,
                ignored -> new FilterBucket(filterCode, filter.getName() == null ? filterCode : filter.getName())
            );
            bucket.addOption(option.getId(), option.getCode(), option.getValue(), relation.getProduct().getId());
        }
        long selectedCombinationCount = countMatchingProducts(productOptionIdsByProduct, selectedOptionIds);
        List<FilterFacet> facets = new ArrayList<>();
        for (FilterBucket bucket : buckets.values()) {
            List<FilterFacetOption> options = new ArrayList<>();
            for (OptionBucket optionBucket : bucket.options.values()) {
                boolean selected = selectedOptionIds.contains(optionBucket.id);
                long optionCount;
                boolean disabled;
                if (selected) {
                    optionCount = selectedCombinationCount;
                    disabled = false;
                } else {
                    Set<Long> candidateSelection = new HashSet<>(selectedOptionIds);
                    candidateSelection.add(optionBucket.id);
                    optionCount = countMatchingProducts(productOptionIdsByProduct, candidateSelection);
                    disabled = optionCount == 0;
                }
                options.add(new FilterFacetOption(
                    optionBucket.id,
                    optionBucket.code,
                    optionBucket.value,
                    optionCount,
                    selected,
                    disabled
                ));
            }
            options.sort(Comparator.comparing(FilterFacetOption::value, String.CASE_INSENSITIVE_ORDER));
            facets.add(new FilterFacet(bucket.code, bucket.name, options));
        }
        facets.sort(Comparator.comparing(FilterFacet::name, String.CASE_INSENSITIVE_ORDER));
        return facets;
    }

    private Map<Long, Set<Long>> buildProductOptionIndex(List<ProductFilterOption> relations) {
        Map<Long, Set<Long>> productOptionIdsByProduct = new HashMap<>();
        for (ProductFilterOption relation : relations) {
            if (relation.getProduct() == null || relation.getFilterOption() == null) {
                continue;
            }
            Long productId = relation.getProduct().getId();
            Long optionId = relation.getFilterOption().getId();
            if (productId == null || optionId == null) {
                continue;
            }
            productOptionIdsByProduct.computeIfAbsent(productId, ignored -> new HashSet<>()).add(optionId);
        }
        return productOptionIdsByProduct;
    }

    private long countMatchingProducts(Map<Long, Set<Long>> productOptionIdsByProduct, Set<Long> selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
            return productOptionIdsByProduct.size();
        }
        return productOptionIdsByProduct.values().stream()
            .filter(optionIds -> optionIds.containsAll(selectedOptionIds))
            .count();
    }

    private static class FilterBucket {

        private final String code;
        private final String name;
        private final Map<Long, OptionBucket> options = new LinkedHashMap<>();

        private FilterBucket(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private void addOption(Long id, String code, String value, Long productId) {
            if (id == null || productId == null) {
                return;
            }
            OptionBucket bucket = options.computeIfAbsent(
                id,
                ignored -> new OptionBucket(id, code == null ? String.valueOf(id) : code, value == null ? "" : value)
            );
            bucket.productIds.add(productId);
        }
    }

    private static class OptionBucket {

        private final Long id;
        private final String code;
        private final String value;
        private final Set<Long> productIds = new HashSet<>();

        private OptionBucket(Long id, String code, String value) {
            this.id = id;
            this.code = code;
            this.value = value;
        }
    }
}

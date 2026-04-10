package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.CategoryFilter;
import com.example.gqw.shop.entity.FilterOption;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ProductCharacteristic;
import com.example.gqw.shop.entity.ProductFilter;
import com.example.gqw.shop.entity.ProductFilterOption;
import com.example.gqw.shop.entity.ProductImage;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.OrderItemRepository;
import com.example.gqw.shop.repository.CategoryRepository;
import com.example.gqw.shop.repository.CategoryFilterRepository;
import com.example.gqw.shop.repository.FilterOptionRepository;
import com.example.gqw.shop.repository.ProductCharacteristicRepository;
import com.example.gqw.shop.repository.ProductFilterOptionRepository;
import com.example.gqw.shop.repository.ProductFilterRepository;
import com.example.gqw.shop.repository.ProductImageRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ReviewImageRepository;
import com.example.gqw.shop.repository.ReviewRepository;
import com.example.gqw.shop.repository.ShopOrderRepository;
import com.example.gqw.shop.repository.ShopUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminService {

    private static final int PRODUCT_NAME_MAX_LENGTH = 256;
    private static final int PRODUCT_SLUG_MAX_LENGTH = 256;

    public record CategoryTreeRow(Category category, int depth, boolean leaf, Long parentId) {
    }

    public record ProductPurchaseInfo(
        Long orderId,
        String buyerName,
        String buyerEmail,
        Instant purchasedAt,
        Integer quantity
    ) {
    }

    public record ProductSalesSummary(
        int totalQuantity,
        int ordersCount,
        int buyersCount,
        BigDecimal totalRevenue,
        List<ProductPurchaseInfo> recentPurchases
    ) {
    }

    public record ProductCopyDraft(
        String name,
        String slug,
        String article,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean isNew,
        boolean isHit,
        boolean isDiscount,
        boolean isPublished,
        boolean inStock,
        List<Long> categoryIds,
        List<String> imageUrls
    ) {
    }

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryFilterRepository categoryFilterRepository;
    private final ProductCharacteristicRepository characteristicRepository;
    private final ProductFilterRepository productFilterRepository;
    private final FilterOptionRepository filterOptionRepository;
    private final ProductFilterOptionRepository productFilterOptionRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewRepository reviewRepository;
    private final ProductImageStorageService productImageStorageService;
    private final CategoryImageStorageService categoryImageStorageService;
    private final ShopOrderRepository orderRepository;
    private final ShopUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AdminService(
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        CategoryFilterRepository categoryFilterRepository,
        ProductCharacteristicRepository characteristicRepository,
        ProductFilterRepository productFilterRepository,
        FilterOptionRepository filterOptionRepository,
        ProductFilterOptionRepository productFilterOptionRepository,
        ProductImageRepository productImageRepository,
        OrderItemRepository orderItemRepository,
        ReviewImageRepository reviewImageRepository,
        ReviewRepository reviewRepository,
        ProductImageStorageService productImageStorageService,
        CategoryImageStorageService categoryImageStorageService,
        ShopOrderRepository orderRepository,
        ShopUserRepository userRepository,
        PasswordEncoder passwordEncoder,
        CurrentUserService currentUserService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.categoryFilterRepository = categoryFilterRepository;
        this.characteristicRepository = characteristicRepository;
        this.productFilterRepository = productFilterRepository;
        this.filterOptionRepository = filterOptionRepository;
        this.productFilterOptionRepository = productFilterOptionRepository;
        this.productImageRepository = productImageRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.reviewRepository = reviewRepository;
        this.productImageStorageService = productImageStorageService;
        this.categoryImageStorageService = categoryImageStorageService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<Product> products() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findAll().stream()
            .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeRow> categoryTreeRows() {
        return categoryTreeRows("date_asc");
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeRow> categoryTreeRows(String sortMode) {
        Comparator<Category> comparator = resolveCategoryComparator(sortMode);
        List<Category> allCategories = categoryRepository.findAll();
        Map<Long, List<Category>> childrenByParentId = new LinkedHashMap<>();
        for (Category category : allCategories) {
            Long parentId = category.getParent() != null ? category.getParent().getId() : null;
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(category);
        }
        for (List<Category> children : childrenByParentId.values()) {
            children.sort(comparator);
        }

        List<CategoryTreeRow> rows = new ArrayList<>();
        List<Category> roots = childrenByParentId.getOrDefault(null, List.of());
        for (Category root : roots) {
            appendCategoryRows(root, 0, null, rows, childrenByParentId);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Category categoryById(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
    }

    @Transactional(readOnly = true)
    public List<Product> categoryProducts(Long categoryId) {
        Category category = categoryById(categoryId);
        return productRepository.findDistinctByCategoriesIn(List.of(category), Pageable.unpaged()).getContent().stream()
            .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductSalesSummary> productSalesSummary(List<Product> products, int maxRecentPurchases) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        int limit = Math.max(1, Math.min(maxRecentPurchases, 20));
        Map<Long, ProductSalesSummaryAccumulator> accByProductId = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            accByProductId.put(product.getId(), new ProductSalesSummaryAccumulator());
        }
        if (accByProductId.isEmpty()) {
            return Map.of();
        }

        List<OrderItem> orderItems = orderItemRepository.findByProductInOrderByOrder_CreatedAtDesc(products);
        for (OrderItem item : orderItems) {
            if (item.getProduct() == null || item.getProduct().getId() == null || item.getOrder() == null) {
                continue;
            }
            if (item.getOrder().getStatus() == null
                || item.getOrder().getStatus() == OrderStatus.NEW
                || item.getOrder().getStatus() == OrderStatus.REJECTED) {
                continue;
            }
            ProductSalesSummaryAccumulator acc = accByProductId.get(item.getProduct().getId());
            if (acc == null) {
                continue;
            }
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty < 0) {
                qty = 0;
            }
            acc.totalQuantity += qty;
            BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            acc.totalRevenue = acc.totalRevenue.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            if (item.getOrder().getId() != null) {
                acc.orderIds.add(item.getOrder().getId());
            }
            String buyerKey = resolveBuyerKey(item.getOrder());
            if (buyerKey != null) {
                acc.buyerKeys.add(buyerKey);
            }
            if (acc.recentPurchases.size() < limit) {
                acc.recentPurchases.add(new ProductPurchaseInfo(
                    item.getOrder().getId(),
                    item.getOrder().getCustomerName(),
                    item.getOrder().getCustomerEmail(),
                    item.getOrder().getCreatedAt(),
                    qty
                ));
            }
        }

        Map<Long, ProductSalesSummary> result = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            ProductSalesSummaryAccumulator acc = accByProductId.get(product.getId());
            if (acc == null) {
                result.put(product.getId(), new ProductSalesSummary(0, 0, 0, BigDecimal.ZERO, List.of()));
                continue;
            }
            result.put(product.getId(), new ProductSalesSummary(
                acc.totalQuantity,
                acc.orderIds.size(),
                acc.buyerKeys.size(),
                acc.totalRevenue,
                acc.recentPurchases
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> orders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ShopUser> users() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ShopUser userById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> ordersByUser(ShopUser user) {
        if (user == null) {
            return List.of();
        }
        return orderRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public ShopUser updateUserEnabled(Long userId, boolean enabled) {
        ShopUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new IllegalArgumentException("Нельзя изменить статус администратора");
        }
        user.setIsEnabled(enabled);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<ProductFilter> filters() {
        return productFilterRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> filterCategoryIdsMap() {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        for (ProductFilter filter : filters()) {
            List<Long> categoryIds = categoryFilterRepository.findByFilter(filter).stream()
                .map(cf -> cf.getCategory().getId())
                .distinct()
                .toList();
            map.put(filter.getId(), categoryIds);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<FilterOption>> filterOptionsMap() {
        Map<Long, List<FilterOption>> map = new LinkedHashMap<>();
        for (ProductFilter filter : filters()) {
            map.put(filter.getId(), filterOptionRepository.findByFilter(filter));
        }
        return map;
    }

    @Transactional
    public ProductFilter saveFilter(
        Long filterId,
        String code,
        String name,
        String valueType,
        String viewType,
        boolean multiValue,
        List<Long> categoryIds,
        String predefinedValues
    ) {
        String normalizedCode = resolveSlug(code, code);
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Название фильтра обязательно");
        }
        ProductFilter filter = filterId == null
            ? productFilterRepository.findByCode(normalizedCode).orElseGet(ProductFilter::new)
            : productFilterRepository.findById(filterId).orElseGet(ProductFilter::new);
        ProductFilter codeOwner = productFilterRepository.findByCode(normalizedCode).orElse(null);
        if (codeOwner != null && filterId != null && !codeOwner.getId().equals(filterId)) {
            throw new IllegalArgumentException("Код фильтра уже используется");
        }
        filter.setCode(normalizedCode);
        filter.setName(normalizedName);
        filter.setValueType(valueType == null || valueType.isBlank() ? "LIST" : valueType);
        filter.setViewType(viewType == null || viewType.isBlank() ? "CHECKBOX" : viewType);
        filter.setMultiValue(multiValue);
        filter = productFilterRepository.save(filter);

        categoryFilterRepository.deleteByFilter(filter);
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<Category> categories = categoryRepository.findAllById(categoryIds);
            for (Category category : categories) {
                CategoryFilter relation = new CategoryFilter();
                relation.setCategory(category);
                relation.setFilter(filter);
                categoryFilterRepository.save(relation);
            }
        }

        if (predefinedValues != null && !predefinedValues.isBlank()) {
            List<String> values = List.of(predefinedValues.split("[,\\n]")).stream()
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
            for (String value : values) {
                String optionCode = resolveSlug(value, value);
                if (filterOptionRepository.findByFilterAndCode(filter, optionCode).isPresent()) {
                    continue;
                }
                FilterOption option = new FilterOption();
                option.setFilter(filter);
                option.setCode(optionCode);
                option.setValue(value);
                filterOptionRepository.save(option);
            }
        }
        return filter;
    }

    @Transactional
    public void deleteFilter(Long filterId) {
        ProductFilter filter = productFilterRepository.findById(filterId)
            .orElseThrow(() -> new IllegalArgumentException("Фильтр не найден"));
        categoryFilterRepository.deleteByFilter(filter);
        for (FilterOption option : filterOptionRepository.findByFilter(filter)) {
            filterOptionRepository.delete(option);
        }
        productFilterRepository.delete(filter);
    }

    @Transactional
    public Category saveCategory(String name, String slug, String description, String imageUrl, boolean isPublished) {
        String resolvedSlug = resolveSlug(name, slug);
        Category category = categoryRepository.findBySlug(resolvedSlug).orElseGet(Category::new);
        category.setName(requireName(name));
        category.setSlug(requireUniqueCategorySlug(resolvedSlug, category.getId()));
        category.setDescription(normalizeText(description));
        category.setImageUrl(normalizeText(imageUrl));
        category.setIsPublished(isPublished);
        return categoryRepository.save(category);
    }

    @Transactional
    public Category createCategory(
        String name,
        String slug,
        String description,
        Long parentId,
        boolean isPublished,
        String libraryImageUrl,
        MultipartFile imageFile
    ) {
        Category category = new Category();
        category.setName(requireName(name));
        category.setSlug(requireUniqueCategorySlug(resolveSlug(name, slug), null));
        category.setDescription(normalizeText(description));
        category.setParent(resolveParent(parentId, null));
        category.setIsPublished(isPublished);
        category = categoryRepository.save(category);

        String normalizedLibraryImageUrl = normalizeText(libraryImageUrl);
        if (normalizedLibraryImageUrl != null) {
            category.setImageUrl(normalizedLibraryImageUrl);
        }
        String imageUrl = categoryImageStorageService.store(category.getId(), imageFile);
        if (imageUrl != null) {
            category.setImageUrl(imageUrl);
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(
        Long categoryId,
        String name,
        String slug,
        String description,
        Long parentId,
        boolean isPublished,
        String libraryImageUrl,
        MultipartFile imageFile
    ) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        category.setName(requireName(name));
        category.setSlug(requireUniqueCategorySlug(resolveSlug(name, slug), category.getId()));
        category.setDescription(normalizeText(description));
        category.setParent(resolveParent(parentId, category.getId()));
        category.setIsPublished(isPublished);
        category = categoryRepository.save(category);

        String normalizedLibraryImageUrl = normalizeText(libraryImageUrl);
        if (normalizedLibraryImageUrl != null) {
            category.setImageUrl(normalizedLibraryImageUrl);
        }
        String imageUrl = categoryImageStorageService.store(category.getId(), imageFile);
        if (imageUrl != null) {
            category.setImageUrl(imageUrl);
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        List<Category> children = categoryRepository.findByParentOrderByIdAsc(category);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("Сначала удалите или перепривяжите подкатегории");
        }
        Set<Product> products = new HashSet<>(productRepository.findDistinctByCategoriesIn(List.of(category), org.springframework.data.domain.Pageable.unpaged()).getContent());
        for (Product product : products) {
            product.getCategories().removeIf(c -> categoryId.equals(c.getId()));
            if (product.getCategories().isEmpty()) {
                deleteProductInternal(product);
            } else {
                productRepository.save(product);
            }
        }
        categoryRepository.delete(category);
    }

    @Transactional
    public Product saveProduct(
        String name,
        String slug,
        String article,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean isNew,
        boolean isHit,
        boolean isDiscount,
        boolean isPublished,
        boolean inStock,
        List<Long> categoryIds,
        List<MultipartFile> images,
        List<String> libraryImageUrls
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название товара обязательно");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Некорректная цена товара");
        }
        String normalizedSlug = resolveSlug(name, slug);

        Product product = productRepository.findBySlug(normalizedSlug).orElseGet(Product::new);
        product.setName(name.trim());
        product.setSlug(normalizedSlug);
        product.setArticle(resolveUniqueArticle(article, product.getId()));
        product.setShortDescription(shortDescription == null ? null : shortDescription.trim());
        product.setDescription(description == null ? null : description.trim());
        product.setPrice(price);
        product.setOldPrice(oldPrice != null && oldPrice.signum() > 0 ? oldPrice : null);
        product.setIsNew(isNew);
        product.setIsHit(isHit);
        product.setIsDiscount(isDiscount);
        product.setIsPublished(isPublished);
        product.setInStock(inStock);

        Set<Category> categories = new HashSet<>();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            categories.addAll(categoryRepository.findAllById(categoryIds));
        }
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("Товар должен быть привязан минимум к одной категории");
        }
        product.setCategories(categories);
        Product saved = productRepository.save(product);
        if (saved.getArticle() == null || saved.getArticle().isBlank()) {
            saved.setArticle(generateDefaultArticle(saved.getId()));
            saved = productRepository.save(saved);
        }

        List<String> uploadedImageUrls = productImageStorageService.store(saved.getId(), images);
        List<String> mergedImageUrls = mergeImageUrls(libraryImageUrls, uploadedImageUrls);
        if (!mergedImageUrls.isEmpty()) {
            replaceProductImages(saved, mergedImageUrls);
            saved.setImageUrl(mergedImageUrls.getFirst());
            return productRepository.save(saved);
        }
        if ((saved.getImages() == null || saved.getImages().isEmpty()) && saved.getImageUrl() != null && !saved.getImageUrl().isBlank()) {
            replaceProductImages(saved, List.of(saved.getImageUrl()));
        }
        return saved;
    }

    @Transactional
    public Product createProduct(
        String name,
        String slug,
        String article,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean isNew,
        boolean isHit,
        boolean isDiscount,
        boolean isPublished,
        boolean inStock,
        List<Long> categoryIds,
        List<MultipartFile> images,
        List<String> libraryImageUrls,
        Long copySourceProductId
    ) {
        String normalizedSlug = resolveSlug(name, slug);
        if (productRepository.findBySlug(normalizedSlug).isPresent()) {
            throw new IllegalArgumentException("Slug товара уже занят");
        }
        Product created = saveProduct(
            name,
            normalizedSlug,
            article,
            shortDescription,
            description,
            price,
            oldPrice,
            isNew,
            isHit,
            isDiscount,
            isPublished,
            inStock,
            categoryIds,
            images,
            libraryImageUrls
        );
        if (copySourceProductId != null && copySourceProductId > 0) {
            Product source = productRepository.findById(copySourceProductId)
                .orElseThrow(() -> new IllegalArgumentException("Источник копии товара не найден"));
            applyCopyPayloadFromSource(created, source);
        }
        return created;
    }

    @Transactional
    public Product updateProduct(
        Long productId,
        String name,
        String slug,
        String article,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean isNew,
        boolean isHit,
        boolean isDiscount,
        boolean isPublished,
        boolean inStock,
        List<Long> categoryIds,
        List<MultipartFile> images,
        List<String> libraryImageUrls
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Название товара обязательно");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Некорректная цена товара");
        }
        String normalizedSlug = resolveSlug(normalizedName, slug);
        Product slugOwner = productRepository.findBySlug(normalizedSlug).orElse(null);
        if (slugOwner != null && !slugOwner.getId().equals(productId)) {
            throw new IllegalArgumentException("Slug товара уже занят");
        }

        Set<Category> categories = new HashSet<>();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            categories.addAll(categoryRepository.findAllById(categoryIds));
        }
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("Товар должен быть привязан минимум к одной категории");
        }

        product.setName(normalizedName);
        product.setSlug(normalizedSlug);
        product.setArticle(resolveUniqueArticle(article, productId));
        product.setShortDescription(shortDescription == null ? null : shortDescription.trim());
        product.setDescription(description == null ? null : description.trim());
        product.setPrice(price);
        product.setOldPrice(oldPrice != null && oldPrice.signum() > 0 ? oldPrice : null);
        product.setIsNew(isNew);
        product.setIsHit(isHit);
        product.setIsDiscount(isDiscount);
        product.setIsPublished(isPublished);
        product.setInStock(inStock);
        product.setCategories(categories);
        Product saved = productRepository.save(product);
        if (saved.getArticle() == null || saved.getArticle().isBlank()) {
            saved.setArticle(generateDefaultArticle(saved.getId()));
            saved = productRepository.save(saved);
        }

        List<String> uploadedImageUrls = productImageStorageService.store(saved.getId(), images);
        List<String> mergedImageUrls = mergeImageUrls(libraryImageUrls, uploadedImageUrls);
        if (!mergedImageUrls.isEmpty()) {
            replaceProductImages(saved, mergedImageUrls);
            saved.setImageUrl(mergedImageUrls.getFirst());
            saved = productRepository.save(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Product productById(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
    }

    @Transactional(readOnly = true)
    public ProductCopyDraft buildProductCopyDraft(Long sourceProductId) {
        Product source = productById(sourceProductId);
        List<Long> categoryIds = source.getCategories() == null
            ? List.of()
            : source.getCategories().stream()
                .map(Category::getId)
                .filter(id -> id != null && id > 0)
                .sorted()
                .toList();
        List<String> imageUrls = productImageRepository.findByProductOrderBySortOrderAscIdAsc(source).stream()
            .map(ProductImage::getImageUrl)
            .filter(url -> url != null && !url.isBlank())
            .toList();
        if (imageUrls.isEmpty() && source.getImageUrl() != null && !source.getImageUrl().isBlank()) {
            imageUrls = List.of(source.getImageUrl());
        }
        return new ProductCopyDraft(
            generateDuplicateName(source.getName()),
            generateDuplicateSlug(source.getSlug()),
            null,
            source.getShortDescription(),
            source.getDescription(),
            source.getPrice(),
            source.getOldPrice(),
            Boolean.TRUE.equals(source.getIsNew()),
            Boolean.TRUE.equals(source.getIsHit()),
            Boolean.TRUE.equals(source.getIsDiscount()),
            Boolean.TRUE.equals(source.getIsPublished()),
            Boolean.TRUE.equals(source.getInStock()),
            categoryIds,
            imageUrls
        );
    }

    @Transactional(readOnly = true)
    public List<Review> reviewsByProduct(Long productId) {
        Product product = productById(productId);
        return reviewRepository.findByProductAndParentIsNullOrderByCreatedAtDesc(product);
    }

    @Transactional
    public Product duplicateProduct(Long productId) {
        Product source = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));

        Product duplicated = new Product();
        duplicated.setName(generateDuplicateName(source.getName()));
        duplicated.setSlug(generateDuplicateSlug(source.getSlug()));
        duplicated.setArticle(null);
        duplicated.setShortDescription(source.getShortDescription());
        duplicated.setDescription(source.getDescription());
        duplicated.setImageUrl(source.getImageUrl());
        duplicated.setPrice(source.getPrice());
        duplicated.setOldPrice(source.getOldPrice());
        duplicated.setIsNew(Boolean.TRUE.equals(source.getIsNew()));
        duplicated.setIsHit(Boolean.TRUE.equals(source.getIsHit()));
        duplicated.setIsDiscount(Boolean.TRUE.equals(source.getIsDiscount()));
        duplicated.setRatingAvg(0.0);
        duplicated.setReviewCount(0);
        duplicated.setCategories(new HashSet<>(source.getCategories() == null ? Set.of() : source.getCategories()));
        if (duplicated.getCategories().isEmpty()) {
            throw new IllegalArgumentException("Нельзя скопировать товар без категории");
        }

        Product saved = productRepository.save(duplicated);
        saved.setArticle(generateDefaultArticle(saved.getId()));
        saved = productRepository.save(saved);

        List<ProductImage> sourceImages = productImageRepository.findByProductOrderBySortOrderAscIdAsc(source);
        if (!sourceImages.isEmpty()) {
            List<ProductImage> copiedImages = new ArrayList<>();
            for (ProductImage sourceImage : sourceImages) {
                if (sourceImage.getImageUrl() == null || sourceImage.getImageUrl().isBlank()) {
                    continue;
                }
                ProductImage copy = new ProductImage();
                copy.setProduct(saved);
                copy.setImageUrl(sourceImage.getImageUrl());
                copy.setSortOrder(sourceImage.getSortOrder() == null ? 0 : sourceImage.getSortOrder());
                copiedImages.add(copy);
            }
            if (!copiedImages.isEmpty()) {
                productImageRepository.saveAll(copiedImages);
                saved.setImageUrl(copiedImages.getFirst().getImageUrl());
                saved = productRepository.save(saved);
            }
        }

        List<ProductCharacteristic> sourceCharacteristics = characteristicRepository.findByProductOrderBySortOrderAsc(source);
        if (!sourceCharacteristics.isEmpty()) {
            List<ProductCharacteristic> copiedCharacteristics = new ArrayList<>();
            for (ProductCharacteristic sourceCharacteristic : sourceCharacteristics) {
                ProductCharacteristic copy = new ProductCharacteristic();
                copy.setProduct(saved);
                copy.setName(sourceCharacteristic.getName());
                copy.setValue(sourceCharacteristic.getValue());
                copy.setSortOrder(sourceCharacteristic.getSortOrder() == null ? 0 : sourceCharacteristic.getSortOrder());
                copiedCharacteristics.add(copy);
            }
            characteristicRepository.saveAll(copiedCharacteristics);
        }

        List<ProductFilterOption> sourceFilterOptions = productFilterOptionRepository.findByProduct(source);
        if (!sourceFilterOptions.isEmpty()) {
            Set<Long> copiedFilterOptionIds = new HashSet<>();
            List<ProductFilterOption> copiedFilterOptions = new ArrayList<>();
            for (ProductFilterOption sourceFilterOption : sourceFilterOptions) {
                if (sourceFilterOption.getFilterOption() == null || sourceFilterOption.getFilterOption().getId() == null) {
                    continue;
                }
                Long optionId = sourceFilterOption.getFilterOption().getId();
                if (!copiedFilterOptionIds.add(optionId)) {
                    continue;
                }
                ProductFilterOption copy = new ProductFilterOption();
                copy.setProduct(saved);
                copy.setFilterOption(sourceFilterOption.getFilterOption());
                copiedFilterOptions.add(copy);
            }
            if (!copiedFilterOptions.isEmpty()) {
                productFilterOptionRepository.saveAll(copiedFilterOptions);
            }
        }

        return saved;
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        deleteProductInternal(product);
    }

    @Transactional
    public ProductCharacteristic addProductCharacteristic(Long productId, String name, String value, Integer sortOrder) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        String normalizedName = name == null ? "" : name.trim();
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Название характеристики обязательно");
        }
        if (normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Значение характеристики обязательно");
        }

        ProductCharacteristic characteristic = new ProductCharacteristic();
        characteristic.setProduct(product);
        characteristic.setName(normalizedName);
        characteristic.setValue(normalizedValue);
        characteristic.setSortOrder(sortOrder == null ? 0 : Math.max(0, sortOrder));
        return characteristicRepository.save(characteristic);
    }

    @Transactional
    public ProductCharacteristic updateProductCharacteristic(Long characteristicId, String name, String value) {
        ProductCharacteristic characteristic = characteristicRepository.findById(characteristicId)
            .orElseThrow(() -> new IllegalArgumentException("Характеристика не найдена"));
        String normalizedName = name == null ? "" : name.trim();
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Название характеристики обязательно");
        }
        if (normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Значение характеристики обязательно");
        }
        characteristic.setName(normalizedName);
        characteristic.setValue(normalizedValue);
        return characteristicRepository.save(characteristic);
    }

    @Transactional
    public Long deleteProductCharacteristic(Long characteristicId) {
        ProductCharacteristic characteristic = characteristicRepository.findById(characteristicId)
            .orElseThrow(() -> new IllegalArgumentException("Характеристика не найдена"));
        Long productId = characteristic.getProduct() != null ? characteristic.getProduct().getId() : null;
        characteristicRepository.delete(characteristic);
        return productId;
    }

    @Transactional
    public ProductFilterOption addProductFilterOption(
        Long productId,
        String filterCode,
        String filterName,
        String optionCode,
        String optionValue
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));

        String normalizedFilterCode = resolveFilterCode(filterCode, filterName);
        String normalizedFilterName = filterName == null ? "" : filterName.trim();
        String normalizedOptionCode = resolveOptionCode(optionCode, optionValue);
        String normalizedOptionValue = optionValue == null ? "" : optionValue.trim();

        if (normalizedFilterCode.isBlank()) {
            throw new IllegalArgumentException("Код фильтра обязателен");
        }
        if (normalizedFilterName.isBlank()) {
            throw new IllegalArgumentException("Название фильтра обязательно");
        }
        if (normalizedOptionValue.isBlank()) {
            throw new IllegalArgumentException("Значение опции фильтра обязательно");
        }

        ProductFilter filter = productFilterRepository.findByCode(normalizedFilterCode)
            .orElseGet(ProductFilter::new);
        filter.setCode(normalizedFilterCode);
        filter.setName(normalizedFilterName);
        filter = productFilterRepository.save(filter);

        FilterOption option = filterOptionRepository.findByFilterAndCode(filter, normalizedOptionCode)
            .orElseGet(FilterOption::new);
        option.setFilter(filter);
        option.setCode(normalizedOptionCode);
        option.setValue(normalizedOptionValue);
        option = filterOptionRepository.save(option);
        FilterOption savedOption = option;

        return productFilterOptionRepository.findByProductAndFilterOption(product, savedOption)
            .orElseGet(() -> {
                ProductFilterOption relation = new ProductFilterOption();
                relation.setProduct(product);
                relation.setFilterOption(savedOption);
                return productFilterOptionRepository.save(relation);
            });
    }

    @Transactional
    public ProductFilterOption updateProductFilterOption(
        Long relationId,
        String filterCode,
        String filterName,
        String optionCode,
        String optionValue
    ) {
        ProductFilterOption relation = productFilterOptionRepository.findById(relationId)
            .orElseThrow(() -> new IllegalArgumentException("Опция фильтра не найдена"));
        Product product = relation.getProduct();
        if (product == null) {
            throw new IllegalArgumentException("Товар не найден для опции фильтра");
        }

        String normalizedFilterCode = resolveFilterCode(filterCode, filterName);
        String normalizedFilterName = filterName == null ? "" : filterName.trim();
        String normalizedOptionCode = resolveOptionCode(optionCode, optionValue);
        String normalizedOptionValue = optionValue == null ? "" : optionValue.trim();

        if (normalizedFilterCode.isBlank()) {
            throw new IllegalArgumentException("Код фильтра обязателен");
        }
        if (normalizedFilterName.isBlank()) {
            throw new IllegalArgumentException("Название фильтра обязательно");
        }
        if (normalizedOptionValue.isBlank()) {
            throw new IllegalArgumentException("Значение опции фильтра обязательно");
        }

        ProductFilter filter = productFilterRepository.findByCode(normalizedFilterCode)
            .orElseGet(ProductFilter::new);
        filter.setCode(normalizedFilterCode);
        filter.setName(normalizedFilterName);
        filter = productFilterRepository.save(filter);

        FilterOption option = filterOptionRepository.findByFilterAndCode(filter, normalizedOptionCode)
            .orElseGet(FilterOption::new);
        option.setFilter(filter);
        option.setCode(normalizedOptionCode);
        option.setValue(normalizedOptionValue);
        option = filterOptionRepository.save(option);

        ProductFilterOption existing = productFilterOptionRepository.findByProductAndFilterOption(product, option).orElse(null);
        if (existing != null && !existing.getId().equals(relation.getId())) {
            productFilterOptionRepository.delete(relation);
            return existing;
        }

        relation.setFilterOption(option);
        return productFilterOptionRepository.save(relation);
    }

    @Transactional
    public Long deleteProductFilterOption(Long relationId) {
        ProductFilterOption relation = productFilterOptionRepository.findById(relationId)
            .orElseThrow(() -> new IllegalArgumentException("Опция фильтра не найдена"));
        Long productId = relation.getProduct() != null ? relation.getProduct().getId() : null;
        productFilterOptionRepository.delete(relation);
        return productId;
    }

    @Transactional(readOnly = true)
    public List<ProductCharacteristic> productCharacteristics(Long productId) {
        return characteristicRepository.findByProductIdOrderBySortOrderAsc(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductFilterOption> productFilterOptions(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        return productFilterOptionRepository.findByProduct(product);
    }

    @Transactional(readOnly = true)
    public List<ProductImage> productImages(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        return productImageRepository.findByProductOrderBySortOrderAscIdAsc(product);
    }

    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        ProductImage image = productImageRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Изображение не найдено"));
        if (!image.getProduct().getId().equals(product.getId())) {
            throw new IllegalArgumentException("Изображение не принадлежит указанному товару");
        }
        productImageRepository.delete(image);
        List<ProductImage> remaining = productImageRepository.findByProductOrderBySortOrderAscIdAsc(product);
        if (remaining.isEmpty()) {
            product.setImageUrl(null);
        } else {
            product.setImageUrl(remaining.getFirst().getImageUrl());
        }
        productRepository.save(product);
    }

    @Transactional
    public ShopOrder updateOrderStatus(Long orderId, OrderStatus status) {
        ShopOrder order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        order.setStatus(status);
        return orderRepository.save(order);
    }

    @Transactional
    public void updateAdminCredentials(String username, String rawPassword) {
        ShopUser admin = currentUserService.findCurrentUser()
            .filter(user -> Boolean.TRUE.equals(user.getIsAdmin()))
            .or(() -> userRepository.findFirstByIsAdminTrue())
            .orElseThrow(() -> new IllegalStateException("Admin user not found"));

        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Логин не может быть пустым");
        }
        if (!admin.getUsername().equalsIgnoreCase(normalizedUsername) && userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Логин уже занят");
        }

        admin.setUsername(normalizedUsername);
        if (rawPassword != null && !rawPassword.isBlank()) {
            if (rawPassword.length() < 6) {
                throw new IllegalArgumentException("Пароль должен содержать минимум 6 символов");
            }
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        userRepository.save(admin);
    }

    @Transactional(readOnly = true)
    public String resolveAdminUsername() {
        return currentUserService.findCurrentUser()
            .filter(user -> Boolean.TRUE.equals(user.getIsAdmin()))
            .map(ShopUser::getUsername)
            .or(() -> userRepository.findFirstByIsAdminTrue().map(ShopUser::getUsername))
            .orElse("admin");
    }

    private static String resolveBuyerKey(ShopOrder order) {
        if (order == null) {
            return null;
        }
        if (order.getUser() != null && order.getUser().getId() != null) {
            return "U:" + order.getUser().getId();
        }
        if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) {
            return "E:" + order.getCustomerEmail().trim().toLowerCase(Locale.ROOT);
        }
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            return "N:" + order.getCustomerName().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static final class ProductSalesSummaryAccumulator {
        private int totalQuantity;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final Set<String> buyerKeys = new LinkedHashSet<>();
        private final List<ProductPurchaseInfo> recentPurchases = new ArrayList<>();
    }

    private void appendCategoryRows(
        Category category,
        int depth,
        Long parentId,
        List<CategoryTreeRow> rows,
        Map<Long, List<Category>> childrenByParentId
    ) {
        List<Category> children = childrenByParentId.getOrDefault(category.getId(), List.of());
        rows.add(new CategoryTreeRow(category, depth, children.isEmpty(), parentId));
        for (Category child : children) {
            appendCategoryRows(child, depth + 1, category.getId(), rows, childrenByParentId);
        }
    }

    private static Comparator<Category> resolveCategoryComparator(String sortMode) {
        String normalizedSort = sortMode == null ? "" : sortMode.trim().toLowerCase(Locale.ROOT);
        Comparator<Category> byIdAsc = Comparator.comparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Category> byIdDesc = Comparator.comparing(Category::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Category> byNameAsc = Comparator.comparing(
            (Category category) -> category.getName() == null ? "" : category.getName(),
            String.CASE_INSENSITIVE_ORDER
        ).thenComparing(byIdAsc);
        Comparator<Category> byNameDesc = Comparator.comparing(
            (Category category) -> category.getName() == null ? "" : category.getName(),
            String.CASE_INSENSITIVE_ORDER.reversed()
        ).thenComparing(byIdAsc);
        return switch (normalizedSort) {
            case "date_desc" -> byIdDesc;
            case "date_asc" -> byIdAsc;
            case "name_desc" -> byNameDesc;
            case "name_asc" -> byNameAsc;
            default -> byIdAsc;
        };
    }

    private Category resolveParent(Long parentId, Long currentCategoryId) {
        if (parentId == null || parentId <= 0) {
            return null;
        }
        if (currentCategoryId != null && parentId.equals(currentCategoryId)) {
            throw new IllegalArgumentException("Категория не может быть родителем самой себя");
        }
        return categoryRepository.findById(parentId)
            .orElseThrow(() -> new IllegalArgumentException("Родительская категория не найдена"));
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Название категории обязательно");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String resolveFilterCode(String filterCode, String filterName) {
        String source = filterCode == null || filterCode.isBlank() ? filterName : filterCode;
        if (source == null || source.isBlank()) {
            return "";
        }
        return resolveSlug(source, source);
    }

    private static String resolveOptionCode(String optionCode, String optionValue) {
        String source = optionCode == null || optionCode.isBlank() ? optionValue : optionCode;
        if (source == null || source.isBlank()) {
            return "";
        }
        return resolveSlug(source, source);
    }

    private String resolveUniqueArticle(String article, Long currentProductId) {
        if (article == null || article.isBlank()) {
            return null;
        }
        String normalized = article.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Артикул не должен превышать 64 символа");
        }
        Product articleOwner = productRepository.findByArticleIgnoreCase(normalized).orElse(null);
        if (articleOwner != null && (currentProductId == null || !articleOwner.getId().equals(currentProductId))) {
            throw new IllegalArgumentException("Артикул уже используется");
        }
        return normalized;
    }

    private String generateDefaultArticle(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Не удалось сгенерировать артикул");
        }
        String article = String.format(Locale.ROOT, "ART-%06d", productId);
        Product articleOwner = productRepository.findByArticleIgnoreCase(article).orElse(null);
        if (articleOwner == null || articleOwner.getId().equals(productId)) {
            return article;
        }
        return String.format(Locale.ROOT, "ART-%06d-%d", productId, productId % 97);
    }

    private String generateDuplicateName(String sourceName) {
        String base = sourceName == null ? "" : sourceName.trim();
        if (base.isBlank()) {
            base = "Товар";
        }
        int suffix = 2;
        String candidate = "";
        while (candidate.isBlank() || productRepository.findByName(candidate).isPresent()) {
            String postfix = suffix == 2 ? " (копия)" : " (копия " + (suffix - 1) + ")";
            int maxBaseLength = Math.max(1, PRODUCT_NAME_MAX_LENGTH - postfix.length());
            String normalizedBase = trimToLength(base, maxBaseLength);
            candidate = normalizedBase + postfix;
            suffix++;
        }
        return candidate;
    }

    private String generateDuplicateSlug(String sourceSlug) {
        String base = sourceSlug == null ? "" : sourceSlug.trim().toLowerCase(Locale.ROOT);
        if (base.isBlank()) {
            base = "product";
        }
        base = base
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "product";
        }
        int suffix = 2;
        String candidate = "";
        while (candidate.isBlank() || productRepository.findBySlug(candidate).isPresent()) {
            String postfix = suffix == 2 ? "-copy" : "-copy-" + (suffix - 1);
            int maxBaseLength = Math.max(1, PRODUCT_SLUG_MAX_LENGTH - postfix.length());
            String normalizedBase = trimToLength(base, maxBaseLength)
                .replaceAll("(^-|-$)", "");
            if (normalizedBase.isBlank()) {
                normalizedBase = "product";
                normalizedBase = trimToLength(normalizedBase, Math.max(1, PRODUCT_SLUG_MAX_LENGTH - postfix.length()));
            }
            candidate = normalizedBase + postfix;
            suffix++;
        }
        return candidate;
    }

    private static String trimToLength(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private String requireUniqueCategorySlug(String slug, Long currentId) {
        Category existing = categoryRepository.findBySlug(slug).orElse(null);
        if (existing != null && (currentId == null || !existing.getId().equals(currentId))) {
            throw new IllegalArgumentException("Slug уже используется другой категорией");
        }
        return slug;
    }

    private static String resolveSlug(String name, String slug) {
        String source = slug == null || slug.isBlank() ? name : slug;
        String transliterated = transliterateRu(source == null ? "" : source.trim().toLowerCase(Locale.ROOT));
        String normalized = transliterated
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Не удалось сформировать slug");
        }
        return normalized;
    }

    private static String transliterateRu(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('а', "a"); map.put('б', "b"); map.put('в', "v"); map.put('г', "g"); map.put('д', "d");
        map.put('е', "e"); map.put('ё', "e"); map.put('ж', "zh"); map.put('з', "z"); map.put('и', "i");
        map.put('й', "y"); map.put('к', "k"); map.put('л', "l"); map.put('м', "m"); map.put('н', "n");
        map.put('о', "o"); map.put('п', "p"); map.put('р', "r"); map.put('с', "s"); map.put('т', "t");
        map.put('у', "u"); map.put('ф', "f"); map.put('х', "h"); map.put('ц', "c"); map.put('ч', "ch");
        map.put('ш', "sh"); map.put('щ', "sch"); map.put('ъ', ""); map.put('ы', "y"); map.put('ь', "");
        map.put('э', "e"); map.put('ю', "yu"); map.put('я', "ya");
        StringBuilder out = new StringBuilder();
        for (char ch : input.toCharArray()) {
            out.append(map.getOrDefault(ch, String.valueOf(ch)));
        }
        return out.toString();
    }

    private List<String> mergeImageUrls(List<String> libraryImageUrls, List<String> uploadedImageUrls) {
        List<String> merged = new ArrayList<>();
        if (libraryImageUrls != null) {
            for (String url : libraryImageUrls) {
                if (url == null) {
                    continue;
                }
                String normalized = url.trim();
                if (normalized.isBlank() || merged.contains(normalized)) {
                    continue;
                }
                merged.add(normalized);
            }
        }
        if (uploadedImageUrls != null) {
            for (String url : uploadedImageUrls) {
                if (url == null) {
                    continue;
                }
                String normalized = url.trim();
                if (normalized.isBlank() || merged.contains(normalized)) {
                    continue;
                }
                merged.add(normalized);
            }
        }
        return merged;
    }

    private void applyCopyPayloadFromSource(Product target, Product source) {
        if (target == null || source == null || target.getId() == null || source.getId() == null) {
            return;
        }
        if (target.getId().equals(source.getId())) {
            return;
        }

        if (characteristicRepository.findByProductOrderBySortOrderAsc(target).isEmpty()) {
            List<ProductCharacteristic> sourceCharacteristics = characteristicRepository.findByProductOrderBySortOrderAsc(source);
            if (!sourceCharacteristics.isEmpty()) {
                List<ProductCharacteristic> copiedCharacteristics = new ArrayList<>();
                for (ProductCharacteristic sourceCharacteristic : sourceCharacteristics) {
                    ProductCharacteristic copy = new ProductCharacteristic();
                    copy.setProduct(target);
                    copy.setName(sourceCharacteristic.getName());
                    copy.setValue(sourceCharacteristic.getValue());
                    copy.setSortOrder(sourceCharacteristic.getSortOrder() == null ? 0 : sourceCharacteristic.getSortOrder());
                    copiedCharacteristics.add(copy);
                }
                characteristicRepository.saveAll(copiedCharacteristics);
            }
        }

        if (productFilterOptionRepository.findByProduct(target).isEmpty()) {
            List<ProductFilterOption> sourceFilterOptions = productFilterOptionRepository.findByProduct(source);
            if (!sourceFilterOptions.isEmpty()) {
                Set<Long> copiedFilterOptionIds = new HashSet<>();
                List<ProductFilterOption> copiedFilterOptions = new ArrayList<>();
                for (ProductFilterOption sourceFilterOption : sourceFilterOptions) {
                    if (sourceFilterOption.getFilterOption() == null || sourceFilterOption.getFilterOption().getId() == null) {
                        continue;
                    }
                    Long optionId = sourceFilterOption.getFilterOption().getId();
                    if (!copiedFilterOptionIds.add(optionId)) {
                        continue;
                    }
                    ProductFilterOption copy = new ProductFilterOption();
                    copy.setProduct(target);
                    copy.setFilterOption(sourceFilterOption.getFilterOption());
                    copiedFilterOptions.add(copy);
                }
                if (!copiedFilterOptions.isEmpty()) {
                    productFilterOptionRepository.saveAll(copiedFilterOptions);
                }
            }
        }
    }

    private void deleteProductInternal(Product product) {
        if (orderItemRepository.existsByProduct(product)) {
            throw new IllegalArgumentException("Нельзя удалить товар, который уже есть в заказах");
        }
        try {
            Long productId = product.getId();
            reviewRepository.clearParentLinksByProductId(productId);
            reviewImageRepository.deleteByProductId(productId);
            reviewRepository.deleteByProductId(productId);
            reviewRepository.flush();

            productFilterOptionRepository.deleteByProduct(product);
            characteristicRepository.deleteByProduct(product);
            productImageRepository.deleteByProduct(product);
            product.getCategories().clear();
            productRepository.save(product);
            productRepository.flush();

            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Нельзя удалить товар, связанный с заказами или другими данными");
        }
    }

    private void replaceProductImages(Product product, List<String> imageUrls) {
        productImageRepository.deleteByProduct(product);
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            if (url == null || url.isBlank()) {
                continue;
            }
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(url.trim());
            image.setSortOrder(i);
            images.add(image);
        }
        if (!images.isEmpty()) {
            productImageRepository.saveAll(images);
            if (product.getImages() != null) {
                product.getImages().clear();
                product.getImages().addAll(images);
            }
        }
    }
}


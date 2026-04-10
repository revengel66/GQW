package com.example.gqw.shop.web;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.service.AdminService;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.LibraryStorageService;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class AdminController {

    private record OrderReviewGroup(Product product, List<Review> reviews) {
    }

    private final AdminService adminService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final SupportService supportService;
    private final ReviewService reviewService;
    private final LibraryStorageService libraryStorageService;

    public AdminController(
        AdminService adminService,
        CatalogService catalogService,
        OrderService orderService,
        SupportService supportService,
        ReviewService reviewService,
        LibraryStorageService libraryStorageService
    ) {
        this.adminService = adminService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.supportService = supportService;
        this.reviewService = reviewService;
        this.libraryStorageService = libraryStorageService;
    }

    @GetMapping("/admin")
    public String adminHome(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false, defaultValue = "") String period,
        Model model
    ) {
        List<ShopOrder> orders = adminService.orders();
        List<ShopUser> users = adminService.users();
        List<?> products = adminService.products();
        int openRequestsCount = supportService.openRequests().size();
        int pendingReviewsCount = reviewService.pendingReviews().size();
        Instant recentUsersThreshold = Instant.now().minus(7, ChronoUnit.DAYS);
        long usersCount = users.stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
            .count();

        long newUsersCount = users.stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
            .filter(user -> user.getCreatedAt() != null && user.getCreatedAt().isAfter(recentUsersThreshold))
            .count();
        long newOrdersCount = orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.NEW)
            .count();

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate periodTo = to;
        LocalDate periodFrom = from;
        String normalizedPeriod = period == null ? "" : period.trim().toLowerCase(Locale.ROOT);
        if ((periodFrom == null || periodTo == null) && !normalizedPeriod.isBlank()) {
            if ("7d".equals(normalizedPeriod)) {
                periodTo = today;
                periodFrom = today.minusDays(6);
            } else if ("30d".equals(normalizedPeriod)) {
                periodTo = today;
                periodFrom = today.minusDays(29);
            } else if ("90d".equals(normalizedPeriod)) {
                periodTo = today;
                periodFrom = today.minusDays(89);
            }
        }
        if (periodTo == null) {
            periodTo = today;
        }
        if (periodFrom == null) {
            periodFrom = periodTo.minusDays(29);
        }
        if (periodFrom.isAfter(periodTo)) {
            LocalDate tmp = periodFrom;
            periodFrom = periodTo;
            periodTo = tmp;
        }

        Instant periodFromInclusive = periodFrom.atStartOfDay(zoneId).toInstant();
        Instant periodToExclusive = periodTo.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<ShopOrder> periodOrders = new ArrayList<>();
        for (ShopOrder order : orders) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            if (!order.getCreatedAt().isBefore(periodFromInclusive) && order.getCreatedAt().isBefore(periodToExclusive)) {
                periodOrders.add(order);
            }
        }

        Map<OrderStatus, Long> statusCounts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusCounts.put(status, 0L);
        }
        long periodCompletedOrdersCount = 0;
        long periodCancelledOrdersCount = 0;
        long periodDeliveryCount = 0;
        long periodPickupCount = 0;
        BigDecimal periodRevenue = BigDecimal.ZERO;
        for (ShopOrder order : periodOrders) {
            OrderStatus status = order.getStatus();
            if (status != null) {
                statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
            }
            if ("DELIVERY".equalsIgnoreCase(order.getDeliveryType())) {
                periodDeliveryCount++;
            } else {
                periodPickupCount++;
            }
            if (status == OrderStatus.REJECTED) {
                periodCancelledOrdersCount++;
                continue;
            }
            if (isSaleOrderStatus(status)) {
                periodCompletedOrdersCount++;
                if (order.getTotalAmount() != null) {
                    periodRevenue = periodRevenue.add(order.getTotalAmount());
                }
            }
        }

        long inProgressOrdersCount = Math.max(0, periodOrders.size() - periodCompletedOrdersCount - periodCancelledOrdersCount);
        BigDecimal periodAverageCheck = BigDecimal.ZERO;
        if (periodCompletedOrdersCount > 0) {
            periodAverageCheck = periodRevenue.divide(BigDecimal.valueOf(periodCompletedOrdersCount), 2, RoundingMode.HALF_UP);
        }

        long totalCompletedOrdersCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (ShopOrder order : orders) {
            if (isSaleOrderStatus(order.getStatus())) {
                totalCompletedOrdersCount++;
                if (order.getTotalAmount() != null) {
                    totalRevenue = totalRevenue.add(order.getTotalAmount());
                }
            }
        }

        Map<String, String> statusRu = orderStatusRuMap();
        List<Map<String, Object>> dashboardStatusStats = new ArrayList<>();
        long periodOrdersCount = periodOrders.size();
        for (OrderStatus status : OrderStatus.values()) {
            long count = statusCounts.getOrDefault(status, 0L);
            int percent = periodOrdersCount == 0 ? 0 : (int) Math.round((double) count * 100.0 / (double) periodOrdersCount);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", status.name());
            row.put("label", statusRu.getOrDefault(status.name(), status.name()));
            row.put("count", count);
            row.put("percent", percent);
            dashboardStatusStats.add(row);
        }

        model.addAttribute("productsCount", products.size());
        model.addAttribute("ordersCount", orders.size());
        model.addAttribute("usersCount", usersCount);
        model.addAttribute("openRequestsCount", openRequestsCount);
        model.addAttribute("pendingReviewsCount", pendingReviewsCount);
        model.addAttribute("newUsersCount", newUsersCount);
        model.addAttribute("newOrdersCount", newOrdersCount);
        model.addAttribute("newRequestsCount", openRequestsCount);
        model.addAttribute("newReviewsCount", pendingReviewsCount);
        model.addAttribute("adminUsername", adminService.resolveAdminUsername());
        model.addAttribute("dashboardFromDate", periodFrom);
        model.addAttribute("dashboardToDate", periodTo);
        model.addAttribute("dashboardPeriod", normalizedPeriod);
        model.addAttribute("periodOrdersCount", periodOrdersCount);
        model.addAttribute("periodCompletedOrdersCount", periodCompletedOrdersCount);
        model.addAttribute("periodCancelledOrdersCount", periodCancelledOrdersCount);
        model.addAttribute("periodInProgressOrdersCount", inProgressOrdersCount);
        model.addAttribute("periodRevenue", periodRevenue);
        model.addAttribute("periodAverageCheck", periodAverageCheck);
        model.addAttribute("periodDeliveryCount", periodDeliveryCount);
        model.addAttribute("periodPickupCount", periodPickupCount);
        model.addAttribute("totalCompletedOrdersCount", totalCompletedOrdersCount);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("dashboardStatusStats", dashboardStatusStats);
        return "admin/dashboard";
    }

    @GetMapping("/admin/products")
    public String products(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedTo,
        @RequestParam(required = false, defaultValue = "date_desc") String sort,
        @RequestParam(required = false, defaultValue = "20") Integer limit,
        Model model
    ) {
        List<Product> allProducts = adminService.products();
        List<AdminService.CategoryTreeRow> categoryRows = adminService.categoryTreeRows();

        Map<Long, List<Long>> childrenByParentId = new LinkedHashMap<>();
        Map<Long, Long> parentByCategoryId = new LinkedHashMap<>();
        for (AdminService.CategoryTreeRow row : categoryRows) {
            if (row.category() == null || row.category().getId() == null) {
                continue;
            }
            parentByCategoryId.put(row.category().getId(), row.parentId());
            if (row.parentId() == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(row.category().getId());
        }

        Set<Long> selectedCategoryIds = new HashSet<>();
        if (categoryId != null) {
            collectCategoryAndDescendants(categoryId, childrenByParentId, selectedCategoryIds);
        }
        Set<Long> openedCategoryIds = new HashSet<>();
        Long currentCategoryId = categoryId;
        while (currentCategoryId != null && openedCategoryIds.add(currentCategoryId)) {
            currentCategoryId = parentByCategoryId.get(currentCategoryId);
        }

        LocalDate normalizedFromMutable = addedFrom;
        LocalDate normalizedToMutable = addedTo;
        if (normalizedFromMutable != null && normalizedToMutable != null && normalizedFromMutable.isAfter(normalizedToMutable)) {
            LocalDate temp = normalizedFromMutable;
            normalizedFromMutable = normalizedToMutable;
            normalizedToMutable = temp;
        }
        final LocalDate normalizedFrom = normalizedFromMutable;
        final LocalDate normalizedTo = normalizedToMutable;
        String normalizedSort = sort == null ? "date_desc" : sort.trim().toLowerCase(Locale.ROOT);
        if (!List.of("date_desc", "date_asc", "name_asc", "name_desc", "price_asc", "price_desc").contains(normalizedSort)) {
            normalizedSort = "date_desc";
        }
        int pageStep = 20;
        int resolvedLimit = limit == null ? pageStep : Math.max(pageStep, Math.min(limit, 500));
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        ZoneId zoneId = ZoneId.systemDefault();

        List<Product> filtered = allProducts.stream()
            .filter(product -> {
                if (selectedCategoryIds.isEmpty()) {
                    return true;
                }
                if (product.getCategories() == null || product.getCategories().isEmpty()) {
                    return false;
                }
                return product.getCategories().stream()
                    .anyMatch(category -> category != null && category.getId() != null && selectedCategoryIds.contains(category.getId()));
            })
            .filter(product -> {
                if (query.isBlank()) {
                    return true;
                }
                String idValue = product.getId() == null ? "" : String.valueOf(product.getId());
                String name = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ROOT);
                String slugValue = product.getSlug() == null ? "" : product.getSlug().toLowerCase(Locale.ROOT);
                String articleValue = product.getArticle() == null ? "" : product.getArticle().toLowerCase(Locale.ROOT);
                return idValue.contains(query) || name.contains(query) || slugValue.contains(query) || articleValue.contains(query);
            })
            .filter(product -> {
                if (product.getCreatedAt() == null) {
                    return normalizedFrom == null && normalizedTo == null;
                }
                LocalDate createdDate = product.getCreatedAt().atZone(zoneId).toLocalDate();
                if (normalizedFrom != null && createdDate.isBefore(normalizedFrom)) {
                    return false;
                }
                if (normalizedTo != null && createdDate.isAfter(normalizedTo)) {
                    return false;
                }
                return true;
            })
            .toList();

        Comparator<Product> comparator = Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("date_asc".equals(normalizedSort)) {
            comparator = Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("name_asc".equals(normalizedSort)) {
            comparator = Comparator.comparing(p -> p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT));
        } else if ("name_desc".equals(normalizedSort)) {
            comparator = Comparator.comparing((Product p) -> p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT)).reversed();
        } else if ("price_asc".equals(normalizedSort)) {
            comparator = Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("price_desc".equals(normalizedSort)) {
            comparator = Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.reverseOrder()));
        }

        filtered = filtered.stream()
            .sorted(comparator.thenComparing(Product::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        int totalFiltered = filtered.size();
        int visibleCount = Math.min(totalFiltered, resolvedLimit);
        List<Product> visibleProducts = filtered.subList(0, visibleCount);
        boolean hasMoreProducts = visibleCount < totalFiltered;
        int nextLimit = hasMoreProducts ? Math.min(totalFiltered, resolvedLimit + pageStep) : resolvedLimit;

        model.addAttribute("products", visibleProducts);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("openedCategoryIds", openedCategoryIds);
        model.addAttribute("categoryTree", catalogService.categoryTree());
        model.addAttribute("productSearchQuery", q == null ? "" : q.trim());
        model.addAttribute("addedFrom", normalizedFrom);
        model.addAttribute("addedTo", normalizedTo);
        model.addAttribute("productSort", normalizedSort);
        model.addAttribute("productLimit", resolvedLimit);
        model.addAttribute("nextProductLimit", nextLimit);
        model.addAttribute("hasMoreProducts", hasMoreProducts);
        model.addAttribute("productsShown", visibleCount);
        model.addAttribute("productsTotal", totalFiltered);
        return "admin/products";
    }

    @GetMapping("/admin/products/new")
    public String newProduct(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long copySourceProductId,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        model.addAttribute("categoryRows", adminService.categoryTreeRows());
        model.addAttribute("filters", adminService.filters());
        model.addAttribute("libraryFiles", libraryStorageService.listFiles());
        model.addAttribute("editingProduct", null);
        model.addAttribute("preselectedCategoryId", categoryId);
        model.addAttribute("editingProductCategoryIds", List.of());
        model.addAttribute("productFormMode", "create");
        model.addAttribute("copySourceProductId", null);
        model.addAttribute("copyDraft", null);
        model.addAttribute("copyDraftImageUrls", List.of());
        model.addAttribute("productCharacteristics", List.of());
        model.addAttribute("productFilterOptions", List.of());
        model.addAttribute("productImages", List.of());
        model.addAttribute("productReviews", List.of());
        model.addAttribute("reviewRepliesById", Map.of());
        model.addAttribute("productSalesSummary", new AdminService.ProductSalesSummary(0, 0, 0, BigDecimal.ZERO, List.of()));

        if (copySourceProductId != null && copySourceProductId > 0) {
            try {
                AdminService.ProductCopyDraft copyDraft = adminService.buildProductCopyDraft(copySourceProductId);
                model.addAttribute("copySourceProductId", copySourceProductId);
                model.addAttribute("copyDraft", copyDraft);
                model.addAttribute("copyDraftImageUrls", copyDraft.imageUrls());
                model.addAttribute("editingProductCategoryIds", copyDraft.categoryIds());
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("productError", ex.getMessage());
                return "redirect:/admin/products";
            }
        }
        return "admin/product-form";
    }

    @GetMapping("/admin/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("categoryRows", adminService.categoryTreeRows());
            model.addAttribute("filters", adminService.filters());
            model.addAttribute("libraryFiles", libraryStorageService.listFiles());
            var product = adminService.productById(id);
            model.addAttribute("editingProduct", product);
            model.addAttribute("editingProductCategoryIds", product.getCategories().stream().map(c -> c.getId()).toList());
            model.addAttribute("productCharacteristics", adminService.productCharacteristics(id));
            model.addAttribute("productFilterOptions", adminService.productFilterOptions(id));
            model.addAttribute("productImages", adminService.productImages(id));
            List<Review> productReviews = adminService.reviewsByProduct(id);
            Map<Long, List<Review>> reviewRepliesById = new LinkedHashMap<>();
            for (Review review : productReviews) {
                reviewRepliesById.put(review.getId(), reviewService.replies(review));
            }
            model.addAttribute("productReviews", productReviews);
            model.addAttribute("reviewRepliesById", reviewRepliesById);
            AdminService.ProductSalesSummary productSalesSummary = adminService.productSalesSummary(List.of(product), 20)
                .getOrDefault(product.getId(), new AdminService.ProductSalesSummary(0, 0, 0, BigDecimal.ZERO, List.of()));
            model.addAttribute("productSalesSummary", productSalesSummary);
            model.addAttribute("productFormMode", "edit");
            model.addAttribute("copySourceProductId", null);
            model.addAttribute("copyDraft", null);
            model.addAttribute("copyDraftImageUrls", List.of());
            return "admin/product-form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
            return "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/save")
    public String saveProduct(
        @RequestParam String name,
        @RequestParam String slug,
        @RequestParam(required = false) String article,
        @RequestParam String shortDescription,
        @RequestParam String description,
        @RequestParam BigDecimal price,
        @RequestParam(required = false) BigDecimal oldPrice,
        @RequestParam(defaultValue = "false") boolean isNew,
        @RequestParam(defaultValue = "false") boolean isHit,
        @RequestParam(defaultValue = "false") boolean isDiscount,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<MultipartFile> images,
        @RequestParam(required = false) List<String> libraryImageUrls,
        @RequestParam(required = false) Long copySourceProductId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            var created = adminService.createProduct(
                name,
                slug,
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
                libraryImageUrls,
                copySourceProductId
            );
            redirectAttributes.addFlashAttribute("productSuccess", "Товар сохранён");
            return "redirect:/admin/products/" + created.getId() + "/edit";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products")
    public String createProduct(
        @RequestParam String name,
        @RequestParam String slug,
        @RequestParam(required = false) String article,
        @RequestParam String shortDescription,
        @RequestParam String description,
        @RequestParam BigDecimal price,
        @RequestParam(required = false) BigDecimal oldPrice,
        @RequestParam(defaultValue = "false") boolean isNew,
        @RequestParam(defaultValue = "false") boolean isHit,
        @RequestParam(defaultValue = "false") boolean isDiscount,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<MultipartFile> images,
        @RequestParam(required = false) List<String> libraryImageUrls,
        @RequestParam(required = false) Long copySourceProductId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            var created = adminService.createProduct(
                name,
                slug,
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
                libraryImageUrls,
                copySourceProductId
            );
            redirectAttributes.addFlashAttribute("productSuccess", "Товар создан");
            return "redirect:/admin/products/" + created.getId() + "/edit";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/{id}")
    public String updateProduct(
        @PathVariable Long id,
        @RequestParam String name,
        @RequestParam String slug,
        @RequestParam(required = false) String article,
        @RequestParam String shortDescription,
        @RequestParam String description,
        @RequestParam BigDecimal price,
        @RequestParam(required = false) BigDecimal oldPrice,
        @RequestParam(defaultValue = "false") boolean isNew,
        @RequestParam(defaultValue = "false") boolean isHit,
        @RequestParam(defaultValue = "false") boolean isDiscount,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<MultipartFile> images,
        @RequestParam(required = false) List<String> libraryImageUrls,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateProduct(
                id,
                name,
                slug,
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
            redirectAttributes.addFlashAttribute("productSuccess", "Товар обновлён");
            return "redirect:/admin/products/" + id + "/edit";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/characteristics/save")
    public String saveProductCharacteristic(
        @RequestParam Long productId,
        @RequestParam String name,
        @RequestParam String value,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.addProductCharacteristic(productId, name, value, 0);
            redirectAttributes.addFlashAttribute("characteristicSuccess", "Характеристика добавлена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("characteristicError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/characteristics/{id}")
    public String updateProductCharacteristic(
        @PathVariable Long id,
        @RequestParam String name,
        @RequestParam String value,
        @RequestParam Long productId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateProductCharacteristic(id, name, value);
            redirectAttributes.addFlashAttribute("characteristicSuccess", "Характеристика обновлена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("characteristicError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/characteristics/{id}/delete")
    public String deleteProductCharacteristic(
        @PathVariable Long id,
        @RequestParam(required = false) Long productId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Long resolvedProductId = adminService.deleteProductCharacteristic(id);
            redirectAttributes.addFlashAttribute("characteristicSuccess", "Характеристика удалена");
            return "redirect:/admin/products/" + (productId != null ? productId : resolvedProductId) + "/edit";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("characteristicError", ex.getMessage());
            return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/filter-options/save")
    public String saveProductFilterOption(
        @RequestParam Long productId,
        @RequestParam String filterCode,
        @RequestParam(required = false) String filterCodeCustom,
        @RequestParam String filterName,
        @RequestParam(required = false) String optionCode,
        @RequestParam String optionValue,
        RedirectAttributes redirectAttributes
    ) {
        try {
            String resolvedFilterCode = "__custom__".equalsIgnoreCase(filterCode)
                ? filterCodeCustom
                : filterCode;
            adminService.addProductFilterOption(productId, resolvedFilterCode, filterName, optionCode, optionValue);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Опция фильтра привязана к товару");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/filter-options/{id}")
    public String updateProductFilterOption(
        @PathVariable Long id,
        @RequestParam Long productId,
        @RequestParam String filterCode,
        @RequestParam(required = false) String filterCodeCustom,
        @RequestParam String filterName,
        @RequestParam(required = false) String optionCode,
        @RequestParam String optionValue,
        RedirectAttributes redirectAttributes
    ) {
        try {
            String resolvedFilterCode = "__custom__".equalsIgnoreCase(filterCode)
                ? filterCodeCustom
                : filterCode;
            adminService.updateProductFilterOption(id, resolvedFilterCode, filterName, optionCode, optionValue);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Опция фильтра обновлена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/filter-options/{id}/delete")
    public String deleteProductFilterOption(
        @PathVariable Long id,
        @RequestParam(required = false) Long productId,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Long resolvedProductId = adminService.deleteProductFilterOption(id);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Опция фильтра удалена");
            return "redirect:/admin/products/" + (productId != null ? productId : resolvedProductId) + "/edit";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
            return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            adminService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("productSuccess", "Товар удалён");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("productError", "Не удалось удалить товар из-за связанных данных");
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/{id}/duplicate")
    public String duplicateProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            adminService.productById(id);
            return "redirect:/admin/products/new?copySourceProductId=" + id;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
            return "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/{id}/images/{imageId}/delete")
    public String deleteProductImage(@PathVariable Long id, @PathVariable Long imageId, RedirectAttributes redirectAttributes) {
        try {
            adminService.deleteProductImage(id, imageId);
            redirectAttributes.addFlashAttribute("productSuccess", "Изображение удалено");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products/" + id + "/edit";
    }

    @GetMapping("/admin/categories")
    public String categories(
        @RequestParam(required = false, defaultValue = "date_desc") String sort,
        Model model
    ) {
        String normalizedSort = sort == null ? "date_desc" : sort.trim().toLowerCase(Locale.ROOT);
        if (!List.of("date_desc", "date_asc", "name_asc", "name_desc").contains(normalizedSort)) {
            normalizedSort = "date_desc";
        }
        model.addAttribute("categorySort", normalizedSort);
        model.addAttribute("categoryRows", adminService.categoryTreeRows(normalizedSort));
        return "admin/categories";
    }

    @GetMapping("/admin/categories/new")
    public String newCategory(Model model) {
        model.addAttribute("categoryRows", adminService.categoryTreeRows());
        model.addAttribute("libraryFiles", libraryStorageService.listFiles());
        model.addAttribute("categoryFormMode", "create");
        model.addAttribute("editingCategory", null);
        return "admin/category-form";
    }

    @GetMapping("/admin/categories/{id}/edit")
    public String editCategory(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Category category = adminService.categoryById(id);
            model.addAttribute("categoryRows", adminService.categoryTreeRows());
            model.addAttribute("libraryFiles", libraryStorageService.listFiles());
            model.addAttribute("categoryFormMode", "edit");
            model.addAttribute("editingCategory", category);
            model.addAttribute("categoryProducts", adminService.categoryProducts(id));
            return "admin/category-form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/admin/categories")
    public String createCategory(
        @RequestParam String name,
        @RequestParam(required = false) String slug,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Long parentId,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(required = false) String libraryImageUrl,
        @RequestParam(required = false) MultipartFile image,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.createCategory(name, slug, description, parentId, isPublished, libraryImageUrl, image);
            redirectAttributes.addFlashAttribute("categorySuccess", "Категория сохранена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}")
    public String updateCategory(
        @PathVariable Long id,
        @RequestParam String name,
        @RequestParam(required = false) String slug,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Long parentId,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(required = false) String libraryImageUrl,
        @RequestParam(required = false) MultipartFile image,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateCategory(id, name, slug, description, parentId, isPublished, libraryImageUrl, image);
            redirectAttributes.addFlashAttribute("categorySuccess", "Категория обновлена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/admin/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            adminService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("categorySuccess", "Категория удалена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("categoryError", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/orders")
    public String orders(Model model) {
        model.addAttribute("orders", adminService.orders());
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders";
    }

    @PostMapping("/admin/orders/{id}/status")
    public String updateOrderStatus(
        @PathVariable Long id,
        @RequestParam OrderStatus status,
        @RequestParam(required = false, defaultValue = "orders") String returnTo,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String orderStatus,
        @RequestParam(required = false) String orderType,
        @RequestParam(required = false) String orderSort,
        @RequestParam(required = false) String orderQuery,
        RedirectAttributes redirectAttributes
    ) {
        boolean returnToUser = "user".equalsIgnoreCase(returnTo) && userId != null;
        try {
            orderService.changeStatus(id, status);
            if (returnToUser) {
                redirectAttributes.addFlashAttribute("userSuccess", "Статус заказа обновлён");
            } else {
                redirectAttributes.addFlashAttribute("orderSuccess", "Статус заказа обновлён");
            }
        } catch (IllegalArgumentException ex) {
            if (returnToUser) {
                redirectAttributes.addFlashAttribute("userError", ex.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("orderError", ex.getMessage());
            }
        }

        if (returnToUser) {
            redirectAttributes.addAttribute("tab", tab == null ? "orders" : tab);
            if (orderStatus != null) {
                redirectAttributes.addAttribute("orderStatus", orderStatus);
            }
            if (orderType != null) {
                redirectAttributes.addAttribute("orderType", orderType);
            }
            if (orderSort != null) {
                redirectAttributes.addAttribute("orderSort", orderSort);
            }
            if (orderQuery != null) {
                redirectAttributes.addAttribute("orderQuery", orderQuery);
            }
            return "redirect:/admin/users/" + userId;
        }
        return "redirect:/admin/orders";
    }

    @GetMapping("/admin/users")
    public String users(
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false, defaultValue = "ALL") String userEnabled,
        @RequestParam(required = false, defaultValue = "NEWEST") String sort,
        @RequestParam(required = false, defaultValue = "1") Integer page,
        Model model
    ) {
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String enabledFilterRequested = userEnabled == null ? "ALL" : userEnabled.trim().toUpperCase(Locale.ROOT);
        String enabledFilter = switch (enabledFilterRequested) {
            case "ACTIVE", "BLOCKED" -> enabledFilterRequested;
            default -> "ALL";
        };
        String sortRequested = sort == null ? "NEWEST" : sort.trim().toUpperCase(Locale.ROOT);
        String sortMode = switch (sortRequested) {
            case "OLDEST", "NAME_ASC", "NAME_DESC", "ORDERS_DESC", "SPENT_DESC" -> sortRequested;
            default -> "NEWEST";
        };
        int currentPageRequested = page == null ? 1 : page;
        int pageSize = 20;

        List<ShopUser> allUsers = adminService.users().stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsAdmin()))
            .toList();
        List<ShopOrder> allOrders = adminService.orders();

        Map<Long, Integer> orderCountByUserId = new LinkedHashMap<>();
        Map<Long, BigDecimal> userTotalSpentById = new LinkedHashMap<>();
        for (ShopUser user : allUsers) {
            List<ShopOrder> userOrders = allOrders.stream()
                .filter(order -> order.getUser() != null && user.getId().equals(order.getUser().getId()))
                .toList();
            orderCountByUserId.put(user.getId(), userOrders.size());
            BigDecimal totalSpent = userOrders.stream()
                .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            userTotalSpentById.put(user.getId(), totalSpent);
        }

        List<ShopUser> filteredUsers = allUsers.stream()
            .filter(user -> {
                if ("ACTIVE".equals(enabledFilter)) {
                    return Boolean.TRUE.equals(user.getIsEnabled());
                }
                if ("BLOCKED".equals(enabledFilter)) {
                    return !Boolean.TRUE.equals(user.getIsEnabled());
                }
                return true;
            })
            .filter(user -> {
                if (search.isBlank()) {
                    return true;
                }
                String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase(Locale.ROOT);
                String fullName = user.getFullName() == null ? "" : user.getFullName().toLowerCase(Locale.ROOT);
                String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT);
                String phone = user.getPhone() == null ? "" : user.getPhone().toLowerCase(Locale.ROOT);
                return username.contains(search) || fullName.contains(search) || email.contains(search) || phone.contains(search);
            })
            .toList();

        Comparator<ShopUser> usersComparator = Comparator.comparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("OLDEST".equals(sortMode)) {
            usersComparator = Comparator.comparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("NAME_ASC".equals(sortMode)) {
            usersComparator = Comparator.comparing(
                user -> ((user.getFullName() == null || user.getFullName().isBlank()) ? user.getUsername() : user.getFullName()).toLowerCase(Locale.ROOT),
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if ("NAME_DESC".equals(sortMode)) {
            usersComparator = Comparator.comparing(
                user -> ((user.getFullName() == null || user.getFullName().isBlank()) ? user.getUsername() : user.getFullName()).toLowerCase(Locale.ROOT),
                Comparator.nullsLast(Comparator.reverseOrder())
            );
        } else if ("ORDERS_DESC".equals(sortMode)) {
            usersComparator = Comparator
                .comparing((ShopUser user) -> orderCountByUserId.getOrDefault(user.getId(), 0), Comparator.reverseOrder())
                .thenComparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if ("SPENT_DESC".equals(sortMode)) {
            usersComparator = Comparator
                .comparing((ShopUser user) -> userTotalSpentById.getOrDefault(user.getId(), BigDecimal.ZERO), Comparator.reverseOrder())
                .thenComparing(ShopUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        filteredUsers = filteredUsers.stream()
            .sorted(usersComparator)
            .toList();

        int totalUsers = filteredUsers.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalUsers / (double) pageSize));
        int currentPage = Math.max(1, Math.min(currentPageRequested, totalPages));
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalUsers);
        int toIndex = Math.min(fromIndex + pageSize, totalUsers);
        List<ShopUser> users = filteredUsers.subList(fromIndex, toIndex);

        Map<Long, Integer> pageOrderCountByUserId = new LinkedHashMap<>();
        Map<Long, BigDecimal> pageTotalSpentByUserId = new LinkedHashMap<>();
        for (ShopUser user : users) {
            pageOrderCountByUserId.put(user.getId(), orderCountByUserId.getOrDefault(user.getId(), 0));
            pageTotalSpentByUserId.put(user.getId(), userTotalSpentById.getOrDefault(user.getId(), BigDecimal.ZERO));
        }

        model.addAttribute("users", users);
        model.addAttribute("userOrderCountById", pageOrderCountByUserId);
        model.addAttribute("userTotalSpentById", pageTotalSpentByUserId);
        model.addAttribute("searchQuery", q == null ? "" : q.trim());
        model.addAttribute("userEnabledFilter", enabledFilter);
        model.addAttribute("userSort", sortMode);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("hasPrevPage", currentPage > 1);
        model.addAttribute("hasNextPage", currentPage < totalPages);
        model.addAttribute("prevPage", Math.max(1, currentPage - 1));
        model.addAttribute("nextPage", Math.min(totalPages, currentPage + 1));
        model.addAttribute("firstItemIndex", totalUsers == 0 ? 0 : fromIndex + 1);
        model.addAttribute("lastItemIndex", toIndex);
        return "admin/users";
    }

    @GetMapping("/admin/users/{id}")
    public String userDetails(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "orders") String tab,
        @RequestParam(required = false, defaultValue = "ALL") String orderStatus,
        @RequestParam(required = false, defaultValue = "ALL") String orderType,
        @RequestParam(required = false, defaultValue = "NEWEST") String orderSort,
        @RequestParam(required = false, defaultValue = "") String orderQuery,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser user;
        try {
            user = adminService.userById(id);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("userError", ex.getMessage());
            return "redirect:/admin/users";
        }
        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            redirectAttributes.addFlashAttribute("userError", "Администратор не отображается в списке пользователей");
            return "redirect:/admin/users";
        }

        String activeUserTab = tab == null ? "orders" : tab.trim().toLowerCase(Locale.ROOT);
        if (!"orders".equals(activeUserTab) && !"reviews".equals(activeUserTab) && !"requests".equals(activeUserTab)) {
            activeUserTab = "orders";
        }

        List<ShopOrder> allUserOrders = adminService.ordersByUser(user);
        List<SupportRequest> supportRequests = supportService.requestsByUser(user);
        List<Review> userReviews = reviewService.reviewsByUser(user);
        Map<Long, List<Review>> userReviewsByProductId = new LinkedHashMap<>();
        for (Review review : userReviews) {
            if (review.getProduct() == null || review.getProduct().getId() == null) {
                continue;
            }
            userReviewsByProductId.computeIfAbsent(review.getProduct().getId(), key -> new ArrayList<>()).add(review);
        }
        String normalizedStatusRequested = orderStatus == null ? "ALL" : orderStatus.trim().toUpperCase(Locale.ROOT);
        OrderStatus selectedStatus = null;
        if (!"ALL".equals(normalizedStatusRequested)) {
            try {
                selectedStatus = OrderStatus.valueOf(normalizedStatusRequested);
            } catch (IllegalArgumentException ignored) {
                selectedStatus = null;
            }
        }
        String normalizedStatus = selectedStatus == null ? "ALL" : selectedStatus.name();
        String normalizedTypeRequested = orderType == null ? "ALL" : orderType.trim().toUpperCase(Locale.ROOT);
        String normalizedType = switch (normalizedTypeRequested) {
            case "DELIVERY", "PICKUP" -> normalizedTypeRequested;
            default -> "ALL";
        };
        String normalizedSortRequested = orderSort == null ? "NEWEST" : orderSort.trim().toUpperCase(Locale.ROOT);
        String normalizedSort = switch (normalizedSortRequested) {
            case "OLDEST", "AMOUNT_DESC", "AMOUNT_ASC", "STATUS_ASC" -> normalizedSortRequested;
            default -> "NEWEST";
        };
        String query = orderQuery == null ? "" : orderQuery.trim().toLowerCase(Locale.ROOT);

        final OrderStatus selectedStatusFilter = selectedStatus;
        List<ShopOrder> orders = allUserOrders.stream()
            .filter(order -> selectedStatusFilter == null || order.getStatus() == selectedStatusFilter)
            .filter(order -> {
                if ("ALL".equals(normalizedType)) {
                    return true;
                }
                return normalizedType.equalsIgnoreCase(order.getDeliveryType());
            })
            .filter(order -> {
                if (query.isBlank()) {
                    return true;
                }
                String orderId = String.valueOf(order.getId());
                String customerName = order.getCustomerName() == null ? "" : order.getCustomerName().toLowerCase(Locale.ROOT);
                String customerEmail = order.getCustomerEmail() == null ? "" : order.getCustomerEmail().toLowerCase(Locale.ROOT);
                return orderId.contains(query) || customerName.contains(query) || customerEmail.contains(query);
            })
            .toList();

        Comparator<ShopOrder> ordersComparator = Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        if ("OLDEST".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(ShopOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("AMOUNT_DESC".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(
                order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount(),
                Comparator.reverseOrder()
            );
        } else if ("AMOUNT_ASC".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount());
        } else if ("STATUS_ASC".equals(normalizedSort)) {
            ordersComparator = Comparator.comparing(order -> order.getStatus() == null ? "" : order.getStatus().name());
        }
        orders = orders.stream()
            .sorted(ordersComparator)
            .toList();

        Map<Long, List<OrderItem>> orderItemsByOrderId = new LinkedHashMap<>();
        Map<Long, Integer> orderQuantityByOrderId = new LinkedHashMap<>();
        Map<Long, List<com.example.gqw.shop.entity.OrderStatusHistory>> orderHistoryByOrderId = new LinkedHashMap<>();
        Map<Long, List<OrderReviewGroup>> orderReviewGroupsByOrderId = new LinkedHashMap<>();
        for (ShopOrder order : orders) {
            List<OrderItem> items = orderService.itemsForOrder(order);
            orderItemsByOrderId.put(order.getId(), items);
            int totalQuantity = items.stream()
                .map(OrderItem::getQuantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();
            orderQuantityByOrderId.put(order.getId(), totalQuantity);
            orderHistoryByOrderId.put(order.getId(), orderService.statusHistoryForOrder(order));

            Map<Long, List<Review>> orderReviewsByProductId = new LinkedHashMap<>();
            Map<Long, Product> orderProductsById = new LinkedHashMap<>();
            for (OrderItem item : items) {
                if (item.getProduct() == null || item.getProduct().getId() == null) {
                    continue;
                }
                Long productId = item.getProduct().getId();
                orderProductsById.putIfAbsent(productId, item.getProduct());
                List<Review> productReviews = userReviewsByProductId.get(item.getProduct().getId());
                if (productReviews != null) {
                    orderReviewsByProductId.putIfAbsent(productId, new ArrayList<>(productReviews));
                }
            }
            List<OrderReviewGroup> reviewGroups = new ArrayList<>();
            for (Map.Entry<Long, List<Review>> entry : orderReviewsByProductId.entrySet()) {
                reviewGroups.add(new OrderReviewGroup(orderProductsById.get(entry.getKey()), entry.getValue()));
            }
            orderReviewGroupsByOrderId.put(order.getId(), reviewGroups);
        }

        BigDecimal totalSpentAll = allUserOrders.stream()
            .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpentFiltered = orders.stream()
            .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("user", user);
        model.addAttribute("orders", orders);
        model.addAttribute("orderItemsByOrderId", orderItemsByOrderId);
        model.addAttribute("orderQuantityByOrderId", orderQuantityByOrderId);
        model.addAttribute("orderHistoryByOrderId", orderHistoryByOrderId);
        model.addAttribute("orderReviewGroupsByOrderId", orderReviewGroupsByOrderId);
        model.addAttribute("allOrdersCount", allUserOrders.size());
        model.addAttribute("filteredOrdersCount", orders.size());
        model.addAttribute("totalSpentAll", totalSpentAll);
        model.addAttribute("totalSpentFiltered", totalSpentFiltered);
        model.addAttribute("orderStatusFilter", normalizedStatus);
        model.addAttribute("orderTypeFilter", normalizedType);
        model.addAttribute("orderSort", normalizedSort);
        model.addAttribute("orderQuery", orderQuery == null ? "" : orderQuery.trim());
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("orderStatusRuMap", orderStatusRuMap());
        model.addAttribute("supportRequests", supportRequests);
        model.addAttribute("userReviews", userReviews);
        model.addAttribute("activeUserTab", activeUserTab);
        return "admin/user-details";
    }

    @PostMapping("/admin/users/{id}/status")
    public String updateUserStatus(
        @PathVariable Long id,
        @RequestParam boolean enabled,
        @RequestParam(required = false, defaultValue = "list") String returnTo,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String userEnabled,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) String orderStatus,
        @RequestParam(required = false) String orderType,
        @RequestParam(required = false) String orderSort,
        @RequestParam(required = false) String orderQuery,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateUserEnabled(id, enabled);
            redirectAttributes.addFlashAttribute("userSuccess", enabled ? "Пользователь разблокирован" : "Пользователь заблокирован");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("userError", ex.getMessage());
        }
        if ("detail".equalsIgnoreCase(returnTo)) {
            if (tab != null) {
                redirectAttributes.addAttribute("tab", tab);
            }
            if (orderStatus != null) {
                redirectAttributes.addAttribute("orderStatus", orderStatus);
            }
            if (orderType != null) {
                redirectAttributes.addAttribute("orderType", orderType);
            }
            if (orderSort != null) {
                redirectAttributes.addAttribute("orderSort", orderSort);
            }
            if (orderQuery != null) {
                redirectAttributes.addAttribute("orderQuery", orderQuery);
            }
            return "redirect:/admin/users/" + id;
        }
        if (q != null) {
            redirectAttributes.addAttribute("q", q);
        }
        if (userEnabled != null) {
            redirectAttributes.addAttribute("userEnabled", userEnabled);
        }
        if (sort != null) {
            redirectAttributes.addAttribute("sort", sort);
        }
        if (page != null) {
            redirectAttributes.addAttribute("page", page);
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/support")
    public String support(Model model) {
        model.addAttribute("requests", supportService.openRequests());
        return "admin/support";
    }

    @PostMapping("/admin/support/{id}/processed")
    public String markSupportProcessed(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            supportService.markProcessed(id);
            redirectAttributes.addFlashAttribute("supportSuccess", "Заявка помечена как обработанная");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("supportError", ex.getMessage());
        }
        return "redirect:/admin/support";
    }

    @GetMapping("/admin/reviews")
    public String reviews(
        @RequestParam(defaultValue = "PENDING") String status,
        @RequestParam(required = false) Integer rating,
        @RequestParam(required = false) Long productId,
        Model model
    ) {
        model.addAttribute("reviews", reviewService.reviewsForAdmin(status, rating, productId));
        model.addAttribute("products", adminService.products());
        model.addAttribute("status", status);
        model.addAttribute("rating", rating);
        model.addAttribute("productId", productId);
        return "admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/moderate")
    public String moderateReview(@PathVariable Long id, @RequestParam boolean approved, RedirectAttributes redirectAttributes) {
        try {
            reviewService.moderate(id, approved);
            redirectAttributes.addFlashAttribute("reviewSuccess", "Модерация выполнена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/delete")
    public String deleteReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reviewService.deleteReview(id);
            redirectAttributes.addFlashAttribute("reviewSuccess", "Отзыв удалён");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    @GetMapping("/admin/files")
    public String files(Model model) {
        model.addAttribute("libraryFiles", libraryStorageService.listFiles());
        return "admin/files";
    }

    @PostMapping("/admin/files/upload")
    public String uploadFiles(@RequestParam(required = false) List<MultipartFile> files, RedirectAttributes redirectAttributes) {
        try {
            var stored = libraryStorageService.store(files);
            redirectAttributes.addFlashAttribute("filesSuccess", "Загружено файлов: " + stored.size());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filesError", ex.getMessage());
        }
        return "redirect:/admin/files";
    }

    @GetMapping("/admin/files/list")
    @ResponseBody
    public Map<String, Object> filesListApi() {
        return Map.of(
            "ok", true,
            "files", libraryStorageService.listFiles()
        );
    }

    @PostMapping("/admin/files/upload-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFilesJson(@RequestParam(required = false) List<MultipartFile> files) {
        try {
            var stored = libraryStorageService.store(files);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "uploadedCount", stored.size(),
                "files", libraryStorageService.listFiles()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/admin/filters")
    public String filters(Model model) {
        model.addAttribute("filters", adminService.filters());
        model.addAttribute("categoryRows", adminService.categoryTreeRows());
        model.addAttribute("filterCategoryIdsMap", adminService.filterCategoryIdsMap());
        model.addAttribute("filterOptionsMap", adminService.filterOptionsMap());
        return "admin/filters";
    }

    @PostMapping("/admin/filters/save")
    public String saveFilter(
        @RequestParam(required = false) Long filterId,
        @RequestParam String code,
        @RequestParam String name,
        @RequestParam(required = false) String valueType,
        @RequestParam(required = false) String viewType,
        @RequestParam(defaultValue = "false") boolean multiValue,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) String predefinedValues,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.saveFilter(filterId, code, name, valueType, viewType, multiValue, categoryIds, predefinedValues);
            redirectAttributes.addFlashAttribute("filterSuccess", "Фильтр сохранён");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filterError", ex.getMessage());
        }
        return "redirect:/admin/filters";
    }

    @PostMapping("/admin/filters/{id}/delete")
    public String deleteFilter(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            adminService.deleteFilter(id);
            redirectAttributes.addFlashAttribute("filterSuccess", "Фильтр удалён");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("filterError", ex.getMessage());
        }
        return "redirect:/admin/filters";
    }

    private static Map<String, String> orderStatusRuMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(OrderStatus.NEW.name(), "Новый");
        map.put(OrderStatus.ACCEPTED.name(), "Принят");
        map.put(OrderStatus.ASSEMBLED.name(), "Собран");
        map.put(OrderStatus.WAITING_PICKUP.name(), "Готов к выдаче");
        map.put(OrderStatus.DELIVERED.name(), "Доставлен");
        map.put(OrderStatus.REJECTED.name(), "Отменён");
        return map;
    }

    private static void collectCategoryAndDescendants(
        Long categoryId,
        Map<Long, List<Long>> childrenByParentId,
        Set<Long> result
    ) {
        if (categoryId == null || result.contains(categoryId)) {
            return;
        }
        result.add(categoryId);
        List<Long> children = childrenByParentId.get(categoryId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Long childId : children) {
            collectCategoryAndDescendants(childId, childrenByParentId, result);
        }
    }

    private static boolean isSaleOrderStatus(OrderStatus status) {
        return status == OrderStatus.ACCEPTED
            || status == OrderStatus.ASSEMBLED
            || status == OrderStatus.WAITING_PICKUP
            || status == OrderStatus.DELIVERED;
    }

    @PostMapping("/admin/credentials")
    public String updateCredentials(
        @RequestParam String username,
        @RequestParam String password,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.updateAdminCredentials(username, password);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("credentialsError", ex.getMessage());
            return "redirect:/admin";
        }

        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?credentialsUpdated=true";
    }
}


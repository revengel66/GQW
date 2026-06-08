package com.example.gqw.admin.controller;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.admin.service.AdminService;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.ReviewService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AdminProductsController {

    private final AdminService adminService;
    private final CatalogService catalogService;
    private final ReviewService reviewService;
    private final AdminControllerSupport controllerSupport;

    public AdminProductsController(
        AdminService adminService,
        CatalogService catalogService,
        ReviewService reviewService,
        AdminControllerSupport controllerSupport
    ) {
        this.adminService = adminService;
        this.catalogService = catalogService;
        this.reviewService = reviewService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/products")
    public String products(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedTo,
        @RequestParam(required = false, defaultValue = "ALL") String inStock,
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
            controllerSupport.collectCategoryAndDescendants(categoryId, childrenByParentId, selectedCategoryIds);
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
        String normalizedInStockRequested = inStock == null ? "ALL" : inStock.trim().toUpperCase(Locale.ROOT);
        String normalizedInStock = switch (normalizedInStockRequested) {
            case "IN_STOCK", "OUT_OF_STOCK" -> normalizedInStockRequested;
            default -> "ALL";
        };
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
            .filter(product -> {
                if ("IN_STOCK".equals(normalizedInStock)) {
                    return Boolean.TRUE.equals(product.getInStock());
                }
                if ("OUT_OF_STOCK".equals(normalizedInStock)) {
                    return !Boolean.TRUE.equals(product.getInStock());
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
        model.addAttribute("productInStockFilter", normalizedInStock);
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
        AdminControllerSupport.LibraryViewData libraryViewData = controllerSupport.prepareLibraryViewData();
        model.addAttribute("categoryRows", adminService.categoryTreeRows());
        model.addAttribute("categoryTree", catalogService.categoryTree());
        model.addAttribute("filters", adminService.filters());
        model.addAttribute("libraryFiles", libraryViewData.files());
        model.addAttribute("fileFolders", libraryViewData.folders());
        model.addAttribute("selectedLibraryFolder", libraryViewData.selectedFolder());
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
        List<Long> selectedCategoryIds = new ArrayList<>();
        if (categoryId != null && categoryId > 0) {
            selectedCategoryIds.add(categoryId);
        }

        if (copySourceProductId != null && copySourceProductId > 0) {
            try {
                AdminService.ProductCopyDraft copyDraft = adminService.buildProductCopyDraft(copySourceProductId);
                model.addAttribute("copySourceProductId", copySourceProductId);
                model.addAttribute("copyDraft", copyDraft);
                model.addAttribute("copyDraftImageUrls", copyDraft.imageUrls());
                model.addAttribute("editingProductCategoryIds", copyDraft.categoryIds());
                selectedCategoryIds.clear();
                selectedCategoryIds.addAll(copyDraft.categoryIds());
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("productError", ex.getMessage());
                return "redirect:/admin/products";
            }
        }
        model.addAttribute("availableProductFilters", adminService.availableFiltersForCategoryIds(selectedCategoryIds));
        model.addAttribute("filterOptionsByFilterId", adminService.filterOptionsForCategoryIds(selectedCategoryIds));
        return "admin/product-form";
    }

    @GetMapping("/admin/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            AdminControllerSupport.LibraryViewData libraryViewData = controllerSupport.prepareLibraryViewData();
            model.addAttribute("categoryRows", adminService.categoryTreeRows());
            model.addAttribute("categoryTree", catalogService.categoryTree());
            model.addAttribute("filters", adminService.filters());
            model.addAttribute("libraryFiles", libraryViewData.files());
            model.addAttribute("fileFolders", libraryViewData.folders());
            model.addAttribute("selectedLibraryFolder", libraryViewData.selectedFolder());
            var product = adminService.productById(id);
            model.addAttribute("editingProduct", product);
            List<Long> categoryIds = product.getCategories().stream().map(c -> c.getId()).toList();
            model.addAttribute("editingProductCategoryIds", categoryIds);
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
            model.addAttribute("availableProductFilters", adminService.availableFiltersForCategoryIds(categoryIds));
            model.addAttribute("filterOptionsByFilterId", adminService.filterOptionsForCategoryIds(categoryIds));
            return "admin/product-form";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
            return "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/save")
    public Object saveProduct(
        @RequestParam("productName") String name,
        @RequestParam("productSlug") String slug,
        @RequestParam(name = "productArticle", required = false) String article,
        @RequestParam("productShortDescription") String shortDescription,
        @RequestParam("productDescription") String description,
        @RequestParam("productPrice") BigDecimal price,
        @RequestParam(name = "productOldPrice", required = false) BigDecimal oldPrice,
        @RequestParam(defaultValue = "false") boolean isNew,
        @RequestParam(defaultValue = "false") boolean isHit,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<MultipartFile> images,
        @RequestParam(required = false) List<String> libraryImageUrls,
        @RequestParam(required = false) Long copySourceProductId,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
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
                isPublished,
                inStock,
                categoryIds,
                images,
                libraryImageUrls,
                copySourceProductId
            );
            if (controllerSupport.isAjaxRequest(request)) {
                return "redirect:/admin/products/" + created.getId() + "/edit";
            }
            redirectAttributes.addFlashAttribute("productSuccess", "Товар сохранён");
            return "redirect:/admin/products/" + created.getId() + "/edit";
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products")
    public Object createProduct(
        @RequestParam("productName") String name,
        @RequestParam("productSlug") String slug,
        @RequestParam(name = "productArticle", required = false) String article,
        @RequestParam("productShortDescription") String shortDescription,
        @RequestParam("productDescription") String description,
        @RequestParam("productPrice") BigDecimal price,
        @RequestParam(name = "productOldPrice", required = false) BigDecimal oldPrice,
        @RequestParam(defaultValue = "false") boolean isNew,
        @RequestParam(defaultValue = "false") boolean isHit,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<MultipartFile> images,
        @RequestParam(required = false) List<String> libraryImageUrls,
        @RequestParam(required = false) Long copySourceProductId,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
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
                isPublished,
                inStock,
                categoryIds,
                images,
                libraryImageUrls,
                copySourceProductId
            );
            if (controllerSupport.isAjaxRequest(request)) {
                return "redirect:/admin/products/" + created.getId() + "/edit";
            }
            redirectAttributes.addFlashAttribute("productSuccess", "Товар создан");
            return "redirect:/admin/products/" + created.getId() + "/edit";
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/{id}")
    public Object updateProduct(
        @PathVariable Long id,
        @RequestParam("productName") String name,
        @RequestParam("productSlug") String slug,
        @RequestParam(name = "productArticle", required = false) String article,
        @RequestParam("productShortDescription") String shortDescription,
        @RequestParam("productDescription") String description,
        @RequestParam("productPrice") BigDecimal price,
        @RequestParam(name = "productOldPrice", required = false) BigDecimal oldPrice,
        @RequestParam(defaultValue = "false") boolean isNew,
        @RequestParam(defaultValue = "false") boolean isHit,
        @RequestParam(defaultValue = "false") boolean isPublished,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<MultipartFile> images,
        @RequestParam(required = false) List<String> libraryImageUrls,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
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
                isPublished,
                inStock,
                categoryIds,
                images,
                libraryImageUrls
            );
            if (controllerSupport.isAjaxRequest(request)) {
                return "redirect:/admin/products/" + id + "/edit";
            }
            redirectAttributes.addFlashAttribute("productSuccess", "Товар обновлён");
            return "redirect:/admin/products/" + id + "/edit";
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/characteristics/save")
    public String saveProductCharacteristic(
        @RequestParam Long productId,
        @RequestParam String name,
        @RequestParam String value,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.addProductCharacteristic(productId, name, value, 0);
            redirectAttributes.addFlashAttribute("characteristicSuccess", "Характеристика добавлена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
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
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.updateProductCharacteristic(id, name, value);
            redirectAttributes.addFlashAttribute("characteristicSuccess", "Характеристика обновлена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("characteristicError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/characteristics/{id}/delete")
    public String deleteProductCharacteristic(
        @PathVariable Long id,
        @RequestParam(required = false) Long productId,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            Long resolvedProductId = adminService.deleteProductCharacteristic(id);
            redirectAttributes.addFlashAttribute("characteristicSuccess", "Характеристика удалена");
            return "redirect:/admin/products/" + (productId != null ? productId : resolvedProductId) + "/edit";
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("characteristicError", ex.getMessage());
            return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/filter-options/save")
    public String saveProductFilterOption(
        @RequestParam Long productId,
        @RequestParam Long filterId,
        @RequestParam String optionValue,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.addProductFilterOption(productId, filterId, optionValue);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Опция фильтра привязана к товару");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/filter-options/{id}")
    public String updateProductFilterOption(
        @PathVariable Long id,
        @RequestParam Long productId,
        @RequestParam String optionValue,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.updateProductFilterOption(id, optionValue);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Опция фильтра обновлена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/admin/products/filter-options/{id}/delete")
    public String deleteProductFilterOption(
        @PathVariable Long id,
        @RequestParam(required = false) Long productId,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            Long resolvedProductId = adminService.deleteProductFilterOption(id);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Опция фильтра удалена");
            return "redirect:/admin/products/" + (productId != null ? productId : resolvedProductId) + "/edit";
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
            return productId != null ? "redirect:/admin/products/" + productId + "/edit" : "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/{id}/delete")
    public String deleteProduct(
        @PathVariable Long id,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("productSuccess", "Товар удалён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("productError", "Не удалось удалить товар из-за связанных данных");
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/{id}/duplicate")
    public String duplicateProduct(
        @PathVariable Long id,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.productById(id);
            return "redirect:/admin/products/new?copySourceProductId=" + id;
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
            return "redirect:/admin/products";
        }
    }

    @PostMapping("/admin/products/{id}/images/{imageId}/delete")
    public String deleteProductImage(
        @PathVariable Long id,
        @PathVariable Long imageId,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            adminService.deleteProductImage(id, imageId);
            redirectAttributes.addFlashAttribute("productSuccess", "Изображение удалено");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("productError", ex.getMessage());
        }
        return "redirect:/admin/products/" + id + "/edit";
    }

    @PostMapping("/admin/products/{productId}/reviews/{reviewId}/moderate")
    public String moderateProductReview(
        @PathVariable Long productId,
        @PathVariable Long reviewId,
        @RequestParam boolean approved,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            reviewService.moderate(reviewId, approved);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Статус отзыва обновлён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit?tab=reviews";
    }

    @PostMapping("/admin/products/{productId}/reviews/{reviewId}/delete")
    public String deleteProductReview(
        @PathVariable Long productId,
        @PathVariable Long reviewId,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            reviewService.deleteReview(reviewId);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Отзыв удалён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit?tab=reviews";
    }

    @PostMapping("/admin/products/{productId}/reviews/{reviewId}/reply")
    public String replyProductReview(
        @PathVariable Long productId,
        @PathVariable Long reviewId,
        @RequestParam String text,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            Review reply = reviewService.reply(reviewId, text, null, null);
            reviewService.moderate(reply.getId(), true);
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Ответ на отзыв добавлен");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit?tab=reviews";
    }

    @PostMapping("/admin/products/{productId}/reviews/{reviewId}/reply/{replyId}")
    public String updateProductReviewReply(
        @PathVariable Long productId,
        @PathVariable Long reviewId,
        @PathVariable Long replyId,
        @RequestParam String text,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        try {
            Review updated = reviewService.updateReviewText(replyId, text);
            if (updated.getParent() == null || !reviewId.equals(updated.getParent().getId())) {
                throw new IllegalArgumentException("Ответ не принадлежит выбранному отзыву");
            }
            redirectAttributes.addFlashAttribute("filterOptionSuccess", "Ответ администратора обновлён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("filterOptionError", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit?tab=reviews";
    }
}

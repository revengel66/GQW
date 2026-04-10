package com.example.gqw.shop.web;

import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.shop.dto.CheckoutRequest;
import com.example.gqw.shop.dto.RegisterRequest;
import com.example.gqw.shop.dto.SupportRequestForm;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.OrderItem;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.OrderStatusHistory;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.entity.WishlistItem;
import com.example.gqw.shop.service.CartService;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import com.example.gqw.shop.service.UserService;
import com.example.gqw.shop.service.WishlistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

@Controller
public class ShopController {

    private static final String GUEST_REVIEW_NAME_COOKIE = "gqw_guest_review_name";
    private static final String GUEST_REVIEW_EMAIL_COOKIE = "gqw_guest_review_email";
    private static final int GUEST_REVIEW_COOKIE_MAX_AGE = 60 * 60 * 24 * 3;

    private record ReviewRatingBucketVm(int score, long count, int percent) {
    }

    private final CatalogService catalogService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final SupportService supportService;
    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final AnalyticsTrackingApi analyticsTrackingApi;

    public ShopController(
        CatalogService catalogService,
        CartService cartService,
        WishlistService wishlistService,
        OrderService orderService,
        ReviewService reviewService,
        SupportService supportService,
        UserService userService,
        CurrentUserService currentUserService,
        AnalyticsTrackingApi analyticsTrackingApi
    ) {
        this.catalogService = catalogService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.supportService = supportService;
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.analyticsTrackingApi = analyticsTrackingApi;
    }

    @ModelAttribute("currentUser")
    public ShopUser currentUser(HttpServletRequest request) {
        syncSessionCollectionsIfAuthorized(request);
        return currentUserService.findCurrentUser().orElse(null);
    }

    @ModelAttribute("supportForm")
    public SupportRequestForm supportForm() {
        return new SupportRequestForm("", "", "", "");
    }

    @ModelAttribute("cartCount")
    public int cartCount(HttpServletRequest request) {
        syncSessionCollectionsIfAuthorized(request);
        return countCartItems(request.getSession().getId());
    }

    @ModelAttribute("cartItemQuantities")
    public Map<Long, Integer> cartItemQuantities(HttpServletRequest request) {
        syncSessionCollectionsIfAuthorized(request);
        return cartService.items(request.getSession().getId()).stream()
            .collect(Collectors.toMap(
                it -> it.getProduct().getId(),
                CartItem::getQuantity,
                Integer::sum,
                LinkedHashMap::new
            ));
    }

    @ModelAttribute("wishlistCount")
    public int wishlistCount(HttpServletRequest request) {
        syncSessionCollectionsIfAuthorized(request);
        return wishlistService.count(request.getSession().getId());
    }

    @ModelAttribute("wishlistProductIds")
    public Set<Long> wishlistProductIds(HttpServletRequest request) {
        syncSessionCollectionsIfAuthorized(request);
        return wishlistService.items(request.getSession().getId()).stream()
            .map(item -> item.getProduct().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @ModelAttribute("topCategories")
    public List<Category> topCategories() {
        return catalogService.topCategories();
    }

    @ModelAttribute("subcategoriesByParentId")
    public Map<Long, List<Category>> subcategoriesByParentId() {
        return catalogService.subcategoriesByParentId(catalogService.topCategories());
    }

    @ModelAttribute("guestReviewerName")
    public String guestReviewerName(HttpServletRequest request) {
        return readCookieValue(request, GUEST_REVIEW_NAME_COOKIE);
    }

    @ModelAttribute("guestReviewerEmail")
    public String guestReviewerEmail(HttpServletRequest request) {
        return readCookieValue(request, GUEST_REVIEW_EMAIL_COOKIE);
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featuredCategories", catalogService.featuredTopCategories(4));
        List<Product> products = catalogService.latestProducts();
        model.addAttribute("products", products);
        model.addAttribute("productCardFeatures", catalogService.cardCharacteristics(products, 3));
        return "index";
    }

    @GetMapping("/catalog")
    public String catalog(Model model) {
        model.addAttribute("catalogCategories", catalogService.topCategories());
        return "catalog";
    }

    @GetMapping("/category/{slug}")
    public String category(
        @PathVariable String slug,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "32") int size,
        @RequestParam(defaultValue = "date_desc") String sort,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) List<Long> optionIds,
        @RequestParam(required = false) Boolean inStockOnly,
        Model model
    ) {
        int resolvedSize = Math.min(32, Math.max(1, size));
        boolean onlyInStock = inStockOnly == null || inStockOnly;
        Category category = catalogService.categoryBySlug(slug);
        var categoryData = catalogService.categoryCatalogData(slug, page, resolvedSize, sort, q, minPrice, maxPrice, optionIds, onlyInStock);
        var priceBounds = catalogService.categoryPriceBounds(slug, q, optionIds, onlyInStock);
        var pageData = categoryData.pageData();
        model.addAttribute("category", category);
        model.addAttribute("pageData", pageData);
        model.addAttribute("filterFacets", categoryData.facets());
        model.addAttribute("productCardFeatures", catalogService.cardCharacteristics(pageData.getContent(), 3));
        model.addAttribute("categoryTree", catalogService.categoryTree());
        model.addAttribute("sort", sort);
        model.addAttribute("q", q);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        BigDecimal priceRangeMin = priceBounds.min() != null ? priceBounds.min() : BigDecimal.ZERO;
        BigDecimal priceRangeMax = priceBounds.max() != null ? priceBounds.max() : priceRangeMin;
        if (priceRangeMax.compareTo(priceRangeMin) < 0) {
            priceRangeMax = priceRangeMin;
        }
        BigDecimal priceSelectedMin = minPrice != null ? minPrice : priceRangeMin;
        BigDecimal priceSelectedMax = maxPrice != null ? maxPrice : priceRangeMax;
        if (priceSelectedMin.compareTo(priceRangeMin) < 0) {
            priceSelectedMin = priceRangeMin;
        }
        if (priceSelectedMax.compareTo(priceRangeMax) > 0) {
            priceSelectedMax = priceRangeMax;
        }
        if (priceSelectedMin.compareTo(priceSelectedMax) > 0) {
            priceSelectedMin = priceRangeMin;
            priceSelectedMax = priceRangeMax;
        }
        model.addAttribute("priceRangeMin", priceRangeMin.stripTrailingZeros().toPlainString());
        model.addAttribute("priceRangeMax", priceRangeMax.stripTrailingZeros().toPlainString());
        model.addAttribute("priceSelectedMin", priceSelectedMin.stripTrailingZeros().toPlainString());
        model.addAttribute("priceSelectedMax", priceSelectedMax.stripTrailingZeros().toPlainString());
        List<Long> resolvedSelectedOptionIds = categoryData.facets().stream()
            .flatMap(facet -> facet.options().stream())
            .filter(option -> option.selected() && option.id() != null)
            .map(option -> option.id())
            .toList();
        model.addAttribute("selectedOptionIds", resolvedSelectedOptionIds);
        model.addAttribute("inStockOnly", onlyInStock);
        return "category";
    }

    @GetMapping("/product/{slug}")
    public String product(
        @PathVariable String slug,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) Boolean openReview,
        Model model,
        HttpServletRequest request
    ) {
        UUID eventUid = startEvent("VIEW_PRODUCT", request);
        Long stageController = analyticsTrackingApi.startStage(eventUid, "CONTROLLER", 1);
        try {
            Long stageService = analyticsTrackingApi.startStage(eventUid, "SERVICE", 2);
            Product product = catalogService.productBySlug(slug);
            analyticsTrackingApi.addAttribute(eventUid, "PRODUCT_ID", String.valueOf(product.getId()));
            analyticsTrackingApi.finishStageSuccess(stageService);

            Long stageDb = analyticsTrackingApi.startStage(eventUid, "DATABASE", 3);
            analyticsTrackingApi.recordMetricNum(stageDb, "DB_QUERY_COUNT", new BigDecimal("4"), "count");
            analyticsTrackingApi.recordMetricNum(stageDb, "RESPONSE_SIZE_BYTES", new BigDecimal("12000"), "bytes");
            analyticsTrackingApi.finishStageSuccess(stageDb);

            model.addAttribute("product", product);
            model.addAttribute("productDescriptionHtml", toDescriptionHtml(product.getDescription()));
            model.addAttribute("characteristics", catalogService.characteristics(product));
            model.addAttribute("filterOptions", catalogService.filterOptions(product));
            List<Review> reviews = catalogService.approvedReviews(product);
            Map<Long, List<Review>> repliesByReviewId = new LinkedHashMap<>();
            for (Review review : reviews) {
                repliesByReviewId.put(review.getId(), reviewService.replies(review));
            }
            List<ReviewRatingBucketVm> reviewRatingBuckets = buildReviewRatingBuckets(reviews);
            int reviewRatingCount = totalRatedReviews(reviewRatingBuckets);
            double reviewRatingAvg = averageReviewRating(reviewRatingBuckets, reviewRatingCount);
            model.addAttribute("reviews", reviews);
            model.addAttribute("repliesByReviewId", repliesByReviewId);
            model.addAttribute("reviewRatingBuckets", reviewRatingBuckets);
            model.addAttribute("reviewRatingCount", reviewRatingCount);
            model.addAttribute("reviewRatingWord", reviewWord(reviewRatingCount));
            model.addAttribute("reviewRatingAvg", reviewRatingAvg);
            List<Product> relatedProducts = catalogService.relatedProducts(product, 8);
            model.addAttribute("relatedProducts", relatedProducts);
            model.addAttribute("relatedProductCardFeatures", catalogService.cardCharacteristics(relatedProducts, 3));
            model.addAttribute("productInitialTab", tab);
            model.addAttribute("openReviewModal", openReview != null && openReview);

            analyticsTrackingApi.finishStageSuccess(stageController);
            analyticsTrackingApi.finishEventSuccess(eventUid, 200);
            return "product";
        } catch (RuntimeException ex) {
            analyticsTrackingApi.finishStageError(stageController, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageController, "ERROR_CODE", "PRODUCT_VIEW_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 500, ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/cart")
    public String cart(Model model, HttpServletRequest request) {
        List<CartItem> items = cartService.items(request.getSession().getId());
        model.addAttribute("items", items);
        model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
        return "cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(
        @RequestParam Long productId,
        @RequestParam(defaultValue = "1") int quantity,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            processAddToCart(productId, quantity, request);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/api/cart/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCartApi(
        @RequestParam Long productId,
        @RequestParam(defaultValue = "1") int quantity,
        HttpServletRequest request
    ) {
        try {
            processAddToCart(productId, quantity, request);
            return ResponseEntity.ok(buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/api/cart/increment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> incrementCartItemApi(
        @RequestParam Long productId,
        HttpServletRequest request
    ) {
        try {
            cartService.incrementProduct(productId, request.getSession().getId());
            return ResponseEntity.ok(buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/api/cart/decrement")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> decrementCartItemApi(
        @RequestParam Long productId,
        HttpServletRequest request
    ) {
        try {
            cartService.decrementProduct(productId, request.getSession().getId());
            return ResponseEntity.ok(buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/api/cart/toggle-one")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleCartItemApi(
        @RequestParam Long productId,
        @RequestParam(required = false) Integer expectedQuantity,
        HttpServletRequest request
    ) {
        try {
            String sessionId = request.getSession().getId();
            cartService.toggleOne(productId, sessionId, expectedQuantity);
            return ResponseEntity.ok(buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @GetMapping("/api/cart/count")
    @ResponseBody
    public Map<String, Integer> cartCountApi(HttpServletRequest request) {
        return Map.of("count", countCartItems(request.getSession().getId()));
    }

    private void processAddToCart(Long productId, int quantity, HttpServletRequest request) {
        UUID eventUid = startEvent("ADD_TO_CART", request);
        Long stageService = analyticsTrackingApi.startStage(eventUid, "SERVICE", 1);
        try {
            analyticsTrackingApi.addAttribute(eventUid, "PRODUCT_ID", String.valueOf(productId));
            cartService.addProduct(productId, quantity, request.getSession().getId());
            analyticsTrackingApi.finishStageSuccess(stageService);
            analyticsTrackingApi.finishEventSuccess(eventUid, 200);
        } catch (RuntimeException ex) {
            analyticsTrackingApi.finishStageError(stageService, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageService, "ERROR_CODE", "ADD_TO_CART_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 500, ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long itemId, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            cartService.removeItem(itemId, request.getSession().getId());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(
        @RequestParam Long itemId,
        @RequestParam int quantity,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            cartService.updateQuantity(itemId, quantity, request.getSession().getId());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @GetMapping("/wishlist")
    public String wishlist(Model model, HttpServletRequest request) {
        List<WishlistItem> items = wishlistService.items(request.getSession().getId());
        List<Product> products = items.stream()
            .map(WishlistItem::getProduct)
            .toList();
        model.addAttribute("items", items);
        model.addAttribute("wishlistProducts", products);
        model.addAttribute("productCardFeatures", catalogService.cardCharacteristics(products, 3));
        return "wishlist";
    }

    @PostMapping("/api/wishlist/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleWishlistApi(
        @RequestParam Long productId,
        HttpServletRequest request
    ) {
        try {
            String sessionId = request.getSession().getId();
            boolean inWishlist = wishlistService.toggle(productId, sessionId);
            int count = wishlistService.count(sessionId);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "productId", productId,
                "inWishlist", inWishlist,
                "count", count
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/wishlist/add")
    public String addToWishlist(@RequestParam Long productId, HttpServletRequest request) {
        UUID eventUid = startEvent("ADD_TO_WISHLIST", request);
        Long stageService = analyticsTrackingApi.startStage(eventUid, "SERVICE", 1);
        try {
            analyticsTrackingApi.addAttribute(eventUid, "PRODUCT_ID", String.valueOf(productId));
            wishlistService.add(productId, request.getSession().getId());
            analyticsTrackingApi.finishStageSuccess(stageService);
            analyticsTrackingApi.finishEventSuccess(eventUid, 200);
            return "redirect:/wishlist";
        } catch (RuntimeException ex) {
            analyticsTrackingApi.finishStageError(stageService, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageService, "ERROR_CODE", "ADD_TO_WISHLIST_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 500, ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/wishlist/remove")
    public String removeFromWishlist(@RequestParam Long itemId, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            wishlistService.remove(itemId, request.getSession().getId());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("wishlistError", ex.getMessage());
        }
        return "redirect:/wishlist";
    }

    @GetMapping("/checkout")
    public String checkout(Model model, HttpServletRequest request) {
        List<CartItem> items = cartService.items(request.getSession().getId());
        if (items.isEmpty()) {
            return "redirect:/cart";
        }
        ShopUser currentUser = currentUserService.findCurrentUser().orElse(null);
        CheckoutRequest checkoutRequest = new CheckoutRequest(
            currentUser != null && currentUser.getFullName() != null ? currentUser.getFullName() : "",
            currentUser != null && currentUser.getEmail() != null ? currentUser.getEmail() : "",
            currentUser != null && currentUser.getPhone() != null ? currentUser.getPhone() : "",
            "PICKUP",
            currentUser != null ? firstNonBlank(currentUser.getAddressStreet(), currentUser.getAddress()) : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressHouse(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressApartment(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressEntrance(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressFloor(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressIntercom(), "") : "",
            null,
            null,
            null
        );
        model.addAttribute("checkoutRequest", checkoutRequest);
        model.addAttribute("items", items);
        model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
        return "checkout";
    }

    @PostMapping("/checkout")
    public String checkoutSubmit(
        @Valid @ModelAttribute("checkoutRequest") CheckoutRequest checkoutRequest,
        BindingResult bindingResult,
        HttpServletRequest request,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("items", cartService.items(request.getSession().getId()));
            model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
            return "checkout";
        }

        UUID eventUid = startEvent("CHECKOUT_SUBMIT", request);
        Long stageService = analyticsTrackingApi.startStage(eventUid, "SERVICE", 1);
        try {
            ShopOrder order = orderService.checkout(checkoutRequest, request.getSession().getId());
            analyticsTrackingApi.addAttribute(eventUid, "ORDER_ID", String.valueOf(order.getId()));
            analyticsTrackingApi.finishStageSuccess(stageService);
            analyticsTrackingApi.finishEventSuccess(eventUid, 200);
            return "redirect:/account?checkoutSuccess=true";
        } catch (IllegalStateException | IllegalArgumentException ex) {
            analyticsTrackingApi.finishStageError(stageService, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageService, "ERROR_CODE", "CHECKOUT_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 400, ex.getMessage());
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
            return "redirect:/cart";
        } catch (RuntimeException ex) {
            analyticsTrackingApi.finishStageError(stageService, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageService, "ERROR_CODE", "CHECKOUT_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 500, ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/account")
    public String account(
        @RequestParam(required = false) Boolean checkoutSuccess,
        @RequestParam(required = false) Boolean profileUpdated,
        @RequestParam(required = false) String profileError,
        @RequestParam(required = false) String orderSuccess,
        @RequestParam(required = false) String orderError,
        @RequestParam(required = false) String supportSuccess,
        @RequestParam(required = false) String supportError,
        @RequestParam(required = false, defaultValue = "profile") String tab,
        @RequestParam(required = false) Long orderForSupport,
        Model model
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        List<ShopOrder> orders = orderService.userOrders();
        String activeAccountTab = normalizeAccountTab(tab);
        Map<Long, List<OrderItem>> orderItemsByOrderId = new LinkedHashMap<>();
        Map<Long, Integer> orderQuantityByOrderId = new LinkedHashMap<>();
        Map<Long, List<OrderStatusHistory>> orderHistoryByOrderId = new LinkedHashMap<>();
        Map<Long, Boolean> editableOrderById = new LinkedHashMap<>();
        Map<Long, Boolean> cancelableOrderById = new LinkedHashMap<>();
        for (ShopOrder order : orders) {
            List<OrderItem> orderItems = orderService.itemsForOrder(order);
            orderItemsByOrderId.put(order.getId(), orderItems);
            int totalQuantity = orderItems.stream()
                .map(OrderItem::getQuantity)
                .filter(qty -> qty != null)
                .mapToInt(Integer::intValue)
                .sum();
            orderQuantityByOrderId.put(order.getId(), totalQuantity);
            orderHistoryByOrderId.put(order.getId(), orderService.statusHistoryForOrder(order));
            editableOrderById.put(order.getId(), orderService.canEditByCustomer(order));
            cancelableOrderById.put(order.getId(), orderService.canCancelByCustomer(order));
        }
        BigDecimal totalSpent = orders.stream()
            .map(ShopOrder::getTotalAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SupportRequest> supportRequests = supportService.userRequests();
        List<Review> accountReviews = reviewService.currentUserTopLevelReviews();
        List<Review> reviewReplies = reviewService.repliesToReviews(accountReviews);
        Map<Long, List<Review>> reviewRepliesByParentId = new LinkedHashMap<>();
        for (Review reply : reviewReplies) {
            if (reply.getParent() == null || reply.getParent().getId() == null) {
                continue;
            }
            reviewRepliesByParentId.computeIfAbsent(reply.getParent().getId(), ignored -> new ArrayList<>()).add(reply);
        }
        Instant reviewRepliesSeenAt = currentUser.getReviewRepliesSeenAt();
        long newRepliesCount = reviewReplies.stream()
            .filter(reply -> reply.getCreatedAt() != null && (reviewRepliesSeenAt == null || reply.getCreatedAt().isAfter(reviewRepliesSeenAt)))
            .count();
        if ("reviews".equals(activeAccountTab) && newRepliesCount > 0) {
            Instant now = Instant.now();
            userService.markReviewRepliesSeen(currentUser, now);
            currentUser.setReviewRepliesSeenAt(now);
            newRepliesCount = 0;
        }
        Set<Long> reviewedProductIds = accountReviews.stream()
            .filter(review -> review.getProduct() != null && review.getProduct().getId() != null)
            .map(review -> review.getProduct().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        model.addAttribute("orders", orders);
        model.addAttribute("orderItemsByOrderId", orderItemsByOrderId);
        model.addAttribute("orderQuantityByOrderId", orderQuantityByOrderId);
        model.addAttribute("orderHistoryByOrderId", orderHistoryByOrderId);
        model.addAttribute("editableOrderById", editableOrderById);
        model.addAttribute("cancelableOrderById", cancelableOrderById);
        model.addAttribute("orderStatusRuMap", orderStatusRuMap());
        model.addAttribute("ordersCount", orders.size());
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("lastOrder", orders.isEmpty() ? null : orders.get(0));
        model.addAttribute("supportRequests", supportRequests);
        model.addAttribute("supportRequestsCount", supportRequests.size());
        model.addAttribute("accountReviews", accountReviews);
        model.addAttribute("reviewReplies", reviewReplies);
        model.addAttribute("reviewRepliesByParentId", reviewRepliesByParentId);
        model.addAttribute("reviewedProductIds", reviewedProductIds);
        model.addAttribute("newReviewRepliesCount", newRepliesCount);
        model.addAttribute("activeAccountTab", activeAccountTab);
        model.addAttribute("preselectedSupportOrderId", orderForSupport);
        model.addAttribute("checkoutSuccess", checkoutSuccess != null && checkoutSuccess);
        model.addAttribute("profileUpdated", profileUpdated != null && profileUpdated);
        model.addAttribute("profileError", profileError);
        model.addAttribute("orderSuccess", orderSuccess);
        model.addAttribute("orderError", orderError);
        model.addAttribute("supportSuccess", supportSuccess);
        model.addAttribute("supportError", supportError);
        return "account";
    }

    @PostMapping("/account/profile")
    public String updateProfile(
        @RequestParam String fullName,
        @RequestParam String phone,
        @RequestParam String email,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        try {
            userService.updateContacts(currentUser, fullName, phone, email);
            redirectAttributes.addAttribute("profileUpdated", true);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("profileError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "profile");
        return "redirect:/account";
    }

    @PostMapping("/account/address")
    public String updateAddress(
        @RequestParam(required = false) String addressStreet,
        @RequestParam(required = false) String addressHouse,
        @RequestParam(required = false) String addressApartment,
        @RequestParam(required = false) String addressEntrance,
        @RequestParam(required = false) String addressFloor,
        @RequestParam(required = false) String addressIntercom,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        try {
            userService.updateAddress(
                currentUser,
                addressStreet,
                addressHouse,
                addressApartment,
                addressEntrance,
                addressFloor,
                addressIntercom
            );
            redirectAttributes.addAttribute("profileUpdated", true);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("profileError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "address");
        return "redirect:/account";
    }

    @PostMapping("/account/delete")
    public String deleteAccount(
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        ShopUser currentUser = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        if (Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            return "redirect:/admin";
        }
        try {
            userService.deleteAccount(currentUser);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("profileError", ex.getMessage());
            redirectAttributes.addAttribute("tab", "profile");
            return "redirect:/account";
        }
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?accountDeleted=true";
    }

    @PostMapping("/account/orders/{orderId}/cancel")
    public String cancelOrderFromAccount(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelForCurrentUser(orderId);
            redirectAttributes.addAttribute("orderSuccess", "Заказ отменён");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("orderError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "orders");
        return "redirect:/account";
    }

    @PostMapping("/account/orders/{orderId}/update")
    public String updateOrderFromAccount(
        @PathVariable Long orderId,
        @RequestParam String customerName,
        @RequestParam String customerEmail,
        @RequestParam(required = false) String customerPhone,
        @RequestParam(required = false, defaultValue = "PICKUP") String deliveryType,
        @RequestParam(required = false) LocalDate pickupDate,
        @RequestParam(required = false) LocalDate deliveryDate,
        @RequestParam(required = false) LocalTime deliveryTime,
        @RequestParam(required = false) String deliveryStreet,
        @RequestParam(required = false) String deliveryHouse,
        @RequestParam(required = false) String deliveryApartment,
        @RequestParam(required = false) String deliveryEntrance,
        @RequestParam(required = false) String deliveryFloor,
        @RequestParam(required = false) String deliveryIntercom,
        @RequestParam(required = false) List<Long> itemId,
        @RequestParam(required = false) List<Integer> itemQuantity,
        RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.updateByCurrentUser(
                orderId,
                customerName,
                customerEmail,
                customerPhone,
                deliveryType,
                pickupDate,
                deliveryDate,
                deliveryTime,
                deliveryStreet,
                deliveryHouse,
                deliveryApartment,
                deliveryEntrance,
                deliveryFloor,
                deliveryIntercom,
                itemId,
                itemQuantity
            );
            redirectAttributes.addAttribute("orderSuccess", "Заказ обновлён");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addAttribute("orderError", ex.getMessage());
        }
        redirectAttributes.addAttribute("tab", "orders");
        return "redirect:/account";
    }

    @PostMapping("/account/support/create")
    public String createSupportTicketFromAccount(
        @RequestParam(required = false) Long orderId,
        @RequestParam String message,
        RedirectAttributes redirectAttributes
    ) {
        try {
            supportService.createForAccount(orderId, message);
            redirectAttributes.addAttribute("supportSuccess", "Тикет создан. Мы ответим в ближайшее время.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addAttribute("supportError", ex.getMessage());
            if (orderId != null) {
                redirectAttributes.addAttribute("orderForSupport", orderId);
            }
        }
        redirectAttributes.addAttribute("tab", "support");
        return "redirect:/account";
    }

    private static String normalizeAccountTab(String tab) {
        if (tab == null) {
            return "profile";
        }
        String normalized = tab.trim().toLowerCase();
        return switch (normalized) {
            case "profile", "address", "orders", "support", "reviews" -> normalized;
            default -> "profile";
        };
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

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest("", "", "", "", ""));
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(
        @Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.register(registerRequest);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("register_error", ex.getMessage());
            return "register";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Регистрация выполнена. Войдите в систему.");
        return "redirect:/login";
    }

    @PostMapping("/support/request")
    public String support(
        @Valid @ModelAttribute("supportForm") SupportRequestForm supportForm,
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            supportService.create(supportForm);
            redirectAttributes.addFlashAttribute("supportSuccess", "Заявка отправлена. Мы свяжемся с вами.");
        } else {
            redirectAttributes.addFlashAttribute("supportError", "Проверьте корректность введенных данных.");
        }
        return "redirect:/";
    }

    @PostMapping("/review/add")
    public String addReview(
        @RequestParam Long productId,
        @RequestParam Integer rating,
        @RequestParam String text,
        @RequestParam(required = false) String pros,
        @RequestParam(required = false) String cons,
        @RequestParam(required = false) String usagePeriod,
        @RequestParam(required = false) String guestName,
        @RequestParam(required = false) String guestEmail,
        @RequestParam(required = false) List<MultipartFile> images,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        String productSlug = findProductSlug(productId);
        try {
            reviewService.addReview(productId, rating, text, pros, cons, usagePeriod, guestName, guestEmail, images);
            if (currentUserService.findCurrentUser().isEmpty()) {
                persistGuestReviewerCookies(response, guestName, guestEmail, request.isSecure());
            }
            redirectAttributes.addFlashAttribute("reviewSuccess", "Отзыв отправлен на модерацию");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/product/" + productSlug;
    }

    @PostMapping("/review/reply")
    public String replyReview(
        @RequestParam Long reviewId,
        @RequestParam String text,
        @RequestParam(required = false) String guestName,
        @RequestParam(required = false) String guestEmail,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Review reply = reviewService.reply(reviewId, text, guestName, guestEmail);
            if (currentUserService.findCurrentUser().isEmpty()) {
                persistGuestReviewerCookies(response, guestName, guestEmail, request.isSecure());
            }
            redirectAttributes.addFlashAttribute("reviewSuccess", "Ответ отправлен на модерацию");
            return "redirect:/product/" + reply.getProduct().getSlug();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
            return "redirect:/catalog";
        }
    }

    @GetMapping("/contacts")
    public String contacts() {
        return "contacts";
    }

    @GetMapping("/delivery")
    public String delivery() {
        return "delivery";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/reviews")
    public String reviewsPage(Model model) {
        model.addAttribute("reviewsFeed", catalogService.latestApprovedReviews(60));
        return "reviews";
    }

    private String findProductSlug(Long productId) {
        Product product = catalogService.productById(productId);
        return product.getSlug();
    }

    private static List<ReviewRatingBucketVm> buildReviewRatingBuckets(List<Review> reviews) {
        long[] countsByScore = new long[6];
        if (reviews != null) {
            for (Review review : reviews) {
                Integer rating = review.getRating();
                if (rating == null || rating < 1 || rating > 5) {
                    continue;
                }
                countsByScore[rating] = countsByScore[rating] + 1;
            }
        }
        int ratedTotal = 0;
        for (int score = 1; score <= 5; score++) {
            ratedTotal += (int) countsByScore[score];
        }
        List<ReviewRatingBucketVm> buckets = new ArrayList<>(5);
        for (int score = 5; score >= 1; score--) {
            int percent = ratedTotal == 0
                ? 0
                : (int) Math.round((countsByScore[score] * 100.0d) / ratedTotal);
            buckets.add(new ReviewRatingBucketVm(score, countsByScore[score], percent));
        }
        return buckets;
    }

    private static int totalRatedReviews(List<ReviewRatingBucketVm> buckets) {
        int total = 0;
        for (ReviewRatingBucketVm bucket : buckets) {
            total += (int) bucket.count();
        }
        return total;
    }

    private static double averageReviewRating(List<ReviewRatingBucketVm> buckets, int totalReviews) {
        if (totalReviews <= 0) {
            return 0.0d;
        }
        long weightedSum = 0L;
        for (ReviewRatingBucketVm bucket : buckets) {
            weightedSum += (long) bucket.score() * bucket.count();
        }
        return weightedSum / (double) totalReviews;
    }

    private static String reviewWord(int count) {
        int value = Math.abs(count);
        int lastTwo = value % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "отзывов";
        }
        return switch (value % 10) {
            case 1 -> "отзыв";
            case 2, 3, 4 -> "отзыва";
            default -> "отзывов";
        };
    }

    private UUID startEvent(String eventTypeCode, HttpServletRequest request) {
        Long userId = currentUserService.findCurrentUser().map(ShopUser::getId).orElse(null);
        return analyticsTrackingApi.startEvent(
            eventTypeCode,
            userId,
            request.getSession().getId(),
            request.getRequestURI(),
            request.getMethod(),
            UUID.randomUUID().toString().replace("-", "")
        );
    }

    private int countCartItems(String sessionId) {
        return cartService.items(sessionId).stream()
            .mapToInt(CartItem::getQuantity)
            .sum();
    }

    private static void persistGuestReviewerCookies(
        HttpServletResponse response,
        String guestName,
        String guestEmail,
        boolean secure
    ) {
        setCookie(response, GUEST_REVIEW_NAME_COOKIE, guestName, secure);
        setCookie(response, GUEST_REVIEW_EMAIL_COOKIE, guestEmail, secure);
    }

    private static void setCookie(
        HttpServletResponse response,
        String cookieName,
        String value,
        boolean secure
    ) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return;
        }
        Cookie cookie = new Cookie(cookieName, URLEncoder.encode(normalized, StandardCharsets.UTF_8));
        cookie.setHttpOnly(false);
        cookie.setMaxAge(GUEST_REVIEW_COOKIE_MAX_AGE);
        cookie.setPath("/");
        cookie.setSecure(secure);
        response.addCookie(cookie);
    }

    private static String readCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (!cookieName.equals(cookie.getName())) {
                continue;
            }
            String raw = cookie.getValue();
            if (raw == null || raw.isBlank()) {
                return "";
            }
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        }
        return "";
    }

    private Map<String, Object> buildCartPayload(Long productId, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        int productQuantity = cartService.quantityForProduct(productId, sessionId);
        int count = countCartItems(sessionId);
        return Map.of(
            "ok", true,
            "productId", productId,
            "productQuantity", productQuantity,
            "count", count
        );
    }

    private static String toDescriptionHtml(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "<p class=\"text-muted mb-0\">Подробное описание скоро появится.</p>";
        }
        String trimmed = rawDescription.trim();
        boolean containsHtml = trimmed.contains("<") && trimmed.contains(">");
        if (containsHtml) {
            return trimmed;
        }

        String escaped = HtmlUtils.htmlEscape(trimmed);
        String[] paragraphs = escaped.split("\\R{2,}");
        StringBuilder html = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isBlank()) {
                continue;
            }
            html.append("<p>")
                .append(paragraph.replaceAll("\\R", "<br/>"))
                .append("</p>");
        }
        if (html.isEmpty()) {
            return "<p class=\"text-muted mb-0\">Подробное описание скоро появится.</p>";
        }
        return html.toString();
    }

    private void syncSessionCollectionsIfAuthorized(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute("sessionCollectionsSynced"))) {
            return;
        }
        if (currentUserService.findCurrentUser().isEmpty()) {
            request.setAttribute("sessionCollectionsSynced", Boolean.TRUE);
            return;
        }
        String sessionId = request.getSession().getId();
        cartService.mergeSessionCartToUser(sessionId);
        wishlistService.mergeSessionWishlistToUser(sessionId);
        request.setAttribute("sessionCollectionsSynced", Boolean.TRUE);
    }
}

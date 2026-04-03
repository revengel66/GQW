package com.example.gqw.shop.web;

import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.shop.dto.CheckoutRequest;
import com.example.gqw.shop.dto.RegisterRequest;
import com.example.gqw.shop.dto.SupportRequestForm;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.WishlistItem;
import com.example.gqw.shop.service.CartService;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.OrderService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import com.example.gqw.shop.service.UserService;
import com.example.gqw.shop.service.WishlistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ShopController {

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
    public ShopUser currentUser() {
        return currentUserService.findCurrentUser().orElse(null);
    }

    @ModelAttribute("supportForm")
    public SupportRequestForm supportForm() {
        return new SupportRequestForm("", "", "", "");
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("categories", catalogService.categories());
        model.addAttribute("products", catalogService.latestProducts());
        return "index";
    }

    @GetMapping("/catalog")
    public String catalog(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(defaultValue = "date_desc") String sort,
        Model model
    ) {
        model.addAttribute("pageData", catalogService.catalog(page, size, sort));
        model.addAttribute("sort", sort);
        model.addAttribute("categories", catalogService.categories());
        return "catalog";
    }

    @GetMapping("/category/{slug}")
    public String category(
        @PathVariable String slug,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(defaultValue = "date_desc") String sort,
        Model model
    ) {
        Category category = catalogService.categoryBySlug(slug);
        model.addAttribute("category", category);
        model.addAttribute("pageData", catalogService.productsByCategory(slug, page, size, sort));
        model.addAttribute("sort", sort);
        model.addAttribute("categories", catalogService.categories());
        return "category";
    }

    @GetMapping("/product/{slug}")
    public String product(@PathVariable String slug, Model model, HttpServletRequest request) {
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
            model.addAttribute("characteristics", catalogService.characteristics(product));
            model.addAttribute("filterOptions", catalogService.filterOptions(product));
            model.addAttribute("reviews", catalogService.approvedReviews(product));
            model.addAttribute("categories", catalogService.categories());

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
        HttpServletRequest request
    ) {
        UUID eventUid = startEvent("ADD_TO_CART", request);
        Long stageService = analyticsTrackingApi.startStage(eventUid, "SERVICE", 1);
        try {
            analyticsTrackingApi.addAttribute(eventUid, "PRODUCT_ID", String.valueOf(productId));
            cartService.addProduct(productId, quantity, request.getSession().getId());
            analyticsTrackingApi.finishStageSuccess(stageService);
            analyticsTrackingApi.finishEventSuccess(eventUid, 200);
            return "redirect:/cart";
        } catch (RuntimeException ex) {
            analyticsTrackingApi.finishStageError(stageService, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageService, "ERROR_CODE", "ADD_TO_CART_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 500, ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long itemId) {
        cartService.removeItem(itemId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long itemId, @RequestParam int quantity) {
        cartService.updateQuantity(itemId, quantity);
        return "redirect:/cart";
    }

    @GetMapping("/wishlist")
    public String wishlist(Model model) {
        List<WishlistItem> items = wishlistService.items();
        model.addAttribute("items", items);
        return "wishlist";
    }

    @PostMapping("/wishlist/add")
    public String addToWishlist(@RequestParam Long productId, HttpServletRequest request) {
        UUID eventUid = startEvent("ADD_TO_WISHLIST", request);
        Long stageService = analyticsTrackingApi.startStage(eventUid, "SERVICE", 1);
        try {
            analyticsTrackingApi.addAttribute(eventUid, "PRODUCT_ID", String.valueOf(productId));
            wishlistService.add(productId);
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
    public String removeFromWishlist(@RequestParam Long itemId) {
        wishlistService.remove(itemId);
        return "redirect:/wishlist";
    }

    @GetMapping("/checkout")
    public String checkout(Model model, HttpServletRequest request) {
        model.addAttribute("checkoutRequest", new CheckoutRequest("", "", ""));
        model.addAttribute("items", cartService.items(request.getSession().getId()));
        model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
        return "checkout";
    }

    @PostMapping("/checkout")
    public String checkoutSubmit(
        @Valid @ModelAttribute("checkoutRequest") CheckoutRequest checkoutRequest,
        BindingResult bindingResult,
        HttpServletRequest request,
        Model model
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
        } catch (RuntimeException ex) {
            analyticsTrackingApi.finishStageError(stageService, ex.getMessage());
            analyticsTrackingApi.recordMetricText(stageService, "ERROR_CODE", "CHECKOUT_FAIL", null);
            analyticsTrackingApi.finishEventError(eventUid, 500, ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/account")
    public String account(@RequestParam(required = false) Boolean checkoutSuccess, Model model) {
        model.addAttribute("orders", orderService.userOrders());
        model.addAttribute("checkoutSuccess", checkoutSuccess != null && checkoutSuccess);
        return "account";
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
        userService.register(registerRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Регистрация выполнена. Войдите в систему.");
        return "redirect:/login";
    }

    @PostMapping("/support/request")
    public String support(@Valid @ModelAttribute("supportForm") SupportRequestForm supportForm, BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            supportService.create(supportForm);
        }
        return "redirect:/";
    }

    @PostMapping("/review/add")
    public String addReview(@RequestParam Long productId, @RequestParam Integer rating, @RequestParam String text) {
        reviewService.addReview(productId, rating, text);
        return "redirect:/product/" + findProductSlug(productId);
    }

    @PostMapping("/review/reply")
    public String replyReview(@RequestParam Long reviewId, @RequestParam String text) {
        Review reply = reviewService.reply(reviewId, text);
        return "redirect:/product/" + reply.getProduct().getSlug();
    }

    private String findProductSlug(Long productId) {
        Product product = catalogService.productById(productId);
        return product.getSlug();
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
}

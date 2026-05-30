package com.example.gqw.shop.controller;

import com.example.gqw.shop.dto.SupportRequestForm;
import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.WishlistService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackageClasses = {
    CatalogController.class,
    CartController.class,
    WishlistController.class,
    CheckoutController.class,
    AccountController.class,
    AuthController.class,
    SupportController.class,
    ReviewController.class
})
public class ShopModelAttributesAdvice {

    private final CatalogService catalogService;
    private final CurrentUserService currentUserService;
    private final WishlistService wishlistService;
    private final ShopWebSupport shopWebSupport;

    public ShopModelAttributesAdvice(
        CatalogService catalogService,
        CurrentUserService currentUserService,
        WishlistService wishlistService,
        ShopWebSupport shopWebSupport
    ) {
        this.catalogService = catalogService;
        this.currentUserService = currentUserService;
        this.wishlistService = wishlistService;
        this.shopWebSupport = shopWebSupport;
    }

    @ModelAttribute("currentUser")
    public ShopUser currentUser(HttpServletRequest request) {
        shopWebSupport.syncSessionCollectionsIfAuthorized(request);
        return currentUserService.findCurrentUser().orElse(null);
    }

    @ModelAttribute("supportForm")
    public SupportRequestForm supportForm() {
        return new SupportRequestForm("", "", "", "");
    }

    @ModelAttribute("cartCount")
    public int cartCount(HttpServletRequest request) {
        shopWebSupport.syncSessionCollectionsIfAuthorized(request);
        return shopWebSupport.countCartItems(request.getSession().getId());
    }

    @ModelAttribute("cartItemQuantities")
    public Map<Long, Integer> cartItemQuantities(HttpServletRequest request) {
        shopWebSupport.syncSessionCollectionsIfAuthorized(request);
        return shopWebSupport.cartItemQuantities(request.getSession().getId());
    }

    @ModelAttribute("wishlistCount")
    public int wishlistCount(HttpServletRequest request) {
        shopWebSupport.syncSessionCollectionsIfAuthorized(request);
        return wishlistService.count(request.getSession().getId());
    }

    @ModelAttribute("wishlistProductIds")
    public Set<Long> wishlistProductIds(HttpServletRequest request) {
        shopWebSupport.syncSessionCollectionsIfAuthorized(request);
        return shopWebSupport.wishlistProductIds(request.getSession().getId());
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
        return shopWebSupport.guestReviewerName(request);
    }

    @ModelAttribute("guestReviewerEmail")
    public String guestReviewerEmail(HttpServletRequest request) {
        return shopWebSupport.guestReviewerEmail(request);
    }
}

package com.example.gqw.shop.controller;

import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.service.CartService;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.WishlistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ShopWebSupport {

    private static final String GUEST_REVIEW_NAME_COOKIE = "gqw_guest_review_name";
    private static final String GUEST_REVIEW_EMAIL_COOKIE = "gqw_guest_review_email";
    private static final int GUEST_REVIEW_COOKIE_MAX_AGE = 60 * 60 * 24 * 3;

    private final CartService cartService;
    private final WishlistService wishlistService;
    private final CurrentUserService currentUserService;

    public ShopWebSupport(
        CartService cartService,
        WishlistService wishlistService,
        CurrentUserService currentUserService
    ) {
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.currentUserService = currentUserService;
    }

    public void syncSessionCollectionsIfAuthorized(HttpServletRequest request) {
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

    public int countCartItems(String sessionId) {
        return cartService.items(sessionId).stream()
            .mapToInt(CartItem::getQuantity)
            .sum();
    }

    public Map<Long, Integer> cartItemQuantities(String sessionId) {
        return cartService.items(sessionId).stream()
            .collect(Collectors.toMap(
                it -> it.getProduct().getId(),
                CartItem::getQuantity,
                Integer::sum,
                LinkedHashMap::new
            ));
    }

    public Set<Long> wishlistProductIds(String sessionId) {
        return wishlistService.items(sessionId).stream()
            .map(item -> item.getProduct().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Map<String, Object> buildCartPayload(Long productId, HttpServletRequest request) {
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

    public String guestReviewerName(HttpServletRequest request) {
        return readCookieValue(request, GUEST_REVIEW_NAME_COOKIE);
    }

    public String guestReviewerEmail(HttpServletRequest request) {
        return readCookieValue(request, GUEST_REVIEW_EMAIL_COOKIE);
    }

    public void persistGuestReviewerCookies(
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
}

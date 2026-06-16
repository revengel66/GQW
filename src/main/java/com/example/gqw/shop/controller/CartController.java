package com.example.gqw.shop.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CartController {

    private final CartService cartService;
    private final ShopWebSupport shopWebSupport;

    public CartController(
        CartService cartService,
        ShopWebSupport shopWebSupport
    ) {
        this.cartService = cartService;
        this.shopWebSupport = shopWebSupport;
    }

    @GetMapping("/cart")
    @TrackAnalyticsEvent(code = "CART_VIEW")
    public String cart(Model model, HttpServletRequest request) {
        List<CartItem> items = cartService.items(request.getSession().getId());
        model.addAttribute("items", items);
        model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
        return "shop/cart";
    }

    @PostMapping("/cart/add")
    @TrackAnalyticsEvent(
        code = "ADD_TO_CART",
        entityType = "'PRODUCT'",
        entityId = "#p0",
        attributes = {
            @TrackAnalyticsAttribute(code = "QUANTITY", value = "#p1")
        }
    )
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
    @TrackAnalyticsEvent(
        code = "ADD_TO_CART",
        entityType = "'PRODUCT'",
        entityId = "#p0",
        attributes = {
            @TrackAnalyticsAttribute(code = "QUANTITY", value = "#p1")
        }
    )
    public ResponseEntity<Map<String, Object>> addToCartApi(
        @RequestParam Long productId,
        @RequestParam(defaultValue = "1") int quantity,
        HttpServletRequest request
    ) {
        try {
            processAddToCart(productId, quantity, request);
            return ResponseEntity.ok(shopWebSupport.buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/api/cart/increment")
    @ResponseBody
    @TrackAnalyticsEvent(code = "CART_UPDATE", entityType = "'PRODUCT'", entityId = "#p0")
    public ResponseEntity<Map<String, Object>> incrementCartItemApi(
        @RequestParam Long productId,
        HttpServletRequest request
    ) {
        try {
            cartService.incrementProduct(productId, request.getSession().getId());
            return ResponseEntity.ok(shopWebSupport.buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/api/cart/decrement")
    @ResponseBody
    @TrackAnalyticsEvent(code = "CART_UPDATE", entityType = "'PRODUCT'", entityId = "#p0")
    public ResponseEntity<Map<String, Object>> decrementCartItemApi(
        @RequestParam Long productId,
        HttpServletRequest request
    ) {
        try {
            cartService.decrementProduct(productId, request.getSession().getId());
            return ResponseEntity.ok(shopWebSupport.buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/api/cart/toggle-one")
    @ResponseBody
    @TrackAnalyticsEvent(code = "CART_UPDATE", entityType = "'PRODUCT'", entityId = "#p0")
    public ResponseEntity<Map<String, Object>> toggleCartItemApi(
        @RequestParam Long productId,
        @RequestParam(required = false) Integer expectedQuantity,
        HttpServletRequest request
    ) {
        try {
            String sessionId = request.getSession().getId();
            cartService.toggleOne(productId, sessionId, expectedQuantity);
            return ResponseEntity.ok(shopWebSupport.buildCartPayload(productId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @GetMapping("/api/cart/count")
    @ResponseBody
    public Map<String, Integer> cartCountApi(HttpServletRequest request) {
        return Map.of("count", shopWebSupport.countCartItems(request.getSession().getId()));
    }

    @PostMapping("/cart/remove")
    @TrackAnalyticsEvent(code = "REMOVE_TO_CART", entityType = "'CART_ITEM'", entityId = "#p0")
    public String removeFromCart(@RequestParam Long itemId, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String sessionId = request.getSession().getId();
        try {
            cartService.removeItem(itemId, sessionId);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    @TrackAnalyticsEvent(
        code = "CART_UPDATE",
        entityType = "'CART_ITEM'",
        entityId = "#p0",
        attributes = {
            @TrackAnalyticsAttribute(code = "QUANTITY", value = "#p1")
        }
    )
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

    private void processAddToCart(Long productId, int quantity, HttpServletRequest request) {
        cartService.addProduct(productId, quantity, request.getSession().getId());
    }
}

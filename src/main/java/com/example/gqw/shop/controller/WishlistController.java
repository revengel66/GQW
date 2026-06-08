package com.example.gqw.shop.controller;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.WishlistItem;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.WishlistService;
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
public class WishlistController {

    private final WishlistService wishlistService;
    private final CatalogService catalogService;

    public WishlistController(
        WishlistService wishlistService,
        CatalogService catalogService
    ) {
        this.wishlistService = wishlistService;
        this.catalogService = catalogService;
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
        return "shop/wishlist";
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
        wishlistService.add(productId, request.getSession().getId());
        return "redirect:/wishlist";
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
}

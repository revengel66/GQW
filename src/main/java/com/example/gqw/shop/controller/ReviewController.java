package com.example.gqw.shop.controller;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {

    private final ReviewService reviewService;
    private final CatalogService catalogService;
    private final CurrentUserService currentUserService;
    private final ShopWebSupport shopWebSupport;

    public ReviewController(
        ReviewService reviewService,
        CatalogService catalogService,
        CurrentUserService currentUserService,
        ShopWebSupport shopWebSupport
    ) {
        this.reviewService = reviewService;
        this.catalogService = catalogService;
        this.currentUserService = currentUserService;
        this.shopWebSupport = shopWebSupport;
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
                shopWebSupport.persistGuestReviewerCookies(response, guestName, guestEmail, request.isSecure());
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
                shopWebSupport.persistGuestReviewerCookies(response, guestName, guestEmail, request.isSecure());
            }
            redirectAttributes.addFlashAttribute("reviewSuccess", "Ответ отправлен на модерацию");
            return "redirect:/product/" + reply.getProduct().getSlug();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
            return "redirect:/catalog";
        }
    }

    private String findProductSlug(Long productId) {
        Product product = catalogService.productById(productId);
        return product.getSlug();
    }
}

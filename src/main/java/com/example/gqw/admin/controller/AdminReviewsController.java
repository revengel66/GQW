package com.example.gqw.admin.controller;

import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminReviewsController {

    private final ReviewService reviewService;
    private final AdminControllerSupport controllerSupport;

    public AdminReviewsController(
        ReviewService reviewService,
        AdminControllerSupport controllerSupport
    ) {
        this.reviewService = reviewService;
        this.controllerSupport = controllerSupport;
    }

    @GetMapping("/admin/reviews")
    public String reviews(
        @RequestParam(defaultValue = "PENDING") String status,
        @RequestParam(required = false) Integer rating,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false, defaultValue = "30") Integer limit,
        Model model
    ) {
        LocalDate normalizedFromMutable = dateFrom;
        LocalDate normalizedToMutable = dateTo;
        if (normalizedFromMutable != null && normalizedToMutable != null && normalizedFromMutable.isAfter(normalizedToMutable)) {
            LocalDate tmp = normalizedFromMutable;
            normalizedFromMutable = normalizedToMutable;
            normalizedToMutable = tmp;
        }
        final LocalDate normalizedFrom = normalizedFromMutable;
        final LocalDate normalizedTo = normalizedToMutable;
        ZoneId zone = ZoneId.systemDefault();
        List<Review> filteredReviews = reviewService.reviewsForAdmin(status, rating, null).stream()
            .filter(review -> {
                if (review.getCreatedAt() == null) {
                    return normalizedFrom == null && normalizedTo == null;
                }
                LocalDate createdDate = review.getCreatedAt().atZone(zone).toLocalDate();
                if (normalizedFrom != null && createdDate.isBefore(normalizedFrom)) {
                    return false;
                }
                if (normalizedTo != null && createdDate.isAfter(normalizedTo)) {
                    return false;
                }
                return true;
            })
            .toList();
        int pageStep = 30;
        int resolvedLimit = limit == null ? pageStep : Math.max(pageStep, Math.min(limit, 600));
        int totalReviews = filteredReviews.size();
        int shownReviews = Math.min(totalReviews, resolvedLimit);
        List<Review> visibleReviews = filteredReviews.subList(0, shownReviews);
        boolean hasMoreReviews = shownReviews < totalReviews;
        int nextReviewLimit = hasMoreReviews ? Math.min(totalReviews, resolvedLimit + pageStep) : resolvedLimit;

        model.addAttribute("reviews", visibleReviews);
        model.addAttribute("reviewImagesByReviewId", reviewService.imagesByReviews(visibleReviews));
        model.addAttribute("status", status);
        model.addAttribute("rating", rating);
        model.addAttribute("dateFrom", normalizedFrom);
        model.addAttribute("dateTo", normalizedTo);
        model.addAttribute("reviewLimit", resolvedLimit);
        model.addAttribute("nextReviewLimit", nextReviewLimit);
        model.addAttribute("hasMoreReviews", hasMoreReviews);
        model.addAttribute("reviewsShown", shownReviews);
        model.addAttribute("reviewsTotal", totalReviews);
        return "admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/moderate")
    public String moderateReview(
        @PathVariable Long id,
        @RequestParam boolean approved,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer rating,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            reviewService.moderate(id, approved);
            redirectAttributes.addFlashAttribute("reviewSuccess", "Модерация выполнена");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
        }
        controllerSupport.appendReviewRedirectFilters(redirectAttributes, status, rating, dateFrom, dateTo, limit);
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/delete")
    public String deleteReview(
        @PathVariable Long id,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer rating,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        try {
            reviewService.deleteReview(id);
            redirectAttributes.addFlashAttribute("reviewSuccess", "Отзыв удалён");
        } catch (IllegalArgumentException ex) {
            controllerSupport.rethrowAjax(ex, request);
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
        }
        controllerSupport.appendReviewRedirectFilters(redirectAttributes, status, rating, dateFrom, dateTo, limit);
        return "redirect:/admin/reviews";
    }
}

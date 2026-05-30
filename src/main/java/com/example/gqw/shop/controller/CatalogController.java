package com.example.gqw.shop.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.service.CatalogService;
import com.example.gqw.shop.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

@Controller
public class CatalogController {

    private record ReviewRatingBucketVm(int score, long count, int percent) {
    }

    private final CatalogService catalogService;
    private final ReviewService reviewService;

    public CatalogController(
        CatalogService catalogService,
        ReviewService reviewService
    ) {
        this.catalogService = catalogService;
        this.reviewService = reviewService;
    }

    @GetMapping("/")
    @TrackAnalyticsEvent(code = "HOME_VIEW")
    public String home(Model model) {
        model.addAttribute("featuredCategories", catalogService.featuredTopCategories(4));
        List<Product> products = catalogService.latestProducts();
        model.addAttribute("products", products);
        model.addAttribute("productCardFeatures", catalogService.cardCharacteristics(products, 3));
        return "shop/index";
    }

    @TrackAnalyticsEvent(code = "CATALOG_VIEW")
    @GetMapping("/catalog")
    public String catalog(Model model, HttpServletRequest request) {
        model.addAttribute("catalogCategories", catalogService.topCategories());
        return "shop/catalog";
    }

    @TrackAnalyticsEvent(
        code = "CATEGORY_VIEW",
        entityType = "'CATEGORY'",
        entityId = "#model.asMap()['category'] != null && #model.asMap()['category'].id != null ? #model.asMap()['category'].id : #slug",
        attributes = {
            @TrackAnalyticsAttribute(code = "CATEGORY_SLUG", value = "#slug"),
            @TrackAnalyticsAttribute(code = "CATEGORY_NAME", value = "#model.asMap()['category'] != null ? #model.asMap()['category'].name : null"),
            @TrackAnalyticsAttribute(code = "SORT_TYPE", value = "#sort"),
            @TrackAnalyticsAttribute(code = "SEARCH_QUERY", value = "#q ?: ''"),
            @TrackAnalyticsAttribute(code = "PAGE_INDEX", value = "T(java.lang.Math).max(#page, 0)"),
            @TrackAnalyticsAttribute(code = "PAGE_SIZE", value = "T(java.lang.Math).min(32, T(java.lang.Math).max(1, #size))"),
            @TrackAnalyticsAttribute(code = "IN_STOCK_ONLY", value = "#inStockOnly != null && #inStockOnly"),
            @TrackAnalyticsAttribute(code = "OPTION_IDS_COUNT", value = "#optionIds == null ? 0 : #optionIds.size()"),
            @TrackAnalyticsAttribute(code = "MIN_PRICE", value = "#minPrice != null ? #minPrice.toPlainString() : null"),
            @TrackAnalyticsAttribute(code = "MAX_PRICE", value = "#maxPrice != null ? #maxPrice.toPlainString() : null")
        }
    )
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
        Model model,
        HttpServletRequest request
    ) {
        int resolvedSize = Math.min(32, Math.max(1, size));
        boolean onlyInStock = inStockOnly != null && inStockOnly;
        var category = catalogService.categoryBySlug(slug);
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
        return "shop/category";
    }

    @TrackAnalyticsEvent(
        code = "PRODUCT_VIEW",
        entityType = "'PRODUCT'",
        entityId = "#model.asMap()['product'] != null && #model.asMap()['product'].id != null ? #model.asMap()['product'].id : #slug"
    )
    @GetMapping("/product/{slug}")
    public String product(
        @PathVariable String slug,
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) Boolean openReview,
        Model model,
        HttpServletRequest request
    ) {
        Product product = catalogService.productBySlug(slug);
        model.addAttribute("product", product);
        model.addAttribute("productDescriptionHtml", toDescriptionHtml(product.getDescription()));
        model.addAttribute("characteristics", catalogService.characteristics(product));
        model.addAttribute("filterOptions", catalogService.filterOptions(product));
        List<Review> reviews = catalogService.approvedReviews(product);
        var repliesByReviewId = new LinkedHashMap<Long, List<Review>>();
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
        return "shop/product";
    }

    @GetMapping("/contacts")
    @TrackAnalyticsEvent(code = "CONTACTS_VIEW")
    public String contacts() {
        return "shop/contacts";
    }

    @GetMapping("/delivery")
    @TrackAnalyticsEvent(code = "DELIVERY_VIEW")
    public String delivery() {
        return "shop/delivery";
    }

    @GetMapping("/about")
    @TrackAnalyticsEvent(code = "ABOUT_VIEW")
    public String about() {
        return "shop/about";
    }

    @GetMapping("/reviews")
    @TrackAnalyticsEvent(code = "REVIEWS_PAGE_VIEW")
    public String reviewsPage(Model model) {
        model.addAttribute("reviewsFeed", catalogService.latestApprovedReviews(60));
        return "shop/reviews";
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
}

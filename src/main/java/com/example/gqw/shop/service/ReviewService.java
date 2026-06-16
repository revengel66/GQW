package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ReviewImage;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.OrderItemRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ReviewImageRepository;
import com.example.gqw.shop.repository.ReviewRepository;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReviewService {

    private static final EnumSet<OrderStatus> PURCHASED_ORDER_STATUSES = EnumSet.of(
        OrderStatus.WAITING_PICKUP,
        OrderStatus.DELIVERED
    );

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewImageStorageService reviewImageStorageService;
    private final CurrentUserService currentUserService;

    public ReviewService(
        ReviewRepository reviewRepository,
        ProductRepository productRepository,
        OrderItemRepository orderItemRepository,
        ReviewImageRepository reviewImageRepository,
        ReviewImageStorageService reviewImageStorageService,
        CurrentUserService currentUserService
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.reviewImageStorageService = reviewImageStorageService;
        this.currentUserService = currentUserService;
    }
	
    @Transactional
    public Review addReview(
        Long productId,
        Integer rating,
        String text,
        String pros,
        String cons,
        String usagePeriod,
        String guestName,
        String guestEmail,
        List<MultipartFile> images
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Оценка должна быть от 1 до 5");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст отзыва не может быть пустым");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(rating);
        review.setText(text.trim());
        review.setPros(normalizeNullable(pros, 1024));
        review.setCons(normalizeNullable(cons, 1024));
        review.setUsagePeriod(normalizeUsagePeriod(usagePeriod));
        if (user == null) {
            String normalizedGuestName = guestName == null ? "" : guestName.trim();
            String normalizedGuestEmail = guestEmail == null ? "" : guestEmail.trim();
            if (normalizedGuestName.isBlank()) {
                throw new IllegalArgumentException("Для гостевого отзыва укажите имя");
            }
            if (normalizedGuestEmail.isBlank() || !normalizedGuestEmail.contains("@")) {
                throw new IllegalArgumentException("Для гостевого отзыва укажите корректный email");
            }
            review.setGuestName(normalizedGuestName);
            review.setGuestEmail(normalizedGuestEmail);
            review.setPurchased(false);
        } else {
            review.setGuestName(null);
            review.setGuestEmail(null);
            review.setPurchased(orderItemRepository.existsByOrderUserAndProductAndOrderStatusIn(user, product, PURCHASED_ORDER_STATUSES));
        }
        review.setModerated(false);
        review.setApproved(false);
        Review saved = reviewRepository.save(review);
        saveReviewImages(saved, images);
        return saved;
    }

    @Transactional
    public Review reply(Long parentReviewId, String text, String guestName, String guestEmail) {
        Review parent = reviewRepository.findById(parentReviewId)
            .orElseThrow(() -> new IllegalArgumentException("Отзыв не найден"));
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст ответа не может быть пустым");
        }
        Review reply = new Review();
        reply.setParent(parent);
        reply.setProduct(parent.getProduct());
        reply.setUser(user);
        reply.setRating(null);
        reply.setText(text.trim());
        if (user == null) {
            String normalizedGuestName = guestName == null ? "" : guestName.trim();
            String normalizedGuestEmail = guestEmail == null ? "" : guestEmail.trim();
            if (normalizedGuestName.isBlank()) {
                throw new IllegalArgumentException("Для гостевого ответа укажите имя");
            }
            if (normalizedGuestEmail.isBlank() || !normalizedGuestEmail.contains("@")) {
                throw new IllegalArgumentException("Для гостевого ответа укажите корректный email");
            }
            reply.setGuestName(normalizedGuestName);
            reply.setGuestEmail(normalizedGuestEmail);
            reply.setPurchased(false);
        } else {
            reply.setGuestName(null);
            reply.setGuestEmail(null);
            reply.setPurchased(orderItemRepository.existsByOrderUserAndProductAndOrderStatusIn(user, parent.getProduct(), PURCHASED_ORDER_STATUSES));
        }
        reply.setModerated(false);
        reply.setApproved(false);
        return reviewRepository.save(reply);
    }

    @Transactional
    public Review moderate(Long reviewId, boolean approved) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("Отзыв не найден"));
        review.setModerated(true);
        review.setApproved(approved);
        Review saved = reviewRepository.save(review);
        refreshProductRating(saved.getProduct());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Review> replies(Review parent) {
        return reviewRepository.findByParentAndApprovedTrueOrderByCreatedAtAsc(parent);
    }

    @Transactional(readOnly = true)
    public List<Review> pendingReviews() {
        return reviewRepository.findByModeratedFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Review> currentUserTopLevelReviews() {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        return reviewRepository.findByUserAndParentIsNullOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Review> reviewsByUser(ShopUser user) {
        if (user == null) {
            return List.of();
        }
        return reviewRepository.findByUserAndParentIsNullOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Review> repliesToReviews(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        return reviewRepository.findByParentInAndApprovedTrueOrderByParentIdAscCreatedAtAsc(reviews);
    }

    @Transactional(readOnly = true)
    public List<Review> reviewsForAdmin(String status, Integer rating, Long productId) {
        String normalizedStatus = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(review -> review.getParent() == null)
            .filter(review -> {
                if ("ALL".equals(normalizedStatus)) {
                    return true;
                }
                if ("APPROVED".equals(normalizedStatus)) {
                    return Boolean.TRUE.equals(review.getModerated()) && Boolean.TRUE.equals(review.getApproved());
                }
                if ("REJECTED".equals(normalizedStatus)) {
                    return Boolean.TRUE.equals(review.getModerated()) && Boolean.FALSE.equals(review.getApproved());
                }
                return Boolean.FALSE.equals(review.getModerated());
            })
            .filter(review -> rating == null || (review.getRating() != null && review.getRating().equals(rating)))
            .filter(review -> productId == null || (review.getProduct() != null && productId.equals(review.getProduct().getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ReviewImage>> imagesByReviews(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ReviewImage>> map = new LinkedHashMap<>();
        for (Review review : reviews) {
            if (review == null || review.getId() == null) {
                continue;
            }
            map.put(review.getId(), reviewImageRepository.findByReviewOrderBySortOrderAscIdAsc(review));
        }
        return map;
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("Отзыв не найден"));
        for (Review reply : reviewRepository.findByParent(review)) {
            reviewImageRepository.deleteByReview(reply);
            reviewRepository.delete(reply);
        }
        reviewImageRepository.deleteByReview(review);
        reviewRepository.delete(review);
        refreshProductRating(review.getProduct());
    }

    @Transactional
    public Review updateReviewText(Long reviewId, String text) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("Отзыв не найден"));
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank()) {
            throw new IllegalArgumentException("Текст не может быть пустым");
        }
        review.setText(normalizedText);
        return reviewRepository.save(review);
    }

    private void saveReviewImages(Review review, List<MultipartFile> files) {
        if (review == null || review.getId() == null || files == null || files.isEmpty()) {
            return;
        }
        List<String> urls = reviewImageStorageService.store(review.getId(), files);
        for (int i = 0; i < urls.size(); i++) {
            ReviewImage reviewImage = new ReviewImage();
            reviewImage.setReview(review);
            reviewImage.setImageUrl(urls.get(i));
            reviewImage.setSortOrder(i);
            reviewImageRepository.save(reviewImage);
        }
    }

    private static String normalizeNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private static String normalizeUsagePeriod(String usagePeriod) {
        if (usagePeriod == null) {
            return null;
        }
        String normalized = usagePeriod.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LT_MONTH", "UP_TO_YEAR", "GT_YEAR" -> normalized;
            default -> null;
        };
    }

    @Transactional
    protected void refreshProductRating(Product product) {
        List<Review> approvedWithRating = reviewRepository.findByProductAndApprovedTrueAndRatingIsNotNull(product);
        int reviewCount = approvedWithRating.size();
        double ratingAvg = approvedWithRating.stream()
            .mapToInt(Review::getRating)
            .average()
            .orElse(0.0);

        product.setReviewCount(reviewCount);
        product.setRatingAvg(ratingAvg);
        productRepository.save(product);
    }
}

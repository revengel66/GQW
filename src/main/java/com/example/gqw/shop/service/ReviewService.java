package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ReviewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public ReviewService(
        ReviewRepository reviewRepository,
        ProductRepository productRepository,
        CurrentUserService currentUserService
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Review addReview(Long productId, Integer rating, String text) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(rating);
        review.setText(text);
        review.setModerated(false);
        review.setApproved(false);
        return reviewRepository.save(review);
    }

    @Transactional
    public Review reply(Long parentReviewId, String text) {
        Review parent = reviewRepository.findById(parentReviewId)
            .orElseThrow(() -> new IllegalArgumentException("Отзыв не найден"));
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        Review reply = new Review();
        reply.setParent(parent);
        reply.setProduct(parent.getProduct());
        reply.setUser(user);
        reply.setRating(null);
        reply.setText(text);
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
        return reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<Review> replies(Review parent) {
        return reviewRepository.findByParentOrderByCreatedAtAsc(parent);
    }

    @Transactional(readOnly = true)
    public List<Review> pendingReviews() {
        return reviewRepository.findByModeratedFalseOrderByCreatedAtDesc();
    }
}

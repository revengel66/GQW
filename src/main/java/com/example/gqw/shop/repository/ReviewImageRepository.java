package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ReviewImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewOrderBySortOrderAscIdAsc(Review review);

    @Modifying
    @Transactional
    @Query("""
        delete from ReviewImage ri
        where ri.review = :review
        """)
    void deleteByReview(@Param("review") Review review);

    @Modifying
    @Transactional
    @Query("""
        delete from ReviewImage ri
        where ri.review.product = :product
        """)
    void deleteByProduct(@Param("product") Product product);

    @Modifying
    @Transactional
    @Query(
        value = """
            delete from shop.review_image
            where review_id in (
                select id
                from shop.review
                where product_id = :productId
            )
            """,
        nativeQuery = true
    )
    void deleteByProductId(@Param("productId") Long productId);
}

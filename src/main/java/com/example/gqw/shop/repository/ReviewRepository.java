package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import com.example.gqw.shop.entity.ShopUser;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductAndApprovedTrueAndParentIsNullOrderByCreatedAtDesc(Product product);

    List<Review> findByProductAndParentIsNullOrderByCreatedAtDesc(Product product);

    List<Review> findByParentAndApprovedTrueOrderByCreatedAtAsc(Review parent);

    List<Review> findByModeratedFalseOrderByCreatedAtDesc();

    List<Review> findByProductAndApprovedTrueAndRatingIsNotNull(Product product);

    List<Review> findByApprovedTrueAndParentIsNullOrderByCreatedAtDesc(Pageable pageable);

    List<Review> findAllByOrderByCreatedAtDesc();

    List<Review> findByParent(Review parent);

    List<Review> findByUserAndParentIsNullOrderByCreatedAtDesc(ShopUser user);

    List<Review> findByParentInAndApprovedTrueOrderByCreatedAtDesc(List<Review> parents);

    boolean existsByProduct(Product product);

    @Modifying
    @Transactional
    @Query("""
        delete from Review r
        where r.product = :product
        """)
    void deleteByProduct(@Param("product") Product product);

    @Modifying
    @Transactional
    @Query(
        value = """
            update shop.review
            set parent_id = null
            where parent_id in (
                select id
                from shop.review
                where product_id = :productId
            )
            """,
        nativeQuery = true
    )
    void clearParentLinksByProductId(@Param("productId") Long productId);

    @Modifying
    @Transactional
    @Query(
        value = """
            delete from shop.review
            where product_id = :productId
            """,
        nativeQuery = true
    )
    void deleteByProductId(@Param("productId") Long productId);
}

package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductAndApprovedTrueAndParentIsNullOrderByCreatedAtDesc(Product product);

    List<Review> findByParentOrderByCreatedAtAsc(Review parent);

    List<Review> findByModeratedFalseOrderByCreatedAtDesc();
}

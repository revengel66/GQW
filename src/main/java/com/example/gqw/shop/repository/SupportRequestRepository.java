package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByProcessedFalseOrderByCreatedAtDesc();

    List<SupportRequest> findByUserOrderByCreatedAtDesc(ShopUser user);
}


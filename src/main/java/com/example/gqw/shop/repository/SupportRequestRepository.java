package com.example.gqw.shop.repository;

import com.example.gqw.shop.entity.SupportRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByProcessedFalseOrderByCreatedAtDesc();
}


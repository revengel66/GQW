package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsEventAttributeRepository extends JpaRepository<AnalyticsEventAttribute, Long> {

    List<AnalyticsEventAttribute> findByEventId(Long eventId);
}


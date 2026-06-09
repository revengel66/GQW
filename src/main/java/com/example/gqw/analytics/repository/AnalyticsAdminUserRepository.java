package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsAdminUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsAdminUserRepository extends JpaRepository<AnalyticsAdminUser, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    Optional<AnalyticsAdminUser> findByUsernameIgnoreCase(String username);
}


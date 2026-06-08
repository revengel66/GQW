package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.StageMetricType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StageMetricTypeRepository extends JpaRepository<StageMetricType, String> {

    java.util.List<StageMetricType> findByIsActiveTrueOrderByNameAsc();

    java.util.List<StageMetricType> findByIsSystemTrueOrderByCodeAsc();

    @Modifying
    @Transactional(transactionManager = "analyticsTransactionManager")
    @Query(value = """
        INSERT INTO analytics.stage_metric_type(code, name, description, reading_guide, value_kind, unit_default, is_system, is_active)
        VALUES (:code, :name, :description, :readingGuide, :valueKind, :unitDefault, :isSystem, :isActive)
        ON CONFLICT (code) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            reading_guide = EXCLUDED.reading_guide,
            value_kind = EXCLUDED.value_kind,
            unit_default = EXCLUDED.unit_default,
            is_system = EXCLUDED.is_system,
            is_active = EXCLUDED.is_active
        """, nativeQuery = true)
    void upsert(
        @Param("code") String code,
        @Param("name") String name,
        @Param("description") String description,
        @Param("readingGuide") String readingGuide,
        @Param("valueKind") String valueKind,
        @Param("unitDefault") String unitDefault,
        @Param("isSystem") boolean isSystem,
        @Param("isActive") boolean isActive
    );

    @Modifying
    @Transactional(transactionManager = "analyticsTransactionManager")
    @Query("update StageMetricType t set t.readingGuide = :readingGuide where t.code = :code")
    int updateReadingGuideByCode(
        @Param("code") String code,
        @Param("readingGuide") String readingGuide
    );
}


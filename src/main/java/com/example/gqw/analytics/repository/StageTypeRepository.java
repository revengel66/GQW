package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.StageType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StageTypeRepository extends JpaRepository<StageType, String> {

    java.util.List<StageType> findByIsActiveTrueOrderByNameAsc();

    java.util.List<StageType> findByIsSystemTrueOrderByCodeAsc();

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO analytics.stage_type(code, name, description, is_system, is_active)
        VALUES (:code, :name, :description, :isSystem, :isActive)
        ON CONFLICT (code) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            is_system = EXCLUDED.is_system,
            is_active = EXCLUDED.is_active
        """, nativeQuery = true)
    void upsert(
        @Param("code") String code,
        @Param("name") String name,
        @Param("description") String description,
        @Param("isSystem") boolean isSystem,
        @Param("isActive") boolean isActive
    );
}


package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.ModuleType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleTypeRepository extends JpaRepository<ModuleType, String> {

    java.util.List<ModuleType> findByIsActiveTrueOrderByNameAsc();

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO analytics.module_type(code, name, description, is_active)
        VALUES (:code, :name, :description, :isActive)
        ON CONFLICT (code) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            is_active = EXCLUDED.is_active
        """, nativeQuery = true)
    void upsert(
        @Param("code") String code,
        @Param("name") String name,
        @Param("description") String description,
        @Param("isActive") boolean isActive
    );
}

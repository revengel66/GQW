package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.EventType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventTypeRepository extends JpaRepository<EventType, String> {

    java.util.List<EventType> findByIsActiveTrueOrderByNameAsc();

    java.util.List<EventType> findByIsActiveTrueAndIsSystemFalseOrderByNameAsc();

    java.util.List<EventType> findByIsSystemTrueOrderByCodeAsc();

    java.util.List<EventType> findByIsActiveTrueAndModuleCodeOrderByNameAsc(String moduleCode);

    java.util.List<EventType> findByIsActiveTrueAndModuleCodeAndIsSystemFalseOrderByNameAsc(String moduleCode);

    java.util.List<EventType> findByModuleCodeOrderByCodeAsc(String moduleCode);

    long countByModuleCode(String moduleCode);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO analytics.event_type(code, name, description, module_code, is_system, is_active)
        VALUES (:code, :name, :description, :moduleCode, :isSystem, :isActive)
        ON CONFLICT (code) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            module_code = EXCLUDED.module_code,
            is_system = EXCLUDED.is_system,
            is_active = EXCLUDED.is_active
        """, nativeQuery = true)
    void upsert(
        @Param("code") String code,
        @Param("name") String name,
        @Param("description") String description,
        @Param("moduleCode") String moduleCode,
        @Param("isSystem") boolean isSystem,
        @Param("isActive") boolean isActive
    );
}


package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.EventAttributeType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventAttributeTypeRepository extends JpaRepository<EventAttributeType, String> {

    java.util.List<EventAttributeType> findByIsActiveTrueOrderByNameAsc();

    java.util.List<EventAttributeType> findByIsSystemTrueOrderByCodeAsc();

    @Modifying
    @Transactional(transactionManager = "analyticsTransactionManager")
    @Query(value = """
        INSERT INTO analytics.event_attribute_type(code, name, description, value_kind, unit_default, is_system, is_active)
        VALUES (:code, :name, :description, :valueKind, :unitDefault, :isSystem, :isActive)
        ON CONFLICT (code) DO UPDATE
        SET name = EXCLUDED.name,
            description = EXCLUDED.description,
            value_kind = EXCLUDED.value_kind,
            unit_default = EXCLUDED.unit_default,
            is_system = EXCLUDED.is_system,
            is_active = EXCLUDED.is_active
        """, nativeQuery = true)
    void upsert(
        @Param("code") String code,
        @Param("name") String name,
        @Param("description") String description,
        @Param("valueKind") String valueKind,
        @Param("unitDefault") String unitDefault,
        @Param("isSystem") boolean isSystem,
        @Param("isActive") boolean isActive
    );
}


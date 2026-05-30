package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsCodeAlias;
import com.example.gqw.analytics.entity.AnalyticsCodeAliasType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsCodeAliasRepository extends JpaRepository<AnalyticsCodeAlias, Long> {

    Optional<AnalyticsCodeAlias> findByAliasTypeAndSourceCodeAndIsActiveTrue(
        AnalyticsCodeAliasType aliasType,
        String sourceCode
    );

    Optional<AnalyticsCodeAlias> findByAliasTypeAndSourceCode(AnalyticsCodeAliasType aliasType, String sourceCode);

    List<AnalyticsCodeAlias> findAllByAliasTypeOrderBySourceCodeAsc(AnalyticsCodeAliasType aliasType);
}


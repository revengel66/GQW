package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsEventAttributeService {

    private final AnalyticsEventAttributeRepository eventAttributeRepository;
    private final EventAttributeTypeRepository eventAttributeTypeRepository;
    private final AnalyticsCodeResolverService codeResolverService;

    public AnalyticsEventAttributeService(
        AnalyticsEventAttributeRepository eventAttributeRepository,
        EventAttributeTypeRepository eventAttributeTypeRepository,
        AnalyticsCodeResolverService codeResolverService
    ) {
        this.eventAttributeRepository = eventAttributeRepository;
        this.eventAttributeTypeRepository = eventAttributeTypeRepository;
        this.codeResolverService = codeResolverService;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void addTextAttribute(Long eventId, String attributeTypeCode, String value) {
        String resolvedCode = codeResolverService.resolveAttributeTypeCode(attributeTypeCode);
        EventAttributeType type = eventAttributeTypeRepository.findById(resolvedCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown attribute type: " + attributeTypeCode));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalArgumentException("Inactive attribute type: " + resolvedCode);
        }
        if (type.getValueKind().name().equals("NUMERIC")) {
            throw new IllegalArgumentException("Attribute type is not text: " + resolvedCode);
        }

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(eventId);
        attribute.setAttributeTypeCode(resolvedCode);
        attribute.setAttrValue(value);
        attribute.setCreatedAt(Instant.now());
        eventAttributeRepository.save(attribute);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void addJsonAttribute(Long eventId, String attributeTypeCode, String valueJson) {
        String resolvedCode = codeResolverService.resolveAttributeTypeCode(attributeTypeCode);
        EventAttributeType type = eventAttributeTypeRepository.findById(resolvedCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown attribute type: " + attributeTypeCode));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalArgumentException("Inactive attribute type: " + resolvedCode);
        }

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(eventId);
        attribute.setAttributeTypeCode(resolvedCode);
        attribute.setAttrValueJson(valueJson);
        attribute.setCreatedAt(Instant.now());
        eventAttributeRepository.save(attribute);
    }
}


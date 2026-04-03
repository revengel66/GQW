package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsEventAttributeService {

    private final AnalyticsEventAttributeRepository eventAttributeRepository;
    private final EventAttributeTypeRepository eventAttributeTypeRepository;

    public AnalyticsEventAttributeService(
        AnalyticsEventAttributeRepository eventAttributeRepository,
        EventAttributeTypeRepository eventAttributeTypeRepository
    ) {
        this.eventAttributeRepository = eventAttributeRepository;
        this.eventAttributeTypeRepository = eventAttributeTypeRepository;
    }

    @Transactional
    public void addTextAttribute(Long eventId, String attributeTypeCode, String value) {
        EventAttributeType type = eventAttributeTypeRepository.findById(attributeTypeCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown attribute type: " + attributeTypeCode));
        if (type.getValueKind().name().equals("NUMERIC")) {
            throw new IllegalArgumentException("Attribute type is not text: " + attributeTypeCode);
        }

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(eventId);
        attribute.setAttributeTypeCode(attributeTypeCode);
        attribute.setAttrValue(value);
        attribute.setCreatedAt(Instant.now());
        eventAttributeRepository.save(attribute);
    }

    @Transactional
    public void addJsonAttribute(Long eventId, String attributeTypeCode, String valueJson) {
        eventAttributeTypeRepository.findById(attributeTypeCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown attribute type: " + attributeTypeCode));

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(eventId);
        attribute.setAttributeTypeCode(attributeTypeCode);
        attribute.setAttrValueJson(valueJson);
        attribute.setCreatedAt(Instant.now());
        eventAttributeRepository.save(attribute);
    }
}


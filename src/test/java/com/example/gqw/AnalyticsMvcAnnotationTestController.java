package com.example.gqw;

import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.analytics.aop.TrackAnalyticsMetric;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AnalyticsMvcAnnotationTestController {

    @GetMapping("/test/analytics/mvc/declared-unknown")
    @TrackAnalyticsEvent(
        code = "ANNOTATION_METRIC_EVENT",
        attributes = {
            @TrackAnalyticsAttribute(code = "MVC_UNKNOWN_ATTRIBUTE_FOR_TEST", value = "'mvc-attribute'")
        },
        metrics = {
            @TrackAnalyticsMetric(code = "MVC_UNKNOWN_METRIC_FOR_TEST", value = "'123'", unit = "count")
        },
        trackPayloadSize = false
    )
    public String declaredUnknown() {
        return "shop/contacts";
    }

    @GetMapping("/test/analytics/mvc/declared-known")
    @TrackAnalyticsEvent(
        code = "ANNOTATION_METRIC_EVENT",
        attributes = {
            @TrackAnalyticsAttribute(code = "MVC_KNOWN_ATTRIBUTE_FOR_TEST", value = "'mvc-attribute'")
        },
        metrics = {
            @TrackAnalyticsMetric(code = "MVC_KNOWN_METRIC_FOR_TEST", value = "'123'", unit = "count")
        },
        trackPayloadSize = false
    )
    public String declaredKnown() {
        return "shop/contacts";
    }
}

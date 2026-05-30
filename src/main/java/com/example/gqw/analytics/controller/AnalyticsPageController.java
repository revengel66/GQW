package com.example.gqw.analytics.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AnalyticsPageController {

    private static final DateTimeFormatter INPUT_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @GetMapping("/analytics")
    public String analyticsDashboard(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("analyticsTo", now.format(INPUT_DT));
        model.addAttribute("analyticsFrom", now.minusHours(24).format(INPUT_DT));
        model.addAttribute("analyticsApiBase", "/analytics/api");
        return "analytics/dashboard";
    }
}

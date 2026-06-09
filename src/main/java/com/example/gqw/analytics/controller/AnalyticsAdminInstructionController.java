package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AnalyticsAdminInstructionController {

    @GetMapping("/analytics-admin/instruction")
    public String instruction(Model model, HttpServletRequest request) {
        model.addAttribute(
            "analyticsAdminUsername",
            request.getSession(true).getAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME)
        );
        return "analytics/admin-instruction";
    }
}

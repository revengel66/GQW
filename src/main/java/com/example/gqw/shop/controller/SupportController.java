package com.example.gqw.shop.controller;

import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.shop.dto.SupportRequestForm;
import com.example.gqw.shop.service.SupportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping("/support/request")
    @TrackAnalyticsEvent(
        code = "SUPPORT_REQUEST",
        attributes = {
            @TrackAnalyticsAttribute(code = "SUPPORT_TOPIC", value = "'SUPPORT_FORM'"),
            @TrackAnalyticsAttribute(code = "EMAIL_DOMAIN", value = "#p0.email() == null || !#p0.email().contains('@') ? null : #p0.email().substring(#p0.email().indexOf('@') + 1)")
        }
    )
    public String support(
        @Valid @ModelAttribute("supportForm") SupportRequestForm supportForm,
        BindingResult bindingResult,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            supportService.create(supportForm);
            redirectAttributes.addFlashAttribute("supportSuccess", "Заявка отправлена. Мы свяжемся с вами.");
        } else {
            redirectAttributes.addFlashAttribute("supportError", "Проверьте корректность введенных данных.");
        }
        return "redirect:/support";
    }

    @GetMapping("/support")
    @TrackAnalyticsEvent(code = "SUPPORT_PAGE_VIEW")
    public String supportPage() {
        return "shop/support";
    }
}

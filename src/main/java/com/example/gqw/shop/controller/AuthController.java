package com.example.gqw.shop.controller;

import com.example.gqw.shop.dto.RegisterRequest;
import com.example.gqw.shop.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "shop/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest("", "", "", "", ""));
        return "shop/register";
    }

    @PostMapping("/register")
    public String registerSubmit(
        @Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
        BindingResult bindingResult,
        HttpServletRequest request,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "shop/register";
        }
        try {
            userService.register(registerRequest);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("register_error", ex.getMessage());
            return "shop/register";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Регистрация выполнена. Войдите в систему.");
        return "redirect:/login";
    }
}

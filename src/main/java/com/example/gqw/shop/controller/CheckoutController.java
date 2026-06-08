package com.example.gqw.shop.controller;

import com.example.gqw.shop.dto.CheckoutRequest;
import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.service.CartService;
import com.example.gqw.shop.service.CurrentUserService;
import com.example.gqw.shop.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public CheckoutController(
        CartService cartService,
        OrderService orderService,
        CurrentUserService currentUserService
    ) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/checkout")
    public String checkout(Model model, HttpServletRequest request) {
        List<CartItem> items = cartService.items(request.getSession().getId());
        if (items.isEmpty()) {
            return "redirect:/cart";
        }
        ShopUser currentUser = currentUserService.findCurrentUser().orElse(null);
        CheckoutRequest checkoutRequest = new CheckoutRequest(
            currentUser != null && currentUser.getFullName() != null ? currentUser.getFullName() : "",
            currentUser != null && currentUser.getEmail() != null ? currentUser.getEmail() : "",
            currentUser != null && currentUser.getPhone() != null ? currentUser.getPhone() : "",
            "PICKUP",
            currentUser != null ? firstNonBlank(currentUser.getAddressStreet(), currentUser.getAddress()) : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressHouse(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressApartment(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressEntrance(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressFloor(), "") : "",
            currentUser != null ? firstNonBlank(currentUser.getAddressIntercom(), "") : "",
            null,
            null,
            null
        );
        model.addAttribute("checkoutRequest", checkoutRequest);
        model.addAttribute("items", items);
        model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
        return "shop/checkout";
    }

    @PostMapping("/checkout")
    public String checkoutSubmit(
        @Valid @ModelAttribute("checkoutRequest") CheckoutRequest checkoutRequest,
        BindingResult bindingResult,
        HttpServletRequest request,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("items", cartService.items(request.getSession().getId()));
            model.addAttribute("total", cartService.totalAmount(request.getSession().getId()));
            return "shop/checkout";
        }

        String demoFaultHeader = request.getHeader("X-Demo-Fault");
        boolean demoReservationFailure =
            "CHECKOUT_RESERVATION_FAIL".equalsIgnoreCase(demoFaultHeader)
                || "CHECKOUT_BUSINESS_FAIL".equalsIgnoreCase(demoFaultHeader);
        try {
            orderService.checkout(checkoutRequest, request.getSession().getId(), demoReservationFailure);
            return "redirect:/account?checkoutSuccess=true";
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
            return "redirect:/cart";
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "";
    }
}

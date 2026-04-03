package com.example.gqw.shop.web;

import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.service.AdminService;
import com.example.gqw.shop.service.ReviewService;
import com.example.gqw.shop.service.SupportService;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private final AdminService adminService;
    private final SupportService supportService;
    private final ReviewService reviewService;

    public AdminController(AdminService adminService, SupportService supportService, ReviewService reviewService) {
        this.adminService = adminService;
        this.supportService = supportService;
        this.reviewService = reviewService;
    }

    @GetMapping("/admin")
    public String adminHome(Model model) {
        model.addAttribute("productsCount", adminService.products().size());
        model.addAttribute("ordersCount", adminService.orders().size());
        model.addAttribute("usersCount", adminService.users().size());
        model.addAttribute("openRequestsCount", supportService.openRequests().size());
        model.addAttribute("pendingReviewsCount", reviewService.pendingReviews().size());
        return "admin/dashboard";
    }

    @GetMapping("/admin/products")
    public String products(Model model) {
        model.addAttribute("products", adminService.products());
        return "admin/products";
    }

    @PostMapping("/admin/products/save")
    public String saveProduct(
        @RequestParam String name,
        @RequestParam String slug,
        @RequestParam String shortDescription,
        @RequestParam String description,
        @RequestParam BigDecimal price
    ) {
        adminService.saveProduct(name, slug, shortDescription, description, price);
        return "redirect:/admin/products";
    }

    @GetMapping("/admin/categories")
    public String categories(Model model) {
        model.addAttribute("categories", adminService.categories());
        return "admin/categories";
    }

    @PostMapping("/admin/categories/save")
    public String saveCategory(
        @RequestParam String name,
        @RequestParam String slug,
        @RequestParam String description,
        @RequestParam(required = false) String imageUrl
    ) {
        adminService.saveCategory(name, slug, description, imageUrl);
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/orders")
    public String orders(Model model) {
        model.addAttribute("orders", adminService.orders());
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders";
    }

    @PostMapping("/admin/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        adminService.updateOrderStatus(id, status);
        return "redirect:/admin/orders";
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        model.addAttribute("users", adminService.users());
        return "admin/users";
    }

    @GetMapping("/admin/support")
    public String support(Model model) {
        model.addAttribute("requests", supportService.openRequests());
        return "admin/support";
    }

    @PostMapping("/admin/support/{id}/processed")
    public String markSupportProcessed(@PathVariable Long id) {
        supportService.markProcessed(id);
        return "redirect:/admin/support";
    }

    @GetMapping("/admin/reviews")
    public String reviews(Model model) {
        model.addAttribute("reviews", reviewService.pendingReviews());
        return "admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/moderate")
    public String moderateReview(@PathVariable Long id, @RequestParam boolean approved) {
        reviewService.moderate(id, approved);
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/credentials")
    public String updateCredentials(@RequestParam String username, @RequestParam String password) {
        adminService.updateAdminCredentials(username, password);
        return "redirect:/admin";
    }
}


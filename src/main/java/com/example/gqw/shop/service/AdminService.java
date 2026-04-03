package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Category;
import com.example.gqw.shop.entity.OrderStatus;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.CategoryRepository;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.ShopOrderRepository;
import com.example.gqw.shop.repository.ShopUserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ShopOrderRepository orderRepository;
    private final ShopUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        ShopOrderRepository orderRepository,
        ShopUserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Product> products() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> orders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ShopUser> users() {
        return userRepository.findAll();
    }

    @Transactional
    public Category saveCategory(String name, String slug, String description, String imageUrl) {
        Category category = categoryRepository.findBySlug(slug).orElseGet(Category::new);
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        category.setImageUrl(imageUrl);
        return categoryRepository.save(category);
    }

    @Transactional
    public Product saveProduct(String name, String slug, String shortDescription, String description, BigDecimal price) {
        Product product = productRepository.findBySlug(slug).orElseGet(Product::new);
        product.setName(name);
        product.setSlug(slug);
        product.setShortDescription(shortDescription);
        product.setDescription(description);
        product.setPrice(price);
        return productRepository.save(product);
    }

    @Transactional
    public ShopOrder updateOrderStatus(Long orderId, OrderStatus status) {
        ShopOrder order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        order.setStatus(status);
        return orderRepository.save(order);
    }

    @Transactional
    public void updateAdminCredentials(String username, String rawPassword) {
        ShopUser admin = userRepository.findByUsername("admin")
            .orElseThrow(() -> new IllegalStateException("Admin user not found"));
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(admin);
    }
}


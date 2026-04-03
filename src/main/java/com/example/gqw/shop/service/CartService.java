package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.CartItem;
import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.CartItemRepository;
import com.example.gqw.shop.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public CartService(
        CartItemRepository cartItemRepository,
        ProductRepository productRepository,
        CurrentUserService currentUserService
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void addProduct(Long productId, int quantity, String sessionId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        ShopUser user = currentUserService.findCurrentUser().orElse(null);

        Optional<CartItem> existing = user != null
            ? cartItemRepository.findByUserAndProduct(user, product)
            : cartItemRepository.findBySessionIdAndProduct(sessionId, product);

        CartItem item = existing.orElseGet(CartItem::new);
        item.setProduct(product);
        item.setQuantity((item.getQuantity() == null ? 0 : item.getQuantity()) + Math.max(1, quantity));
        if (user != null) {
            item.setUser(user);
            item.setSessionId(null);
        } else {
            item.setSessionId(sessionId);
            item.setUser(null);
        }
        cartItemRepository.save(item);
    }

    @Transactional
    public void removeItem(Long itemId) {
        cartItemRepository.deleteById(itemId);
    }

    @Transactional
    public void updateQuantity(Long itemId, int quantity) {
        CartItem item = cartItemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Позиция корзины не найдена"));
        item.setQuantity(Math.max(1, quantity));
        cartItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<CartItem> items(String sessionId) {
        return currentUserService.findCurrentUser()
            .map(cartItemRepository::findByUser)
            .orElseGet(() -> cartItemRepository.findBySessionId(sessionId));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAmount(String sessionId) {
        return items(sessionId).stream()
            .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void clear(String sessionId) {
        List<CartItem> items = items(sessionId);
        cartItemRepository.deleteAll(items);
    }
}


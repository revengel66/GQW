package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.WishlistItem;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.WishlistItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public WishlistService(
        WishlistItemRepository wishlistItemRepository,
        ProductRepository productRepository,
        CurrentUserService currentUserService
    ) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void add(Long productId) {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        if (wishlistItemRepository.findByUserAndProduct(user, product).isPresent()) {
            return;
        }
        WishlistItem item = new WishlistItem();
        item.setUser(user);
        item.setProduct(product);
        wishlistItemRepository.save(item);
    }

    @Transactional
    public void remove(Long itemId) {
        wishlistItemRepository.deleteById(itemId);
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> items() {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        return wishlistItemRepository.findByUser(user);
    }
}


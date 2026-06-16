package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.Product;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.WishlistItem;
import com.example.gqw.shop.repository.ProductRepository;
import com.example.gqw.shop.repository.WishlistItemRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    private static final long GUEST_WISHLIST_TTL_DAYS = 3;

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
    public void add(Long productId, String sessionId) {
        cleanupGuestWishlist();
        Product product = resolveProduct(productId);
        ShopUser user = currentUserService.findCurrentUser().orElse(null);

        Optional<WishlistItem> existing = user != null
            ? wishlistItemRepository.findByUserAndProduct(user, product)
            : wishlistItemRepository.findBySessionIdAndProduct(sessionId, product);
        if (existing.isPresent()) {
            return;
        }

        WishlistItem item = new WishlistItem();
        item.setProduct(product);
        if (user != null) {
            item.setUser(user);
            item.setSessionId(null);
        } else {
            item.setUser(null);
            item.setSessionId(sessionId);
        }
        wishlistItemRepository.save(item);
    }

    @Transactional
    public boolean toggle(Long productId, String sessionId) {
        cleanupGuestWishlist();
        Product product = resolveProduct(productId);
        ShopUser user = currentUserService.findCurrentUser().orElse(null);

        Optional<WishlistItem> existing = user != null
            ? wishlistItemRepository.findByUserAndProduct(user, product)
            : wishlistItemRepository.findBySessionIdAndProduct(sessionId, product);
        if (existing.isPresent()) {
            wishlistItemRepository.delete(existing.get());
            return false;
        }

        WishlistItem item = new WishlistItem();
        item.setProduct(product);
        if (user != null) {
            item.setUser(user);
            item.setSessionId(null);
        } else {
            item.setUser(null);
            item.setSessionId(sessionId);
        }
        wishlistItemRepository.save(item);
        return true;
    }

    @Transactional
    public void remove(Long itemId, String sessionId) {
        cleanupGuestWishlist();
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        WishlistItem item = user != null
            ? wishlistItemRepository.findByIdAndUser(itemId, user)
                .orElseThrow(() -> new IllegalArgumentException("Позиция избранного не найдена"))
            : wishlistItemRepository.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Позиция избранного не найдена"));
        wishlistItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> items(String sessionId) {
        Instant threshold = Instant.now().minus(GUEST_WISHLIST_TTL_DAYS, ChronoUnit.DAYS);
        return currentUserService.findCurrentUser()
            .map(wishlistItemRepository::findByUser)
            .orElseGet(() -> wishlistItemRepository.findBySessionId(sessionId).stream()
                .filter(item -> item.getCreatedAt() == null || !item.getCreatedAt().isBefore(threshold))
                .toList());
    }

    @Transactional(readOnly = true)
    public int count(String sessionId) {
        return items(sessionId).size();
    }

    @Transactional
    public void mergeSessionWishlistToUser(String sessionId) {
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        if (user == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        cleanupGuestWishlist();

        List<WishlistItem> guestItems = wishlistItemRepository.findBySessionId(sessionId);
        for (WishlistItem guestItem : guestItems) {
            Optional<WishlistItem> existing = wishlistItemRepository.findByUserAndProduct(user, guestItem.getProduct());
            if (existing.isPresent()) {
                wishlistItemRepository.delete(guestItem);
                continue;
            }
            guestItem.setUser(user);
            guestItem.setSessionId(null);
            wishlistItemRepository.save(guestItem);
        }
    }

    @Transactional
    public void cleanupGuestWishlist() {
        Instant threshold = Instant.now().minus(GUEST_WISHLIST_TTL_DAYS, ChronoUnit.DAYS);
        wishlistItemRepository.deleteByCreatedAtBeforeAndSessionIdIsNotNull(threshold);
    }

    private Product resolveProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
    }
}


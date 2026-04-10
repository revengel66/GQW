package com.example.gqw.shop.service;

import com.example.gqw.shop.dto.SupportRequestForm;
import com.example.gqw.shop.entity.ShopOrder;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.repository.ShopOrderRepository;
import com.example.gqw.shop.repository.SupportRequestRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {

    private final SupportRequestRepository supportRequestRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final CurrentUserService currentUserService;

    public SupportService(
        SupportRequestRepository supportRequestRepository,
        ShopOrderRepository shopOrderRepository,
        CurrentUserService currentUserService
    ) {
        this.supportRequestRepository = supportRequestRepository;
        this.shopOrderRepository = shopOrderRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public SupportRequest create(SupportRequestForm form) {
        SupportRequest request = new SupportRequest();
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        request.setUser(user);
        request.setOrder(null);
        request.setName(form.name().trim());
        request.setEmail(form.email().trim());
        request.setPhone(form.phone() == null ? null : form.phone().trim());
        request.setSubject("Общий вопрос");
        request.setMessage(form.message().trim());
        request.setProcessed(false);
        return supportRequestRepository.save(request);
    }

    @Transactional
    public SupportRequest createForAccount(Long orderId, String message) {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("Текст обращения не может быть пустым");
        }
        SupportRequest request = new SupportRequest();
        request.setUser(user);
        request.setName(user.getFullName());
        request.setEmail(user.getEmail());
        request.setPhone(user.getPhone());
        request.setMessage(normalizedMessage);
        request.setProcessed(false);
        if (orderId != null) {
            ShopOrder order = shopOrderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new IllegalArgumentException("Заказ для обращения не найден"));
            request.setOrder(order);
            request.setSubject("Вопрос по заказу #" + order.getId());
        } else {
            request.setOrder(null);
            request.setSubject("Общий вопрос");
        }
        return supportRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> openRequests() {
        return supportRequestRepository.findByProcessedFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> userRequests() {
        ShopUser user = currentUserService.findCurrentUser()
            .orElseThrow(() -> new IllegalStateException("Требуется авторизация"));
        return supportRequestRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> requestsByUser(ShopUser user) {
        if (user == null) {
            return List.of();
        }
        return supportRequestRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public void markProcessed(Long id) {
        SupportRequest request = supportRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));
        request.setProcessed(true);
        supportRequestRepository.save(request);
    }
}


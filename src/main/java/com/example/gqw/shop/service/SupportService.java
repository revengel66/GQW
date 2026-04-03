package com.example.gqw.shop.service;

import com.example.gqw.shop.dto.SupportRequestForm;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.entity.SupportRequest;
import com.example.gqw.shop.repository.SupportRequestRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {

    private final SupportRequestRepository supportRequestRepository;
    private final CurrentUserService currentUserService;

    public SupportService(SupportRequestRepository supportRequestRepository, CurrentUserService currentUserService) {
        this.supportRequestRepository = supportRequestRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public SupportRequest create(SupportRequestForm form) {
        SupportRequest request = new SupportRequest();
        ShopUser user = currentUserService.findCurrentUser().orElse(null);
        request.setUser(user);
        request.setName(form.name());
        request.setEmail(form.email());
        request.setPhone(form.phone());
        request.setMessage(form.message());
        request.setProcessed(false);
        return supportRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> openRequests() {
        return supportRequestRepository.findByProcessedFalseOrderByCreatedAtDesc();
    }

    @Transactional
    public void markProcessed(Long id) {
        SupportRequest request = supportRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Запрос не найден"));
        request.setProcessed(true);
        supportRequestRepository.save(request);
    }
}


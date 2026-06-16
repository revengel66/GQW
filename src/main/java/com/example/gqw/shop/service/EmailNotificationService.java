package com.example.gqw.shop.service;

import com.example.gqw.shop.entity.ShopOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    public void notifyOrderStatus(ShopOrder order) {
        log.info("Order email notification: orderId={}, email={}, status={}",
            order.getId(), order.getCustomerEmail(), order.getStatus());
    }
}


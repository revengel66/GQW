package com.example.gqw.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
    @NotBlank String customerName,
    @NotBlank @Email String customerEmail,
    String customerPhone
) {
}


package com.example.gqw.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalTime;

public record CheckoutRequest(
    @NotBlank String customerName,
    @NotBlank @Email String customerEmail,
    String customerPhone,
    String deliveryType,
    String deliveryStreet,
    String deliveryHouse,
    String deliveryApartment,
    String deliveryEntrance,
    String deliveryFloor,
    String deliveryIntercom,
    LocalDate pickupDate,
    LocalDate deliveryDate,
    LocalTime deliveryTime
) {
}


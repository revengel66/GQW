package com.example.gqw.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_filter", schema = "shop")
public class ProductFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String valueType = "LIST";

    @Column(length = 32)
    private String viewType = "CHECKBOX";

    @Column
    private Boolean multiValue = true;

    @Column
    private Boolean isEnabled = true;

    @Column
    private Boolean systemFilter = false;
}


package com.example.gqw.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product", schema = "shop")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 256)
    private String name;

    @Column(nullable = false, unique = true, length = 256)
    private String slug;

    @Column(unique = true, length = 64)
    private String article;

    @Column(length = 1024)
    private String shortDescription;

    @Column(length = 4000)
    private String description;

    @Column(length = 2048)
    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(nullable = false)
    private Boolean isNew = false;

    @Column(nullable = false)
    private Boolean isHit = false;

    @Column(nullable = false)
    private Boolean isDiscount = false;

    @Column
    private Boolean isPublished = true;

    @Column(nullable = false)
    private Boolean inStock = true;

    @Column(nullable = false)
    private Double ratingAvg = 0.0;

    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(
        name = "product_category",
        schema = "shop",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Transient
    public Integer getDiscountPercent() {
        if (oldPrice == null || price == null || oldPrice.signum() <= 0 || oldPrice.compareTo(price) <= 0) {
            return null;
        }
        BigDecimal ratio = BigDecimal.ONE.subtract(price.divide(oldPrice, 4, RoundingMode.HALF_UP));
        return ratio.multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
    }

    @Transient
    public String getPrimaryImageUrl() {
        if (images != null && Hibernate.isInitialized(images) && !images.isEmpty()) {
            ProductImage first = images.getFirst();
            if (first != null && first.getImageUrl() != null && !first.getImageUrl().isBlank()) {
                return first.getImageUrl();
            }
        }
        return imageUrl;
    }
}


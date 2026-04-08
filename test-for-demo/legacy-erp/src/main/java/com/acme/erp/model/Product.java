package com.acme.erp.model;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "PRODUCTS")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "PRODUCT_SEQ", allocationSize = 1)
    @Column(name = "PRODUCT_ID")
    private Long id;

    @Column(name = "SKU", unique = true, nullable = false, length = 30)
    private String sku;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Column(name = "UNIT_PRICE", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "STOCK_QUANTITY", nullable = false)
    private Integer stockQuantity;

    @Column(name = "CATEGORY", length = 50)
    private String category;

    @Column(name = "ACTIVE")
    private Boolean active = true;

    public Product() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}

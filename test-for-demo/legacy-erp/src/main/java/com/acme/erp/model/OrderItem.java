package com.acme.erp.model;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ORDER_ITEMS")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")
    @SequenceGenerator(name = "order_item_seq", sequenceName = "ORDER_ITEM_SEQ", allocationSize = 1)
    @Column(name = "ITEM_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "UNIT_PRICE", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "LINE_TOTAL", precision = 15, scale = 2)
    private BigDecimal lineTotal;

    public OrderItem() {}

    public BigDecimal getLineTotal() {
        this.lineTotal = unitPrice.multiply(new BigDecimal(quantity));
        return lineTotal;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}

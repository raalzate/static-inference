package com.acme.erp.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Core Order entity — accessed by 8+ services, 3 DAOs, and 2 servlets.
 * High coupling candidate for modernization.
 */
@Entity
@Table(name = "ORDERS")
@NamedQueries({
    @NamedQuery(name = "Order.findByStatus",
        query = "SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC"),
    @NamedQuery(name = "Order.findByCustomer",
        query = "SELECT o FROM Order o WHERE o.customer.id = :customerId"),
    @NamedQuery(name = "Order.findRecentWithItems",
        query = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.createdAt > :since")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "ORDER_SEQ", allocationSize = 1)
    @Column(name = "ORDER_ID")
    private Long id;

    @Column(name = "ORDER_NUMBER", unique = true, nullable = false, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUSTOMER_ID", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "TOTAL_AMOUNT", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "DISCOUNT_PERCENT", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATED_AT", nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "UPDATED_AT")
    private Date updatedAt;

    @Column(name = "NOTES", length = 2000)
    private String notes;

    @Version
    @Column(name = "VERSION")
    private Long version;

    public Order() {}

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotal();
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        recalculateTotal();
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(OrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = totalAmount.multiply(discountPercent).divide(new BigDecimal("100"));
            this.totalAmount = totalAmount.subtract(discount);
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

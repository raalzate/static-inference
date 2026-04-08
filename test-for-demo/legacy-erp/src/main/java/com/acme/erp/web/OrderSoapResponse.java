package com.acme.erp.web;

import javax.xml.bind.annotation.*;
import java.math.BigDecimal;

@XmlRootElement(name = "order")
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderSoapResponse {

    private Long id;
    private String orderNumber;
    private String status;
    private BigDecimal totalAmount;
    private String customerName;
    private String createdAt;

    public OrderSoapResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

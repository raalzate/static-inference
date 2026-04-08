package com.acme.erp.model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "CUSTOMERS")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "CUSTOMER_SEQ", allocationSize = 1)
    @Column(name = "CUSTOMER_ID")
    private Long id;

    @Column(name = "COMPANY_NAME", nullable = false, length = 200)
    private String companyName;

    @Column(name = "TAX_ID", unique = true, length = 20)
    private String taxId;

    @Column(name = "EMAIL", length = 100)
    private String email;

    @Column(name = "PHONE", length = 30)
    private String phone;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "CREDIT_LIMIT", precision = 15, scale = 2)
    private java.math.BigDecimal creditLimit;

    @Column(name = "ACTIVE")
    private Boolean active = true;

    @OneToMany(mappedBy = "customer")
    private List<Order> orders = new ArrayList<>();

    public Customer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public java.math.BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(java.math.BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }
}

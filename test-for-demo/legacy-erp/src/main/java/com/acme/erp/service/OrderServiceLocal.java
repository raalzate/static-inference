package com.acme.erp.service;

import com.acme.erp.model.Order;
import com.acme.erp.model.OrderStatus;

import javax.ejb.Local;
import java.util.List;

@Local
public interface OrderServiceLocal {
    Order createOrder(Long customerId, List<OrderItemRequest> items);
    Order updateStatus(Long orderId, OrderStatus newStatus);
    List<Order> findByCustomer(Long customerId);
    List<Order> findByStatus(OrderStatus status);
    Order findById(Long orderId);
    List<Order> findRecentOrders(int days);
}

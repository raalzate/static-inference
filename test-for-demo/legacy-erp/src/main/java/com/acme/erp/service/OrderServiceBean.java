package com.acme.erp.service;

import com.acme.erp.dao.OrderDAO;
import com.acme.erp.dao.ProductDAO;
import com.acme.erp.model.*;

import javax.ejb.*;
import javax.inject.Inject;
import javax.annotation.Resource;
import javax.jms.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Core order management EJB — high coupling (CBO ~22).
 * Depends on: OrderDAO, ProductDAO, InventoryServiceBean, CustomerServiceBean, NotificationServiceBean.
 * Accessed by: OrderAction (Struts), OrderServlet, OrderWebService (SOAP).
 * Sends JMS messages on order creation and status changes.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class OrderServiceBean implements OrderServiceLocal {

    @EJB
    private InventoryServiceBean inventoryService;

    @EJB
    private CustomerServiceBean customerService;

    @EJB
    private NotificationServiceBean notificationService;

    @Inject
    private OrderDAO orderDAO;

    @Inject
    private ProductDAO productDAO;

    @Resource(mappedName = "jms/OrderEventsQueue")
    private Queue orderEventsQueue;

    @Resource(mappedName = "jms/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource
    private SessionContext ctx;

    @Override
    public Order createOrder(Long customerId, List<OrderItemRequest> items) {
        Customer customer = customerService.findById(customerId);
        if (customer == null) {
            ctx.setRollbackOnly();
            throw new EJBException("Customer not found: " + customerId);
        }

        // Check credit limit
        BigDecimal orderTotal = calculateTotal(items);
        BigDecimal outstandingBalance = orderDAO.getOutstandingBalance(customerId);
        if (outstandingBalance.add(orderTotal).compareTo(customer.getCreditLimit()) > 0) {
            ctx.setRollbackOnly();
            throw new EJBException("Credit limit exceeded for customer: " + customerId);
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.DRAFT);
        order.setCreatedAt(new Date());

        for (OrderItemRequest itemReq : items) {
            Product product = productDAO.findById(itemReq.getProductId());
            if (product == null) {
                ctx.setRollbackOnly();
                throw new EJBException("Product not found: " + itemReq.getProductId());
            }

            // Reserve inventory
            boolean reserved = inventoryService.reserveStock(
                product.getId(), itemReq.getQuantity());
            if (!reserved) {
                ctx.setRollbackOnly();
                throw new EJBException("Insufficient stock for product: " + product.getSku());
            }

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getUnitPrice());
            order.addItem(item);
        }

        orderDAO.save(order);
        sendOrderEvent("ORDER_CREATED", order);
        notificationService.notifyOrderCreated(order);

        return order;
    }

    @Override
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderDAO.findById(orderId);
        if (order == null) {
            throw new EJBException("Order not found: " + orderId);
        }

        OrderStatus oldStatus = order.getStatus();
        validateStatusTransition(oldStatus, newStatus);

        order.setStatus(newStatus);
        order.setUpdatedAt(new Date());
        orderDAO.update(order);

        if (newStatus == OrderStatus.CANCELLED) {
            // Release reserved inventory
            for (OrderItem item : order.getItems()) {
                inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
            }
        }

        sendOrderEvent("STATUS_CHANGED", order);
        notificationService.notifyStatusChanged(order, oldStatus, newStatus);

        return order;
    }

    @Override
    public List<Order> findByCustomer(Long customerId) {
        return orderDAO.findByCustomer(customerId);
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return orderDAO.findByStatus(status);
    }

    @Override
    public Order findById(Long orderId) {
        return orderDAO.findById(orderId);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Order> findRecentOrders(int days) {
        return orderDAO.findRecentWithItems(days);
    }

    private BigDecimal calculateTotal(List<OrderItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            Product product = productDAO.findById(item.getProductId());
            if (product != null) {
                total = total.add(product.getUnitPrice().multiply(new BigDecimal(item.getQuantity())));
            }
        }
        return total;
    }

    private void validateStatusTransition(OrderStatus from, OrderStatus to) {
        // Business rule: only valid transitions
        if (from == OrderStatus.CANCELLED || from == OrderStatus.DELIVERED) {
            throw new EJBException("Cannot transition from terminal status: " + from);
        }
        if (from == OrderStatus.DRAFT && to != OrderStatus.PENDING_APPROVAL && to != OrderStatus.CANCELLED) {
            throw new EJBException("Draft orders can only be submitted or cancelled");
        }
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }

    private void sendOrderEvent(String eventType, Order order) {
        try {
            Connection conn = connectionFactory.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(orderEventsQueue);

            TextMessage message = session.createTextMessage();
            message.setStringProperty("eventType", eventType);
            message.setStringProperty("orderId", order.getId().toString());
            message.setStringProperty("orderNumber", order.getOrderNumber());
            message.setStringProperty("status", order.getStatus().name());
            message.setText("{\"orderId\":" + order.getId() + ",\"event\":\"" + eventType + "\"}");

            producer.send(message);
            producer.close();
            session.close();
            conn.close();
        } catch (JMSException e) {
            // Log but don't fail the transaction for messaging errors
            System.err.println("Failed to send order event: " + e.getMessage());
        }
    }
}

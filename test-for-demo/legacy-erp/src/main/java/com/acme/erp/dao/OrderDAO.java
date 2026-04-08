package com.acme.erp.dao;

import com.acme.erp.model.Order;
import com.acme.erp.model.OrderStatus;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Order DAO — raw EntityManager usage, typical legacy pattern.
 * Should migrate to Spring Data JPA repository.
 */
public class OrderDAO {

    @PersistenceContext(unitName = "erpPU")
    private EntityManager em;

    public Order findById(Long id) {
        return em.find(Order.class, id);
    }

    public List<Order> findByCustomer(Long customerId) {
        return em.createNamedQuery("Order.findByCustomer", Order.class)
            .setParameter("customerId", customerId)
            .getResultList();
    }

    public List<Order> findByStatus(OrderStatus status) {
        return em.createNamedQuery("Order.findByStatus", Order.class)
            .setParameter("status", status)
            .getResultList();
    }

    public List<Order> findRecentWithItems(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date since = cal.getTime();
        return em.createNamedQuery("Order.findRecentWithItems", Order.class)
            .setParameter("since", since)
            .getResultList();
    }

    public BigDecimal getOutstandingBalance(Long customerId) {
        TypedQuery<BigDecimal> query = em.createQuery(
            "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
            "WHERE o.customer.id = :customerId " +
            "AND o.status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')",
            BigDecimal.class);
        query.setParameter("customerId", customerId);
        return query.getSingleResult();
    }

    public List<Order> searchOrders(String keyword, OrderStatus status, Date fromDate, Date toDate) {
        StringBuilder jpql = new StringBuilder("SELECT o FROM Order o WHERE 1=1");
        if (keyword != null) {
            jpql.append(" AND (o.orderNumber LIKE :keyword OR o.notes LIKE :keyword)");
        }
        if (status != null) {
            jpql.append(" AND o.status = :status");
        }
        if (fromDate != null) {
            jpql.append(" AND o.createdAt >= :fromDate");
        }
        if (toDate != null) {
            jpql.append(" AND o.createdAt <= :toDate");
        }
        jpql.append(" ORDER BY o.createdAt DESC");

        TypedQuery<Order> query = em.createQuery(jpql.toString(), Order.class);
        if (keyword != null) query.setParameter("keyword", "%" + keyword + "%");
        if (status != null) query.setParameter("status", status);
        if (fromDate != null) query.setParameter("fromDate", fromDate);
        if (toDate != null) query.setParameter("toDate", toDate);

        return query.getResultList();
    }

    public void save(Order order) {
        em.persist(order);
        em.flush();
    }

    public void update(Order order) {
        em.merge(order);
    }

    public void delete(Long id) {
        Order order = findById(id);
        if (order != null) {
            em.remove(order);
        }
    }
}

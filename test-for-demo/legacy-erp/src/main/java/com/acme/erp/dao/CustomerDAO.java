package com.acme.erp.dao;

import com.acme.erp.model.Customer;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

public class CustomerDAO {

    @PersistenceContext(unitName = "erpPU")
    private EntityManager em;

    public Customer findById(Long id) {
        return em.find(Customer.class, id);
    }

    public Customer findByTaxId(String taxId) {
        List<Customer> results = em.createQuery(
            "SELECT c FROM Customer c WHERE c.taxId = :taxId", Customer.class)
            .setParameter("taxId", taxId)
            .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Customer> findActive() {
        return em.createQuery(
            "SELECT c FROM Customer c WHERE c.active = true ORDER BY c.companyName", Customer.class)
            .getResultList();
    }

    public void save(Customer customer) {
        em.persist(customer);
    }

    public void update(Customer customer) {
        em.merge(customer);
    }
}

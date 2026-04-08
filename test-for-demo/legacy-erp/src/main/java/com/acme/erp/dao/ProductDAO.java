package com.acme.erp.dao;

import com.acme.erp.model.Product;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

public class ProductDAO {

    @PersistenceContext(unitName = "erpPU")
    private EntityManager em;

    public Product findById(Long id) {
        return em.find(Product.class, id);
    }

    public Product findBySku(String sku) {
        List<Product> results = em.createQuery(
            "SELECT p FROM Product p WHERE p.sku = :sku", Product.class)
            .setParameter("sku", sku)
            .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Product> findByCategory(String category) {
        return em.createQuery(
            "SELECT p FROM Product p WHERE p.category = :category AND p.active = true", Product.class)
            .setParameter("category", category)
            .getResultList();
    }

    public List<Product> findAll() {
        return em.createQuery("SELECT p FROM Product p WHERE p.active = true ORDER BY p.name", Product.class)
            .getResultList();
    }

    public void save(Product product) {
        em.persist(product);
    }

    public void update(Product product) {
        em.merge(product);
    }
}

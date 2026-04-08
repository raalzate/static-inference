package com.acme.erp.service;

import com.acme.erp.dao.ProductDAO;
import com.acme.erp.model.Product;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

/**
 * Inventory management EJB — manages stock reservations.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class InventoryServiceBean {

    @Inject
    private ProductDAO productDAO;

    public boolean reserveStock(Long productId, int quantity) {
        Product product = productDAO.findById(productId);
        if (product == null || product.getStockQuantity() < quantity) {
            return false;
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productDAO.update(product);
        return true;
    }

    public void releaseStock(Long productId, int quantity) {
        Product product = productDAO.findById(productId);
        if (product != null) {
            product.setStockQuantity(product.getStockQuantity() + quantity);
            productDAO.update(product);
        }
    }

    public int getAvailableStock(Long productId) {
        Product product = productDAO.findById(productId);
        return product != null ? product.getStockQuantity() : 0;
    }
}

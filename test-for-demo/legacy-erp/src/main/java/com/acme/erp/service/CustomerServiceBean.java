package com.acme.erp.service;

import com.acme.erp.dao.CustomerDAO;
import com.acme.erp.model.Customer;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import java.util.List;

/**
 * Customer management EJB.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class CustomerServiceBean {

    @Inject
    private CustomerDAO customerDAO;

    public Customer findById(Long customerId) {
        return customerDAO.findById(customerId);
    }

    public Customer findByTaxId(String taxId) {
        return customerDAO.findByTaxId(taxId);
    }

    public List<Customer> findActiveCustomers() {
        return customerDAO.findActive();
    }

    public Customer createCustomer(Customer customer) {
        customer.setActive(true);
        customerDAO.save(customer);
        return customer;
    }

    public void deactivateCustomer(Long customerId) {
        Customer customer = customerDAO.findById(customerId);
        if (customer != null) {
            customer.setActive(false);
            customerDAO.update(customer);
        }
    }
}

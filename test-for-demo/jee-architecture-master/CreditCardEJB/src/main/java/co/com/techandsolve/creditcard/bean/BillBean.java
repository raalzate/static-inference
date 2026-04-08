package co.com.techandsolve.creditcard.bean;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import co.com.techandsolve.creditcard.entities.Bill;

@Stateless
public class BillBean {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public void create(Bill bill) {
		entityManager.persist(bill);
	}
}

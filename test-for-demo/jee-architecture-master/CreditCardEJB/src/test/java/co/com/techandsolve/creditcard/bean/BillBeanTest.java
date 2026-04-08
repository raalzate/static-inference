package co.com.techandsolve.creditcard.bean;


import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import co.com.techandsolve.creditcard.entities.Bill;

@RunWith(MockitoJUnitRunner.class)
public class BillBeanTest {

	@Mock
	EntityManager entityManager;
	
	@InjectMocks
	BillBean billBean;

	private Bill bill;

	@Before
	public void setUp(){
		bill = new Bill();
	}

	@Test
	public void verificarPersistencia(){
		bill = new Bill("Label", "Description", 10000);
		
		billBean.create(bill);
		
		Mockito.verify(entityManager).persist(bill);
	}
	
}

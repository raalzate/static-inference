package co.com.techandsolve.creditcard.mdb;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import co.com.techandsolve.creditcard.bean.BillBean;
import co.com.techandsolve.creditcard.entities.Bill;

@RunWith(MockitoJUnitRunner.class)
public class BillMdbTest {
	
	@Mock
	BillBean billBean;
	
	@Mock 
	ObjectMessage objectMessage;
	
	@InjectMocks
	BillMdb billMDB;
	
	@Test
	public void debeGuardarUnaFactura() throws JMSException{
		Bill bill = new Bill("label", "description", 1000);
		objectMessage.setObject(bill);
		
		billMDB.onMessage(objectMessage);
		
		Mockito.verify(billBean).create((Bill) objectMessage.getObject());
	}
}

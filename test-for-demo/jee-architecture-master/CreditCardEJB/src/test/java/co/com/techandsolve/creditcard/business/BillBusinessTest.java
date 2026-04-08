package co.com.techandsolve.creditcard.business;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import co.com.techandsolve.creditcard.entities.Bill;

@RunWith(MockitoJUnitRunner.class)
public class BillBusinessTest {
	
	@Mock QueueConnectionFactory queueConnectionFactory;
	@Mock Queue queue;
	@Mock Connection connection;
	@Mock Session session;
	@Mock MessageProducer messageProducer;
	@Mock ObjectMessage objectMessage;
	
	@InjectMocks BillBusiness billBusinnes;
	
	Bill bill;
	
	@Before
	public void setUp(){
		bill = new Bill("Cuenta de cobro", "valor por prestaciones", 500000);
	}
	
	@Test 
	public void debeEnviarUnMensaje() throws JMSException{
		
		Mockito.when(queueConnectionFactory.createConnection())
		.thenReturn(connection);
		
		Mockito.when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE))
		.thenReturn(session);
		
		Mockito.when(session.createProducer(queue))
		.thenReturn(messageProducer);
		
		Mockito.when(session.createObjectMessage())
		.thenReturn(objectMessage);
		
		
		//create inOrder object passing any mocks that need to be verified in order
		InOrder inOrder = Mockito.inOrder(queueConnectionFactory, connection,session,messageProducer,connection);
		
		billBusinnes.sendMessage(bill);
		
		inOrder.verify(queueConnectionFactory).createConnection();
		inOrder.verify(connection).createSession(false, Session.AUTO_ACKNOWLEDGE);
		inOrder.verify(session).createProducer(queue);
		inOrder.verify(session).createObjectMessage();
		inOrder.verify(messageProducer).send(objectMessage);
		inOrder.verify(connection).close();;

	}
	
	

}

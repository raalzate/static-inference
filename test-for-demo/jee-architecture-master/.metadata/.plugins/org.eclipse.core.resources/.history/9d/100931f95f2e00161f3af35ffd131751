package co.com.techandsolve.creditcard.business;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;

import co.com.techandsolve.creditcard.entities.Bill;

public class BillBusiness {

	@Resource(mappedName="BillFactory")
	private QueueConnectionFactory queueConnectionFactory;
	
	@Resource(mappedName="jms/queue/BillQueue")
	private Queue queue;
	
	private Connection connection;
	private MessageProducer messageProducer;

	private void connect() throws JMSException {
		connection = queueConnectionFactory.createConnection();	
	}
	
	private void close() throws JMSException{
	   connection.close();
	}
	
	private Session createSession() throws JMSException{
		return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
	}
	
	private void createProducer(Session session) throws JMSException{
		messageProducer = session.createProducer(queue);
	}
	
	public void sendMessage(Bill bill) throws JMSException{
		
		connect();
		Session session = createSession();
		createProducer(session);

		ObjectMessage objectMessage = session.createObjectMessage();
		objectMessage.setObject(bill);
		messageProducer.send(objectMessage);
		
		close();
	}
	
}

package co.com.techandsolve.creditcard.mdb;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import co.com.techandsolve.creditcard.bean.BillBean;
import co.com.techandsolve.creditcard.entities.Bill;

@MessageDriven(
		mappedName = "jms/queue/BillQueue", 
		activationConfig = { 
			@ActivationConfigProperty(
						propertyName = "acknowledgeMode", 
						propertyValue = "Auto-acknowledge"),
						
			@ActivationConfigProperty(
					propertyName = "destinationType", 
					propertyValue = "javax.jms.Queue"),
			@ActivationConfigProperty(
					propertyName = "destination", 
					propertyValue = "java:/jms/queue/BillQueue")
			}
)
public class BillMDB implements MessageListener {

	@Inject BillBean billBean;
	
	@Override
	public void onMessage(Message message) {

		ObjectMessage objectMessage = (ObjectMessage) message;
		try {
			Bill bill = (Bill) objectMessage.getObject();
			save(bill);
		} catch (JMSException e) {
			e.printStackTrace();
		}

	}
	
	private void save(Bill bill){
		billBean.create(bill);
	}

}

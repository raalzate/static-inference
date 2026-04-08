package com.acme.erp.messaging;

import com.acme.erp.service.OrderServiceLocal;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Message-Driven Bean that processes order events from the JMS queue.
 * Handles ORDER_CREATED, STATUS_CHANGED events for audit logging
 * and external system notifications.
 */
@MessageDriven(
    name = "OrderEventProcessor",
    activationConfig = {
        @ActivationConfigProperty(
            propertyName = "destinationType",
            propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(
            propertyName = "destination",
            propertyValue = "jms/OrderEventsQueue"),
        @ActivationConfigProperty(
            propertyName = "acknowledgeMode",
            propertyValue = "Auto-acknowledge"),
        @ActivationConfigProperty(
            propertyName = "maxSession",
            propertyValue = "5")
    }
)
public class OrderEventMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(OrderEventMDB.class.getName());

    @EJB
    private OrderServiceLocal orderService;

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String eventType = message.getStringProperty("eventType");
                String orderId = message.getStringProperty("orderId");
                String payload = textMessage.getText();

                LOG.info("Processing order event: " + eventType + " for order: " + orderId);

                switch (eventType) {
                    case "ORDER_CREATED":
                        handleOrderCreated(orderId, payload);
                        break;
                    case "STATUS_CHANGED":
                        handleStatusChanged(orderId, payload);
                        break;
                    default:
                        LOG.warning("Unknown event type: " + eventType);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error processing order event", e);
            // In a real system, would send to DLQ or retry
        }
    }

    private void handleOrderCreated(String orderId, String payload) {
        LOG.info("Audit: Order created — " + orderId);
        // In production: write audit record, notify warehouse, update dashboards
    }

    private void handleStatusChanged(String orderId, String payload) {
        LOG.info("Audit: Order status changed — " + orderId);
        // In production: write audit record, notify customer, update analytics
    }
}

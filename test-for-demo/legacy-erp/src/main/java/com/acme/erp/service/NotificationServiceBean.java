package com.acme.erp.service;

import com.acme.erp.model.Order;
import com.acme.erp.model.OrderStatus;

import javax.ejb.Stateless;
import javax.ejb.Asynchronous;
import javax.annotation.Resource;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Notification EJB — sends email notifications for order events.
 * Uses JavaMail via JNDI resource.
 */
@Stateless
public class NotificationServiceBean {

    private static final Logger LOG = Logger.getLogger(NotificationServiceBean.class.getName());

    @Resource(mappedName = "mail/ERPMailSession")
    private Session mailSession;

    @Asynchronous
    public void notifyOrderCreated(Order order) {
        String subject = "New Order Created: " + order.getOrderNumber();
        String body = "Order " + order.getOrderNumber() + " has been created for customer "
            + order.getCustomer().getCompanyName() + ".\n"
            + "Total: $" + order.getTotalAmount();
        sendEmail(order.getCustomer().getEmail(), subject, body);
    }

    @Asynchronous
    public void notifyStatusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        String subject = "Order " + order.getOrderNumber() + " Status Updated";
        String body = "Order " + order.getOrderNumber() + " status changed from "
            + oldStatus + " to " + newStatus + ".";
        sendEmail(order.getCustomer().getEmail(), subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress("erp@acme.com"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            LOG.info("Email sent to " + to + ": " + subject);
        } catch (MessagingException e) {
            LOG.log(Level.SEVERE, "Failed to send email to " + to, e);
        }
    }
}

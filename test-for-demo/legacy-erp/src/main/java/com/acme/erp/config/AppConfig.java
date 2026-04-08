package com.acme.erp.config;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.logging.Logger;

/**
 * Application configuration — uses JNDI lookups for all resources.
 * Typical legacy pattern tightly coupled to the application server.
 */
public class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());

    private static final String DATASOURCE_JNDI = "java:/jdbc/ErpDS";
    private static final String MAIL_SESSION_JNDI = "mail/ERPMailSession";
    private static final String JMS_FACTORY_JNDI = "jms/ConnectionFactory";

    public static DataSource getDataSource() {
        try {
            InitialContext ctx = new InitialContext();
            return (DataSource) ctx.lookup(DATASOURCE_JNDI);
        } catch (NamingException e) {
            LOG.severe("Failed to lookup datasource: " + e.getMessage());
            throw new RuntimeException("Cannot obtain datasource", e);
        }
    }

    // Hardcoded configuration values — should be externalized
    public static String getExternalApiUrl() {
        return System.getProperty("erp.external.api.url", "http://legacy-partner.acme.com:8080/api");
    }

    public static int getMaxRetries() {
        return Integer.parseInt(System.getProperty("erp.max.retries", "3"));
    }

    public static String getSmtpHost() {
        return System.getProperty("erp.smtp.host", "smtp.acme.internal");
    }

    public static int getBatchSize() {
        return Integer.parseInt(System.getProperty("erp.batch.size", "100"));
    }
}

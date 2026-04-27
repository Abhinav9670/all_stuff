package org.styli.services.order.db.product.config;

import com.sendgrid.SendGrid;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SendGridEmailConfig {

    private static final Log LOGGER = LogFactory.getLog(SendGridEmailConfig.class);

    @Value("${order.service.sendgrid.api_key}")
    private String sendGridApiKey;

    @Bean
    public SendGrid sendGrid() {
        LOGGER.info("SendGrid API Key : " + sendGridApiKey);
        return new SendGrid(sendGridApiKey);
    }
}

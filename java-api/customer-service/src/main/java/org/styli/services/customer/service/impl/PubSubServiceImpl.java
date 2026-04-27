package org.styli.services.customer.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.jetbrains.annotations.NotNull;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.InfluencerApiService;
import com.mongodb.client.result.UpdateResult;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author Aakanksha
 * @project customer-service
 */

@Component
public class PubSubServiceImpl {

    private static final Log LOGGER = LogFactory.getLog(PubSubServiceImpl.class);

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private SaveCustomer saveCustomer;

    @Autowired
    private PubSubTemplate pubSubTemplate;

    @Value("${pubsub.topic.influencer.email}")
    private String influencerEmailTopic;

    @Autowired
    private CustomerEntityRepository customerEntityRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    @Lazy
    private InfluencerApiService influencerApiService;

    // Listener for messages from preferredPaymentTopic channel
    @ServiceActivator(inputChannel = "preferredPaymentTopic")
    public void receivePreferredPaymentTopicMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
        String payload = message.getPayload();

        try {
            // Parse message payload (JSON) into a Map
            Map<String, Object> data = mapper.readValue(payload, Map.class);

            Object customerIdObj = data.get("customerId");
            String paymentMethod = (String) data.get("paymentMethod");
            Object storeIdObj = data.get("storeId");

            Integer customerId = extractCustomerId(customerIdObj);
            Integer storeId = extractStoreId(storeIdObj);

            LOGGER.info("Received preferred payment method for customerId " + customerId + "payment method: " + paymentMethod);

            if (customerId != null && paymentMethod != null && storeId != null) {
                saveCustomer.savePreferredPaymentMethodToCustomerEntity(customerId, paymentMethod, storeId);
            } else {
                LOGGER.info("Missing customerId or paymentMethod in message payload.");
            }
            // Acknowledge the message manually
            acknowledgeMessage(original);

        } catch (Exception e) {
            LOGGER.info("Error processing preferred payment method message. Will be retried. " + e);
        }
    }


    @Async("asyncExecutor")
    public void publishEmails(List<String> emails) {
        try {
            String message = mapper.writeValueAsString(emails); // now sending JSON array
            ListenableFuture<String> response = pubSubTemplate.publish(influencerEmailTopic, message);

            response.addCallback(new ListenableFutureCallback<>() {
                @Override
                public void onSuccess(String result) {
                    LOGGER.info("Email message published to Pub/Sub successfully: {}" +result);
                }

                @Override
                public void onFailure(@NotNull Throwable ex) {
                    LOGGER.info("Failed to publish email to Pub/Sub: " +ex);
                }
            });

        } catch (Exception e) {
            LOGGER.info("Exception while publishing emails to Pub/Sub" +e);
        }
    }

    @ServiceActivator(inputChannel = "influencerEmailTopic")
    public void receiveEmailMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
        try {
            LOGGER.info("Pub/Sub message received for influencer email check: " + message.getPayload());

            // Parse JSON array of email strings
            List<String> emailList = mapper.readValue(
                    message.getPayload(),
                    new TypeReference<List<String>>() {}
            );

            for (String email : emailList) {
                Query query = new Query(Criteria.where("email").is(email));
                Update update = new Update().set("isInfluencer", true);
                UpdateResult result = mongoTemplate.updateMulti(query, update, "customer_entity");

                if (result.getModifiedCount() > 0) {
                    LOGGER.info("Marked as influencer in DB for email: " + email);
                    influencerApiService.sendInfluencerToBraze(email);
                } else {
                    LOGGER.info("No customer found for email: " + email);
                }
            }

            // Acknowledge message if all processing is successful
            acknowledgeMessage(original);

        } catch (Exception e) {
            LOGGER.info("Error processing email list message; not acknowledged." +e);
        }
    }


    private void acknowledgeMessage(Object original) {
        if (original != null) {
            try {
                Method ackMethod = original.getClass().getMethod("ack");
                ackMethod.setAccessible(true);
                ackMethod.invoke(original);
                LOGGER.info("Message acknowledged successfully via reflection.");
            } catch (Exception ex) {
                LOGGER.error("Failed to acknowledge message via reflection. " + ex.getMessage(), ex);
            }
        }
    }

    private Integer extractIntegerField(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        } else if (value instanceof String string) {
            try {
                return Integer.valueOf(string);
            } catch (NumberFormatException e) {
                LOGGER.info("Invalid format");
            }
        } else if (value != null) {
            LOGGER.info("Unsupported type");
        }
        return null;
    }

    private Integer extractCustomerId(Object customerIdObj) {
        return extractIntegerField(customerIdObj);
    }

    private Integer extractStoreId(Object storeIdObj) {
        return extractIntegerField(storeIdObj);
    }

}

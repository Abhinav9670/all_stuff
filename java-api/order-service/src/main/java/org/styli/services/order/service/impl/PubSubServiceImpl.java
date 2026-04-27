package org.styli.services.order.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.styli.services.order.pojo.RefundPaymentInfo;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Biswabhusan Pradhan
 * @project order-service
 */

@Component
public class PubSubServiceImpl  {

    private static final Log LOGGER = LogFactory.getLog(PubSubServiceImpl.class);

    @Autowired
    private PubSubTemplate pubSubTemplate;
    @Autowired
    SalesOrderServiceV3Impl salesOrderServiceV3;
    @Autowired
    private ObjectMapper mapper;

    @ServiceActivator(inputChannel = "omsRefundAPIRearrangementTopic")
    public void receiveOMSRefundAPIRearrangementMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
        try {
            LOGGER.info("Pub/Sub messages received for oms refund API rearrangement topic: " + message.getPayload());
            RefundPaymentInfo request = mapper.readValue(message.getPayload(), RefundPaymentInfo.class);
            salesOrderServiceV3.findPaymentMethodAndPushToQueue(request);

            if (original != null) {
                try {
                    Method ackMethod = original.getClass().getMethod("ack");
                    ackMethod.setAccessible(true);
                    ackMethod.invoke(original);
                    LOGGER.info("Message acknowledged successfully via reflection.");
                } catch (Exception ex) {
                    LOGGER.error("Failed to ack message via reflection.", ex);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error processing message, negative acknowledgment sent for retry.", e);
        }

    }

    @Async("asyncExecutor")
    public void publishReturnRefundToPubSub(String topicName,Object data) {
        try {
            ListenableFuture<String> response = pubSubTemplate.publish(topicName, mapper.writeValueAsString(data));
            response.addCallback(new ListenableFutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    LOGGER.info("OMS tamara refund message published successfully with result: " + result);
                }

                @Override
                public void onFailure(Throwable ex) {
                    LOGGER.error("Error in publishing oms tamara refund message to Pub/Sub.", ex);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error in publishing oms tamara refund to Pub/Sub", e);
        }
    }

    @ServiceActivator(inputChannel = "omsTamaraRefundTopic")
    public void receiveOMSTamaraRefundMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
        try {
            LOGGER.info("Pub/Sub messages received for oms tamara refund topic: " + message.getPayload());
            RefundPaymentInfo refundPaymentInfo = mapper.readValue(message.getPayload(),RefundPaymentInfo.class);
            //Logic to pay tamara refund
            Boolean result =salesOrderServiceV3.refundData(refundPaymentInfo);
            if(result) {
                if (original != null) {
                    try {
                        Method ackMethod = original.getClass().getMethod("ack");
                        ackMethod.setAccessible(true);
                        ackMethod.invoke(original);
                        LOGGER.info("Message acknowledged successfully In Oms Payfort Refund.");
                    } catch (Exception ex) {
                        LOGGER.error("Failed to ack message via reflection.", ex);
                    }
                }
            }
        } catch (Exception e) {

            LOGGER.error("Error in processing message oms tamara refund topic and no AckReplyConsumer found.", e);

        }
    }

    @ServiceActivator(inputChannel = "omsPayfortRefundTopic")
    public void receiveOMSPayfortRefundMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);

        try {
            LOGGER.info("Pub/Sub messages received for oms payfort refund topic: " + message.getPayload());
            RefundPaymentInfo refundPaymentInfo = mapper.readValue(message.getPayload(),RefundPaymentInfo.class);
            //Logic to pay payfort refund
            Boolean result=salesOrderServiceV3.refundData(refundPaymentInfo);
            if(result) {
                if (original != null) {
                    try {
                        Method ackMethod = original.getClass().getMethod("ack");
                        ackMethod.setAccessible(true);
                        ackMethod.invoke(original);
                        LOGGER.info("Message acknowledged successfully In Oms Payfort Refund.");
                    } catch (Exception ex) {
                        LOGGER.error("Failed to ack message via reflection.", ex);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in processing message oms payfort refund topic: " ,e);
        }
    }

    @ServiceActivator(inputChannel = "omsShukranRefundTopic")
    public void receiveOMSShukranRefundMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
        try {
            LOGGER.info("Pub/Sub messages received for oms shukran refund topic: " + message.getPayload());
            RefundPaymentInfo refundPaymentInfo = mapper.readValue(message.getPayload(),RefundPaymentInfo.class);
            Boolean result=salesOrderServiceV3.refundData(refundPaymentInfo);
            if(result) {
                if (original != null) {
                    try {
                        Method ackMethod = original.getClass().getMethod("ack");
                        ackMethod.setAccessible(true);
                        ackMethod.invoke(original);
                        LOGGER.info("Message acknowledged successfully In Oms Shukran Refund.");
                    } catch (Exception ex) {
                        LOGGER.error("Failed to ack message via reflection.", ex);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in processing message oms shukran refund topic: " ,e);
        }
    }

    @ServiceActivator(inputChannel = "omsDefaultRefundTopic")
    public void receiveOMSDefaultRefundMessage(Message<String> message) {
        Object original = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
        try {
            LOGGER.info("Pub/Sub messages received for oms default refund topic: " + message.getPayload());
            RefundPaymentInfo refundPaymentInfo = mapper.readValue(message.getPayload(),RefundPaymentInfo.class);
            //Logic to pay shukran refund
            Boolean result =salesOrderServiceV3.refundData(refundPaymentInfo);

            if(result) {
                if (original != null) {
                    try {
                        Method ackMethod = original.getClass().getMethod("ack");
                        ackMethod.setAccessible(true);
                        ackMethod.invoke(original);
                        LOGGER.info("Message acknowledged successfully In Oms Default Refund.");
                    } catch (Exception ex) {
                        LOGGER.error("Failed to ack message via reflection.", ex);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in processing message oms default refund topic: .", e);
        }
    }

    @Async("asyncExecutor")
    public void publishSplitOrderPubSub(String topicName,Object data) {
        try {
            ListenableFuture<String> response = pubSubTemplate.publish(topicName, mapper.writeValueAsString(data));
            response.addCallback(new ListenableFutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    LOGGER.info("In split order message published successfully with result: " + result);
                }

                @Override
                public void onFailure(Throwable ex) {
                    LOGGER.error("Error in publishing split order message to Pub/Sub.", ex);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error in publishing split order to Pub/Sub", e);
        }
    }
    @Async("asyncExecutor")
    public void publishPreferredPaymentMethodToPubSub(String topicName, String paymentMethod, Integer customerId, Integer storeId) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("customerId", String.valueOf(customerId));
            data.put("paymentMethod", paymentMethod);
            data.put("storeId", String.valueOf(storeId));

            ListenableFuture<String> response = pubSubTemplate.publish(topicName, mapper.writeValueAsString(data));
            response.addCallback(new ListenableFutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    LOGGER.info("Preferred payment method message published successfully with result: " +result);
                }

                @Override
                public void onFailure(Throwable ex) {
                    LOGGER.info("Error in publishing Preferred payment method message to Pub/Sub." +ex);
                }
            });
        } catch (Exception e) {
            LOGGER.info("Error in publishing Preferred payment method to Pub/Sub" +e);
        }
    }

    @Async("asyncExecutor")
    public void publishOrderTrackingPubSub(String topicName,Object data) {
        try {
            ListenableFuture<String> response = pubSubTemplate.publish(topicName, mapper.writeValueAsString(data));
            response.addCallback(new ListenableFutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    LOGGER.info("In  order tracking service message published successfully with result: " + result);
                }

                @Override
                public void onFailure(Throwable ex) {
                    LOGGER.error("Error in publishing order trackign service message to Pub/Sub.", ex);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error in publishing order tracking service to Pub/Sub", e);
        }
    }

    @Async("asyncExecutor")
    public void publishSellerCentralPubSub(String topicName,Object data) {
        try {
            ListenableFuture<String> response = pubSubTemplate.publish(topicName, mapper.writeValueAsString(data));
            response.addCallback(new ListenableFutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    LOGGER.info("In Seller central order message published successfully with result: " + result);
                }

                @Override
                public void onFailure(Throwable ex) {
                    LOGGER.error("Error in publishing seller central order message to Pub/Sub.", ex);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error in publishing seller central order to Pub/Sub", e);
        }
    }
}

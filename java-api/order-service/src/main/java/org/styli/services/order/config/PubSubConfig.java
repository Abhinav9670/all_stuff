package org.styli.services.order.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.cloud.gcp.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.core.publisher.PubSubPublisherTemplate;
import org.springframework.cloud.gcp.pubsub.core.subscriber.PubSubSubscriberTemplate;

@Configuration
public class PubSubConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubConfig.class);

    @Value("${pubsub.subscription.refund.rearrangement}")
    private String omsRefundApiRearrangementSub;

    @Value("${pubsub.subscription.payfort.refund}")
    private String omsPayfortRefundSub;

    @Value("${pubsub.subscription.tamara.refund}")
    private String omsTamaraRefundSub;

    @Value("${pubsub.subscription.wallet.refund}")
    private String omsWalletRefundSub;

    @Value("${pubsub.subscription.shukran.refund}")
    private String omsShukranRefundSub;

    @Bean
    public PubSubTemplate pubSubTemplate(PubSubPublisherTemplate publisherTemplate, PubSubSubscriberTemplate subscriberTemplate) {
        return new PubSubTemplate(publisherTemplate, subscriberTemplate);
    }

    @Bean
    public MessageChannel splitOrderTopic() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel omsRefundAPIRearrangementTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter omsRefundAPIRearrangementMessageChannelAdapter(
            @Qualifier("omsRefundAPIRearrangementTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, omsRefundApiRearrangementSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    @Bean
    public MessageChannel omsPayfortRefundTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter omsPayfortRefundMessageChannelAdapter(
            @Qualifier("omsPayfortRefundTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, omsPayfortRefundSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    @Bean
    public MessageChannel omsTamaraRefundTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter omsTamaraRefundMessageChannelAdapter(
            @Qualifier("omsTamaraRefundTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, omsTamaraRefundSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    @Bean
    public MessageChannel omsDefaultRefundTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter omsDefaultRefundMessageChannelAdapter(
            @Qualifier("omsDefaultRefundTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, omsWalletRefundSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    @Bean
    public MessageChannel omsShukranRefundTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter omsShukranRefundMessageChannelAdapter(
            @Qualifier("omsShukranRefundTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, omsShukranRefundSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }
}

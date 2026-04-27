package org.styli.services.customer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Value("${pubsub.subscription.preferred.payment.method}")
    private String preferredPaymentMethodSub;

    @Value("${pubsub.subscription.influencer.email}")
    private String influencerEmailSub;

    @Bean
    public PubSubTemplate pubSubTemplate(PubSubPublisherTemplate publisherTemplate,
            PubSubSubscriberTemplate subscriberTemplate) {
        return new PubSubTemplate(publisherTemplate, subscriberTemplate);
    }

    // Listener channel and adapter for preferredPaymentTopic
    @Bean
    public MessageChannel preferredPaymentTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter preferredPaymentTopicMessageChannelAdapter(
            @Qualifier("preferredPaymentTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
                preferredPaymentMethodSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    // Listener channel and adapter for influencerEmailTopic
    @Bean
    public MessageChannel influencerEmailTopic() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter influencerEmailTopicMessageChannelAdapter(
            @Qualifier("influencerEmailTopic") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate, influencerEmailSub);
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }
}

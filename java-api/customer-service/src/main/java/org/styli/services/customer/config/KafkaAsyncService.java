package org.styli.services.customer.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.PreferredPaymentData;
import org.styli.services.customer.pojo.InfluencerEmailPayload;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.service.AccountDeleteService;
import java.lang.reflect.Type;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.styli.services.customer.service.impl.SaveCustomer;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.InfluencerApiService;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * @author Swapna Mahajan (Swapna.Mahajan@landmarkgroup.com)
 *
 */
@Component
@EnableAsync
public class KafkaAsyncService {

	private static final Log LOGGER = LogFactory.getLog(KafkaAsyncService.class);

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Value("${customer.kafka.customer_entity_topic}")
	private String customerTopic;

	@Value("${env}")
	private String env;

	@Autowired
	private AccountDeleteService accountDeleteService;

	@Autowired
	private SaveCustomer saveCustomer;

	@Value("${kafka.influencer.topic}")
	private String kafkaInfluencerTopic;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	InfluencerApiService influencerApiService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Async
	public CompletableFuture<String> publishCustomerEntityToKafka(Customer customer) {

		try {
			kafkaTemplate.send(customerTopic + "_" + env, customer);
		} catch (Exception e) {
			LOGGER.error("Error In Publishing Customer entity to Kafka. " + e);
		}
		return null;
	}

	/**
	 * Consume Deleted customer info and revoke apple authentication
	 * 
	 * @param message
	 */
	@KafkaListener(topics = "${customer.kafka.customer_delete_topic}", groupId = "cust-service-deleted-customers")
	public void consumeDeletedCustomers(String message) {
		try {
			LOGGER.info("Delete Customer : Kafka stream to revoke apple authentication " + message);

			Gson gson = new GsonBuilder()
					.registerTypeAdapter(LocalDate.class, new JsonDeserializer<LocalDate>() {
						@Override
						public LocalDate deserialize(JsonElement json, Type type, JsonDeserializationContext context)
								throws JsonParseException {
							return LocalDate.parse(json.getAsJsonPrimitive().getAsString());
						}
					})
					.create();

			Customer customer = gson.fromJson(message, Customer.class);
			if (Objects.nonNull(customer)) {
				LOGGER.info("Delete Customer : Calling revokeAppleAuth function ");
				accountDeleteService.revokeAppleAuth(customer);
			}
		} catch (Exception e) {
			LOGGER.error(" Delete Customer : Error in processing Kafka request for deleted customers. Error: " + e);
		}
	}

	// =================== Influencer Email Publisher ===================
	@Async("asyncExecutor")
	public void publishInfluencerEmailsToKafka(InfluencerEmailPayload payload) {
		LOGGER.info("Inside publishInfluencerEmailsToKafka function");
		LOGGER.info("Kafka topic being used: " + kafkaInfluencerTopic);

		if (payload == null || payload.getInfluencerEmailList() == null || payload.getInfluencerEmailList().isEmpty()) {
			LOGGER.warn("No influencer emails to publish.");
			return;
		}

		try {
			LOGGER.info("Sending the payload to kafka: ");
			kafkaTemplate.send(kafkaInfluencerTopic, payload);
			LOGGER.info("Published influencer emails to Kafka: " + payload.getInfluencerEmailList());
		} catch (Exception e) {
			LOGGER.error("Exception while publishing influencer emails to Kafka: " + e.getMessage(), e);
		}
	}

	// =================== Influencer Email Listener ===================
	@KafkaListener(topics = "${kafka.influencer.topic}", groupId = "cust-service-influnecer-group", containerFactory = "influencerEmailKafkaListenerContainerFactory")
	public void consumeInfluencerEmails(InfluencerEmailPayload payload) {
		try {
			LOGGER.info("Received Influencer Email payload " + payload);

			String rawEmails = payload.getInfluencerEmailList();
			if (rawEmails == null || rawEmails.trim().isEmpty()) {
				LOGGER.info("Received empty influencer email list.");
				return;
			}

			List<String> emailList = Arrays.stream(rawEmails.split(","))
					.map(String::trim)
					.filter(email -> !email.isEmpty())
					.distinct()
					.toList();

			LOGGER.info("Processing Influencer Emails " + emailList);

			for (String email : emailList) {
				Query query = new Query(Criteria.where("email").is(email));
				Update update = new Update().set("isInfluencer", true);
				UpdateResult result = mongoTemplate.updateMulti(query, update, "customer_entity"); // <--- use
																									// collection name

				if (result.getModifiedCount() > 0) {
					LOGGER.info("Marked as influencer in DB for email: " + email);
					influencerApiService.sendInfluencerToBraze(email);
				} else {
					LOGGER.info("No customer found for email: " + email);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error processing Influencer Email Kafka message", e);
		}
	}

	@KafkaListener(topics = "${preferred.payment.kafka.topic}", groupId = "cust-service-deleted-customers")
	public void consumePreferredPayment(String message) {
		try {
			LOGGER.info("Received Preferred Payment message from Kafka: {}" + message);

			Gson gson = new Gson();
			PreferredPaymentData data = gson.fromJson(message, PreferredPaymentData.class);

			if (data != null) {
				Integer customerId = data.getCustomerId();
				String paymentMethod = data.getPaymentMethod();
				Integer storeId = data.getStoreId();

				LOGGER.info("Processing Preferred Payment for customerId: " + customerId
						+ " store Id : " + storeId + " method : " + paymentMethod);

				if (customerId != null && paymentMethod != null && storeId != null) {
					saveCustomer.savePreferredPaymentMethodToCustomerEntity(customerId, paymentMethod, storeId);
				} else {
					LOGGER.info("Missing customerId, paymentMethod, or storeId in message payload.");
				}
			} else {
				LOGGER.info("Kafka message could not be parsed into PreferredPaymentData: " + message);
			}

		} catch (Exception e) {
			LOGGER.info("Error processing Preferred Payment Kafka message: " + e);
		}
	}
}

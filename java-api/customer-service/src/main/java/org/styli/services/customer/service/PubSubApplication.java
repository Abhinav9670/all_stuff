package org.styli.services.customer.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.cloud.gcp.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.utility.Constants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import antlr.StringUtils;

@Configuration
public class PubSubApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(PubSubApplication.class);
//	private final WishlistService wishlistService;
//	private final CustomerEntityRepository customerEntityRepository;
	@Value("${vm.url}")
	private String vmUrl;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

//	public PubSubApplication(WishlistService wishlistService, CustomerEntityRepository customerEntityRepository, RestTemplate restTemplate) {
//		this.wishlistService = wishlistService;
//		this.customerEntityRepository = customerEntityRepository;
//		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
//		requestFactory.setConnectTimeout(30000);  // 5 seconds
//		requestFactory.setReadTimeout(30000);     // 5 seconds
//		this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));
//	}

	@Bean
	public MessageChannel inputMessageChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public PubSubInboundChannelAdapter inboundChannelAdapter(
			@Qualifier("inputMessageChannel") MessageChannel messageChannel,
			PubSubTemplate pubSubTemplate) {
		LOGGER.info("subscription Id{}: "+ Constants.getConsulConfigResponse().getPubsubWishlistSubscriptionId());
		PubSubInboundChannelAdapter adapter =
				new PubSubInboundChannelAdapter(pubSubTemplate, Constants.getConsulConfigResponse().getPubsubWishlistSubscriptionId());
		adapter.setOutputChannel(messageChannel);
		adapter.setAckMode(AckMode.MANUAL);
		adapter.setPayloadType(String.class);
		return adapter;
	}

	@ServiceActivator(inputChannel = "inputMessageChannel")
	public void messageReceiver(
			String payload,
			@Header(GcpPubSubHeaders.ORIGINAL_MESSAGE) BasicAcknowledgeablePubsubMessage message) {

		LOGGER.info("Message arrived! Payload: {} " + payload);
//		try {
//			JsonArray jsonArray = null;
//			if (org.apache.commons.lang3.StringUtils.isNoneBlank(payload) && org.apache.commons.lang3.StringUtils.isNotEmpty(payload)) {
//				jsonArray = JsonParser.parseString(payload).getAsJsonArray();
//			}
//
//			if(jsonArray != null) {
//				for (JsonElement element : jsonArray) {
//					JsonObject jsonObject = element.getAsJsonObject();
//					LOGGER.info("jsonObject" + jsonObject);
//					if (jsonObject != null) {
//						JsonObject valueObject = jsonObject.getAsJsonObject("value");
//						if (null != valueObject) {
//							String optionId = valueObject.get("optionId") != null ? valueObject.get("optionId").getAsString() : "";
//							long count = wishlistService.countWishlistEntitiesByOptionIds(optionId);
//							int limit = Constants.getConsulConfigResponse().getBrazeWishlistCount();
//							LOGGER.info("Limit Chunk Size :{}" + limit);
//							long skip = 0L;
//							LOGGER.info("Limit optionId" + optionId);
//							if (org.apache.commons.lang3.StringUtils.isNotBlank(optionId) && org.apache.commons.lang3.StringUtils.isNotEmpty(optionId)) {
//								for (int i = 0; i < count; i += limit) {
//									LOGGER.info("Limit value i :{}" + limit);
//									BrazeWishlistEventData brazeWishlistEventData = new BrazeWishlistEventData();
//									List<SkuData> finalSkuData = new ArrayList<>();
//									skip = i;
//									LOGGER.info("optionId limit skip:{}" + optionId + " " + limit + " " + skip);
//									List<WishlistEntity> wishlistEntities = wishlistService.findWishlistEntitiesByOptionIds(optionId, limit, skip);
//									ObjectMapper objectMapper = new ObjectMapper();
//									LOGGER.info("wishlist item  :{}" + objectMapper.writeValueAsString(wishlistEntities));
//									for (WishlistEntity entity : wishlistEntities) {
//										SkuData skuData = new SkuData();
//										skuData.setExternal_id(entity.getId().toString());
//										skuData.setName("wishlist_price_drop");
//										skuData.setTime(LocalDateTime.now().toString());
//										PropertiesData propertiesData = new PropertiesData();
//										if (null != entity.getWishListItems() && null != entity.getWishListItems().get(0) && entity.getWishListItems().get(0).getCurrency() != null && entity.getWishListItems().get(0).getLastPrice() > 0 && !optionId.isEmpty()) {
//											LOGGER.info("inside  entity  :{}");
//											double currentPrice = 0.0;
//											switch (entity.getWishListItems().get(0).getCurrency()) {
//												case "SAR":
//													currentPrice = valueObject.get("productPrice_SAR") != null && !valueObject.get("productPrice_SAR").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_SAR").getAsString()) : 0.0;
//													break;
//												case "AED":
//													currentPrice = valueObject.get("productPrice_AED") != null && !valueObject.get("productPrice_AED").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_AED").getAsString()) : 0.0;
//													break;
//												case "BHD":
//													currentPrice = valueObject.get("productPrice_BHD") != null && !valueObject.get("productPrice_BHD").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_BHD").getAsString()) : 0.0;
//													break;
//												case "KWD":
//													currentPrice = valueObject.get("productPrice_KWD") != null && !valueObject.get("productPrice_KWD").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_KWD").getAsString()) : 0.0;
//													break;
//											}
//											LOGGER.info("after  switch  :{}");
//											if (currentPrice > 0 && Double.toString(entity.getWishListItems().get(0).getLastPrice()) != null && currentPrice < entity.getWishListItems().get(0).getLastPrice()){
//												propertiesData.setOld_price(BigDecimal.valueOf(entity.getWishListItems().get(0).getLastPrice()).setScale(2, RoundingMode.HALF_UP).doubleValue());
//												propertiesData.setCurrent_price(BigDecimal.valueOf(currentPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
//												propertiesData.setPriceDiff(BigDecimal.valueOf(entity.getWishListItems().get(0).getLastPrice() - currentPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
//												propertiesData.setStoreCurrency(entity.getWishListItems().get(0).getCurrency());
//												double priceDropPercent = (entity.getWishListItems().get(0).getLastPrice() - currentPrice) / entity.getWishListItems().get(0).getLastPrice() * 100;
//												propertiesData.setDrop_percent(BigDecimal.valueOf(priceDropPercent).setScale(2, RoundingMode.HALF_UP).doubleValue());
//												propertiesData.setStoreCurrency(entity.getWishListItems().get(0).getCurrency());
//												propertiesData.setProduct_name(valueObject.get("productName").getAsString());
//												propertiesData.setImage(valueObject.get("productImage").getAsString());
//												propertiesData.setUser_id(entity.getId().toString());
//												skuData.setProperties(propertiesData);
//												finalSkuData.add(skuData);
//												LOGGER.info("after  add  :{}");
//											}else{
//												continue;
//											}
//										}else{
//											continue;
//										}
//									}
//									LOGGER.info("before brazeWishlistEventData ");
//									brazeWishlistEventData.setEvents(finalSkuData);
//									if (!finalSkuData.isEmpty()) {
//										try{
//											objectMapper = new ObjectMapper();
//											LOGGER.info("Sending request to Braze API: {}", objectMapper.writeValueAsString(brazeWishlistEventData));
//											HttpHeaders headers = new HttpHeaders();
//											headers.setContentType(MediaType.APPLICATION_JSON);
//											headers.setBearerAuth(Constants.getConsulConfigResponse().getBrazeWishlistToken());
//											HttpEntity<BrazeWishlistEventData> request = new HttpEntity<>(brazeWishlistEventData, headers);
//											ResponseEntity<BrazeDetailResponse> response = restTemplate.exchange(Constants.getConsulConfigResponse().getBrazeWishlistUrl(), HttpMethod.POST, request, BrazeDetailResponse.class);
//											LOGGER.info("Braze API response: {}", Objects.requireNonNull(response.getBody()).getMessage());
//										}catch(JsonProcessingException ex){
//											LOGGER.error("Error processing JSON: {}", ex.getMessage(), ex);
//										}catch (RestClientException ex) {
//											LOGGER.error("Error sending data to Braze API: {}", ex.getMessage(), ex);
//										}
//									}else {
//										LOGGER.info("No events to send to Braze API.");
//									}
//								}
//							}
//
//						}
//					}
//				}
//			}
//		}catch(Exception e){
//			LOGGER.error("Error processing pubsub + " + e);
//		}
		message.ack();
	}
//	public static <T> List<List<T>> splitList(List<T> list, int chunkSize) {
//		List<List<T>> chunks = new ArrayList<>();
//		int listSize= list.size();
//		for (int i=0; i < listSize; i += chunkSize) {
//			chunks.add(new ArrayList<>(list.subList(i, Math.min(listSize, i + chunkSize))));
//		}
//		return chunks;
//	}
}
package org.styli.services.customer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.utility.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@EnableAsync
public class WishlistJavaKafkaConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WishlistJavaKafkaConsumer.class);
    private final WishlistService wishlistService;
    private final CustomerEntityRepository customerEntityRepository;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Value("${customer.kafka.customer_wishlist_topic}")
    private String kafkaTopic;

    public WishlistJavaKafkaConsumer(WishlistService wishlistService, CustomerEntityRepository customerEntityRepository, RestTemplate restTemplate) {
        this.wishlistService = wishlistService;
        this.customerEntityRepository = customerEntityRepository;
        LOGGER.info("kafkaTopic" + kafkaTopic);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);  // 5 seconds
        requestFactory.setReadTimeout(30000);     // 5 seconds
        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));
    }
    @KafkaListener(topics = "${customer.kafka.customer_wishlist_topic}", groupId = "customer-wishlist-group")
    public void consume(String message) {
        try {
            LOGGER.info("Consumed message data: " + message);
            JsonObject valueObject = JsonParser.parseString(message).getAsJsonObject();
            LOGGER.info("data to send to braze: " + valueObject);
            if (null != valueObject) {
                String optionId = valueObject.get("optionId") != null ? valueObject.get("optionId").getAsString() : "";
                long count = wishlistService.countWishlistEntitiesByOptionIds(optionId);
                int limit = Constants.getConsulConfigResponse().getBrazeWishlistCount();
                LOGGER.info("Limit Chunk Size : " + limit);
                long skip = 0L;
                LOGGER.info("Limit optionId" + optionId);
                if (StringUtils.isNotBlank(optionId) && StringUtils.isNotEmpty(optionId)) {
                    for (int i = 0; i < count; i += limit) {
                        LOGGER.info("Limit value i : " + limit);
                        BrazeWishlistEventData brazeWishlistEventData = new BrazeWishlistEventData();
                        List<SkuData> finalSkuData = new ArrayList<>();
                        skip = i;
                        LOGGER.info("optionId limit skip: " + optionId + " " + limit + " " + skip);
                        List<WishlistEntity> wishlistEntities = wishlistService.findWishlistEntitiesByOptionIds(optionId, limit, skip);
                        ObjectMapper objectMapper = new ObjectMapper();
                        LOGGER.info("wishlist item  : " + objectMapper.writeValueAsString(wishlistEntities));
                        for (WishlistEntity entity : wishlistEntities) {
                            SkuData skuData = new SkuData();
                            skuData.setExternal_id(entity.getId().toString());
                            skuData.setName("wishlist_price_drop");
                            skuData.setTime(LocalDateTime.now().toString());
                            PropertiesData propertiesData = new PropertiesData();
                            if (null != entity.getWishListItems() && null != entity.getWishListItems().get(0) && entity.getWishListItems().get(0).getCurrency() != null && entity.getWishListItems().get(0).getLastPrice() > 0 && !optionId.isEmpty()) {
                                LOGGER.info("inside  entity  : ");
                                double currentPrice = 0.0;
                                switch (entity.getWishListItems().get(0).getCurrency()) {
                                    case "SAR":
                                        currentPrice = valueObject.get("productPrice_SAR") != null && !valueObject.get("productPrice_SAR").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_SAR").getAsString()) : 0.0;
                                        break;
                                    case "AED":
                                        currentPrice = valueObject.get("productPrice_AED") != null && !valueObject.get("productPrice_AED").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_AED").getAsString()) : 0.0;
                                        break;
                                    case "BHD":
                                        currentPrice = valueObject.get("productPrice_BHD") != null && !valueObject.get("productPrice_BHD").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_BHD").getAsString()) : 0.0;
                                        break;
                                    case "KWD":
                                        currentPrice = valueObject.get("productPrice_KWD") != null && !valueObject.get("productPrice_KWD").getAsString().isEmpty() ? Double.parseDouble(valueObject.get("productPrice_KWD").getAsString()) : 0.0;
                                        break;
                                }
                                LOGGER.info("after  switch  : ");
                                if (currentPrice > 0 && Double.toString(entity.getWishListItems().get(0).getLastPrice()) != null && currentPrice < entity.getWishListItems().get(0).getLastPrice()) {
                                    propertiesData.setOld_price(BigDecimal.valueOf(entity.getWishListItems().get(0).getLastPrice()).setScale(2, RoundingMode.HALF_UP).doubleValue());
                                    propertiesData.setCurrent_price(BigDecimal.valueOf(currentPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
                                    propertiesData.setPriceDiff(BigDecimal.valueOf(entity.getWishListItems().get(0).getLastPrice() - currentPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
                                    propertiesData.setStoreCurrency(entity.getWishListItems().get(0).getCurrency());
                                    double priceDropPercent = (entity.getWishListItems().get(0).getLastPrice() - currentPrice) / entity.getWishListItems().get(0).getLastPrice() * 100;
                                    propertiesData.setDrop_percent(BigDecimal.valueOf(priceDropPercent).setScale(2, RoundingMode.HALF_UP).doubleValue());
                                    propertiesData.setStoreCurrency(entity.getWishListItems().get(0).getCurrency());
                                    if(valueObject.get("productName") != null && !valueObject.get("productName").getAsString().isEmpty()) {
                                        propertiesData.setProduct_name(valueObject.get("productName").getAsString());
                                    }
                                    if(valueObject.get("productImage") != null && !valueObject.get("productImage").getAsString().isEmpty()) {
                                        propertiesData.setImage(valueObject.get("productImage").getAsString());
                                    }
                                    propertiesData.setUser_id(entity.getId().toString());
                                    skuData.setProperties(propertiesData);
                                    finalSkuData.add(skuData);
                                    LOGGER.info("after  add  : ");
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        }
                        LOGGER.info("before brazeWishlistEventData ");
                        brazeWishlistEventData.setEvents(finalSkuData);
                        if (!finalSkuData.isEmpty()) {
                            try {
                                objectMapper = new ObjectMapper();
                                LOGGER.info("Sending request to Braze API: {}", objectMapper.writeValueAsString(brazeWishlistEventData));
                                HttpHeaders headers = new HttpHeaders();
                                headers.setContentType(MediaType.APPLICATION_JSON);
                                headers.setBearerAuth(Constants.getConsulConfigResponse().getBrazeWishlistToken());
                                HttpEntity<BrazeWishlistEventData> request = new HttpEntity<>(brazeWishlistEventData, headers);
                                ResponseEntity<BrazeDetailResponse> response = restTemplate.exchange(Constants.getConsulConfigResponse().getBrazeWishlistUrl(), HttpMethod.POST, request, BrazeDetailResponse.class);
                                LOGGER.info("Braze API response: {}", Objects.requireNonNull(response.getBody()).getMessage());
                            } catch (JsonProcessingException ex) {
                                LOGGER.error("Error processing JSON: {}", ex.getMessage(), ex);
                            } catch (RestClientException ex) {
                                LOGGER.error("Error sending data to Braze API: {}", ex.getMessage(), ex);
                            }
                        } else {
                            LOGGER.info("No events to send to Braze API.");
                        }
                    }
                }
            }
        }catch(Exception e){
            LOGGER.error("Error processing pubsub + " + e);
        }
    }
}
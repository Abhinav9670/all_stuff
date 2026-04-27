package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.OrderPushItem;
import org.styli.services.order.pojo.ShukranLedgerData;
import org.styli.services.order.pojo.eas.EASCoinUpdateResponse;
import org.styli.services.order.pojo.eas.EASKafkaCancelOrder;
import org.styli.services.order.pojo.eas.EASKafkaCustomerDetailSaleOrder;
import org.styli.services.order.pojo.eas.EASKafkaReturnOrder;
import org.styli.services.order.pojo.eas.EASKafkaSaleOrder;
import org.styli.services.order.pojo.eas.EASPartialCancelRefundResponse;
import org.styli.services.order.pojo.eas.EASReturnInitResponse;
import org.styli.services.order.pojo.eas.ReturnProduct;
import org.styli.services.order.pojo.eas.StyliCoinUpdate;
import org.styli.services.order.pojo.OrderPushItem;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.request.LockUnlockHttpRequestBody;
import org.styli.services.order.pojo.response.LockUnlockHttpResponseBody;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.styli.services.order.utility.OrderConstants;

@Service
public class EASServiceImpl {

	private static final Log LOGGER = LogFactory.getLog(EASServiceImpl.class);

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Value("${kafka.topic.eas.spend}")
	private String kafkaTopicEasSpend;

	@Value("${kafka.topic.eas.cancel}")
	private String kafkaTopicEasCancel;

	@Value("${kafka.topic.eas.cancel.partial}")
	private String kafkaTopicEasCancelPartial;

	@Value("${kafka.topic.eas.return}")
	private String kafkaTopicEasReturn;

	@Autowired
	MulinHelper mulinHelper;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	private OrderEntityConverter orderEntityConverter;

	@Autowired
	private SalesOrderRepository salesOrderRepository;
	
	@Autowired
	private SubSalesOrderRepository subSalesOrderRepository;


	@Autowired
	private AmastyRmaRequestRepository amastyRmaRequestRepository;


	@Autowired
	private SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	private ObjectMapper mapper;
	
    @Value("${eas.base.url}")
	public String earnUrl;
    
    @Autowired
    OrderHelper orderHelper;

	@Autowired
	SplitSubSalesOrderRepository splitSubSalesOrderRepository;

	@Transactional
	public void publishSaleOrderToKafka(SalesOrder order) {
		try {
			LOGGER.info("EAS publishSaleOrderToKafka!");

			// Ensure lazy-loaded collections are initialized
			Hibernate.initialize(order.getSalesShipmentTrack());  // Initialize if necessary

			// Call the async method
			this.asyncPublishSaleOrderToKafka(order);

		} catch (Exception e) {
			LOGGER.error("Error initializing sales order collections before async call.", e);
		}
	}

	@Async("asyncExecutor")
	public void asyncPublishSaleOrderToKafka(SalesOrder order) {
		try {
			LOGGER.info("EAS publishSaleOrderToKafka!");
			// only where customer exist and is not guest user
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				LOGGER.info("EAS publishSaleOrderToKafka -2!");
				EASKafkaSaleOrder eASKafkaSaleOrder = new EASKafkaSaleOrder();
				ObjectMapper mapper = new ObjectMapper();
				Map<String, ProductResponseBody> productsFromMulin = mulinHelper
						.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
				OrderResponse orderResponseBody = orderEntityConverter.convertOrder(order, true, mapper,
						order.getStoreId(), productsFromMulin, "", false);

				eASKafkaSaleOrder.setCustomerId(Integer.parseInt(orderResponseBody.getCustomerId()));
				eASKafkaSaleOrder.setStoreId(Integer.parseInt(orderResponseBody.getStoreId()));
				eASKafkaSaleOrder.setOrderId(orderResponseBody.getOrderId());
				eASKafkaSaleOrder.setSpendCoin(orderResponseBody.getSpendCoin());
				eASKafkaSaleOrder.setOtherDetail(orderResponseBody);

				EASKafkaCustomerDetailSaleOrder customerDetail = new EASKafkaCustomerDetailSaleOrder();

				CustomerEntity customerEntity = orderHelper.getCustomerDetails(order.getCustomerId() , null);
				customerDetail.setEmail(customerEntity.getEmail());
				customerDetail.setMobileNumber(customerDetail.getMobileNumber());
				customerDetail.setName(this.getFullName(customerEntity.getFirstName(), customerEntity.getMiddleName(),
						customerEntity.getLastName()));

				eASKafkaSaleOrder.setCustomerDetail(customerDetail);

				kafkaTemplate.send(kafkaTopicEasSpend, eASKafkaSaleOrder);
				LOGGER.info("EAS publishSaleOrderToKafka done!");
			}

		} catch (Exception e) {
			LOGGER.error("Earn Kafka Error publishSaleOrderToKafka.", e);
		}
	}

	@Async("asyncExecutor")
	public void updateShukranLedger(ShukranLedgerData shukranLedgerData) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			headers.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
			headers.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
			String url = earnUrl + "/api/v1/saveLedger";
			HttpEntity<ShukranLedgerData> requestBodyData = new HttpEntity<>(shukranLedgerData, headers);
			LOGGER.info("Update Shukran Ledger Body "+ mapper.writeValueAsString(shukranLedgerData));

			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, requestBodyData, new ParameterizedTypeReference<Map<String, Object>>() {});
			LOGGER.info("Response Of Update Shukran Ledger: "+ response);
		} catch (JsonProcessingException ex) {
			LOGGER.info("Response Of Update Shukran Ledger exception " + ex.getMessage());
		} catch (RestClientException e) {
			LOGGER.info("Exception During Update Shukran Ledger " + e.getMessage());
		}
	}

	@Async("asyncExecutor")
	public void removeCoinManually(CoinAdditionData coinAdditionData, String deviceId) {
		try {
			List<CoinAdditionData> coinAdditionList = new ArrayList<>();
			coinAdditionList.add(coinAdditionData);

			HttpHeaders headers = new HttpHeaders();

			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			headers.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
			headers.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

			if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
				headers.add("device-id", deviceId);
			}
			LOGGER.info("remove Manual Coin Headers" + headers);
			LOGGER.info("remove Manual Coin Body" + coinAdditionList);
			HttpEntity<List<CoinAdditionData>> requestEntity = new HttpEntity<>(coinAdditionList, headers);
			String url = earnUrl + "/api/v1/removeManualCoin";

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

			LOGGER.info("Response of manually adding coin: "+ response);
		} catch (Exception e) {
			LOGGER.error("Error Adding coin manually: {}", e);
		}
	}

	@Async("asyncExecutor")
	public void publishCancelOrderToKafka(SalesOrder order, Double easCoin) {
		try {
			LOGGER.info("EAS publishCancelOrderToKafka!");
			// only where customer exist and is not guest user
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				LOGGER.info("EAS publishCancelOrderToKafka! -2");
				EASKafkaCancelOrder eASKafkaCancelOrder = new EASKafkaCancelOrder();

				eASKafkaCancelOrder.setOrderId(order.getEntityId());
				eASKafkaCancelOrder.setCustomerId(order.getCustomerId());
				eASKafkaCancelOrder.setStoreId(order.getStoreId());
				eASKafkaCancelOrder.setState(order.getState());
				eASKafkaCancelOrder.setStatus(order.getStatus());
				eASKafkaCancelOrder.setSpendCoin(0);
				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
					if(easCoin != null && easCoin>0){
						Integer setFinalCoinValueToBeSubtracted= (int)(easCoin*100);
						LOGGER.info("Kafka setFinalCoinValueToBeSubtracted: " + setFinalCoinValueToBeSubtracted);
						eASKafkaCancelOrder.setSpendCoin(setFinalCoinValueToBeSubtracted);
					}else {
						eASKafkaCancelOrder.setSpendCoin(order.getSubSalesOrder().getEasCoins());
					}
					LOGGER.info("EAS Coins publishCancelOrderToKafka: " + order.getSubSalesOrder().getEasCoins());
				}

				kafkaTemplate.send(kafkaTopicEasCancel, eASKafkaCancelOrder);
				LOGGER.info("EAS publishCancelOrderToKafka done!");
			}

		} catch (Exception e) {
			LOGGER.error("Earn Kafka Error publishCancelOrderToKafka.", e);
		}
	}

	@Async("asyncExecutor")
	public void publishCancelOrderToKafkaForSplitOrder(SplitSalesOrder order, Double easCoin) {
		try {
			LOGGER.info("EAS publishCancelOrderToKafka!");
			// only where customer exist and is not guest user
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				LOGGER.info("EAS publishCancelOrderToKafka! -2");
				EASKafkaCancelOrder eASKafkaCancelOrder = new EASKafkaCancelOrder();

				eASKafkaCancelOrder.setOrderId(order.getEntityId());
				eASKafkaCancelOrder.setCustomerId(order.getCustomerId());
				eASKafkaCancelOrder.setStoreId(order.getStoreId());
				eASKafkaCancelOrder.setState(order.getState());
				eASKafkaCancelOrder.setStatus(order.getStatus());
				eASKafkaCancelOrder.setSpendCoin(0);
				if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getEasCoins()) {
					if(easCoin != null && easCoin>0){
						Integer setFinalCoinValueToBeSubtracted= (int)(easCoin*100);
						LOGGER.info("Kafka setFinalCoinValueToBeSubtracted: " + setFinalCoinValueToBeSubtracted);
						eASKafkaCancelOrder.setSpendCoin(setFinalCoinValueToBeSubtracted);
					}else {
						eASKafkaCancelOrder.setSpendCoin(order.getSplitSubSalesOrder().getEasCoins());
					}
					LOGGER.info("EAS Coins publishCancelOrderToKafka: " + order.getSplitSubSalesOrder().getEasCoins());
				}

				kafkaTemplate.send(kafkaTopicEasCancel, eASKafkaCancelOrder);
				LOGGER.info("EAS publishCancelOrderToKafka done!");
			}

		} catch (Exception e) {
			LOGGER.error("Earn Kafka Error publishCancelOrderToKafka.", e);
		}
	}

	@Async("asyncExecutor")
	public void publishCancelPartialOrderToKafka(SalesOrder order, List<OrderPushItem> orderItems) {
		try {
			LOGGER.info("EAS publishCancelPartialOrderToKafka!");
			// only where customer exist and is not guest user
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				LOGGER.info("EAS publishCancelPartialOrderToKafka! -2");
				EASKafkaReturnOrder eASKafkaReturnOrder = new EASKafkaReturnOrder();

				eASKafkaReturnOrder.setOrderId(order.getEntityId());
				eASKafkaReturnOrder.setCustomerId(order.getCustomerId());
				eASKafkaReturnOrder.setStoreId(order.getStoreId());
				eASKafkaReturnOrder.setSpendCoin(0);
				eASKafkaReturnOrder.setRequestId(0);
				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
					eASKafkaReturnOrder.setSpendCoin(order.getSubSalesOrder().getEasCoins());
					LOGGER.info(
							"EAS Coins publishCancelPartialOrderToKafka: " + order.getSubSalesOrder().getEasCoins());
				}
				List<ReturnProduct> returnProducts = new ArrayList<ReturnProduct>();
				for (OrderPushItem cancelOrderItem : orderItems) {
					if (null != cancelOrderItem.getCancelledQuantity()) {
						if (cancelOrderItem.getCancelledQuantity().intValue() != 0) {
							ReturnProduct returnProduct = new ReturnProduct();
							returnProduct.setSku(cancelOrderItem.getChannelSkuCode());
							returnProduct.setQty(cancelOrderItem.getCancelledQuantity().toString());
							returnProducts.add(returnProduct);
						}
					}
				}
				eASKafkaReturnOrder.setReturnProduct(returnProducts);
				LOGGER.info(eASKafkaReturnOrder);
				LOGGER.info("EAS publishCancelPartialOrderToKafka orderItems:" + orderItems + ", returnProduct: ");
				kafkaTemplate.send(kafkaTopicEasCancelPartial, eASKafkaReturnOrder);
				LOGGER.info("EAS publishCancelPartialOrderToKafka done!");
			}

		} catch (Exception e) {
			LOGGER.error("Earn Kafka Error publishCancelPartialOrderToKafka.", e);
		}
	}
	
	public EASPartialCancelRefundResponse easPartialCancelRefund(SalesOrder order, List<OrderPushItem> orderItems, String deviceId) {
		BigDecimal coinAmountRefunded = BigDecimal.ZERO;
		EASPartialCancelRefundResponse eASPartialCancelRefundResponse = new EASPartialCancelRefundResponse();
		eASPartialCancelRefundResponse.setCoinAmountRefunded(BigDecimal.ZERO);
		eASPartialCancelRefundResponse.setEasValueInBaseCurrency(BigDecimal.ZERO);
		if ((Objects.nonNull(Constants.disabledServices) && Constants.disabledServices.isEarnDisabled())
				&&  (order.getSubSalesOrder().getEasCoins() == 0 || Objects.isNull(order.getSubSalesOrder().getEasCoins()))) {
			return eASPartialCancelRefundResponse;
		}

		try {
			LOGGER.info("EAS easPartialCancelRefund!");
			// only where customer exist and is not guest user
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				SubSalesOrder subSaleOrderObj = order.getSubSalesOrder();
				if(null != subSaleOrderObj.getEasValueInCurrency()) {
					eASPartialCancelRefundResponse.setEasValueInBaseCurrency(subSaleOrderObj.getEasValueInCurrency());
				}
				
				LOGGER.info("EAS easPartialCancelRefund! -2");
				EASKafkaReturnOrder eASPartialCancelOrder = new EASKafkaReturnOrder();

				eASPartialCancelOrder.setOrderId(order.getEntityId());
				eASPartialCancelOrder.setCustomerId(order.getCustomerId());
				eASPartialCancelOrder.setStoreId(order.getStoreId());
				eASPartialCancelOrder.setSpendCoin(0);
				eASPartialCancelOrder.setRequestId(0);
				// returnFee field not set - will be null and excluded from JSON
				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
					eASPartialCancelOrder.setSpendCoin(order.getSubSalesOrder().getEasCoins());
					LOGGER.info("EAS easPartialCancelRefund: " + order.getSubSalesOrder().getEasCoins());
				}
				List<ReturnProduct> cancelProducts = new ArrayList<ReturnProduct>();
				for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {
					if (null != salesOrderItem.getQtyCanceled() && !(salesOrderItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))) {
						if (salesOrderItem.getQtyCanceled().intValue() != 0) {
							ReturnProduct cancelProduct = new ReturnProduct();
							cancelProduct.setSku(salesOrderItem.getSku());
							cancelProduct.setQty(Integer.toString(salesOrderItem.getQtyCanceled().intValue()));
							cancelProducts.add(cancelProduct);
						}
					}
				}
				eASPartialCancelOrder.setReturnProduct(cancelProducts);
				LOGGER.info("EAS easPartialCancelRefund :" + eASPartialCancelOrder);
				
				
				
				
				StyliCoinUpdate styliCoinUpdate;

				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
				requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
				if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
					requestHeaders.add("device-id", deviceId);
				}
				HttpEntity<EASKafkaReturnOrder> requestBody = new HttpEntity<>(eASPartialCancelOrder, requestHeaders);

				String earnPartialCancelUrl = earnUrl + "/api/v1/spendCoinOnCancelOrderPartial";
				LOGGER.info("EAS easPartialCancelRefund url: " + earnPartialCancelUrl);
				LOGGER.info(
						"EAS easPartialCancelRefund request Body:" + mapper.writeValueAsString(requestBody.getBody()));
				ResponseEntity<EASCoinUpdateResponse> response = restTemplate.exchange(earnPartialCancelUrl,
						HttpMethod.POST, requestBody, EASCoinUpdateResponse.class);
				LOGGER.info("EAS easPartialCancelRefund Response: " + response);
				
				if (response.getStatusCode() == HttpStatus.OK) {
					if (response.getBody() != null && response.getBody().getStatus().equals(200)) {
						LOGGER.info("EAS easPartialCancelRefund success. order: " + order.getIncrementId() + ", Code: "
								+ response.getBody().getStatus() + ", Message: " + response.getBody().getMessage()
								+ ", Response: " + response.getBody().getResponse());
						styliCoinUpdate = response.getBody().getResponse();
						
						
						LOGGER.info("EAS easPartialCancelRefund easValueInCurrency: " + subSaleOrderObj.getEasValueInCurrency());
						if(null != subSaleOrderObj.getEasValueInCurrency() && null != styliCoinUpdate.getCoinToCurrency()) {
							LOGGER.info("EAS easPartialCancelRefund oldCoins: " + subSaleOrderObj.getEasCoins() + ", newCoins: " + styliCoinUpdate.getCoin());
							coinAmountRefunded = subSaleOrderObj.getEasValueInCurrency().subtract(styliCoinUpdate.getCoinToCurrency());
							
							subSaleOrderObj.setEasCoins(styliCoinUpdate.getCoin());
							subSaleOrderObj.setEasValueInBaseCurrency(styliCoinUpdate.getBaseCurrencyValue());
					    	subSaleOrderObj.setEasValueInCurrency(styliCoinUpdate.getCoinToCurrency());
					    	subSalesOrderRepository.saveAndFlush(subSaleOrderObj);
					    	
						}
						LOGGER.info("EAS easPartialCancelRefund coinAmountRefunded: " + coinAmountRefunded);
						eASPartialCancelRefundResponse.setCoinAmountRefunded(coinAmountRefunded);
						// return coinAmountRefunded;
					} else {
						LOGGER.error("EAS easPartialCancelRefund failed. order: " + order.getIncrementId() + ", Code: "
								+ response.getBody().getStatus() + ", Message: " + response.getBody().getMessage());

					}
				} else {
					LOGGER.error("EAS easPartialCancelRefund ERROR for order: " + order.getIncrementId() + ", HTTP_CODE: "
							+ response.getStatusCode());
				}
				// kafkaTemplate.send(kafkaTopicEasCancelPartial, eASKafkaReturnOrder);
				LOGGER.info("EAS easPartialCancelRefund done!");
			}

		} catch (Exception e) {
			LOGGER.error("EAS easPartialCancelRefund ERROR", e);
		}
		return eASPartialCancelRefundResponse;
	}

	public EASPartialCancelRefundResponse easSplitPartialCancelRefund(SplitSalesOrder splitSalesOrder, List<OrderPushItem> orderItems, String deviceId) {
		BigDecimal coinAmountRefunded = BigDecimal.ZERO;
		EASPartialCancelRefundResponse eASPartialCancelRefundResponse = new EASPartialCancelRefundResponse();
		eASPartialCancelRefundResponse.setCoinAmountRefunded(BigDecimal.ZERO);
		eASPartialCancelRefundResponse.setEasValueInBaseCurrency(BigDecimal.ZERO);
		if ((Objects.nonNull(Constants.disabledServices) && Constants.disabledServices.isEarnDisabled())
				&&  (splitSalesOrder.getSplitSubSalesOrder().getEasCoins() == 0 || Objects.isNull(splitSalesOrder.getSplitSubSalesOrder().getEasCoins()))) {
			return eASPartialCancelRefundResponse;
		}

		try {
			LOGGER.info("EAS easSplitPartialCancelRefund!");
			// only where customer exist and is not guest user
			if (null != splitSalesOrder.getCustomerId() && splitSalesOrder.getCustomerIsGuest() != 1) {
				SplitSubSalesOrder splitSubSalesOrderObj = splitSalesOrder.getSplitSubSalesOrder();
				if(null != splitSubSalesOrderObj.getEasValueInCurrency()) {
					eASPartialCancelRefundResponse.setEasValueInBaseCurrency(splitSubSalesOrderObj.getEasValueInCurrency());
				}

				LOGGER.info("EAS easSplitPartialCancelRefund! -2");
				EASKafkaReturnOrder eASPartialCancelOrder = new EASKafkaReturnOrder();

				eASPartialCancelOrder.setOrderId(splitSalesOrder.getEntityId());
				eASPartialCancelOrder.setCustomerId(splitSalesOrder.getCustomerId());
				eASPartialCancelOrder.setStoreId(splitSalesOrder.getStoreId());
				eASPartialCancelOrder.setSpendCoin(0);
				eASPartialCancelOrder.setRequestId(0);
				// returnFee field not set - will be null and excluded from JSON
				if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getEasCoins()) {
					eASPartialCancelOrder.setSpendCoin(splitSalesOrder.getSplitSubSalesOrder().getEasCoins());
					LOGGER.info("EAS easSplitPartialCancelRefund: " + splitSalesOrder.getSplitSubSalesOrder().getEasCoins());
				}
				List<ReturnProduct> cancelProducts = new ArrayList<ReturnProduct>();
				for (SplitSalesOrderItem splitSalesOrderItem : splitSalesOrder.getSplitSalesOrderItems()) {
					if (null != splitSalesOrderItem.getQtyCanceled() && !(splitSalesOrderItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))) {
						if (splitSalesOrderItem.getQtyCanceled().intValue() != 0) {
							ReturnProduct cancelProduct = new ReturnProduct();
							cancelProduct.setSku(splitSalesOrderItem.getSku());
							cancelProduct.setQty(Integer.toString(splitSalesOrderItem.getQtyCanceled().intValue()));
							cancelProducts.add(cancelProduct);
						}
					}
				}
				eASPartialCancelOrder.setReturnProduct(cancelProducts);
				LOGGER.info("EAS easPartialCancelRefund :" + eASPartialCancelOrder);




				StyliCoinUpdate styliCoinUpdate;

				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
				requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
				if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
					requestHeaders.add("device-id", deviceId);
				}
				HttpEntity<EASKafkaReturnOrder> requestBody = new HttpEntity<>(eASPartialCancelOrder, requestHeaders);

				String earnPartialCancelUrl = earnUrl + "/api/v1/spendCoinOnCancelOrderPartial";
				LOGGER.info("EAS easSplitPartialCancelRefund url: " + earnPartialCancelUrl);
				LOGGER.info(
						"EAS easSplitPartialCancelRefund request Body:" + mapper.writeValueAsString(requestBody.getBody()));
				ResponseEntity<EASCoinUpdateResponse> response = restTemplate.exchange(earnPartialCancelUrl,
						HttpMethod.POST, requestBody, EASCoinUpdateResponse.class);
				LOGGER.info("EAS easPartialCancelRefund Response: " + response);

				if (response.getStatusCode() == HttpStatus.OK) {
					if (response.getBody() != null && response.getBody().getStatus().equals(200)) {
						LOGGER.info("EAS easSplitPartialCancelRefund success. order: " + splitSalesOrder.getIncrementId() + ", Code: "
								+ response.getBody().getStatus() + ", Message: " + response.getBody().getMessage()
								+ ", Response: " + response.getBody().getResponse());
						styliCoinUpdate = response.getBody().getResponse();


						LOGGER.info("EAS easSplitPartialCancelRefund easValueInCurrency: " +splitSubSalesOrderObj.getEasValueInCurrency());
						if(null != splitSubSalesOrderObj.getEasValueInCurrency() && null != styliCoinUpdate.getCoinToCurrency()) {
							LOGGER.info("EAS easSplitPartialCancelRefund oldCoins: " + splitSubSalesOrderObj.getEasCoins() + ", newCoins: " + styliCoinUpdate.getCoin());
							coinAmountRefunded = splitSubSalesOrderObj.getEasValueInCurrency().subtract(styliCoinUpdate.getCoinToCurrency());

							splitSubSalesOrderObj.setEasCoins(styliCoinUpdate.getCoin());
							splitSubSalesOrderObj.setEasValueInBaseCurrency(styliCoinUpdate.getBaseCurrencyValue());
							splitSubSalesOrderObj.setEasValueInCurrency(styliCoinUpdate.getCoinToCurrency());
							splitSubSalesOrderRepository.saveAndFlush(splitSubSalesOrderObj);

						}
						LOGGER.info("EAS easSplitPartialCancelRefund coinAmountRefunded: " + coinAmountRefunded);
						eASPartialCancelRefundResponse.setCoinAmountRefunded(coinAmountRefunded);
						// return coinAmountRefunded;
					} else {
						LOGGER.error("EAS easSplitPartialCancelRefund failed. order: " + splitSalesOrder.getIncrementId() + ", Code: "
								+ response.getBody().getStatus() + ", Message: " + response.getBody().getMessage());

					}
				} else {
					LOGGER.error("EAS easSplitPartialCancelRefund ERROR for order: " + splitSalesOrder.getIncrementId() + ", HTTP_CODE: "
							+ response.getStatusCode());
				}
				// kafkaTemplate.send(kafkaTopicEasCancelPartial, eASKafkaReturnOrder);
				LOGGER.info("EAS easSplitPartialCancelRefund done!");
			}

		} catch (Exception e) {
			LOGGER.error("EAS easSplitPartialCancelRefund ERROR", e);
		}
		return eASPartialCancelRefundResponse;
	}

	public String getFullName(String firstName, String middleName, String lastName) {
		String fName = firstName != null ? firstName : "";
		String mName = middleName != null ? middleName : "";
		String lName = lastName != null ? lastName : "";
		return (fName + " " + mName + " " + lName).trim();
	}

	@KafkaListener(topics = "${kafka.topic.eas.update.order}", groupId = "${kafka.order.service.group}")
	@Transactional
	public void receiveOrderCoinUpdate(@Payload List<String> messages,
			@Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
			@Header(KafkaHeaders.OFFSET) List<Long> offsets, Acknowledgment acknowledgment) {

		try {
			LOGGER.info("Earn Kafka messages received for Coin UPdate : " + messages);
			long startOffset = offsets.get(0);
			long endOffset = offsets.get(offsets.size() - 1);

			acknowledgment.acknowledge();

			LOGGER.info("Processing kafka messages on startOffset: " + startOffset + " and endOffset: " + endOffset);

			messages.stream().filter(Objects::nonNull).forEach(msg -> {
				try {
					LOGGER.info("Update Coin Details on initiated with message : " + msg);
					StyliCoinUpdate coinUpdateMsg = mapper.readValue(msg, StyliCoinUpdate.class);
					processKafka(coinUpdateMsg);
				} catch (Exception e) {
					LOGGER.error("Error in processing Return Refund. Message : " + msg + " Error: " + e);
				}
			});

		} catch (Exception e) {
			LOGGER.error("Earn Kafka Global catch for kafka. Error " + e);
		}

	}

	@Transactional
	public AmastyRmaRequest processKafka(StyliCoinUpdate messages) {
		LOGGER.info("EAS processKafka!");
		AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRequestId(messages.getRequestId());

		if (Objects.nonNull(rmaRequest) && messages.getCoin() > 0) {
			LOGGER.info("EAS processKafka coins: " + messages.getCoin() + ", CoinToCurrency: "
					+ messages.getCoinToCurrency() + ", BaseCurrencyValue: " + messages.getBaseCurrencyValue());
			rmaRequest.setEasCoins(messages.getCoin());
			rmaRequest.setEasValueInCurrency(messages.getCoinToCurrency());
			rmaRequest.setEasValueInBaseCurrency(messages.getBaseCurrencyValue());
			amastyRmaRequestRepository.saveAndFlush(rmaRequest);
		}
		LOGGER.info("EAS processKafka! getRmaIncId" + rmaRequest.getRmaIncId());
		List<SalesCreditmemo> salesCreditmemos = salesCreditmemoRepository
				.findByRmaNumber(rmaRequest.getRequestId().toString());
		if (!salesCreditmemos.isEmpty()) {
			LOGGER.info("EAS processKafka!");
			for (SalesCreditmemo memo : salesCreditmemos) {
				memo.setEasCoins(messages.getCoin());
				memo.setEasValueInCurrency(messages.getCoinToCurrency());
				memo.setEasValueInBaseCurrency(messages.getBaseCurrencyValue());
				salesCreditmemoRepository.saveAndFlush(memo);
			}
		}
		LOGGER.info("EAS processKafka Updated: " + amastyRmaRequestRepository.findByRequestId(messages.getRequestId()) + ", Up: " + rmaRequest);
		return rmaRequest;
	}

	public void processRTOOrders() {
		LOGGER.info("EAS processRTOOrders process");
		try {
			List<SalesOrder> salesOrders = salesOrderRepository.findRTOOrdersWithCoinsInXhrs(1);
			for (SalesOrder salesOrder : salesOrders) {
				LOGGER.info("EAS RTO OrderID: " + salesOrder.getEntityId().toString() + ", Order IncrementID: "
						+ salesOrder.getIncrementId().toString());
				this.publishCancelOrderToKafka(salesOrder, 0.0);
			}
		} catch (Exception e) {
			LOGGER.error("EAS RTO Error processRTOOrders.", e);
		}
	}

	public AmastyRmaRequest easReturnRefund(SalesOrder order, AmastyRmaRequest rmaRequest, String deviceId, Double returnFee) {
		try {
			LOGGER.info("EAS easReturnRefund!");
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				LOGGER.info("EAS easReturnRefund 2!");
				EASKafkaReturnOrder eASKafkaReturnOrder = new EASKafkaReturnOrder();

				eASKafkaReturnOrder.setOrderId(rmaRequest.getOrderId());
				eASKafkaReturnOrder.setCustomerId(rmaRequest.getCustomerId());
				eASKafkaReturnOrder.setStoreId(rmaRequest.getStoreId());
				eASKafkaReturnOrder.setSpendCoin(0);
				eASKafkaReturnOrder.setRequestId(rmaRequest.getRequestId());
				eASKafkaReturnOrder.setReturnFee(returnFee);
				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
					eASKafkaReturnOrder.setSpendCoin(order.getSubSalesOrder().getEasCoins());
					LOGGER.info("EAS easReturnRefund: " + order.getSubSalesOrder().getEasCoins());
				}
				order.getSalesOrderItem();

				List<ReturnProduct> returnProducts = new ArrayList<ReturnProduct>();
				for (AmastyRmaRequestItem amastyRmaRequestItem : rmaRequest.getAmastyRmaRequestItems()) {
					for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {

						if (salesOrderItem.getItemId() != null) {
							System.out.println(
									salesOrderItem.getItemId() + " : == : " + amastyRmaRequestItem.getOrderItemId());
							if (salesOrderItem.getItemId().equals(amastyRmaRequestItem.getOrderItemId())) {
								ReturnProduct returnProduct = new ReturnProduct();
								returnProduct.setSku(salesOrderItem.getSku());
								returnProduct.setQty(amastyRmaRequestItem.getQty().toString());
								returnProducts.add(returnProduct);
							}
						}
					}
				}
				eASKafkaReturnOrder.setReturnProduct(returnProducts);
				LOGGER.info("EAS easReturnRefund : " + eASKafkaReturnOrder);
				StyliCoinUpdate styliCoinUpdate;

				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
				requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
				if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
					requestHeaders.add("device-id", deviceId);
				}
				HttpEntity<EASKafkaReturnOrder> requestBody = new HttpEntity<>(eASKafkaReturnOrder, requestHeaders);

				String earnReturnRefundUrl = earnUrl + "/api/v1/onReturnPickupSuccess";
				LOGGER.info("EAS easReturnRefund url: " + earnReturnRefundUrl);
				LOGGER.info(
						"EAS easReturnRefund request Body:" + mapper.writeValueAsString(requestBody.getBody()));
				ResponseEntity<EASCoinUpdateResponse> response = restTemplate.exchange(earnReturnRefundUrl,
						HttpMethod.POST, requestBody, EASCoinUpdateResponse.class);
				LOGGER.info(response);
				LOGGER.info(response.getStatusCode());
				// return false;
				if (response.getStatusCode() == HttpStatus.OK) {
					if (response.getBody() != null && response.getBody().getStatus().equals(200)) {
						LOGGER.info("EAS easReturnRefund success. order: " + order.getIncrementId() + ", Code: "
								+ response.getBody().getStatus() + ", Message: " + response.getBody().getMessage()
								+ ", Response: " + response.getBody().getResponse());
						styliCoinUpdate = response.getBody().getResponse();
						AmastyRmaRequest rmaRequestRes = processKafka(styliCoinUpdate);
						LOGGER.info("EAS easReturnRefund Updated_: " + rmaRequest + ", Up_: " + rmaRequestRes);
					} else {
						LOGGER.error("EAS easReturnRefund failed. order: " + order.getIncrementId() + ", Code: "
								+ response.getBody().getStatus() + ", Message: " + response.getBody().getMessage());

					}
				} else {
					LOGGER.error("EAS easReturnRefund ERROR for order: " + order.getIncrementId() + ", HTTP_CODE: "
							+ response.getStatusCode());
				}

				LOGGER.info("EAS easReturnRefund done!");
			}

		} catch (Exception e) {
			LOGGER.error("EAS Error easReturnRefund." + e.getMessage());
		}

		return amastyRmaRequestRepository.findByRequestId(rmaRequest.getRequestId());
	}
	
	public EASReturnInitResponse easReturnRefundInit(SalesOrder order, RMAOrderV2Request rmaRequest, String deviceId) {
		try {
			LOGGER.info("EAS easReturnRefundInit!");
			if (null != order.getCustomerId() && order.getCustomerIsGuest() != 1) {
				LOGGER.info("EAS easReturnInit 2!");
				EASKafkaReturnOrder eASKafkaReturnOrder = new EASKafkaReturnOrder();

				eASKafkaReturnOrder.setOrderId(rmaRequest.getOrderId());
				eASKafkaReturnOrder.setCustomerId(rmaRequest.getCustomerId());
				eASKafkaReturnOrder.setStoreId(rmaRequest.getStoreId());
				eASKafkaReturnOrder.setSpendCoin(0);
				eASKafkaReturnOrder.setRequestId(0);
				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getInitialEasCoins()) {
					eASKafkaReturnOrder.setSpendCoin(order.getSubSalesOrder().getInitialEasCoins());
					LOGGER.info("EAS easReturnInit: " + order.getSubSalesOrder().getInitialEasCoins());
				}
				order.getSalesOrderItem();

				List<ReturnProduct> returnProducts = new ArrayList<ReturnProduct>();
				for (RMAOrderItemV2Request amastyRmaRequestItem : rmaRequest.getItems()) {
					for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {

						if (salesOrderItem.getItemId() != null) {
							if (salesOrderItem.getItemId().equals(amastyRmaRequestItem.getParentOrderItemId())) {
								ReturnProduct returnProduct = new ReturnProduct();
								returnProduct.setSku(salesOrderItem.getSku());
								returnProduct.setQty(amastyRmaRequestItem.getReturnQuantity().toString());
								returnProducts.add(returnProduct);
							}
						}
					}
				}
				eASKafkaReturnOrder.setReturnProduct(returnProducts);
				LOGGER.info("EAS easReturnInit : " + eASKafkaReturnOrder);
				EASReturnInitResponse eASReturnInitResponse = new EASReturnInitResponse();

				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
				requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
				if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
					requestHeaders.add("device-id", deviceId);
				}
				HttpEntity<EASKafkaReturnOrder> requestBody = new HttpEntity<>(eASKafkaReturnOrder, requestHeaders);

				String earnReturnInitUrl = earnUrl + "/api/v1/onReturnInit";
				LOGGER.info("EAS easReturnInit url: " + earnReturnInitUrl);
				LOGGER.info(
						"EAS easReturnRefund request Body:" + mapper.writeValueAsString(requestBody.getBody()));
				ResponseEntity<Object> response = restTemplate.exchange(earnReturnInitUrl,
						HttpMethod.POST, requestBody, Object.class);
				LOGGER.info(response);
				LOGGER.info(response.getStatusCode());
				// return false;
				if (response.getStatusCode() == HttpStatus.OK) {
					JsonObject jsonObj = JsonParser.parseString(response.getBody().toString()).getAsJsonObject();			
					String currencyValue = jsonObj.get("storeCurrency").getAsString() + " "+ jsonObj.get("coinValueInCurrency").getAsString();
					
					eASReturnInitResponse.setCoinValueInCurrency(jsonObj.get("coinValueInCurrency").getAsBigDecimal());
					eASReturnInitResponse.setCoinValueInCurrencyLabel(currencyValue);
					eASReturnInitResponse.setReturnCoin(jsonObj.get("returnCoin").getAsInt());
					return eASReturnInitResponse;
				} else {
					LOGGER.error("EAS easReturnInit ERROR for order: " + order.getIncrementId() + ", HTTP_CODE: "
							+ response.getStatusCode());
				}

				LOGGER.info("EAS easReturnInit fetch coin done!");
			}

		} catch (Exception e) {
			LOGGER.error("EAS Error easReturnInit." + e.getMessage());
		}

		return null;
	}

	public String getAuthorization(String authToken) {
		String token = null;
		if (StringUtils.isNotEmpty(authToken)) {
			if (authToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(authToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList)) {
					token = authTokenList.get(0);
				}
			}
		}
		return token;
	}

	/**
	 * Common method for handling Styli coins refund on seller cancellation
	 * @param orderId Order ID for logging
	 * @param customerId Customer ID
	 * @param customerIsGuest Guest customer flag
	 * @param store Store object
	 * @param orderItems List of cancelled order items
	 * @param deviceId Device ID
	 * @param isSplitOrder Whether it's a split order
	 * @param splitSalesOrder Split sales order (null for regular orders)
	 * @param salesOrder Regular sales order (null for split orders)
	 * @return EASPartialCancelRefundResponse
	 */
	public EASPartialCancelRefundResponse handleSellerCancellationStyliCoinsRefund(
			String orderId, Integer customerId, Integer customerIsGuest, Stores store, 
			List<OrderPushItem> orderItems, String deviceId, boolean isSplitOrder,
			SplitSalesOrder splitSalesOrder, SalesOrder salesOrder) {
		
		EASPartialCancelRefundResponse response = new EASPartialCancelRefundResponse();
		response.setCoinAmountRefunded(BigDecimal.ZERO);
		
		try {
			// Check if customer exists and is not guest
			if (customerId == null || customerIsGuest == 1) {
				LOGGER.info("EAS seller cancellation skipped - Customer is guest or null for order: " + orderId);
				return response;
			}
			
			// Check if EAS is enabled and not blocked
			if (store == null || store.getIsShukranEnable() || 
				(Constants.disabledServices != null && Constants.disabledServices.isEarnDisabled())) {
				LOGGER.info("EAS service disabled or Shukran enabled for seller cancellation - Order: " + orderId);
				return response;
			}
			
			LOGGER.info("Calling EAS service for seller cancellation - Order: " + orderId);
			
			// Call appropriate EAS method based on order type
			if (isSplitOrder && splitSalesOrder != null) {
				response = easSplitPartialCancelRefund(splitSalesOrder, orderItems, deviceId);
			} else if (!isSplitOrder && salesOrder != null) {
				response = easPartialCancelRefund(salesOrder, orderItems, deviceId);
			} else {
				LOGGER.warn("Invalid order type or null order for seller cancellation - Order: " + orderId);
				return response;
			}
			
			// Log the result
			if (response != null && response.getCoinAmountRefunded() != null && 
				response.getCoinAmountRefunded().compareTo(BigDecimal.ZERO) > 0) {
				LOGGER.info("EAS seller cancellation success. Order: " + orderId + 
						   ", Coins refunded: " + response.getCoinAmountRefunded());
			} else {
				LOGGER.warn("EAS seller cancellation completed but no coins were refunded for order: " + orderId);
			}
			
		} catch (Exception e) {
			LOGGER.error("EAS Error in seller cancellation for order: " + orderId + ", Error: " + e.getMessage());
			// Don't fail the cancellation if EAS service fails
		}
		
		return response;
	}
}

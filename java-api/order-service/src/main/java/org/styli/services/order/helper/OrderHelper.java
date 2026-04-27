package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.CoreConfigData;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.sequence.*;
import org.styli.services.order.model.sales.RtoAutoRefund;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.model.sales.SplitSellerOrder;
import org.styli.services.order.model.sales.SplitSellerOrderItem;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.pojo.GiftOption;
import org.styli.services.order.pojo.OrderPushItem;
import org.styli.services.order.pojo.OrderunfulfilmentRequest;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.order.OTSOrderRequest;
import org.styli.services.order.pojo.order.SellerStatusMessage;
import org.styli.services.order.pojo.order.SkuItem;
import org.styli.services.order.pojo.order.StatusMessage;
import org.styli.services.order.pojo.request.BlockInventory;
import org.styli.services.order.pojo.request.CustomerRequestBody;
import org.styli.services.order.pojo.request.InventoryRequest;
import org.styli.services.order.pojo.request.OtsTrackingRequest;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.PaymentInformationCOD;
import org.styli.services.order.pojo.request.RequestBody;
import org.styli.services.order.pojo.request.UpdateOrderRequest;
import org.styli.services.order.pojo.response.CustomerUpdateProfileResponse;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.OrderStatusResponse;
import org.styli.services.order.pojo.response.OtsTrackingResponse;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.Rma.sequence.*;
import org.styli.services.order.repository.SalesOrder.RtoAutoRefundRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderPaymentRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesPaymentTransactionRepository;
import org.styli.services.order.repository.SalesOrder.SplitSellerOrderRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.service.impl.PubSubServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
@Scope("singleton")
public class OrderHelper {

	private static final String V1_REST_ORDERSMS = "/v1/rest/ordersms";

	private static final Log LOGGER = LogFactory.getLog(OrderHelper.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderAddressRepository salesOrderAddressRepository;

	@Autowired
	SalesOrderItemRepository salesOrderItemRepository;

	@Autowired
	SalesOrderPaymentRepository salesOrderPaymentRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SequenceOrderOneRepository sequenceOrderOneRepository;

	@Autowired
	SequenceOrderThreeRepository sequenceOrderThreeRepository;

	@Autowired
	SequenceOrderSevenRepository sequenceOrderSevenRepository;

	@Autowired
	SequenceOrderElevenRepository sequenceOrderElevenRepository;

	@Autowired
	SequenceOrderTwelveRepository sequenceOrderTwelveRepository;

	@Autowired
	SequenceOrderThirteenRepository sequenceOrderThirteenRepository;

	@Autowired
	SequenceOrderFifteenRepository sequenceOrderFifteenRepository;

	@Autowired
	SequenceOrderSeventeenRepository sequenceOrderSeventeenRepository;

	@Autowired
	SequenceOrderNineteenRepository sequenceOrderNineteenRepository;

	@Autowired
	SequenceOrderFiftyOneRepository sequenceOrderFiftyOneRepository;

	@Autowired
	SequenceOrderTwentyOneRepository sequenceOrderTwentyOneRepository;

	@Autowired
	SequenceOrderTwentyThreeRepository sequenceOrderTwentyThreeRepository;

	@Autowired
	SequenceOrderTwentyFiveRepository sequenceOrderTwentyFiveRepository;

	@Autowired
	SalesPaymentTransactionRepository salesPaymentTransactionRepository;

	@Autowired
	CoreConfigDataRepository coreConfigDataRepository;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	RtoAutoRefundRepository rtoAutoRefundRepository;

	@Autowired
	SplitSellerOrderRepository splitSellerOrderRepository;

	@Autowired
	SalesOrderServiceV3 salesOrderServiceV3;

	@Autowired
	OrderpushHelper orderpushHelper;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	SplitOrderpushHelper splitOrderpushHelper;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${promo.coupon.promo.url}")
	private String couponRedeemUrl;

	@Value("${env}")
	private String env;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	StatusChaneHistoryRepository statusChaneHistoryRepository;

	@Autowired
	PubSubServiceImpl pubSubServiceImpl;

	@Value("${pubsub.topic.split.order.tracking}")
	private String splitOrderTrackingTopic;

	public String generateIncrementId(Integer newSequenceValue, Integer storeId) {

		Integer incrementStartValue = 1;
		int incrementStepValue = 1;

		String storeIdStr = storeId == 1 ? "" : String.valueOf(storeId);

		return storeIdStr + String.format(OrderConstants.INCREMENT_PADDING,
				((newSequenceValue - incrementStartValue) * incrementStepValue + incrementStartValue));
	}

	public String getIncrementId(Integer storeId) {

		String incrementId = null;

		try {

			switch (storeId) {
			// Store id - 3
			case OrderConstants.STORE_ID_SA_AR: {
				SequenceOrderThree sequenceOrderNew = new SequenceOrderThree();
				sequenceOrderThreeRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_3 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}
			// Store id - 7
			case OrderConstants.STORE_ID_AE_EN: {
				SequenceOrderSeven sequenceOrderNew = new SequenceOrderSeven();
				sequenceOrderSevenRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_7 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}
			// Store id - 11
			case OrderConstants.STORE_ID_AE_AR: {
				SequenceOrderEleven sequenceOrderNew = new SequenceOrderEleven();
				sequenceOrderElevenRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_11 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}

			// Store id - 12
			case OrderConstants.STORE_ID_KW_EN: {
				SequenceOrderTwelve sequenceOrderNew = new SequenceOrderTwelve();
				sequenceOrderTwelveRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_12 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}
			// Store id - 13
			case OrderConstants.STORE_ID_KW_AR: {
				SequenceOrderThirteen sequenceOrderNew = new SequenceOrderThirteen();
				sequenceOrderThirteenRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_13 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}
			// Store id - 15
			case OrderConstants.STORE_ID_QA_EN: {
				SequenceOrderFifteen sequenceOrderNew = new SequenceOrderFifteen();
				sequenceOrderFifteenRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_16 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}

			// Store id - 17
			case OrderConstants.STORE_ID_QA_AR: {
				SequenceOrderSeventeen sequenceOrderNew = new SequenceOrderSeventeen();
				sequenceOrderSeventeenRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_18 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}

			// Store id - 19
			case OrderConstants.STORE_ID_BH_EN: {
				SequenceOrderNineteen sequenceOrderNew = new SequenceOrderNineteen();
				sequenceOrderNineteenRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_20 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}

			// Store id - 21
			case OrderConstants.STORE_ID_BH_AR: {
				SequenceOrderTwentyOne sequenceOrderNew = new SequenceOrderTwentyOne();
				sequenceOrderTwentyOneRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_22 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}

			// Store id - 23
				case OrderConstants.STORE_ID_OM_EN: {
					SequenceOrderTwentyThree sequenceOrderNew = new SequenceOrderTwentyThree();
					sequenceOrderTwentyThreeRepository.saveAndFlush(sequenceOrderNew);
					LOGGER.info("Increment ID creted in sequence_order_23 table!");
					Integer sequenceValue = sequenceOrderNew.getSequenceValue();
					incrementId = generateIncrementId(sequenceValue, storeId);
					break;
				}

				// Store id - 25
				case OrderConstants.STORE_ID_OM_AR: {
					SequenceOrderTwentyFive sequenceOrderNew = new SequenceOrderTwentyFive();
					sequenceOrderTwentyFiveRepository.saveAndFlush(sequenceOrderNew);
					LOGGER.info("Increment ID creted in sequence_order_25 table!");
					Integer sequenceValue = sequenceOrderNew.getSequenceValue();
					incrementId = generateIncrementId(sequenceValue, storeId);
					break;
				}

			// Store id - 51
			case OrderConstants.STORE_ID_IN_EN: {
				SequenceOrderFiftyOne sequenceOrderNew = new SequenceOrderFiftyOne();
				sequenceOrderFiftyOneRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_51 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}
			default: {
				SequenceOrderOne sequenceOrderNew = new SequenceOrderOne();
				sequenceOrderOneRepository.saveAndFlush(sequenceOrderNew);
				LOGGER.info("Increment ID creted in sequence_order_1 table!");
				Integer sequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(sequenceValue, storeId);
				break;
			}
			}

		} catch (DataAccessException daException) {

			LOGGER.error("Could not create increment ID. storeId: " + storeId);
			LOGGER.error("Excpetion::" + daException.getMessage());
		}

		return incrementId;

	}

	public void updateOrderStatusHistory(UpdateOrderRequest request, SalesOrder order) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();

		sh.setParentId(order.getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(0);
		sh.setComment("Order updated with message: " + request.getMessage());
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setEntityName(Constants.ORDER_ENTITY);

		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}

	public void createOrderStatusHistory(SalesOrder order, String commentMessage) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();

		sh.setParentId(order.getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(0);
		if (commentMessage != null)
			sh.setComment(commentMessage);
		else
			sh.setComment("Order has been created!");
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setEntityName(Constants.ORDER_ENTITY);

		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}

	public void createOrderPayment(QuoteDTO quoteObject, SalesOrder order, String paymentMethod) {
		SalesOrderPayment payment = new SalesOrderPayment();

		payment.setShippingAmount(getShippingAmount(quoteObject));
		payment.setBaseShippingAmount(getShippingAmount(quoteObject));

		payment.setBaseAmountPaid(null);
		payment.setAmountPaid(null);
		payment.setBaseAmountOrdered(getBaseGrandTotal(quoteObject));
		payment.setAmountOrdered(getBaseGrandTotal(quoteObject));
		payment.setMethod(paymentMethod);

		String additionalInformation = "";
		try {

			String methodTitle = getMethodTitle(order.getStoreId(), paymentMethod);
			String methodInstructions = getMethodInstructions(order.getStoreId(), paymentMethod);

			PaymentInformationCOD information = new PaymentInformationCOD();
			information.setMethodTitle(methodTitle);
			information.setInstructions(methodInstructions);
			additionalInformation = mapper.writeValueAsString(information);
		} catch (JsonProcessingException e) {
			LOGGER.error(
					"createOrderPayment. Could not create payment information. IncrementId: " + order.getIncrementId());
		}
		payment.setAdditionalInformation(additionalInformation);

		order.addOrderPayment(payment);
	}

	public String getMethodInstructions(Integer storeId, String paymentMethod) {
		String methodInstructions = null;
		try {
			String configPath = "payment" + "/" + paymentMethod + "/" + "instructions";
			CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, storeId);
			if (coreConfigData == null) {
				coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, Constants.ADMIN_STORE_ID);
			}
			if (coreConfigData != null)
				methodInstructions = coreConfigData.getValue();
		} catch (Exception e) {
			LOGGER.error("Error in Method Ins : " + e);
		}
		return methodInstructions;
	}

	public String getMethodTitle(Integer storeId, String paymentMethod) {
		String methodTitle = null;
		try {
			String configPath = "payment" + "/" + paymentMethod + "/" + "title";
			CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, storeId);
			if (coreConfigData == null) {
				coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, Constants.ADMIN_STORE_ID);
			}
			if (coreConfigData != null)
				methodTitle = coreConfigData.getValue();
		} catch (Exception e) {
			LOGGER.error("Error in Method title : " + e);
		}
		return methodTitle;
	}

	private BigDecimal getBaseGrandTotal(QuoteDTO quoteObject) {
		return quoteObject.getBaseGrandTotal() == null ? new BigDecimal(0)
				: new BigDecimal(quoteObject.getBaseGrandTotal());
	}

	private BigDecimal getShippingAmount(QuoteDTO quoteObject) {
		return quoteObject.getShippingAmount() == null ? new BigDecimal(0)
				: new BigDecimal(quoteObject.getShippingAmount());
	}

	/**
	 * @param customerEmail email
	 * @return boolean
	 */
	public boolean checkFirstOrderByCustomer(String customerEmail) {

		boolean firstOrder = false;

		try {
			List<SalesOrder> orders = salesOrderRepository.findByCustomerEmail(customerEmail);
			List<SalesOrder> filteredOrders = orders.stream()
					.filter(el -> el.getStatus() != null && !el.getStatus().equals(OrderConstants.CANCELED_ORDER_STATUS)
							&& !el.getStatus().equals(OrderConstants.FAILED_ORDER_STATUS)
							&& !el.getStatus().equals(OrderConstants.CANCELED_ORDER_STATE))
					.collect(Collectors.toList());

			if (CollectionUtils.isNotEmpty(filteredOrders)) {
				firstOrder = filteredOrders.size() == 1;
			}
		} catch (DataAccessException e) {
			LOGGER.error("could not fetch orders for flag firstOrderByCustomer");
		}

		return firstOrder;
	}

	/**
	 * @param customerEmail email
	 * @return boolean
	 */
	public boolean checkFirstCreateOrder(String customerEmail) {

		boolean firstOrder = false;

		try {
//			List<SalesOrder> filteredOrders = salesOrderRepository.findFirstCreateOrder(customerEmail,
//					OrderConstants.CANCELED_ORDER_STATUS, OrderConstants.FAILED_ORDER_STATUS,
//					OrderConstants.CANCELED_ORDER_STATE);

			int countData = salesOrderRepository.countCreateOrders(customerEmail,
					OrderConstants.CANCELED_ORDER_STATUS, OrderConstants.FAILED_ORDER_STATUS,
					OrderConstants.CANCELED_ORDER_STATE);

			if (countData <= 0) {
				firstOrder = true;
			}
		} catch (DataAccessException e) {
			LOGGER.error("could not fetch orders for flag firstOrderByCustomer");
		}
		return firstOrder;
	}

	/**
	 * @param quote
	 * @param order
	 */
	public void blockInventory(SalesOrder order) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
		String url = "";
		InventoryRequest payload = setBlockInventoryRequest(order);

		payload.setIncrementId(order.getIncrementId());
		try {

			HttpEntity<InventoryRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

			if (null != Constants.orderCredentials
					&& null != Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
				url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()
						+ "/api/inventory/storefront/block";

			}

			LOGGER.info("inventory block request url:" + url);
			LOGGER.info("inventory block request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<InventoryBlockResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					InventoryBlockResponse.class);

			LOGGER.info("block inventory response body" + mapper.writeValueAsString(response.getBody()));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("inventory block for:" + mapper.writeValueAsString(requestBody));

			}

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("exception occoured during block inventory:" + e.getMessage());
		}
	}

	/**
	 * @param quote
	 * @param order
	 */
	public void releaseInventoryQty(SalesOrder order, Map<String, BigDecimal> skuMapList, boolean updateQty,
			String releaseType) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
		String url = "";
		InventoryRequest payload = null;
		LOGGER.info("release before retry payment:" + order.getRetryPayment() + "order id:"+order.getIncrementId());

			try {
				if (MapUtils.isNotEmpty(skuMapList)) {

					payload = setCancelledInventoryRequest(order, skuMapList);
				} else {

					payload = setInventoryRequest(order);

				}
				payload.setIncrementId(order.getIncrementId());
				payload.setUpdateQty(updateQty);
				payload.setReleaseType(releaseType);
				// Don't send warehouseId, as inventory is blocked based on sku,sku level warehouseid and storeId
				/*if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getWarehouseLocationId()) {
					payload.setWarehouseId(order.getSubSalesOrder().getWarehouseLocationId().toString());
				}*/
				HttpEntity<InventoryRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
				if (null != Constants.orderCredentials
						&& null != Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
					url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()
							+ "/api/inventory/storefront/release";

				}

				LOGGER.info("release inventory URl:" + url);
				LOGGER.info(" Inventory release request body" + mapper.writeValueAsString(requestBody));

				ResponseEntity<InventoryBlockResponse> response = restTemplate.exchange(url, HttpMethod.POST,
						requestBody, InventoryBlockResponse.class);

				LOGGER.info("response inventory release Body" + mapper.writeValueAsString(response.getBody()));
				if (response.getStatusCode() == HttpStatus.OK) {

					LOGGER.info("inventory release for:" + mapper.writeValueAsString(requestBody));

				}

			} catch (RestClientException | JsonProcessingException e) {

				LOGGER.error("exception occoured during release inventory:" + e.getMessage());
			}
		
	}

	public void releaseInventoryQtyForSplitOrder(SplitSalesOrder order, Map<String, BigDecimal> skuMapList, boolean updateQty,
			String releaseType) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
		String url = "";
		InventoryRequest payload = null;
		LOGGER.info("release before retry payment:" + order.getRetryPayment() + "order id:"+order.getIncrementId());

			try {
				if (MapUtils.isNotEmpty(skuMapList)) {

					payload = setCancelledInventoryRequestForSplitOrder(order, skuMapList);
				} else {

					payload = setInventoryRequestForSplitOrder(order);

				}
				payload.setIncrementId(order.getIncrementId());
				payload.setUpdateQty(updateQty);
				payload.setReleaseType(releaseType);
				// Don't send warehouseId, as inventory is blocked based on sku, sku level warehouseid and storeId
				/*if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getWarehouseLocationId()) {
					payload.setWarehouseId(order.getSplitSubSalesOrder().getWarehouseLocationId().toString());
				}*/
				HttpEntity<InventoryRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
				if (null != Constants.orderCredentials
						&& null != Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
					url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()
							+ "/api/inventory/storefront/release";

				}

				LOGGER.info("release inventory URl:" + url);
				LOGGER.info(" Inventory release request body" + mapper.writeValueAsString(requestBody));

				ResponseEntity<InventoryBlockResponse> response = restTemplate.exchange(url, HttpMethod.POST,
						requestBody, InventoryBlockResponse.class);

				LOGGER.info("response inventory release Body" + mapper.writeValueAsString(response.getBody()));
				if (response.getStatusCode() == HttpStatus.OK) {

					LOGGER.info("inventory release for:" + mapper.writeValueAsString(requestBody));

				}

			} catch (RestClientException | JsonProcessingException e) {

				LOGGER.error("exception occoured during release inventory:" + e.getMessage());
			}

	}

	public OrderStatusResponse fetchOrderStatusOMS(Integer parentOrderId) {
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
		String url = "";
		OtsTrackingRequest payload = new OtsTrackingRequest();
		try {
			payload.setParentOrderId(parentOrderId);

			HttpEntity<OtsTrackingRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
				if (null != Constants.orderCredentials
						&& null != Constants.orderCredentials.getOrderDetails().getOtsServiceBaseUrl()) {
					url = Constants.orderCredentials.getOrderDetails().getOtsServiceBaseUrl() + "/v1/orderDetailsOMS";
				}

				LOGGER.info("OTS tracking URl:" + url);
				LOGGER.info(" OTS tracking request body" + mapper.writeValueAsString(requestBody));

				ResponseEntity<OrderStatusResponse> response = restTemplate.exchange(url, HttpMethod.POST,
						requestBody, OrderStatusResponse.class);

				return response.getBody();

            } catch (RestClientException | JsonProcessingException e) {

                LOGGER.error("Exception occurred while fetching order status from OTS for parentOrderId " + payload.getParentOrderId(), e);

            }
			return null;
	}


	/**
	 * @param quote
	 * @param order
	 */
	public void updateStatusHistory(SalesOrder order, boolean isOrder, boolean isProcessing, boolean isPendingPayment,
			boolean isCancel, boolean isPacked) {

		try {
		StatusChangeHistory statusChangeHistory = null;
		
		List<StatusChangeHistory> statusChangeHistoryList = statusChaneHistoryRepository.findByOrderId(order.getEntityId().toString());
		
		if (isOrder && CollectionUtils.isEmpty(statusChangeHistoryList)) {
			statusChangeHistory = new StatusChangeHistory();
			statusChangeHistory.setOrderId(order.getEntityId().toString());
			statusChangeHistory.setOrderIncrementId(order.getIncrementId());
		} else if (CollectionUtils.isNotEmpty(statusChangeHistoryList)){
			statusChangeHistory = statusChangeHistoryList.get(0);
		} else {
			statusChangeHistory = new StatusChangeHistory();
			statusChangeHistory.setOrderId(order.getEntityId().toString());
			statusChangeHistory.setOrderIncrementId(order.getIncrementId());
		}

		if (null != statusChangeHistory) {

			if (isOrder) {

				statusChangeHistory.setCreateAt(new Timestamp(new Date().getTime()));
				statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
			}
			if (isProcessing) {
				statusChangeHistory.setProcessingDate(new Timestamp(new Date().getTime()));
				statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

			}
			if (isPendingPayment) {
				statusChangeHistory.setPendingPaymentDate(new Timestamp(new Date().getTime()));
				statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

			}
			if (isCancel) {
				statusChangeHistory.setCancelDate(new Timestamp(new Date().getTime()));
				statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

			}
			if (isPacked) {
				statusChangeHistory.setPackedDate(new Timestamp(new Date().getTime()));
				statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
			}
			statusChaneHistoryRepository.saveAndFlush(statusChangeHistory);

		}
		}catch(Exception ex) {
			
			LOGGER.error("exception occoured during update in history:"+ex.getMessage());
		}

	}

	public void updateSplitStatusHistory(SplitSalesOrder splitSalesOrder, boolean isOrder, boolean isProcessing, boolean isPendingPayment,
									boolean isCancel, boolean isPacked) {

		try {
			StatusChangeHistory statusChangeHistory = null;

			statusChangeHistory = statusChaneHistoryRepository.findBySplitOrderIncrementId(splitSalesOrder.getIncrementId());

			if (null == statusChangeHistory) {
				statusChangeHistory = new StatusChangeHistory();
				statusChangeHistory.setOrderId(splitSalesOrder.getSalesOrder().getEntityId().toString());
				statusChangeHistory.setOrderIncrementId(splitSalesOrder.getSalesOrder().getIncrementId());
				statusChangeHistory.setSplitOrderId(splitSalesOrder.getEntityId());
				statusChangeHistory.setSplitOrderIncrementId(splitSalesOrder.getIncrementId());
			}

			if (null != statusChangeHistory) {

				if (isOrder) {

					statusChangeHistory.setCreateAt(new Timestamp(new Date().getTime()));
					statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
				}
				if (isProcessing) {
					statusChangeHistory.setProcessingDate(new Timestamp(new Date().getTime()));
					statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

				}
				if (isPendingPayment) {
					statusChangeHistory.setPendingPaymentDate(new Timestamp(new Date().getTime()));
					statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

				}
				if (isCancel) {
					statusChangeHistory.setCancelDate(new Timestamp(new Date().getTime()));
					statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

				}
				if (isPacked) {
					statusChangeHistory.setPackedDate(new Timestamp(new Date().getTime()));
					statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
				}
				statusChaneHistoryRepository.saveAndFlush(statusChangeHistory);

			}
		}catch(Exception ex) {

			LOGGER.error("exception occoured during update in history:"+ex.getMessage());
		}

	}


	/**
	 * @param order
	 * @return
	 */
	/**
	 * @param order
	 * @return
	 */
	private InventoryRequest setInventoryRequest(SalesOrder order) {

		InventoryRequest request = new InventoryRequest();

		List<BlockInventory> inventories = new ArrayList<>();
		for (SalesOrderItem salesItem : order.getSalesOrderItem()) {

			if (!(salesItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					&& null != salesItem.getQtyCanceled() && null != salesItem.getQtyOrdered()) {

				BlockInventory inventory = new BlockInventory();

				inventory.setChannelSkuCode(salesItem.getSku());
				if (null != salesItem.getQtyCanceled()
						&& salesItem.getQtyCanceled().compareTo(salesItem.getQtyOrdered()) != 0) {

					BigDecimal qytCancelled = salesItem.getQtyCanceled();
					BigDecimal qtyOrdered = salesItem.getQtyOrdered();
					BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);
					inventory.setQuantity(actualQty.toString());

				} else if (null != salesItem.getQtyOrdered()) {
					inventory.setQuantity(salesItem.getQtyOrdered().toString());
				}
				//set warehouseId based on sku level warehouseId
				if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
					inventory.setWarehouseId(salesItem.getWarehouseLocationId());
				} else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getWarehouseLocationId()) {
					inventory.setWarehouseId(order.getSubSalesOrder().getWarehouseLocationId().toString());
				}
				inventories.add(inventory);

			} else if (salesItem.getQtyCanceled() == null) {

				BlockInventory inventory = new BlockInventory();

				inventory.setChannelSkuCode(salesItem.getSku());

				inventory.setQuantity(salesItem.getQtyOrdered().toString());
				//set warehouseId based on sku level warehouseId
				if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
					inventory.setWarehouseId(salesItem.getWarehouseLocationId());
				} else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getWarehouseLocationId()) {
					inventory.setWarehouseId(order.getSubSalesOrder().getWarehouseLocationId().toString());
				}
				inventories.add(inventory);
			}
		}
		request.setStoreId(order.getStoreId());
		request.setInventories(inventories);

		return request;

	}

	private InventoryRequest setInventoryRequestForSplitOrder(SplitSalesOrder order) {

		InventoryRequest request = new InventoryRequest();

		List<BlockInventory> inventories = new ArrayList<>();
		for (SplitSalesOrderItem salesItem : order.getSplitSalesOrderItems()) {

			if (!(salesItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					&& null != salesItem.getQtyCanceled() && null != salesItem.getQtyOrdered()) {

				BlockInventory inventory = new BlockInventory();

				inventory.setChannelSkuCode(salesItem.getSku());
				if (null != salesItem.getQtyCanceled()
						&& salesItem.getQtyCanceled().compareTo(salesItem.getQtyOrdered()) != 0) {

					BigDecimal qytCancelled = salesItem.getQtyCanceled();
					BigDecimal qtyOrdered = salesItem.getQtyOrdered();
					BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);
					inventory.setQuantity(actualQty.toString());

				} else if (null != salesItem.getQtyOrdered()) {
					inventory.setQuantity(salesItem.getQtyOrdered().toString());
				}
				//set warehouseId based on sku level warehouseId
				if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
					inventory.setWarehouseId(salesItem.getWarehouseLocationId());
				} else if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getWarehouseLocationId()) {
					inventory.setWarehouseId(order.getSplitSubSalesOrder().getWarehouseLocationId().toString());
				}
				inventories.add(inventory);

			} else if (salesItem.getQtyCanceled() == null) {

				BlockInventory inventory = new BlockInventory();

				inventory.setChannelSkuCode(salesItem.getSku());

				inventory.setQuantity(salesItem.getQtyOrdered().toString());
				//set warehouseId based on sku level warehouseId
				if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
					inventory.setWarehouseId(salesItem.getWarehouseLocationId());
				} else if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getWarehouseLocationId()) {
					inventory.setWarehouseId(order.getSplitSubSalesOrder().getWarehouseLocationId().toString());
				}
				inventories.add(inventory);
			}
		}
		request.setStoreId(order.getStoreId());
		request.setInventories(inventories);

		return request;

	}

	/**
	 * @param order
	 * @return
	 */
	private InventoryRequest setBlockInventoryRequest(SalesOrder order) {

		InventoryRequest request = new InventoryRequest();

		List<BlockInventory> inventories = new ArrayList<>();
		for (SalesOrderItem salesItem : order.getSalesOrderItem()) {

			if (!salesItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {

				BlockInventory inventory = new BlockInventory();

				inventory.setChannelSkuCode(salesItem.getSku());
				if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
					inventory.setWarehouseId(salesItem.getWarehouseLocationId());
				} else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getWarehouseLocationId()) {
					inventory.setWarehouseId(order.getSubSalesOrder().getWarehouseLocationId().toString());
				}
				if (null != salesItem.getQtyOrdered()) {
					inventory.setQuantity(salesItem.getQtyOrdered().toString());

					inventories.add(inventory);
				}

				request.setStoreId(order.getStoreId());

			}
		}
		request.setInventories(inventories);

		return request;

	}

	/**
	 * @param order
	 * @return
	 */
	/**
	 * @param order
	 * @param mapSkuList
	 * @return
	 */
	private InventoryRequest setCancelledInventoryRequest(SalesOrder order, Map<String, BigDecimal> mapSkuList) {

		InventoryRequest request = new InventoryRequest();

		List<BlockInventory> inventories = new ArrayList<>();

		for (Map.Entry<String, BigDecimal> mapEntrySet : mapSkuList.entrySet()) {

			BlockInventory inventory = new BlockInventory();
			//Set warehouseId based on sku level warehouseId
			String warehouseId = order.getSalesOrderItem().stream()
					.filter(e ->!e.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.filter(e -> e.getSku() != null && e.getSku().equals(mapEntrySet.getKey()))
					.map(SalesOrderItem::getWarehouseLocationId)
					.filter(Objects::nonNull)
					.map(Object::toString)
					.findFirst()
					.orElseGet(() -> {
						if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getWarehouseLocationId() != null) {
							return order.getSubSalesOrder().getWarehouseLocationId().toString();
						} else {
							return null;
						}
					});
			inventory.setWarehouseId(warehouseId);
			inventory.setChannelSkuCode(mapEntrySet.getKey());
			inventory.setQuantity(mapEntrySet.getValue().toString());

			inventories.add(inventory);

		}
		request.setStoreId(order.getStoreId());
		request.setInventories(inventories);
		request.setReleaseType("cancel");
		return request;

	}

	private InventoryRequest setCancelledInventoryRequestForSplitOrder(SplitSalesOrder order, Map<String, BigDecimal> mapSkuList) {

		InventoryRequest request = new InventoryRequest();

		List<BlockInventory> inventories = new ArrayList<>();

		for (Map.Entry<String, BigDecimal> mapEntrySet : mapSkuList.entrySet()) {

			BlockInventory inventory = new BlockInventory();

			//Set warehouseId based on sku level warehouseId
			inventory.setChannelSkuCode(mapEntrySet.getKey());
			inventory.setQuantity(mapEntrySet.getValue().toString());
			//Set warehouseId based on sku level warehouseId
			String warehouseId = order.getSplitSalesOrderItems().stream()
					.filter(e ->!e.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.filter(e -> e.getSku() != null && e.getSku().equals(mapEntrySet.getKey()))
					.map(SplitSalesOrderItem::getWarehouseLocationId)
					.filter(Objects::nonNull)
					.map(Object::toString)
					.findFirst()
					.orElseGet(() -> {
						if (order.getSplitSubSalesOrder() != null && order.getSplitSubSalesOrder().getWarehouseLocationId() != null) {
							return order.getSplitSubSalesOrder().getWarehouseLocationId().toString();
						} else {
							return null;
						}
					});
			inventory.setWarehouseId(warehouseId);
			inventories.add(inventory);

		}
		request.setStoreId(order.getStoreId());
		request.setInventories(inventories);
		request.setReleaseType("cancel");
		return request;

	}

	/**
	 * @param quote
	 * @param order
	 */
	public void sendSmsAndEMail(String incrementId, String type, String templateName, String totalAmount,String cpId) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

		try {

			org.styli.services.order.pojo.request.RequestBody payload = new RequestBody();

			payload.setIncrementId(incrementId);
			payload.setTemplate(templateName);
			payload.setType(type);
			payload.setCodPartialCancelAmount(templateName);
			payload.setCodPartialCancelAmount(totalAmount);
			payload.setCpId(cpId);
			String url = "";

			HttpEntity<RequestBody> requestBody = new HttpEntity<>(payload, requestHeaders);

			if (null != Constants.orderCredentials
					&& null != Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()) {
				url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + V1_REST_ORDERSMS;

			}

			LOGGER.info("URL SMS & Email :" + url);

			LOGGER.info(" sms request body" + mapper.writeValueAsString(requestBody));

			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, Object.class);

			LOGGER.info("sms response body" + mapper.writeValueAsString(response.getBody()));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("sms sent for: " + mapper.writeValueAsString(requestBody));

			}

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("exception occoured during send sms:" + e.getMessage());
		}
	}

	/**
	 * @param quote
	 * @param order
	 */

	public void returnInventoryWmsRestCall(String requestId) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

		try {

			RequestBody payload = new RequestBody();

			payload.setRmaId(requestId);
			String url = "";

			HttpEntity<RequestBody> requestBody = new HttpEntity<>(payload, requestHeaders);

			if (null != Constants.orderCredentials
					&& null != Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()) {
				url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + "/v1/rest/return-push";

			}

			LOGGER.info("wms return push url:" + url);
			LOGGER.info(" return wms request body" + mapper.writeValueAsString(requestBody));

			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, Object.class);

			LOGGER.info("response return wms Body" + mapper.writeValueAsString(response.getBody()));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("return wms sent for:" + mapper.writeValueAsString(requestBody));

			}

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("exception occoured during return inventory wms call:" + e.getMessage());
		}
	}

	public String getAuthorization(String authToken) {

		String token = null;

		if (StringUtils.isNotEmpty(authToken) &&  (authToken.contains(","))) {

				List<String> authTokenList = Arrays.asList(authToken.split(","));

				if (CollectionUtils.isNotEmpty(authTokenList)) {

					token = authTokenList.get(0);
				}
			
		}

		return token;
	}

	/**
	 * @param quote
	 * @param order
	 */
	public CustomerUpdateProfileResponse sendCancelOrderSmsAndEMail(SalesOrder order, String totalCodCancelledAmount) {

		CustomerUpdateProfileResponse responseBody = new CustomerUpdateProfileResponse();
		String paymentMethod = null;
		if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
			for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
				paymentMethod = payment.getMethod();
			}
		}
		boolean isFullyCancellation = false;

		BigDecimal sumOrderedQty = order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyOrdered()).reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal sumOrderedCancelled = order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyCanceled()).reduce(BigDecimal.ZERO, BigDecimal::add);

		if (sumOrderedQty.intValue() == sumOrderedCancelled.intValue()) {

			isFullyCancellation = true;
		}

		boolean isPrepaidOrder = getIsPrepidOrder(paymentMethod);
		String smsTemplate = getSmsTemplateName(isFullyCancellation, isPrepaidOrder);

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

		try {

			RequestBody payload = new RequestBody();

			payload.setIncrementId(order.getIncrementId());
			payload.setTemplate(smsTemplate);
			payload.setType(Constants.ORDER_ENTITY);
			if (null != totalCodCancelledAmount) {
				payload.setCodPartialCancelAmount(totalCodCancelledAmount);

			}
			String url = "";

			HttpEntity<RequestBody> requestBody = new HttpEntity<>(payload, requestHeaders);

			if (null != Constants.orderCredentials
					&& null != Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()) {
				url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + V1_REST_ORDERSMS;

			}

			LOGGER.info("URL SMS:" + url);

			LOGGER.info(" SMS request body" + mapper.writeValueAsString(requestBody));

			ResponseEntity<CustomerUpdateProfileResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, CustomerUpdateProfileResponse.class);

			LOGGER.info("response send sms Body" + mapper.writeValueAsString(response.getBody()));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("sms cancel sent for:" + mapper.writeValueAsString(requestBody));
				
				 responseBody = response.getBody();

			}

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("exception occoured during send cancel sms:" + e.getMessage());
			return responseBody;
		}
		
		return responseBody;
	}

	public void sendRefundSmsAndEMail(String incrementId, String type, String templateName,
			String totalRefundOnlineAmount) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

		try {

			RequestBody payload = new RequestBody();

			payload.setIncrementId(incrementId);
			payload.setTemplate(templateName);
			payload.setType(type);
			payload.setCodPartialCancelAmount(totalRefundOnlineAmount);
			String url = "";

			HttpEntity<RequestBody> requestBody = new HttpEntity<>(payload, requestHeaders);

			if (null != Constants.orderCredentials
					&& null != Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()) {
				url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + V1_REST_ORDERSMS;

			}

			LOGGER.info("URL SMS:" + url);

			LOGGER.info(" sms request body" + mapper.writeValueAsString(requestBody));

			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, Object.class);

			LOGGER.info("sms response body" + mapper.writeValueAsString(response.getBody()));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("sms refeund sent for:" + mapper.writeValueAsString(requestBody));

			}

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("exception occoured during send Refund sms:" + e.getMessage());
		}
	}

	/**
	 * @param method
	 * @return
	 */
	private Boolean getIsPrepidOrder(String method) {
		Boolean isPrepaidOrder = true;

		if (StringUtils.isNotBlank(method) && method.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {
			isPrepaidOrder = false;
		}

		return isPrepaidOrder;
	}

	/**
	 * @param isFullyCancellation
	 * @param isPrepaidOrder
	 * @return
	 */
	private String getSmsTemplateName(Boolean isFullyCancellation, Boolean isPrepaidOrder) {

		String smsTemplate = null;
		if (isPrepaidOrder && isFullyCancellation) {

			smsTemplate = OrderConstants.SMS_TEMPLATE_PREPAID_FULLY_UNFULFILMENT;
		} else if (isPrepaidOrder && !isFullyCancellation) {

			smsTemplate = OrderConstants.SMS_TEMPLATE_PREPAID_PARTIAL_UNFULFILMENT;

		} else if (!isPrepaidOrder && isFullyCancellation) {

			smsTemplate = OrderConstants.SMS_TEMPLATE_COD_FULLY_UNFULFILMENT;

		} else if (!isPrepaidOrder && !isFullyCancellation) {

			smsTemplate = OrderConstants.SMS_TEMPLATE_COD_PARTIAL_UNFULFILMENT;
		}
		return smsTemplate;
	}

	public CustomerEntity getCustomerDetails(Integer customerId, String customerEmail) {
		return this.getCustomerDetails(customerId, customerEmail, null);
	}

	public CustomerEntity getCustomerDetails(Integer customerId, String customerEmail, String customerPhoneNo) {

		CustomerUpdateProfileResponse customerUpdateProfileResponse = null;
		CustomerEntity customerEntity = new CustomerEntity();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

		try {

			CustomerRequestBody payload = new CustomerRequestBody();

			payload.setCustomerId(customerId);
			payload.setCustomerEmail(customerEmail);
			payload.setCustomerPhoneNo(customerPhoneNo);
			
			String url = "";

			HttpEntity<CustomerRequestBody> requestBody = new HttpEntity<>(payload, requestHeaders);

			if (null != Constants.orderCredentials
					&& null != Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl()) {
				url = Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl() + "/rest/customer/oms/details";
			}

			LOGGER.info("URL customer fetch :" + url);

			LOGGER.info("customer request body" + mapper.writeValueAsString(requestBody));

			ResponseEntity<CustomerUpdateProfileResponse> response = restTemplate.exchange(
					url, HttpMethod.POST, requestBody, CustomerUpdateProfileResponse.class);

			LOGGER.info("customer response body" + mapper.writeValueAsString(response.getBody()));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("sms sent for:" + mapper.writeValueAsString(requestBody));
				
				customerUpdateProfileResponse = response.getBody();
				if (null != customerUpdateProfileResponse && null != customerUpdateProfileResponse.getResponse()
						&& null != customerUpdateProfileResponse.getResponse().getCustomer()) {
					customerEntity
							.setEntityId(customerUpdateProfileResponse.getResponse().getCustomer().getCustomerId());
					customerEntity.setEmail(customerUpdateProfileResponse.getResponse().getCustomer().getEmail());
					customerEntity.setPhoneNumber(
							customerUpdateProfileResponse.getResponse().getCustomer().getMobileNumber());
					customerEntity
							.setFirstName(customerUpdateProfileResponse.getResponse().getCustomer().getFirstName());
					customerEntity.setLastName(customerUpdateProfileResponse.getResponse().getCustomer().getLastName());
					customerEntity.setIsActive(customerUpdateProfileResponse.getResponse().getCustomer().getIsActive());
					if (null != customerUpdateProfileResponse.getResponse().getCustomer().getJwtFlag()) {
						customerEntity
								.setJwtToken(customerUpdateProfileResponse.getResponse().getCustomer().getJwtFlag());
					}

				}
			}

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("exception occoured during customer fetch:" + e.getMessage());
				
		}
		
		return customerEntity;
	}
	
	public BigDecimal getExclTaxfactor(BigDecimal tax) {
		BigDecimal price = new BigDecimal(100).add(tax);
		return price.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
	}

	@Transactional(readOnly = true)
	public List<SalesCreditmemo> getSalesCreditMemoList(Integer orderId) {
		List<SalesCreditmemo> salesCreditMemoList = salesCreditmemoRepository.findByOrderIdAndSplitOrderIdIsNull(orderId);
		if(CollectionUtils.isNotEmpty(salesCreditMemoList)) {
			return salesCreditMemoList;
		}
		return salesCreditmemoRepository.findByOrderId(orderId);
	}

	public List<RtoAutoRefund> getRtoAutoRefundList(SalesOrder order) {
		try {
			// Use repository instead of ORM relationship to avoid SQL preparation issues
			List<RtoAutoRefund> rtoAutoRefundList = rtoAutoRefundRepository.findBySalesOrderEntityId(order.getEntityId());

			if(rtoAutoRefundList == null || rtoAutoRefundList.isEmpty()) {
				return Collections.emptyList();
			}

			if(rtoAutoRefundList.stream().anyMatch(rto -> Objects.isNull(rto.getSplitSalesOrder()))) {
				return rtoAutoRefundList.stream().filter(rto -> Objects.isNull(rto.getSplitSalesOrder())).toList();
			}
			return rtoAutoRefundList;
		} catch (Exception e) {
			LOGGER.error("Error accessing rtoAutoRefund via repository for order " + order.getEntityId() + ": " + e.getMessage());
			return Collections.emptyList();
		}
	}

	public List<RtoAutoRefund> getSplitOrderRtoAutoRefundList(SplitSalesOrder splitSalesOrder) {
		try {
			// Use repository instead of ORM relationship to avoid SQL preparation issues
			List<RtoAutoRefund> rtoAutoRefundList = rtoAutoRefundRepository.findBySplitSalesOrderEntityId(splitSalesOrder.getEntityId());

			if(rtoAutoRefundList == null || rtoAutoRefundList.isEmpty()) {
				return Collections.emptyList();
			}

			return rtoAutoRefundList;
		} catch (Exception e) {
			LOGGER.error("Error accessing rtoAutoRefund via repository for splitSalesOrder  " + splitSalesOrder.getEntityId() + ": " + e.getMessage());
			return Collections.emptyList();
		}
	}

	public OmsUnfulfilmentResponse cancelSellerOrder(SplitSellerOrder order, OrderunfulfilmentRequest request, Map<String, String> httpRequestHeadrs) {
		OmsUnfulfilmentResponse response = new OmsUnfulfilmentResponse();
		try {
			if(!checkSellerOrderStatusBeforeCancellation(order)) {
				LOGGER.info("Seller order is not in processing status so we cannot cancel it :" + order.getIncrementId());
				response.setErrorMessage("Seller order is not in processing status so we cannot cancel it :" + order.getIncrementId());
				response.setHasError(true);
				return response;
			}

			String mainOrderStatus = order.getSplitOrder() != null ? order.getSplitOrder().getStatus() : order.getSalesOrder().getStatus();


			Map<String, BigDecimal> skuToCancelledQtyMap = new HashMap<>();
			request.getOrderItems().stream().forEach(item -> {
				skuToCancelledQtyMap.put(item.getChannelSkuCode(), item.getCancelledQuantity());
			});

			if(mainOrderStatus.equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				Map<String, Integer> requestItemsSkuToSplitItemIdMap = new HashMap<>();
				Map<String, Integer> requestItemsSkuToMainOrderItemIdMap = new HashMap<>();
				Map<String, BigDecimal> requestItemsSkuToCancelledQtyMap = new HashMap<>();
	
				order.getSplitSellerOrderItems()
				.stream()
				.filter(item -> !item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.forEach(item -> {
					if (order.getSplitOrder() != null && item.getSplitSalesOrderItem() != null && item.getSplitSalesOrderItem().getItemId() != null) {
						requestItemsSkuToSplitItemIdMap.put(item.getSku(), item.getSplitSalesOrderItem().getItemId());
						requestItemsSkuToCancelledQtyMap.put(item.getSku(), item.getSplitSalesOrderItem().getQtyCanceled());
					} else if (item.getSalesOrderItem() != null && item.getSalesOrderItem().getItemId() != null) {
						requestItemsSkuToMainOrderItemIdMap.put(item.getSku(), item.getSalesOrderItem().getItemId());
						requestItemsSkuToCancelledQtyMap.put(item.getSku(), item.getSalesOrderItem().getQtyCanceled());
					}
					else {
						LOGGER.warn("SplitSalesOrderItem or MainOrderItem or ItemId is null for SKU: " + item.getSku());
					}
				});

				List<OrderPushItem> orderPushItemsForOMSCancelApi = new ArrayList<>();

				for (OrderPushItem item : request.getOrderItems()) {				
					OrderPushItem orderPushItemForOMSCancelApi = item;
					Integer itemId = null;
					if(order.getSplitOrder() != null) {
						itemId = requestItemsSkuToSplitItemIdMap.get(item.getChannelSkuCode());
					} else {
						itemId = requestItemsSkuToMainOrderItemIdMap.get(item.getChannelSkuCode());
					}
					if (itemId != null) {
						orderPushItemForOMSCancelApi.setOrderItemCode(itemId.toString());
					} else {
						LOGGER.warn("No item ID found for SKU: " + item.getChannelSkuCode());
						orderPushItemForOMSCancelApi.setOrderItemCode("0"); // fallback value
					}
					BigDecimal cancelledQty = requestItemsSkuToCancelledQtyMap.get(item.getChannelSkuCode()) != null ? requestItemsSkuToCancelledQtyMap.get(item.getChannelSkuCode()).add(item.getCancelledQuantity()) : item.getCancelledQuantity();
					orderPushItemForOMSCancelApi.setCancelledQuantity(cancelledQty);
					orderPushItemsForOMSCancelApi.add(orderPushItemForOMSCancelApi);
				}
		
				request.setOrderItems(orderPushItemsForOMSCancelApi);
				if(request.getCancelledBy() != OrderConstants.CANCELLED_BY_SYSTEM){
					LOGGER.warn("Unexpected 'cancelledBy' value: " + request.getCancelledBy() + " in cancelSellerOrder. Defaulting to CANCELLED_BY_SELLER.");
					request.setCancelledBy(OrderConstants.CANCELLED_BY_SELLER);
				}
				request.setSplitSellerOrder(order);
				request.setCancelledSellerSkuQtyMap(new HashMap<String, BigDecimal>(skuToCancelledQtyMap));

				SplitSalesOrder splitSalesOrder = order.getSplitOrder();

				if(splitSalesOrder != null) {
					request.setOrderCode(order.getSplitOrder().getIncrementId());
					response = splitOrderpushHelper.cancelUnfulfilledSplitOrder(order.getSplitOrder(), request, httpRequestHeadrs);
					if(response.getHasError()) {
						LOGGER.info("Error in split order cancellation " + response.getErrorMessage());
						return response;
					}
				} else {
					request.setOrderCode(order.getSalesOrder().getIncrementId());
					response = orderpushHelper.cancelUnfulfiorder(order.getSalesOrder(), request, httpRequestHeadrs);
					
					if(response.getHasError()) {
						LOGGER.info("Error in split order cancellation " + response.getErrorMessage());
						return response;
					}
				}
				LOGGER.info("Seller Cancelation Response : " + response);

			} else {
				LOGGER.info("Main order is not in processing status. Cancelling only seller order " + order.getIncrementId() + " without main order.");
				cancelSellerOrderAndItems(order, OrderConstants.CANCELLED_BY_SELLER_WITHOUT_MAIN_ORDER, OrderConstants.WMS_STATUS_PUSHED, skuToCancelledQtyMap);
			}

			return response;
		} catch (Exception e) {
			LOGGER.info("Error in seller order cancellation " + e.getMessage());
			response = new OmsUnfulfilmentResponse();
			response.setErrorMessage("Error in seller order cancellation " + e.getMessage());
			response.setHasError(true);
			return response;
		}
	}

	@Transactional
	public void cancelSellerOrderAndItems(SplitSellerOrder order, Integer cancelledBy, Integer wmsStatus, Map<String, BigDecimal> cancelledSellerSkuQtyMap) {
        // Check if the entity is deserialized without required relationships (salesOrder is null)
        // This happens when splitSellerOrder is sent in the API payload and Jackson deserializes it
        SplitSellerOrder managedOrder = order;
        if (order.getSalesOrder() == null && order.getEntityId() != null) {
            // Load the entity from database to ensure relationships are properly loaded
            managedOrder = splitSellerOrderRepository.findByEntityId(order.getEntityId());
            if (managedOrder == null) {
                throw new IllegalArgumentException("SplitSellerOrder not found with entityId: " + order.getEntityId());
            }
        }
		Timestamp updatedAt = new Timestamp(new Date().getTime());
		managedOrder.getSplitSellerOrderItems().stream().forEach(item -> {
			BigDecimal cancelledQty = cancelledSellerSkuQtyMap.containsKey(item.getSku()) ? cancelledSellerSkuQtyMap.get(item.getSku()).add(item.getQtyCanceled()) : item.getQtyOrdered();
			item.setQtyCanceled(cancelledQty);
			item.setCancelledBy(cancelledBy);
			item.setUpdatedAt(updatedAt);
		});

		switch(cancelledBy) {
			case OrderConstants.CANCELLED_BY_SELLER_WITHOUT_MAIN_ORDER:
                managedOrder.setCancellationReason("Seller order cancelled by Seller without main order cancellation");
				break;
			case OrderConstants.CANCELLED_BY_WMS:
                managedOrder.setCancellationReason("Seller order cancelled by Styli WMS");
				break;
			case OrderConstants.CANCELLED_BY_CUSTOMER:
                managedOrder.setCancellationReason("Seller order cancelled by Customer");
				break;
			case OrderConstants.CANCELLED_BY_SELLER:
                managedOrder.setCancellationReason("Seller order cancelled by Seller");
				break;
			case OrderConstants.CANCELLED_BY_SYSTEM:
				order.setCancellationReason("Seller order cancelled by System");
				break;
			default:
                managedOrder.setCancellationReason("Seller order cancelled by unknown reason");
				break;
		}

		boolean allCancelled = managedOrder.getSplitSellerOrderItems().stream().allMatch(item -> item.getQtyCanceled().compareTo(item.getQtyOrdered()) == 0);
		if(allCancelled) {
			managedOrder.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			managedOrder.setWmsStatus(wmsStatus);
		} else {
			managedOrder.setStatus(getFinalStatusForSellerOrder(order));
		}
		managedOrder.setUpdatedAt(updatedAt);
		splitSellerOrderRepository.saveAndFlush(managedOrder);
		orderHelper.buildOTSPayloadForSellerOrderAndPublishToPubSub(order, "Seller Order Closed", "4.0");
	}

	public void cancelSellerOrders(SalesOrder order, SplitSalesOrder splitSalesOrder, OrderunfulfilmentRequest request) {
		if(request.getSplitSellerOrder() != null) {
			int cancelledBy = request.getCancelledBy();
			if(cancelledBy != OrderConstants.CANCELLED_BY_SELLER && cancelledBy != OrderConstants.CANCELLED_BY_SYSTEM) {
				LOGGER.warn("Cancelled by is not a valid value. Setting it to " + OrderConstants.CANCELLED_BY_SELLER);
				cancelledBy = OrderConstants.CANCELLED_BY_SELLER;
			}
			cancelSellerOrderAndItems(request.getSplitSellerOrder(), cancelledBy, OrderConstants.WMS_STATUS_PUSH_TO_WMS, request.getCancelledSellerSkuQtyMap());
		}
		else {
			List<SplitSellerOrder> toCancelSellerOrders = new ArrayList<>();
			Map<String, BigDecimal> remainingBySku = new HashMap<>();
			for(OrderPushItem item : request.getOrderItems()) {
				remainingBySku.put(item.getChannelSkuCode(), item.getCancelledQuantity());
			}
			// Active seller orders (not CLOSED)
			List<SplitSellerOrder> activeSellerOrders =
					(order != null ? order.getSplitSellerOrders().stream()
							: splitSalesOrder.getSplitSellerOrders().stream())
							.sorted(Comparator.comparing(SplitSellerOrder::getEntityId)) // Ascending by entityId
							.toList();
			for(SplitSellerOrder so : activeSellerOrders) {
				// get first item safely
				SplitSellerOrderItem soi = (so.getSplitSellerOrderItems() != null)
						? so.getSplitSellerOrderItems().stream().findFirst().orElse(null)
						: null;
				if (soi == null || soi.getSku() == null) {
					continue;
				}
				String sku = soi.getSku();
				BigDecimal remaining = remainingBySku.get(sku);
				if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				if(so.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
					remainingBySku.put(sku, remaining.subtract(soi.getQtyCanceled()));
					continue;
				}
				BigDecimal ordered = soi.getQtyOrdered() != null ? soi.getQtyOrdered() : BigDecimal.ZERO;
				BigDecimal alreadyCanceled = soi.getQtyCanceled() != null ? soi.getQtyCanceled() : BigDecimal.ZERO;
				BigDecimal cancellable = ordered.subtract(alreadyCanceled);

				// Nothing left to cancel
				if (cancellable.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				// Scenario 1 & 3: Full cancel when remaining >= cancellable and cancellable == ordered
				if (remaining.compareTo(cancellable) >= 0 && cancellable.compareTo(ordered) == 0) {
					toCancelSellerOrders.add(so);
					remaining = remaining.subtract(cancellable);
				}
				// Scenario 2: Partial cancel
				else if (remaining.compareTo(cancellable) < 0) {
					soi.setQtyCanceled(alreadyCanceled.add(remaining));
					remaining = BigDecimal.ZERO;
				}
				// If remaining ≥ cancellable but order was partially canceled before
				else if (remaining.compareTo(cancellable) >= 0) {
					soi.setQtyCanceled(alreadyCanceled.add(cancellable));
					toCancelSellerOrders.add(so);
					remaining = remaining.subtract(cancellable);
				}
				// Step 4: Update remaining map
				if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
					remainingBySku.remove(sku);
				} else {
					remainingBySku.put(sku, remaining);
				}
				// Persist the mutation on the managed entity, for partial cancellations
				so.setUpdatedAt(new Timestamp(new Date().getTime()));
				splitSellerOrderRepository.saveAndFlush(so);
				// Stop early if all cancellations processed
				if (remainingBySku.isEmpty()) {
					break;
				}
			}

			for (SplitSellerOrder so : toCancelSellerOrders) {
				cancelSellerOrderAndItems(so, OrderConstants.CANCELLED_BY_WMS, OrderConstants.WMS_STATUS_PUSH_TO_WMS, new HashMap<String, BigDecimal>());
			}
		}
	}

	private String getFinalStatusForSellerOrder(SplitSellerOrder sellerOrder) {
        String finalStatus = OrderConstants.PROCESSING_ORDER_STATUS;
        for (SplitSellerOrderItem item : sellerOrder.getSplitSellerOrderItems()) {
            BigDecimal qtyOrdered = item.getQtyOrdered();
            BigDecimal qtyShipped = item.getQtyShipped();
            BigDecimal qtyPacked = item.getQtyPacked();
            BigDecimal qtyCanceled = item.getQtyCanceled();

            BigDecimal qtyRemaining = qtyOrdered.subtract(qtyPacked).subtract(qtyCanceled);
            if (qtyRemaining.compareTo(BigDecimal.ZERO) > 0) {
                finalStatus = OrderConstants.PROCESSING_ORDER_STATUS;
                continue;
            }
            if (qtyPacked.compareTo(BigDecimal.ZERO) > 0) {
                finalStatus = OrderConstants.PACKED_ORDER_STATUS;
            }
            if (qtyShipped.compareTo(BigDecimal.ZERO) > 0
                    && qtyOrdered.subtract(qtyShipped).subtract(qtyCanceled).compareTo(BigDecimal.ZERO) == 0) {
                finalStatus = OrderConstants.SHIPPED_ORDER_STATUS;
                continue;
            }
            if (qtyCanceled.compareTo(BigDecimal.ZERO) > 0
                    && qtyOrdered.subtract(qtyCanceled).compareTo(BigDecimal.ZERO) == 0) {
                finalStatus = OrderConstants.CLOSED_ORDER_STATUS;
            }
        }

        return finalStatus;
    }

	private boolean checkSellerOrderStatusBeforeCancellation(SplitSellerOrder order) {
		if(order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
			LOGGER.info("Seller order is in processing status so we can cancel it :" + order.getIncrementId());
			return true;
		}

		BigDecimal totalCanceledQty = BigDecimal.ZERO;
        BigDecimal totalOrderedQty = BigDecimal.ZERO;
        BigDecimal totalShippedQty = BigDecimal.ZERO;

        for (org.styli.services.order.model.sales.SplitSellerOrderItem item : order.getSplitSellerOrderItems()) {
            totalCanceledQty = totalCanceledQty.add(item.getQtyCanceled());
            totalOrderedQty = totalOrderedQty.add(item.getQtyOrdered());
            totalShippedQty = totalShippedQty.add(item.getQtyShipped());
        }
		BigDecimal totalRemainingQty = totalOrderedQty.subtract(totalShippedQty).subtract(totalCanceledQty);
		if(totalRemainingQty.compareTo(BigDecimal.ZERO) > 0) {
			LOGGER.info("Seller order is not fully canceled so we can cancel it :" + order.getIncrementId());
			return true;
		}

		LOGGER.warn("Seller order is not cancellable :" + order.getIncrementId());

		return false;
	}

	/**
	 * Creates an OrderunfulfilmentRequest for split order cancellation by mapping seller item IDs to split sales item IDs
	 *
	 * @param order The split seller order
	 * @param request The original unfulfillment request
	 * @param sellerItemIdtoParentItemId Map from seller item ID to parent item ID
	 * @param parentItemIdtoSplitSalesItemId Map from parent item ID to split sales item ID
	 * @return OrderunfulfilmentRequest configured for split order
	 */
	private OrderunfulfilmentRequest createSplitOrderUnfulfillmentRequest(
			SplitSellerOrder order,
			OrderunfulfilmentRequest request) {

		OrderunfulfilmentRequest requestSplitOrder = new OrderunfulfilmentRequest();
		requestSplitOrder.setOrderCode(order.getSplitOrder().getIncrementId());
		requestSplitOrder.setLocationCode(order.getSplitOrder().getSplitSubSalesOrder().getWarehouseLocationId().toString());
		requestSplitOrder.setOrderItems(request.getOrderItems());

		return requestSplitOrder;
	}


	public OTSOrderRequest buildOTSPayloadForSellerOrder(SplitSellerOrder order, String statusMessage, String statusId) {
        OTSOrderRequest otsOrderRequest = new OTSOrderRequest();
        otsOrderRequest.setParentOrderId(order.getEntityId());
        otsOrderRequest.setIncrementId(order.getIncrementId());
        otsOrderRequest.setCustomerId(order.getSalesOrder().getCustomerId());
        otsOrderRequest.setCustomerEmail(order.getSalesOrder().getCustomerEmail());

		List<StatusMessage> statuses = new ArrayList<>();

        statuses.add(new SellerStatusMessage(
                statusId,
                statusMessage,
                OffsetDateTime.now().toString(),
                order.getEntityId(),
                order.getIncrementId()
        ));

        Collection<SplitSellerOrderItem> items = Optional.ofNullable(order)
                .map(SplitSellerOrder::getSplitSellerOrderItems)
                .map(col -> (Collection<SplitSellerOrderItem>) col)
                .orElse(Collections.emptyList());

        List<SkuItem> skus = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE
                        .equalsIgnoreCase(item.getSalesOrderItem().getProductType()))
                .map(it -> {
                    SkuItem s = new SkuItem();
                    s.setSku(it.getSku());
					s.setStatusMessage(statuses);
                    return s;
                }).collect(Collectors.toList());

        otsOrderRequest.setSkus(skus);

        return otsOrderRequest;
    }

	public OTSOrderRequest buildOTSPayloadForSplitOrder(SplitSalesOrder order, String statusMessage, String statusId) {
        OTSOrderRequest otsOrderRequest = new OTSOrderRequest();
        otsOrderRequest.setParentOrderId(order.getEntityId());
        otsOrderRequest.setIncrementId(order.getIncrementId());
        otsOrderRequest.setCustomerId(order.getSalesOrder().getCustomerId());
        otsOrderRequest.setCustomerEmail(order.getSalesOrder().getCustomerEmail());

		List<StatusMessage> statuses = new ArrayList<>();

        statuses.add(new StatusMessage(
                statusId,
                statusMessage,
                OffsetDateTime.now().toString()
        ));

		otsOrderRequest.setStatusMessage(statuses);

        return otsOrderRequest;
    }

	public void buildOTSPayloadForSellerOrderAndPublishToPubSub(SplitSellerOrder order, String statusMessage, String statusId) {
		try {
			OTSOrderRequest otsOrderRequest = buildOTSPayloadForSellerOrder(order, statusMessage, statusId);
			pubSubServiceImpl.publishOrderTrackingPubSub(splitOrderTrackingTopic, Arrays.asList(otsOrderRequest));
		} catch(Exception e) {
			LOGGER.error("Error in building OTS payload and publishing to pubsub for orderId: " + order.getEntityId(), e);
		}
	}

	public void buildOTSPayloadForSplitOrderAndPublishToPubSub(SplitSalesOrder order, String statusMessage, String statusId) {
		try {
			OTSOrderRequest otsOrderRequest = buildOTSPayloadForSplitOrder(order, statusMessage, statusId);
			pubSubServiceImpl.publishOrderTrackingPubSub(splitOrderTrackingTopic, Arrays.asList(otsOrderRequest));
		} catch(Exception e) {
			LOGGER.error("Error in building OTS payload and publishing to pubsub for orderId: " + order.getEntityId(), e);
		}
	}

	private OTSOrderRequest buildOTSPayloadForMainOrder(SalesOrder order, String statusMessage, String statusId) {
		OTSOrderRequest otsOrderRequest = new OTSOrderRequest();
		otsOrderRequest.setOp("create");
		otsOrderRequest.setParentOrderId(order.getEntityId());
		otsOrderRequest.setIncrementId(order.getIncrementId());
		otsOrderRequest.setCustomerId(order.getCustomerId());
		otsOrderRequest.setCustomerEmail(order.getCustomerEmail());
		List<StatusMessage> statuses = new ArrayList<>();

		statuses.add(new StatusMessage(
				statusId,
				statusMessage,
				OffsetDateTime.now().toString()
		));
		Collection<SalesOrderItem> items = Optional.ofNullable(order)
				.map(SalesOrder::getSalesOrderItem)
				.map(col -> (Collection<SalesOrderItem>) col)
				.orElse(Collections.emptyList());
		List<SkuItem> skus = items.stream()
				.filter(Objects::nonNull)
				.filter(item -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE
						.equalsIgnoreCase(item.getProductType()))
				.map(it -> {
					SkuItem s = new SkuItem();
					s.setSku(it.getSku());
					s.setWarehosueId(it.getWarehouseLocationId());
					s.setShipementMode("Local".equalsIgnoreCase(it.getShipmentType())?"Express":it.getShipmentType());
					s.setSellerId(it.getSellerId());
					s.setSellername(it.getSellerName());
					return s;
				}).collect(Collectors.toList());
		otsOrderRequest.setSkus(skus);
		otsOrderRequest.setStatusMessage(statuses);

		return otsOrderRequest;
	}

	public void buildOTSPayloadAndPublishToPubSubForMainOrder(SalesOrder order, String statusMessage, String statusId) {
		try {
			OTSOrderRequest otsOrderRequest = buildOTSPayloadForMainOrder(order, statusMessage, statusId);
			LOGGER.info("Pushing payload to Order tracking service PUBSUB for orderId: " + order.getEntityId() + " and payload: " + mapper.writeValueAsString(otsOrderRequest));
			pubSubServiceImpl.publishOrderTrackingPubSub(splitOrderTrackingTopic, Arrays.asList(otsOrderRequest));
		} catch(Exception e) {
			LOGGER.error("Error in building OTS payload and publishing to pubsub for orderId: " + order.getEntityId(), e);
		}
	}
}

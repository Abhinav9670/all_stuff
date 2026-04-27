package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrderPayment;
import org.styli.services.order.model.sales.SplitSubSalesOrder;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.model.sales.SubSalesOrderItem;
import org.styli.services.order.pojo.CancelDetails;
import org.styli.services.order.pojo.OrderSms;
import org.styli.services.order.pojo.PayfortConfiguration;
import org.styli.services.order.pojo.PayfortDtfQueryRequest;
import org.styli.services.order.pojo.PayfortOrderRefundPayLoad;
import org.styli.services.order.pojo.PayfortOrderRefundResponse;
import org.styli.services.order.pojo.PayfortQueryResponse;
import org.styli.services.order.pojo.PayfortVoidAuthorizationRequest;
import org.styli.services.order.pojo.PayfortVoidAuthorizationResponse;
import org.styli.services.order.pojo.RefundAmountObject;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TotalItemsReturnedResponse;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.order.ShukranEarnItem;
import org.styli.services.order.pojo.order.ShukranEarnItemDetails;
import org.styli.services.order.pojo.order.TotalRefundAmountResponse;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.VaultPaymentTokenRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.impl.CalculateRefundAmountResponse;
import org.styli.services.order.service.impl.CoinAdditionData;
import org.styli.services.order.service.impl.CoinAdditionDetailData;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.SalesOrderCancelServiceImpl;
import org.styli.services.order.service.impl.SalesOrderRMAServiceImpl;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.UtilityConstant;

@Component
public class PaymentRefundHelper {

	private static final String ORDERCONSTANT = "order	";
	private static final Log LOGGER = LogFactory.getLog(PaymentRefundHelper.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	@Value("${eas.base.url}")
	private String earnUrl;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;
	@Autowired
	SplitSalesOrderItemRepository splitSalesOrderItemRepository;

	@Autowired
	SalesOrderItemRepository salesOrderItemRepository;

	@Autowired
	MulinHelper mulinHelper;

	@Autowired
	SalesOrderRMAServiceImpl salesOrderRMAServiceImpl;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	VaultPaymentTokenRepository vaultPaymentTokenRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Autowired
	ExternalQuoteHelper externalQuoteHelper;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	RefundHelper refundHelper;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	OrderHelperV2 orderHelperV2;

	@Autowired
	SalesOrderServiceV2 salesOrderServiceV2;

	@Autowired
	ProxyOrderRepository proxyOrderRepository;

	@Autowired
	@Lazy
	private EASServiceImpl eASServiceImpl;

	@Autowired
	private SubSalesOrderItemRepository subSalesOrderItemRepository;

	@Autowired
	private AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	private AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	OrderShipmentHelper orderShipmentHelper;

	@Autowired
	ZatcaServiceImpl zatcaServiceImpl;

	public RefundPaymentRespone payfortRefundcall(SalesOrder order, BigDecimal amount, String fortId,
			String paymentMethod) {
		LOGGER.info("inside payfortRefundcall : ");

		String refundAmount = amount.toString();
		RefundPaymentRespone response = new RefundPaymentRespone();
		PayfortReposne payfortResponse = null;
		if (OrderConstants.checkPaymentMethod(paymentMethod)) {

			PayfortConfiguration configuration = new PayfortConfiguration();
			getPayfortConfDetails(order.getStoreId().toString(), paymentMethod, configuration);
			try {
				LOGGER.info("inside payfortRefundcall : configuration:" + mapper.writeValueAsString(configuration));
			} catch (JsonProcessingException e) {
				LOGGER.error(" inside payfortRefundcall : error during write configuration:" + e.getMessage());
			}

			payfortResponse = triggerPayfortRefundRestApiCall(
					preparePayfortRefundRequest(configuration, order, refundAmount, fortId), order);

		}

		orderHelper.buildOTSPayloadAndPublishToPubSubForMainOrder(order,
				"Refunded amount " + refundAmount + " for order " + order.getIncrementId(), "10.0");

		if (null != payfortResponse && !payfortResponse.isStatus()) {
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(payfortResponse.getMessage());
			return response;
		}

		response.setStatus(true);
		response.setStatusCode("200");
		if (Objects.nonNull(payfortResponse)) {
			response.setPaymentRRN(payfortResponse.getPaymentRRN());
		}
		return response;
	}

	public RefundPaymentRespone payfortQuerycall(SalesOrder order, SalesOrderPayment salesOrderPayment,
			String deviceId) {

		LOGGER.info("Inside payfortQueryCall for order: " + order.getIncrementId());
		RefundPaymentRespone response = new RefundPaymentRespone();
		String paymentMethod = null;
		PayfortDtfQueryRequest payfortQueryDtfRequest = new PayfortDtfQueryRequest();

		if (null != salesOrderPayment) {
			paymentMethod = salesOrderPayment.getMethod();
		}

		String applePay = PaymentCodeENUM.APPLE_PAY.getValue();
		String incrementId = order.getIncrementId();
		if (Objects.nonNull(paymentMethod) && applePay.equalsIgnoreCase(paymentMethod)
				&& Objects.nonNull(order.getEditIncrement())) {
			incrementId = order.getEditIncrement();
		}

		PayfortConfiguration configuration = new PayfortConfiguration();

		getPayfortConfDetails(order.getStoreId().toString(), paymentMethod,
				configuration);
		payfortQueryDtfRequest.setAccessCode(configuration.getAccessCode());
		payfortQueryDtfRequest.setLanguage(configuration.getLanguage());
		payfortQueryDtfRequest.setMerchantIdentifier(configuration.getMerchantIdentifier());
		payfortQueryDtfRequest.setMerchantReference(incrementId);
		payfortQueryDtfRequest.setCommand("CHECK_STATUS");
		payfortQueryDtfRequest
				.setSignature(getQtfQuerySignature(payfortQueryDtfRequest, configuration.getSignatureHash()));

		PayfortQueryResponse responseBody = triggerPayfortQueryRestApiCall(payfortQueryDtfRequest, order);

		if (null != responseBody && null != responseBody.getTransactionCode()
				&& ((responseBody.getTransactionCode()
						.equals(OrderConstants.PAYFORT_DTF_QUERY_SUCCESS_TRANSACTION_CODE)
						&& null != responseBody.getTransactionStatus()
						&& responseBody.getTransactionStatus()
								.equals(OrderConstants.PAYFORT_DTF_QUERY_SUCCESS_TRANSACTION_STATUS))
						|| (responseBody.getTransactionCode()
								.equals(OrderConstants.PAYFORT_AUTHORIZATION_DTF_RESPONSE_CODE)
								&& responseBody.getTransactionStatus()
										.equals(OrderConstants.PAYFORT_AUTHORIZATION_DTF_SUCCESS_ORDER_STATUS)
								&& null != order.getPayfortAuthorized() && order.getPayfortAuthorized() == 1))) {

			LOGGER.info("Query success response for order: " + order.getIncrementId() + " Merchant reference: "
					+ responseBody.getMerchantReference());

			if (null != order.getSubSalesOrder()
					&& (null == order.getSubSalesOrder().getDtfLock() || order.getSubSalesOrder().getDtfLock() == 0)) {

				LOGGER.info("dtf lock is not zero for order: " + order.getIncrementId());

				order.getSubSalesOrder().setDtfLock(1);

				setPaymentDetails(salesOrderPayment, responseBody);
				LOGGER.info("Order status before:"+order.getStatus() +", orderId: "+order.getIncrementId() + ": Setting to:"+OrderConstants.PROCESSING_ORDER_STATUS);
				order.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
				order.setState(OrderConstants.PROCESSING_ORDER_STATUS);
				order.setExtOrderId("0");
				order.getSubSalesOrder().setRetryPayment(0);
				order.setRetryPayment(0);
				if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null
						&& order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
					try {
						List<Stores> stores = Constants.getStoresList();
						Stores store = stores.stream()
								.filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
								.findAny().orElse(null);
						String lockResponse = commonService.lockUnlockShukranData(
								order.getSubSalesOrder().getCustomerProfileId(),
								String.valueOf(order.getSubSalesOrder().getTotalShukranCoinsBurned().intValue()),
								order.getSubSalesOrder().getQuoteId(), true, order, store,
								"Locking Points On Retry Payment Success", "On Retry Payment Success");
						if (StringUtils.isNotBlank(lockResponse) && StringUtils.isNotEmpty(lockResponse)
								&& lockResponse.equalsIgnoreCase("api passed")) {
							LOGGER.info("in retry payment locking 3 ");
							SubSalesOrder subSalesOrder = order.getSubSalesOrder();
							subSalesOrder.setShukranLocked(0);
							subSalesOrderRepository.saveAndFlush(subSalesOrder);
						}
					} catch (Exception e) {
						LOGGER.info("Error In Shukran Locking Points " + e.getMessage());
					}
				}

				paymentDtfHelper.updateOrderStatusHistory(order, OrderConstants.PAYFORT_SUCCESS_MESSAGE,
						OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
				paymentDtfHelper.saveOrderGrid(order, OrderConstants.PROCESSING_ORDER_STATUS);

				if (null != order.getSubSalesOrder().getRetryPayment()
						&& order.getSubSalesOrder().getRetryPayment() == 1) {

					paymentDtfHelper.setEstimatedDeliveryTimeForRetryPayment(order);
				}
				salesOrderRepository.saveAndFlush(order);

				String message = "The Authorized Amount is" + order.getStoreCurrencyCode() + " "
						+ responseBody.getAuthorizedAmount()
						+ " Transaction ID:" + responseBody.getFortId();

				paymentDtfHelper.updateOrderStatusHistory(order, message, "order", order.getStatus());

				String updateMessage = "PayFort Query Response code is:" + responseBody.getResponseCode() + ""
						+ "& Message " + responseBody.getResponseMessage();

				paymentDtfHelper.updateOrderStatusHistory(order, updateMessage, "order", order.getStatus());

				OrderSms ordersms = new OrderSms();
				ordersms.setOrderid(order.getEntityId().toString());

				paymentDtfHelper.publishToKafka(ordersms);
				orderHelper.updateStatusHistory(order, false, true, false, false, false);

				String modeOfPayment = salesOrderPayment.getMethod();
				if (modeOfPayment != null) {
					LOGGER.info("Order " + order.getIncrementId() + "modeOfPayment is : " + modeOfPayment);
					orderHelperV2.publishPreferredPaymentIfValid(modeOfPayment, order);
				}

			} else {

				LOGGER.info("success is not null or zero");
			}
		} else if (null != responseBody
				&& null != responseBody.getTransactionCode()
				&& responseBody.getTransactionCode().equals(OrderConstants.PAYFORT_DTF_QUERY_HOLD_TRANSACTION_CODE)
				&& null != responseBody.getTransactionStatus() &&
				responseBody.getTransactionStatus()
						.equals(OrderConstants.PAYFORT_DTF_QUERY_HOLD_TRANSACTION_STATUS)) {

			LOGGER.info("Query in on hold for order: " + order.getIncrementId() + " Merchant reference: "
					+ responseBody.getMerchantReference());

			LOGGER.info("15777");

			SubSalesOrder subSalesOrder = order.getSubSalesOrder();

			salesOrderPayment = order.getSalesOrderPayment().stream().findFirst()
					.orElse(null);

			setPaymentDetails(salesOrderPayment, responseBody);

			order.setStatus(OrderConstants.ORDER_STATUS_PAYMENT_HOLD);
			order.setState(OrderConstants.ORDER_STATE_PAYMENT_HOLD);
			// if(order.getSubSalesOrder() != null &&
			// order.getSubSalesOrder().getTotalShukranCoinsBurned()!=null &&
			// order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0
			// && order.getSubSalesOrder().getShukranLocked() == 0){
			// List<Stores> stores = Constants.getStoresList();
			// Stores store = stores.stream().filter(e ->
			// Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
			// .orElse(null);
			// commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(),order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(),
			// order.getSubSalesOrder().getQuoteId(), false, order, store);
			// subSalesOrder.setShukranLocked(1);
			// }

			paymentDtfHelper.updateOrderStatusHistory(order, OrderConstants.PAYFORT_HOLD_MESSAGE,
					OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
			paymentDtfHelper.saveOrderGrid(order, OrderConstants.ORDER_STATUS_PAYMENT_HOLD);
			salesOrderRepository.saveAndFlush(order);

			String message = OrderConstants.THE_AUTHORIZED_AMOUNT_IS + order.getBaseCurrencyCode() + " "
					+ salesOrderPayment.getAmountOrdered()
					+ "TRANSACTION_ID" + responseBody.getFortId();

			paymentDtfHelper.updateOrderStatusHistory(order, message, ORDERCONSTANT, order.getStatus());

			String updateMessage = "PayFort Query Response code is:" + responseBody.getTransactionCode() + ""
					+ "& Message " + responseBody.getTransactionMessage();

			paymentDtfHelper.updateOrderStatusHistory(order, updateMessage, ORDERCONSTANT, order.getStatus());

		} else {

			LOGGER.info("else part of Query !!");
			SubSalesOrder subSalesOrder = order.getSubSalesOrder();

			subSalesOrder.setDtfLock(1);

			salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

			setPaymentDetails(salesOrderPayment, responseBody);
			if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getRetryPayment() != null) {
				LOGGER.info("Order retry payment: " + order.getSubSalesOrder().getRetryPayment() + " Order details: "
						+ order.getIncrementId());
			}
			Long clientVersion = Constants.decodeAppVersion(order.getAppVersion());
			Long thresholdVersion = Constants.decodeAppVersion(Constants.getPaymentFailedThresholdVersion());
			String source = order.getSubSalesOrder().getClientSource();
			LOGGER.info("Threshold Version: " + thresholdVersion + " for order: " + order.getIncrementId());
			LOGGER.info("Client Version: " + clientVersion + " for order: " + order.getIncrementId());

			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
					.findAny().orElse(null);

			if (clientVersion != null && thresholdVersion != null && clientVersion < thresholdVersion) {
				LOGGER.info("Order is going to fail - Order details: " + order.getIncrementId());
				order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
				order.setState(OrderConstants.FAILED_ORDER_STATUS);

				failedOrder(order, responseBody, deviceId);
			} else if (StringUtils.isNotBlank(source) && !UtilityConstant.APPSOURCELIST.contains(source)) {
				LOGGER.info("order is  not place by APP - Order details: " + order.getIncrementId());
				order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
				order.setState(OrderConstants.FAILED_ORDER_STATUS);
				failedOrder(order, responseBody, deviceId);
			} else if (clientVersion != null && thresholdVersion != null && clientVersion >= thresholdVersion
					&& null != store && !store.isHoldOrder()) {
				order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
				order.setState(OrderConstants.FAILED_ORDER_STATUS);
				LOGGER.info("order order hold false so going to fail - Order details: " + order.getIncrementId());
				failedOrder(order, responseBody, deviceId);
			} else if (OrderConstants.ORDER_STATUS_PAYMENT_HOLD.equalsIgnoreCase(order.getStatus())) {
				LOGGER.info("Order is still on hold : Order details: " + order.getIncrementId());
				order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
				order.setState(OrderConstants.FAILED_ORDER_STATUS);
				setPayfortPaymentFailedOrder(order, responseBody);
			}
			try {
				if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null
						&& order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0
						&& order.getSubSalesOrder().getShukranLocked().equals(0)) {
					commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(),
							order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(),
							order.getSubSalesOrder().getQuoteId(), false, order, store,
							"Refund Shukran Coins Burned On Payment Failure", "");
					SubSalesOrder subSalesOrder1 = order.getSubSalesOrder();
					subSalesOrder1.setShukranLocked(1);
					subSalesOrderRepository.saveAndFlush(subSalesOrder1);
				}
			} catch (Exception e) {
				LOGGER.info("Exception During Unlocking Data " + e.getMessage());
			}

		}

		response.setStatus(true);
		response.setStatusCode("200");
		return response;
	}

	private void setPayfortPaymentFailedOrder(SalesOrder order, PayfortQueryResponse responseBody) {
		updateFailedOrder(order, responseBody);
		releaseStoreCredit(order);
		orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_QUERY_CALL);
		paymentDtfHelper.releaseCoupon(order);
		releaseCoins(order);

	}

	// EAS to be implement for payment fail.If Earn Service flag ON!.
	private void releaseCoins(SalesOrder order) {

		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafka(order, 0.0);
		}
	}

	private void releaseStoreCredit(SalesOrder order) {
		if (null != order.getAmstorecreditBaseAmount()) {

			paymentDtfHelper.releaseStoreCredit(order, order.getAmstorecreditAmount());
			String stylicreditMsg = OrderConstants.STYLI_CREDIT_FAILED_MSG + order.getOrderCurrencyCode() + ""
					+ order.getAmstorecreditBaseAmount();
			paymentDtfHelper.updateOrderStatusHistory(order, stylicreditMsg, OrderConstants.ORDER2, order.getStatus());

		}

		LOGGER.info("grid set done");
		order.getSubSalesOrder().setRetryPayment(0);
		order.setRetryPayment(0);
		salesOrderRepository.saveAndFlush(order);
	}

	private void updateFailedOrder(SalesOrder order, PayfortQueryResponse responseBody) {
		paymentDtfHelper.updateOrderStatusHistory(order, OrderConstants.PAYFORT_CANCEL_MESSAGE,
				OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());

		String updateMessage = OrderConstants.PAYFORT_QUERY_RESPONSE_CODE_IS + responseBody.getTransactionCode() + ""
				+ "& Message " + responseBody.getTransactionMessage();

		paymentDtfHelper.updateOrderStatusHistory(order, updateMessage, ORDERCONSTANT, order.getStatus());

		if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null
				&& order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0
				&& order.getSubSalesOrder().getShukranLocked().equals(0)) {
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
					.findAny()
					.orElse(null);
			commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(),
					order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(),
					order.getSubSalesOrder().getQuoteId(), false, order, store,
					"Refund Shukran Coins Burned On Payment Failure", "");
			SubSalesOrder subSalesOrder = order.getSubSalesOrder();
			subSalesOrder.setShukranLocked(1);
			subSalesOrderRepository.saveAndFlush(subSalesOrder);
		}

		saveOrderGrid(order, OrderConstants.FAILED_ORDER_STATUS);

	}

	private void failedOrder(SalesOrder order, PayfortQueryResponse responseBody, String deviceId) {
		updateFailedOrder(order, responseBody);
		releaseStoreCredit(order);
		orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_QUERY_CALL);
		paymentDtfHelper.failStatusOnwards(order, deviceId);
		releaseCoins(order);
	}

	/**
	 * @param order
	 * @param message
	 */
	public void saveOrderGrid(SalesOrder order, String message) {

		SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());

		salesorderGrid.setStatus(message);

		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}

	/**
	 * @param salesOrderPayment
	 * @param order
	 */
	private void setPaymentDetails(SalesOrderPayment salesOrderPayment, PayfortQueryResponse response) {

		LOGGER.info("payment set");
		if (null != salesOrderPayment) {

			try {
				String jsonInString = mapper.writeValueAsString(response);
				salesOrderPayment.setAdditionalInformation(jsonInString);
				salesOrderPayment.setLastTransId(response.getFortId());
				salesOrderPayment.setCcTransId(response.getFortId());
			} catch (JsonProcessingException e) {

				LOGGER.error("json parse exception during order payment");
			}
		}
	}

	/**
	 * @param salesOrderPayment
	 * @param order
	 */
	private void setPaymentQueryDetails(SalesOrderPayment salesOrderPayment, PayfortQueryResponse response) {

		LOGGER.info("payment set");
		if (null != salesOrderPayment) {

			try {
				String jsonInString = mapper.writeValueAsString(response);
				salesOrderPayment.setAdditionalInformation(jsonInString);
				salesOrderPayment.setLastTransId(response.getFortId());
				salesOrderPayment.setCcTransId(response.getFortId());
			} catch (JsonProcessingException e) {

				LOGGER.error("json parse exception during order payment");
			}
		}
	}

	/**
	 * @param amount
	 * @param multiplier
	 * @return
	 */
	private String getConvertedAmount(String amount, Integer multiplier) {

		LOGGER.info("amount:" + amount);
		LOGGER.info("multiplier:" + multiplier);
		if (null != amount && null != multiplier) {

			Integer payfortValue = new BigDecimal(amount).multiply(new BigDecimal(multiplier)).intValue();

			return payfortValue.toString();
		} else {

			return null;
		}

	}

	private PayfortReposne triggerPayfortRefundRestApiCall(PayfortOrderRefundPayLoad payfortRedundRequest,
			SalesOrder order) {
		LOGGER.info("inside payfortRefundcall : triggerPayfortRefundRestApiCall : ");

		PayfortReposne payfortResponse = new PayfortReposne();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity<PayfortOrderRefundPayLoad> requestBody = new HttpEntity<>(payfortRedundRequest, requestHeaders);
		String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi"; // temp
																												// for
																												// test
		try {

			LOGGER.info("inside payfortRefundcall : payfort url for refund: " + url);
			LOGGER.info("inside payfortRefundcall : Request body: " + mapper.writeValueAsString(requestBody));

			ResponseEntity<PayfortOrderRefundResponse> response = restTemplate.exchange(url, HttpMethod.POST,
					requestBody,
					PayfortOrderRefundResponse.class);

			if (response.getStatusCode() == HttpStatus.OK) {
				PayfortOrderRefundResponse responseBody = response.getBody();

				LOGGER.info("inside payfortRefundcall : Payfort refund response Body:"
						+ mapper.writeValueAsString(responseBody));
				if (null != responseBody &&
						null != responseBody.getStatus()
						&& responseBody.getStatus().equals(OrderConstants.PAYFORT_REFUND_CONSTANT_SUCCESS_STATUS)) {

					payfortResponse.setStatus(true);
					payfortResponse.setMessage(responseBody.getResponseMessage());
					payfortResponse.setPaymentRRN(responseBody.getReconciliationReference());
				} else {

					payfortResponse.setStatus(false);
					payfortResponse.setMessage(responseBody.getResponseMessage());
				}
			}
		} catch (RestClientException | JsonProcessingException e2) {
			LOGGER.error("inside payfortRefundcall : exception occoured during refund process:" + order.getIncrementId()
					+ " " + e2.getMessage());

			payfortResponse.setStatus(false);
			payfortResponse.setMessage(e2.getMessage());
		}

		return payfortResponse;
	}

	/**
	 * @param payfortDtfQueryRequest
	 * @param order
	 * @return
	 */
	private PayfortQueryResponse triggerPayfortQueryRestApiCall(PayfortDtfQueryRequest payfortDtfQueryRequest,
			SalesOrder order) {

		PayfortQueryResponse responseBody = null;
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity<PayfortDtfQueryRequest> requestBody = new HttpEntity<>(payfortDtfQueryRequest, requestHeaders);
		String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi"; // temp
																												// for
																												// test
		try {

			LOGGER.info("payfort url for payfort query: " + url);
			LOGGER.info("payfort query request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<PayfortQueryResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					PayfortQueryResponse.class);

			if (null != response
					&& response.getStatusCode() == HttpStatus.OK) {
				responseBody = response.getBody();

				LOGGER.info("payfort query response body:" + mapper.writeValueAsString(responseBody));

			}
		} catch (RestClientException | JsonProcessingException e2) {
			LOGGER.error("exception occoured during Query DTF rest call process:" + order.getIncrementId() + " "
					+ e2.getMessage());

		}

		return responseBody;
	}

	/**
	 * @param payfortRedundRequest
	 * @return signature
	 */
	private String getSignature(PayfortOrderRefundPayLoad payfortRedundRequest, String signatureHash) {

		String signature = null;
		String signatureRaw = new StringBuilder()
				.append("access_code=").append(payfortRedundRequest.getAccessCode())
				.append("amount=").append(payfortRedundRequest.getAmount())
				.append("command=").append(OrderConstants.REFUND_STRING)
				.append("currency=").append(payfortRedundRequest.getCurrency())
				.append("fort_id=").append(payfortRedundRequest.getFortId())
				.append("language=").append(payfortRedundRequest.getLanguage())
				.append("merchant_identifier=").append(payfortRedundRequest.getMerchantIdentifier())
				.append("merchant_reference=").append(payfortRedundRequest.getMerchantReference())
				.append("order_description=").append(payfortRedundRequest.getOrderDescription()).toString();

		signature = new StringBuilder().append(signatureHash).append(signatureRaw).append(signatureHash).toString();
		LOGGER.info("signature sequence:" + signature);

		String sha256hex = org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
		LOGGER.info("sha256hex :" + sha256hex);

		return sha256hex;
	}

	/**
	 * @param payfortDtfRequest
	 * @return signature
	 */
	private String getQtfQuerySignature(PayfortDtfQueryRequest payfortDtfRequest, String signatureHash) {

		String signature = null;
		String signatureRaw = new StringBuilder()
				.append("access_code=").append(payfortDtfRequest.getAccessCode())
				.append("language=").append(payfortDtfRequest.getLanguage())
				.append("merchant_identifier=").append(payfortDtfRequest.getMerchantIdentifier())
				.append("merchant_reference=").append(payfortDtfRequest.getMerchantReference())
				.append("query_command=").append("CHECK_STATUS")
				.toString();

		signature = new StringBuilder().append(signatureHash).append(signatureRaw).append(signatureHash).toString();
		LOGGER.info("query signature sequence:" + signature);

		String sha256hex = org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
		LOGGER.info("qury dtf sha256hex :" + sha256hex);

		return sha256hex;
	}

	/**
	 * @param storeId
	 * @param paymentMethod
	 * @param signatureHash
	 * @param merchantIdentifier
	 * @param accessCode
	 * @param language
	 */
	public PayfortConfiguration getPayfortConfDetails(String storeId, String paymentMethod,
			PayfortConfiguration configuration) {
		if (storeId.equals("1") || storeId.equals("3")) {
			setKsaDetails(storeId, paymentMethod, configuration);

		} else if (storeId.equals("7") || storeId.equals("11")) {
			setUaeDetails(storeId, paymentMethod, configuration);

		} else if (storeId.equals("12") || storeId.equals("13")) {
			setKwtDetails(storeId, paymentMethod, configuration);

		} else if (storeId.equals("15") || storeId.equals("17")) {
			setqarDetails(storeId, paymentMethod, configuration);

		} else if (storeId.equals("19") || storeId.equals("21")) {

			setBahDetails(storeId, paymentMethod, configuration);
		}

		else if (storeId.equals("23") || storeId.equals("25")) {

			setOmnDetails(storeId, paymentMethod, configuration);
		}

		return configuration;
	}

	private void setKsaDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("1") || storeId.equals("3"))
				&& paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaAppleAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaHashAppleReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaAppleMerchantIdentifier());
			configuration.setLanguage("en");
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortksaAmountMultiplier()));

		} else if ((storeId.equals("3") || storeId.equals("1"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaCardAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaCardMerchantIdentifier());
			if (storeId.equals("1")) {
				configuration.setLanguage("en");
			} else {
				configuration.setLanguage("ar");
			}

			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortksaAmountMultiplier()));

		}
	}

	private void setUaeDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("7") || storeId.equals("11"))
				&& paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeAppleAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeHashAppleReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeAppleMerchantIdentifier());
			configuration.setLanguage("en");
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortuaeAmountMultiplier()));

		}
		if ((storeId.equals("7") || storeId.equals("11"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeCardAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeCardMerchantIdentifier());
			if (storeId.equals("7")) {
				configuration.setLanguage("en");
			} else {
				configuration.setLanguage("ar");
			}
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortuaeAmountMultiplier()));

		}
	}

	private void setKwtDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("12") || storeId.equals("13"))) {
			if (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
					|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
					|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())) {
				configuration.setAccessCode(
						Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtCardAccessCode());
				configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getKwtCredentials()
						.getPayfortKwtHashCardReqTokenphrase());
				configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getKwtCredentials()
						.getPayfortKwtCardMerchantIdentifier());
				configuration.setMultiplier(Integer.parseInt(
						Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtAmountMultiplier()));
			} else if (paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {
				configuration.setAccessCode(
						Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtApplePayAccessCode());
				configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getKwtCredentials()
						.getPayfortKwtHashApplePayReqTokenPhrase());
				configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getKwtCredentials()
						.getPayfortKwtCardMerchantIdentifier());
				configuration.setMultiplier(Integer.parseInt(
						Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtAmountMultiplier()));
			}
			if (storeId.equals("12")) {
				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}

		}
	}

	private void setqarDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("15") || storeId.equals("17"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatCardAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatCardMerchantIdentifier());
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatAmountMultiplier()));

			if (storeId.equals("15")) {
				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}

		}
	}

	private void setBahDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("19") || storeId.equals("21"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahCardAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahCardMerchantIdentifier());
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahAmountMultiplier()));

			if (storeId.equals("19")) {

				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}
		}
	}

	private void setOmnDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("23") || storeId.equals("25"))) {
				if (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnCardAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnCardMerchantIdentifier());
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnAmountMultiplier()));
			} else if (paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {
				configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnApplePayAccessCode());
				configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnHashApplePayReqTokenPhrase());
				configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnAppleMerchantIdentifier());
				configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnAmountMultiplier()));
			}
			if (storeId.equals("23")) {

				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}
		}
	}

	public CalculateRefundAmountResponse calculaterefundamount(SalesOrder order, String paymentMethod,
			AmastyRmaRequest rmaRequest, RefundAmountObject refundDObject, Map<String, BigDecimal> mapSkuList,
			BigDecimal taxFactor, Stores store, String deviceId) {

		try {
			TotalRefundAmountResponse totalRefundAmountResponse = getTotalRefundAmount(rmaRequest, order, mapSkuList,
					taxFactor, store);
			ObjectMapper objectMapper = new ObjectMapper();
			LOGGER.info("total refund amount response " + objectMapper.writeValueAsString(totalRefundAmountResponse));
			BigDecimal totalRefundAmount = totalRefundAmountResponse.getTotalRefundAmount() != null
					? totalRefundAmountResponse.getTotalRefundAmount()
					: BigDecimal.ZERO;
			CalculateRefundAmountResponse calculateRefundAmountResponse = new CalculateRefundAmountResponse();
			calculateRefundAmountResponse.setTransactionDetails(totalRefundAmountResponse.getTransactionDetails());
			calculateRefundAmountResponse.setOrderNetPrice(totalRefundAmountResponse.getTransactionNetTotal());
			calculateRefundAmountResponse.setTotalQty(totalRefundAmountResponse.getTotalQty());
			BigDecimal totalAmountToBeRefunded = BigDecimal.ZERO;
			BigDecimal totalAmountToBeRefundedAsCredit = BigDecimal.ZERO;
			BigDecimal totalAmountToBeRefundedAsOnline = BigDecimal.ZERO;
			BigDecimal totalAmountToBeRefundedAsCoins = BigDecimal.ZERO;
			BigDecimal totalAmountToBeRefundedAsShukran = BigDecimal.ZERO;
			BigDecimal totalItemsReturnedAlready = BigDecimal.ZERO;
			List<Integer> allIds = new ArrayList<>();
			BigDecimal totalShukranPointsToBeReturned = new BigDecimal(0);
			BigDecimal totalReturnableItems = BigDecimal.ZERO;
			BigDecimal returnableQuantity = totalRefundAmountResponse.getReturnableQuantity();
			BigDecimal refundShukranTotalPoints = totalRefundAmountResponse.getRefundShukranTotalPoints();
			if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null
					&& order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
				TotalItemsReturnedResponse totalItemsReturnedResponse = salesOrderRMAServiceImpl
						.totalItemsReturned(order, rmaRequest);
				totalItemsReturnedAlready = totalItemsReturnedResponse.getTotalQuantity();
				allIds = totalItemsReturnedResponse.getAllIds();
			}
			int refundShukranTotal = refundShukranTotalPoints.intValue();

			AmastyRmaRequest easrmaRequestRes = new AmastyRmaRequest();
			if (store != null && (Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
				Double returnFee = (rmaRequest.getReturnFee() != null) ? rmaRequest.getReturnFee() : 0.0;
				easrmaRequestRes = eASServiceImpl.easReturnRefund(order, rmaRequest, deviceId, returnFee);
			}
			// List<SalesCreditmemo> creditMemoList =
			// salesCreditmemoRepository.findByOrderId(order.getEntityId());
			List<SalesCreditmemo> creditMemoList = orderHelper.getSalesCreditMemoList(order.getEntityId());
			BigDecimal returnEasValueInCurrency = getCalculatedCoinValue(order, easrmaRequestRes, creditMemoList);

			for (SalesOrderItem item : order.getSalesOrderItem()) {
				if (item.getProductType().equalsIgnoreCase("simple")
						&& (item.getQtyOrdered().subtract(item.getQtyCanceled())).compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal itemSubTotal = BigDecimal.ZERO;
					BigDecimal itemDiscount1 = BigDecimal.ZERO;
					if (item.getOriginalPrice() != null) {
						itemSubTotal = item.getOriginalPrice()
								.divide(taxFactor, 6, RoundingMode.HALF_UP)
								.multiply(item.getQtyOrdered());

						itemDiscount1 = (item.getOriginalPrice()
								.subtract(item.getPriceInclTax()))
								.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(item.getQtyOrdered());
					}
					BigDecimal discountAmount = BigDecimal.ZERO;

					if (item.getParentOrderItem() != null) {
						BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository
								.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(),
										item.getParentOrderItem().getItemId());
						if (subSalesOrderDiscountAmount != null) {
							discountAmount = subSalesOrderDiscountAmount;
						}
					} else if (item.getSubSalesOrderItem() != null) {

						for (SubSalesOrderItem i : item.getSubSalesOrderItem()) {
							if (i.isGiftVoucher()) {
								discountAmount = i.getDiscount();
							}
						}

					}
					LOGGER.info("discountAmount: " + discountAmount);

					if (discountAmount.compareTo(new BigDecimal(0)) > 0) {
						totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit.add(discountAmount);
					}
					BigDecimal itemDiscount2 = (item.getDiscountAmount()
							.subtract(discountAmount))
							.divide(taxFactor, 6, RoundingMode.HALF_UP);

					BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemDiscount1).subtract(itemDiscount2);
					BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor)
							.multiply(item.getQtyOrdered().subtract(item.getQtyCanceled()))
							.divide(item.getQtyOrdered(), 6, RoundingMode.HALF_UP);
					if (item.getShukranCoinsBurned() != null
							&& item.getShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0
							&& item.getReturnable() == 1) {
						totalShukranPointsToBeReturned = totalShukranPointsToBeReturned
								.add(item.getShukranCoinsBurned());
						totalReturnableItems = totalReturnableItems
								.add(item.getQtyOrdered().subtract(item.getQtyCanceled()));
					}

					totalAmountToBeRefunded = totalAmountToBeRefunded.add(itemFinalPrice);
				}

			}

			LOGGER.info("Total Amount To Be Refunded" + totalAmountToBeRefunded);

			if (refundShukranTotal > 0
					&& returnableQuantity.compareTo(totalReturnableItems.subtract(totalItemsReturnedAlready)) == 0
					&& allIds != null && !allIds.isEmpty()) {
				BigDecimal totalShukranReturned = amastyRmaRequestRepository.getAllShukranPoints(allIds);
				if (totalShukranReturned == null) {
					totalShukranReturned = BigDecimal.ZERO;
				}
				int totalShukranPoints = totalShukranPointsToBeReturned.intValue();
				boolean isOtherReturnPending = BigDecimal.valueOf(totalShukranPoints)
						.subtract(totalShukranReturned.add(BigDecimal.valueOf(5)))
						.compareTo(BigDecimal.valueOf(refundShukranTotal)) > 0;
				if (!isOtherReturnPending) {
					refundShukranTotal = totalShukranPoints - totalShukranReturned.intValue();
				}
			}

			if (null != paymentMethod && (OrderConstants.checkPaymentMethod(paymentMethod)
					|| OrderConstants.checkBNPLPaymentMethods(paymentMethod)
					|| PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod))) {
				totalAmountToBeRefundedAsOnline = totalAmountToBeRefunded;
				if (order.getSubSalesOrder().getEasValueInCurrency() != null
						&& order.getSubSalesOrder().getEasValueInCurrency().compareTo(new BigDecimal(0)) > 0) {
					totalAmountToBeRefundedAsCoins = order.getSubSalesOrder().getEasValueInCurrency();
					totalAmountToBeRefundedAsOnline = totalAmountToBeRefundedAsOnline
							.subtract(totalAmountToBeRefundedAsCoins);
				}
				if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null
						&& order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
					totalAmountToBeRefundedAsOnline = totalAmountToBeRefundedAsOnline
							.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
					totalAmountToBeRefundedAsShukran = order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
				}
				if (order.getAmstorecreditAmount() != null
						&& order.getAmstorecreditAmount().compareTo(new BigDecimal(0)) > 0) {
					totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit
							.add(order.getAmstorecreditAmount());
				}
				if (totalAmountToBeRefundedAsCredit.compareTo(new BigDecimal(0)) > 0) {
					totalAmountToBeRefundedAsOnline = totalAmountToBeRefundedAsOnline
							.subtract(totalAmountToBeRefundedAsCredit);
				}
			} else {
				totalAmountToBeRefundedAsCredit = totalAmountToBeRefunded;
				if (order.getSubSalesOrder().getEasValueInCurrency() != null
						&& order.getSubSalesOrder().getEasValueInCurrency().compareTo(new BigDecimal(0)) > 0) {
					totalAmountToBeRefundedAsCoins = order.getSubSalesOrder().getEasValueInCurrency();
					totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit
							.subtract(totalAmountToBeRefundedAsCoins);
				}
				if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null
						&& order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
					totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit
							.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
					totalAmountToBeRefundedAsShukran = order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
				}
			}

			BigDecimal divideValue = new BigDecimal(100);
			BigDecimal refundStoreCreditTotal = new BigDecimal(0);
			BigDecimal refundStoreCoinsTotal = new BigDecimal(0);
			BigDecimal beforeRefundStoreCoinsTotal = new BigDecimal(0);
			BigDecimal beforeRefundStoreShukranTotal = new BigDecimal(0);
			BigDecimal refundPrepaidTotal = new BigDecimal(0);
			BigDecimal beforeRefundStoreShukranTotalInBaseCurrency = BigDecimal.ZERO;
			BigDecimal beforeRefundStoreShukranTotalInCurrency = BigDecimal.ZERO;
			if (totalRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal percentageShareOfRefundGrandTotalCompareToOrderOriginal = totalRefundAmount
						.divide(totalAmountToBeRefunded, 6, RoundingMode.HALF_UP).multiply(divideValue);
				if (totalAmountToBeRefundedAsShukran.compareTo(BigDecimal.ZERO) > 0 && store != null) {
					beforeRefundStoreShukranTotalInCurrency = new BigDecimal(refundShukranTotal)
							.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
					beforeRefundStoreShukranTotalInBaseCurrency = new BigDecimal(refundShukranTotal)
							.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
					beforeRefundStoreShukranTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal
							.multiply(totalAmountToBeRefundedAsShukran).divide(divideValue, 6, RoundingMode.HALF_UP);
					totalRefundAmount = totalRefundAmount.subtract(beforeRefundStoreShukranTotal);
					totalAmountToBeRefunded = totalAmountToBeRefunded.subtract(totalAmountToBeRefundedAsShukran);
				}
				if (totalAmountToBeRefunded.compareTo(BigDecimal.ZERO) > 0) {
					percentageShareOfRefundGrandTotalCompareToOrderOriginal = totalRefundAmount
							.divide(totalAmountToBeRefunded, 6, RoundingMode.HALF_UP).multiply(divideValue);

				} else {
					percentageShareOfRefundGrandTotalCompareToOrderOriginal = BigDecimal.ZERO;
				}
				if (totalAmountToBeRefundedAsCredit.compareTo(BigDecimal.ZERO) > 0) {
					refundStoreCreditTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal
							.multiply(totalAmountToBeRefundedAsCredit).divide(divideValue, 6, RoundingMode.HALF_UP);
				}

				if (totalAmountToBeRefundedAsCoins.compareTo(BigDecimal.ZERO) > 0) {
					refundStoreCoinsTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal
							.multiply(totalAmountToBeRefundedAsCoins).divide(divideValue, 6, RoundingMode.HALF_UP);
					beforeRefundStoreCoinsTotal = refundStoreCoinsTotal;
				}

				if (totalAmountToBeRefundedAsOnline.compareTo(BigDecimal.ZERO) > 0) {
					refundPrepaidTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal
							.multiply(totalAmountToBeRefundedAsOnline).divide(divideValue, 6, RoundingMode.HALF_UP);
				}
			}

			refundDObject.setRefundOnlineAmount(refundPrepaidTotal.setScale(2, RoundingMode.HALF_UP));
			refundDObject.setRefundStorecreditAmount(refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP));
			refundDObject.setBaseAmastyStoreCreditAmount(refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP));
			calculateRefundAmountResponse
					.setBeforeReturnEasCoinValue(refundStoreCoinsTotal.setScale(2, RoundingMode.HALF_UP).toString());
			calculateRefundAmountResponse
					.setBeforeRefundOnlineAmount(refundPrepaidTotal.setScale(2, RoundingMode.HALF_UP).toString());
			calculateRefundAmountResponse
					.setBeforeCreditAmount(refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP).toString());
			calculateRefundAmountResponse
					.setBeforeAmastyCreditAmount(refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP).toString());
			calculateRefundAmountResponse.setBeforeCalculatedRefundAmount(totalRefundAmount.toString());
			calculateRefundAmountResponse
					.setBeforeCalculatedShukranValue(beforeRefundStoreShukranTotalInCurrency.toString());
			calculateRefundAmountResponse.setBeforeCalculatedShukranPoints(refundShukranTotal);
			if (rmaRequest.getReturnFee() != null && rmaRequest.getReturnFee() > 0) {
				BigDecimal returnFee = BigDecimal.valueOf(rmaRequest.getReturnFee());
				if ((StringUtils.isNotBlank(rmaRequest.getReturnIncPayfortId())
						&& StringUtils.isNotEmpty(rmaRequest.getReturnIncPayfortId()))
						|| returnFee.compareTo(totalRefundAmount) >= 0) {
					calculateRefundAmountResponse.setAfterReturnEasCoinValue(new BigDecimal(0).toString());
					calculateRefundAmountResponse.setAfterRefundOnlineAmount(new BigDecimal(0).toString());
					calculateRefundAmountResponse.setAfterCreditAmount(new BigDecimal(0).toString());
					calculateRefundAmountResponse.setAfterAmastyCreditAmount(new BigDecimal(0).toString());
					calculateRefundAmountResponse.setAfterCalculatedRefundAmount(new BigDecimal(0).toString());
					calculateRefundAmountResponse.setAfterCalculatedShukranPoints(
							calculateRefundAmountResponse.getBeforeCalculatedShukranPoints());
					calculateRefundAmountResponse.setAfterCalculatedRefundAmount(new BigDecimal(0).toString());
				} else if (totalRefundAmount.compareTo(returnFee) > 0) {

					BigDecimal percentageRefundPrepaid = refundPrepaidTotal
							.divide(totalRefundAmount, 6, RoundingMode.HALF_UP).multiply(divideValue)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					BigDecimal newRefundPrepaidAmountToBeDeducted = returnFee
							.divide(divideValue, 4, RoundingMode.HALF_UP)
							.multiply(percentageRefundPrepaid).setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					refundPrepaidTotal = refundPrepaidTotal.subtract(newRefundPrepaidAmountToBeDeducted);

					BigDecimal percentageRefundCredit = refundStoreCreditTotal
							.divide(totalRefundAmount, 6, RoundingMode.HALF_UP).multiply(divideValue)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					BigDecimal newRefundCreditAmountToBeDeducted = returnFee
							.divide(divideValue, 4, RoundingMode.HALF_UP)
							.multiply(percentageRefundCredit).setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					refundStoreCreditTotal = refundStoreCreditTotal.subtract(newRefundCreditAmountToBeDeducted);

					BigDecimal percentageRefundCoin = refundStoreCoinsTotal
							.divide(totalRefundAmount, 6, RoundingMode.HALF_UP).multiply(divideValue)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					BigDecimal newRefundCoinAmountToBeDeducted = returnFee.divide(divideValue, 4, RoundingMode.HALF_UP)
							.multiply(percentageRefundCoin).setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					refundStoreCoinsTotal = refundStoreCoinsTotal.subtract(newRefundCoinAmountToBeDeducted);

					if (store != null && !store.getIsShukranEnable()
							&& newRefundCoinAmountToBeDeducted.compareTo(BigDecimal.ZERO) > 0) {
						CoinAdditionData coinAdditionData = new CoinAdditionData();
						CoinAdditionDetailData coinAdditionDetailData = new CoinAdditionDetailData();
						coinAdditionDetailData.setKey("returnRefund");
						coinAdditionDetailData.setEn("returnRefund-en");
						coinAdditionDetailData.setAr("returnRefund-ar");
						coinAdditionData.setDetail(coinAdditionDetailData);
						coinAdditionData.setCustomerId(order.getCustomerId());
						coinAdditionData.setIsReturn(true);
						Integer coinsData = newRefundCoinAmountToBeDeducted.multiply(new BigDecimal(100)).intValue();
						LOGGER.info("Coins Data +" + coinsData);
						coinAdditionData.setCoins(-coinsData);
						coinAdditionData.setExpiryInDays(30);

						coinAdditionData.setStoreId(order.getStoreId());

						eASServiceImpl.removeCoinManually(coinAdditionData, deviceId);
					}

					totalRefundAmount = totalRefundAmount.subtract(returnFee);
					calculateRefundAmountResponse.setAfterReturnEasCoinValue(
							refundStoreCoinsTotal.setScale(2, RoundingMode.HALF_UP).toString());
					calculateRefundAmountResponse.setAfterRefundOnlineAmount(
							refundPrepaidTotal.setScale(2, RoundingMode.HALF_UP).toString());
					calculateRefundAmountResponse
							.setAfterCreditAmount(refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP).toString());
					calculateRefundAmountResponse.setAfterAmastyCreditAmount(
							refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP).toString());
					calculateRefundAmountResponse.setAfterCalculatedRefundAmount(totalRefundAmount.toString());
					calculateRefundAmountResponse.setAfterCalculatedShukranPoints(refundShukranTotal);
					calculateRefundAmountResponse
							.setAfterCalculatedShukranValue(beforeRefundStoreShukranTotalInCurrency.toString());

				}
			} else {
				calculateRefundAmountResponse
						.setAfterReturnEasCoinValue(refundStoreCoinsTotal.setScale(2, RoundingMode.HALF_UP).toString());
				calculateRefundAmountResponse
						.setAfterRefundOnlineAmount(refundPrepaidTotal.setScale(2, RoundingMode.HALF_UP).toString());
				calculateRefundAmountResponse
						.setAfterCreditAmount(refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP).toString());
				calculateRefundAmountResponse.setAfterAmastyCreditAmount(
						refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP).toString());
				calculateRefundAmountResponse.setAfterCalculatedRefundAmount(totalRefundAmount.toString());
				calculateRefundAmountResponse.setAfterCalculatedShukranPoints(refundShukranTotal);
				calculateRefundAmountResponse
						.setAfterCalculatedShukranValue(beforeRefundStoreShukranTotalInCurrency.toString());
			}

			if (beforeRefundStoreCoinsTotal.compareTo(BigDecimal.ZERO) > 0) {
				rmaRequest.setEasValueInBaseCurrency(beforeRefundStoreCoinsTotal);
				rmaRequest.setEasValueInCurrency(beforeRefundStoreCoinsTotal);
				// Fix: Convert currency value to coins - 1 coin = 0.1 currency (multiply by 10)
				// Example: 7.31 currency → 73.1 coins → 73 coins (as integer)
				Integer coins = beforeRefundStoreCoinsTotal.multiply(new BigDecimal(10)).setScale(0, RoundingMode.HALF_UP).intValue();
				LOGGER.info("EAS Coins Conversion - Currency: " + beforeRefundStoreCoinsTotal + " SAR → Coins: " + coins);
				rmaRequest.setEasCoins(coins);
				amastyRmaRequestRepository.saveAndFlush(rmaRequest);
			}

			if (refundShukranTotal > 0) {
				rmaRequest.setShukranPointsRefundedValueInCurrency(beforeRefundStoreShukranTotal);
				rmaRequest.setShukranPointsRefundedValueInBaseCurrency(beforeRefundStoreShukranTotalInBaseCurrency);
				rmaRequest.setShukranPointsRefunded(BigDecimal.valueOf(refundShukranTotal));
				amastyRmaRequestRepository.saveAndFlush(rmaRequest);
			}

			refundDObject.setRefundStorecreditAmount(refundStoreCreditTotal);
			refundDObject.setBaseAmastyStoreCreditAmount(refundStoreCreditTotal);
			refundDObject.setRefundOnlineAmount(refundPrepaidTotal);

			BigDecimal shippingAmount = BigDecimal.ZERO;
			if (null != order.getShippingAmount()) {
				shippingAmount = order.getShippingAmount();
				refundDObject.setShippingAmount(shippingAmount);
			}
			BigDecimal donationAmount = BigDecimal.ZERO;
			if (null != order.getSubSalesOrder()
					&& null != order.getSubSalesOrder().getDonationAmount()) {

				donationAmount = order.getSubSalesOrder().getDonationAmount();
				refundDObject.setDonationAmount(donationAmount);
			}
			//

			// String returnAmount = null;
			//
			// BigDecimal amastyStoreCreditAmount = BigDecimal.ZERO;
			//
			// BigDecimal donationAmount = BigDecimal.ZERO;
			// BigDecimal differenceRefundTotal = BigDecimal.ZERO;
			//
			// BigDecimal differenceRefundTotalOriginal = BigDecimal.ZERO;
			//

			// if (null != order.getAmstorecreditAmount()) {
			//
			// amastyStoreCreditAmount = order.getAmstorecreditAmount();
			//
			// }
			//
			//
			//
			// BigDecimal originalRefundAmount= totalRefundAmount;

			// List<SalesCreditmemo> creditMemoList =
			// salesCreditmemoRepository.findByOrderId(order.getEntityId()) ;
			//
			// BigDecimal returnEasValueInCurrency = getCalculatedCoinValue(order,
			// easrmaRequestRes, creditMemoList);
			// if(returnEasValueInCurrency != null && returnEasValueInCurrency.compareTo(new
			// BigDecimal(0))>0){
			// calculateRefundAmountResponse.setBeforeReturnEasCoinValue(returnEasValueInCurrency.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// }
			// boolean isRefundApplicable= false;
			// BigDecimal divideValue= new BigDecimal(100);
			// if(totalRefundAmount != null && rmaRequest.getReturnFee() > 0 &&
			// rmaRequest.getReturnFee() < totalRefundAmount.doubleValue()){
			// BigDecimal newReturnFee= BigDecimal.valueOf(rmaRequest.getReturnFee());
			// isRefundApplicable= true;
			// if(returnEasValueInCurrency!= null && returnEasValueInCurrency.compareTo(new
			// BigDecimal(0)) >0){
			//
			// if(totalRefundAmount.compareTo(returnEasValueInCurrency)>0) {
			// BigDecimal percentageRefundCoin = returnEasValueInCurrency
			// .divide(totalRefundAmount, 6,
			// RoundingMode.HALF_UP).multiply(divideValue).setScale(2, RoundingMode.HALF_UP)
			// .setScale(4, RoundingMode.HALF_UP);
			//
			// BigDecimal newRefundCoinAmountToBeDeducted = newReturnFee.divide(divideValue,
			// 4, RoundingMode.HALF_UP)
			// .multiply(percentageRefundCoin).setScale(2, RoundingMode.HALF_DOWN)
			// .setScale(4, RoundingMode.HALF_DOWN);
			//
			// returnEasValueInCurrency =
			// returnEasValueInCurrency.subtract(newRefundCoinAmountToBeDeducted);
			// LOGGER.info("newRefundCoinAmountToBeDeducted + " +
			// newRefundCoinAmountToBeDeducted.doubleValue());
			//

			//
			// }
			//
			//
			// }
			//
			// totalRefundAmount= totalRefundAmount.subtract(newReturnFee);
			// }
			//
			//
			// if(returnEasValueInCurrency!= null && returnEasValueInCurrency.compareTo(new
			// BigDecimal(0))>0){
			// calculateRefundAmountResponse.setAfterReturnEasCoinValue(returnEasValueInCurrency.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// }
			//
			// LOGGER.info("calculateRefundAmountResponse.getBeforeReturnEasCoinValue(): " +
			// calculateRefundAmountResponse.getBeforeReturnEasCoinValue() +" "+
			// calculateRefundAmountResponse.getAfterReturnEasCoinValue());
			// if (totalRefundAmount != null && returnEasValueInCurrency !=null &&
			// StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeReturnEasCoinValue())
			// &&
			// StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeReturnEasCoinValue())
			// && new
			// BigDecimal(calculateRefundAmountResponse.getBeforeReturnEasCoinValue()).compareTo(new
			// BigDecimal(0))>0) {
			// totalRefundAmount = totalRefundAmount.subtract(returnEasValueInCurrency);
			// originalRefundAmount= originalRefundAmount.subtract(new
			// BigDecimal(calculateRefundAmountResponse.getBeforeReturnEasCoinValue()));
			//
			//
			// }
			//
			//
			// if (OrderConstants.checkPaymentMethod(paymentMethod) ||
			// OrderConstants.checkBNPLPaymentMethods(paymentMethod)
			// || paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE)) {
			//
			// BigDecimal divideVal = new BigDecimal(100);
			// BigDecimal refundGrandTotal = BigDecimal.ZERO;
			// BigDecimal refundGrandTotalOriginal = BigDecimal.ZERO;
			//
			// BigDecimal totalAmount = order.getGrandTotal().add(amastyStoreCreditAmount);
			//
			//
			// BigDecimal cancelPercenatgeShare = totalRefundAmount
			// .divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
			// .setScale(4, RoundingMode.HALF_UP);
			//
			// BigDecimal cancelPercenatgeShareOriginal = originalRefundAmount
			// .divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
			// .setScale(4, RoundingMode.HALF_UP);
			//
			//
			// refundGrandTotal = order.getGrandTotal().divide(divideVal, 4,
			// RoundingMode.HALF_UP)
			// .multiply(cancelPercenatgeShare).setScale(2, RoundingMode.HALF_UP)
			// .setScale(4, RoundingMode.HALF_UP);
			//
			// refundGrandTotalOriginal= order.getGrandTotal().divide(divideVal, 4,
			// RoundingMode.HALF_UP)
			// .multiply(cancelPercenatgeShareOriginal).setScale(2, RoundingMode.HALF_UP)
			// .setScale(4, RoundingMode.HALF_UP);
			//
			// BigDecimal returnedStoreCreditAMount =
			// amastyStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
			// .multiply(cancelPercenatgeShare)
			// .setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
			//
			// BigDecimal returnedStoreCreditAMountOriginal =
			// amastyStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
			// .multiply(cancelPercenatgeShareOriginal)
			// .setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
			//
			//
			// if(CollectionUtils.isNotEmpty(creditMemoList)) {
			//
			// BigDecimal totalCreditmemoRefunded = BigDecimal.ZERO;
			// BigDecimal totalmastyStoreCredit = BigDecimal.ZERO;
			// BigDecimal differenceAmount = BigDecimal.ZERO;
			// BigDecimal differenceAmountOriginal = BigDecimal.ZERO;
			//
			//
			// for (SalesCreditmemo memo : creditMemoList) {
			// if(null != memo.getGrandTotal()) {
			// totalCreditmemoRefunded = totalCreditmemoRefunded.add(memo.getGrandTotal());
			// }
			// if(null != memo.getAmstorecreditAmount()) {
			// totalmastyStoreCredit =
			// totalmastyStoreCredit.add(memo.getAmstorecreditAmount());
			// }
			// }
			//
			// if(!(totalCreditmemoRefunded.compareTo(BigDecimal.ZERO) == 0)
			// || !(totalmastyStoreCredit.compareTo(BigDecimal.ZERO) == 0)) {
			//
			// BigDecimal totalOnlineTobeRefund =
			// totalCreditmemoRefunded.add(refundGrandTotal);
			//
			// BigDecimal totalOnlineTobeRefundOriginal =
			// totalCreditmemoRefunded.add(refundGrandTotalOriginal);
			//
			// SalesInvoice salesInvoice =
			// order.getSalesInvoices().stream().findFirst().orElse(null);
			//
			// if(null != salesInvoice ) {
			// boolean differenceFlag = false;
			// BigDecimal invoceGrandTotalAmount = salesInvoice.getGrandTotal();
			//
			// if(null != invoceGrandTotalAmount &&
			// invoceGrandTotalAmount.compareTo(totalOnlineTobeRefund) == -1){
			//
			// differenceAmount = totalOnlineTobeRefund.subtract(invoceGrandTotalAmount);
			// differenceAmountOriginal
			// =totalOnlineTobeRefundOriginal.subtract(invoceGrandTotalAmount);
			// BigDecimal diffentConstantAmount = new BigDecimal(1);
			// if(differenceAmount.compareTo(diffentConstantAmount) == -1) {
			//
			// differenceRefundTotal = refundGrandTotal.subtract(differenceAmount);
			// differenceRefundTotalOriginal =
			// refundGrandTotalOriginal.subtract(differenceAmountOriginal);
			// differenceFlag = true;
			// }
			//
			// }if(!(differenceAmount.compareTo(BigDecimal.ZERO) == 0) && differenceFlag){
			//
			// returnedStoreCreditAMount = returnedStoreCreditAMount.add(differenceAmount);
			// returnedStoreCreditAMountOriginal=
			// returnedStoreCreditAMountOriginal.add(differenceAmount);
			// }
			//
			// }
			// }
			//
			//
			// }
			//
			// if (null != order.getStoreToBaseRate()) {
			//
			// refundDObject.setRefundStorecreditAmount(returnedStoreCreditAMount);
			// refundDObject.setBaseAmastyStoreCreditAmount(
			// returnedStoreCreditAMount.multiply(order.getStoreToBaseRate())
			// .setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP));
			// calculateRefundAmountResponse.setAfterCreditAmount(returnedStoreCreditAMount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// calculateRefundAmountResponse.setAfterAmastyCreditAmount(returnedStoreCreditAMount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// calculateRefundAmountResponse.setBeforeCreditAmount(returnedStoreCreditAMountOriginal.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// calculateRefundAmountResponse.setBeforeAmastyCreditAmount(returnedStoreCreditAMountOriginal.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// }
			//
			//
			//
			// if(!(differenceRefundTotal.compareTo(BigDecimal.ZERO) == 0)){
			// LOGGER.info("Coins currency Value: " + returnEasValueInCurrency + ",
			// differenceRefundTotal: " + differenceRefundTotal);
			//
			// returnAmount = differenceRefundTotal.toString();
			// refundDObject.setRefundOnlineAmount(differenceRefundTotal);
			// calculateRefundAmountResponse.setBeforeRefundOnlineAmount(differenceRefundTotalOriginal.setScale(2,
			// RoundingMode.HALF_UP).toString());
			//
			//
			//
			// }else {
			// LOGGER.info("Coins currency Value: " + returnEasValueInCurrency + ",
			// refundGrandTotal: " + refundGrandTotal);
			// calculateRefundAmountResponse.setBeforeRefundOnlineAmount(refundGrandTotalOriginal.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// calculateRefundAmountResponse.setAfterRefundOnlineAmount(refundGrandTotal.setScale(2,
			// RoundingMode.HALF_UP).toString());
			//
			//
			// returnAmount = refundGrandTotal.toString();
			// }
			// } else {
			// LOGGER.info("Coins currency Value: " +
			// easrmaRequestRes.getEasValueInCurrency() + ", refundGrandTotal: " +
			// totalRefundAmount);
			//
			//
			// assert totalRefundAmount != null;
			// returnAmount= totalRefundAmount.toString();
			//
			// refundDObject.setRefundStorecreditAmount(totalRefundAmount);
			// refundDObject.setBaseAmastyStoreCreditAmount(totalRefundAmount.multiply(order.getStoreToBaseRate().setScale(2,
			// RoundingMode.HALF_UP)
			// .setScale(4,RoundingMode.HALF_UP)));
			// calculateRefundAmountResponse.setBeforeCalculatedRefundAmount(originalRefundAmount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// calculateRefundAmountResponse.setAfterCreditAmount(returnAmount);
			// calculateRefundAmountResponse.setAfterAmastyCreditAmount(returnAmount);
			// calculateRefundAmountResponse.setBeforeCreditAmount(originalRefundAmount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// calculateRefundAmountResponse.setBeforeAmastyCreditAmount(originalRefundAmount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			//
			// }
			//
			// LOGGER.info("refundObject: " + refundDObject);
			// calculateRefundAmountResponse.setAfterCalculatedRefundAmount(returnAmount);
			// if(isRefundApplicable){
			// BigDecimal beforeTotalRefundAmount= new
			// BigDecimal(returnAmount).add(BigDecimal.valueOf(rmaRequest.getReturnFee()));
			// if(StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeReturnEasCoinValue())
			// &&
			// StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeReturnEasCoinValue())
			// && new
			// BigDecimal(calculateRefundAmountResponse.getBeforeReturnEasCoinValue()).compareTo(new
			// BigDecimal(0))>0){
			// beforeTotalRefundAmount= beforeTotalRefundAmount.subtract(new
			// BigDecimal(calculateRefundAmountResponse.getBeforeReturnEasCoinValue()));
			// }
			// calculateRefundAmountResponse.setBeforeCalculatedRefundAmount(beforeTotalRefundAmount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// }else{
			// calculateRefundAmountResponse.setBeforeCalculatedRefundAmount(totalRefundAmount.setScale(2,
			// RoundingMode.HALF_UP).toString());
			// }
			//

			return calculateRefundAmountResponse;
		} catch (JsonProcessingException e) {
			LOGGER.info("exception error in calculate refund amount" + e.getMessage());
			throw new RuntimeException(e);
		} catch (Exception ex) {
			LOGGER.info("error in calculate refund amount" + ex.getMessage());
			throw new RuntimeException(ex);
		}
	}

	/**
	 * @param amastyRequest
	 * @param order
	 * @return
	 */
	private TotalRefundAmountResponse getTotalRefundAmount(AmastyRmaRequest amastyRequest, SalesOrder order,
			Map<String, BigDecimal> mapSkuList, BigDecimal taxFactor, Stores store) {

		try {
			BigDecimal totalRefundAmount = BigDecimal.ZERO;
			BigDecimal qty = BigDecimal.ZERO;
			Integer lineNumber = 1;
			List<ShukranEarnItem> shukranEarnItems = new ArrayList<>();
			int totalQty = 0;
			BigDecimal transactionNetTotal = BigDecimal.ZERO;
			TotalRefundAmountResponse totalRefundAmountResponse = new TotalRefundAmountResponse();
			BigDecimal returnableQuantity = BigDecimal.ZERO;
			BigDecimal refundShukranTotalPoints = new BigDecimal(0);
			Map<Integer, List<SalesOrderItem>> childrenByParentId = order.getSalesOrderItem().stream()
					.filter(item -> item.getParentOrderItem() != null)
					.collect(Collectors.groupingBy(
							item -> order.getIsSplitOrder().equals(1) ? item.getParentOrderItem().getItemId()
									: item.getItemId()));

			// Same order as clawback root: 1) two splits both delivered -> sales_order; 2) else resolve by return item SKU -> that split; 3) else first split.
			String originalTransactionIncrementId = order.getIncrementId();
			Timestamp originalTransactionDateTime = order.getCreatedAt();
			boolean twoSplitsBothDelivered = false;
			if (Integer.valueOf(1).equals(order.getIsSplitOrder())) {
				List<SplitSalesOrder> splitOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId());
				if (splitOrders == null) {
					splitOrders = Collections.emptyList();
				}
				twoSplitsBothDelivered = splitOrders.size() == 2
						&& splitOrders.stream().allMatch(s -> OrderConstants.DELIVERED_ORDER_STATUS.equalsIgnoreCase(s.getStatus()));
				if (twoSplitsBothDelivered) {
					originalTransactionIncrementId = order.getIncrementId();
					originalTransactionDateTime = order.getCreatedAt();
					LOGGER.info("Clawback TransactionDetails OriginalTransaction: orderId=" + order.getEntityId() + ", split_sales_order has exactly two records and both status=delivered, using sales_order incrementId=" + originalTransactionIncrementId + ", createdAt=" + originalTransactionDateTime);
				} else {
					String returnItemSku = null;
					if (CollectionUtils.isNotEmpty(amastyRequest.getAmastyRmaRequestItems())) {
						Integer firstOrderItemId = amastyRequest.getAmastyRmaRequestItems().iterator().next().getOrderItemId();
						SalesOrderItem orderItem = order.getSalesOrderItem().stream().filter(i -> firstOrderItemId.equals(i.getItemId())).findFirst().orElse(null);
						if (orderItem != null) {
							SalesOrderItem parentItem = orderItem.getParentOrderItem();
							returnItemSku = (parentItem != null ? parentItem.getSku() : orderItem.getSku());
						}
					}
					SplitSalesOrderItem splitItemBySku = (StringUtils.isNotBlank(returnItemSku)) ? splitSalesOrderItemRepository.findFirstBySalesOrder_EntityIdAndProductTypeAndSku(order.getEntityId(), "configurable", returnItemSku) : null;
					if (splitItemBySku != null && splitItemBySku.getSplitSalesOrder() != null) {
						SplitSalesOrder splitForReturnItem = splitItemBySku.getSplitSalesOrder();
						originalTransactionIncrementId = StringUtils.isNotBlank(splitForReturnItem.getIncrementId()) ? splitForReturnItem.getIncrementId() : originalTransactionIncrementId;
						originalTransactionDateTime = splitForReturnItem.getCreatedAt() != null ? splitForReturnItem.getCreatedAt() : originalTransactionDateTime;
					} else {
						if (CollectionUtils.isNotEmpty(splitOrders)) {
							SplitSalesOrder firstSplit = splitOrders.get(0);
							if (StringUtils.isNotBlank(firstSplit.getIncrementId())) {
								originalTransactionIncrementId = firstSplit.getIncrementId();
							}
							if (firstSplit.getCreatedAt() != null) {
								originalTransactionDateTime = firstSplit.getCreatedAt();
							}
						}
					}
				}
			}

			for (AmastyRmaRequestItem amastyItem : amastyRequest.getAmastyRmaRequestItems()) {

				ShukranEarnItem shukranEarnItem = new ShukranEarnItem();
				if (null != amastyItem.getItemStatus() && !(amastyItem.getItemStatus().equals(12) ||
						amastyItem.getItemStatus().equals(13))) {

					LOGGER.info(" order item id:" + amastyItem.getOrderItemId());
					LOGGER.info(" order  actal requested qty:" + amastyItem.getQty());
					LOGGER.info(" order  actal returned qty:" + amastyItem.getActualQuantyReturned());

					BigDecimal qcFailedQty = BigDecimal.ZERO;

					if (amastyItem.getItemStatus().equals(25)
							&& amastyItem.getQcFailedQty() != null
							&& amastyItem.getQcFailedQty() == 0
							&& (amastyItem.getActualQuantyReturned() == null
									|| amastyItem.getActualQuantyReturned().equals(0))) {

						continue;

					}
					if (null != amastyItem.getActualQuantyReturned()) {

						qty = new BigDecimal(amastyItem.getActualQuantyReturned());

						if (null != amastyItem.getQcFailedQty() && amastyItem.getQcFailedQty().intValue() > 0) {

							qcFailedQty = new BigDecimal(amastyItem.getQcFailedQty());

						}

					} else {

						qty = amastyItem.getQty();

						if (null != amastyItem.getQcFailedQty() && amastyItem.getQcFailedQty().intValue() > 0) {

							qcFailedQty = new BigDecimal(amastyItem.getQcFailedQty());

							qty = qty.subtract(qcFailedQty);
						}

					}

					List<SalesOrderItem> children = childrenByParentId.get(amastyItem.getOrderItemId());
					SalesOrderItem orderItem = (children != null && !children.isEmpty()) ? children.get(0) : null;
					LOGGER.info("order item data " + mapper.writeValueAsString(orderItem));

					if (null != orderItem) {
						SalesOrderItem parentItem = orderItem.getParentOrderItem();
						LOGGER.info("order parent item data " + mapper.writeValueAsString(parentItem));
						BigDecimal individualValue = BigDecimal.ZERO;
						if (parentItem != null) {
							if (parentItem.getOriginalPrice() != null) {
								BigDecimal itemSubTotal = parentItem.getOriginalPrice()
										.divide(taxFactor, 6, RoundingMode.HALF_UP)
										.multiply(parentItem.getQtyOrdered());
								BigDecimal itemDiscount1 = (parentItem.getOriginalPrice()
										.subtract(parentItem.getPriceInclTax()))
										.divide(taxFactor, 6, RoundingMode.HALF_UP)
										.multiply(parentItem.getQtyOrdered());
								BigDecimal discountAmount = BigDecimal.ZERO;
								if (parentItem.getParentOrderItem() != null) {
									BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository
											.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(),
													parentItem.getParentOrderItem().getItemId());
									if (subSalesOrderDiscountAmount != null) {
										discountAmount = subSalesOrderDiscountAmount;
									}
								} else if (parentItem.getSubSalesOrderItem() != null) {

									for (SubSalesOrderItem i : parentItem.getSubSalesOrderItem()) {
										if (i.isGiftVoucher()) {
											discountAmount = i.getDiscount();
										}
									}

								}
								BigDecimal itemDiscount2 = (parentItem.getDiscountAmount()
										.subtract(discountAmount))
										.divide(taxFactor, 6, RoundingMode.HALF_UP);

								BigDecimal itemFinalDiscount = itemDiscount1.add(itemDiscount2);
								BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemFinalDiscount);
								BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor);
								individualValue = itemFinalPrice.divide(parentItem.getQtyOrdered(), 6,
										RoundingMode.HALF_UP);
								individualValue = individualValue.multiply(qty).setScale(4, RoundingMode.HALF_UP);
								returnableQuantity = returnableQuantity.add(qty);
								if (parentItem.getShukranCoinsBurned() != null
										&& parentItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
									refundShukranTotalPoints = refundShukranTotalPoints
											.add(parentItem.getShukranCoinsBurned()
													.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_UP)
													.multiply(qty).setScale(2, RoundingMode.HALF_UP));
								}
								if (qty.intValue() > 0) {
									shukranEarnItem.setDollarValueGross(0);
									shukranEarnItem.setShippingAndHandlingAmount(0);
									BigDecimal itemDollarValueNet = itemTaxablePrice
											.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_UP).multiply(qty)
											.setScale(2, RoundingMode.HALF_UP);
									shukranEarnItem.setDollarValueNet(itemDollarValueNet.negate());
									transactionNetTotal = transactionNetTotal.add(itemDollarValueNet);
									// shukranEarnItem.setTaxAmount((itemFinalPrice.subtract(itemTaxablePrice)).divide(parentItem.getQtyOrdered(),
									// 6, RoundingMode.HALF_UP).setScale(2,
									// RoundingMode.HALF_UP).multiply(qty).negate());
									shukranEarnItem.setTaxAmount(BigDecimal.ZERO);
									// shukranEarnItem.setDiscountAmount(itemFinalDiscount.divide(parentItem.getQtyOrdered(),
									// 6, RoundingMode.HALF_UP).multiply(qty).setScale(2,
									// RoundingMode.HALF_UP).negate());
									shukranEarnItem.setDiscountAmount(BigDecimal.ZERO);
									shukranEarnItem.setItemNumber(parentItem.getSku());
									shukranEarnItem.setItemNumberTypeCode(Constants.getShukranItemTypeCode());
									shukranEarnItem
											.setItemDescription(StringUtils.isNotBlank(parentItem.getDescription())
													&& StringUtils.isNotEmpty(parentItem.getDescription())
															? parentItem.getDescription()
															: "");
									shukranEarnItem.setQuantity(qty.intValue());
									shukranEarnItem.setLineNumber(lineNumber);
									shukranEarnItem.setFulfillStoreCode(order.getSubSalesOrder().getShukranStoreCode());
									shukranEarnItem.setTransactionDateTime(timeStampData());
									shukranEarnItem.setTransactionNumber(
											Constants.getShukranEnrollmentCommonCode() + amastyRequest.getRmaIncId());
									shukranEarnItem
											.setOriginalStoreCode(order.getSubSalesOrder().getShukranStoreCode());
									// Use default (parent or resolved split). Only resolve by this line's SKU when not "two splits both delivered".
									String lineOriginalTransactionIncrementId = originalTransactionIncrementId;
									Timestamp lineOriginalTransactionDateTime = originalTransactionDateTime;
									if (!twoSplitsBothDelivered && Integer.valueOf(1).equals(order.getIsSplitOrder()) && StringUtils.isNotBlank(parentItem.getSku())) {
										SplitSalesOrderItem lineSplitItem = splitSalesOrderItemRepository.findFirstBySalesOrder_EntityIdAndProductTypeAndSku(order.getEntityId(), "configurable", parentItem.getSku());
										if (lineSplitItem != null && lineSplitItem.getSplitSalesOrder() != null) {
											SplitSalesOrder lineSplit = lineSplitItem.getSplitSalesOrder();
											lineOriginalTransactionIncrementId = StringUtils.isNotBlank(lineSplit.getIncrementId()) ? lineSplit.getIncrementId() : lineOriginalTransactionIncrementId;
											lineOriginalTransactionDateTime = lineSplit.getCreatedAt() != null ? lineSplit.getCreatedAt() : lineOriginalTransactionDateTime;
										}
									}
									shukranEarnItem.setOriginalTransactionNumber(
											Constants.getShukranEnrollmentCommonCode() + lineOriginalTransactionIncrementId);
									shukranEarnItem.setOriginalTransactionDateTime(lineOriginalTransactionDateTime != null ? formatTimestampToIsoUtc(lineOriginalTransactionDateTime) : null);
									shukranEarnItem.setUom(null);
									ShukranEarnItemDetails shukranEarnItemDetails = getShukranEarnItemDetails(store,
											parentItem);
									shukranEarnItem.setJsonExternalData(shukranEarnItemDetails);
									shukranEarnItems.add(shukranEarnItem);
									totalQty = totalQty + qty.intValue();
								}
							} else {
								individualValue = orderItem.getPriceInclTax();
								if (null != orderItem.getDiscountAmount()
										&& !(orderItem.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {
									LOGGER.info(orderItem.getDiscountAmount());
									BigDecimal indivisualDiscount = orderItem.getDiscountAmount()
											.divide(orderItem.getQtyOrdered(), 6, RoundingMode.HALF_DOWN)
											.setScale(4, RoundingMode.HALF_DOWN);
									BigDecimal actualDisAmount = indivisualDiscount.multiply(qty)
											.setScale(2, RoundingMode.HALF_UP)
											.setScale(4, RoundingMode.HALF_UP);
									if (!(actualDisAmount.compareTo(orderItem.getPriceInclTax()) == 0)) {

										individualValue = individualValue.multiply(qty)
												.setScale(2, RoundingMode.HALF_UP)
												.setScale(4, RoundingMode.HALF_UP)
												.subtract(actualDisAmount);

									} else {

										individualValue = BigDecimal.ZERO;
										continue;
									}
								} else {

									individualValue = individualValue.multiply(qty).setScale(2, RoundingMode.HALF_UP)
											.setScale(4,
													RoundingMode.HALF_UP);
								}
							}
						}

						if (null != individualValue) {

							totalRefundAmount = totalRefundAmount.add(individualValue);

						}
						BigDecimal qtyRefunded = BigDecimal.ZERO;
						if (null != orderItem.getQtyRefunded()) {
							qtyRefunded = orderItem.getQtyRefunded();
						}
						if (!(qcFailedQty.compareTo(BigDecimal.ZERO) == 0)) {

							BigDecimal totalQcFailed = qtyRefunded.add(qty).add(qcFailedQty);
							if (totalQcFailed.compareTo(orderItem.getQtyOrdered()) == 0
									|| totalQcFailed.compareTo(orderItem.getQtyOrdered()) == -1) {
								orderItem.setQtyRefunded(qtyRefunded.add(qty).add(qcFailedQty));
								if (null != parentItem) {
									parentItem.setQtyRefunded(qtyRefunded.add(qty).add(qcFailedQty));
								}

							}

						} else {

							orderItem.setQtyRefunded(qtyRefunded.add(qty));
							if (null != parentItem) {
								parentItem.setQtyRefunded(qtyRefunded.add(qty));
							}
						}
						mapSkuList.put(orderItem.getSku(), qty);

					}
				}
				lineNumber++;

			}
			salesOrderRepository.saveAndFlush(order);
			totalRefundAmountResponse.setReturnableQuantity(returnableQuantity);
			totalRefundAmountResponse.setRefundShukranTotalPoints(refundShukranTotalPoints);
			totalRefundAmountResponse.setTotalRefundAmount(totalRefundAmount);
			LOGGER.info("shukran earn items " + mapper.writeValueAsString(shukranEarnItems));
			totalRefundAmountResponse.setTransactionDetails(shukranEarnItems);
			totalRefundAmountResponse.setTotalQty(totalQty);
			totalRefundAmountResponse.setTransactionNetTotal(transactionNetTotal);
			return totalRefundAmountResponse;
		} catch (Exception e) {
			LOGGER.info("total refund amount error " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@NotNull
	private static ShukranEarnItemDetails getShukranEarnItemDetails(Stores store, SalesOrderItem parentItem) {
		ShukranEarnItemDetails shukranEarnItemDetails = new ShukranEarnItemDetails();
		shukranEarnItemDetails.setLmsmultiplier(null);
		shukranEarnItemDetails.setItemDescription(null);
		shukranEarnItemDetails.setPromoCode(null);
		shukranEarnItemDetails.setItemDescription_AR(parentItem.getName());
		shukranEarnItemDetails.setSaleFlag(parentItem.getOnSale() != null && parentItem.getOnSale() ? "Y" : "N");
		shukranEarnItemDetails.setConceptCode(Constants.getShukranEnrollmentConceptCode());
		shukranEarnItemDetails.setDepartmentCode(parentItem.getShukranL4Category());
		shukranEarnItemDetails.setProductName(Constants.orderCredentials.getShukranBasicItemName());
		shukranEarnItemDetails.setIsBeautyBay("N");
		shukranEarnItemDetails.setInvoiceTerritory(store.getInvoiceTerritory());
		return shukranEarnItemDetails;
	}

	/**
	 * @param order
	 * @param styliCreditAmount
	 */
	public void addStoreCredit(SalesOrder order, BigDecimal styliCreditAmount, boolean isGiftVoucher) {

		LOGGER.info("Styli Credit Amount: " + styliCreditAmount);
		long currTime = new Date().getTime() / 1000;
		BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
		bulkWalletUpdate.setEmail(order.getCustomerEmail());
		bulkWalletUpdate.setCustomerId(order.getCustomerId());
		bulkWalletUpdate.setStore_id(order.getStoreId());
		bulkWalletUpdate.setAmount_to_be_refunded(styliCreditAmount);
		bulkWalletUpdate.setOrder_no(order.getIncrementId());
		// bulkWalletUpdate.setComment("Pushed from java-service");
		// IMP:: do not change initiatedBy value
		bulkWalletUpdate.setInitiatedBy("java-api");
		bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
		bulkWalletUpdate.setJobId("JAVA Service");
		if (isGiftVoucher) {
			bulkWalletUpdate.setReturnableToBank(false);
		} else {
			bulkWalletUpdate.setReturnableToBank(true);
		}

		LOGGER.info("addStoreCredit => " + bulkWalletUpdate.toString());
		paymentDtfHelper.publishSCToKafka(bulkWalletUpdate);

	}

	public void addStoreCreditForSplitOrder(SplitSalesOrder order, BigDecimal styliCreditAmount,
			boolean isGiftVoucher) {

		LOGGER.info("Styli Credit Amount: " + styliCreditAmount);
		long currTime = new Date().getTime() / 1000;
		BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
		bulkWalletUpdate.setEmail(order.getCustomerEmail());
		bulkWalletUpdate.setCustomerId(order.getCustomerId());
		bulkWalletUpdate.setStore_id(order.getStoreId());
		bulkWalletUpdate.setAmount_to_be_refunded(styliCreditAmount);
		bulkWalletUpdate.setOrder_no(order.getIncrementId());
		// bulkWalletUpdate.setComment("Pushed from java-service");
		// IMP:: do not change initiatedBy value
		bulkWalletUpdate.setInitiatedBy("java-api");
		bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
		bulkWalletUpdate.setJobId("JAVA Service");
		if (isGiftVoucher) {
			bulkWalletUpdate.setReturnableToBank(false);
		} else {
			bulkWalletUpdate.setReturnableToBank(true);
		}

		LOGGER.info("addStoreCredit => " + bulkWalletUpdate.toString());
		paymentDtfHelper.publishSCToKafka(bulkWalletUpdate);

	}

	public void createReturnRma(SalesOrder order, String paymentMethod, String refundAmount,
			AmastyRmaRequest returnRequest, RefundAmountObject refundAmountDetails, Map<String, BigDecimal> mapSkuList,
			String msgString, RefundPaymentRespone refundResponse, String totalRefundOnlineAmount,
			String totalAmastyCreditAmount, BigDecimal totalAmountToShowInSMS) {

		String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
		SalesCreditmemo memo = refundHelper.createReturnCreditMemo(order, memoIncrementId, refundAmount, paymentMethod,
				returnRequest.getRequestId().toString(), refundAmountDetails, mapSkuList, msgString, refundResponse,
				totalRefundOnlineAmount, totalAmastyCreditAmount, returnRequest.getReturnFee(),
				returnRequest.getReturnInvoiceAmount(), totalAmountToShowInSMS);
		refundHelper.createRefundCreditmemoItems(order, memo, returnRequest, mapSkuList);
		// ZATCA start creditMemo
		if (Constants.getZatcaFlag(order.getStoreId())) {
			SalesInvoice invoice = order.getSalesInvoices().stream().findFirst().orElse(null);
			zatcaServiceImpl.sendZatcaCreditMemo(memo, invoice, order, null, false, null, null, null, null);
		}
		// ZATCA Ends creditMemo

		ZatcaConfig zatcaConfig = Constants.orderCredentials.getZatcaConfig();
		if (returnRequest.getReturnFee() != null && returnRequest.getReturnFee() > 0
				&& zatcaConfig.getSecondReturnZatcainvoice()) {
			zatcaServiceImpl.sendZatcaInvoice(order, true, returnRequest);
		}
		if (StringUtils.isNotBlank(msgString)) {
			refundHelper.createCreditmemoFailComment(memo, refundAmountDetails.getRefundStorecreditAmount(), msgString);
		} else {

			refundHelper.createCreditmemoComment(memo, refundAmountDetails.getRefundStorecreditAmount());
		}
	}

	/**
	 * @param order
	 * @return
	 */
	public BigDecimal getCanceledAmount(SalesOrder order) {

		BigDecimal grandTotal = BigDecimal.ZERO;

		BigDecimal qty = BigDecimal.ZERO;
		for (SalesOrderItem orderItem : order.getSalesOrderItem()) {

			if (null != orderItem.getQtyCanceled() && orderItem.getProductType().equalsIgnoreCase("simple")) {

				if (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() != 0) {
					qty = orderItem.getQtyCanceled();
				} else {
					qty = orderItem.getQtyOrdered();
				}

				BigDecimal indivisualValue = orderItem.getPriceInclTax().multiply(qty);

				if (null != orderItem.getDiscountAmount()) {

					indivisualValue = indivisualValue.subtract(orderItem.getDiscountAmount());
				}
				grandTotal = grandTotal.add(indivisualValue);
			}

		}
		if (null != order.getAmstorecreditAmount()
				&& (order.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) == 0)) {

			grandTotal = order.getGrandTotal();

		}
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {

			BigDecimal paidTotal = order.getGrandTotal().subtract(order.getSubSalesOrder().getDonationAmount());

			if (paidTotal.compareTo(grandTotal) == 1) {

				grandTotal = paidTotal;
			}
		}

		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {
			LOGGER.info("cancel -this order has donation amount");

			BigDecimal donationAmount = order.getSubSalesOrder().getDonationAmount();

			if (null != order.getAmstorecreditAmount()) {

				BigDecimal amastyStoreCreditAmount = order.getAmstorecreditAmount();

				BigDecimal divideVal = new BigDecimal(100);

				BigDecimal totalAmount = grandTotal.add(amastyStoreCreditAmount);

				BigDecimal donationPercenatgeShare = donationAmount.divide(totalAmount, 6, RoundingMode.HALF_UP)
						.multiply(divideVal).setScale(4, RoundingMode.HALF_UP);

				BigDecimal refundGrandTotal = grandTotal.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(donationPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				grandTotal = grandTotal.subtract(refundGrandTotal);

			} else {
				//
				// if(null != order.getShippingAmount()) {
				//
				// grandTotal = grandTotal.add(order.getShippingAmount()) ;
				//
				// }

				// BigDecimal paidGrandTotal = order.getGrandTotal();
				// if(null != paidGrandTotal && null != donationAmount) {
				//
				// paidGrandTotal = paidGrandTotal.subtract(donationAmount);
				// }else if(null != shippingAmount && null != paidGrandTotal) {
				//
				// paidGrandTotal = paidGrandTotal.subtract(shippingAmount);
				// }
				//
				// if(null != paidGrandTotal && paidGrandTotal.compareTo(grandTotal) == 1) {
				//
				// grandTotal = paidGrandTotal;
				//
				// }else if(null != paidGrandTotal) {
				//
				// grandTotal = paidGrandTotal.subtract(grandTotal);
				// }

			}
		}
		return grandTotal;
	}

	public BigDecimal getTotalCanceledAmount(SalesOrder order) {

		BigDecimal grandTotal = order.getGrandTotal();

		if (null != order.getAmstorecreditAmount()
				&& (order.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) == 0)) {

			grandTotal = order.getGrandTotal();

		}
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {

			BigDecimal paidTotal = order.getGrandTotal().subtract(order.getSubSalesOrder().getDonationAmount());

			if (paidTotal.compareTo(grandTotal) == 1) {

				grandTotal = paidTotal;
			}
		}

		return grandTotal;
	}

	/**
	 * @param order
	 * @return
	 */
	// public BigDecimal getParcialCanceledAmount(SalesOrder order, BigDecimal
	// cancelAmount,
	// BigDecimal beforeCancelledAmount, boolean isFullyCancellation ) {
	//
	// BigDecimal grandTotal =cancelAmount;
	//
	//
	//// if(null != order.getAmstorecreditAmount()) {
	////
	//// BigDecimal amastyStoreCreditAmount = order.getAmstorecreditAmount();
	////
	//// BigDecimal divideVal = new BigDecimal(100);
	////
	//// BigDecimal totalAmount =
	// grandTotal.add(amastyStoreCreditAmount).subtract(donationAmount);
	////
	//// BigDecimal donationPercenatgeShare = donationAmount.
	//// divide(totalAmount, 4,
	// RoundingMode.HALF_UP).multiply(divideVal).setScale(4, RoundingMode.HALF_UP);
	////
	//// BigDecimal refundGrandTotal = grandTotal.divide(divideVal,4,
	// RoundingMode.HALF_UP)
	//// .multiply(donationPercenatgeShare).setScale(4, RoundingMode.HALF_UP);
	////
	//// grandTotal = grandTotal.subtract(refundGrandTotal);
	////
	////
	//// }
	//
	// if (null != order.getGrandTotal() && null != order.getShippingAmount()
	// && isFullyCancellation) {
	//
	// grandTotal = cancelAmount.add(order.getShippingAmount());
	// cancelAmount = grandTotal.add(order.getImportFee());
	//
	// //BigDecimal shippingAmount = order.getShippingAmount();
	//
	// //BigDecimal paidGrandTotal = order.getGrandTotal();
	//
	//
	//// BigDecimal totalCalculatedGrandVal =
	// beforeCancelledAmount.add(cancelAmount);
	//// totalCalculatedGrandVal = totalCalculatedGrandVal.add(shippingAmount);
	//// if (null != paidGrandTotal
	//// && order.getGrandTotal().compareTo(totalCalculatedGrandVal) == 0) {
	////
	//// grandTotal = cancelAmount.add(shippingAmount);
	//// } else {
	////
	//// grandTotal = cancelAmount;
	//// }
	//
	// }
	//
	// return grandTotal;
	// }

	public BigDecimal getCanceledItemQty(SalesOrder order) {

		BigDecimal totalCancelVal = new BigDecimal(0);

		List<SalesOrderItem> cancelledItemList = order.getSalesOrderItem().stream()
				.filter(e -> null != e.getProductType()
						&& !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
						&& null != e.getQtyCanceled() && e.getQtyCanceled().intValue() != 0)
				.collect(Collectors.toList());

		if (CollectionUtils.isNotEmpty(cancelledItemList)) {

			for (SalesOrderItem item : cancelledItemList) {

				BigDecimal priceIcludingTax = item.getPriceInclTax();

				if (null != item.getDiscountAmount()) {

					BigDecimal qtyCancelled = item.getQtyCanceled();
					BigDecimal qtyOrdered = item.getQtyOrdered();
					BigDecimal Indivisualdiscount = item.getDiscountAmount().divide(qtyOrdered, 4, RoundingMode.HALF_UP)
							.setScale(4,
									RoundingMode.HALF_UP);
					BigDecimal cancelDiscountVal = BigDecimal.ZERO;
					if (null != qtyCancelled) {
						cancelDiscountVal = Indivisualdiscount.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP);

					}
					priceIcludingTax = priceIcludingTax.subtract(cancelDiscountVal);

				}

				priceIcludingTax = priceIcludingTax.multiply(item.getQtyCanceled());

				totalCancelVal = totalCancelVal.add(priceIcludingTax);

			}
		}

		return totalCancelVal;
	}

	public BigDecimal getStoreCredit(SalesOrder order, BigDecimal storeCreditAmount, Stores store) {

		BigDecimal refundStoreCreditAmount = null;
		// if(storeCreditAmount.compareTo(order.getSubSalesOrder().getBaseDonationAmount())
		// == 1
		// ||
		// storeCreditAmount.compareTo(order.getSubSalesOrder().getBaseDonationAmount())
		// == 0 ) {
		//
		// storeCreditAmount =
		// storeCreditAmount.subtract(order.getSubSalesOrder().getBaseDonationAmount());
		//
		// }else
		// if(storeCreditAmount.compareTo(order.getSubSalesOrder().getBaseDonationAmount())
		// == -1) {
		//
		// storeCreditAmount = order.getSubSalesOrder().getBaseDonationAmount();
		// }

		BigDecimal grandTotal = order.getGrandTotal();

		BigDecimal divideVal = new BigDecimal(100);

		BigDecimal totalAmount = grandTotal.add(storeCreditAmount);

		BigDecimal donationPercenatgeShare = order.getSubSalesOrder().getDonationAmount()
				.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
				.setScale(4, RoundingMode.HALF_UP);

		BigDecimal calStoreCreditAmount = storeCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
				.multiply(donationPercenatgeShare)
				.setScale(2, RoundingMode.HALF_UP)
				.setScale(4, RoundingMode.HALF_UP);

		refundStoreCreditAmount = storeCreditAmount.subtract(calStoreCreditAmount);

		// if(null != store && null != order.getStoreToBaseRate() ) {
		//
		// refundStoreCreditAmount =
		// refundStoreCreditAmount.multiply(order.getStoreToBaseRate()).setScale(4,
		// RoundingMode.HALF_UP);
		// }

		/** this condition sign is correct **/
		return refundStoreCreditAmount;
	}

	public BigDecimal getCancelledStoreCreditWithCurrentOrderValue(SalesOrder order,
			Stores store, BigDecimal totalAmountToRefund,
			BigDecimal beforeCancelledAmount, boolean isFullyCancellation, String paymentMethod,
			BigDecimal currentOrderValue) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();

		BigDecimal refundStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal amastyStoreCredit = order.getAmstorecreditAmount();

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 0) {
			refundStoreCreditAmount = totalAmountToRefund;

			return refundStoreCreditAmount;
		}
		if (null != beforeCancelledAmount && null != amastyStoreCredit
				&& (amastyStoreCredit.compareTo(beforeCancelledAmount) == 1)) {

			amastyStoreCredit = amastyStoreCredit.subtract(beforeCancelledAmount);
		}

		if (null != order.getShippingAmount() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {

			totalAmountToRefund = totalAmountToRefund.add(order.getShippingAmount());

		}
		// if (null != order.getCashOnDeliveryFee() && isFullyCancellation) {
		//
		// totalAmountToRefund = totalAmountToRefund.add(order.getCashOnDeliveryFee());
		//
		// }
		if (null != order.getImportFee() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {
			// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
			BigDecimal importFee = order.getImportFee();
			// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
			LOGGER.info("In isFullyCancellation getCancelledStoreCreditWithCurrentOrderValue ::importFee " + importFee
					+ " :: currentOrderValue :: " + currentOrderValue
					+ " nonKsaSellerCancellation:: " + nonKsaSellerCancellation + " isFullyCancellation:: "
					+ isFullyCancellation);
			if (nonKsaSellerCancellation && null != currentOrderValue
					&& currentOrderValue.compareTo(BigDecimal.ZERO) > 0) {
				importFee = calculateImportFee(currentOrderValue, store);
				// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
				LOGGER.info("In isFullyCancellation After calculation ::importFee " + importFee);
				order.setRefundedImportFee(order.getRefundedImportFee().add(importFee));
				LOGGER.info("In isFullyCancellation after calculation ::order.getRefundedImportFee() "
						+ order.getRefundedImportFee());
			}

			totalAmountToRefund = totalAmountToRefund.add(importFee);
		}

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 1) {

			totalAmountToRefund = amastyStoreCredit;
		}
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		// If not fully cancellation, calculate import fee for non KSA countries
		LOGGER.info("In getCancelledStoreCreditWithCurrentOrderValue :: isFullyCancellation:: " + isFullyCancellation +
				" nonKsaSellerCancellation:: " + nonKsaSellerCancellation);
		if (nonKsaSellerCancellation && !isFullyCancellation && null != currentOrderValue
				&& currentOrderValue.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal currentImportFee = calculateImportFee(currentOrderValue, store);
			BigDecimal newImportFee = findNewImportFeeOfOrder(currentOrderValue, store, totalAmountToRefund);
			BigDecimal refundImportFee = currentImportFee.subtract(newImportFee).max(BigDecimal.ZERO);
			order.setRefundedImportFee(order.getRefundedImportFee().add(refundImportFee));
			LOGGER.info(
					"In !isFullyCancellation getCancelledStoreCreditWithCurrentOrderValue :: Before totalAmountToRefund "
							+ totalAmountToRefund);
			totalAmountToRefund = totalAmountToRefund.add(refundImportFee);
			// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
			LOGGER.info("In !isFullyCancellation After calculation ::currentImportFee " + currentImportFee
					+ " :: newImportFee :: " + newImportFee + " :: refundImportFee :: " + refundImportFee
					+ " order.getRefundedImportFee():: " + order.getRefundedImportFee()
					+ " After totalAmountToRefund:: " + totalAmountToRefund);
		}
		/** this condition sign is correct **/
		return totalAmountToRefund;
	}

	/**
	 * @param order
	 * @param storeCreditAmount
	 * @param store
	 * @return
	 */
	public BigDecimal getCancelledStoreCredit(SalesOrder order,
			Stores store, BigDecimal totalAmountToRefund,
			BigDecimal beforeCancelledAmount, boolean isFullyCancellation, String paymentMethod) {
		return getCancelledStoreCreditWithCurrentOrderValue(order, store, totalAmountToRefund, beforeCancelledAmount,
				isFullyCancellation, paymentMethod, null);
	}

	public BigDecimal getCancelledStoreCreditForSplitOrder(SplitSalesOrder order,
			Stores store, BigDecimal totalAmountToRefund,
			BigDecimal beforeCancelledAmount, boolean isFullyCancellation, String paymentMethod) {

		BigDecimal refundStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal amastyStoreCredit = order.getAmstorecreditAmount();
		if (totalAmountToRefund == null)
			totalAmountToRefund = BigDecimal.ZERO;

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 0) {
			refundStoreCreditAmount = totalAmountToRefund;

			return refundStoreCreditAmount;
		}
		if (null != beforeCancelledAmount && null != amastyStoreCredit
				&& (amastyStoreCredit.compareTo(beforeCancelledAmount) == 1)) {

			amastyStoreCredit = amastyStoreCredit.subtract(beforeCancelledAmount);
		}

		if (null != order.getShippingAmount() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {

			totalAmountToRefund = totalAmountToRefund.add(order.getShippingAmount());

		}
		// if (null != order.getCashOnDeliveryFee() && isFullyCancellation) {
		//
		// totalAmountToRefund = totalAmountToRefund.add(order.getCashOnDeliveryFee());
		//
		// }
		if (null != order.getImportFee() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {

			totalAmountToRefund = totalAmountToRefund.add(order.getImportFee());
		}

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 1) {

			totalAmountToRefund = amastyStoreCredit;
		}

		/** this condition sign is correct **/
		return totalAmountToRefund;
	}

	public BigDecimal cancelPercentageCalculationForSplitOrder(SplitSalesOrder order, BigDecimal calcultedcancelAmount,
			BigDecimal storeCreditAmount, CancelDetails details, boolean isFullyCancellation, String paymentMethod,
			BigDecimal totalVoucherToRefund) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);
		BigDecimal paidStoreCreditAmount = storeCreditAmount;
		BigDecimal divideVal = new BigDecimal(100);
		BigDecimal totalAmount = BigDecimal.ZERO;
		BigDecimal cancelDonationStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal refundShippingStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal refundImportStoreCreditAmount = BigDecimal.ZERO;
		// BigDecimal totalUsedStyliCoinsValue = BigDecimal.ZERO;
		details.setAmasyStoreCredit(BigDecimal.ZERO);

		BigDecimal sumOrderedCancelled = order.getSplitSalesOrderItems().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyCanceled())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// if(null != order.getSubSalesOrder() && null !=
		// order.getSubSalesOrder().getInitialEasValueInCurrency()) {
		// totalUsedStyliCoinsValue =
		// order.getSubSalesOrder().getInitialEasValueInCurrency();
		// }

		if (sumOrderedCancelled.compareTo(BigDecimal.ZERO) == 0) {

			totalAmount = order.getGrandTotal();

			if (null != paidStoreCreditAmount) {

				totalAmount = totalAmount.add(paidStoreCreditAmount);

			}
			// if(null != totalUsedStyliCoinsValue) {
			// totalAmount = totalAmount.add(totalUsedStyliCoinsValue);
			// }

			if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getDonationAmount()
					&& !(order.getSplitSubSalesOrder().getDonationAmount().compareTo(BigDecimal.ZERO) == 0)
					&& isFullyCancellation && !(order.getGrandTotal().compareTo(BigDecimal.ZERO) == 0)
					&& !paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

				if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {

					BigDecimal donationPercenatgeShare = order.getSplitSubSalesOrder().getDonationAmount()
							.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
							.setScale(4, RoundingMode.HALF_UP);

					BigDecimal refundDonation = order.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
							.multiply(donationPercenatgeShare)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					totalAmount = order.getGrandTotal().subtract(refundDonation);

					BigDecimal cancelDonation = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
							.multiply(donationPercenatgeShare)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					cancelDonationStoreCreditAmount = cancelDonation;

					paidStoreCreditAmount = paidStoreCreditAmount.subtract(cancelDonationStoreCreditAmount);

					details.setTotalOnliineCancelAMount(totalAmount);
					details.setAmasyStoreCredit(paidStoreCreditAmount);

					return totalAmount;

				}
			} else if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
				if (null != order.getSplitSubSalesOrder()
						&& null != order.getSplitSubSalesOrder().getDonationAmount()) {

					paidStoreCreditAmount = paidStoreCreditAmount
							.subtract(order.getSplitSubSalesOrder().getDonationAmount());
					details.setAmasyStoreCredit(paidStoreCreditAmount);
				} else {

					details.setAmasyStoreCredit(paidStoreCreditAmount);
				}
				details.setTotalOnliineCancelAMount(order.getGrandTotal());

				return order.getGrandTotal();

			}
			if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getDonationAmount()) {

				totalAmount = totalAmount.subtract(order.getSplitSubSalesOrder().getDonationAmount());
				details.setTotalOnliineCancelAMount(totalAmount);
			} else {

				details.setTotalOnliineCancelAMount(order.getGrandTotal());
			}
			if (!totalVoucherToRefund.equals(BigDecimal.ZERO) && !isFullyCancellation) {
				BigDecimal amasyStoreCredit = details.getAmasyStoreCredit();
				amasyStoreCredit = amasyStoreCredit.add(totalVoucherToRefund);
				details.setAmasyStoreCredit(amasyStoreCredit);
				details.setGiftVoucher(true);
			}
			return totalAmount;
		}

		totalAmount = order.getGrandTotal();
		if (null != paidStoreCreditAmount) {
			totalAmount = order.getGrandTotal().add(paidStoreCreditAmount);
		}

		BigDecimal cancelPercenatgeShare = calcultedcancelAmount
				.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
				.setScale(4, RoundingMode.HALF_UP);

		BigDecimal refundGrandTotal = order.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
				.multiply(cancelPercenatgeShare)
				.setScale(2, RoundingMode.HALF_UP)
				.setScale(4, RoundingMode.HALF_UP);

		// if(null !=order.getImportFee() && !
		// (order.getImportFee().compareTo(BigDecimal.ZERO) ==0)
		// && isFullyCancellation) {

		// if (null != paidStoreCreditAmount &&
		// !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
		// //SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		// BigDecimal importFee = order.getImportFee();
		// LOGGER.info("In IF cancelPercentageCalculation ::importFee " + importFee);
		// LOGGER.info("In IF cancelPercentageCalculation
		// ::details.getCurrentOrderValue() " +
		// details.getCurrentOrderValue()+"nonKsaSellerCancellation ::
		// "+nonKsaSellerCancellation);
		// if(nonKsaSellerCancellation && null!=details.getCurrentOrderValue() &&
		// details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
		// importFee = calculateImportFee(details.getCurrentOrderValue(), store);
		// //SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ----
		// START
		// LOGGER.info("In IF cancelPercentageCalculation ::importFee " + importFee);
		// order.setRefundedImportFee(order.getRefundedImportFee().add(importFee));
		// LOGGER.info("In IF cancelPercentageCalculation ::order.getRefundedImportFee()
		// " + order.getRefundedImportFee());
		// }
		// BigDecimal cancelImportPercenatgeShare = importFee
		// .divide(totalAmount, 6, RoundingMode.HALF_UP)
		// .multiply(divideVal)
		// .setScale(4, RoundingMode.HALF_UP);

		// BigDecimal onlileImportReturnAmount = order.getGrandTotal()
		// .divide(divideVal, 4, RoundingMode.HALF_UP)
		// .multiply(cancelImportPercenatgeShare)
		// .setScale(2, RoundingMode.HALF_UP)
		// .setScale(4, RoundingMode.HALF_UP);

		// refundGrandTotal = refundGrandTotal.add(onlileImportReturnAmount);

		// BigDecimal returnImportCredit = paidStoreCreditAmount.
		// divide(divideVal, 4, RoundingMode.HALF_UP)
		// .multiply(cancelImportPercenatgeShare)
		// .setScale(2, RoundingMode.HALF_UP)
		// .setScale(4, RoundingMode.HALF_UP);

		// refundImportStoreCreditAmount = returnImportCredit;
		// } else {
		// //SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		// BigDecimal importFee = order.getImportFee();
		// LOGGER.info("In ELSE cancelPercentageCalculation ::importFee " + importFee+"
		// details.getCurrentOrderValue() :: "+ details.getCurrentOrderValue()+"
		// nonKsaSellerCancellation :: "+nonKsaSellerCancellation);
		// if(nonKsaSellerCancellation && null != details.getCurrentOrderValue() &&
		// details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
		// importFee = calculateImportFee(details.getCurrentOrderValue(), store);
		// //SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ----
		// START
		// LOGGER.info("In ELSE cancelPercentageCalculation ::after calculation
		// importFee " + importFee);
		// order.setRefundedImportFee(order.getRefundedImportFee().add(importFee));
		// LOGGER.info("In ELSE cancelPercentageCalculation
		// ::order.getRefundedImportFee() " + order.getRefundedImportFee());
		// }
		// refundGrandTotal = refundGrandTotal.add(importFee);

		// }

		// }
		if (null != order.getShippingAmount() && !(order.getShippingAmount().compareTo(BigDecimal.ZERO) == 0)
				&& isFullyCancellation) {

			if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {

				BigDecimal cancelShippingPercenatgeShare = order.getShippingAmount()
						.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
						.setScale(4, RoundingMode.HALF_UP);

				BigDecimal refundShippingAmount = order.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelShippingPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundGrandTotal = refundGrandTotal.add(refundShippingAmount);

				BigDecimal returnShippingCredit = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelShippingPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundShippingStoreCreditAmount = returnShippingCredit;
			} else {

				refundGrandTotal = refundGrandTotal.add(order.getShippingAmount());
			}

		}

		if (null != paidStoreCreditAmount) {

			paidStoreCreditAmount = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
					.multiply(cancelPercenatgeShare)
					.setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP);

			paidStoreCreditAmount = paidStoreCreditAmount.add(refundShippingStoreCreditAmount)
					.add(refundImportStoreCreditAmount).subtract(cancelDonationStoreCreditAmount);

		}
		calcultedcancelAmount = refundGrandTotal;

		if (!totalVoucherToRefund.equals(BigDecimal.ZERO) && !isFullyCancellation) {
			if (Objects.isNull(paidStoreCreditAmount)) {
				paidStoreCreditAmount = BigDecimal.ZERO;
			}
			paidStoreCreditAmount = paidStoreCreditAmount.add(totalVoucherToRefund);
			details.setGiftVoucher(true);
		}

		details.setAmasyStoreCredit(paidStoreCreditAmount);

		if (null != paidStoreCreditAmount) {

			details.setAmastyBaseStoreCredit(paidStoreCreditAmount.multiply(order.getStoreToBaseRate())
					.setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP));
		}

		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		// If not fully cancellation, calculate import fee for non KSA countries
		// LOGGER.info("In cancelPercentageCalculation ::nonKsaSellerCancellation " +
		// nonKsaSellerCancellation+
		// " :: isFullyCancellation ::"+isFullyCancellation);
		// if (nonKsaSellerCancellation && !isFullyCancellation && null !=
		// details.getCurrentOrderValue() &&
		// details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
		// BigDecimal currentImportFee =
		// calculateImportFee(details.getCurrentOrderValue(), store);
		// BigDecimal newImportFee =
		// findNewImportFeeOfOrder(details.getCurrentOrderValue(), store,
		// calcultedcancelAmount);
		// BigDecimal refundImportFee =
		// currentImportFee.subtract(newImportFee).max(BigDecimal.ZERO);
		// //SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ----
		// START
		// LOGGER.info("In !isFullyCancellation cancelPercentageCalculation
		// ::currentImportFee " + currentImportFee+
		// " :: newImportFee ::"+newImportFee+" :: refundImportFee ::"+refundImportFee);
		// order.setRefundedImportFee(order.getRefundedImportFee().add(refundImportFee));
		// LOGGER.info("In !isFullyCancellation cancelPercentageCalculation
		// ::order.getRefundedImportFee() " + order.getRefundedImportFee());

		// calcultedcancelAmount = calcultedcancelAmount.add(refundImportFee);
		// }

		details.setTotalOnliineCancelAMount(calcultedcancelAmount);

		// Subtract EAS Coin amount from refund for cancel by OMS or payment failed
		// if (null != order.getSubSalesOrder() && null !=
		// order.getSubSalesOrder().getEasCoins()) {
		// calcultedcancelAmount =
		// calcultedcancelAmount.subtract(order.getSubSalesOrder().getEasValueInCurrency());
		//
		// }
		// Subtract EAS Coin amount from refund for cancel by OMS or payment failed
		return calcultedcancelAmount;

	}

	public BigDecimal cancelPercentageCalculation(SalesOrder order, BigDecimal calcultedcancelAmount,
			BigDecimal storeCreditAmount, CancelDetails details, boolean isFullyCancellation, String paymentMethod,
			BigDecimal totalVoucherToRefund) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);
		BigDecimal paidStoreCreditAmount = storeCreditAmount;
		BigDecimal divideVal = new BigDecimal(100);
		BigDecimal totalAmount = BigDecimal.ZERO;
		BigDecimal cancelDonationStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal refundShippingStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal refundImportStoreCreditAmount = BigDecimal.ZERO;
		// BigDecimal totalUsedStyliCoinsValue = BigDecimal.ZERO;
		details.setAmasyStoreCredit(BigDecimal.ZERO);

		BigDecimal sumOrderedCancelled = order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyCanceled())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// if(null != order.getSubSalesOrder() && null !=
		// order.getSubSalesOrder().getInitialEasValueInCurrency()) {
		// totalUsedStyliCoinsValue =
		// order.getSubSalesOrder().getInitialEasValueInCurrency();
		// }

		if (sumOrderedCancelled.compareTo(BigDecimal.ZERO) == 0) {

			totalAmount = order.getGrandTotal();

			if (null != paidStoreCreditAmount) {

				totalAmount = totalAmount.add(paidStoreCreditAmount);

			}
			// if(null != totalUsedStyliCoinsValue) {
			// totalAmount = totalAmount.add(totalUsedStyliCoinsValue);
			// }

			if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()
					&& !(order.getSubSalesOrder().getDonationAmount().compareTo(BigDecimal.ZERO) == 0)
					&& isFullyCancellation && !(order.getGrandTotal().compareTo(BigDecimal.ZERO) == 0)
					&& !paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

				if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {

					BigDecimal donationPercenatgeShare = order.getSubSalesOrder().getDonationAmount()
							.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
							.setScale(4, RoundingMode.HALF_UP);

					BigDecimal refundDonation = order.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
							.multiply(donationPercenatgeShare)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					totalAmount = order.getGrandTotal().subtract(refundDonation);

					BigDecimal cancelDonation = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
							.multiply(donationPercenatgeShare)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					cancelDonationStoreCreditAmount = cancelDonation;

					paidStoreCreditAmount = paidStoreCreditAmount.subtract(cancelDonationStoreCreditAmount);

					details.setTotalOnliineCancelAMount(totalAmount);
					details.setAmasyStoreCredit(paidStoreCreditAmount);

					return totalAmount;

				}
			} else if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {

					paidStoreCreditAmount = paidStoreCreditAmount
							.subtract(order.getSubSalesOrder().getDonationAmount());
					details.setAmasyStoreCredit(paidStoreCreditAmount);
				} else {

					details.setAmasyStoreCredit(paidStoreCreditAmount);
				}
				details.setTotalOnliineCancelAMount(order.getGrandTotal());

				return order.getGrandTotal();

			}
			if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {

				totalAmount = totalAmount.subtract(order.getSubSalesOrder().getDonationAmount());
				details.setTotalOnliineCancelAMount(totalAmount);
			} else {

				details.setTotalOnliineCancelAMount(order.getGrandTotal());
			}
			if (!totalVoucherToRefund.equals(BigDecimal.ZERO) && !isFullyCancellation) {
				BigDecimal amasyStoreCredit = details.getAmasyStoreCredit();
				amasyStoreCredit = amasyStoreCredit.add(totalVoucherToRefund);
				details.setAmasyStoreCredit(amasyStoreCredit);
				details.setGiftVoucher(true);
			}
			return totalAmount;
		}

		totalAmount = order.getGrandTotal();
		if (null != paidStoreCreditAmount) {

			totalAmount = order.getGrandTotal().add(paidStoreCreditAmount);

		}
		// if(null != totalUsedStyliCoinsValue) {
		// totalAmount = totalAmount.add(totalUsedStyliCoinsValue);
		// }

		BigDecimal cancelPercenatgeShare = calcultedcancelAmount
				.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
				.setScale(4, RoundingMode.HALF_UP);

		BigDecimal refundGrandTotal = order.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
				.multiply(cancelPercenatgeShare)
				.setScale(2, RoundingMode.HALF_UP)
				.setScale(4, RoundingMode.HALF_UP);

		if (null != order.getImportFee() && !(order.getImportFee().compareTo(BigDecimal.ZERO) == 0)
				&& isFullyCancellation) {

			if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
				// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
				BigDecimal importFee = order.getImportFee();
				LOGGER.info("In IF cancelPercentageCalculation ::importFee " + importFee);
				LOGGER.info("In IF cancelPercentageCalculation ::details.getCurrentOrderValue() "
						+ details.getCurrentOrderValue() + "nonKsaSellerCancellation :: " + nonKsaSellerCancellation);
				if (nonKsaSellerCancellation && null != details.getCurrentOrderValue()
						&& details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
					importFee = calculateImportFee(details.getCurrentOrderValue(), store);
					// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
					LOGGER.info("In IF cancelPercentageCalculation ::importFee " + importFee);
					order.setRefundedImportFee(order.getRefundedImportFee().add(importFee));
					LOGGER.info("In IF cancelPercentageCalculation ::order.getRefundedImportFee() "
							+ order.getRefundedImportFee());
				}
				BigDecimal cancelImportPercenatgeShare = importFee
						.divide(totalAmount, 6, RoundingMode.HALF_UP)
						.multiply(divideVal)
						.setScale(4, RoundingMode.HALF_UP);

				BigDecimal onlileImportReturnAmount = order.getGrandTotal()
						.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelImportPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundGrandTotal = refundGrandTotal.add(onlileImportReturnAmount);

				BigDecimal returnImportCredit = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelImportPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundImportStoreCreditAmount = returnImportCredit;
			} else {
				// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
				BigDecimal importFee = order.getImportFee();
				LOGGER.info("In ELSE cancelPercentageCalculation ::importFee " + importFee
						+ " details.getCurrentOrderValue() :: " + details.getCurrentOrderValue()
						+ " nonKsaSellerCancellation :: " + nonKsaSellerCancellation);
				if (nonKsaSellerCancellation && null != details.getCurrentOrderValue()
						&& details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
					importFee = calculateImportFee(details.getCurrentOrderValue(), store);
					// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
					LOGGER.info("In ELSE cancelPercentageCalculation ::after calculation importFee " + importFee);
					order.setRefundedImportFee(order.getRefundedImportFee().add(importFee));
					LOGGER.info("In ELSE cancelPercentageCalculation ::order.getRefundedImportFee() "
							+ order.getRefundedImportFee());
				}
				refundGrandTotal = refundGrandTotal.add(importFee);

			}

		}
		if (null != order.getShippingAmount() && !(order.getShippingAmount().compareTo(BigDecimal.ZERO) == 0)
				&& isFullyCancellation) {

			if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {

				BigDecimal cancelShippingPercenatgeShare = order.getShippingAmount()
						.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
						.setScale(4, RoundingMode.HALF_UP);

				BigDecimal refundShippingAmount = order.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelShippingPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundGrandTotal = refundGrandTotal.add(refundShippingAmount);

				BigDecimal returnShippingCredit = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelShippingPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundShippingStoreCreditAmount = returnShippingCredit;
			} else {

				refundGrandTotal = refundGrandTotal.add(order.getShippingAmount());
			}

		}

		if (null != paidStoreCreditAmount) {

			paidStoreCreditAmount = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
					.multiply(cancelPercenatgeShare)
					.setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP);

			paidStoreCreditAmount = paidStoreCreditAmount.add(refundShippingStoreCreditAmount)
					.add(refundImportStoreCreditAmount).subtract(cancelDonationStoreCreditAmount);

		}
		calcultedcancelAmount = refundGrandTotal;

		if (!totalVoucherToRefund.equals(BigDecimal.ZERO) && !isFullyCancellation) {
			if (Objects.isNull(paidStoreCreditAmount)) {
				paidStoreCreditAmount = BigDecimal.ZERO;
			}
			paidStoreCreditAmount = paidStoreCreditAmount.add(totalVoucherToRefund);
			details.setGiftVoucher(true);
		}

		details.setAmasyStoreCredit(paidStoreCreditAmount);

		if (null != paidStoreCreditAmount) {

			details.setAmastyBaseStoreCredit(paidStoreCreditAmount.multiply(order.getStoreToBaseRate())
					.setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP));
		}

		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		// If not fully cancellation, calculate import fee for non KSA countries
		LOGGER.info("In cancelPercentageCalculation ::nonKsaSellerCancellation " + nonKsaSellerCancellation +
				" :: isFullyCancellation ::" + isFullyCancellation);
		if (nonKsaSellerCancellation && !isFullyCancellation && null != details.getCurrentOrderValue()
				&& details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal currentImportFee = calculateImportFee(details.getCurrentOrderValue(), store);
			BigDecimal newImportFee = findNewImportFeeOfOrder(details.getCurrentOrderValue(), store,
					calcultedcancelAmount);
			BigDecimal refundImportFee = currentImportFee.subtract(newImportFee).max(BigDecimal.ZERO);
			// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
			LOGGER.info("In !isFullyCancellation cancelPercentageCalculation ::currentImportFee " + currentImportFee +
					" :: newImportFee ::" + newImportFee + " :: refundImportFee ::" + refundImportFee);
			order.setRefundedImportFee(order.getRefundedImportFee().add(refundImportFee));
			LOGGER.info("In !isFullyCancellation cancelPercentageCalculation ::order.getRefundedImportFee() "
					+ order.getRefundedImportFee());

			calcultedcancelAmount = calcultedcancelAmount.add(refundImportFee);
		}

		details.setTotalOnliineCancelAMount(calcultedcancelAmount);

		// Subtract EAS Coin amount from refund for cancel by OMS or payment failed
		// if (null != order.getSubSalesOrder() && null !=
		// order.getSubSalesOrder().getEasCoins()) {
		// calcultedcancelAmount =
		// calcultedcancelAmount.subtract(order.getSubSalesOrder().getEasValueInCurrency());
		//
		// }
		// Subtract EAS Coin amount from refund for cancel by OMS or payment failed
		return calcultedcancelAmount;
	}

	/**
	 * Calculate cancellation percentage for split orders
	 * 
	 * @param splitOrder             the split sales order
	 * @param calculatedCancelAmount the calculated cancel amount
	 * @param storeCreditAmount      the store credit amount
	 * @param details                the cancel details object
	 * @param isFullyCancellation    whether it's a full cancellation
	 * @param paymentMethod          the payment method
	 * @param totalVoucherToRefund   the total voucher amount to refund
	 * @return the calculated cancellation amount
	 */
	/**
	 * @param order
	 * @return
	 */
	public BigDecimal getCancelAmount(SalesOrder order, Map<String, BigDecimal> skumapList,
			List<SalesOrderItem> canCelitemList) {

		BigDecimal totalAmountToRefund = BigDecimal.ZERO;
		List<SalesOrderItem> itemList = order.getSalesOrderItem()
				.stream().filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

		for (SalesOrderItem item : itemList) {

			BigDecimal incluTaxPrice = item.getPriceInclTax();
			BigDecimal qtyOrdered = item.getQtyOrdered();
			BigDecimal qtyCancelled = item.getQtyCanceled();
			if (null != qtyCancelled && !(qtyOrdered.compareTo(qtyCancelled) == 0)) {
				qtyOrdered = qtyOrdered.subtract(qtyCancelled);
			} else {

				continue;
			}

			if (null != item.getDiscountAmount() && !(item.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {

				BigDecimal Indivisualdiscount = item.getDiscountAmount()
						.divide(item.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
				BigDecimal cancelDiscountVal = BigDecimal.ZERO;
				cancelDiscountVal = Indivisualdiscount.multiply(qtyOrdered).setScale(4, RoundingMode.HALF_UP);

				incluTaxPrice = incluTaxPrice.multiply(qtyOrdered).setScale(4, RoundingMode.HALF_UP)
						.subtract(cancelDiscountVal);
				totalAmountToRefund = totalAmountToRefund.add(incluTaxPrice);

			} else {

				totalAmountToRefund = totalAmountToRefund
						.add(incluTaxPrice.multiply(qtyOrdered).setScale(4, RoundingMode.HALF_UP));
			}

			skumapList.put(item.getSku(), qtyOrdered);
			canCelitemList.add(item);

		}
		return totalAmountToRefund;
	}

	public BigDecimal getCancelAmountForSplitOrder(SplitSalesOrder order, Map<String, BigDecimal> skumapList,
			List<SplitSalesOrderItem> canCelitemList) {

		BigDecimal totalAmountToRefund = BigDecimal.ZERO;
		List<SplitSalesOrderItem> itemList = order.getSplitSalesOrderItems()
				.stream().filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

		for (SplitSalesOrderItem item : itemList) {

			BigDecimal incluTaxPrice = item.getPriceInclTax();
			BigDecimal qtyOrdered = item.getQtyOrdered();
			BigDecimal qtyCancelled = item.getQtyCanceled();
			if (null != qtyCancelled && !(qtyOrdered.compareTo(qtyCancelled) == 0)) {
				qtyOrdered = qtyOrdered.subtract(qtyCancelled);
			} else {

				continue;
			}

			if (null != item.getDiscountAmount() && !(item.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {

				BigDecimal Indivisualdiscount = item.getDiscountAmount()
						.divide(item.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
				BigDecimal cancelDiscountVal = BigDecimal.ZERO;
				cancelDiscountVal = Indivisualdiscount.multiply(qtyOrdered).setScale(4, RoundingMode.HALF_UP);

				incluTaxPrice = incluTaxPrice.multiply(qtyOrdered).setScale(4, RoundingMode.HALF_UP)
						.subtract(cancelDiscountVal);
				totalAmountToRefund = totalAmountToRefund.add(incluTaxPrice);

			} else {

				totalAmountToRefund = totalAmountToRefund
						.add(incluTaxPrice.multiply(qtyOrdered).setScale(4, RoundingMode.HALF_UP));
			}

			skumapList.put(item.getSku(), qtyOrdered);
			canCelitemList.add(item);

		}
		return totalAmountToRefund;
	}

	public static void main(String arg[]) {
		//
		// BigDecimal amastyStoreCreditAmount = new BigDecimal("31");
		// BigDecimal grandTotal = new BigDecimal("700.80");
		// BigDecimal totalAmount = new BigDecimal("0");
		// BigDecimal refundGrandTotal = new BigDecimal("0");
		// BigDecimal totalRefundAmount = new BigDecimal("316.80");
		//
		// totalAmount = grandTotal.add(amastyStoreCreditAmount);
		//
		// BigDecimal cancelPercenatgeShare = totalRefundAmount
		// .divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
		// .setScale(4,RoundingMode.HALF_UP);
		//
		// System.out.println("cancelPercenatgeShare:"+cancelPercenatgeShare);
		//
		// //43.2905 //316.80
		// BigDecimal percenatge = new BigDecimal("43.2906");
		//
		// refundGrandTotal = grandTotal.divide(new BigDecimal(100), 4,
		// RoundingMode.HALF_UP)
		// .multiply(cancelPercenatgeShare)
		// .setScale(2,RoundingMode.HALF_UP)
		// .setScale(4,RoundingMode.HALF_UP);
		//
		//
		// BigDecimal stylicredit = amastyStoreCreditAmount.divide(new BigDecimal(100),
		// 4, BigDecimal.ROUND_HALF_UP)
		// .multiply(cancelPercenatgeShare)
		// .setScale(2,RoundingMode.HALF_UP)
		// .setScale(4,RoundingMode.HALF_UP);
		//
		// System.out.println("online refundTotal:"+refundGrandTotal);
		// System.out.println("stylicredit refundTotal:"+stylicredit);
		// System.out.println("total refundTotal:"+refundGrandTotal.add(stylicredit));
		//
		//
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_UP));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_CEILING));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_DOWN));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_FLOOR));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_HALF_DOWN));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_HALF_EVEN));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(2,
		// BigDecimal.ROUND_HALF_UP));
		// System.out.println("***********");
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(4,
		// RoundingMode.HALF_EVEN));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(4,
		// RoundingMode.CEILING));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(4,
		// RoundingMode.HALF_DOWN));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(4,
		// RoundingMode.UP));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(4,
		// RoundingMode.HALF_UP));
		// System.out.println("refundTotal:"+refundGrandTotal.setScale(4,
		// RoundingMode.FLOOR));
	}

	public BigDecimal getCalculatedCoinValue(SalesOrder order, AmastyRmaRequest easrmaRequestRes,
			List<SalesCreditmemo> creditMemoList) {
		BigDecimal returnEasValueInCurrency = BigDecimal.ZERO;

		BigDecimal sumOrderedQty = order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyOrdered())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal sumOrderedCancelled = order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyCanceled())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal sumRefundedCancelled = order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyRefunded())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal totalOrderQty = sumOrderedQty.subtract(sumOrderedCancelled).subtract(sumRefundedCancelled);

		if (totalOrderQty.compareTo(BigDecimal.ZERO) != 0) {
			returnEasValueInCurrency = easrmaRequestRes.getEasValueInCurrency();
		} else {
			if (null != easrmaRequestRes.getEasValueInCurrency() && null != order.getSubSalesOrder()
					&& null != order.getSubSalesOrder().getEasValueInCurrency()) {
				BigDecimal totalCoinRefundedInCurrency = BigDecimal.ZERO;
				if (CollectionUtils.isNotEmpty(creditMemoList)) {
					for (SalesCreditmemo memo : creditMemoList) {
						if (null != memo.getEasValueInCurrency()) {
							totalCoinRefundedInCurrency = totalCoinRefundedInCurrency.add(memo.getEasValueInCurrency());
						}
					}
				}
				returnEasValueInCurrency = order.getSubSalesOrder().getEasValueInCurrency()
						.subtract(totalCoinRefundedInCurrency);
			}
		}

		return returnEasValueInCurrency;
	}

	/**
	 * responsible to prepare payfort refund payload
	 * 
	 * @param configuration
	 * @param order
	 * @param refundAmount
	 * @param fortId
	 * @return
	 */
	private PayfortOrderRefundPayLoad preparePayfortRefundRequest(PayfortConfiguration configuration, SalesOrder order,
			String refundAmount, String fortId) {
		LOGGER.info("inside payfortRefundcall : preparePayfortRefundRequest ");
		PayfortOrderRefundPayLoad payfortRedundRequest = new PayfortOrderRefundPayLoad();
		payfortRedundRequest.setAccessCode(configuration.getAccessCode());
		payfortRedundRequest.setAmount(getConvertedAmount(refundAmount, configuration.getMultiplier()));
		payfortRedundRequest.setCommand(OrderConstants.REFUND_STRING);
		payfortRedundRequest.setCurrency(order.getStoreCurrencyCode());
		payfortRedundRequest.setFortId(fortId);
		payfortRedundRequest.setLanguage(configuration.getLanguage());
		payfortRedundRequest.setMerchantIdentifier(configuration.getMerchantIdentifier());
		String incrementId = getIncrementIdForApplePay(order);
		payfortRedundRequest.setMerchantReference(incrementId);
		payfortRedundRequest.setOrderDescription(incrementId);
		payfortRedundRequest.setSignature(getSignature(payfortRedundRequest, configuration.getSignatureHash()));

		return payfortRedundRequest;
	}

	public RefundPaymentRespone payfortVoidAuthorizationcall(SalesOrder order, String fortId,
			String paymentMethod) {

		RefundPaymentRespone response = new RefundPaymentRespone();
		PayfortReposne payfortResponse = null;
		if (OrderConstants.checkPaymentMethod(paymentMethod)) {

			PayfortConfiguration configuration = new PayfortConfiguration();
			getPayfortConfDetails(order.getStoreId().toString(), paymentMethod, configuration);
			try {
				LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
			} catch (JsonProcessingException e) {
				LOGGER.error("error during write configuration:" + e.getMessage());
			}

			payfortResponse = triggerPayfortVoidAuthorizationRestApiCall(
					preparePayfortVoidAuthorizationRequest(configuration, order, fortId), order);

		}
		if (null != payfortResponse && !payfortResponse.isStatus()) {
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(payfortResponse.getMessage());
			return response;
		}

		response.setStatus(true);
		response.setStatusCode("200");
		return response;
	}

	/**
	 * responsible to prepare payfort void authorization request/pyaload
	 * 
	 * @param configuration
	 * @param order
	 * @param fortId
	 * @return
	 */
	private PayfortVoidAuthorizationRequest preparePayfortVoidAuthorizationRequest(PayfortConfiguration configuration,
			SalesOrder order, String fortId) {
		PayfortVoidAuthorizationRequest payfortVoidAuthorizationRequest = new PayfortVoidAuthorizationRequest();
		payfortVoidAuthorizationRequest.setAccessCode(configuration.getAccessCode());
		payfortVoidAuthorizationRequest.setCommand(OrderConstants.VOID_AUTHORIZATION);
		payfortVoidAuthorizationRequest.setFortId(fortId);
		payfortVoidAuthorizationRequest.setLanguage(configuration.getLanguage());
		payfortVoidAuthorizationRequest.setMerchantIdentifier(configuration.getMerchantIdentifier());
		String incrementId = getIncrementIdForApplePay(order);
		payfortVoidAuthorizationRequest.setMerchantReference(incrementId);
		payfortVoidAuthorizationRequest.setOrderDescription(incrementId);
		payfortVoidAuthorizationRequest.setSignature(
				getVoidAuthorizationSignature(payfortVoidAuthorizationRequest, configuration.getSignatureHash()));

		return payfortVoidAuthorizationRequest;
	}

	/**
	 * @param payfortRedundRequest
	 * @param signatureHash
	 * @return
	 */
	private String getVoidAuthorizationSignature(PayfortVoidAuthorizationRequest payfortRedundRequest,
			String signatureHash) {

		String signature = null;
		String signatureRaw = new StringBuilder().append("access_code=").append(payfortRedundRequest.getAccessCode())
				.append("command=").append(OrderConstants.VOID_AUTHORIZATION)
				.append("fort_id=").append(payfortRedundRequest.getFortId())
				.append("language=").append(payfortRedundRequest.getLanguage())
				.append("merchant_identifier=").append(payfortRedundRequest.getMerchantIdentifier())
				.append("merchant_reference=").append(payfortRedundRequest.getMerchantReference())
				.append("order_description=").append(payfortRedundRequest.getOrderDescription()).toString();

		signature = new StringBuilder().append(signatureHash).append(signatureRaw).append(signatureHash).toString();
		LOGGER.info("Void authorization signature sequence:" + signature);
		String sha256hex = org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
		LOGGER.info("Void Authorization sha256hex :" + sha256hex);

		return sha256hex;
	}

	/**
	 * responsible to call payfort payment api by passing command as
	 * VOID_AUTHORIZATION
	 * 
	 * @param payfortVoidAuthorizationRequest
	 * @param order
	 * @return
	 */
	private PayfortReposne triggerPayfortVoidAuthorizationRestApiCall(
			PayfortVoidAuthorizationRequest payfortVoidAuthorizationRequest, SalesOrder order) {

		PayfortReposne payfortResponse = new PayfortReposne();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity<PayfortVoidAuthorizationRequest> requestBody = new HttpEntity<>(payfortVoidAuthorizationRequest,
				requestHeaders);
		String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi";

		try {
			LOGGER.info("payfort url for void authorization: " + url);
			LOGGER.info("Request body: " + mapper.writeValueAsString(requestBody));

			ResponseEntity<PayfortVoidAuthorizationResponse> response = restTemplate.exchange(url, HttpMethod.POST,
					requestBody, PayfortVoidAuthorizationResponse.class);

			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				PayfortVoidAuthorizationResponse responseBody = response.getBody();

				LOGGER.info("Payfort VOID_AUTHORIZATION response Body:" + mapper.writeValueAsString(responseBody));
				if (null != responseBody) {
					if (null != responseBody.getStatus() && responseBody.getStatus()
							.equals(OrderConstants.PAYFORT_VOID_AUTHORIZATION_SUCCESS_STATUS)) {
						payfortResponse.setStatus(true);
						payfortResponse.setMessage(responseBody.getResponseMessage());
					} else {
						payfortResponse.setStatus(false);
						payfortResponse.setMessage(responseBody.getResponseMessage());
					}
				} else {
					payfortResponse.setStatus(false);
					payfortResponse.setMessage("Response body is null");
				}
			}
		} catch (RestClientException | JsonProcessingException e2) {
			LOGGER.error("exception occoured during void authorization process:" + order.getIncrementId() + " "
					+ e2.getMessage());
			payfortResponse.setStatus(false);
			payfortResponse.setMessage(e2.getMessage());
		}

		return payfortResponse;
	}

	public BigDecimal getGiftVoucherRefundAmount(SalesOrder order, AmastyRmaRequest rmaRequest,
			RefundAmountObject refundAmountDetails) {

		BigDecimal returnGiftVoucherAmount = BigDecimal.ZERO;
		BigDecimal discountAmount = BigDecimal.ZERO;
		BigDecimal indivisualdiscount = BigDecimal.ZERO;
		LOGGER.info("Inside Calculate Gift voucher refund Amount:");
		try {
			if (Objects.nonNull(rmaRequest)) {
				Set<AmastyRmaRequestItem> amastyRmaRequestItems = rmaRequest.getAmastyRmaRequestItems();

				List<SalesOrderItem> itemList = order.getSalesOrderItem().stream()
						.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.collect(Collectors.toList());

				for (SalesOrderItem salesOrderItem : itemList) {
					for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequestItems) {
						if (salesOrderItem.getItemId().equals(amastyRmaRequestItem.getOrderItemId())) {
							BigDecimal orderQuantity = salesOrderItem.getQtyOrdered();

							SalesOrderItem salesOrderItem1 = salesOrderItem.getParentOrderItem();

							List<SubSalesOrderItem> subSalesOrderItem = subSalesOrderItemRepository
									.findBySalesOrderItem(salesOrderItem1);
							SubSalesOrderItem subSalesOrderItem1 = subSalesOrderItem.stream()
									.filter(SubSalesOrderItem::isGiftVoucher).findAny().orElse(null);
							if (Objects.nonNull(subSalesOrderItem1)) {
								discountAmount = subSalesOrderItem1.getDiscount();
							}
							indivisualdiscount = discountAmount.divide(orderQuantity, 4, RoundingMode.HALF_UP)
									.setScale(4, RoundingMode.HALF_UP);
							LOGGER.info("Calculated Gift Voucher Individual  Amount:" + indivisualdiscount);

							BigDecimal returnAmount = amastyRmaRequestItem.getQty().multiply(indivisualdiscount)
									.setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

							amastyRmaRequestItem.setGiftVoucherRefundedAmount(returnAmount);
							amastyRmaRequestItemRepository.saveAndFlush(amastyRmaRequestItem);
							returnGiftVoucherAmount = returnGiftVoucherAmount.add(returnAmount);
							LOGGER.info(
									"Calculated Gift Voucher Amount Inside calculation :" + returnGiftVoucherAmount);
						}
					}
				}
				rmaRequest.setGiftVoucherRefundedAmount(returnGiftVoucherAmount);
				amastyRmaRequestRepository.saveAndFlush(rmaRequest);
			}
			LOGGER.info("Calculated Gift Voucher Amount:" + returnGiftVoucherAmount);
			refundAmountDetails.setGiftVoucher(true);
			return returnGiftVoucherAmount;
		} catch (Exception e) {
			LOGGER.error("error during calculation gift voucher refund amount");
			return returnGiftVoucherAmount;
		}
	}

	public void setReturnVoucherValueInDB(SalesOrder order, BigDecimal returnGiftVoucherAmount) {
		try {
			SubSalesOrder subSalesOrder = order.getSubSalesOrder();
			if (Objects.nonNull(subSalesOrder) && Objects.nonNull(subSalesOrder.getGiftVoucherRefundedAmount())
					&& Objects.nonNull(returnGiftVoucherAmount)
					&& returnGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
				BigDecimal refundedAmount = subSalesOrder.getGiftVoucherRefundedAmount();
				BigDecimal updateRefundedAmount = refundedAmount.add(returnGiftVoucherAmount);
				if ((updateRefundedAmount.compareTo(returnGiftVoucherAmount) <= 0)) {
					subSalesOrder.setGiftVoucherRefundedAmount(
							subSalesOrder.getGiftVoucherRefundedAmount().add(returnGiftVoucherAmount));
					order.setSubSalesOrder(subSalesOrder);
					salesOrderRepository.saveAndFlush(order);

				} else {
					LOGGER.error("error during DB update return voucher");
				}
			}
		} catch (Exception e) {
			LOGGER.error("error during DB update return voucher");
		}
	}

	public void setReturnVoucherValueInDBForSplitOrder(SplitSalesOrder order, BigDecimal returnGiftVoucherAmount) {
		try {
			SplitSubSalesOrder subSalesOrder = order.getSplitSubSalesOrder();
			if (Objects.nonNull(subSalesOrder) && Objects.nonNull(subSalesOrder.getGiftVoucherRefundedAmount())
					&& Objects.nonNull(returnGiftVoucherAmount)
					&& returnGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
				BigDecimal refundedAmount = subSalesOrder.getGiftVoucherRefundedAmount();
				BigDecimal updateRefundedAmount = refundedAmount.add(returnGiftVoucherAmount);
				if ((updateRefundedAmount.compareTo(returnGiftVoucherAmount) <= 0)) {
					subSalesOrder.setGiftVoucherRefundedAmount(
							subSalesOrder.getGiftVoucherRefundedAmount().add(returnGiftVoucherAmount));
					order.setSplitSubSalesOrder(subSalesOrder);
					splitSalesOrderRepository.saveAndFlush(order);

				} else {
					LOGGER.error("error during DB update return voucher");
				}
			}
		} catch (Exception e) {
			LOGGER.error("error during DB update return voucher");
		}
	}

	public String getIncrementIdForApplePay(SalesOrder order) {
		String incrementId = order.getIncrementId();
		List<Stores> stores = Constants.getStoresList();
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst()
				.orElse(null);
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);
		if (Objects.nonNull(store) && store.isEnableApplepayholdOrder() && Objects.nonNull(order.getEditIncrement())
				&& null != salesOrderPayment && null != salesOrderPayment.getMethod()
				&& salesOrderPayment.getMethod().equalsIgnoreCase("apple_pay")) {
			incrementId = order.getEditIncrement();
		}
		return incrementId;
	}

	public String getIncrementIdForApplePayV2(SplitSalesOrder splitSalesOrder) {
		String incrementId = splitSalesOrder.getSalesOrder().getIncrementId();
		List<Stores> stores = Constants.getStoresList();
		SplitSalesOrderPayment salesOrderPayment = splitSalesOrder.getSplitSalesOrderPayments().stream().findFirst()
				.orElse(null);
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(splitSalesOrder.getStoreId()))
				.findAny()
				.orElse(null);
		if (Objects.nonNull(store) && store.isEnableApplepayholdOrder()
				&& Objects.nonNull(splitSalesOrder.getSalesOrder().getEditIncrement())
				&& null != salesOrderPayment && null != salesOrderPayment.getMethod()
				&& salesOrderPayment.getMethod().equalsIgnoreCase("apple_pay")) {
			incrementId = splitSalesOrder.getSalesOrder().getEditIncrement();
		}
		return incrementId;
	}

	public String timeStampData() {
		return formatTimestampToIsoUtc(new Timestamp(new Date().getTime()));
	}

	/** Formats a Timestamp to ISO-8601 UTC (yyyy-MM-dd'T'HH:mm:ss.SSS'Z') - same as clawback TransactionDateTime. */
	private String formatTimestampToIsoUtc(Timestamp timestamp) {
		if (timestamp == null) return null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(timestamp);
	}

	public BigDecimal findCurrentOrderValue(SalesOrder order) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
		LOGGER.info("In findCurrentOrderValue order increment Id: " + order.getIncrementId());
		BigDecimal currentOrderValue = order.getSubtotalInclTax().add(order.getShippingAmount())
				.add(order.getCashOnDeliveryFee()).add(order.getDiscountAmount()).setScale(2, RoundingMode.HALF_UP);
		for (SalesOrderItem item : order.getSalesOrderItem()) {
			if (!item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
					&& item.getQtyCanceled().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal itemPrice = item.getPriceInclTax().multiply(item.getQtyCanceled());
				currentOrderValue = currentOrderValue.subtract(itemPrice);
			}
		}

		LOGGER.info("In findCurrentOrderValue : currentOrderValue :: " + currentOrderValue);
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- END

		return currentOrderValue;
	}

	public BigDecimal calculateImportFee(BigDecimal orderValue, Stores store) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
		LOGGER.info("In calculateImportFee order value: " + orderValue);
		BigDecimal minValue = store.getMinimumDutiesAmount();
		BigDecimal importLow = store.getImportFeePercentage();
		BigDecimal importHigh = store.getImportMaxFeePercentage();
		BigDecimal dutyPercentage = store.getCustomDutiesPercentage();
		BigDecimal dutyAmount = new BigDecimal(0);
		BigDecimal feePercentage = importLow;
		if (orderValue.compareTo(minValue) > 0) {
			feePercentage = importHigh;
			dutyAmount = orderValue.multiply(dutyPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		}
		LOGGER.info("In calculateImportFee : dutyAmount :: " + dutyAmount);
		BigDecimal importFeeAmount = (orderValue.add(dutyAmount)).multiply(feePercentage)
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		LOGGER.info("In calculateImportFee : importFeeAmount :: " + importFeeAmount);
		BigDecimal totalFee = importFeeAmount.add(dutyAmount);
		LOGGER.info("In calculateImportFee : importFeeAmount+dutyAmount :: final import fee" + totalFee);

		LOGGER.info("In calculateImportFee : import fee :: " + totalFee);
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- END
		return totalFee.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal findNewImportFeeOfOrder(BigDecimal currentOrderValue, Stores store,
			BigDecimal totalAmountToRefund) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- START
		LOGGER.info("In findNewImportFeeOfOrder ::currentOrderValue " + currentOrderValue);
		LOGGER.info("In findNewImportFeeOfOrder ::totalAmountToRefund " + totalAmountToRefund);
		BigDecimal newOrderValue = currentOrderValue.subtract(totalAmountToRefund);
		BigDecimal newImportFee = calculateImportFee(newOrderValue, store);
		LOGGER.info("In findNewImportFeeOfOrder ::newImportFee " + newImportFee);
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries ---- END

		return newImportFee;
	}
}

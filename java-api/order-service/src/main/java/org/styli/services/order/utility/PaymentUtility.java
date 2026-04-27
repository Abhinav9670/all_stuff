package org.styli.services.order.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.WmsException;
import org.styli.services.order.exception.UnAuthorisedRequestException;
import org.styli.services.order.helper.CashfreeHelper;
import org.styli.services.order.helper.PaymentRefundHelper;
import org.styli.services.order.helper.TabbyHelper;
import org.styli.services.order.helper.TamaraHelper;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.CashfreePaymentDTO;
import org.styli.services.order.pojo.OrderSms;
import org.styli.services.order.pojo.PayfortConfiguration;
import org.styli.services.order.pojo.PayfortPaymentCaptureRequest;
import org.styli.services.order.pojo.PayfortPaymentCaptureStatusCheckRequest;
import org.styli.services.order.pojo.PayfortCaptureResponse;
import org.styli.services.order.pojo.PayfortCaptureStatusCheckResponse;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.order.OTSOrderRequest;
import org.styli.services.order.pojo.order.PaymentReturnAdditioanls;
import org.styli.services.order.pojo.order.StatusMessage;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.service.PaymentService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.impl.CashfreePaymentServiceImpl;
import org.styli.services.order.service.impl.KafkaServiceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.styli.services.order.service.impl.PubSubServiceImpl;

@Component
public class PaymentUtility {

	private static final Log LOGGER = LogFactory.getLog(PaymentUtility.class);
		
	@Autowired
	private SalesOrderService salesOrderService;
	
	@Autowired
	@Qualifier("tabbyPaymentServiceImpl")
	private PaymentService tabbyPaymentService;
	
	@Autowired
	@Qualifier("tamaraPaymentServiceImpl")
	private PaymentService tamaraPaymentService;
	
	@Autowired
	@Qualifier("cashfreePaymentServiceImpl")
	private PaymentService cashfreePaymentService;
	
	@Autowired
	private TabbyHelper tabbyHelper;

	@Autowired
	private TamaraHelper tamaraHelper;

	@Autowired
	private CashfreeHelper cashfreeHelper;
	
	@Autowired
	private HttpServletRequest httpServletRequest;
	
	@Lazy
	@Autowired
	KafkaServiceImpl kafkaService;
	
	@Value("${region.value}")
	private String region;
	
	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	
	@Autowired
	PaymentRefundHelper paymentRefundHelper;
	
	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	PubSubServiceImpl pubSubServiceImpl;

	@Value("${pubsub.topic.split.order}")
	private String splitOrderTopic;

	@Value("${pubsub.topic.split.order.tracking}")
	private String splitOrderTrackingTopic;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	private static final ObjectMapper mapper = new ObjectMapper();
	
	public PaymentDTO buildPaymentDTOForTamara(String paymentPayload) {
		JsonObject jsonObj = JsonParser.parseString(paymentPayload).getAsJsonObject();
		String paymentId = jsonObj.get(Constants.ORDER_ID_KEY).getAsString();
		String paymentStatus = jsonObj.get("status").getAsString();
		return new PaymentDTO(paymentId, paymentStatus, paymentPayload);
	}
	
	public PaymentDTO buildPaymentDTOForTamaraWebhook(String paymentPayload) throws UnAuthorisedRequestException {
		JsonObject jsonObj = JsonParser.parseString(paymentPayload).getAsJsonObject();
		String paymentId = jsonObj.get(Constants.ORDER_ID_KEY).getAsString();
		String paymentStatus;
		JsonElement eventType = jsonObj.get("event_type");
		String xauthtoken = httpServletRequest.getHeader("xauthtoken");
		String webhookNotificationToken = Constants.orderCredentials.getTamara().getWebhookNotificationToken();
		if (Objects.nonNull(eventType)) {// If event type exists then it's TAMARA custom webhook
			if (Objects.nonNull(xauthtoken) && webhookNotificationToken.equals(xauthtoken)) {
				paymentStatus = mapToGenericStatus(eventType.getAsString());
			} else {
				LOGGER.error("Error in Processing tamara token :  " + xauthtoken);
				throw new UnAuthorisedRequestException("Provided Request can not accepted");
			}
		} else {
			paymentStatus = jsonObj.get("order_status").getAsString();
		}
		return new PaymentDTO(paymentId, paymentStatus, paymentPayload);
	}

	private String mapToGenericStatus(String eventType) {
		switch (eventType) {
		case "order_expired":
			return "expired";
		case "order_declined":
			return "declined";
		default:
			return "";
		}
	}

	public PaymentDTO buildPaymentDTOForTabby(String paymentPayload) {
		JsonObject jsonObj = JsonParser.parseString(paymentPayload).getAsJsonObject();
		String paymentId = jsonObj.get("id").getAsString();
		String paymentStatus = jsonObj.get("status").getAsString();
		return new PaymentDTO(paymentId, paymentStatus, paymentPayload);
	}
	
	public boolean buildPaymentDTOForTabbyCapture(String paymentPayload) {
		JsonObject jsonObj = JsonParser.parseString(paymentPayload).getAsJsonObject();
		JsonArray paymentStatus = jsonObj.get("captures").getAsJsonArray();
		return !(null != paymentStatus && (paymentStatus.isJsonNull() || paymentStatus.size() == 0));
	}
	
	
	public List<ProxyOrder> findPendingOrders() {
		return salesOrderService.findPendingProxyOrders();
	}
	
	/**
	 * Update Payment Pending Order Status 
	 * @return 
	 */
	@Transactional
	public BNPLOrderUpdateResponse updatePaymentStatus(ProxyOrder order, String deviceId) {
		BNPLOrderUpdateResponse response = new BNPLOrderUpdateResponse();
		try {
			if (OrderConstants.checkTabbyPaymentMethod(order.getPaymentMethod())) {
				response = tabbyPaymentService.updatePaymentStatus(order, deviceId);
			} else if (OrderConstants.checkTamaraPaymentMethod(order.getPaymentMethod())) {
				response = tamaraPaymentService.updatePaymentStatus(order, deviceId);
			}
		} catch (Exception e) {
			LOGGER.error("Error In Updating Payment Status for " + order.getPaymentId() + " Increment ID "
					+ order.getIncrementId() + " Error : " + e);
		}
		return response;
	}
	
	/**
	 * Capture the Payments
	 * @param order
	 * @param paymentMethod
	 */
	public boolean capturePayment(SalesOrder order, String paymentMethod) {
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			return tabbyPaymentService.capturePayment(order);
		} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			return tamaraPaymentService.capturePayment(order);
		}
		
		return false;
	}

	/**
	 * Capture the Payments
	 * @param splitSalesOrder
	 * @param paymentMethod
	 */
	public boolean capturePaymentV2(SplitSalesOrder splitSalesOrder, String paymentMethod) {
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			return tabbyPaymentService.capturePaymentV2(splitSalesOrder);
		} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			return tamaraPaymentService.capturePaymentV2(splitSalesOrder);
		}

		return false;
	}
	
	/**
	 * On Payment Failure Replica should initiate.
	 * @param order
	 * @param paymentId
	 */
	public void initiateReplica(SalesOrder order, String paymentId, ProxyOrder proxyOrder, String deviceId) {
		String paymentMethod = null;
		if (Objects.nonNull(proxyOrder)) {
			paymentMethod = proxyOrder.getPaymentMethod();
		} else {
			Optional<String> methodOps = order.getSalesOrderPayment().stream().map(SalesOrderPayment::getMethod).findFirst();
			if (methodOps.isPresent()) {
				paymentMethod = methodOps.get();
			}
		}
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			tabbyPaymentService.updatePaymentOnReplica(paymentId, deviceId);
		} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			tamaraPaymentService.updatePaymentOnReplica(paymentId, deviceId);
		}
	}
	
	/**
	 * Initiate Refund for BNPL Payments. If the refund process is failed then close the payment.
	 * @param order
	 * @param returnAmount
	 * @param paymentMethod
	 */
	/**
	 * @param order
	 * @param returnAmount
	 * @param paymentMethod
	 * @return
	 */
	public RefundPaymentRespone initiateRefund(SalesOrder order, String returnAmount, String paymentMethod) {
		RefundPaymentRespone refundPayment = null;
		LOGGER.info("BNPL Refund Initiated. Order ID : " + order.getIncrementId() + " Amount: " + returnAmount);
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			refundPayment = tabbyHelper.refundPayment(order, returnAmount);
		} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			try {
				// Check whether order is having split orders or not
				if (Objects.equals(order.getIsSplitOrder(), 1)) {
					refundPayment = doTamaraSplitRefunds(order,returnAmount);
				} else {
					refundPayment = tamaraHelper.refundPayment(order, returnAmount);
				}
			} catch (Exception e) {
				refundPayment = tamaraHelper.refundPayment(order, returnAmount);
			}
		}
		
		LOGGER.info("BNPL Order " + order.getIncrementId() + "completed. Response from BNPL : " + refundPayment);
		if(Objects.nonNull(refundPayment) && refundPayment.isStatus())
			return refundPayment;
		
		// If Payment is not able to refund then close the Payment
		if (Objects.nonNull(refundPayment) && !refundPayment.isStatus()
				&& OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			return tamaraHelper.cancelPayment(order, returnAmount);
		}
		return refundPayment;
	}

	/**
	 * @param order
	 * @param returnAmount
	 * @param paymentMethod
	 * @return
	 */
	public RefundPaymentRespone initiateSplitRefund(SplitSalesOrder splitSalesOrder, String returnAmount, String paymentMethod) {
		RefundPaymentRespone refundPayment = null;
		LOGGER.info("BNPL Refund Initiated. Order ID : " + splitSalesOrder.getIncrementId() + " Amount: " + returnAmount);
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			refundPayment = tabbyHelper.refundSplitPayment(splitSalesOrder, returnAmount);
		} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			refundPayment = tamaraHelper.refundSplitPayment(splitSalesOrder, returnAmount);
		}

		LOGGER.info("BNPL Order " + splitSalesOrder.getIncrementId() + "completed. Response from BNPL : " + refundPayment);
		if(Objects.nonNull(refundPayment) && refundPayment.isStatus())
			return refundPayment;

		// If Payment is not able to refund then close the Payment
		if (Objects.nonNull(refundPayment) && !refundPayment.isStatus()
				&& OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			return tamaraHelper.cancelSplitPayment(splitSalesOrder, returnAmount);
		}
		return refundPayment;
	}

	/**
	 * Overloaded method to accept more scenario without impacting existing method signature and logic
	 * @param order
	 * @param addtionals
	 * @return
	 */
	public RefundPaymentRespone initiateRefund(SalesOrder order, PaymentReturnAdditioanls addtionals) {
		String returnAmount = addtionals.getReturnAmount();
		String paymentMethod = addtionals.getPaymentMethod();
		LOGGER.info("BNPL Payment Refund Initiated. Order ID : " + order.getIncrementId() + " Amount: " + returnAmount);
		if (paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE)) {
			return cashfreeHelper.refundPayment(order, addtionals);
		} else if (Regions.INDIA.equals(region)
				&& PaymentConstants.CASHFREE.equals(addtionals.getRmaRequest().getRmaPaymentMethod())) {
			CashfreePaymentServiceImpl cfservice = (CashfreePaymentServiceImpl) cashfreePaymentService;
			return cfservice.initiateCashgramRefund(order, addtionals);
		} else {
			return initiateRefund(order, returnAmount, paymentMethod);
		}
	}
	
	/**
	 * Initiate BNPL Close/Cancel 
	 * @param order
	 * @param returnAmount
	 * @param paymentMethod
	 * @return
	 */
	public RefundPaymentRespone initiateClose(SalesOrder order, String returnAmount, String paymentMethod) {
		RefundPaymentRespone cancelPayment = new RefundPaymentRespone();
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			String paymentId = order.getSubSalesOrder().getPaymentId();
			TabbyPayment tabbyRes = tabbyHelper.closePayment(paymentId);
			if(tabbyRes.isSuccess())
				cancelPayment.setStatus(true);
			else
				cancelPayment.setStatusMsg(tabbyRes.getStatus());
		} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {
			cancelPayment = tamaraHelper.cancelPayment(order, returnAmount);
		}
		return cancelPayment;
	}
	
	public BigDecimal getCaptureAmount(SalesOrder order) {
		
		BigDecimal totalAmountToRefund = new BigDecimal(0);
		boolean cancelSkuFlag = false;
		List<SalesOrderItem> salesorderItem = 
				order.getSalesOrderItem().stream().filter(e-> e.getProductType().equalsIgnoreCase("simple")).collect(Collectors.toList());
		for (SalesOrderItem item : salesorderItem) {

			BigDecimal priceIncludeTax = item.getPriceInclTax();
			BigDecimal qtyCancelled = item.getQtyCanceled();
			BigDecimal qtyOrdered = item.getQtyOrdered();
			BigDecimal discountVal;
			BigDecimal discountPriceIncludeTax = new BigDecimal(0);

			if (null != qtyCancelled &&  qtyCancelled.compareTo(BigDecimal.ZERO) != 0) {
				
				LOGGER.info("order has cancel sku , order:"+order.getIncrementId());
				
				BigDecimal cancelledindivisualdiscount = item.getDiscountAmount().divide(item.getQtyOrdered(), 4, RoundingMode.HALF_UP)
						.setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
				BigDecimal cancelledDiscountVal = cancelledindivisualdiscount.multiply(item.getQtyCanceled()).setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
				
				discountPriceIncludeTax = priceIncludeTax.multiply(item.getQtyCanceled()).setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP)
						.subtract(cancelledDiscountVal);
				
				qtyOrdered = qtyOrdered.subtract(item.getQtyCanceled());
				
				cancelSkuFlag = true;
			}
			if (null != item.getDiscountAmount() && item.getDiscountAmount().compareTo(BigDecimal.ZERO) != 0) {

				BigDecimal indivisualdiscount = item.getDiscountAmount()
						.divide(item.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);
				discountVal = indivisualdiscount.multiply(item.getQtyOrdered()).setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);
				
				if((qtyCancelled.compareTo(BigDecimal.ZERO) == 0)) {
					
					discountVal = item.getDiscountAmount();
				}

				priceIncludeTax = priceIncludeTax.multiply(item.getQtyOrdered()).setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP).subtract(discountVal).subtract(discountPriceIncludeTax);

				totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);
			} else {
				priceIncludeTax = priceIncludeTax.multiply(qtyOrdered).setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
				totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);
			}
		}
		if(Objects.nonNull(order.getAmstorecreditAmount())) {
			
			BigDecimal amstyStoreCredit = order.getAmstorecreditAmount();
			
			
			if (cancelSkuFlag) {

				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(order.getIncrementId()).concat("\"]");
				LOGGER.info("OrderActionData:" + orderActionData);

				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData,0);
				
				if(CollectionUtils.isNotEmpty(amastyHistoryList)) {
					
					for(AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {
						
						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}
					
					amstyStoreCredit = amstyStoreCredit.subtract(cancelledAmastyAmount);
				}
			}
			
				totalAmountToRefund = totalAmountToRefund.subtract(amstyStoreCredit);
		}
		if(Objects.nonNull(order.getShippingAmount())) {
			totalAmountToRefund = totalAmountToRefund.add(order.getShippingAmount());
		}
		if(Objects.nonNull(order.getSubSalesOrder().getDonationAmount())) {
			totalAmountToRefund = totalAmountToRefund.add(order.getSubSalesOrder().getDonationAmount());
		}
		if(Objects.nonNull(order.getImportFee())) {
			totalAmountToRefund = totalAmountToRefund.add(order.getImportFee());
		}
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()
				&& (Objects.nonNull(order.getSubSalesOrder().getEasValueInCurrency()))) {
			totalAmountToRefund = totalAmountToRefund.subtract(order.getSubSalesOrder().getEasValueInCurrency());

		}

		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency()
				&& order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0) {
			totalAmountToRefund = totalAmountToRefund.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());

		}
		
		return totalAmountToRefund;
	}
	
	/**
	 * Publish orderId to Kafka
	 * 
	 * @param orderId
	 */
	public void publishToKafka(OrderSms orderId) {
		kafkaService.publishToKafka(orderId);
	}
	
	public CashfreePaymentDTO buildPaymentDTOForCashfree(String paymentPayload) {
		try {
			JsonObject jsonObj = JsonParser.parseString(paymentPayload).getAsJsonObject();
			JsonObject data = jsonObj.get("data").getAsJsonObject();
			JsonObject jsonObjorder = data.get("order").getAsJsonObject();
			String orderId = jsonObjorder.get(Constants.ORDER_ID_KEY).getAsString();
			JsonObject jsonObjpayment = data.get("payment").getAsJsonObject();
			String paymentStatus = jsonObjpayment.get("payment_status").getAsString();
			String paymentId = jsonObjpayment.get("cf_payment_id").getAsString();
			CashfreePaymentDTO paymentDTO = new CashfreePaymentDTO(paymentId, paymentStatus, paymentPayload);
			setPaymentOption(jsonObjpayment, paymentDTO);
			paymentDTO.setOrderId(orderId);
			paymentDTO.setCfOrderId(paymentId);
			paymentDTO.setMessage(paymentStatus);

			return paymentDTO;
		} catch (Exception e) {
			LOGGER.error("error in parse cashfree webhook response : "+ e);
		}
		return null;
	}

	private static void setPaymentOption(JsonObject jsonObjpayment, CashfreePaymentDTO paymentDTO) {
		try {
			String jsonObjPaymentMethod = jsonObjpayment.get("payment_method").getAsJsonObject().entrySet().iterator()
					.next().getKey();
			paymentDTO.setPaymentType(jsonObjPaymentMethod);
		} catch (Exception e) {
			LOGGER.error("Error in setting cashfree payment option : " + e);
			paymentDTO.setPaymentType("");
		}
	}
	
	public CashfreePaymentDTO buildPaymentDTOForCashfreeRetrievePayment(String paymentPayload) {
		try {
			JsonObject jsonObj = JsonParser.parseString(paymentPayload).getAsJsonObject();
			String orderId = jsonObj.get(Constants.ORDER_ID_KEY).getAsString();
			String orderStatus = jsonObj.get("order_status").getAsString();
			String paymentId = jsonObj.get("cf_order_id").getAsString();
			CashfreePaymentDTO paymentDTO = new CashfreePaymentDTO(paymentId, orderStatus, paymentPayload);
			paymentDTO.setCfOrderId(paymentId);
			paymentDTO.setMessage(orderStatus);
			paymentDTO.setPaymentType("");
			paymentDTO.setOrderId(orderId);
			return paymentDTO;
		} catch (Exception e) {
			LOGGER.error("error in parse cashfree get status response: "+ e);
		}
		return null;
	}
	
	public void processBNPLPayment(PaymentUtility paymentUtility, SalesOrder order, Optional<String> paymentMethod, 
			TabbyHelper tabbyHelper, TamaraHelper tamaraHelper) {

		LOGGER.info("PaymentUtility -> processBNPLPayment starts : Payment Method: " + paymentMethod.get());
		boolean bnplStatusFlag = false;
		boolean captureFlag = false;

		BigDecimal captureAmount = paymentUtility.getCaptureAmount(order);
		BigDecimal totalAmount = order.getGrandTotal();
		int diffamount = totalAmount.compareTo(captureAmount);
		LOGGER.info("Create Shipment BNPL Amount to Capture : " + captureAmount + " | Total Amount : "
				+ totalAmount + " | Diff Amount : " + diffamount);
		if (captureAmount.compareTo(BigDecimal.ZERO) == 0) {
			LOGGER.info("Capture Amount is Zero");
			if (OrderConstants.checkTabbyPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tabby call");
				tabbyHelper.closePayment(order.getSubSalesOrder().getPaymentId());
				bnplStatusFlag = true;
			} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tamara call");
				tamaraHelper.cancelPayment(order, order.getGrandTotal().toString());
				bnplStatusFlag = true;
			}
		}
		if (diffamount == 0) {
			LOGGER.info("Diffamount Amount is Zero");
			captureFlag = paymentUtility.capturePayment(order, paymentMethod.get());
			bnplStatusFlag = true;
		} else if (diffamount >= 1) {
			LOGGER.info("Diffamount Amount is One");
			if (OrderConstants.checkTabbyPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tabby call");
				captureFlag = paymentUtility.capturePayment(order, paymentMethod.get());
				tabbyHelper.closePayment(order.getSubSalesOrder().getPaymentId());
			} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tamara call");
				captureFlag = paymentUtility.capturePayment(order, paymentMethod.get());
			}
			bnplStatusFlag = true;
		}

		LOGGER.info("bnplStatusFlag:" + bnplStatusFlag + " captureFlag" + captureFlag);
		if (!captureFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown for capture");
			throw new WmsException("There is error in capture payment!");
		} else if (!bnplStatusFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown");
			throw new WmsException("BNPL capture call has not triggered ");
		}
		
		LOGGER.info("PaymentUtility -> processBNPLPayment exits");
	}
	
	/**
	 * responsible to calculate capture amount and call payment service capture payment api
	 * @param paymentUtility
	 * @param order
	 * @param paymentMethod
	 */
	public PayfortReposne processPayfortAuthorizedCapturePayment(PaymentUtility paymentUtility, SalesOrder order, Optional<String> paymentMethod) {
		
		LOGGER.info("PaymentUtility -> processPayfortAuthorizedCapturePayment starts : Payment Method:" + paymentMethod.get());
		PayfortReposne response = null;
		boolean payfortPaymentStatusFlag = false;
		boolean captureFlag = false;
		BigDecimal captureAmount = paymentUtility.getCaptureAmount(order);
		BigDecimal totalAmount = order.getGrandTotal();
		int diffamount = totalAmount.compareTo(captureAmount);
		LOGGER.info("Payfort Authorized Amount to Capture : " + captureAmount + " | Total Amount : "
				+ totalAmount + " | Diff Amount : " + diffamount);
		
		PayfortConfiguration configuration = new PayfortConfiguration();
		paymentRefundHelper.getPayfortConfDetails(order.getStoreId().toString(), paymentMethod.get(), configuration);
		try {
			LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
		} catch (JsonProcessingException e) {
			LOGGER.error("error during write configuration:" + e.getMessage());
		}
		
		String fortId = null;
		if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
			for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
				fortId = payment.getCcTransId();
			}
		}
		
		if (diffamount >= 0) {
			LOGGER.info("Diff amount Amount is Zero");
			PayfortPaymentCaptureRequest request = preparePayfortCaptureRequest(configuration, order, captureAmount, fortId);
			response = triggerPayfortPaymentCaptureRestApiCall(request, order, configuration);
			if(response.isStatus()) {
				captureFlag = true;
				payfortPaymentStatusFlag = true;
			}
							
		}//else if(diffamount >=1) {
			//LOGGER.error("error thrown for capture");
			//throw new WmsException("There is error in capture payment!");
		//}
		
		LOGGER.info("payfortPaymentStatusFlag: " + payfortPaymentStatusFlag + " | "+ "captureFlag: " + captureFlag);
		if (!captureFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown for capture");
			throw new WmsException("There is error in capture payment!");
		} else if (!payfortPaymentStatusFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown");
			throw new WmsException("Payfort Authorize payment capture call has not triggered");

		}
	
		LOGGER.info("PaymentUtility -> processPayfortAuthorizedCapturePayment exits");
		return response;
	}
	
	/**
	 * responsible to prepare payfort authorized capture payment
	 * @param configuration
	 * @param order
	 * @param fortId
	 * @return
	 */
	public PayfortPaymentCaptureRequest preparePayfortCaptureRequest(PayfortConfiguration configuration, SalesOrder order, BigDecimal captureAmount, String fortId) {
		PayfortPaymentCaptureRequest request = new PayfortPaymentCaptureRequest();
		request.setAccessCode(configuration.getAccessCode());
		request.setAmount(Constants.getConvertedAmount(String.valueOf(captureAmount), configuration.getMultiplier()));
		request.setCommand(OrderConstants.CAPTURE);
		request.setCurrency(order.getStoreCurrencyCode());
		request.setFortId(fortId);
		request.setLanguage(configuration.getLanguage());
		request.setMerchantIdentifier(configuration.getMerchantIdentifier());
		
		String incrementId = paymentRefundHelper.getIncrementIdForApplePay(order);
		
		request.setMerchantReference(incrementId);
		request.setOrderDescription(incrementId);
		request.setSignature(getPayfortCaptureRequestSignature(request, configuration.getSignatureHash()));
		LOGGER.info("preparePayfortCaptureRequest — Amount {}"+ Constants.getConvertedAmount(String.valueOf(captureAmount), configuration.getMultiplier()));
		return  request;
	}



	/**
	 * responsible to prepare payfort authorized capture payment
	 * @param configuration
	 * @param splitSalesOrder
	 * @param fortId
	 * @return
	 */
	public PayfortPaymentCaptureRequest preparePayfortCaptureRequestV2(PayfortConfiguration configuration, SplitSalesOrder splitSalesOrder, BigDecimal captureAmount, String fortId) {
		PayfortPaymentCaptureRequest request = new PayfortPaymentCaptureRequest();
		request.setAccessCode(configuration.getAccessCode());
		request.setAmount(Constants.getConvertedAmount(String.valueOf(captureAmount), configuration.getMultiplier()));
		request.setCommand(OrderConstants.CAPTURE);
		request.setCurrency(splitSalesOrder.getStoreCurrencyCode());
		request.setFortId(fortId);
		request.setLanguage(configuration.getLanguage());
		request.setMerchantIdentifier(configuration.getMerchantIdentifier());

		String incrementId = paymentRefundHelper.getIncrementIdForApplePayV2(splitSalesOrder);

        request.setMerchantReference(incrementId);
        request.setOrderDescription(incrementId);
        request.setSignature(getPayfortCaptureRequestSignature(request, configuration.getSignatureHash()));
        LOGGER.info("preparePayfortCaptureRequest — Amount {}"+ Constants.getConvertedAmount(String.valueOf(captureAmount), configuration.getMultiplier()));
        return  request;
    }
    /**
     * @param request
     * @param signatureHash
     * @return
     */
    private String getPayfortCaptureRequestSignature(PayfortPaymentCaptureRequest request, String signatureHash) {

        String signature = null;
        StringBuilder signatureBuilder = new StringBuilder();
        signatureBuilder.append("access_code=").append(request.getAccessCode());
        signatureBuilder.append("amount=").append(request.getAmount());
        signatureBuilder.append("command=").append(OrderConstants.CAPTURE);
        signatureBuilder.append("currency=").append(request.getCurrency());
        signatureBuilder.append("fort_id=").append(request.getFortId() != null ? request.getFortId() : "null");
        signatureBuilder.append("language=").append(request.getLanguage());
        signatureBuilder.append("merchant_identifier=").append(request.getMerchantIdentifier());
        signatureBuilder.append("merchant_reference=").append(request.getMerchantReference());
        signatureBuilder.append("order_description=").append(request.getOrderDescription());

        String signatureRaw = signatureBuilder.toString();
        signature = signatureHash + signatureRaw + signatureHash;

        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
    }

    /**
     * responsible to call capture payment api of payment service for payfort authorized transaction
     * @param request
     * @param order
     * @return
     */
    public PayfortReposne triggerPayfortPaymentCaptureRestApiCall(PayfortPaymentCaptureRequest request, SalesOrder order,PayfortConfiguration configuration) {

			PayfortReposne payfortResponse = new PayfortReposne();
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

			HttpEntity<PayfortPaymentCaptureRequest> requestBody = new HttpEntity<>(request, requestHeaders);			
			//String url = Constants.orderCredentials.getOrderDetails().getPaymentServiceBaseUrl() + "/capturePayment";
			String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi";
			boolean payfortCaptureChanges = Constants.orderCredentials.getPayfort().isPayfortCaptureChanges();
			
			try {
				
				LOGGER.info("payfort url for capture payment: " + url);
				LOGGER.info("Request body: " + mapper.writeValueAsString(requestBody));

				ResponseEntity<PayfortCaptureResponse> response = restTemplate.exchange(url, HttpMethod.POST,
						requestBody, PayfortCaptureResponse.class);

				if (response.getStatusCode() == HttpStatus.OK) {
					PayfortCaptureResponse responseBody = response.getBody();

                LOGGER.info("Payfort capture response Body: " + mapper.writeValueAsString(responseBody));
                if (null != responseBody && null != responseBody.getStatus()) {
                    if (responseBody.getStatus().equals(OrderConstants.CAPTURE_PAYMENT_STATUS)) {
                        payfortResponse.setStatus(true);
                        payfortResponse.setMessage(responseBody.getResponseMessage());
                    } else if (responseBody.getStatus().equals(OrderConstants.CAPTURE_ALREADY_DONE_PAYMENT_STATUS) && payfortCaptureChanges && responseBody.getResponseCode().equals(OrderConstants.CAPTURE_ALREADY_DONE_PAYMENT_CODE)) {
                        //capture amount validation in payfort to check the status of already captured amount, if any.
                        //CAPTURE_AlREADY_DONE_PAYMENT_STATUS is a flag set in consul to enable/disable this part
                        PayfortPaymentCaptureStatusCheckRequest captureRequest = preparePayfortPaymentCaptureStatusCheckRequest(configuration,  order);
                        Boolean statusMatch = triggerPayfortPaymentStatusCheckRestApiCall(captureRequest, request.getAmount());
                        LOGGER.info(" Response for Capture Status Check API: " + statusMatch);
                        if(statusMatch != null && statusMatch) {
                            payfortResponse.setStatus(true);
                            payfortResponse.setMessage(responseBody.getResponseMessage());
                        } else {
                            payfortResponse.setStatus(false);
                            payfortResponse.setMessage(responseBody.getResponseMessage());
                        }
                    } else {
                        payfortResponse.setStatus(false);
                        payfortResponse.setMessage(responseBody.getResponseMessage());
                    }
                } else {
                    payfortResponse.setStatus(false);
                    payfortResponse.setMessage("Invalid response from Payfort");
                }
            }
        } catch (RestClientException | JsonProcessingException e2) {
            LOGGER.error("Exception occoured during capture payment process: " + order.getIncrementId() + " " + e2.getMessage());
            payfortResponse.setStatus(false);
            payfortResponse.setMessage(e2.getMessage());
        }

			return payfortResponse;
		}



	/**
	 * responsible to call capture payment api of payment service for payfort authorized transaction
	 * @param request
	 * @param splitSalesOrder
	 * @return
	 */
	public PayfortReposne triggerPayfortPaymentCaptureRestApiCallV2(PayfortPaymentCaptureRequest request, SplitSalesOrder splitSalesOrder,PayfortConfiguration configuration) {

		PayfortReposne payfortResponse = new PayfortReposne();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity<PayfortPaymentCaptureRequest> requestBody = new HttpEntity<>(request, requestHeaders);
		//String url = Constants.orderCredentials.getOrderDetails().getPaymentServiceBaseUrl() + "/capturePayment";
		String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi";
		boolean payfortCaptureChanges = Constants.orderCredentials.getPayfort().isPayfortCaptureChanges();

		try {

			LOGGER.info("payfort url for capture payment: " + url);
			LOGGER.info("Request body: " + mapper.writeValueAsString(requestBody));

			ResponseEntity<PayfortCaptureResponse> response = restTemplate.exchange(url, HttpMethod.POST,
					requestBody, PayfortCaptureResponse.class);

			if (response.getStatusCode() == HttpStatus.OK) {
				PayfortCaptureResponse responseBody = response.getBody();

                LOGGER.info("Payfort capture response Body: " + mapper.writeValueAsString(responseBody));
                if (null != responseBody && null != responseBody.getStatus()) {
                    if (responseBody.getStatus().equals(OrderConstants.CAPTURE_PAYMENT_STATUS)) {
                        payfortResponse.setStatus(true);
                        payfortResponse.setMessage(responseBody.getResponseMessage());
                    } else if (responseBody.getStatus().equals(OrderConstants.CAPTURE_ALREADY_DONE_PAYMENT_STATUS) && payfortCaptureChanges && responseBody.getResponseCode().equals(OrderConstants.CAPTURE_ALREADY_DONE_PAYMENT_CODE)) {
                        //capture amount validation in payfort to check the status of already captured amount, if any.
                        //CAPTURE_AlREADY_DONE_PAYMENT_STATUS is a flag set in consul to enable/disable this part
                        PayfortPaymentCaptureStatusCheckRequest captureRequest = preparePayfortPaymentCaptureStatusCheckRequestV2(configuration,  splitSalesOrder);
                        Boolean statusMatch = triggerPayfortPaymentStatusCheckRestApiCall(captureRequest, request.getAmount());
                        LOGGER.info(" Response for Capture Status Check API: " + statusMatch);
                        if(statusMatch != null && statusMatch) {
                            payfortResponse.setStatus(true);
                            payfortResponse.setMessage(responseBody.getResponseMessage());
                        } else {
                            payfortResponse.setStatus(false);
                            payfortResponse.setMessage(responseBody.getResponseMessage());
                        }
                    } else {
                        payfortResponse.setStatus(false);
                        payfortResponse.setMessage(responseBody.getResponseMessage());
                    }
                } else {
                    payfortResponse.setStatus(false);
                    payfortResponse.setMessage("Invalid response from Payfort");
                }
            }
        } catch (RestClientException | JsonProcessingException e2) {
            LOGGER.error("Exception occoured during capture payment process: " + splitSalesOrder.getIncrementId() + " " + e2.getMessage());
            payfortResponse.setStatus(false);
            payfortResponse.setMessage(e2.getMessage());
        }

		return payfortResponse;
	}
	
	 /**
	  * function responsible to validate if requested capture amount during shipment creation is already captured in payfort
	 * @param request
	 * @param order
	 * @return
	 */

	private Boolean triggerPayfortPaymentStatusCheckRestApiCall(PayfortPaymentCaptureStatusCheckRequest request, String capturedAmount) {
	    PayfortCaptureStatusCheckResponse response = callPayfortStatusCheckApi(request);

	    if (response == null) {
	        return false;
	    }

	    LOGGER.info("Payfort Status Check: Captured Amount from Response = {}" +response.getCapturedAmount());
	    return capturedAmount != null && capturedAmount.equals(response.getCapturedAmount());
	}

	 /**
	  * API call responsible to validate if requested capture amount during shipment creation is already captured in payfort
	 * @param request
	 * @param order
	 * @return
	 */
	public PayfortCaptureStatusCheckResponse callPayfortStatusCheckApi(PayfortPaymentCaptureStatusCheckRequest request) {
	    String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi";
	    HttpHeaders headers = createJsonHeaders();
	    HttpEntity<PayfortPaymentCaptureStatusCheckRequest> httpEntity = new HttpEntity<>(request, headers);

	    try {
	        LOGGER.info("Payfort Status Check: URL = {}" +url);
	        LOGGER.info("Payfort Status Check: Request Body = {}" +mapper.writeValueAsString(request));

	        ResponseEntity<PayfortCaptureStatusCheckResponse> response = restTemplate.exchange(
	                url,
	                HttpMethod.POST,
	                httpEntity,
	                PayfortCaptureStatusCheckResponse.class
	        );

	        if (response.getStatusCode() != HttpStatus.OK) {
	            LOGGER.error("Payfort Status Check: Non-OK HTTP Status = {}" +response.getStatusCode());
	            return null;
	        }

	        PayfortCaptureStatusCheckResponse responseBody = response.getBody();
	        LOGGER.info("Payfort Status Check: Response Body = {}" +mapper.writeValueAsString(responseBody));
	        return responseBody;

	    } catch (Exception ex) {
	        LOGGER.error("Payfort Status Check: Exception occurred while calling API" +ex);
	        return null;
	    }
	}

	private HttpHeaders createJsonHeaders() {
	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
	    return headers;
	}

	/**
	 * responsible to prepare payfort capture status check request
	 * @param configuration
	 * @param order
	 * @param fortId
	 * @return
	 */
	public PayfortPaymentCaptureStatusCheckRequest preparePayfortPaymentCaptureStatusCheckRequest(PayfortConfiguration configuration, SalesOrder order) {
		PayfortPaymentCaptureStatusCheckRequest request = new PayfortPaymentCaptureStatusCheckRequest();		
		request.setQueryCommand("CHECK_STATUS");
	    request.setAccessCode(configuration.getAccessCode());
	    request.setMerchantIdentifier(configuration.getMerchantIdentifier());
	    request.setMerchantReference(order.getIncrementId());
	    request.setLanguage(configuration.getLanguage());
	    request.setSignature(getPayfortPaymentCaptureStatusCheckSignature(request, configuration.getSignatureHash()));
	    
	    return  request;
	}

	/**
	 * responsible to prepare payfort capture status check request
	 * @param configuration
	 * @param splitSalesOrder
	 * @param fortId
	 * @return
	 */
	public PayfortPaymentCaptureStatusCheckRequest preparePayfortPaymentCaptureStatusCheckRequestV2(PayfortConfiguration configuration, SplitSalesOrder splitSalesOrder) {
		PayfortPaymentCaptureStatusCheckRequest request = new PayfortPaymentCaptureStatusCheckRequest();
		request.setQueryCommand("CHECK_STATUS");
		request.setAccessCode(configuration.getAccessCode());
		request.setMerchantIdentifier(configuration.getMerchantIdentifier());
		request.setMerchantReference(splitSalesOrder.getIncrementId());
		request.setLanguage(configuration.getLanguage());
		request.setSignature(getPayfortPaymentCaptureStatusCheckSignature(request, configuration.getSignatureHash()));

        return  request;
    }

    /**
     * @param request
     * @param signatureHash
     * @return
     */
    private String getPayfortPaymentCaptureStatusCheckSignature(PayfortPaymentCaptureStatusCheckRequest request, String signatureHash) {

        String signature = null;
        StringBuilder signatureBuilder = new StringBuilder();
        signatureBuilder.append("access_code=").append(request.getAccessCode());
        signatureBuilder.append("language=").append(request.getLanguage());
        signatureBuilder.append("merchant_identifier=").append(request.getMerchantIdentifier());
        signatureBuilder.append("merchant_reference=").append(request.getMerchantReference());
        signatureBuilder.append("query_command=").append("CHECK_STATUS");

        String signatureRaw = signatureBuilder.toString();
        signature = signatureHash + signatureRaw + signatureHash;

        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
    }

	public void processBNPLPaymentV2(PaymentUtility paymentUtility, SplitSalesOrder splitSalesOrder, Optional<String> paymentMethod,
									 TabbyHelper tabbyHelper, TamaraHelper tamaraHelper) {

		LOGGER.info("PaymentUtility -> processBNPLPayment starts : Payment Method: " + paymentMethod.get());
		boolean bnplStatusFlag = false;
		boolean captureFlag = false;

		BigDecimal captureAmount = paymentUtility.getCaptureAmountV2(splitSalesOrder);
		BigDecimal totalAmount = splitSalesOrder.getGrandTotal();
		int diffamount = totalAmount.compareTo(captureAmount);
		LOGGER.info("Create Shipment BNPL Amount to Capture : " + captureAmount + " | Total Amount : "
				+ totalAmount + " | Diff Amount : " + diffamount);
		if (captureAmount.compareTo(BigDecimal.ZERO) == 0) {
			LOGGER.info("Capture Amount is Zero");
			if (OrderConstants.checkTabbyPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tabby call");
				tabbyHelper.closePayment(splitSalesOrder.getSplitSubSalesOrder().getPaymentId());
				bnplStatusFlag = true;
			} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tamara call");
				tamaraHelper.cancelPayment(splitSalesOrder.getSalesOrder(), splitSalesOrder.getSalesOrder().getGrandTotal().toString());
				bnplStatusFlag = true;
			}
		}
		if (diffamount == 0) {
			LOGGER.info("Diffamount Amount is Zero");
			captureFlag = paymentUtility.capturePaymentV2(splitSalesOrder, paymentMethod.get());
			bnplStatusFlag = true;
		} else if (diffamount >= 1) {
			LOGGER.info("Diffamount Amount is One");
			if (OrderConstants.checkTabbyPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tabby call");
				captureFlag = paymentUtility.capturePaymentV2(splitSalesOrder, paymentMethod.get());
				tabbyHelper.closePayment(splitSalesOrder.getSplitSubSalesOrder().getPaymentId());
			} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod.get())) {
				LOGGER.info("Tamara call");
				captureFlag = paymentUtility.capturePaymentV2(splitSalesOrder, paymentMethod.get());
			}
			bnplStatusFlag = true;
		}

		LOGGER.info("bnplStatusFlag:" + bnplStatusFlag + " captureFlag" + captureFlag);
		if (!captureFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown for capture");
			throw new WmsException("There is error in capture payment!");
		} else if (!bnplStatusFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown");
			throw new WmsException("BNPL capture call has not triggered ");
		}

		LOGGER.info("PaymentUtility -> processBNPLPayment exits");
	}

	public BigDecimal getCaptureAmountV2(SplitSalesOrder splitSalesOrder) {

		BigDecimal totalAmountToRefund = new BigDecimal(0);
		boolean cancelSkuFlag = false;
		List<SplitSalesOrderItem> splitSalesOrderItems =
				splitSalesOrder.getSplitSalesOrderItems().stream().filter(e-> e.getProductType().equalsIgnoreCase("simple")).collect(Collectors.toList());
		for (SplitSalesOrderItem item : splitSalesOrderItems) {
			BigDecimal priceIncludeTax = item.getPriceInclTax();
			BigDecimal qtyCancelled = item.getQtyCanceled();
			BigDecimal qtyOrdered = item.getQtyOrdered();
			BigDecimal discountVal;
			BigDecimal discountPriceIncludeTax = new BigDecimal(0);

			if (null != qtyCancelled &&  qtyCancelled.compareTo(BigDecimal.ZERO) != 0) {

				LOGGER.info("splitSalesOrder has cancel sku , order:"+splitSalesOrder.getIncrementId());

				BigDecimal cancelledindivisualdiscount = item.getDiscountAmount().divide(item.getQtyOrdered(), 4, RoundingMode.HALF_UP)
						.setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
				BigDecimal cancelledDiscountVal = cancelledindivisualdiscount.multiply(item.getQtyCanceled()).setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);

				discountPriceIncludeTax = priceIncludeTax.multiply(item.getQtyCanceled()).setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP)
						.subtract(cancelledDiscountVal);

				qtyOrdered = qtyOrdered.subtract(item.getQtyCanceled());

				cancelSkuFlag = true;
			}
			if (null != item.getDiscountAmount() && item.getDiscountAmount().compareTo(BigDecimal.ZERO) != 0) {

				BigDecimal indivisualdiscount = item.getDiscountAmount()
						.divide(item.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);
				discountVal = indivisualdiscount.multiply(item.getQtyOrdered()).setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				if((qtyCancelled.compareTo(BigDecimal.ZERO) == 0)) {

					discountVal = item.getDiscountAmount();
				}

				priceIncludeTax = priceIncludeTax.multiply(item.getQtyOrdered()).setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP).subtract(discountVal).subtract(discountPriceIncludeTax);

				totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);
			} else {
				priceIncludeTax = priceIncludeTax.multiply(qtyOrdered).setScale(2, RoundingMode.HALF_UP).setScale(4,RoundingMode.HALF_UP);
				totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);
			}
		}
		if(Objects.nonNull(splitSalesOrder.getAmstorecreditAmount())) {

			BigDecimal amstyStoreCredit = splitSalesOrder.getAmstorecreditAmount();


			if (cancelSkuFlag) {

				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(splitSalesOrder.getIncrementId()).concat("\"]");
				LOGGER.info("OrderActionData:" + orderActionData);

				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData,0);

				if(CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for(AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

					amstyStoreCredit = amstyStoreCredit.subtract(cancelledAmastyAmount);
				}
			}

			totalAmountToRefund = totalAmountToRefund.subtract(amstyStoreCredit);
		}
		if(Objects.nonNull(splitSalesOrder.getShippingAmount())) {
			totalAmountToRefund = totalAmountToRefund.add(splitSalesOrder.getShippingAmount());
		}
		if(Objects.nonNull(splitSalesOrder.getSplitSubSalesOrder().getDonationAmount())) {
			totalAmountToRefund = totalAmountToRefund.add(splitSalesOrder.getSplitSubSalesOrder().getDonationAmount());
		}
		if(Objects.nonNull(splitSalesOrder.getImportFee())) {
			totalAmountToRefund = totalAmountToRefund.add(splitSalesOrder.getImportFee());
		}
		if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getEasCoins()
				&& (Objects.nonNull(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency()))) {
			totalAmountToRefund = totalAmountToRefund.subtract(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency());

		}

		if (null != splitSalesOrder.getSplitSubSalesOrder() && null !=splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency()
				&& splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0) {
			totalAmountToRefund = totalAmountToRefund.subtract(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency());

		}

		return totalAmountToRefund;
	}

	/**
	 * responsible to calculate capture amount and call payment service capture payment api
	 * @param paymentUtility
	 * @param splitSalesOrder
	 * @param paymentMethod
	 */
	public PayfortReposne processPayfortAuthorizedCapturePaymentV2(PaymentUtility paymentUtility, SplitSalesOrder splitSalesOrder, Optional<String> paymentMethod) {

		LOGGER.info("PaymentUtility -> processPayfortAuthorizedCapturePayment starts : Payment Method:" + paymentMethod.get());
		PayfortReposne response = null;
		boolean payfortPaymentStatusFlag = false;
		boolean captureFlag = false;
		BigDecimal captureAmount = paymentUtility.getCaptureAmountV2(splitSalesOrder);
		BigDecimal totalAmount = splitSalesOrder.getGrandTotal();
		int diffamount = totalAmount.compareTo(captureAmount);
		LOGGER.info("Payfort Authorized Amount to Capture : " + captureAmount + " | Total Amount : "
				+ totalAmount + " | Diff Amount : " + diffamount);

		PayfortConfiguration configuration = new PayfortConfiguration();
		paymentRefundHelper.getPayfortConfDetails(splitSalesOrder.getStoreId().toString(), paymentMethod.get(), configuration);
		try {
			LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
		} catch (JsonProcessingException e) {
			LOGGER.error("error during write configuration:" + e.getMessage());
		}

		String fortId = null;
		if (null!=splitSalesOrder.getSalesOrder()  && CollectionUtils.isNotEmpty(splitSalesOrder.getSalesOrder().getSalesOrderPayment())) {
			for (SalesOrderPayment payment : splitSalesOrder.getSalesOrder().getSalesOrderPayment()) {
				fortId = payment.getCcTransId();
			}
		}
		if (null == fortId) {
			LOGGER.error("Fort Id not found for order: " + splitSalesOrder.getIncrementId() + " - fortId " + fortId);
			throw new WmsException("Fort Id not found for order: " + fortId);
		}

        if (diffamount >= 0) {
            LOGGER.info("Diff amount Amount is Zero");
            PayfortPaymentCaptureRequest request = preparePayfortCaptureRequestV2(configuration, splitSalesOrder, captureAmount, fortId);
            response = triggerPayfortPaymentCaptureRestApiCallV2(request, splitSalesOrder, configuration);
            if(response != null && response.isStatus()) {
                captureFlag = true;
                payfortPaymentStatusFlag = true;
            } else if(response != null && !response.isStatus()) {
                LOGGER.error("Payfort payment error for order: " + splitSalesOrder.getIncrementId() + " - " + response.getMessage());
                throw new WmsException("Payment processing failed: " + response.getMessage());
            }

		}//else if(diffamount >=1) {
		//LOGGER.error("error thrown for capture");
		//throw new WmsException("There is error in capture payment!");
		//}

		LOGGER.info("payfortPaymentStatusFlag: " + payfortPaymentStatusFlag + " | "+ "captureFlag: " + captureFlag);
		if (!captureFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown for capture");
			throw new WmsException("There is error in capture payment!");
		} else if (!payfortPaymentStatusFlag && null != Constants.orderCredentials.getWms()
				&& Constants.orderCredentials.getWms().isCheckBNPLAmountDifference()) {
			LOGGER.error("error thrown");
			throw new WmsException("Payfort Authorize payment capture call has not triggered");

		}

		LOGGER.info("PaymentUtility -> processPayfortAuthorizedCapturePayment exits");
		return response;
	}

	public void publishToSplitPubSub(Integer orderId) {
		try {
			Map<String,String> pubsubPayload = new HashMap<>();
			pubsubPayload.put("orderId", orderId.toString());
			LOGGER.info("In Publishing Webhook split order pubsub for orderId: " + orderId+" and pubsubPayload: " + pubsubPayload);
			// Check If sales order is split order or not check and push to pubsub
			SalesOrder salesOrder = salesOrderRepository.findByEntityId(orderId);
			boolean isSplitOrder = Integer.valueOf(1).equals(salesOrder.getIsSplitOrder());
			if (isSplitOrder)
				pubSubServiceImpl.publishSplitOrderPubSub(splitOrderTopic,pubsubPayload);
		} catch (Exception e) {
			LOGGER.error("Error in publishing split order pubsub for orderId: " + orderId+" and error is: " + e);
		}
	}

	public void publishToSplitPubSubOTSForSalesOrder(SalesOrder salesOrder,String statusId,String flag) {
		try {
			LOGGER.info("In Publishing Webhook split order pubsub OTS for salesOrder entity id: " + salesOrder.getEntityId());
			// Check If sales order is split order or not check and push to pubsub
			pushToOTS(salesOrder,statusId,flag);
		} catch (Exception e) {
			LOGGER.error("Error in publishing split order pubsub OTS for orderId: " + (null!=salesOrder.getEntityId()?salesOrder.getEntityId():"")+" and error is: " + e);
		}
	}
	public void publishToSplitPubSubOTS(Integer orderId,String statusId,String flag) {
		try {
			LOGGER.info("In Publishing Webhook split order pubsub OTS for orderId: " + orderId);
			// Check If sales order is split order or not check and push to pubsub
			SalesOrder salesOrder = salesOrderRepository.findByEntityId(orderId);
			pushToOTS(salesOrder,statusId,flag);
		} catch (Exception e) {
			LOGGER.error("Error in publishing split order pubsub OTS for orderId: " + orderId+" and error is: " + e);
		}
	}

	private void pushToOTS(SalesOrder salesOrder,String statusId,String flag) throws JsonProcessingException {
		boolean isSplitOrder = Integer.valueOf(1).equals(salesOrder.getIsSplitOrder());
		if (isSplitOrder){
			OTSOrderRequest otsOrderRequest = buildOTSPayloadSplitOrder(salesOrder,statusId,flag);
			if(otsOrderRequest==null){
				return;
			}
			List<OTSOrderRequest> otsOrderRequestList = new ArrayList<>();
			otsOrderRequestList.add(otsOrderRequest);
			LOGGER.info("In Publishing Webhook split order pubsub OTS for salesOrder entity id: " + salesOrder.getEntityId()+" and payload: " + mapper.writeValueAsString(otsOrderRequestList));
			pubSubServiceImpl.publishOrderTrackingPubSub(splitOrderTrackingTopic,otsOrderRequestList);
		}
	}


    public OTSOrderRequest buildOTSPayloadSplitOrder(SalesOrder order,String statusId,String flag) {
        OTSOrderRequest otsOrderRequest = new OTSOrderRequest();
        otsOrderRequest.setOp("create");
        otsOrderRequest.setParentOrderId(order.getEntityId());
        otsOrderRequest.setIncrementId(order.getIncrementId());
        otsOrderRequest.setCustomerId(order.getCustomerId());
        otsOrderRequest.setCustomerEmail(order.getCustomerEmail());
        List<StatusMessage> statuses = new ArrayList<>();
        String currentTime = OffsetDateTime.now().toString();
		if (StringUtils.isNotBlank(flag)) {
			statuses.add(new StatusMessage(statusId, flag, currentTime));
			otsOrderRequest.setStatusMessage(statuses);
			return otsOrderRequest;
		}
        switch (order.getStatus().toLowerCase()) {
			case OrderConstants.PROCESSING_ORDER_STATUS -> statuses.add(new StatusMessage("3.0", "Processing", currentTime));
            case OrderConstants.ORDER_STATUS_PAYMENT_HOLD -> statuses.add(new StatusMessage("2.1", "On Hold", currentTime));
            case OrderConstants.PENDING_PAYMENT_ORDER_STATUS -> statuses.add(new StatusMessage("2.0", "Pending Payment", currentTime));
            case OrderConstants.PENDING_ORDER_STATUS -> statuses.add(new StatusMessage("2.0", "Pending", currentTime));
            case OrderConstants.FAILED_ORDER_STATUS -> statuses.add(new StatusMessage("3.0", "Payment Failed", currentTime));
            case OrderConstants.REFUNDED_ORDER_STATUS -> statuses.add(new StatusMessage("10.0", "Refunded", currentTime));
            case OrderConstants.ORDER_STATUS_RTO -> statuses.add(new StatusMessage("7.0", "RTO Initiated", currentTime));
        }

		if(statuses.isEmpty()){
			return null;
		}
        otsOrderRequest.setStatusMessage(statuses);
        return otsOrderRequest;
    }

	private RefundPaymentRespone doTamaraSplitRefunds(SalesOrder salesOrder,String returnAmount) {
		RefundPaymentRespone refundPaymentRespone = new RefundPaymentRespone();
		try {
			LOGGER.info("doTamaraSplitRefunds Initiating split refunds for order: " + salesOrder.getIncrementId()+" returnAmount : "+returnAmount);
			List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findBySalesOrder(salesOrder);
			if (splitSalesOrders == null || splitSalesOrders.isEmpty()) {
				LOGGER.error("No split sales orders found for salesOrder={} "+salesOrder.getIncrementId());
				refundPaymentRespone.setStatus(false);
				return refundPaymentRespone;
			}
			// If split sales orders are only one then we can directly call refund api
			if (splitSalesOrders.size() == 1) {
				LOGGER.info("Only one split sales order found for salesOrder: "+salesOrder.getIncrementId()+" Calling refund api directly with returnAmount : "+returnAmount);
				refundPaymentRespone = tamaraHelper.refundSplitPayment(
						splitSalesOrders.get(0),
						returnAmount
				);
				return refundPaymentRespone;
			}
			// If multiple split sales orders exist
			splitSalesOrders.sort(Comparator.comparing(SplitSalesOrder::getEntityId));
			BigDecimal remaining = new BigDecimal(returnAmount);
			BigDecimal totalRequested = new BigDecimal(returnAmount);
			LOGGER.info("Multiple split sales orders found for salesOrder: "+salesOrder.getIncrementId()+" Starting iterative refunds with total returnAmount : "+returnAmount);
			for (SplitSalesOrder splitSalesOrder : splitSalesOrders) {
				BigDecimal captured = getCaptureAmountV2(splitSalesOrder);
				BigDecimal refundAmount = captured.min(remaining).setScale(2, RoundingMode.HALF_UP);
				LOGGER.info("Calculated refund amount for splitSalesOrder={} is {} based on captured amount={} and remaining refund amount={}" +
						splitSalesOrder.getIncrementId() + refundAmount + captured + remaining);
				// Skip 0 refunds to avoid useless/invalid API calls
				if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				remaining = remaining.subtract(refundAmount);
				String refundAmountStr = amountToTwoDecimalPlace(refundAmount);
				refundPaymentRespone = tamaraHelper.refundSplitPayment(
						splitSalesOrder,refundAmountStr
				);
			}
			if (remaining.compareTo(BigDecimal.ZERO) > 0) {
				// Means requested refund was higher than total captured across splits
				LOGGER.warn("Refund partially completed due to insufficient captured amount. salesOrder={}, requested={}, remaining={}"+
						salesOrder.getIncrementId()+ totalRequested+ remaining);
			}
			return refundPaymentRespone;
		} catch (Exception e) {
			LOGGER.error("Error during Tamara split refunds for order: " + salesOrder.getIncrementId() + " and error is: " + e);
			 refundPaymentRespone.setStatus(false);
			 return refundPaymentRespone;
		}
	}

	/**
	 * Convert an amount to two decimal place
	 * @param amount
	 * @return
	 */
	private String amountToTwoDecimalPlace(BigDecimal amount) {
		DecimalFormat df = new DecimalFormat("###.##");
		return df.format(amount);
	}
}

/**
 * 
 */
package org.styli.services.order.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.CashfreeHelper;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.PaymentRefundHelper;
import org.styli.services.order.helper.RefundHelper;
import org.styli.services.order.helper.TabbyHelper;
import org.styli.services.order.helper.TamaraHelper;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.CancelDetails;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.PaymentService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.UtilityConstant;

/**
 * @author manineemahapatra
 *
 */

public abstract class PaymentServiceImpl implements PaymentService {

	private static final Log LOGGER = LogFactory.getLog(PaymentServiceImpl.class);

	private static final String AND_MESSAGE = "& Message ";

	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	TabbyHelper tabbyHelper;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	OrderHelperV2 orderHelperV2;

	@Autowired
	AmastyStoreCreditRepository amastyStoreCreditRepository;

	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Autowired
	SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Value("${auth.internal.jwt.token}")
	private String authInternalJwtToken;

	@Autowired
	ExternalQuoteHelper externalQuoteHelper;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	PaymentRefundHelper paymentDtfRefundHelper;

	@Autowired
	RefundHelper refundHelper;

	@Autowired
	private SalesCreditmemoRepository creditmemoRepository;

	@Autowired
	ProxyOrderRepository proxyOrderRepository;

	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	TamaraHelper tamaraHelper;

	@Autowired
	PaymentUtility paymentUtility;

	@Autowired
	public SalesOrderServiceV2 salesOrderServiceV2;

	@Autowired
	CashfreeHelper cashfreeHelper;

	public static final ObjectMapper objectMapper = new ObjectMapper();

	public BNPLOrderUpdateResponse onPaymentSuccess(PaymentDTO paymentDto, SalesOrder salesOrder,
			String paymentSuccessMsg, String modeOfPayment) {
		BNPLOrderUpdateResponse response = new BNPLOrderUpdateResponse();
		if (!salesOrder.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATE)) {
			response.setSuccess(false);
			response.setErrorMessage("Order status is already processed! Status: " + salesOrder.getStatus());
			return response;
		}
		LOGGER.info("Order status before:"+salesOrder.getStatus() +", orderId: "+salesOrder.getIncrementId() + ": Setting to:"+OrderConstants.PROCESSING_ORDER_STATUS);
		LOGGER.info("onPaymentSuccess: Fetching payment details for SalesOrder Increment ID: " + salesOrder.getIncrementId() + "status : " +salesOrder.getStatus());
		SalesOrderPayment salesOrderPayment = salesOrder.getSalesOrderPayment().stream().findFirst().orElse(null);
		setPaymentDetails(salesOrderPayment, paymentDto);
		salesOrder.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
		salesOrder.setState(OrderConstants.PROCESSING_ORDER_STATUS);
		salesOrder.setExtOrderId("0");
		SubSalesOrder subSalesOrder = salesOrder.getSubSalesOrder();
        if(subSalesOrder != null && subSalesOrder.getTotalShukranCoinsBurned() != null && subSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
			try {
				List<Stores> stores = Constants.getStoresList();
				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId()))
						.findAny().orElse(null);
				String lockResponse = commonService.lockUnlockShukranData(subSalesOrder.getCustomerProfileId(), String.valueOf(subSalesOrder.getTotalShukranCoinsBurned().intValue()), subSalesOrder.getQuoteId(), true, salesOrder, store, "Locking Points On Retry Payment Success", "On Retry Payment Success");
				if (StringUtils.isNotBlank(lockResponse) && StringUtils.isNotEmpty(lockResponse) && lockResponse.equalsIgnoreCase("api passed")) {
					LOGGER.info("in retry payment locking 2 ");
					subSalesOrder.setShukranLocked(0);
				}
			} catch (Exception e) {
				LOGGER.info("Locking Error Of Shukran Points"+ e.getMessage());
			}
		}
		subSalesOrder.setRetryPayment(0);

		salesOrder.setRetryPayment(0);

		updateOrderStatusHistory(salesOrder, paymentSuccessMsg, OrderConstants.ORDER_STATUS_HISTORY_ENTITY,
				salesOrder.getStatus());
		saveOrderGrid(salesOrder, OrderConstants.PROCESSING_ORDER_STATUS);
		if(salesOrder.getSubSalesOrder() != null && salesOrder.getSubSalesOrder().getRetryPayment() != null){
			LOGGER.info("onPaymentSuccess : BNPL retry Payment success. Retry count : " + salesOrder.getSubSalesOrder().getRetryPayment());
		}
		LOGGER.info("onPaymentSuccess: Updated status details for SalesOrder Increment ID: " + salesOrder.getIncrementId() + "status : " +salesOrder.getStatus());
		salesOrderRepository.saveAndFlush(salesOrder);
		onProxyOrderPaymentSuccess(paymentDto);
		orderHelper.updateStatusHistory(salesOrder, false, true, false, false, false);

		if (modeOfPayment != null) {
			LOGGER.info("Order " + salesOrder.getIncrementId() + "modeOfPayment is : " + modeOfPayment);
			orderHelperV2.publishPreferredPaymentIfValid(modeOfPayment, salesOrder);
		}

		response.setPaymentSuccess(true);
		response.setIncrementId(salesOrder.getIncrementId());
		response.setOrderEntityId(salesOrder.getEntityId());
		response.setSuccess(true);
		return response;
	}

	public void onProxyOrderPaymentSuccess(PaymentDTO paymentDto) {
		ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentDto.getId());
		if (Objects.nonNull(proxyOrder)) {
			proxyOrder.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
			LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in onProxyOrderPaymentSuccess");
			proxyOrderRepository.saveAndFlush(proxyOrder);
		}
	}

	/**
	 * Update SalesOrder status to Processing / Payment Failed to avoid race
	 * condition, if the replica call, WebHook & Scheduler execute at the same time.
	 * 
	 * @param salesOrder
	 * @param processingOrderStatus
	 */
	@Transactional
	public void updateOrderStatus(SalesOrder salesOrder, String processingOrderStatus) {
		SalesOrder order = salesOrderRepository.findByEntityId(salesOrder.getEntityId());
		order.setState(processingOrderStatus);
		LOGGER.info("Order state :"+processingOrderStatus + " in updateOrderStatus"+ " order id"+salesOrder.getIncrementId());
		salesOrderRepository.saveAndFlush(order);
	}

	@Transactional
	public void onPaymentFailure(PaymentDTO paymentDto, SalesOrder salesOrder, String paymentFailedMsg, String deviceId) {
		try {
			if (null != salesOrder
					&& salesOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
				updateOrderStatus(salesOrder, OrderConstants.FAILED_ORDER_STATUS);
				LOGGER.info("Order status :"+salesOrder.getStatus() + " in onPaymentFailure,  order id"+salesOrder.getIncrementId());
				SubSalesOrder subSalesOrder = salesOrder.getSubSalesOrder();
				subSalesOrder.setDtfLock(1);
				SalesOrderPayment salesOrderPayment = salesOrder.getSalesOrderPayment().stream().findFirst()
						.orElse(null);
				setPaymentDetails(salesOrderPayment, paymentDto);
				Long clientVersion = Constants.decodeAppVersion(salesOrder.getAppVersion());
				Long thresholdVersion = Constants.decodeAppVersion(Constants.getPaymentFailedThresholdVersion());
				String source = salesOrder.getSubSalesOrder().getClientSource();
				LOGGER.info("source" + source);
				LOGGER.info("thresholdVersion" + thresholdVersion);

				List<Stores> stores = Constants.getStoresList();
				Stores store = stores.stream()
						.filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId())).findAny()
						.orElse(null);
				if (store != null) {
					LOGGER.info("store hold order status" + store.isHoldOrder());
				}
				if (null != salesOrder.getSubSalesOrder()
						&& (clientVersion != null && thresholdVersion != null && clientVersion < thresholdVersion)) {
					failedOrder(paymentDto, salesOrder, paymentFailedMsg);
					orderHelper.releaseInventoryQty(salesOrder, new HashMap<>(), true, paymentFailedMsg);

				} else if (StringUtils.isNotBlank(source) && !UtilityConstant.APPSOURCELIST.contains(source)) {
					LOGGER.info("order order not placed from APP ");
					failedOrder(paymentDto, salesOrder, paymentFailedMsg);
					orderHelper.releaseInventoryQty(salesOrder, new HashMap<>(), true, paymentFailedMsg);
				} else if (clientVersion != null && thresholdVersion != null && clientVersion >= thresholdVersion
						&& null != store && !store.isHoldOrder()) {

					LOGGER.info("order order hold false so going to fail");
					failedOrder(paymentDto, salesOrder, paymentFailedMsg);
					orderHelper.releaseInventoryQty(salesOrder, new HashMap<>(), true, paymentFailedMsg);
				}

				LOGGER.info("grid set done");
				salesOrderRepository.saveAndFlush(salesOrder);

				failStatusOnwards(salesOrder, deviceId);
			}
		} catch (Exception e) {
	        updateOrderStatus(salesOrder, OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
	        LOGGER.error("Error in Tabby Payment Failure Status Update. ", e);
		}
	}

	private void failedOrder(PaymentDTO paymentDto, SalesOrder salesOrder, String paymentFailedMsg) {
		salesOrder.setStatus(OrderConstants.FAILED_ORDER_STATUS);
		salesOrder.setState(OrderConstants.FAILED_ORDER_STATUS);
		salesOrder.getSubSalesOrder().setRetryPayment(0);
		salesOrder.setRetryPayment(0);

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId())).findAny()
				.orElse(null);
		updateOrderStatusHistory(salesOrder, paymentFailedMsg, OrderConstants.ORDER_STATUS_HISTORY_ENTITY,
				salesOrder.getStatus());

		if(salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned() != null &&  salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && salesOrder.getSubSalesOrder().getShukranLocked() != null && salesOrder.getSubSalesOrder().getShukranLocked().equals(0)){
			commonService.lockUnlockShukranData(salesOrder.getSubSalesOrder().getCustomerProfileId(), salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), salesOrder.getSubSalesOrder().getQuoteId(), false, salesOrder, store, "Refund Shukran Burned Points On Failed Order", "");
			SubSalesOrder subSalesOrder= salesOrder.getSubSalesOrder();
			subSalesOrder.setShukranLocked(1);
			subSalesOrderRepository.saveAndFlush(subSalesOrder);
		}
		String updateMessage = paymentFailedMsg + "" + AND_MESSAGE + paymentDto.getStatus();

		updateOrderStatusHistory(salesOrder, updateMessage, OrderConstants.ORDER2, salesOrder.getStatus());

		saveOrderGrid(salesOrder, OrderConstants.FAILED_ORDER_STATUS);

		if (null != salesOrder.getAmstorecreditBaseAmount()) {
			releaseStoreCredit(salesOrder, salesOrder.getAmstorecreditBaseAmount());
			String stylicreditMsg = OrderConstants.STYLI_CREDIT_FAILED_MSG + salesOrder.getStoreCurrencyCode() + ""
					+ salesOrder.getAmstorecreditBaseAmount();
			updateOrderStatusHistory(salesOrder, stylicreditMsg, OrderConstants.ORDER2, salesOrder.getStatus());
		}
	}

	/**
	 * On Proxy Order Failure perform following action. 1. Release Inventory 2.
	 * Update Proxy Order status to : payment_failed
	 * 
	 * @param salesOrder
	 * @param proxyOrder
	 */
	public void onProxyOrderPaymentFailure(SalesOrder salesOrder, ProxyOrder proxyOrder) {

		proxyOrder.setStatus(OrderConstants.FAILED_ORDER_STATUS);
		try {
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId())).findAny()
					.orElse(null);
			SalesOrder existingOrder = salesOrderRepository.findByIncrementId(proxyOrder.getIncrementId());
			LOGGER.info("increment id : " + proxyOrder.getIncrementId());
			if (Objects.isNull(existingOrder)) {
				LOGGER.info("increment id not exists in sales order table");
				updateCouponAndInventoryReleaase(salesOrder, proxyOrder, stores, true);
			} else {
				SubSalesOrder subSalesOrder = existingOrder.getSubSalesOrder();
				boolean isHoldOrder = StringUtils.isNotEmpty(salesOrder.getExtOrderId()) && StringUtils.isNotBlank(salesOrder.getExtOrderId()) && salesOrder.getExtOrderId().equalsIgnoreCase("1");
				if(subSalesOrder.getTotalShukranCoinsBurned() != null &&  subSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && subSalesOrder.getShukranLocked()!=null && subSalesOrder.getShukranLocked().equals(0) && !salesOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS) && !isHoldOrder) {
					commonService.lockUnlockShukranData(subSalesOrder.getCustomerProfileId(), subSalesOrder.getTotalShukranCoinsBurned().toString(), subSalesOrder.getQuoteId(), false, salesOrder, store, "Refund Shukran Burned Points On Payment Failure", "");
					subSalesOrder.setShukranLocked(1);
					subSalesOrderRepository.saveAndFlush(subSalesOrder);
				}
				LOGGER.info("incremnt id is exists in sales order");
				boolean releaseFlag = true;
				Integer retryPaymentCount = subSalesOrder.getRetryPaymentCount();
				Integer retryPaymentCountThreshold = subSalesOrder.getRetryPaymentCountThreshold();
				if (Objects.nonNull(retryPaymentCount) && retryPaymentCount >= 1
						&& Objects.nonNull(retryPaymentCountThreshold) && retryPaymentCountThreshold >= 1) {
					releaseFlag = false;
				}
				updateCouponAndInventoryReleaase(salesOrder, proxyOrder, stores, releaseFlag);
			}
		} catch (Exception e) {
			LOGGER.error("Error In Coupon Reedmention. Error : " + e);
		}
		proxyOrderRepository.saveAndFlush(proxyOrder);
	}

	private void updateCouponAndInventoryReleaase(SalesOrder salesOrder, ProxyOrder proxyOrder, List<Stores> stores,
			boolean releaseFlag) {
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId()))
				.findAny().orElse(null);
		if (StringUtils.isNotEmpty(salesOrder.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(salesOrder.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& releaseFlag) {
			LOGGER.info("Orders are in pending_payment release coupon to fail :" + salesOrder.getIncrementId());
			salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, salesOrder, true, true);
		}
		if (releaseFlag) {
			orderHelper.releaseInventoryQty(salesOrder, new HashMap<>(), true, OrderConstants.FAILED_ORDER_STATUS);
			orderHelperV2.updateProxyOrderInventoryRelease(proxyOrder.getId(), true);
		}

	}

	/**
	 * @param salesOrderPayment
	 * @param order
	 */
	private void setPaymentDetails(SalesOrderPayment salesOrderPayment, PaymentDTO paymentDto) {

		LOGGER.info("payment set");
		if (null != salesOrderPayment) {

			try {
				String jsonInString = mapper.writeValueAsString(paymentDto);
				salesOrderPayment.setAdditionalInformation(jsonInString);
			} catch (JsonProcessingException e) {
				LOGGER.error("json parse exception during order payment");
			}
		}
	}

	private void releaseStoreCredit(SalesOrder salesOrder, BigDecimal storeCreditAmount) {

		List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
				.findByCustomerId(salesOrder.getCustomerId());
		AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
		if (amastyStoreCredit != null) {
			BigDecimal customerStoreCreditBalance = amastyStoreCredit.getStoreCredit();
			amastyStoreCredit.setStoreCredit(customerStoreCreditBalance.add(storeCreditAmount));
			amastyStoreCreditRepository.saveAndFlush(amastyStoreCredit);

			List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository
					.findByCustomerId(salesOrder.getCustomerId());
			int newCustomerHistoryId = 1;
			if (CollectionUtils.isNotEmpty(histories)) {
				AmastyStoreCreditHistory lastHistory = histories.get(histories.size() - 1);
				newCustomerHistoryId = lastHistory.getCustomerHistoryId() + 1;
			}
			AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
			history.setCustomerHistoryId(newCustomerHistoryId);
			history.setCustomerId(salesOrder.getCustomerId());
			history.setDeduct(0);
			history.setDifference(storeCreditAmount);
			history.setStoreCreditBalance(amastyStoreCredit.getStoreCredit());
			history.setAction(5);
			history.setActionData("[\"" + salesOrder.getIncrementId() + "\"]");
			history.setMessage(null);
			history.setCreatedAt(new Timestamp(new Date().getTime()));
			history.setStoreId(salesOrder.getStoreId());
			amastyStoreCreditHistoryRepository.saveAndFlush(history);
		}
	}

	/**
	 * @param request
	 * @param order
	 */
	public void updateOrderStatusHistory(SalesOrder order, String message, String entity, String status) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();
		LOGGER.info("History set");
		sh.setParentId(order.getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(1);
		sh.setComment("Order updated with message: " + message);
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setStatus(status);
		sh.setEntityName(entity);
		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}

	/**
	 * @param order
	 * @param message
	 */
	public void saveOrderGrid(SalesOrder order, String message) {
		SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());
		salesorderGrid.setStatus(message);
		LOGGER.info("in payment service impl Save to GRID : "+message +" for increment Id "+ order.getIncrementId());
		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}

	public void failStatusOnwards(SalesOrder order, String deviceId) {
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())) {
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
					.findAny().orElse(null);
			salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, order, true, false);
			String tokenHeader = authInternalJwtToken;
			String quoteId = order.getSubSalesOrder().getExternalQuoteId().toString();
			externalQuoteHelper.enableExternalQuote(quoteId, order.getStoreId(), tokenHeader, deviceId);
		}
	}

	/**
	 * Tabby payment refund
	 */
	@Override
	public String refundPayment(Integer orderId) {
		SalesOrder order = salesOrderRepository.findByEntityId(orderId);
		if (Objects.isNull(order))
			return "Order doesn't exists";
		List<SalesCreditmemo> creditMemos = creditmemoRepository.findByRmaNumber(order.getEntityId().toString());
		if (!creditMemos.isEmpty())
			return "Order already refunded.";

		String message = "";
		Optional<SalesOrderPayment> findFirst = order.getSalesOrderPayment().stream()
				.filter(ord -> StringUtils.containsIgnoreCase(ord.getMethod(), "tabby")).findFirst();
		if (!findFirst.isPresent()) {
			return "This isn't a tabby Order";
		}

		Map<String, BigDecimal> skumapList = new HashMap<>();
		List<SalesOrderItem> salesItemList = new ArrayList<>();
		BigDecimal amountToRefund = paymentDtfRefundHelper.getCancelAmount(order, skumapList, salesItemList);

		BigDecimal storeCreditAmount = order.getAmstorecreditAmount();
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		//Find import fee of order based on available items of order
		BigDecimal currentOrderValue = paymentDtfRefundHelper.findCurrentOrderValue(order);
		CancelDetails details = new CancelDetails();
		details.setCurrentOrderValue(currentOrderValue);
		String paymentMethod = findFirst.map(SalesOrderPayment::getMethod).orElse(null);
		String fortId = findFirst.map(SalesOrderPayment::getCcTransId).orElse(null);

		BigDecimal totalVoucherToRefund = BigDecimal.ZERO;
		BigDecimal onlineAmountToBeRefunded = paymentDtfRefundHelper.cancelPercentageCalculation(order, amountToRefund,
				storeCreditAmount, details, true, paymentMethod, totalVoucherToRefund);

		if (details.getAmasyStoreCredit() != null && order.getCustomerId() != null
				&& details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal baseAmastStoreCredit = details.getAmasyStoreCredit().multiply(order.getStoreToBaseRate())
					.setScale(4, RoundingMode.HALF_UP);
			refundHelper.releaseStoreCredit(order, baseAmastStoreCredit);
		}

		RefundPaymentRespone response = paymentDtfRefundHelper.payfortRefundcall(order, onlineAmountToBeRefunded,
				fortId, paymentMethod);

		String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
		SalesOrderGrid grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
		SalesCreditmemo memo = refundHelper.createCreditMemo(order, memoIncrementId, onlineAmountToBeRefunded,
				details.getAmasyStoreCredit(), paymentMethod, skumapList, true, null, response, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
		refundHelper.createCancelCreditmemoItems(order, memo, skumapList, salesItemList);
		if (StringUtils.isNotBlank(response.getStatusMsg())) {
			refundHelper.createCreditmemoFailComment(memo, details.getAmasyStoreCredit(), response.getStatusMsg());

		} else {
			refundHelper.createCreditmemoComment(memo, details.getAmasyStoreCredit());
		}
		refundHelper.createCreditmemoGrid(order, memo, memoIncrementId, grid, order.getBaseGrandTotal());

		if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)) {
			message = "We refunded " + order.getStoreCurrencyCode() + " " + memo.getGrandTotal() + " online tabby";
			if (null != order.getAmstorecreditAmount()) {
				message = message + "  & " + order.getStoreCurrencyCode() + " " + memo.getAmstorecreditAmount()
						+ "to your styli credit account";
			}
		} else {
			message = OrderConstants.WE_REFUND_STYLI_CREDIT + order.getStoreCurrencyCode()
					+ memo.getAmstorecreditAmount() + " to user account";
		}
		updateOrderStatusHistory(order, message, "return", order.getStatus());
		return "Refund success";
	}

	/**
	 * If order is not created then create from back-end.
	 * 
	 * @param paymentDto
	 * @param proxyOrder
	 * @return
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SalesOrder placeOrderInternally(PaymentDTO paymentDto, ProxyOrder proxyOrder, boolean isWebhook) {
		long timeDiff = new Date().getTime() - proxyOrder.getCreatedAt().getTime();
		long minutesDiff = TimeUnit.MILLISECONDS.toMinutes(timeDiff);
		LOGGER.info("Payment Order ID Not found : " + paymentDto.getId() + " Minutes Diff :" + minutesDiff);
		TabbyDetails tabby = Constants.orderCredentials.getTabby();

		if (minutesDiff > tabby.getBackendCreateOrderDurationMins() && tabby.isEnableBackendCreateOrder()
				&& !isWebhook) {
			String deviceId = deviceId(proxyOrder);
			try {
				LOGGER.info("Creating Order from Backend:" + proxyOrder.getPaymentId());

				CreateOrderRequestV2 orderReq = mapper.readValue(proxyOrder.getOrderRequest(),
						CreateOrderRequestV2.class);
				if (null != orderReq) {
					orderReq.setProxy(false);
					orderReq.setPaymentId(proxyOrder.getPaymentId());
					String source = orderReq.getXSource();
					String ip = orderReq.getCustomerIp();
					Map<String, String> requestHeader = new HashMap<>();
					boolean isSplitOrder = CheckSplitOrder(proxyOrder);
					if (isSplitOrder) {
						LOGGER.info("Order is split order, calling convertQuoteToOrderV3 for order creation. Order Id : " + proxyOrder.getIncrementId());
						CreateOrderResponseDTO result = salesOrderServiceV2.convertQuoteToOrderV3(orderReq, null,
								proxyOrder.getIncrementId(), source, requestHeader, null, null, ip, deviceId);
						LOGGER.info("[SPLIT ORDER] Order status: " + proxyOrder.getIncrementId()
								+ " Status Msg : " + result.getStatusMsg() + " Code " + result.getStatusCode());
					} else {
						LOGGER.info("Order is non split order, calling convertQuoteToOrderV2 for order creation. Order Id : " + proxyOrder.getIncrementId());
						CreateOrderResponseDTO result = salesOrderServiceV2.convertQuoteToOrderV2(orderReq, null,
								proxyOrder.getIncrementId(), source, requestHeader, null, null, ip, deviceId);
						LOGGER.info("[NON SPLIT ORDER] Order status: " + proxyOrder.getIncrementId()
								+ " Status Msg : " + result.getStatusMsg() + " Code " + result.getStatusCode());
					}
					return salesOrderService.findSalesOrderByPaymentId(paymentDto.getId());
				} else {
					LOGGER.info("order request value is null for:" + proxyOrder.getPaymentId());
					return null;
				}
			} catch (Exception e) {
				LOGGER.info("Error In Placing BNPL Order through Webhook. Error : " + e);
				return null;
			}
		} else
			return null;
	}

	private String deviceId(ProxyOrder proxyOrder) {
		String deviceId = null;
		try {
			if (Objects.nonNull(proxyOrder)) {
				SalesOrder so = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
				if (Objects.nonNull(so) && Objects.nonNull(so.getSubSalesOrder())) {
					SubSalesOrder sso = so.getSubSalesOrder();
					deviceId = sso.getDeviceId();
				}
			}
		} catch (Exception e) {
			LOGGER.error("Could not fetch deviceId from proxy subSalesOrder" + e.getMessage());
		}
		return deviceId;
	}

	@Override
	public boolean failProxyOrderByOrderId(String incrementId) {
		try {
			List<ProxyOrder> proxyOrders = proxyOrderRepository.findByIncrementId(incrementId);
			if(proxyOrders.isEmpty()) return false;
			
			LOGGER.info("Proxy orders to be failed. Order Id : " + incrementId);
			proxyOrders.stream().filter(po -> OrderConstants.PENDING_PAYMENT_ORDER_STATE.equals(po.getStatus()))
					.forEach(po -> {
						po.setStatus(OrderConstants.FAILED_ORDER_STATUS);
						po.setInventoryReleased(true);
					});
			proxyOrderRepository.saveAll(proxyOrders);
		} catch (Exception e) {
			LOGGER.error("Error in failing proxy order. Error : " + e);
		}
		return true;
	}

	private boolean CheckSplitOrder(ProxyOrder proxyOrder) {
		boolean isSplit = false;
		try {
			if (proxyOrder == null) {
				return false;
			}
			LOGGER.info("isSplitOrder Checking split order for proxy order id : " + proxyOrder.getQuoteId());
			if(null != proxyOrder.getSalesOrder()) {
				SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
				return Integer.valueOf(1).equals(salesOrder.getIsSplitOrder());
			}
		} catch (Exception e) {
			LOGGER.error("Error in checking split order. Error : " + e.getMessage(),e);
		}
		return isSplit;
	}

}

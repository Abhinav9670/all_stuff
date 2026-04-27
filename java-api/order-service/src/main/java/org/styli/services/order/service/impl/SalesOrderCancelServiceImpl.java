package org.styli.services.order.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.checkerframework.checker.units.qual.A;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.PaymentRefundHelper;
import org.styli.services.order.helper.RefundHelper;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.CancelDetails;
import org.styli.services.order.pojo.PayfortCaptureStatusCheckResponse;
import org.styli.services.order.pojo.PayfortConfiguration;
import org.styli.services.order.pojo.PayfortPaymentCaptureRequest;
import org.styli.services.order.pojo.PayfortPaymentCaptureStatusCheckRequest;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.SalesOrderPaymentInformation;
import org.styli.services.order.pojo.cancel.CancelOrderInitResponse;
import org.styli.services.order.pojo.cancel.CancelOrderInitResponseDTO;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.cancel.Reason;
import org.styli.services.order.pojo.cancel.SalesOrderCancelReason;
import org.styli.services.order.pojo.cancel.SalesOrderCancelReasonStore;
import org.styli.services.order.pojo.request.CouponValidationRequestV4Metadata;
import org.styli.services.order.pojo.response.CouponExternalRedemptionRequest;
import org.styli.services.order.pojo.response.CustomCouponCancelRedemptionResponse;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.increff.IncreffBackOrderPushRequest;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.SalesOrderCancelService;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Umesh, 14/05/2020
 * @project product-service
 */

@Component
public class SalesOrderCancelServiceImpl implements SalesOrderCancelService {

	private static final Log LOGGER = LogFactory.getLog(SalesOrderCancelServiceImpl.class);

	private static final String MISSING_MSG = "Parameters missing!";

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderCancelReasonRepository salesOrderCancelReasonRepository;

	@Autowired
	OrderShipmentHelper orderShipmentHelper;

	@Autowired
	RefundHelper refundHelper;

	@Value("${promo.coupon.promo.url}")
	private String couponRedeemUrl;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${kaleyra.url}")
	private String kaleyraUrl;

	@Value("${env}")
	private String env;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	OrderHelperV2 orderHelperV2;

	@Autowired
	PaymentRefundHelper paymentDtfRefundHelper;

	@Autowired
	PaymentUtility paymentUtility;

	@Autowired
	ProxyOrderRepository proxyOrderRepository;

	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	SplitSubSalesOrderRepository splitSubSalesOrderRepository;

	@Autowired
	SplitSalesOrderItemRepository splitSalesOrderItemRepository;

	@Autowired
	SellerBackOrderItemRepository sellerBackOrderItemRepository;

	@Autowired
	SellerBackOrderRepository sellerBackOrderRepository;

	@Autowired
	SellerConfigRepository sellerConfigRepository;

	private final org.styli.services.order.helper.OrderpushHelper orderpushHelper;

	@Autowired
	@Lazy
	EASServiceImpl eASServiceImpl;
	@Autowired
	CommonServiceImpl commonService;
	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	public SalesOrderCancelServiceImpl(@Lazy org.styli.services.order.helper.OrderpushHelper orderpushHelper) {
		this.orderpushHelper = orderpushHelper;
	}

	private String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}

	/**
	 * Checks if SubSalesOrder needs Shukran refund.
	 */
	private boolean needsShukranRefund(SubSalesOrder subSalesOrder) {
		return subSalesOrder != null 
			&& subSalesOrder.getTotalShukranCoinsBurned() != null 
			&& subSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 
			&& subSalesOrder.getShukranLocked() != null 
			&& subSalesOrder.getShukranLocked().equals(0);
	}

	/**
	 * Checks if SplitSubSalesOrder needs Shukran refund.
	 */
	private boolean needsShukranRefundForSplit(SplitSubSalesOrder splitSubSalesOrder) {
		return splitSubSalesOrder != null 
			&& splitSubSalesOrder.getTotalShukranCoinsBurned() != null 
			&& splitSubSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 
			&& splitSubSalesOrder.getShukranLocked() != null 
			&& splitSubSalesOrder.getShukranLocked().equals(0);
	}

	/**
	 * Checks if order status is pending_payment or payment_failed.
	 */
	private boolean isPendingPaymentOrFailed(String orderStatus) {
		if (orderStatus == null) {
			return false;
		}
		return OrderConstants.PENDING_PAYMENT_ORDER_STATUS.equalsIgnoreCase(orderStatus)
			|| OrderConstants.FAILED_ORDER_STATUS.equalsIgnoreCase(orderStatus);
	}

	/**
	 * Checks if any seller order has pending_payment or payment_failed status.
	 */
	private boolean hasPendingPaymentOrFailedSellerOrder(Collection<SplitSellerOrder> splitSellerOrders) {
		if (CollectionUtils.isEmpty(splitSellerOrders)) {
			return false;
		}
		return splitSellerOrders.stream()
			.anyMatch(sellerOrder -> sellerOrder != null 
				&& sellerOrder.getStatus() != null 
				&& isPendingPaymentOrFailed(sellerOrder.getStatus()));
	}

	/**
	 * Checks if cancellation should be allowed for pending_payment/payment_failed orders.
	 * For pending_payment/payment_failed orders, payment hasn't been completed, so packing restrictions don't apply.
	 * 
	 * @param orderStatus The status of the order
	 * @param splitSellerOrders Collection of seller orders to check (can be Set or List)
	 * @return true if cancellation should be allowed (order or any seller order has pending_payment/payment_failed status)
	 */
	private boolean shouldAllowCancellationForPendingPayment(String orderStatus, Collection<SplitSellerOrder> splitSellerOrders) {
		return isPendingPaymentOrFailed(orderStatus) || hasPendingPaymentOrFailedSellerOrder(splitSellerOrders);
	}

	/**
	 * Checks if order is a MADA transaction.
	 */
	private boolean isMadaTransaction(SalesOrder order) {
		if (order == null || order.getEntityId() == null) {
			return false;
		}
		int count = salesOrderRepository.checkIfMadaTransaction(order.getEntityId());
		return count > 0;
	}

	/**
	 * Checks if seller cancellation was done before.
	 */
	private boolean isSellerCancelDoneBefore(SalesOrder order) {
		if (order == null || order.getEntityId() == null) {
			return false;
		}
		int sellerCancelCount = salesOrderRepository.checkIfSellerCancelExists(order.getEntityId());
		return sellerCancelCount > 0;
	}

	/**
	 * Validates request and store, returns error response if invalid.
	 */
	private OrderResponseDTO validateRequestAndStore(CancelOrderRequest request, OrderResponseDTO resp, Stores store) {
		if (request.getOrderId() == null || request.getStoreId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg(MISSING_MSG);
			return resp;
		}

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store not found!");
			return resp;
		}

		return null;
	}

	/**
	 * Creates an error response.
	 */
	private OrderResponseDTO createErrorResponse(OrderResponseDTO resp, String statusCode, String statusMsg) {
		resp.setStatus(false);
		resp.setStatusCode(statusCode);
		resp.setStatusMsg(statusMsg);
		return resp;
	}

	/**
	 * Checks if split order is already closed or canceled.
	 */
	private boolean isSplitOrderAlreadyClosedOrCanceled(SplitSalesOrder order) {
		if (order.getStatus() == null) {
			return false;
		}
		return order.getStatus().equalsIgnoreCase(OrderConstants.CANCELED_ORDER_STATE)
			|| order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS);
	}

	/**
	 * Checks if any seller order has restricted status (packed, shipped, or delivered).
	 */
	private boolean hasRestrictedSellerOrderStatus(Collection<SplitSellerOrder> splitSellerOrders) {
		if (CollectionUtils.isEmpty(splitSellerOrders)) {
			return false;
		}
		return splitSellerOrders.stream()
			.anyMatch(sellerOrder -> sellerOrder != null && sellerOrder.getStatus() != null &&
				(sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.PACKED_ORDER_STATUS) ||
					sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.SHIPPED_ORDER_STATUS) ||
					sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.DELIVERED_ORDER_STATUS)||
					sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.DELIVERED_ORDER_STATUS))|| 
					sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.INWARD_MIDMILE_ORDER_STATUS) ||
				   sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.OUTWARD_MIDMILE_ORDER_STATUS));
	}

	/**
	 * Checks if order is already closed or canceled.
	 */
	private boolean isOrderClosedOrCanceled(SalesOrder order) {
		if (order == null || order.getStatus() == null) {
			return false;
		}
		return order.getStatus().equalsIgnoreCase(OrderConstants.CANCELED_ORDER_STATE)
			|| order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS);
	}

	/**
	 * Checks if order has shipments.
	 */
	private boolean hasOrderShipments(SalesOrder order) {
		return CollectionUtils.isNotEmpty(order.getSalesShipments());
	}

	/**
	 * Checks if cancellation is not allowed for the order.
	 */
	private boolean isCancellationNotAllowed(SalesOrder order) {
		return order.getIsCancelAllowed() != null && !order.getIsCancelAllowed();
	}

	/**
	 * Processes Payfort capture and refund for order cancellation.
	 * @return array with [0] = response, [1] = amountToCaptureAndRefund (or null if not calculated)
	 */
	private Object[] processPayfortCaptureAndRefund(SalesOrder order, PayfortConfiguration configuration,
			String fortId, String paymentMethod) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		BigDecimal amountToCaptureAndRefund = null;
		PayfortPaymentCaptureStatusCheckRequest statusCheckRequest = paymentUtility
				.preparePayfortPaymentCaptureStatusCheckRequest(configuration, order);
		PayfortCaptureStatusCheckResponse statusCheckResponse = paymentUtility
				.callPayfortStatusCheckApi(statusCheckRequest);

		if (statusCheckResponse != null && statusCheckResponse.getRefundedAmount() != null
				&& statusCheckResponse.getAuthorizedAmount() != null) {
			String authorizedAmountStr = statusCheckResponse.getAuthorizedAmount();
			String refundedAmountStr = statusCheckResponse.getRefundedAmount();

			BigDecimal authorizedAmount = new BigDecimal(authorizedAmountStr);
			BigDecimal refundedAmount = new BigDecimal(refundedAmountStr);

			amountToCaptureAndRefund = authorizedAmount.subtract(refundedAmount);

			LOGGER.info("processPreInvoiceOrder — Remaining Amount to Capture & Refund: {}"
					+ amountToCaptureAndRefund);

			LOGGER.info("processPreInvoiceOrder — Preparing capture request for Order {}" + order.getIncrementId());
			configuration.setMultiplier(1);
			PayfortPaymentCaptureRequest captureRequest = paymentUtility.preparePayfortCaptureRequest(
					configuration, order, amountToCaptureAndRefund, fortId);

			LOGGER.info("processPreInvoiceOrder — Sending capture request to Payfort for Order {}"
					+ order.getIncrementId());
			PayfortReposne captureResponse = paymentUtility
					.triggerPayfortPaymentCaptureRestApiCall(captureRequest, order, configuration);

			if (captureResponse != null && captureResponse.isStatus()) {
				LOGGER.info(
						"processPreInvoiceOrder — Payfort capture successful for Order {}. Proceeding with refund."
								+ order.getIncrementId());
				BigDecimal refundamount = amountToCaptureAndRefund.divide(new BigDecimal("100"));
				response = paymentDtfRefundHelper.payfortRefundcall(order, refundamount, fortId, paymentMethod);
			} else {
				LOGGER.error("processPreInvoiceOrder — Payfort capture failed for Order {}. Skipping refund."
						+ order.getIncrementId());
			}
		} else {
			LOGGER.info(
					"processPreInvoiceOrder — Payfort capture status response is incomplete or null for Order {}"
							+ order.getIncrementId());
		}
		response.setStatus(true);
		response.setStatusCode("200");
		return new Object[] { response, amountToCaptureAndRefund };
	}

	@Override
	@Transactional
	public OrderResponseDTO cancelOrder(CancelOrderRequest request) {
		OrderResponseDTO resp = new OrderResponseDTO();
		SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(),
				request.getCustomerId());
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);
		OrderResponseDTO validatResponse = validateCancelOrder(request, resp, order, store);
		if (null != validatResponse.getStatus() && !validatResponse.getStatus()) {
			return validatResponse;
		}
		
		if(order!=null && needsShukranRefund(order.getSubSalesOrder())) {
			SubSalesOrder subSalesOrder= order.getSubSalesOrder();
			commonService.lockUnlockShukranData(subSalesOrder.getCustomerProfileId(), 
				subSalesOrder.getTotalShukranCoinsBurned().toString(), 
				subSalesOrder.getQuoteId(), false, order, store, 
				OrderConstants.SHUKRAN_REFUND_REASON_CANCEL_ORDER, "");
			subSalesOrder.setShukranLocked(1);
			subSalesOrderRepository.saveAndFlush(subSalesOrder);
		}
		
		if (null != order && null != store) {
			RefundPaymentRespone paymentResponse = processPreInvoiceOrder(request, order, store);
			if (!paymentResponse.isStatus()) {
				resp.setStatus(false);
				resp.setStatusCode(paymentResponse.getStatusCode());
				resp.setStatusMsg("something went wrong !!");
				return resp;
			}
			resp.setRefund(paymentResponse.isRefund());
		}
		
		if (null != order && null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())) {
			cancelReedmeExternalCoupon(store, order, false, false);
		}
		orderHelper.releaseInventoryQty(order, new HashMap<>(), false, OrderConstants.RELEASE_CUSTOMER_CANCELLATION);
		// EAS_CHANGES on cancel order push to kafka.If Earn Service flag ON!.
		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafka(order, 0.0);
		}
		
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Successfully Cancelled the order!");
		return resp;
	}

	@Override
	@Transactional
	public OrderResponseDTO cancelOrderV2(CancelOrderRequest request) {
		OrderResponseDTO resp = new OrderResponseDTO();
		List<SplitSalesOrder> splitSalesOrderList = splitSalesOrderRepository.findByOrderId(request.getOrderId());
		SplitSalesOrder order = splitSalesOrderList.stream().filter(e -> e.getEntityId().equals(request.getSplitOrderId())).findAny().orElse(null);
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);
		OrderResponseDTO validatResponse = validateCancelOrderForSplit(request, resp, order, store);
		if (null != validatResponse.getStatus() && !validatResponse.getStatus()) {
			return validatResponse;
		}

		SalesOrder parentOrder = order != null ? order.getSalesOrder() : null;
		if (parentOrder == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Parent order not found!");
			return resp;
		}
		
		List<SplitSalesOrder> splitSalesOrders = parentOrder.getSplitSalesOrders() != null 
			? parentOrder.getSplitSalesOrders().stream().toList() 
			: new ArrayList<>();
		boolean shouldParentOrderClose = splitSalesOrders.stream()
			.filter(e -> e != null && e.getStatus() != null && OrderConstants.CLOSED_ORDER_STATUS.equals(e.getStatus()))
			.count() == splitSalesOrders.size() - 1;

		// Calculate total burned points from ALL shipments in the order
		BigDecimal totalOrderBurnedPoints = BigDecimal.ZERO;
		boolean isAllShipmentsCancelled = shouldParentOrderClose;
		
		if (parentOrder != null) {
			List<SplitSalesOrder> allSplitOrders = splitSalesOrderRepository.findBySalesOrder(parentOrder);
			if (CollectionUtils.isNotEmpty(allSplitOrders)) {
				for (SplitSalesOrder splitOrder : allSplitOrders) {
					if (splitOrder.getSplitSubSalesOrder() != null 
							&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null
							&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
						totalOrderBurnedPoints = totalOrderBurnedPoints.add(splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
						LOGGER.info("cancelOrderV2: Found burned points in shipment - splitOrderId: " + splitOrder.getEntityId() + 
								", incrementId: " + splitOrder.getIncrementId() + 
								", burnedPoints: " + splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
					}
				}
			}
			LOGGER.info("cancelOrderV2: Total burned points from ALL shipments in order: " + totalOrderBurnedPoints + ", isAllShipmentsCancelled: " + isAllShipmentsCancelled);
		}	


		// Unlock ALL points from the entire order (all shipments)
		if (order != null && order.getSplitSubSalesOrder() != null && totalOrderBurnedPoints.compareTo(BigDecimal.ZERO) > 0) {
			// Check if points are currently locked
			if (order.getSplitSubSalesOrder().getShukranLocked() != null && order.getSplitSubSalesOrder().getShukranLocked().equals(0)) {
				LOGGER.info("cancelOrderV2: Unlocking ALL burned points from entire order - Total: " + totalOrderBurnedPoints);
				commonService.lockUnlockShukranDataForSplit(order.getSplitSubSalesOrder().getCustomerProfileId(), totalOrderBurnedPoints.toString(), order.getSplitSubSalesOrder().getQuoteId(), false, order, store, "Refund Shukran Burned Points On Cancel Order", "");
				SplitSubSalesOrder subSalesOrder = order.getSplitSubSalesOrder();
				subSalesOrder.setShukranLocked(1);
				splitSubSalesOrderRepository.saveAndFlush(subSalesOrder);
				LOGGER.info("cancelOrderV2: Successfully unlocked all " + totalOrderBurnedPoints + " points from entire order and set ShukranLocked=1");
			} else {
				LOGGER.info("cancelOrderV2: Points are already unlocked (ShukranLocked != 0) or no burned points. ShukranLocked: " + order.getSplitSubSalesOrder().getShukranLocked());
			}
		}

		// Also unlock parent order points if all shipments are closed (for backward compatibility)
		if(shouldParentOrderClose) {
			if (parentOrder != null && parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned() != null && parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && parentOrder.getSubSalesOrder().getShukranLocked() != null && parentOrder.getSubSalesOrder().getShukranLocked().equals(0)) {
				LOGGER.info("cancelOrderV2: All shipments closed - Also unlocking parent order points: " + parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned());
				commonService.lockUnlockShukranDataForSplit(parentOrder.getSubSalesOrder().getCustomerProfileId(), parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), parentOrder.getSubSalesOrder().getQuoteId(), false, order, store, "Refund Shukran Burned Points On Cancel Order", "");
				SubSalesOrder subSalesOrder = parentOrder.getSubSalesOrder();
				subSalesOrder.setShukranLocked(1);
				subSalesOrderRepository.saveAndFlush(subSalesOrder);
			}
		}

		if (null != order && null != store) {

			RefundPaymentRespone paymentResponse = processPreInvoiceOrderForSplit(request, order, store, shouldParentOrderClose, parentOrder);

			if (!paymentResponse.isStatus()) {

				resp.setStatus(false);
				resp.setStatusCode(paymentResponse.getStatusCode());
				resp.setStatusMsg("something went wrong !!");
				return resp;
			}
			resp.setRefund(paymentResponse.isRefund());
		}
		if (null != order && null != order.getSplitSubSalesOrder()
				&& null != order.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(order.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId())) {
			cancelReedmeExternalCouponForSplitOrder(store, order, false, false);
		}

		orderHelper.releaseInventoryQtyForSplitOrder(order, new HashMap<>(), false, OrderConstants.RELEASE_CUSTOMER_CANCELLATION);

		// EAS_CHANGES on cancel order push to kafka.If Earn Service flag ON!.
		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafkaForSplitOrder(order, 0.0);
		}
		
		// Lock back burned points from other shipments for non-cancelled items only
		// This should run even if current shipment is fully cancelled, as long as other shipments exist
		if (parentOrder != null && store != null && !isAllShipmentsCancelled) {
			try {
				LOGGER.info("cancelOrderV2: Checking for other shipments in split order. Current splitOrderId: " + order.getEntityId() + ", incrementId: " + order.getIncrementId() + ", Parent orderId: " + parentOrder.getEntityId());
				List<SplitSalesOrder> allSplitOrders = splitSalesOrderRepository.findBySalesOrder(parentOrder);
				
				if (CollectionUtils.isNotEmpty(allSplitOrders)) {
					BigDecimal otherShipmentsBurnedPoints = BigDecimal.ZERO;
					
					for (SplitSalesOrder otherSplitOrder : allSplitOrders) {
						// Skip the current split order being cancelled
						if (otherSplitOrder.getEntityId().equals(order.getEntityId())) {
							LOGGER.info("cancelOrderV2: Skipping current shipment being cancelled (splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ")");
							continue;
						}
						
						LOGGER.info("cancelOrderV2: Checking other shipment - splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ", status: " + otherSplitOrder.getStatus());
						
						// Fetch all items for this other shipment
						List<SplitSalesOrderItem> otherShipmentItems = splitSalesOrderItemRepository.findBySplitSalesOrderEntityId(otherSplitOrder.getEntityId());
						
						if (CollectionUtils.isNotEmpty(otherShipmentItems)) {
							BigDecimal shipmentBurnedPoints = BigDecimal.ZERO;
							
							for (SplitSalesOrderItem item : otherShipmentItems) {
								// Skip configurable products (parent items)
								if (OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(item.getProductType())) {
									continue;
								}
								
								// Check if this item has any non-cancelled quantity
								BigDecimal qtyOrdered = item.getQtyOrdered() != null ? item.getQtyOrdered() : BigDecimal.ZERO;
								BigDecimal qtyCancelled = item.getQtyCanceled() != null ? item.getQtyCanceled() : BigDecimal.ZERO;
								BigDecimal qtyNotCancelled = qtyOrdered.subtract(qtyCancelled);
								
								// If item has non-cancelled quantity and has burned points, calculate proportional points
								if (qtyNotCancelled.compareTo(BigDecimal.ZERO) > 0 && item.getShukranCoinsBurned() != null && item.getShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
									// Calculate proportional points for non-cancelled quantity from shukran_coins_burned
									BigDecimal itemPointsForNonCancelled;
									if (qtyOrdered.compareTo(BigDecimal.ZERO) > 0) {
										// Proportional calculation: (shukran_coins_burned / qty_ordered) * qty_not_cancelled
										itemPointsForNonCancelled = item.getShukranCoinsBurned()
												.divide(qtyOrdered, 6, RoundingMode.HALF_UP)
												.multiply(qtyNotCancelled)
												.setScale(2, RoundingMode.HALF_UP);
									} else {
										// If qtyOrdered is 0, use full burned points (edge case)
										itemPointsForNonCancelled = item.getShukranCoinsBurned();
									}
									
									shipmentBurnedPoints = shipmentBurnedPoints.add(itemPointsForNonCancelled);
									
									LOGGER.info("cancelOrderV2: Found non-cancelled item in other shipment - SKU: " + item.getSku() + 
											", ItemId: " + item.getItemId() +
											", QtyOrdered: " + qtyOrdered + 
											", QtyCancelled: " + qtyCancelled + 
											", QtyNotCancelled: " + qtyNotCancelled + 
											", ShukranCoinsBurned (from DB): " + item.getShukranCoinsBurned() + 
											", PointsForNonCancelledQty: " + itemPointsForNonCancelled);
								} else {
									LOGGER.info("cancelOrderV2: Skipping item (fully cancelled or no burned points) - SKU: " + item.getSku() + 
											", ItemId: " + item.getItemId() +
											", QtyOrdered: " + qtyOrdered + 
											", QtyCancelled: " + qtyCancelled + 
											", ShukranCoinsBurned: " + item.getShukranCoinsBurned());
								}
							}
							
							if (shipmentBurnedPoints.compareTo(BigDecimal.ZERO) > 0) {
								otherShipmentsBurnedPoints = otherShipmentsBurnedPoints.add(shipmentBurnedPoints);
								LOGGER.info("cancelOrderV2: Accumulated points from shipment (splitOrderId: " + otherSplitOrder.getEntityId() + 
										", incrementId: " + otherSplitOrder.getIncrementId() + "): " + shipmentBurnedPoints);
							} else {
								LOGGER.info("cancelOrderV2: No non-cancelled items with burned points in shipment (splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ")");
							}
						} else {
							LOGGER.info("cancelOrderV2: No items found for other shipment (splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ")");
						}
					}
					
					// Lock back points from other shipments if any
					if (otherShipmentsBurnedPoints.compareTo(BigDecimal.ZERO) > 0) {
						LOGGER.info("cancelOrderV2: Locking back burned points from non-cancelled items in other shipments. Total points: " + otherShipmentsBurnedPoints);
						commonService.lockUnlockShukranDataForSplit(
								order.getSplitSubSalesOrder().getCustomerProfileId(), 
								otherShipmentsBurnedPoints.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString(), 
								order.getSplitSubSalesOrder().getQuoteId(), 
								true, // true = LOCK
								order, 
								store, 
								"Shukran Burned Points On Other Shipments (Not Cancelled)", 
								"Customer Cancelation Api - Split Order"
						);
						LOGGER.info("cancelOrderV2: Successfully locked back " + otherShipmentsBurnedPoints + " points from non-cancelled items in other shipments");
					} else {
						LOGGER.info("cancelOrderV2: No burned points found in non-cancelled items from other shipments to lock back");
					}
				} else {
					LOGGER.info("cancelOrderV2: No split orders found for parent orderId: " + parentOrder.getEntityId());
				}
			} catch (Exception e) {
				LOGGER.error("cancelOrderV2: Error while locking back points from other shipments in split order. SplitOrderId: " + order.getEntityId() + ". Error: " + e.getMessage(), e);
				// Don't throw exception - continue with cancellation even if this fails
			}
		} else if (isAllShipmentsCancelled) {
			LOGGER.info("cancelOrderV2: All shipments cancelled - All Shukran points remain unlocked (not locking back)");
		}
		
		// Call OMS service API for split order cancellation
		callOmsServiceForSplitOrderCancel(order);
		
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Successfully Cancelled the order!");
		return resp;
	}

	/**
	 * Calls OMS service API to handle split order cancellation
	 * @param order SplitSalesOrder that was cancelled
	 */
	private void callOmsServiceForSplitOrderCancel(SplitSalesOrder order) {
		try {
			// Check if OMS service URL is configured
			if (Constants.orderCredentials == null || 
				Constants.orderCredentials.getOrderDetails() == null ||
				StringUtils.isBlank(Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl())) {
				LOGGER.warn("OMS Service Base URL not configured. Skipping OMS service call for split order cancellation.");
				return;
			}

			String url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + "/v1/rest/handle-split-order-cancel";
			
			// Build request payload - only incrementId
			Map<String, Object> payload = new HashMap<>();
			if (order != null && order.getIncrementId() != null) {
				payload.put("incrementId", order.getIncrementId());
			} else {
				LOGGER.warn("Split order incrementId is null. Skipping OMS service call.");
				return;
			}

			// Set up HTTP headers
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);

			HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, requestHeaders);

			LOGGER.info("Calling OMS service for split order cancellation. URL: " + url);
			LOGGER.info("OMS service request body: " + mapper.writeValueAsString(payload));

			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Object.class);

			LOGGER.info("OMS service response status: " + response.getStatusCode());
			LOGGER.info("OMS service response body: " + mapper.writeValueAsString(response.getBody()));

			if (response.getStatusCode() == HttpStatus.OK) {
				LOGGER.info("Successfully notified OMS service about split order cancellation for incrementId: " + order.getIncrementId());
			} else {
				LOGGER.warn("OMS service returned non-OK status: " + response.getStatusCode() + " for split order cancellation");
			}

		} catch (RestClientException e) {
			LOGGER.error("Error calling OMS service for split order cancellation. IncrementId: " + (order != null ? order.getIncrementId() : "null") + ". Error: " + e.getMessage(), e);
			// Don't throw exception - cancellation should succeed even if OMS call fails
		} catch (JsonProcessingException e) {
			LOGGER.error("Error serializing request payload for OMS service call. IncrementId: " + (order != null ? order.getIncrementId() : "null") + ". Error: " + e.getMessage(), e);
			// Don't throw exception - cancellation should succeed even if OMS call fails
		} catch (Exception e) {
			LOGGER.error("Unexpected error calling OMS service for split order cancellation. IncrementId: " + (order != null ? order.getIncrementId() : "null") + ". Error: " + e.getMessage(), e);
			// Don't throw exception - cancellation should succeed even if OMS call fails
		}
	}

	/**
	 * @param request
	 * @param resp
	 * @return
	 */
	private OrderResponseDTO validateCancelOrder(CancelOrderRequest request, OrderResponseDTO resp, SalesOrder order,
			Stores store) {
		OrderResponseDTO validationResult = validateRequestAndStore(request, resp, store);
		if (validationResult != null) {
			return validationResult;
		}

		if (order == null) {
			return createErrorResponse(resp, "202", "Order not found!");
		}

		if (isOrderClosedOrCanceled(order)) {
			return createErrorResponse(resp, "203", "Order status is not correct to close !");
		}

		if (hasOrderShipments(order)) {
			return createErrorResponse(resp, "203", "Order has shipments!");
		}

		// Allow cancellation for pending_payment and payment_failed orders regardless of seller order status or isCancelAllowed flag
		boolean shouldAllowCancellation = shouldAllowCancellationForPendingPayment(order.getStatus(), order.getSplitSellerOrders());
		if (shouldAllowCancellation) {
			return resp;
		}
		// Check if any split_seller_order has status packed, shipped, or delivered
		if (hasRestrictedSellerOrderStatus(order.getSplitSellerOrders())) {
			return createErrorResponse(resp, "203", OrderConstants.PACKING_STARTED_CANCELLATION_UNAVAILABLE_MSG);
		}

		// Check if cancellation is allowed
		if (isCancellationNotAllowed(order)) {
			return createErrorResponse(resp, "203", OrderConstants.PACKING_STARTED_CANCELLATION_UNAVAILABLE_MSG);
		}
		return resp;
	}

/**
	 * @param request
	 * @param resp
	 * @return
	 */
	private OrderResponseDTO validateCancelOrderForSplit(CancelOrderRequest request, OrderResponseDTO resp, SplitSalesOrder order,
			Stores store) {
		OrderResponseDTO validationResult = validateRequestAndStore(request, resp, store);
		if (validationResult != null) {
			return validationResult;
		}

		if (order == null) {
			return createErrorResponse(resp, "202", "Order not found!");
		}

		if (isSplitOrderAlreadyClosedOrCanceled(order)) {
			return createErrorResponse(resp, "203", "Order status is not correct to close !");
		}

		if (CollectionUtils.isNotEmpty(order.getSplitSalesShipments())) {
			return createErrorResponse(resp, "203", "Order has shipments!");
		}

		// Allow cancellation for pending_payment and payment_failed orders regardless of seller order status or isCancelAllowed flag
		boolean shouldAllowCancellation = shouldAllowCancellationForPendingPayment(order.getStatus(), order.getSplitSellerOrders());
		if (shouldAllowCancellation) {
			return resp;
		}
		// Check if any split_seller_order has status packed, shipped, or delivered
		if (hasRestrictedSellerOrderStatus(order.getSplitSellerOrders())) {
			return createErrorResponse(resp, "203", OrderConstants.PACKING_STARTED_CANCELLATION_UNAVAILABLE_MSG);
		}

		// Check if cancellation is allowed
		if (order.getIsCancelAllowed() != null && !order.getIsCancelAllowed()) {
			return createErrorResponse(resp, "203", OrderConstants.PACKING_STARTED_CANCELLATION_UNAVAILABLE_MSG);
		}
		return resp;
	}


	@Override
	@Transactional(readOnly = true)
	public CancelOrderInitResponseDTO cancelOrderInit(CancelOrderRequest request) {

		CancelOrderInitResponseDTO resp = new CancelOrderInitResponseDTO();

		if (request.getCustomerId() == null || request.getOrderId() == null || request.getStoreId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg(MISSING_MSG);
		}

		CancelOrderInitResponse cancelOrderInitResponse = new CancelOrderInitResponse();
		List<Reason> reasons = new ArrayList<>();

		List<SalesOrderCancelReason> cancelReasons = salesOrderCancelReasonRepository
				.findByStatusOrderBySortOrderAsc(1);
		if (CollectionUtils.isNotEmpty(cancelReasons)) {
			checkReason(request, reasons, cancelReasons);
		}

		cancelOrderInitResponse.setReasons(reasons);
		resp.setResponse(cancelOrderInitResponse);
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Success!");

		return resp;
	}

	private void checkReason(CancelOrderRequest request, List<Reason> reasons,
			List<SalesOrderCancelReason> cancelReasons) {
		for (SalesOrderCancelReason salesOrderCancelReason : cancelReasons) {
			String reasonTitle = salesOrderCancelReason.getTitle();

			for (SalesOrderCancelReasonStore salesOrderCancelReasonStore : salesOrderCancelReason
					.getSalesOrderCancelReasonStores()) {
				if (salesOrderCancelReasonStore.getStoreId().equals(request.getStoreId())
						&& salesOrderCancelReasonStore.getLabel() != null
						&& !salesOrderCancelReasonStore.getLabel().isEmpty()) {
					reasonTitle = salesOrderCancelReasonStore.getLabel();
				}
			}
			if (salesOrderCancelReason.getReasonId() != null) {
				Reason reason = new Reason(salesOrderCancelReason.getReasonId().toString(), reasonTitle);
				reasons.add(reason);
			}
		}
	}

	private RefundPaymentRespone processPreInvoiceOrder(CancelOrderRequest request, SalesOrder order, Stores store) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		BigDecimal storeCreditAmount = order.getAmstorecreditAmount();
		BigDecimal totalPaid = null;
		Map<String, BigDecimal> skumapList = new HashMap<>();
		List<SalesOrderItem> salesItemList = new ArrayList<>();
		String payfortErronMessage = null;

		String fortId = null;
		String paymentMethod = null;
		SalesOrderGrid grid = null;
		String paymentInformation = null;
		String paymentStatus = null;
		boolean isRefund = false;
		if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
			for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
				paymentMethod = payment.getMethod();
				fortId = payment.getCcTransId();
				paymentInformation = payment.getAdditionalInformation();
			}
		}
		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInformation,
						SalesOrderPaymentInformation.class);
				if (OrderConstants.checkPaymentMethod(paymentMethod)) {
					paymentStatus = parseNullStr(salesOrderPaymentInformation.getResponseCode());
				} else {
					paymentStatus = parseNullStr(salesOrderPaymentInformation.getResponseMessage());
				}
			} catch (IOException e) {
				LOGGER.error("Error in get sales Order Payment Information for " + order.getIncrementId() + ": ", e);
			}
		}
		if (OrderConstants.PAYMENT_SUCCESS_CODES.contains(paymentStatus)) {
			isRefund = true;
		}
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		//Find import fee of order based on available items of order
		BigDecimal currentOrderValue = paymentDtfRefundHelper.findCurrentOrderValue(order);
		CancelDetails details = new CancelDetails();
		details.setCurrentOrderValue(currentOrderValue);
		BigDecimal getProductCanceledAmount = paymentDtfRefundHelper.getCancelAmount(order, skumapList, salesItemList);

		BigDecimal totalVoucherToRefund = BigDecimal.ZERO;
		BigDecimal calculatedOnlineAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order,
				getProductCanceledAmount, storeCreditAmount, details, true, paymentMethod, totalVoucherToRefund);
		BigDecimal amountToCaptureAndRefundForOrderCancellation = BigDecimal.ZERO;

		if (OrderConstants.checkPaymentMethod(paymentMethod)) {

			PayfortConfiguration configuration = new PayfortConfiguration();
			paymentDtfRefundHelper.getPayfortConfDetails(order.getStoreId().toString(), paymentMethod, configuration);
			try {
				LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
			} catch (JsonProcessingException e) {
				LOGGER.error("error during write configuration:" + e.getMessage());
			}

			// flag payfortRefundOnSellerCancellation to check consul tryue or false:
			boolean payfortRefundOnSellerCancellation = Constants.orderCredentials.getPayfort()
					.isPayfortRefundOnSellerCancellation();
			boolean isMadaTransaction = isMadaTransaction(order);
			boolean isSellerCancelDoneBefore = isSellerCancelDoneBefore(order);

			LOGGER.info("processPreInvoiceOrder — Order " + order.getIncrementId() + " consul flag is : "
					+ payfortRefundOnSellerCancellation);
			LOGGER.info("processPreInvoiceOrder — Order " + order.getIncrementId() + " is MADA transaction: "
					+ isMadaTransaction);
			LOGGER.info("processPreInvoiceOrder — Order " + order.getIncrementId()
					+ " has previous seller cancellations: " + isSellerCancelDoneBefore);

			if (payfortRefundOnSellerCancellation && isMadaTransaction && isSellerCancelDoneBefore) {
				LOGGER.info(
						"processPreInvoiceOrder — Order {} meets all conditions: Payfort Refund on Seller Cancellation is enabled, is a MADA transaction, and has previous seller cancellations."
								+ order.getIncrementId());

				LOGGER.info("processPreInvoiceOrder — Initiating Payfort Capture Status Check for Order {}"
						+ order.getIncrementId());
				PayfortPaymentCaptureStatusCheckRequest statusCheckRequest = paymentUtility
						.preparePayfortPaymentCaptureStatusCheckRequest(configuration, order);
				PayfortCaptureStatusCheckResponse statusCheckResponse = paymentUtility
						.callPayfortStatusCheckApi(statusCheckRequest);

				if (statusCheckResponse != null && statusCheckResponse.getRefundedAmount() != null
						&& statusCheckResponse.getAuthorizedAmount() != null) {
					String authorizedAmountStr = statusCheckResponse.getAuthorizedAmount();
					String refundedAmountStr = statusCheckResponse.getRefundedAmount();

					BigDecimal authorizedAmount = new BigDecimal(authorizedAmountStr);
					BigDecimal refundedAmount = new BigDecimal(refundedAmountStr);

					amountToCaptureAndRefundForOrderCancellation = authorizedAmount.subtract(refundedAmount);

					LOGGER.info("processPreInvoiceOrder — Remaining Amount to Capture & Refund: {}"
							+ amountToCaptureAndRefundForOrderCancellation);

					LOGGER.info("processPreInvoiceOrder — Preparing capture request for Order {}" + order.getIncrementId());
					configuration.setMultiplier(1);
					PayfortPaymentCaptureRequest captureRequest = paymentUtility.preparePayfortCaptureRequest(
							configuration, order, amountToCaptureAndRefundForOrderCancellation, fortId);

					LOGGER.info("processPreInvoiceOrder — Sending capture request to Payfort for Order {}"
							+ order.getIncrementId());
					PayfortReposne captureResponse = paymentUtility
							.triggerPayfortPaymentCaptureRestApiCall(captureRequest, order, configuration);

					if (captureResponse != null && captureResponse.isStatus()) {
						LOGGER.info(
								"processPreInvoiceOrder — Payfort capture successful for Order {}. Proceeding with refund."
										+ order.getIncrementId());
						BigDecimal refundamount = amountToCaptureAndRefundForOrderCancellation.divide(new BigDecimal("100"));
						response = paymentDtfRefundHelper.payfortRefundcall(order,
								refundamount, fortId, paymentMethod);
					} else {
						LOGGER.error("processPreInvoiceOrder — Payfort capture failed for Order {}. Skipping refund."
								+ order.getIncrementId());
					}
				} else {
					LOGGER.info(
							"processPreInvoiceOrder — Payfort capture status response is incomplete or null for Order {}"
									+ order.getIncrementId());
				}
				calculatedOnlineAmount = amountToCaptureAndRefundForOrderCancellation;
				response.setStatus(true);
				response.setStatusCode("200");

			} else if (null != order.getPayfortAuthorized() && order.getPayfortAuthorized() == 1) {
				response = paymentDtfRefundHelper.payfortVoidAuthorizationcall(order, fortId, paymentMethod);

			} else {
				response = paymentDtfRefundHelper.payfortRefundcall(order, calculatedOnlineAmount, fortId,
						paymentMethod);
			}
			if (response != null && !"200".equals(response.getStatusCode())) {
				payfortErronMessage = response.getStatusMsg();
			}
			grid = storeCreditAndMemo(order, store, skumapList, salesItemList, payfortErronMessage, paymentMethod,
					details, calculatedOnlineAmount, isRefund);
		} else if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)) {

		// Check if SubSalesOrder exists before accessing paymentId (may be null for pending_payment/payment_failed orders)
		if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getPaymentId() != null) {
			ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(order.getSubSalesOrder().getPaymentId());

			if (OrderConstants.checkTabbyPaymentMethod(paymentMethod) && Objects.isNull(proxyOrder)) {
				paymentUtility.initiateRefund(order, calculatedOnlineAmount.toString(), paymentMethod);

			} else {
				RefundPaymentRespone closeRes = paymentUtility.initiateClose(order, calculatedOnlineAmount.toString(),
						paymentMethod);
				if (closeRes.isStatus()) {
					orderHelperV2.updateProxyOrderStatusByPaymentId(order.getSubSalesOrder().getPaymentId(),
							OrderConstants.CLOSED_ORDER_STATUS);
				} else {
					payfortErronMessage = closeRes.getStatusMsg();
				}

				}
			}
			grid = storeCreditAndMemo(order, store, skumapList, salesItemList, payfortErronMessage, paymentMethod,
					details, calculatedOnlineAmount, isRefund);
		} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
			paymentUtility.initiateRefund(order, calculatedOnlineAmount.toString(), paymentMethod);
			if (null != order.getGiftVoucherDiscount()
					&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {
				paymentDtfRefundHelper.addStoreCredit(order, order.getGiftVoucherDiscount(), true);
				paymentDtfRefundHelper.setReturnVoucherValueInDB(order, order.getGiftVoucherDiscount());
			}
		} else {
			grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
			if (details.getAmasyStoreCredit() != null && order.getCustomerId() != null
					&& details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) >= 0) {

				BigDecimal baseAmastStoreCredit = details.getAmasyStoreCredit().multiply(order.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP);
				refundHelper.releaseStoreCredit(order, baseAmastStoreCredit);
				// if (isRefund) {
				// String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
				// SalesCreditmemo memo = refundHelper.createCreditMemo(order, memoIncrementId,
				// store,
				// calculatedOnlineAmount, details.getAmasyStoreCredit(), paymentMethod,
				// skumapList, true,
				// new HashMap<>());
				// refundHelper.createCancelCreditmemoItems(order, memo, skumapList,
				// salesItemList);
				// refundHelper.createCreditmemoComment(memo, details.getAmasyStoreCredit());
				// refundHelper.createCreditmemoGrid(order, memo, memoIncrementId, grid,
				// order.getBaseGrandTotal());
				// }
			}
		}

		if (grid != null) {
			grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			salesOrderGridRepository.saveAndFlush(grid);
		}

		order = refundHelper.cancelOrderObject(request, order, false, paymentMethod);
		LOGGER.info("wms status during cancellation" + order.getIncrementId() + " status: " + order.getWmsStatus());
		if (null != order.getWmsStatus()) {
			order.setWmsStatus(2);
		}

		// also do it for parent order items
		order = refundHelper.cancelOrderItems(order, false);
		if (null != order.getAmstorecreditAmount()) {

			totalPaid = order.getGrandTotal().add(order.getAmstorecreditAmount());
		} else {
			totalPaid = order.getGrandTotal();
		}
		String message = OrderConstants.CANCELLED_MSG_CUSTOMER;
		order = refundHelper.cancelStatusHistory(order, false, totalPaid, message);
		order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
		order.setRetryPayment(0);
		// Check if SubSalesOrder exists before accessing (may be null for pending_payment/payment_failed orders)
		if (order.getSubSalesOrder() != null) {
		order.getSubSalesOrder().setRetryPayment(0);
		order.getSubSalesOrder().setOrderExpiredAt(null);
		order.getSubSalesOrder().setFirstNotificationAt(null);
		order.getSubSalesOrder().setSecondNotificationAt(null);
	}
		salesOrderRepository.saveAndFlush(order);

		if(order.getSplitSellerOrders() != null) {
			// Create a copy to avoid ConcurrentModificationException
			List<SplitSellerOrder> sellerOrdersCopy = new ArrayList<>(order.getSplitSellerOrders());
			for (SplitSellerOrder sellerOrder : sellerOrdersCopy) {
				orderHelper.cancelSellerOrderAndItems(sellerOrder, OrderConstants.CANCELLED_BY_CUSTOMER, OrderConstants.WMS_STATUS_PUSH_TO_WMS, new HashMap<String, BigDecimal>());
			}
		}

		if(CollectionUtils.isNotEmpty(order.getSplitSalesOrders())) {
			// Create a copy to avoid ConcurrentModificationException
			List<SplitSalesOrder> splitSalesOrdersCopy = new ArrayList<>(order.getSplitSalesOrders());
			for (SplitSalesOrder splitSalesOrder : splitSalesOrdersCopy) {
				if (splitSalesOrder.getSplitSalesOrderPayments() != null && !splitSalesOrder.getSplitSalesOrderPayments().isEmpty()) {
				String paymentMethodForSplitOrder = splitSalesOrder.getSplitSalesOrderPayments().stream().findFirst().get().getMethod();
				
				refundHelper.cancelOrderObjectForSplitOrder(request, splitSalesOrder, false, paymentMethodForSplitOrder);
				refundHelper.cancelOrderItemsForSplitOrder(splitSalesOrder, false);
				
				splitSalesOrderRepository.saveAndFlush(splitSalesOrder);
			}
			}
		}

		orderHelper.updateStatusHistory(order, false, false, false, true, false);
		
		// do it only when parent order is closed
		orderHelper.buildOTSPayloadAndPublishToPubSubForMainOrder(order, "Order Closed", "4.0");
	// Check if SubSalesOrder exists before accessing paymentId (may be null for pending_payment/payment_failed orders)
	if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getPaymentId() != null) {
		orderHelperV2.updateProxyOrderStatusByPaymentId(order.getSubSalesOrder().getPaymentId(),
				OrderConstants.CLOSED_ORDER_STATUS);
	}
		response.setRefund(isRefund);
		response.setStatus(true);
		response.setStatusCode("200");
		response.setStatusMsg("cancelled successfully!");
		return response;
	}

	private RefundPaymentRespone processPreInvoiceOrderForSplit(CancelOrderRequest request, SplitSalesOrder order, Stores store, boolean shouldParentOrderClose, SalesOrder parentOrder) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		BigDecimal storeCreditAmount = order.getAmstorecreditAmount();
		BigDecimal totalPaid = null;
		Map<String, BigDecimal> skumapList = new HashMap<>();
		List<SplitSalesOrderItem> salesItemList = new ArrayList<>();
		String payfortErronMessage = null;

		String fortId = null;
		String paymentMethod = null;
		String paymentMethodValue = null;
		SalesOrderGrid grid = null;
		String paymentInformation = null;
		String paymentInformationValue = null;
		String paymentStatus = null;
		String paymentStatusValue = null;
		boolean isRefund = false;
		if (null!=order.getSalesOrder() && CollectionUtils.isNotEmpty(order.getSalesOrder().getSalesOrderPayment())) {
			for (SalesOrderPayment payment : order.getSalesOrder().getSalesOrderPayment()) {
				paymentMethod = payment.getMethod();
				fortId = payment.getCcTransId();
				paymentInformation = payment.getAdditionalInformation();
			}
		}

		if (CollectionUtils.isNotEmpty(parentOrder.getSalesOrderPayment())) {
			for (SalesOrderPayment paymentData : parentOrder.getSalesOrderPayment()) {
				paymentInformationValue = paymentData.getAdditionalInformation();
				paymentMethodValue = paymentData.getMethod();
			}
		}

		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInformation,
						SalesOrderPaymentInformation.class);
				if (OrderConstants.checkPaymentMethod(paymentMethod)) {
					paymentStatus = parseNullStr(salesOrderPaymentInformation.getResponseCode());
				} else {
					paymentStatus = parseNullStr(salesOrderPaymentInformation.getResponseMessage());
				}
			} catch (IOException e) {
				LOGGER.error("Error in get sales Order Payment Information for " + order.getIncrementId() + ": ", e);
			}
		}

		if (paymentInformationValue != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformationValue = mapper.readValue(paymentInformationValue,
						SalesOrderPaymentInformation.class);
				if (OrderConstants.checkPaymentMethod(paymentMethodValue)) {
					paymentStatusValue = parseNullStr(salesOrderPaymentInformationValue.getResponseCode());
				} else {
					paymentStatusValue = parseNullStr(salesOrderPaymentInformationValue.getResponseMessage());
				}
			} catch (IOException e) {
				LOGGER.error("Error in get sales Order Payment Information for " + order.getIncrementId() + ": ", e);
			}
		}

		if (OrderConstants.PAYMENT_SUCCESS_CODES.contains(paymentStatusValue)) {
			isRefund = true;
		}
		CancelDetails details = new CancelDetails();
		BigDecimal getProductCanceledAmount = paymentDtfRefundHelper.getCancelAmountForSplitOrder(order, skumapList, salesItemList);

		BigDecimal totalVoucherToRefund = BigDecimal.ZERO;
		BigDecimal calculatedOnlineAmount = paymentDtfRefundHelper.cancelPercentageCalculationForSplitOrder(order,
				getProductCanceledAmount, storeCreditAmount, details, true, paymentMethod, totalVoucherToRefund);

		if (OrderConstants.checkPaymentMethod(paymentMethod)) {
			PayfortConfiguration configuration = new PayfortConfiguration();
			paymentDtfRefundHelper.getPayfortConfDetails(order.getStoreId().toString(), paymentMethod, configuration);
			try {
				LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
			} catch (JsonProcessingException e) {
				LOGGER.error("error during write configuration:" + e.getMessage());
			}

			// flag payfortRefundOnSellerCancellation to check consul tryue or false:
			boolean payfortRefundOnSellerCancellation = Constants.orderCredentials.getPayfort()
					.isPayfortRefundOnSellerCancellation();
			boolean isMadaTransaction = isMadaTransaction(parentOrder);
			boolean isSellerCancelDoneBefore = isSellerCancelDoneBefore(parentOrder);

			LOGGER.info("processPreInvoiceOrder — Order " + order.getIncrementId() + " consul flag is : "
					+ payfortRefundOnSellerCancellation);
			LOGGER.info("processPreInvoiceOrder — Order " + order.getIncrementId() + " is MADA transaction: "
					+ isMadaTransaction);
			LOGGER.info("processPreInvoiceOrder — Order " + order.getIncrementId()
					+ " has previous seller cancellations: " + isSellerCancelDoneBefore);

			if(shouldParentOrderClose && parentOrder != null) {
				boolean shouldCaptureAndRefund = payfortRefundOnSellerCancellation && isMadaTransaction && isSellerCancelDoneBefore;
				if (shouldCaptureAndRefund) {
					LOGGER.info(
							"processPreInvoiceOrder — Order {} meets all conditions: Payfort Refund on Seller Cancellation is enabled, is a MADA transaction, and has previous seller cancellations."
									+ parentOrder.getIncrementId());

					LOGGER.info("processPreInvoiceOrder — Initiating Payfort Capture Status Check for Order {}"
							+ parentOrder.getIncrementId());

					Object[] result = processPayfortCaptureAndRefund(parentOrder, configuration, fortId, paymentMethod);
					response = (RefundPaymentRespone) result[0];
					if (result[1] != null) {
						calculatedOnlineAmount = (BigDecimal) result[1];
					}
				} else if (null != parentOrder.getPayfortAuthorized() && parentOrder.getPayfortAuthorized() == 1) {
					response = paymentDtfRefundHelper.payfortVoidAuthorizationcall(parentOrder, fortId, paymentMethod);

				} else {
					response = paymentDtfRefundHelper.payfortRefundcall(parentOrder, calculatedOnlineAmount, fortId,
							paymentMethod);
				}
			} else if (shouldParentOrderClose && parentOrder == null) {
				response.setStatus(true);
				response.setStatusCode("200");
			}
			// If parent order is not closing, then only do refund/void for the split order. If parent order is closing, refund/void would have been already done for parent order, so skip it for split order to avoid duplicate refund/void calls
			//SFP-1392  START - cancel shipment in multi shipment order
			else if (!shouldParentOrderClose) {
				LOGGER.info("Parent order is not closing for : " + order.getIncrementId()+" store id: "+order.getStoreId());
				LOGGER.info("PayfortAuthorized: " + ((null!=parentOrder && null!=parentOrder.getPayfortAuthorized()) ? parentOrder.getPayfortAuthorized() : "null"));
				if (null != parentOrder.getPayfortAuthorized() && parentOrder.getPayfortAuthorized() == 0) {
					LOGGER.info("Payfort refund call for split order: " + order.getIncrementId());
					response = paymentDtfRefundHelper.payfortRefundcall(parentOrder, calculatedOnlineAmount, fortId,
							paymentMethod);
				}
			}
			//SFP-1392 - END cancel shipment in multi shipment order
			if (!"200".equals(response.getStatusCode())) {
				payfortErronMessage = response.getStatusMsg();
			}
			grid = storeCreditAndMemoForSplitOrder(order, store, skumapList, new ArrayList<>(), payfortErronMessage, paymentMethod,
					details, calculatedOnlineAmount, isRefund, shouldParentOrderClose);
		} else if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)) {
			// Check if SubSalesOrder exists before accessing paymentId (may be null for pending_payment/payment_failed orders)
			if (parentOrder != null && parentOrder.getSubSalesOrder() != null && parentOrder.getSubSalesOrder().getPaymentId() != null) {
			ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(parentOrder.getSubSalesOrder().getPaymentId());
			if(shouldParentOrderClose){
				if (OrderConstants.checkTabbyPaymentMethod(paymentMethod) && Objects.isNull(proxyOrder)) {
					paymentUtility.initiateRefund(parentOrder, calculatedOnlineAmount.toString(), paymentMethod);

				} else {
					RefundPaymentRespone closeRes = paymentUtility.initiateClose(parentOrder, calculatedOnlineAmount.toString(),
							paymentMethod);
					if (closeRes.isStatus()) {
						orderHelperV2.updateProxyOrderStatusByPaymentId(parentOrder.getSubSalesOrder().getPaymentId(),
								OrderConstants.CLOSED_ORDER_STATUS);
					} else {
						payfortErronMessage = closeRes.getStatusMsg();
					}

					}
				}
			}

			grid = storeCreditAndMemoForSplitOrder(order, store, skumapList, new ArrayList<>(), payfortErronMessage, paymentMethod,
					details, calculatedOnlineAmount, isRefund, shouldParentOrderClose);
		} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
			if(shouldParentOrderClose && order.getSalesOrder() != null){
				// Check if salesOrder relationship exists before accessing
				paymentUtility.initiateRefund(order.getSalesOrder(), calculatedOnlineAmount.toString(), paymentMethod);
			}
			if (null != order.getGiftVoucherDiscount()
					&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {
				paymentDtfRefundHelper.addStoreCreditForSplitOrder(order, order.getGiftVoucherDiscount(), true);
				paymentDtfRefundHelper.setReturnVoucherValueInDBForSplitOrder(order, order.getGiftVoucherDiscount());
			}
		} else {
			if(shouldParentOrderClose && order.getSalesOrder() != null){
			// Check if salesOrder relationship exists before accessing
				grid = refundHelper.cancelOrderGrid(order.getSalesOrder(), true, paymentMethod);
			}

			if (details.getAmasyStoreCredit() != null && order.getCustomerId() != null
					&& details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) >= 0) {

				BigDecimal baseAmastStoreCredit = details.getAmasyStoreCredit().multiply(order.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP);
				refundHelper.releaseStoreCreditForSplitOrder(order, baseAmastStoreCredit);
			}
		}

		if (grid != null) {
			grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			salesOrderGridRepository.saveAndFlush(grid);
		}

		
		order = refundHelper.cancelOrderObjectForSplitOrder(request, order, false, paymentMethod);
		if(!shouldParentOrderClose && parentOrder != null){
			parentOrder = refundHelper.cancelOrderObjectV2(request, parentOrder, order, false, paymentMethod);
		}
		LOGGER.info("wms status during cancellation" + order.getIncrementId() + " status: " + order.getWmsStatus());
		if (null != order.getWmsStatus()) {
			order.setWmsStatus(2);
		}

		if(shouldParentOrderClose && parentOrder != null){
			parentOrder = refundHelper.cancelOrderObject(request, parentOrder, false, paymentMethod);
			LOGGER.info("wms status during cancellation" + parentOrder.getIncrementId() + " status: " + parentOrder.getWmsStatus());
			if (null != parentOrder.getWmsStatus()) {
				parentOrder.setWmsStatus(2);
			}
		}

		order = refundHelper.cancelOrderItemsForSplitOrder(order, false);
		if (parentOrder != null) {
		parentOrder = refundHelper.cancelOrderItemsV2(parentOrder, order, false);
		if(shouldParentOrderClose){
			parentOrder = refundHelper.cancelOrderItems(parentOrder, false);
		}
		}
		
		String message = OrderConstants.CANCELLED_MSG_CUSTOMER;
		order = refundHelper.cancelStatusHistoryForSplitOrder(order, false, totalPaid, message);

		if(shouldParentOrderClose && parentOrder != null){
			if (null != parentOrder.getAmstorecreditAmount()) {
				totalPaid = parentOrder.getGrandTotal().add(parentOrder.getAmstorecreditAmount());
			} else {
				totalPaid = parentOrder.getGrandTotal();
			}
			parentOrder = refundHelper.cancelStatusHistory(parentOrder, false, totalPaid, message);
		}

		if(shouldParentOrderClose){
			// Check if SubSalesOrder exists before accessing (may be null for pending_payment/payment_failed orders)
			if (parentOrder != null && parentOrder.getSubSalesOrder() != null) {
			parentOrder.getSubSalesOrder().setRetryPayment(0);
			parentOrder.setRetryPayment(0);
			parentOrder.getSubSalesOrder().setOrderExpiredAt(null);
			parentOrder.getSubSalesOrder().setFirstNotificationAt(null);
			parentOrder.getSubSalesOrder().setSecondNotificationAt(null);
			parentOrder.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			orderHelper.updateStatusHistory(parentOrder, false, false, false, true, false);
				
			orderHelper.buildOTSPayloadForSplitOrderAndPublishToPubSub(order, "Split Order Closed", "4.0");
			if (parentOrder.getSubSalesOrder().getPaymentId() != null) {
			orderHelperV2.updateProxyOrderStatusByPaymentId(parentOrder.getSubSalesOrder().getPaymentId(),
					OrderConstants.CLOSED_ORDER_STATUS);
			}
			} else {
				parentOrder.setRetryPayment(0);
				parentOrder.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				orderHelper.updateStatusHistory(parentOrder, false, false, false, true, false);
				orderHelper.buildOTSPayloadForSplitOrderAndPublishToPubSub(order, "Split Order Closed", "4.0");
			}
		}

		order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
		order.setRetryPayment(0);
		// Check if SplitSubSalesOrder exists before accessing (may be null for pending_payment/payment_failed orders)
		if (order.getSplitSubSalesOrder() != null) {
		order.getSplitSubSalesOrder().setRetryPayment(0);
		order.getSplitSubSalesOrder().setOrderExpiredAt(null);
		order.getSplitSubSalesOrder().setFirstNotificationAt(null);
		order.getSplitSubSalesOrder().setSecondNotificationAt(null);
		}
		// Check if parentOrder is not null before saving
		if (parentOrder != null) {
		salesOrderRepository.saveAndFlush(parentOrder);
		}
		splitSalesOrderRepository.saveAndFlush(order);
		
		if(order.getSplitSellerOrders() != null) {
			// Create a copy to avoid ConcurrentModificationException
			List<SplitSellerOrder> sellerOrdersCopy = new ArrayList<>(order.getSplitSellerOrders());
			for (SplitSellerOrder sellerOrder : sellerOrdersCopy) {
				orderHelper.cancelSellerOrderAndItems(sellerOrder, OrderConstants.CANCELLED_BY_CUSTOMER, OrderConstants.WMS_STATUS_PUSH_TO_WMS, new HashMap<String, BigDecimal>());
			}
		}

		response.setRefund(isRefund);
		response.setStatus(true);
		response.setStatusCode("200");
		response.setStatusMsg("cancelled successfully!");
		return response;
	}

	/**
	 * Generate Credit Memo and Refund to Styli Credit On Order Cancellation
	 * 
	 * @param order
	 * @param store
	 * @param skumapList
	 * @param salesItemList
	 * @param payfortErronMessage
	 * @param paymentMethod
	 * @param details
	 * @param calculatedOnlineAmount
	 * @return
	 */
	private SalesOrderGrid storeCreditAndMemo(SalesOrder order, Stores store, Map<String, BigDecimal> skumapList,
			List<SalesOrderItem> salesItemList, String payfortErronMessage, String paymentMethod, CancelDetails details,
			BigDecimal calculatedOnlineAmount, boolean isRefund) {
		SalesOrderGrid grid;
		if (details.getAmasyStoreCredit() != null && order.getCustomerId() != null
				&& details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) != 0) {

			BigDecimal baseAmastStoreCredit = details.getAmasyStoreCredit().multiply(order.getStoreToBaseRate())
					.setScale(4, RoundingMode.HALF_UP);
			refundHelper.releaseStoreCredit(order, baseAmastStoreCredit);

		}

		// String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
		grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
		// if (isRefund) {
		// SalesCreditmemo memo = refundHelper.createCreditMemo(order, memoIncrementId,
		// store, calculatedOnlineAmount,
		// details.getAmasyStoreCredit(), paymentMethod, skumapList, true, new
		// HashMap<>());
		// refundHelper.createCancelCreditmemoItems(order, memo, skumapList,
		// salesItemList);
		// if (StringUtils.isNotBlank(payfortErronMessage)) {
		// refundHelper.createCreditmemoFailComment(memo, details.getAmasyStoreCredit(),
		// payfortErronMessage);
		// refundHelper.updateOrderStatusHistory(order, payfortErronMessage, "order",
		// order.getStatus());
		// } else {
		// refundHelper.createCreditmemoComment(memo, details.getAmasyStoreCredit());
		// }
		// refundHelper.createCreditmemoGrid(order, memo, memoIncrementId, grid,
		// order.getBaseGrandTotal());
		// }
		return grid;
	}

	private SalesOrderGrid storeCreditAndMemoForSplitOrder(SplitSalesOrder order, Stores store, Map<String, BigDecimal> skumapList,
			List<SalesOrderItem> salesItemList, String payfortErronMessage, String paymentMethod, CancelDetails details,
			BigDecimal calculatedOnlineAmount, boolean isRefund, boolean isParentOrderClosed) {
		SalesOrderGrid grid = null;
		if (details.getAmasyStoreCredit() != null && order.getCustomerId() != null
				&& details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) != 0) {

			BigDecimal baseAmastStoreCredit = details.getAmasyStoreCredit().multiply(order.getStoreToBaseRate())
					.setScale(4, RoundingMode.HALF_UP);
			refundHelper.releaseStoreCreditForSplitOrder(order, baseAmastStoreCredit);

		}
		if(isParentOrderClosed && order.getSalesOrder() != null){
		// Check if salesOrder relationship exists before accessing
			grid = refundHelper.cancelOrderGrid(order.getSalesOrder(), true, paymentMethod);
		}
		return grid;
	}

	public void cancelReedmeExternalCoupon(Stores store, SalesOrder order, boolean isReplica, boolean isProxy) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

		CouponExternalRedemptionRequest payload = new CouponExternalRedemptionRequest();

		if (null != order.getSubSalesOrder()) {

			payload.setTrackingId(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId());
			payload.setRedemptionStatus(0);

			CouponValidationRequestV4Metadata metadata = new CouponValidationRequestV4Metadata();
			metadata.setStoreId(Integer.parseInt(store.getStoreId()));
			metadata.setCurrency(store.getStoreCurrency());
			HttpEntity<CouponExternalRedemptionRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
			String url = getPromoRedemptionChangeStatusUrl(store.getStoreId(), order.getCustomerEmail());
			Map<String, Object> parameters = new HashMap<>();
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
			LOGGER.info("Redemption Coupon URl:" + builder.buildAndExpand(parameters).toUri());
			SubSalesOrder subSaleOrder = order.getSubSalesOrder();
			LOGGER.info("Cancel Redemption Coupon URl:" + builder.buildAndExpand(parameters).toUri());
			try {
				ResponseEntity<CustomCouponCancelRedemptionResponse> response = restTemplate.exchange(
						builder.buildAndExpand(parameters).toUri(), HttpMethod.PUT, requestBody,
						CustomCouponCancelRedemptionResponse.class);

				if (response.getStatusCode() == HttpStatus.OK && !isProxy) {
					checkResponseStatus(order, isReplica, subSaleOrder, response);
				}
			} catch (RestClientException | JsonProcessingException e) {
				LOGGER.error("Exception occurred  during REST call:" + e.getMessage());
				if (!isProxy) {
					subSaleOrder.setExternalCouponRedemptionStatus(0);
					subSaleOrder.setSalesOrder(order);
					order.setSubSalesOrder(subSaleOrder);
					salesOrderRepository.saveAndFlush(order);
				}
			}
		}
	}

	public void cancelReedmeExternalCouponForSplitOrder(Stores store, SplitSalesOrder order, boolean isReplica, boolean isProxy) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

		CouponExternalRedemptionRequest payload = new CouponExternalRedemptionRequest();

		if (null != order.getSplitSubSalesOrder()) {

			payload.setTrackingId(order.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId());
			payload.setRedemptionStatus(0);

			CouponValidationRequestV4Metadata metadata = new CouponValidationRequestV4Metadata();
			metadata.setStoreId(Integer.parseInt(store.getStoreId()));
			metadata.setCurrency(store.getStoreCurrency());
			HttpEntity<CouponExternalRedemptionRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
			String url = getPromoRedemptionChangeStatusUrl(store.getStoreId(), order.getCustomerEmail());
			Map<String, Object> parameters = new HashMap<>();
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
			LOGGER.info("Redemption Coupon URl:" + builder.buildAndExpand(parameters).toUri());
			SplitSubSalesOrder subSaleOrder = order.getSplitSubSalesOrder();
			LOGGER.info("Cancel Redemption Coupon URl:" + builder.buildAndExpand(parameters).toUri());
			try {
				ResponseEntity<CustomCouponCancelRedemptionResponse> response = restTemplate.exchange(
						builder.buildAndExpand(parameters).toUri(), HttpMethod.PUT, requestBody,
						CustomCouponCancelRedemptionResponse.class);

				if (response.getStatusCode() == HttpStatus.OK && !isProxy) {
					checkResponseStatusForSplitOrder(order, isReplica, subSaleOrder, response);
				}
			} catch (RestClientException | JsonProcessingException e) {
				LOGGER.error("Exception occurred  during REST call:" + e.getMessage());
				if (!isProxy) {
					subSaleOrder.setExternalCouponRedemptionStatus(0);
					subSaleOrder.setSplitSalesOrder(order);
					order.setSplitSubSalesOrder(subSaleOrder);
					splitSalesOrderRepository.saveAndFlush(order);
				}
			}
		}
	}

	private String getPromoRedemptionChangeStatusUrl(String storeId, String customerEmail) {
		PromoRedemptionValues values = Constants.getPromoRedemptionUrl();
		String url = values.getDefaultRedemptionChangeStatusEndpoint();
		if (values.isEnabled() && !values.isAllowAllStores()) {
			boolean allowedStores = values.getAllowedStores().stream()
					.anyMatch(store -> store.equals(Integer.parseInt(storeId)));
			if ((values.isAllowInternalUsers() && customerEmail.contains(Constants.INTERNAL_USERS_EMAIL)
					&& !values.getExcludeEmailId().contains(customerEmail)) || allowedStores) {
				url = values.getRedemptionChangeStatusEndpoint();
			}
		}
		LOGGER.info("Inside getPromoRedemptionChangeStatusUrl: url is " + url);
		return url;
	}

	private void checkResponseStatus(SalesOrder order, boolean isReplica, SubSalesOrder subSaleOrder,
			ResponseEntity<CustomCouponCancelRedemptionResponse> response) throws JsonProcessingException {
		LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));
		CustomCouponCancelRedemptionResponse body = response.getBody();
		if (body != null && body.getCode() == 200) {
			LOGGER.info("Coupon redemption cancellation response: " + response.getBody());
			subSaleOrder.setExternalCouponRedemptionStatus(2);
			if (isReplica) {
				subSaleOrder.setExternalQuoteStatus(1);
			}
			subSaleOrder.setSalesOrder(order);
			order.setSubSalesOrder(subSaleOrder);
			salesOrderRepository.saveAndFlush(order);
		} else {
			subSaleOrder.setExternalCouponRedemptionStatus(0);
			subSaleOrder.setSalesOrder(order);
			order.setSubSalesOrder(subSaleOrder);
			salesOrderRepository.saveAndFlush(order);
		}
	}

	private void checkResponseStatusForSplitOrder(SplitSalesOrder order, boolean isReplica, SplitSubSalesOrder subSaleOrder,
			ResponseEntity<CustomCouponCancelRedemptionResponse> response) throws JsonProcessingException {
		LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));
		CustomCouponCancelRedemptionResponse body = response.getBody();
		if (body != null && body.getCode() == 200) {
			LOGGER.info("Coupon redemption cancellation response: " + response.getBody());
			subSaleOrder.setExternalCouponRedemptionStatus(2);
			if (isReplica) {
				subSaleOrder.setExternalQuoteStatus(1);
			}
			subSaleOrder.setSplitSalesOrder(order);
			order.setSplitSubSalesOrder(subSaleOrder);
			splitSalesOrderRepository.saveAndFlush(order);
		} else {
			subSaleOrder.setExternalCouponRedemptionStatus(0);
			subSaleOrder.setSplitSalesOrder(order);
			order.setSplitSubSalesOrder(subSaleOrder);
			splitSalesOrderRepository.saveAndFlush(order);
		}
	}

	/**
	 * Process back order items linked to a specific split seller order.
	 * Used when cancellations are initiated via seller order flows.
	 */
	public void processBackOrderItemsOnCancelForSplitSellerOrder(SplitSellerOrder sellerOrder) {
		try {
			if (sellerOrder == null || sellerOrder.getEntityId() == null) {
				return;
			}

			List<SellerBackOrderItem> backOrderItems = sellerBackOrderItemRepository
					.findBySplitSellerOrderAndStatusNot(sellerOrder, "CLOSED");

			if (CollectionUtils.isEmpty(backOrderItems)) {
				LOGGER.info("[BackOrder][Seller] No active back order items found for seller order: " + sellerOrder.getIncrementId());
				return;
			}

			LOGGER.info("[BackOrder][Seller] Found " + backOrderItems.size() + " back order items for seller order: " + sellerOrder.getIncrementId());

			Map<Integer, List<SellerBackOrderItem>> itemsBySeller = groupItemsBySeller(backOrderItems);
			
			// Separate items by parent back order status
			BackOrderItemsByStatus itemsByStatus = separateItemsByParentStatus(itemsBySeller, sellerOrder);
			
			// Process items with CLOSED parent status
			processClosedParentBackOrders(itemsByStatus.getItemsWithClosedParent(), sellerOrder);

			LOGGER.info("[BackOrder][Seller] Successfully processed back order items for seller order: " + sellerOrder.getIncrementId());

		} catch (Exception e) {
			LOGGER.error("[BackOrder][Seller] Error processing back order items for seller order: "
					+ (sellerOrder != null ? sellerOrder.getIncrementId() : "null") + " - " + e.getMessage(), e);
		}
	}

	private Map<Integer, List<SellerBackOrderItem>> groupItemsBySeller(List<SellerBackOrderItem> backOrderItems) {
		Map<Integer, List<SellerBackOrderItem>> itemsBySeller = new HashMap<>();
		for (SellerBackOrderItem item : backOrderItems) {
			if (item.getSellerBackOrder() != null) {
				Integer sellerId = item.getSellerBackOrder().getSellerId();
				itemsBySeller.computeIfAbsent(sellerId, k -> new ArrayList<>()).add(item);
			}
		}
		return itemsBySeller;
	}

	private BackOrderItemsByStatus separateItemsByParentStatus(
			Map<Integer, List<SellerBackOrderItem>> itemsBySeller, SplitSellerOrder sellerOrder) {
		
		List<SellerBackOrderItem> itemsWithOpenParent = new ArrayList<>();
		Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParentBySeller = new HashMap<>();
		
		for (Map.Entry<Integer, List<SellerBackOrderItem>> entry : itemsBySeller.entrySet()) {
			List<SellerBackOrderItem> sellerItems = entry.getValue();
			
			for (SellerBackOrderItem item : sellerItems) {
				categorizeItemByParentStatus(item, itemsWithOpenParent, itemsWithClosedParentBySeller);
			}
		}
		
		logOpenParentItemsClosed(itemsWithOpenParent, sellerOrder);
		
		return new BackOrderItemsByStatus(itemsWithOpenParent, itemsWithClosedParentBySeller);
	}

	private void categorizeItemByParentStatus(SellerBackOrderItem item,
			List<SellerBackOrderItem> itemsWithOpenParent,
			Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParentBySeller) {
		
		SellerBackOrder parentBackOrder = item.getSellerBackOrder();
		if (parentBackOrder == null) {
			LOGGER.warn("[BackOrder][Seller] Item " + item.getEntityId() + " has no parent back order, skipping");
			return;
		}
		
		String parentStatus = parentBackOrder.getStatus();
		if ("OPEN".equalsIgnoreCase(parentStatus)) {
			closeItemWithOpenParent(item, itemsWithOpenParent);
		} else if ("CLOSED".equalsIgnoreCase(parentStatus)) {
			groupItemForClosedParent(item, parentBackOrder, itemsWithClosedParentBySeller);
		} else {
			LOGGER.warn("[BackOrder][Seller] Item " + item.getEntityId() + " has parent back order with unknown status: " + parentStatus);
		}
	}

	private void closeItemWithOpenParent(SellerBackOrderItem item, List<SellerBackOrderItem> itemsWithOpenParent) {
		item.setStatus("CLOSED");
		sellerBackOrderItemRepository.save(item);
		itemsWithOpenParent.add(item);
	}

	private void groupItemForClosedParent(SellerBackOrderItem item, SellerBackOrder parentBackOrder,
			Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParentBySeller) {
		
		Integer sellerId = parentBackOrder.getSellerId();
		itemsWithClosedParentBySeller.computeIfAbsent(sellerId, k -> new ArrayList<>()).add(item);
	}

	private void logOpenParentItemsClosed(List<SellerBackOrderItem> itemsWithOpenParent, SplitSellerOrder sellerOrder) {
		if (!itemsWithOpenParent.isEmpty()) {
			LOGGER.info("[BackOrder][Seller] Closed " + itemsWithOpenParent.size() 
					+ " back order items with OPEN parent status for seller order: " + sellerOrder.getIncrementId());
		}
	}

	private void processClosedParentBackOrders(
			Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParentBySeller, SplitSellerOrder sellerOrder) {
		
		// Group items by parent back order to handle parent closure
		Map<Integer, List<SellerBackOrderItem>> itemsByParentBackOrder = groupItemsByParentBackOrder(itemsWithClosedParentBySeller);
		
		// Process each parent back order group
		for (Map.Entry<Integer, List<SellerBackOrderItem>> parentEntry : itemsByParentBackOrder.entrySet()) {
			processParentBackOrderGroup(parentEntry.getValue(), sellerOrder);
		}
	}

	private Map<Integer, List<SellerBackOrderItem>> groupItemsByParentBackOrder(
			Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParentBySeller) {
		
		Map<Integer, List<SellerBackOrderItem>> itemsByParentBackOrder = new HashMap<>();
		
		for (Map.Entry<Integer, List<SellerBackOrderItem>> entry : itemsWithClosedParentBySeller.entrySet()) {
			List<SellerBackOrderItem> sellerItems = entry.getValue();
			for (SellerBackOrderItem item : sellerItems) {
				if (item.getSellerBackOrder() != null) {
					Integer parentBackOrderId = item.getSellerBackOrder().getEntityId();
					itemsByParentBackOrder.computeIfAbsent(parentBackOrderId, k -> new ArrayList<>()).add(item);
				}
			}
		}
		
		return itemsByParentBackOrder;
	}

	private void processParentBackOrderGroup(List<SellerBackOrderItem> itemsForParent, SplitSellerOrder sellerOrder) {
		if (CollectionUtils.isEmpty(itemsForParent)) {
			return;
		}
		
		SellerBackOrder parentBackOrder = itemsForParent.get(0).getSellerBackOrder();
		if (parentBackOrder == null) {
			LOGGER.warn("[BackOrder][Seller] No parent back order found for items");
			return;
		}
		
		String backOrderIncrementId = parentBackOrder.getBackOrderIncrementid();
		LOGGER.info("[BackOrder][Seller] Processing CLOSED parent back order: " + backOrderIncrementId 
				+ " with " + itemsForParent.size() + " items");
		
		try {
			// Push back order cancellation to seller WMS cancel endpoint
			orderpushHelper.orderBackOrderCancelpushTowmsForSellerOrder(sellerOrder, itemsForParent, backOrderIncrementId);
			LOGGER.info("[BackOrder][Seller] Successfully pushed back order cancellation to seller WMS for back order: " + backOrderIncrementId);
			
			// Close all child items of this parent back order (not just the ones in this seller order)
			closeAllChildBackOrderItems(parentBackOrder);
			
			LOGGER.info("[BackOrder][Seller] Successfully closed all child items for parent back order: " + backOrderIncrementId);
		} catch (Exception e) {
			LOGGER.error("[BackOrder][Seller] Failed to push back order cancellation to seller WMS for back order: " 
					+ backOrderIncrementId + " - " + e.getMessage(), e);
		}
	}

	/**
	 * Helper class to hold items categorized by parent back order status.
	 */
	private static class BackOrderItemsByStatus {
		private final List<SellerBackOrderItem> itemsWithOpenParent;
		private final Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParent;

		public BackOrderItemsByStatus(List<SellerBackOrderItem> itemsWithOpenParent,
				Map<Integer, List<SellerBackOrderItem>> itemsWithClosedParent) {
			this.itemsWithOpenParent = itemsWithOpenParent;
			this.itemsWithClosedParent = itemsWithClosedParent;
		}

		public List<SellerBackOrderItem> getItemsWithOpenParent() {
			return itemsWithOpenParent;
		}

		public Map<Integer, List<SellerBackOrderItem>> getItemsWithClosedParent() {
			return itemsWithClosedParent;
		}
	}

	/**
	 * Close all child back order items when parent back order is CLOSED.
	 * This will find all items across all orders linked to this parent back order and close them.
	 */
	private void closeAllChildBackOrderItems(SellerBackOrder parentBackOrder) {
		try {
			if (parentBackOrder == null || parentBackOrder.getEntityId() == null) {
				LOGGER.warn("[BackOrder][Seller] Cannot close child items - parent back order is null or has no ID");
				return;
			}
			
			// Find all back order items linked to this parent back order
			List<SellerBackOrderItem> allChildItems = sellerBackOrderItemRepository.findAll()
					.stream()
					.filter(item -> item.getSellerBackOrder() != null && 
							item.getSellerBackOrder().getEntityId().equals(parentBackOrder.getEntityId()) &&
							!"CLOSED".equalsIgnoreCase(item.getStatus()))
					.collect(java.util.stream.Collectors.toList());
			
			if (CollectionUtils.isEmpty(allChildItems)) {
				LOGGER.info("[BackOrder][Seller] No active child items found for parent back order: " + parentBackOrder.getBackOrderIncrementid());
				return;
			}
			
			LOGGER.info("[BackOrder][Seller] Closing " + allChildItems.size() + " child items for parent back order: " + parentBackOrder.getBackOrderIncrementid());
			
			// Close all child items
			for (SellerBackOrderItem item : allChildItems) {
				item.setStatus("CLOSED");
				sellerBackOrderItemRepository.save(item);
			}
			
			LOGGER.info("[BackOrder][Seller] Successfully closed all " + allChildItems.size() + " child items for parent back order: " + parentBackOrder.getBackOrderIncrementid());
			
		} catch (Exception e) {
			LOGGER.error("[BackOrder][Seller] Error closing child back order items for parent: " 
					+ (parentBackOrder != null ? parentBackOrder.getBackOrderIncrementid() : "null") + " - " + e.getMessage(), e);
		}
	}

}

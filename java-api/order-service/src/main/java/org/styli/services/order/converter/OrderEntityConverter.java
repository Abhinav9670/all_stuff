package org.styli.services.order.converter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.SalesOrder.AddressChangeHistory;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.consul.oms.base.AddressChangeAttributes;
import org.styli.services.order.pojo.mulin.GalleryItem;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.pojo.response.V3.OrderResponseV3;
import org.styli.services.order.pojo.response.V3.SplitOrderResponse;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.KeySpec;


/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class OrderEntityConverter {

	private static final Log LOGGER = LogFactory.getLog(OrderEntityConverter.class);

	@Autowired
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	/** UAE Arabic store ID used as fallback when an Arabic store has no status labels in DB (e.g. Oman 25). */
	private static final int ARABIC_LABEL_FALLBACK_STORE_ID = 11;

	@Autowired
	ConfigService configService;

	/**
	 * Get status label for (status, storeId). If not found and storeId is an Arabic store, fallback to UAE Arabic (11)
	 * so Arabic translations show for stores that have no rows in sales_order_status_label (e.g. Oman 25).
	 */
	private SalesOrderStatusLabel getStatusLabelForStore(String status, Integer storeId) {
		if (status == null || storeId == null) return null;
		SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
		key.setStatus(status);
		key.setStoreId(storeId);
		SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
		if (label != null) return label;
		if (Constants.ARABIC_STORES.contains(storeId) && storeId != ARABIC_LABEL_FALLBACK_STORE_ID) {
			key = new SalesOrderStatusLabelPK();
			key.setStatus(status);
			key.setStoreId(ARABIC_LABEL_FALLBACK_STORE_ID);
			return salesOrderStatusLabelRepository.findById(key);
		}
		return null;
	}

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	AmastyRmaTrackingRepository amastyRmaTrackingRepository;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	ShipmentTrackerRepository shipmentTrackerRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepo;

	@Autowired
	private AddressChangeHistoryRepository addressChangeHistoryRepository;

	@Autowired
	private SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	OrderHelper	orderHelper;

	@Value("${magento.base.url}")
	private String magentoBaseUrl;

	@Value("${shipping.navik.base.url}")
	private String shippingNavikBaseUrl;

	@Value("${shipping.clickpost.base.url}")
	private String shippingClickpostBaseUrl;

	@Autowired
	SplitSellerOrderRepository splitSellerOrderRepository;

	public static final ObjectMapper mapper = new ObjectMapper();

	public OrderResponse convertOrder(SalesOrder order, boolean orderDetails,
									  ObjectMapper mapper, Integer storeId,
									  Map<String, ProductResponseBody> productsFromMulin, String xClientVersion, Boolean isSecondRefund) {

		OrderResponse resp = new OrderResponse();
		List<SalesOrder> pendingOrderList = null;
		Integer rmaCountVal= 0;
		double refundAmountToBeDeducted=0.0;
		if(order.getEntityId() != null && order.getStoreId() != null) {

			if(Constants.orderCredentials.getBlockShukranSecondRefund() && order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
				refundAmountToBeDeducted= 0.0;
			}else {
				boolean isAppVersionSufficient = false;
				if (StringUtils.isNotBlank(xClientVersion) && StringUtils.isNotEmpty(xClientVersion) && StringUtils.isNotBlank(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion()) && StringUtils.isNotEmpty(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion())) {
					Long mobileAppVersion = Constants.decodeAppVersion(xClientVersion);
					Long secondReturnThresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion());
					if (secondReturnThresholdVersion != null && mobileAppVersion != null && secondReturnThresholdVersion <= mobileAppVersion) {
						isAppVersionSufficient = true;
					}
				}


				if (isAppVersionSufficient && Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() != null && Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() > 0) {
					Integer rmaClubbingHours = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();
					rmaCountVal = amastyRmaRequestRepository.getRMACount(order.getEntityId(), rmaClubbingHours);
					if (rmaCountVal != null && rmaCountVal == 0) {
						String requestId = amastyRmaRequestRepository.getLastRequestId(order.getEntityId(), rmaClubbingHours);
						if (StringUtils.isNotEmpty(requestId) && StringUtils.isNotBlank(requestId)) {
							int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
							if (trackingCount > 0) {
								rmaCountVal = 1;
							}
						}
					}
					Double refundValue = configService.getWebsiteRefundByStoreId(order.getStoreId());
					if (refundValue != null && refundValue > 0) {
						refundAmountToBeDeducted = refundValue;
					} else {
						rmaCountVal = 0;
					}
				}
			}
			resp.setRmaCount(rmaCountVal);
			resp.setReturnFee(StringUtils.isNotEmpty(order.getOrderCurrencyCode()) && StringUtils.isNotBlank(order.getOrderCurrencyCode())? order.getOrderCurrencyCode() + " " + refundAmountToBeDeducted:"" +refundAmountToBeDeducted);

		}

		resp.setOrderId(order.getEntityId());
		resp.setStatus(parseNullStr(order.getStatus()));
		resp.setShippingDescription(parseNullStr(order.getShippingDescription()));
		resp.setStoreId(parseNullStr(order.getStoreId()));
		resp.setCustomerId(parseNullStr(order.getCustomerId()));
		resp.setItemCount(parseNullStr(order.getTotalItemCount()));
		resp.setBillingAddressId(parseNullStr(order.getBillingAddressId()));
		resp.setQuoteId(parseNullStr(order.getQuoteId()));
		resp.setShippingAddressId(parseNullStr(order.getShippingAddressId()));
		resp.setIncrementId(parseNullStr(order.getIncrementId()));
		resp.setShippingMethod(parseNullStr(order.getShippingMethod()));
		resp.setCreatedAt(convertTimezone(order.getCreatedAt(), storeId));
		resp.setUpdatedAt(convertTimezone(order.getUpdatedAt(), storeId));
		resp.setEmail(parseNullStr(order.getCustomerEmail()));
		resp.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		resp.setStoreCreditApplied(parseNullStr(order.getAmstorecreditAmount()));
		resp.setIsSecondRefundTagOn(isSecondRefund);
		if(null != order.getSubSalesOrder() ) {
			resp.setTabbyPaymentId(order.getSubSalesOrder().getPaymentId());
			if(order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
				resp.setTotalShukranBurnedPoints(order.getSubSalesOrder().getTotalShukranCoinsBurned().intValue());
				resp.setTotalShukranBurnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
				resp.setTotalShukranBurnedValueInCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
			}
			if(order.getSubSalesOrder().getTotalShukranCoinsEarned() != null && order.getSubSalesOrder().getTotalShukranCoinsEarned().compareTo(BigDecimal.ZERO)>0){
				resp.setTotalShukranEarnedPoints(order.getSubSalesOrder().getTotalShukranCoinsEarned().intValue());
				resp.setTotalShukranEarnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranEarnedValueInBaseCurrency());
				resp.setTotalShukranEarnedValueInCurrency(order.getSubSalesOrder().getTotalShukranEarnedValueInCurrency());
				if(order.getSubSalesOrder().getShukranBasicEarnPoint() != null && order.getSubSalesOrder().getShukranBasicEarnPoint().compareTo(BigDecimal.ZERO)>0) {
					resp.setShukranBasicEarnPoint(order.getSubSalesOrder().getShukranBasicEarnPoint().intValue());
				}
				if(order.getSubSalesOrder().getShukranBonusEarnPoint() != null && order.getSubSalesOrder().getShukranBonusEarnPoint().compareTo(BigDecimal.ZERO)>0) {
					resp.setShukranBonusEarnPoint(order.getSubSalesOrder().getShukranBonusEarnPoint().intValue());
				}
			}
			resp.setQualifiedPurchase(order.getSubSalesOrder().getQualifiedPurchase());
			resp.setTierName(order.getSubSalesOrder().getTierName());
		}

		resp.setDeliveredAt(convertTimezone(order.getDeliveredAt(), storeId));
		if (order.getEstimatedDeliveryTime() != null) {
		    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		    
		    String formattedTime = sdf.format(order.getEstimatedDeliveryTime());
		    resp.setEstimatedDeliveryTime(formattedTime);
		} else {
		    resp.setEstimatedDeliveryTime(null);
		}

		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {
			resp.setDonationAmount(parseNullStr(order.getSubSalesOrder().getDonationAmount()));
		}

		addClickpostmessage(order.getClickpostMessage(), resp, mapper);

		setOrderStatusCallToActionFlag(resp, order);

		Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
		if (statusStatesMap != null) {
			Integer stepValue = statusStatesMap.get(order.getStatus());
			resp.setStatusStepValue(stepValue != null ? stepValue : 0);
		}

		Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
		if (statusColorsMap != null) {
			Integer colorValue = statusColorsMap.get(order.getStatus());
			resp.setStatusColorStepValue(colorValue != null ? colorValue : 0);
		}

		SalesOrderStatusLabel label = getStatusLabelForStore(order.getStatus(), storeId);
		if (label != null) {
			resp.setStatusLabel(label.getLabel());
		} else {
			resp.setStatusLabel(order.getStatus());
		}

		configureOrderTracking(order, resp);

		setFullyOrPartialCancelled(order, resp);

		addOtherinformation(order, resp, orderDetails, productsFromMulin);

		BigDecimal orderTotalValue = order.getGrandTotal();

		resp.setCouponCode(parseNullStr(order.getCouponCode()));
		resp.setGrandTotal(parseNullStr(orderTotalValue));
		resp.setBaseGrandTotal(parseNullStr(orderTotalValue));
		resp.setShippingAmount(parseNullStr(order.getShippingAmount()));
		// SFP-1104 order service cod charges
		BigDecimal codFee = order != null ? order.getCashOnDeliveryFee() : null;
		BigDecimal globalCodFee = order != null ? order.getGlobalCashOnDeliveryFee() : null;
		BigDecimal totalCod = (codFee != null ? codFee : BigDecimal.ZERO)
				.add(globalCodFee != null ? globalCodFee : BigDecimal.ZERO);
		resp.setCodCharges(parseNullStr(totalCod));
		resp.setImportFeesAmount(parseNullStr(order.getImportFee()));
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		resp.setRefundedImportFeesAmount(parseNullStr(order.getRefundedImportFee()));

		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

		addCoupondetails(order, resp, mapper);
		addPaymentinfo(mapper, resp,salesOrderPayment,order);

		addOrderAddress(order, resp, orderDetails);
		addCanceldates(order, resp);
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getRatingStatus()) {
			resp.setIsRated(null != order.getSubSalesOrder().getRatingStatus() && "1".equals(order.getSubSalesOrder().getRatingStatus()) ? true : false);
		}
		// EAS coins added for response order details
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
			resp.setSpendCoin(order.getSubSalesOrder().getEasCoins());
			resp.setCoinToCurrency(parseNullStr(order.getSubSalesOrder().getEasValueInCurrency().toString()));
			resp.setCoinToBaseCurrency(parseNullStr(order.getSubSalesOrder().getEasValueInBaseCurrency().toString()));
		}

		List<RtoAutoRefund> rtoAutoRefund = orderHelper.getRtoAutoRefundList(order);

		if(null != order.getCustomerId()) {

			pendingOrderList = salesOrderRepository.findByCustomerIdAndStatus(order.getCustomerId(), OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
		}
		if (Objects.nonNull(rtoAutoRefund) && !rtoAutoRefund.isEmpty()) {
			resp.setRto(true);
			resp.setRtoStatus(rtoAutoRefund.get(0).getStatus());
			resp.setRtoRefundAt(convertTimezone(rtoAutoRefund.get(0).getRefundAt(), order.getStoreId()));
			resp.setRtoRefundAmount(parseNullStr(rtoAutoRefund.get(0).getRefundAmount()));
		} else if("rto".equals(order.getStatus())){
			resp.setRto(true);
			resp.setRtoStatus("pending");
		}
		LOGGER.info("order retry max threshold:"+Constants.orderCredentials.getOrderDetails().getMaximumOrderPedningOrderThreshold());
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getRetryPayment()
				&& order.getSubSalesOrder().getRetryPayment().equals(1)) {

			resp.setPaymentExpiresAt(convertTimezone(order.getSubSalesOrder().getOrderExpiredAt(), storeId));
			resp.setCanRetryPayment(true);
		}

		if (null != order.getSubSalesOrder() &&
				null != order.getSubSalesOrder().getExternalQuoteId()) {
			resp.setQuoteId(order.getSubSalesOrder().getExternalQuoteId().toString());
		}

		return resp;
	}

	public OrderResponseV3 convertOrderV3(OrderResponse orderResponse,OrderResponseV3 orderResponseBody,SalesOrder order, boolean orderDetails,
										  ObjectMapper mapper, Integer storeId,
										  Map<String, ProductResponseBody> productsFromMulin, String xClientVersion, Boolean isSecondRefund) {

		OrderResponseV3 response = orderResponseBody;
		response.setOrderShipCount(String.valueOf(order.getTotalItemCount()));
		response.setSplitOrder(Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder()));
		if (order.getEstimatedDeliveryTime() != null) {
			LocalDateTime estimatedDeliveryTime = order.getEstimatedDeliveryTime().toLocalDateTime();
			LocalDateTime now = LocalDateTime.now();

			// Calculate remaining SLA days
			long daysBetween = ChronoUnit.DAYS.between(now.toLocalDate(), estimatedDeliveryTime.toLocalDate());
			response.setRemaingSLADays(Math.max(0, (int) daysBetween)+"");

			// Determine if SLA is expired
			response.setSlaExpired(estimatedDeliveryTime.isBefore(now));
		} else {
			response.setRemaingSLADays(0+"");
			response.setSlaExpired(false);
		}
		response.setNewEstimatedDeliveryTime(order.getEstimatedDeliveryTime());
		response.setEstimatedDeliveryTime(null!=order.getEstimatedDeliveryTime()?order.getEstimatedDeliveryTime().toString():"");
		response.setCallToActionFlag(orderResponse.getCallToActionFlag());
		response.setRto(orderResponse.isRto());

		if(null != order.getSubSalesOrder() ) {
			response.setQualifiedPurchase(order.getSubSalesOrder().getQualifiedPurchase());
		}
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		setOrderPaymentInfo(response,salesOrderPayment,order,xClientVersion);
		List<SplitOrderResponse> splitOrderResponses = new ArrayList<>();
		Set<SplitSalesOrder> splitOrders = order.getSplitSalesOrders();
		if (!splitOrders.isEmpty()) {
			response.setSplitOrder(true);
			response.setTotalSplitOrderCount(splitOrders.size());
			int splitCount = 0;
			for (SplitSalesOrder splitOrder : splitOrders) {
				splitCount++;
				SalesOrderStatusLabel label = getStatusLabelForStore(splitOrder.getStatus(), storeId);
				splitOrderResponses.add(convertSplitOrder(orderResponse,splitCount,splitOrder,orderDetails, mapper, storeId, productsFromMulin, xClientVersion, isSecondRefund,label));
			}
		} else {
			response.setSplitOrder(false);
			response.setTotalSplitOrderCount(1);
			SalesOrderStatusLabel label = getStatusLabelForStore(order.getStatus(), storeId);
			splitOrderResponses.add(convertNormalOrder(orderResponse,order, orderDetails, mapper, storeId, productsFromMulin, xClientVersion, isSecondRefund,label));
		}

		response.setSplitOrders(splitOrderResponses);

		return response;
	}

	public SplitOrderResponse convertSplitOrder(
			OrderResponse orderResponse,
			int splitCount,
			SplitSalesOrder splitOrder,
			boolean orderDetails,
			ObjectMapper mapper,
			Integer storeId,
			Map<String, ProductResponseBody> productsFromMulin,
			String xClientVersion,
			Boolean isSecondRefund,
			SalesOrderStatusLabel label) {

		return buildSplitOrderResponse(
				orderResponse,
				splitOrder.getStatus(),
				splitOrder.getShipmentMode(),
				splitOrder.getEntityId(),
				splitOrder.getIncrementId(),
				splitOrder.getEstimatedDeliveryTime(),
				splitCount,
				splitOrder,
				orderDetails,
				productsFromMulin,
				storeId,
				mapper,
				false, // split order
				label
		);
	}

	public SplitOrderResponse convertNormalOrder(
			OrderResponse orderResponse,
			SalesOrder salesOrder,
			boolean orderDetails,
			ObjectMapper mapper,
			Integer storeId,
			Map<String, ProductResponseBody> productsFromMulin,
			String xClientVersion,
			Boolean isSecondRefund,SalesOrderStatusLabel label) {

		return buildSplitOrderResponse(
				orderResponse,
				salesOrder.getStatus(),
				Constants.LOCAL_SHIPMENT,
				salesOrder.getEntityId(),
				salesOrder.getIncrementId(),
				salesOrder.getEstimatedDeliveryTime(),
				1, // always one for normal
				salesOrder,
				orderDetails,
				productsFromMulin,
				storeId,
				mapper,
				true, // normal order
				label
		);
	}

	private SplitOrderResponse buildSplitOrderResponse(
			OrderResponse orderResponse,
			String status,
			String shipmentMode,
			Integer entityId,
			String incrementId,
			Timestamp estimatedDelivery,
			Integer splitCount,
			Object sourceOrder,
			boolean orderDetails,
			Map<String, ProductResponseBody> productsFromMulin,
			Integer storeId,
			ObjectMapper mapper,
			boolean isNormalOrder, // true = SalesOrder, false = SplitSalesOrder
			SalesOrderStatusLabel label
	) {
		SplitOrderResponse response = new SplitOrderResponse();
		response.setSplitOrderCount(splitCount);
		response.setStatus(status);

		response.setStatusLabel(label != null ? label.getLabel() : status);

		response.setSplitIncrementId(incrementId);
		try{
			if (isNormalOrder) {
				response.setEstimatedDeliveryTime(estimatedDelivery);
				response.setNewEstimatedDeliveryTime(estimatedDelivery);
			} else {
				viewUpdateEstimateDateOfSplitOrder((SplitSalesOrder) sourceOrder,((SplitSalesOrder) sourceOrder).getSplitSalesOrderItems().stream().toList(),response);
			}
		}catch (Exception e){
			LOGGER.error("Error while setting estimated delivery time for split order response" +
					"setting from defult delivery date from Main Order, splitOrderId: "+entityId,e);
			response.setEstimatedDeliveryTime(estimatedDelivery);
			response.setNewEstimatedDeliveryTime(estimatedDelivery);
		}

		if (estimatedDelivery != null) {
			LocalDateTime estimated = estimatedDelivery.toLocalDateTime();
			LocalDateTime now = LocalDateTime.now();
			long daysBetween = ChronoUnit.DAYS.between(now.toLocalDate(), estimated.toLocalDate());
			response.setRemaingSLADays(Math.max(0, (int) daysBetween) + "");
			response.setSlaExpired(estimated.isBefore(now));
		} else {
			response.setRemaingSLADays("0");
			response.setSlaExpired(false);
		}

		Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
		Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
		if (isNormalOrder) {
			SalesOrder salesOrder = (SalesOrder) sourceOrder;
			response.setShipmentMode(getShipmentModeOfOrder(shipmentMode,salesOrder.getStoreId()));
			response.setCallToActionFlag(orderResponse.getCallToActionFlag());
			response.setCanceledAt(orderResponse.getCanceledAt());
			response.setDeliveredAt(convertTimezone(salesOrder.getDeliveredAt(), storeId));
			// common population
			populateResponseCommon(
					response,
					salesOrder.getStatus(),
					statusStatesMap,
					statusColorsMap,
					salesOrder.getGrandTotal(),
					salesOrder.getBaseGrandTotal(),
					salesOrder.getDiscountAmount(),
					salesOrder.getSubtotal(),
					salesOrder.getOrderCurrencyCode(),
					salesOrder.getTotalItemCount(),
					salesOrder.getShippingAmount(),
					salesOrder.getCashOnDeliveryFee()
			);
		} else {
			SplitSalesOrder splitSalesOrder = (SplitSalesOrder) sourceOrder;
			// -------- SPLIT SELLER ORDER OVERALL STATUS LOGIC --------
			String overallStatus = resolveOverallStatusFromSplitSellerOrders(
					splitSalesOrder.getEntityId(),splitSalesOrder.getStatus(),splitSalesOrder.getShipmentMode()
			);
			//set split order id if only split order is being converted, for normal order it will be set as null
			response.setSplitOrderId(entityId);
			response.setStatus(overallStatus);
			SalesOrderStatusLabel effectiveLabel = label;
			if (!overallStatus.equalsIgnoreCase(status)) {
				effectiveLabel = getStatusLabelForStore(overallStatus, splitSalesOrder.getStoreId());
			}
			response.setStatusLabel(null != effectiveLabel ? effectiveLabel.getLabel() :overallStatus);
			// Set final status based on split seller orders
			response.setShipmentMode(getShipmentModeOfOrder(shipmentMode,splitSalesOrder.getStoreId()));
			addOtherinformationV2(splitSalesOrder, orderResponse, orderDetails, productsFromMulin);
			response.setSubtotal(orderResponse.getSubtotal());
			response.setDiscountAmount(orderResponse.getDiscountAmount());
			configureOrderTrackingV2(splitSalesOrder, orderResponse);
			setSplitOrderStatusCallToActionFlag(orderResponse, splitSalesOrder, overallStatus);
			response.setCallToActionFlag(orderResponse.getCallToActionFlag());
			addSplitOrderCanceldateAndRTO(splitSalesOrder, response);
			response.setDeliveredAt(convertTimezone(splitSalesOrder.getDeliveredAt(), storeId));
			response.setRto(orderResponse.isRto());
			response.setRtoStatus(orderResponse.getRtoStatus());
			response.setRtoRefundAt(orderResponse.getRtoRefundAt());
			response.setRtoRefundAmount(orderResponse.getRtoRefundAmount());
			// common population
			populateResponseCommon(
					response,
					overallStatus,
					statusStatesMap,
					statusColorsMap,
					splitSalesOrder.getGrandTotal(),
					splitSalesOrder.getBaseGrandTotal(),
					splitSalesOrder.getDiscountAmount(),
					splitSalesOrder.getSubtotal(),
					splitSalesOrder.getOrderCurrencyCode(),
					splitSalesOrder.getTotalItemCount(),
					splitSalesOrder.getShippingAmount(),
					splitSalesOrder.getCashOnDeliveryFee()
			);
		}

		response.setProducts(orderResponse.getProducts());
		response.setCancelProducts(orderResponse.getCancelProducts());
		response.setTrackings(orderResponse.getTrackings());
		response.setShippingUrl(orderResponse.getShippingUrl());

		return response;
	}

	/**
	 * Reusable common mapper for SalesOrder / SplitSalesOrder -> response fields.
	 */
	private void populateResponseCommon(
			SplitOrderResponse response,
			String status,
			Map<String, Integer> statusStatesMap,
			Map<String, Integer> statusColorsMap,
			BigDecimal grandTotal,
			BigDecimal baseGrandTotal,
			BigDecimal discountAmount,
			BigDecimal subtotal,
			String currency,
			Integer totalItemCount,
			BigDecimal shippingAmount,
			BigDecimal cashOnDeliveryFee
	) {
		// status step values (null-safe; uses getOrDefault in Java 8)
		if (statusStatesMap != null) {
			Integer stepValue = OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS.equalsIgnoreCase(status)
					? statusStatesMap.get(OrderConstants.CLOSED_ORDER_STATUS)
					: statusStatesMap.get(status);
			response.setStatusStepValue(stepValue != null ? stepValue : 0);
		}
		if (statusColorsMap != null) {
			Integer colorValue = OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS.equalsIgnoreCase(status)
					? statusColorsMap.get(OrderConstants.CLOSED_ORDER_STATUS)
					: statusColorsMap.get(status);
			response.setStatusColorStepValue(colorValue != null ? colorValue : 0);
		}

		// monetary & count fields (null-safe)
		response.setGrandTotal(safeToString(grandTotal));
		response.setBaseGrandTotal(safeToString(baseGrandTotal));
		response.setDiscountAmount(safeToString(discountAmount));
		response.setSubtotal(safeToString(subtotal));
		response.setCurrency(currency);
		String itemCountStr = String.valueOf(Optional.ofNullable(totalItemCount).orElse(0));
		response.setItemCount(itemCountStr);
		response.setOrderShipCount(itemCountStr); // same as your original code
		response.setShippingAmount(safeToString(shippingAmount));

		// COD charges via your existing helper
		response.setCodCharges(parseNullStr(cashOnDeliveryFee));
	}


	private String safeToString(BigDecimal val) {
		return (val != null) ? val.toPlainString() : "0";
	}

	private void setFullyOrPartialCancelled(SalesOrder order, OrderResponse resp) {
		/** Set fully cancelled and partial cancelled flag */
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsUnfulfilmentOrder()) {

			if(order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(1)) {
				resp.setPartialCancelled(true);

			} else if(order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(2)) {
				resp.setFullyCancelled(true);
			}
		}
	}

	public void addOtherinformation(SalesOrder order, OrderResponse resp, Boolean orderDetails,
									 Map<String, ProductResponseBody> productsFromMulin) {

		BigDecimal subTotal = new BigDecimal(0);
		BigDecimal totalBagDiscount = new BigDecimal(0);
		resp.setProducts(new ArrayList<>());
		resp.setCancelProducts(new ArrayList<>());

		for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {

			if (salesOrderItem.getParentOrderItem() == null) {

				OrderItem productDTO = new OrderItem();

				/**
				 * This flag is for a reason that if request is for order list, a lot of
				 * information is not required which is required for order detail api
				 **/
				if (orderDetails) {
					productDTO.setSku(salesOrderItem.getSku());
					productDTO.setName(salesOrderItem.getName());
					productDTO.setParentOrderItemId(parseNullStr(salesOrderItem.getItemId()));
					if(null != salesOrderItem.getGiftMessageAvailable() && salesOrderItem.getGiftMessageAvailable().equals(1) ) {
						productDTO.setGiftProduct(true);

					}
					addQtydetails(salesOrderItem,productDTO);

					order.getSalesOrderItem().stream()
							.filter(e -> e.getParentOrderItem() != null && e.getParentOrderItem().getItemId() != null
									&& e.getParentOrderItem().getItemId().equals(salesOrderItem.getItemId()))
							.findFirst().ifPresent(childOrderItem -> {
								QuantityReturned quantityReturned = omsorderentityConverter.getSalesQtyReturned(childOrderItem.getItemId(),true);
//								productDTO.setQtyReturned(getQtyReturned(childOrderItem.getItemId()));
								if(childOrderItem.getQtyRefunded().intValue() > 0) {
									productDTO.setQtyReturned(parseNullStr(childOrderItem.getQtyRefunded()));
								}else {
									productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
								}
								productDTO.setQtyReturnedInProcess(parseNullStr(quantityReturned.getQtyReturnedInProcess()));
							});
					BigDecimal totalSellableprice = null;

					if (salesOrderItem.getPriceInclTax() != null && salesOrderItem.getOriginalPrice() != null) {

						totalSellableprice = salesOrderItem.getPriceInclTax();

						BigDecimal productDiscountPrice = new BigDecimal(0);
						BigDecimal bagDiscountPrice = new BigDecimal(0);
						BigDecimal indivisualProductDiscountPrice = new BigDecimal(0);

						if (CollectionUtils.isNotEmpty(salesOrderItem.getSubSalesOrderItem())) {
							productDiscountPrice = salesOrderItem.getSubSalesOrderItem().stream()
									.map(x -> x.getDiscount()).reduce(BigDecimal.ZERO, BigDecimal::add);

							indivisualProductDiscountPrice = salesOrderItem.getSubSalesOrderItem().stream()
									.map(x -> x.getDiscount()).reduce(BigDecimal.ZERO, BigDecimal::add)
									.divide(salesOrderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP);
						}

						BigDecimal discountedSellablePrice = totalSellableprice
								.subtract(indivisualProductDiscountPrice);
						totalSellableprice = totalSellableprice.subtract(productDiscountPrice);

						if (null != salesOrderItem.getOriginalPrice() && orderDetails
								&& null != salesOrderItem.getQtyOrdered()) {

							bagDiscountPrice = salesOrderItem.getOriginalPrice()
									.multiply(salesOrderItem.getQtyOrdered())
									.subtract(salesOrderItem.getPriceInclTax().multiply(salesOrderItem.getQtyOrdered()))
									.setScale(4, RoundingMode.HALF_UP);
							if (bagDiscountPrice.signum() <= 0) {

								bagDiscountPrice = new BigDecimal(0);
							}
						}

						productDTO.setPrice(discountedSellablePrice.toString());

						if (null != salesOrderItem.getOriginalPrice()) {

							productDTO.setOriginalPrice(salesOrderItem.getOriginalPrice().toString());

						}

						double discountAmount = salesOrderItem.getOriginalPrice().subtract(totalSellableprice)
								.doubleValue();

						configureDiscountamount(productDTO,salesOrderItem,discountAmount,totalSellableprice,discountedSellablePrice);
						subTotal = subTotal
								.add(salesOrderItem.getOriginalPrice().multiply(salesOrderItem.getQtyOrdered()));
						totalBagDiscount = totalBagDiscount.add(bagDiscountPrice);

					}

				}

				boolean returnCategoryRestriction = false;
				for (Map.Entry<String,ProductResponseBody> entry : productsFromMulin.entrySet()) {
					ProductResponseBody productDetailsFromMulin = entry.getValue();
					Variant variant = productDetailsFromMulin.getVariants().stream()
							.filter(e -> Objects.nonNull(e.getSku()) && e.getSku().equals(salesOrderItem.getSku()))
							.findAny()
							.orElse(null);
					if(variant != null) {
						if(variant.getSizeLabels() !=null) productDTO.setSize(parseNullStr(variant.getSizeLabels().getEn()));

						returnCategoryRestriction = !productDetailsFromMulin.getIsReturnApplicable();
						// productDTO.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));

						if(productDetailsFromMulin.getMediaGallery() != null
								&& !productDetailsFromMulin.getMediaGallery().isEmpty()) {
							GalleryItem galleryItem = productDetailsFromMulin.getMediaGallery().get(0);
							if(galleryItem != null) productDTO.setImage(galleryItem.getValue());
						}
					}
				}


				Date flagFixDate = Constants.orderCredentials.getOrderDetails().getReturnFlagFixDate(); // fetching golive date
				// from consul
				Timestamp orderCreationDate = salesOrderItem.getCreatedAt(); // fetching order creation date

//				returnCategoryRestriction value is overridden here base on snapshot value
				// if order creation date is on or after golive date
				if (flagFixDate != null && ObjectUtils.isNotEmpty(salesOrderItem.getReturnable()) && (orderCreationDate.compareTo(flagFixDate) >= 0)) {
					productDTO.setReturnCategoryRestriction(salesOrderItem.getReturnable() == 1 ? "false" : "true");
				} // if getReturnable is Empty
				else if (ObjectUtils.isEmpty(salesOrderItem.getReturnable())) {
					productDTO.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));
				} else {
					productDTO.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));
				}

				productDTO.setAvailableNow(true);
//				productDTO.setParentProductId(parseNullStr(salesOrderItem.getProductId()));
				productDTO.setParentProductId(parseNullStr(salesOrderItem.getSku()));

				productDTO.setParentSku(parseNullStr(salesOrderItem.getParentSku()));

				if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsUnfulfilmentOrder()) {

					// Check if the order is partially unfulfilled
					if(order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(2)) {

					}
					if(productDTO.getQty() != null && Double.parseDouble(productDTO.getQty()) > 0
							&& productDTO.getQtyCanceled() != null && Double.parseDouble(productDTO.getQtyCanceled()) > 0) {

						setAmountRefundAndInitiatedDate(order, resp, productDTO, salesOrderItem);
					}
				}

				resp.getProducts().add(productDTO);

				addCancelProducts(order, resp, productDTO, salesOrderItem);
			}
		}

		addtotaldisAmount(totalBagDiscount,resp,subTotal);



	}

	public void addOtherinformationV2(SplitSalesOrder splitSalesOrder, OrderResponse resp, Boolean orderDetails,
									 Map<String, ProductResponseBody> productsFromMulin) {

		BigDecimal subTotal = new BigDecimal(0);
		BigDecimal totalBagDiscount = new BigDecimal(0);
		resp.setProducts(new ArrayList<>());
		resp.setCancelProducts(new ArrayList<>());

		for (SplitSalesOrderItem splitSalesOrderItem : splitSalesOrder.getSplitSalesOrderItems()) {
			SalesOrderItem salesOrderItem = splitSalesOrderItem.getSalesOrderItem();
			if (null != salesOrderItem && salesOrderItem.getParentOrderItem() == null) {

				OrderItem productDTO = new OrderItem();

				/**
				 * This flag is for a reason that if request is for order list, a lot of
				 * information is not required which is required for order detail api
				 **/
				if (orderDetails) {
					productDTO.setSku(splitSalesOrderItem.getSku());
					productDTO.setName(splitSalesOrderItem.getName());
					productDTO.setParentOrderItemId(parseNullStr(splitSalesOrderItem.getItemId()));
					if(null != splitSalesOrderItem.getGiftMessageAvailable() && splitSalesOrderItem.getGiftMessageAvailable().equals(1) ) {
						productDTO.setGiftProduct(true);

					}
					addSplitOrderQtydetails(splitSalesOrderItem,productDTO);

					QuantityReturned quantityReturned = omsorderentityConverter.getSalesQtyReturned(salesOrderItem.getItemId(),true);
					if(salesOrderItem.getQtyRefunded().intValue() > 0) {
						productDTO.setQtyReturned(parseNullStr(salesOrderItem.getQtyRefunded()));
					}else {
						productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
					}
					productDTO.setQtyReturnedInProcess(parseNullStr(quantityReturned.getQtyReturnedInProcess()));

					BigDecimal totalSellableprice = null;

					if (splitSalesOrderItem.getPriceInclTax() != null && splitSalesOrderItem.getOriginalPrice() != null) {

						totalSellableprice = splitSalesOrderItem.getPriceInclTax();

						BigDecimal productDiscountPrice = new BigDecimal(0);
						BigDecimal bagDiscountPrice = new BigDecimal(0);
						BigDecimal indivisualProductDiscountPrice = new BigDecimal(0);

						if (CollectionUtils.isNotEmpty(splitSalesOrderItem.getSplitSubSalesOrderItem())) {
							productDiscountPrice = splitSalesOrderItem.getSplitSubSalesOrderItem().stream()
									.map(x -> x.getDiscount()).reduce(BigDecimal.ZERO, BigDecimal::add);

							indivisualProductDiscountPrice = splitSalesOrderItem.getSplitSubSalesOrderItem().stream()
									.map(x -> x.getDiscount()).reduce(BigDecimal.ZERO, BigDecimal::add)
									.divide(splitSalesOrderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP);
						}

						BigDecimal discountedSellablePrice = totalSellableprice
								.subtract(indivisualProductDiscountPrice);
						totalSellableprice = totalSellableprice.subtract(productDiscountPrice);

						if (null != splitSalesOrderItem.getOriginalPrice() && orderDetails
								&& null != splitSalesOrderItem.getQtyOrdered()) {

							bagDiscountPrice = splitSalesOrderItem.getOriginalPrice()
									.multiply(splitSalesOrderItem.getQtyOrdered())
									.subtract(splitSalesOrderItem.getPriceInclTax().multiply(splitSalesOrderItem.getQtyOrdered()))
									.setScale(4, RoundingMode.HALF_UP);
							if (bagDiscountPrice.signum() <= 0) {

								bagDiscountPrice = new BigDecimal(0);
							}
						}

						productDTO.setPrice(discountedSellablePrice.toString());

						if (null != splitSalesOrderItem.getOriginalPrice()) {

							productDTO.setOriginalPrice(splitSalesOrderItem.getOriginalPrice().toString());

						}

						double discountAmount = splitSalesOrderItem.getOriginalPrice().subtract(totalSellableprice)
								.doubleValue();

						configureDiscountamount(productDTO,salesOrderItem,discountAmount,totalSellableprice,discountedSellablePrice);
						subTotal = subTotal
								.add(splitSalesOrderItem.getOriginalPrice().multiply(splitSalesOrderItem.getQtyOrdered()));
						totalBagDiscount = totalBagDiscount.add(bagDiscountPrice);

					}

				}

				boolean returnCategoryRestriction = false;
				Map<String, Variant> skuToVariantMap = new HashMap<>();
				Map<String, Boolean> skuReturnRestrictionMap = new HashMap<>();
				Map<String, String> skuToImageMap = new HashMap<>();

				for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
					ProductResponseBody product = entry.getValue();

					for (Variant variant : product.getVariants()) {
						if (variant.getSku() != null) {
							skuToVariantMap.put(variant.getSku(), variant);
							skuReturnRestrictionMap.put(variant.getSku(), !product.getIsReturnApplicable());

							if (product.getMediaGallery() != null && !product.getMediaGallery().isEmpty()) {
								GalleryItem galleryItem = product.getMediaGallery().get(0);
								if (galleryItem != null) {
									skuToImageMap.put(variant.getSku(), galleryItem.getValue());
								}
							}
						}
					}
				}
				String sku = splitSalesOrderItem.getSku();
				Variant variant = skuToVariantMap.get(sku);
				if (variant != null) {
					if (variant.getSizeLabels() != null) {
						productDTO.setSize(parseNullStr(variant.getSizeLabels().getEn()));
					}
					Boolean returnRestriction = skuReturnRestrictionMap.get(sku);
					if (returnRestriction != null) {
						returnCategoryRestriction = returnRestriction;
						productDTO.setReturnCategoryRestriction(parseNullStr(returnRestriction));
					}
					String image = skuToImageMap.get(sku);
					if (image != null) {
						productDTO.setImage(image);
					}
				}

				Date flagFixDate = Constants.orderCredentials.getOrderDetails().getReturnFlagFixDate(); // fetching golive date
				// from consul
				Timestamp orderCreationDate = splitSalesOrderItem.getCreatedAt(); // fetching order creation date

//				returnCategoryRestriction value is overridden here base on snapshot value
				// if order creation date is on or after golive date
				if (flagFixDate != null && ObjectUtils.isNotEmpty(splitSalesOrderItem.getReturnable()) && (orderCreationDate.compareTo(flagFixDate) >= 0)) {
					productDTO.setReturnCategoryRestriction(splitSalesOrderItem.getReturnable() == 1 ? "false" : "true");
				} // if getReturnable is Empty
				else if (ObjectUtils.isEmpty(splitSalesOrderItem.getReturnable())) {
					productDTO.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));
				} else {
					productDTO.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));
				}

				productDTO.setAvailableNow(true);
//				productDTO.setParentProductId(parseNullStr(salesOrderItem.getProductId()));
				productDTO.setParentProductId(parseNullStr(splitSalesOrderItem.getSku()));

				productDTO.setParentSku(parseNullStr(splitSalesOrderItem.getParentSku()));

				if(null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getIsUnfulfilmentOrder()) {

					if(productDTO.getQty() != null && Double.parseDouble(productDTO.getQty()) > 0
							&& productDTO.getQtyCanceled() != null && Double.parseDouble(productDTO.getQtyCanceled()) > 0) {

						setAmountRefundAndInitiatedDateV2(splitSalesOrder, resp, productDTO, salesOrderItem);
					}
				}

				resp.getProducts().add(productDTO);

				addCancelProductsV2(splitSalesOrder, resp, productDTO, salesOrderItem);
			}
		}

		addtotaldisAmount(totalBagDiscount,resp,subTotal);
	}

	private void addCancelProducts(SalesOrder order, OrderResponse resp, OrderItem productDTO, SalesOrderItem salesOrderItem) {
		/** Set amount and date for unfulfilled products to show on UI */
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsUnfulfilmentOrder()) {

			// Check if the order is partially unfulfilled
			if(order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(1)) {

				if(productDTO.getQty() != null && Double.parseDouble(productDTO.getQty()) > 0
						&& productDTO.getQtyCanceled() != null && Double.parseDouble(productDTO.getQtyCanceled()) > 0) {

					setAmountRefundAndInitiatedDate(order, resp, productDTO, salesOrderItem);

					resp.getCancelProducts().add(productDTO);
				}
			}
		}
	}

	private void addCancelProductsV2(SplitSalesOrder splitSalesOrder, OrderResponse resp, OrderItem productDTO, SalesOrderItem salesOrderItem) {
		/** Set amount and date for unfulfilled products to show on UI */
		if(null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getIsUnfulfilmentOrder()) {

			// Check if the order is partially unfulfilled
			if(splitSalesOrder.getSplitSubSalesOrder().getIsUnfulfilmentOrder().equals(1)) {

				if(productDTO.getQty() != null && Double.parseDouble(productDTO.getQty()) > 0
						&& productDTO.getQtyCanceled() != null && Double.parseDouble(productDTO.getQtyCanceled()) > 0) {

					setAmountRefundAndInitiatedDateV2(splitSalesOrder, resp, productDTO, salesOrderItem);

					resp.getCancelProducts().add(productDTO);
				}
			}
		}
	}

	private void setAmountRefundAndInitiatedDate(SalesOrder order, OrderResponse resp, OrderItem productDTO,
												 SalesOrderItem salesOrderItem) {
		if(null != salesOrderItem.getDiscountAmount() && null != salesOrderItem.getPriceInclTax()) {

			BigDecimal discount = salesOrderItem.getDiscountAmount().divide(salesOrderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP);

			BigDecimal productAmtRefunded = salesOrderItem.getPriceInclTax()
					.subtract(discount)
					.multiply(salesOrderItem.getQtyCanceled())
					.setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

			productDTO.setAmountRefund(String.valueOf(productAmtRefunded));
		}

		// List<SalesCreditmemo> salesCreditMemoList = salesCreditmemoRepository.findByOrderId(order.getEntityId());
		List<SalesCreditmemo> salesCreditMemoList = orderHelper.getSalesCreditMemoList(order.getEntityId());

		if(salesCreditMemoList != null && !salesCreditMemoList.isEmpty() && salesCreditMemoList.get(0) != null) {

	        BigDecimal styliCredit = salesCreditMemoList.stream().map(SalesCreditmemo::getAmstorecreditAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

	        resp.setStyliCreditRefund(styliCredit.setScale(4, RoundingMode.HALF_UP).toString());

	        BigDecimal totalAmtRefund = salesCreditMemoList.stream().map(SalesCreditmemo::getGrandTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

	        resp.setTotalAmountRefund(totalAmtRefund.setScale(4, RoundingMode.HALF_UP).toString());

	        productDTO.setRefundInitiatedOn(convertTimezone(salesCreditMemoList.get(0).getCreatedAt(), order.getStoreId()));
	    }
	}

	private void setAmountRefundAndInitiatedDateV2(SplitSalesOrder splitSalesOrder, OrderResponse resp, OrderItem productDTO,
												 SalesOrderItem salesOrderItem) {
		if(null != salesOrderItem.getDiscountAmount() && null != salesOrderItem.getPriceInclTax()) {

			BigDecimal discount = salesOrderItem.getDiscountAmount().divide(salesOrderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP);

			BigDecimal productAmtRefunded = salesOrderItem.getPriceInclTax()
					.subtract(discount)
					.multiply(salesOrderItem.getQtyCanceled())
					.setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

			productDTO.setAmountRefund(String.valueOf(productAmtRefunded));
		}

		// List<SalesCreditmemo> salesCreditMemoList = salesCreditmemoRepository.findByOrderId(splitSalesOrder.getEntityId());
		List<SalesCreditmemo> salesCreditMemoList = orderHelper.getSalesCreditMemoList(splitSalesOrder.getEntityId());
		if(salesCreditMemoList != null && !salesCreditMemoList.isEmpty() && salesCreditMemoList.get(0) != null) {

			BigDecimal styliCredit = salesCreditMemoList.stream().map(SalesCreditmemo::getAmstorecreditAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

			resp.setStyliCreditRefund(styliCredit.setScale(4, RoundingMode.HALF_UP).toString());

			BigDecimal totalAmtRefund = salesCreditMemoList.stream().map(SalesCreditmemo::getGrandTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

			resp.setTotalAmountRefund(totalAmtRefund.setScale(4, RoundingMode.HALF_UP).toString());

			productDTO.setRefundInitiatedOn(convertTimezone(salesCreditMemoList.get(0).getCreatedAt(), splitSalesOrder.getStoreId()));
		}
	}
	
	

	private void addQtydetails(SalesOrderItem salesOrderItem, OrderItem productDTO) {

		if (salesOrderItem.getQtyOrdered() != null) {
			productDTO.setQty(salesOrderItem.getQtyOrdered().toString());
		}
		if (salesOrderItem.getQtyCanceled() != null) {
			productDTO.setQtyCanceled(salesOrderItem.getQtyCanceled().toString());
		}


	}

	private void addSplitOrderQtydetails(SplitSalesOrderItem splitSalesOrderItem, OrderItem productDTO) {

		if (splitSalesOrderItem.getQtyOrdered() != null) {
			productDTO.setQty(splitSalesOrderItem.getQtyOrdered().toString());
		}
		if (splitSalesOrderItem.getQtyCanceled() != null) {
			productDTO.setQtyCanceled(splitSalesOrderItem.getQtyCanceled().toString());
		}
	}

	private void addtotaldisAmount(BigDecimal totalBagDiscount, OrderResponse resp
			, BigDecimal subTotal) {

		if (totalBagDiscount.doubleValue() < 0) {
			resp.setDiscountAmount("0.0");
		} else {
			resp.setDiscountAmount(parseNullStr(totalBagDiscount.doubleValue()));
		}

		resp.setSubtotal(parseNullStr(subTotal.setScale(2, RoundingMode.HALF_UP)));

	}

	private void configureDiscountamount(OrderItem productDTO, SalesOrderItem salesOrderItem, double discountAmount,
										 BigDecimal totalSellableprice,
										 BigDecimal discountedSellablePrice) {

		/** In case of tax inclusive prices and no special price. case - 3000000021 */
		if (discountAmount < 0) {
			productDTO.setDiscount("0.0");
		} else if (discountAmount == 0.0
				&& !salesOrderItem.getOriginalPrice().equals(salesOrderItem.getPriceInclTax())) {

			productDTO.setDiscount("100.00");

		} else if (salesOrderItem.getOriginalPrice().equals(totalSellableprice)) {

			productDTO.setDiscount("0.00");

		} else {
			DecimalFormat df = new DecimalFormat("#.##");

			BigDecimal totalDiscount = salesOrderItem.getOriginalPrice()
					.multiply(salesOrderItem.getQtyOrdered())
					.subtract(discountedSellablePrice.multiply(salesOrderItem.getQtyOrdered()));

			double discount = Double
					.parseDouble(
							df.format(totalDiscount.doubleValue()
									/ Double.parseDouble(salesOrderItem.getOriginalPrice()
									.multiply(salesOrderItem.getQtyOrdered()).toString())
									* 100));
			productDTO.setDiscount(parseNullStr(discount));
		}
	}


	private void addCoupondetails(SalesOrder order, OrderResponse resp, ObjectMapper mapper) {

		if (null != order.getSubSalesOrder()) {
			resp.setAutoCouponApplied(order.getSubSalesOrder().getExternalAutoCouponCode());
			if (null != order.getSubSalesOrder().getExternalAutoCouponAmount())
				resp.setAutoCouponDiscount(parseNullStr(order.getSubSalesOrder().getExternalAutoCouponAmount()));
			if (StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData())) {

				List<DiscountData> discountDataList = null;

				try {
					discountDataList = Arrays
							.asList(mapper.readValue(order.getSubSalesOrder().getDiscountData(), DiscountData[].class));
				} catch (JsonMappingException e) {
					LOGGER.error("exception occoured during convert offer string to object" + e.getMessage());
				} catch (JsonProcessingException e) {
					LOGGER.error("exception occoured during convert offer string to object" + e.getMessage());
				}
				resp.setDiscountData(discountDataList);
			}
		}

		BigDecimal couponDiscount = order.getDiscountAmount();

		if(null != couponDiscount && null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getExternalAutoCouponAmount()) {

			couponDiscount = couponDiscount.abs().
					subtract(order.getSubSalesOrder().getExternalAutoCouponAmount());
			resp.setCouponDiscountAmount(parseNullStr(couponDiscount));
		}

	}



	private void addPaymentinfo( ObjectMapper mapper, OrderResponse resp,SalesOrderPayment salesOrderPayment,SalesOrder order) {

		String paymentInformation = null;

		if (salesOrderPayment != null) {
			paymentInformation = salesOrderPayment.getAdditionalInformation();
			resp.setPaymentMethod(salesOrderPayment.getMethod());
		}

		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInformation,
						SalesOrderPaymentInformation.class);
				resp.setCardNumber(parseNullStr(salesOrderPaymentInformation.getCardNumber()));
				resp.setPaymentOption(parseNullStr(salesOrderPaymentInformation.getPaymentOption()));
				resp.setPaymentResponseCode(parseNullStr(salesOrderPaymentInformation.getResponseCode()));
				resp.setPaymentResponseMessage(parseNullStr(salesOrderPaymentInformation.getResponseMessage()));
			} catch (IOException e) {
				LOGGER.error("jackson mapper error!");
			}
		}
		if(Objects.nonNull(salesOrderPayment)) {
			if (salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue())
					|| salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.TABBY_PAYLATER.getValue())) {

				try {
					paymentInformation = salesOrderPayment.getAdditionalInformation();
					if (paymentInformation != null) {
						TabbyPayment tabbyPayment = mapper.readValue(paymentInformation, TabbyPayment.class);
						resp.setCardNumber(OrderConstants.PAYMENT_TYPE);
						resp.setPaymentResponseMessage(tabbyPayment.getStatus());
						resp.setPaymentOption(parseNullStr(OrderConstants.PAYMENT_TYPE));
						resp.setCardNumber(parseNullStr(OrderConstants.PAYMENT_TYPE));
					}
				} catch (Exception e) {
					LOGGER.error("Error in Get order details from Tabby For OMS!");
				}
			} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(salesOrderPayment.getMethod())) {
				try {
					paymentInformation = salesOrderPayment.getAdditionalInformation();
					if (paymentInformation != null) {
						CashfreePaymentDTO paymetDTO = mapper.readValue(paymentInformation, CashfreePaymentDTO.class);
						resp.setCardNumber(paymetDTO.getCfOrderId());
						resp.setPaymentResponseMessage(paymetDTO.getMessage());
						resp.setPaymentOption(paymetDTO.getPaymentType());
					}
				} catch (Exception e) {
					LOGGER.error("Error in Set order details from cashfree in view API. " + e);
				}
			}
		}
	}

	private void addCanceldates(SalesOrder order, OrderResponse resp) {

		if (order.getStatus().equalsIgnoreCase(OrderConstants.CANCELED_ORDER_STATE)) {
			List<SalesOrderStatusHistory> histories = salesOrderStatusHistoryRepository
					.findByParentIdAndStatus(order.getEntityId(), OrderConstants.CANCELED_ORDER_STATE);
			if (CollectionUtils.isNotEmpty(histories)) {
				SalesOrderStatusHistory history = histories.get(0);
				resp.setCanceledAt(parseNullStr(history.getCreatedAt()));
			}
		}

		if (order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
			List<SalesOrderStatusHistory> histories = salesOrderStatusHistoryRepository
					.findByParentIdAndStatus(order.getEntityId(), OrderConstants.CLOSED_ORDER_STATUS);
			if (CollectionUtils.isNotEmpty(histories)) {
				SalesOrderStatusHistory history = histories.get(0);
				resp.setCanceledAt(parseNullStr(history.getCreatedAt()));
			}
		}

	}

	public void addOrderAddress(SalesOrder order, OrderResponse resp, boolean orderDetails) {

		LinkedHashMap<String, AddressChangeAttributes> addressChangeConfig = Constants.orderCredentials.getAddressChangeFlagMap();
		List<SalesShipmentTrack> shipmentTracks = shipmentTrackerRepository.findByOrderId(order.getEntityId());
		SalesShipmentTrack shipmentTrack = (null != shipmentTracks && !shipmentTracks.isEmpty()) ? shipmentTracks.get(0) : null;
		Boolean isAddressChangeEligible = (null == shipmentTrack) || null == shipmentTrack.getTrackNumber();
		List<SalesOrderStatusHistory> salesOrderStatusHistory =  salesOrderStatusHistoryRepo.findByParentId(order.getEntityId());
		List<AddressChangeHistory> addressLogs = addressChangeHistoryRepository.findByOrderId(order.getEntityId());
		boolean terminalStatus = false;
		boolean isCpEligible = false;
		boolean orderClosed = OrderConstants.CLOSED_ORDER_STATUS.equalsIgnoreCase(order.getStatus());
		boolean paymentFailed = OrderConstants.FAILED_ORDER_STATUS.equalsIgnoreCase(order.getStatus());
		boolean addressUpdateLimitExceeded = false;

		if (orderDetails) {

			for (SalesOrderAddress shippingAddress : order.getSalesOrderAddress()) {
				if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {
					AddressChangeAttributes addressChangeAttributes = addressChangeConfig.get(shippingAddress.getCountryId());
					terminalStatus = (null != addressChangeAttributes && salesOrderStatusHistory.stream().filter(e -> null!=e.getFinalStatus() && e.getFinalStatus().equalsIgnoreCase(addressChangeAttributes.getTerminalStatus())).count()>=1);
					isCpEligible = (addressChangeAttributes!=null && (null != shipmentTrack && null!=shipmentTrack.getTitle()) && addressChangeAttributes.getServiceableLogisticPartner().contains(shipmentTrack.getTitle().split(" ")[0]));
					addressUpdateLimitExceeded = addressChangeAttributes != null && addressChangeAttributes.getAddressChangeLimit() != null && addressLogs.size() >= addressChangeAttributes.getAddressChangeLimit();

					OrderAddress orderAddress = new OrderAddress();
					orderAddress.setAddressId(shippingAddress.getEntityId());
					orderAddress.setCustomerAddressId(shippingAddress.getCustomerAddressId());
					orderAddress.setIsAddressChangeEligible(!addressUpdateLimitExceeded && !orderClosed && !terminalStatus && (isAddressChangeEligible || isCpEligible) && !paymentFailed);
					orderAddress.setIsAddressChangeEnabled(addressChangeAttributes!=null && addressChangeAttributes.getFlag());
					orderAddress.setFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setLastName(parseNullStr(shippingAddress.getLastname()));
					orderAddress.setMobileNumber(parseNullStr(shippingAddress.getTelephone()));
					orderAddress.setCity(parseNullStr(shippingAddress.getCity()));
					orderAddress.setStreetAddress(parseNullStr(shippingAddress.getStreet()));
					orderAddress.setCountry(parseNullStr(shippingAddress.getCountryId()));
					orderAddress.setRegion(parseNullStr(shippingAddress.getRegion()));
					orderAddress.setPostCode(parseNullStr(shippingAddress.getPostcode()));
					orderAddress.setRegionId(shippingAddress.getRegionId());

					orderAddress.setArea(parseNullStr(shippingAddress.getArea()));
					orderAddress.setLandmark(parseNullStr(shippingAddress.getNearestLandmark()));
					orderAddress.setLatitude(shippingAddress.getLatitude());
					orderAddress.setLongitude(shippingAddress.getLongitude());
                    orderAddress.setKsaAddressComplaint(shippingAddress.getKsaAddressComplaint());
                    orderAddress.setUnitNumber(shippingAddress.getUnitNumber());
                    orderAddress.setShortAddress(shippingAddress.getShortAddress());
                    orderAddress.setPostalCode(shippingAddress.getPostalCode());
                    orderAddress.setBuildingNumber(shippingAddress.getBuildingNumber());
					resp.setShippingAddress(orderAddress);
				}
			}

			List<String> invoices = new ArrayList<>();
			if (CollectionUtils.isNotEmpty(order.getSalesInvoices())) {
				for (SalesInvoice invoice : order.getSalesInvoices()) {
					if (invoice.getIncrementId() != null) {
						String encodeValue = null;
						if(null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
							encodeValue = order.getEntityId().toString().concat("#").concat(order.getCustomerEmail());
						}else {
							encodeValue = order.getEntityId().toString();
						}
						String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
						String invoiceUrl = Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl() +"/v1/orders/generatePDF/" + encoded;
//						String encodedInvoiceIncrementId = Base64.getEncoder().encodeToString(invoice.getIncrementId().getBytes());
//						String invoiceUrl = magentoBaseUrl + "/invoicepdf/index/index/order/" + encodedInvoiceIncrementId;
						invoices.add(invoiceUrl);
					}
				}
			}
			resp.setInvoices(invoices);
		}

	}

	public void configureOrderTracking(SalesOrder order, OrderResponse resp) {
		List<OrderTracking> orderTrackings = new ArrayList<>();
		for (SalesShipmentTrack salesShipmentTrack : order.getSalesShipmentTrack()) {
			OrderTracking orderTracking = new OrderTracking();
			orderTracking.setCarrier_code(salesShipmentTrack.getCarrierCode());
			orderTracking.setTitle(salesShipmentTrack.getTitle());
			orderTracking.setTrack_number(salesShipmentTrack.getTrackNumber());
			orderTrackings.add(orderTracking);
		}
		resp.setTrackings(orderTrackings);

		if (CollectionUtils.isNotEmpty(orderTrackings)) {
			String trackingNumber = orderTrackings.get(0).getTrack_number();
			List<Stores> stores = Constants.getStoresList();
			Optional<Stores> store = stores.stream()
					.filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
					.findAny();
			String lang = "en";
			if (store.isPresent())
				lang = store.get().getStoreLanguage();

			if (trackingNumber != null) {
				String encryptedAWB = encryptAWB(trackingNumber);
				String joinTrackingUrl = shippingNavikBaseUrl + "/" + lang + "?waybill=" + encryptedAWB;
				resp.setShippingUrl(joinTrackingUrl);
			}
		}
	}

	public void configureOrderTrackingV2(SplitSalesOrder splitSalesOrder, OrderResponse resp) {
		List<OrderTracking> orderTrackings = new ArrayList<>();
		
        String incrementId = splitSalesOrder.getIncrementId();
		List<Stores> stores = Constants.getStoresList();
			Optional<Stores> store = stores.stream()
					.filter(e -> Integer.valueOf(e.getStoreId()).equals(splitSalesOrder.getStoreId()))
					.findAny();
		String lang = "en";
		if (store.isPresent())
			lang = store.get().getStoreLanguage();
		
		for (SalesShipmentTrack salesShipmentTrack : splitSalesOrder.getSalesShipmentTrack()) {
			OrderTracking orderTracking = new OrderTracking();
			orderTracking.setCarrier_code(salesShipmentTrack.getCarrierCode());
			orderTracking.setTitle(salesShipmentTrack.getTitle());
			orderTracking.setTrack_number(salesShipmentTrack.getTrackNumber());
			orderTrackings.add(orderTracking);
		}
		resp.setTrackings(orderTrackings);

		if (CollectionUtils.isNotEmpty(orderTrackings)) {
			String trackingNumber = orderTrackings.get(0).getTrack_number();

			if (trackingNumber != null) {
				String encryptedAWB = encryptAWB(trackingNumber);
				String joinTrackingUrl = shippingNavikBaseUrl + "/" + lang + "?waybill=" + encryptedAWB;
				if(incrementId.contains("-G")) {
					joinTrackingUrl += "&increment_id=" + incrementId;
				}
				LOGGER.info("joinTrackingUrl : " +joinTrackingUrl);
				resp.setShippingUrl(joinTrackingUrl);
				return;
			}
		}
		
		if(incrementId.contains("-G")) {
			String joinTrackingUrl = shippingNavikBaseUrl + "/" + lang + "/?increment_id=" + incrementId;
			LOGGER.info("joinTrackingUrl : " +joinTrackingUrl);
			resp.setShippingUrl(joinTrackingUrl);
		}
	}

	public static String encryptAWB(String waybill) {
		try {
			final String SECRET_KEY = Constants.orderCredentials.getOrderDetails().getSecretkey();
			final String salt = Constants.orderCredentials.getOrderDetails().getSalt();
			int iterations = 10000;
			int keyLength = 128;

			KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), salt.getBytes(), iterations, keyLength);
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			byte[] keyBytes = factory.generateSecret(spec).getEncoded();
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			byte[] encryptedBytes = cipher.doFinal(waybill.getBytes());
			String encryptedAWB = Base64.getEncoder().encodeToString(encryptedBytes);
			// URL-encode the encrypted value
			String encodedAWB = URLEncoder.encode(encryptedAWB, StandardCharsets.UTF_8.toString());
			return encodedAWB;
		} catch (Exception e) {
			LOGGER.info("Exception occurred in encrypting AWB: " + e);
			return null;
		}
	}

	private void addClickpostmessage(String clickpostMessage, OrderResponse resp, ObjectMapper mapper) {


		if (clickpostMessage != null) {
			try {
				ClickpostMessageJSON[] clickpostMessageJSONArray = mapper.readValue(clickpostMessage,
						ClickpostMessageJSON[].class);
				for (ClickpostMessageJSON message : clickpostMessageJSONArray) {
					if (message.getType().equalsIgnoreCase("unDelivered")) {
						resp.setClickpostMessage(parseNullStr(message.getValue()));
					}
				}
			} catch (IOException e) {
				LOGGER.error("exception occoured:"+e.getMessage());
			}
		}

	}

	private String getQtyReturned(Integer orderItemId) {

		BigDecimal qtyReturned = BigDecimal.ZERO;
		List<AmastyRmaRequestItem> amastyRmaRequestItems = amastyRmaRequestItemRepository.findByOrderItemId(orderItemId);
		if (CollectionUtils.isNotEmpty(amastyRmaRequestItems)) {
			for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequestItems) {

				if(null != amastyRmaRequestItem.getAmastyRmaRequest()
						&& null != amastyRmaRequestItem.getAmastyRmaRequest().getShortPickup()
						&& amastyRmaRequestItem.getAmastyRmaRequest().getShortPickup().equals(1)
						&& null != amastyRmaRequestItem.getActualQuantyReturned()) {

					qtyReturned = qtyReturned.add(new BigDecimal(amastyRmaRequestItem.getActualQuantyReturned()));
				}else {
					qtyReturned = qtyReturned.add(amastyRmaRequestItem.getQty());
				}
			}
		}
		return qtyReturned.toString();
	}

	public void setOrderStatusCallToActionFlag(OrderResponse resp, SalesOrder order) {

		switch (order.getStatus()) {
			case OrderConstants.FAILED_ORDER_STATUS:
				if (order.getSubSalesOrder() != null &&
						order.getSubSalesOrder().getExternalQuoteStatus() != null &&
						order.getSubSalesOrder().getExternalQuoteStatus() == 1) {
					resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_RETRY_PAYMENT);
				}
				break;
			case OrderConstants.PENDING_ORDER_STATUS:
			case OrderConstants.PROCESSING_ORDER_STATUS:
				if (isCancelAllowedForOrder(order)) {
					resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_CANCEL);
				}
				break;
			case OrderConstants.PACKED_ORDER_STATUS:
			case OrderConstants.SHIPPED_ORDER_STATUS:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_TRACK_SHIPMENT);
				break;
			case OrderConstants.DELIVERED_ORDER_STATUS:
			case OrderConstants.CANCELED_ORDER_STATE:
			case OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS:
			case OrderConstants.SPLIT_ORDER_CANCELED_ORDER_STATUS:
			case OrderConstants.REFUNDED_ORDER_STATUS:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_REORDER);
				break;
			case OrderConstants.UNDELIVERED_ORDER_STATUS:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_TRACK_SHIPMENT);

				break;
			case OrderConstants.ORDER_STATUS_RTO:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_RESCHEDULE_DELIVERY);

				break;
			default:
				break;
		}

	}

	public String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}

	public String convertTimezone(Timestamp datetime, Integer storeId) {
		return convertTimezone(datetime, storeId, "yyyy-MM-dd HH:mm:ss");
	}

	public String convertTimezone(Timestamp datetime, Integer storeId, String pattern) {
		if (null != datetime) {
			Calendar calendar = Calendar.getInstance();
			Date dateTime = new Date(datetime.getTime());
			calendar.setTime(dateTime);
			SimpleDateFormat sdf = new SimpleDateFormat(pattern);
			if (null != OrderConstants.timeZoneMap.get(storeId)) {
				sdf.setTimeZone(TimeZone.getTimeZone(OrderConstants.timeZoneMap.get(storeId)));
			} else {
				sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			}
			return sdf.format(calendar.getTime());
		} else {
			return null;
		}
	}


	public OrderResponse convertOmsOrderObject(SalesOrderGrid order, boolean orderDetails, ObjectMapper mapper, Integer storeId,
											   Map<String, String> requestHeader, Map<Integer, ProductResponseBody> productsFromMulin) {
		OrderResponse resp = new OrderResponse();

		resp.setOrderId(order.getEntityId());
		resp.setStatus(parseNullStr(order.getStatus()));

		resp.setStoreId(parseNullStr(order.getStoreId()));
		resp.setCustomerId(parseNullStr(order.getCustomerId()));
		resp.setIncrementId(parseNullStr(order.getIncrementId()));
		resp.setCreatedAt(convertTimezone(order.getCreatedAt(),storeId));
		resp.setUpdatedAt(convertTimezone(order.getUpdatedAt(),storeId));

		resp.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		resp.setBaseGrandTotal(parseNullStr(order.getBaseGrandTotal()));
		resp.setPurchaseTotal(parseNullStr(order.getGrandTotal()));
		resp.setStoreName(getStoreName(order.getStoreId()));
		resp.setPaymentMethod(order.getPaymentMethod());
		resp.setAppVersion(order.getAppVersion());
		resp.setBillToName(order.getBillingName());
		resp.setShipToName(order.getShippingName());
		resp.setEmail(parseNullStr(order.getCustomerEmail()));
		resp.setIsSplitOrder(Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder()));

		if(null != order.getToRefund()) {
			resp.setToRefund(order.getToRefund().toString());
		}
		if(order.getSource() == 0) {

			resp.setSource("Web");

		}else if(order.getSource() == 1) {

			resp.setSource("App");
		}else if(order.getSource() == 2) {

			resp.setSource("M-Site");
		}
		else if(order.getSource() == 3) {

			resp.setSource("Admin");
		}

		return resp;
	}

	/**
	 * @param storeId Integer
	 * @return
	 */
	public String getStoreName(Integer storeId) {

		String storeName = null;

		if(storeId.equals(1)) {

			storeName = "KSA English";

		}else if(storeId.equals(3)) {

			storeName = "KSA Arabic";

		}else if(storeId.equals(7)) {

			storeName = "UAE English";
		}else if(storeId.equals(11)) {

			storeName = "UAE Arabic";
		}else if(storeId.equals(12)) {

			storeName = "KWT English";
		}else if(storeId.equals(13)) {

			storeName = "KWT Arabic";

		}else if(storeId.equals(15)) {

			storeName = "QAT English";
		}else if(storeId.equals(17)) {

			storeName = "QAT Arabic";

		}else if(storeId.equals(19)) {

			storeName = "BAH English";
		}else if(storeId.equals(21)) {

			storeName = "BAH Arabic";
		} else if(storeId.equals(23)) {

			storeName = "OMAN English";
		}else if(storeId.equals(25)) {

			storeName = "OMAN Arabic";
		}else if (storeId.equals(51)) {

			storeName = "IN English";
		}

		return storeName;
	}

	public OrderResponse mapOrderInfoForOrderList(SalesOrder order, boolean orderDetails,Integer storeId,
									  Map<String, ProductResponseBody> productsFromMulin,Boolean isSecondRefund) {
		OrderResponse orderResponse = new OrderResponse();
		orderResponse.setOrderId(order.getEntityId());
		orderResponse.setStatus(parseNullStr(order.getStatus()));
		orderResponse.setShippingDescription(parseNullStr(order.getShippingDescription()));
		orderResponse.setStoreId(parseNullStr(order.getStoreId()));
		orderResponse.setCustomerId(parseNullStr(order.getCustomerId()));
		orderResponse.setItemCount(parseNullStr(order.getTotalItemCount()));
		orderResponse.setIncrementId(parseNullStr(order.getIncrementId()));
		orderResponse.setShippingMethod(parseNullStr(order.getShippingMethod()));
		orderResponse.setCreatedAt(convertTimezone(order.getCreatedAt(), storeId));
		orderResponse.setUpdatedAt(convertTimezone(order.getUpdatedAt(), storeId));
		orderResponse.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		orderResponse.setStoreCreditApplied(parseNullStr(order.getAmstorecreditAmount()));
		orderResponse.setIsSecondRefundTagOn(isSecondRefund);
		Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
		if (statusStatesMap != null) {
			Integer stepValue = statusStatesMap.get(order.getStatus());
			orderResponse.setStatusStepValue(stepValue != null ? stepValue : 0);
		}
		Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
		if (statusColorsMap != null) {
			Integer colorValue = statusColorsMap.get(order.getStatus());
			orderResponse.setStatusColorStepValue(colorValue != null ? colorValue : 0);
		}
		SalesOrderStatusLabel label = getStatusLabelForStore(order.getStatus(), storeId);
		if (label != null) {
			orderResponse.setStatusLabel(label.getLabel());
		} else {
			orderResponse.setStatusLabel(order.getStatus());
		}
		if(null != order.getSubSalesOrder() ) {
			orderResponse.setQualifiedPurchase(order.getSubSalesOrder().getQualifiedPurchase());
		}
		setOrderStatusCallToActionFlag(orderResponse, order);
		orderResponse.setDeliveredAt(convertTimezone(order.getDeliveredAt(), storeId));
		if (order.getEstimatedDeliveryTime() != null) {
			SimpleDateFormat sdf = new SimpleDateFormat(OrderConstants.OMS_STANDARD_TIME_FORMAT);
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

			String formattedTime = sdf.format(order.getEstimatedDeliveryTime());
			orderResponse.setEstimatedDeliveryTime(formattedTime);
		} else {
			orderResponse.setEstimatedDeliveryTime(null);
		}
		configureOrderTracking(order, orderResponse);
		addOtherinformation(order, orderResponse, orderDetails, productsFromMulin);
		addCanceldates(order, orderResponse);
		BigDecimal orderTotalValue = order.getGrandTotal();
		orderResponse.setGrandTotal(parseNullStr(orderTotalValue));
		orderResponse.setBaseGrandTotal(parseNullStr(orderTotalValue));

		return orderResponse;
	}

	public void setSplitOrderStatusCallToActionFlag(OrderResponse resp, SplitSalesOrder order,String overallStatus) {
 		// pass split sales order overrall status based on split seller orders, to determine call to action flag
		switch (overallStatus) {
			case OrderConstants.FAILED_ORDER_STATUS:
				if (order.getSplitSubSalesOrder() != null &&
						order.getSplitSubSalesOrder().getExternalQuoteStatus() != null &&
						order.getSplitSubSalesOrder().getExternalQuoteStatus() == 1) {
					resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_RETRY_PAYMENT);
				}
				break;
			case OrderConstants.PENDING_ORDER_STATUS:
			case OrderConstants.PROCESSING_ORDER_STATUS:
				if (isCancelAllowedForSplitOrder(order)) {
					resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_CANCEL);
				} else {
                    resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_TRACK_SHIPMENT);
                }
				break;
			case OrderConstants.PACKED_ORDER_STATUS:
			case OrderConstants.SHIPPED_ORDER_STATUS:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_TRACK_SHIPMENT);
				break;
			case OrderConstants.DELIVERED_ORDER_STATUS:
			case OrderConstants.CANCELED_ORDER_STATE:
			case OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS:
			case OrderConstants.SPLIT_ORDER_CANCELED_ORDER_STATUS:
			case OrderConstants.REFUNDED_ORDER_STATUS:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_REORDER);
				break;
			case OrderConstants.UNDELIVERED_ORDER_STATUS:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_TRACK_SHIPMENT);

				break;
			case OrderConstants.ORDER_STATUS_RTO:
				resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_RESCHEDULE_DELIVERY);

				break;
			default:
				break;
		}

	}

	private boolean isCancelAllowedForOrder(SalesOrder order) {
		// Check if isCancelAllowed flag is false
		if (order.getIsCancelAllowed() != null && !order.getIsCancelAllowed()) {
			return false;
		}
		// Check if any seller order has restricted status (packed, shipped, delivered)
		if (order.getSplitSellerOrders() != null && !order.getSplitSellerOrders().isEmpty()) {
			boolean hasRestrictedStatus = order.getSplitSellerOrders().stream()
					.anyMatch(sellerOrder -> sellerOrder.getStatus() != null &&
							(sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.PACKED_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.SHIPPED_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.DELIVERED_ORDER_STATUS) ||
										sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.INWARD_MIDMILE_ORDER_STATUS) ||
										sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.OUTWARD_MIDMILE_ORDER_STATUS)));
			if (hasRestrictedStatus) {
				return false;
			}
		}
		return true;
	}

	private boolean isCancelAllowedForSplitOrder(SplitSalesOrder order) {
		// Check if isCancelAllowed flag is false
		if (order.getIsCancelAllowed() != null && !order.getIsCancelAllowed()) {
			return false;
		}
		// Check if any seller order has restricted status (packed, shipped, delivered)
		if (order.getSplitSellerOrders() != null && !order.getSplitSellerOrders().isEmpty()) {
			boolean hasRestrictedStatus = order.getSplitSellerOrders().stream()
					.anyMatch(sellerOrder -> sellerOrder.getStatus() != null &&
							(sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.PACKED_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.SHIPPED_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.INWARD_MIDMILE_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.OUTWARD_MIDMILE_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.DELIVERED_ORDER_STATUS)));
			if (hasRestrictedStatus) {
				return false;
			}
		}
		return true;
	}

	private void setOrderPaymentInfo(OrderResponseV3 resp, SalesOrderPayment salesOrderPayment, SalesOrder salesOrder, String xClientVersion) {
		Payments payments = new Payments();
		String paymentInformation = null;

		if (salesOrderPayment != null) {
			paymentInformation = salesOrderPayment.getAdditionalInformation();
			payments.setPaymentMethod(salesOrderPayment.getMethod());
		}

		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation paymentInfo = mapper.readValue(paymentInformation, SalesOrderPaymentInformation.class);
				payments.setCardDetails(parseNullStr(paymentInfo.getCardNumber()));
			} catch (IOException e) {
				LOGGER.error("Jackson mapping error: ", e);
			}
		}

		if (salesOrderPayment != null) {
			String method = salesOrderPayment.getMethod();
			try {
				paymentInformation = salesOrderPayment.getAdditionalInformation();
				if (PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue().equalsIgnoreCase(method)
						|| PaymentCodeENUM.TABBY_PAYLATER.getValue().equalsIgnoreCase(method)) {
					if (paymentInformation != null) {
						TabbyPayment tabbyPayment = mapper.readValue(paymentInformation, TabbyPayment.class);
						payments.setCardDetails(OrderConstants.PAYMENT_TYPE); // static value if Tabby
					}
				} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(method)) {
					if (paymentInformation != null) {
						CashfreePaymentDTO paymetDTO = mapper.readValue(paymentInformation, CashfreePaymentDTO.class);
						payments.setCardDetails(paymetDTO.getCfOrderId());
					}
				}
			} catch (Exception e) {
				LOGGER.error("Payment info parsing error: ", e);
			}
		}

		// Map SalesOrder fields to Payments DTO
		payments.setDiscountAmount(parseNullStr(resp.getDiscountAmount()));
		payments.setGrandTotal(parseNullStr(resp.getGrandTotal()));
		payments.setBaseGrandTotal(parseNullStr(resp.getBaseGrandTotal()));
		payments.setShippingAmount(parseNullStr(salesOrder.getShippingAmount()));
		payments.setGlobalShippingAmount(null!=salesOrder.getGlobalShippingAmount()?salesOrder.getGlobalShippingAmount().toString():"0");
		// SFP-1104 order service cod charges
		BigDecimal codFee = salesOrder != null ? salesOrder.getCashOnDeliveryFee() : null;
		BigDecimal globalCodFee = salesOrder != null ? salesOrder.getGlobalCashOnDeliveryFee() : null;
		BigDecimal totalCod = (codFee != null ? codFee : BigDecimal.ZERO)
				.add(globalCodFee != null ? globalCodFee : BigDecimal.ZERO);
		payments.setCodCharges(parseNullStr(totalCod));
		payments.setSubtotal(parseNullStr(resp.getSubtotal()));
		payments.setCurrency(salesOrder.getOrderCurrencyCode());

		payments.setImportFeesAmount(parseNullStr(salesOrder.getImportFee()));
		//payments.setTaxPercent(parseNullStr(salesOrder.getTaxPercent()));

		if (salesOrder.getSubSalesOrder() != null) {
			payments.setDonationAmount(parseNullStr(salesOrder.getSubSalesOrder().getDonationAmount()));
			payments.setCoinToCurrency(parseNullStr(salesOrder.getSubSalesOrder().getEasValueInCurrency()));
			payments.setCoinToBaseCurrency(parseNullStr(salesOrder.getSubSalesOrder().getEasValueInBaseCurrency()));

			Integer retryPayment = salesOrder.getSubSalesOrder().getRetryPayment();
			payments.setCanRetryPayment(retryPayment != null && retryPayment.equals(1));
		}

		Pair<Integer, String> rmaData = calculateRmaCountAndReturnFee(
				salesOrder.getEntityId(),
				salesOrder.getStoreId(),
				salesOrder.getSubSalesOrder() != null ? salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned() : null,
				salesOrder.getOrderCurrencyCode(),
				xClientVersion
		);
		payments.setReturnFee(rmaData.getRight());

		payments.setOrderAlreadyExists(false); // default unless you manage this elsewhere

		// Set the payments object into the final response
		resp.setPayments(payments);
	}

	private Pair<Integer, String> calculateRmaCountAndReturnFee(
			Integer entityId,
			Integer storeId,
			BigDecimal shukranCoinsBurned,
			String currencyCode,
			String xClientVersion
	) {
		int rmaCountVal = 0;
		double refundAmountToBeDeducted = 0.0;

		if (entityId != null && storeId != null) {
			if (Constants.orderCredentials.getBlockShukranSecondRefund()
					&& shukranCoinsBurned != null
					&& shukranCoinsBurned.compareTo(BigDecimal.ZERO) > 0) {
				refundAmountToBeDeducted = 0.0;
			} else {
				boolean isAppVersionSufficient = false;

				if (org.apache.commons.lang.StringUtils.isNotBlank(xClientVersion)
						&& org.apache.commons.lang.StringUtils.isNotBlank(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion())) {
					Long mobileAppVersion = Constants.decodeAppVersion(xClientVersion);
					Long secondReturnThresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion());

					if (secondReturnThresholdVersion != null && mobileAppVersion != null
							&& secondReturnThresholdVersion <= mobileAppVersion) {
						isAppVersionSufficient = true;
					}
				}

				if (isAppVersionSufficient
						&& Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() != null
						&& Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() > 0) {

					Integer rmaClubbingHours = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();
					rmaCountVal = amastyRmaRequestRepository.getRMACount(entityId, rmaClubbingHours);

					if (rmaCountVal == 0) {
						String requestId = amastyRmaRequestRepository.getLastRequestId(entityId, rmaClubbingHours);
						if (org.apache.commons.lang.StringUtils.isNotBlank(requestId)) {
							int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
							if (trackingCount > 0) {
								rmaCountVal = 1;
							}
						}
					}

					Double refundValue = configService.getWebsiteRefundByStoreId(storeId);
					if (refundValue != null && refundValue > 0) {
						refundAmountToBeDeducted = refundValue;
					} else {
						rmaCountVal = 0;
					}
				}
			}
		}

		String returnFee = org.apache.commons.lang.StringUtils.isNotBlank(currencyCode)
				? currencyCode + " " + refundAmountToBeDeducted
				: "" + refundAmountToBeDeducted;

		return Pair.of(rmaCountVal, returnFee);
	}

	private void addSplitOrderCanceldateAndRTO(SplitSalesOrder splitSalesOrder, SplitOrderResponse resp) {

		// Split order cancel data is not in sales_order_status_history; use updatedAt as last status change time
		if (isSplitOrderCancelStatus(splitSalesOrder.getStatus()) && splitSalesOrder.getUpdatedAt() != null) {
			resp.setCanceledAt(convertTimezone(splitSalesOrder.getUpdatedAt(), splitSalesOrder.getStoreId()));
		}

		List<RtoAutoRefund> rtoAutoRefund = orderHelper.getSplitOrderRtoAutoRefundList(splitSalesOrder);
		if (Objects.nonNull(rtoAutoRefund) && !rtoAutoRefund.isEmpty()) {
			resp.setRto(true);
			resp.setRtoStatus(rtoAutoRefund.get(0).getStatus());
			resp.setRtoRefundAt(convertTimezone(rtoAutoRefund.get(0).getRefundAt(), splitSalesOrder.getStoreId()));
			resp.setRtoRefundAmount(parseNullStr(rtoAutoRefund.get(0).getRefundAmount()));
		} else if("rto".equals(splitSalesOrder.getStatus())){
			resp.setRto(true);
			resp.setRtoStatus("pending");
		}
	}

	public String getShipmentModeOfOrder(String shipmentMode, Integer storeId) {
		if (storeId == null) {
			return Constants.EXPRESS_SHIPMENT.toUpperCase();
		}
		Optional<Stores> storeOptional = Constants.getStoresList().stream()
				.filter(s -> storeId.equals(Integer.valueOf(s.getStoreId())))
				.findFirst();
		if (storeOptional.isEmpty()) {
			return Constants.EXPRESS_SHIPMENT.toUpperCase();
		}
		Stores store = storeOptional.get();
		String mapping = null;
		if (store.getShippingConfig() != null) {
			if (Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(shipmentMode) && store.getShippingConfig().getGlobal() != null) {
				mapping = store.getShippingConfig().getGlobal().getShipmentMapping();
			} else if (store.getShippingConfig().getLocal() != null) {
				mapping = store.getShippingConfig().getLocal().getShipmentMapping();
			}
		}

		if (StringUtils.isNotBlank(mapping)) {
			return mapping.toUpperCase();
		}

		return Constants.EXPRESS_SHIPMENT.toUpperCase();
	}

	public String resolveOverallStatusFromSplitSellerOrders(Integer splitOrderId,
															String splitOrderStatus,
															String shipmentMode) {

		// Apply this aggregation only for GLOBAL shipment
		if (!Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(shipmentMode)) {
			return splitOrderStatus;
		}

		// If order has a terminal status (cancelled, closed, refunded, rto, failed, etc.), return it immediately
		// Terminal statuses should not be overridden by seller order statuses
		if (isTerminalStatus(splitOrderStatus)) {
			return splitOrderStatus;
		}

		List<SplitSellerOrder> sellerOrders =
				splitSellerOrderRepository.findBySplitOrderId(splitOrderId);

		// If no seller orders, don't override
		if (sellerOrders == null || sellerOrders.isEmpty()) {
			return splitOrderStatus;
		}

		// Extract non-null statuses
		List<String> statuses = sellerOrders.stream()
				.filter(Objects::nonNull)
				.map(SplitSellerOrder::getStatus)
				.filter(Objects::nonNull)
				.map(String::toLowerCase)
				.toList();

		if (statuses.isEmpty()) {
			return splitOrderStatus;
		}

		// If product shipments are delivered, status should be delivered regardless of seller shipment status
		if (OrderConstants.DELIVERED_ORDER_STATUS.equalsIgnoreCase(splitOrderStatus)) {
			return OrderConstants.DELIVERED_ORDER_STATUS;
		}

		// Check if all seller orders are delivered
		boolean allSellerOrdersDelivered = areAllSellerOrdersDelivered(statuses);
		
		// If all seller orders are delivered, the overall status depends on product shipments
		if (allSellerOrdersDelivered) {
			return getStatusWhenAllSellersDelivered(splitOrderStatus);
		}
		
		// If not all seller orders are delivered, determine status based on the lowest rank
		if (statuses.size() == 1) {
			String onlyStatus = statuses.get(0);
			return normalizeSellerStatus(onlyStatus, splitOrderStatus);
		}

		// More than one seller order → return lowest status
		int minRank = statuses.stream()
				.mapToInt(this::statusRank)
				.min()
				.orElse(statusRank(splitOrderStatus));

		return rankToStatus(minRank, splitOrderStatus);
	}

	/**
	 * Checks if all seller orders are delivered
	 */
	private boolean areAllSellerOrdersDelivered(List<String> statuses) {
		return statuses.stream()
				.allMatch(status -> OrderConstants.DELIVERED_ORDER_STATUS.equalsIgnoreCase(status));
	}

	/**
	 * Returns the status when all seller orders are delivered
	 * Preserves terminal statuses, otherwise returns "shipped"
	 */
	private String getStatusWhenAllSellersDelivered(String splitOrderStatus) {
		// Don't override terminal statuses (cancelled, refunded, closed, rto, etc.)
		if (isTerminalStatus(splitOrderStatus)) {
			return splitOrderStatus;
		}
		// Seller shipments are delivered but product shipments are not delivered, status is "shipped"
		return OrderConstants.SHIPPED_ORDER_STATUS;
	}

	/**
	 * Checks if a status is a terminal status that should not be overridden
	 * Terminal statuses include: cancelled, refunded, closed, rto, failed, undelivered
	 */
	private boolean isSplitOrderCancelStatus(String status) {
		if (status == null) {
			return false;
		}
		String statusLower = status.toLowerCase();
		return OrderConstants.CLOSED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.CANCELED_ORDER_STATE.toLowerCase().equals(statusLower)
				|| OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.SPLIT_ORDER_CANCELED_ORDER_STATUS.toLowerCase().equals(statusLower);
	}

	private boolean isTerminalStatus(String status) {
		if (status == null) {
			return false;
		}
		String statusLower = status.toLowerCase();
		return OrderConstants.CLOSED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.CANCELED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.CANCELLED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.SPLIT_ORDER_CANCELED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.REFUNDED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.ORDER_STATUS_RTO.toLowerCase().equals(statusLower)
				|| OrderConstants.FAILED_ORDER_STATUS.toLowerCase().equals(statusLower)
				|| OrderConstants.UNDELIVERED_ORDER_STATUS.toLowerCase().equals(statusLower);
	}

	/**
	 * Rank order: processing(1) < packed(2) < shipped(3) = delivered(3)
	 * Unknown statuses get rank 0 so they won't override known statuses.
	 */
	private int statusRank(String status) {
		if (status == null) return 0;

		if (OrderConstants.SHIPPED_ORDER_STATUS.equalsIgnoreCase(status)) return 3;
		if (OrderConstants.DELIVERED_ORDER_STATUS.equalsIgnoreCase(status)) return 3; // Delivered should be treated same as shipped
		if (OrderConstants.PACKED_ORDER_STATUS.equalsIgnoreCase(status)) return 2;
		if (OrderConstants.PROCESSING_ORDER_STATUS.equalsIgnoreCase(status)) return 1;

		return 0;
	}

	private String rankToStatus(int rank, String fallback) {
		return switch (rank) {
			case 3 -> OrderConstants.SHIPPED_ORDER_STATUS;
			case 2 -> OrderConstants.PACKED_ORDER_STATUS;
			case 1 -> OrderConstants.PROCESSING_ORDER_STATUS;
			default -> fallback; // if no known statuses found
		};
	}

	/**
	 * For single seller order case:
	 * If status is known -> return mapped constant.
	 * Else fallback to splitOrderStatus.
	 */
	private String normalizeSellerStatus(String sellerStatus, String fallback) {
		int r = statusRank(sellerStatus);
		return rankToStatus(r, fallback);
	}

	private void viewUpdateEstimateDateOfSplitOrder(SplitSalesOrder splitSalesOrder,List<SplitSalesOrderItem> items, SplitOrderResponse response) {
		if (null==items  || items.isEmpty()) {
			response.setEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			response.setNewEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			return;
		}
		// Estimated delivery: reuse order's EDT fields if you have them; else null-safe
		Timestamp estDate = null;
		Timestamp maxEdt = null;
		Timestamp minEdt = null;
		for (SplitSalesOrderItem item : items) {
			Timestamp parsedDate = item.getEstimatedDeliveryDate();
			if ((estDate == null || parsedDate.after(estDate)) && null != parsedDate) {
				estDate = parsedDate;
			}
			parsedDate = item.getMaxEstimatedDate();
			if ((maxEdt == null || parsedDate.after(maxEdt)) && null != parsedDate) {
				maxEdt = parsedDate;
			}
			parsedDate = item.getMinEstimatedDate();
			if ((minEdt == null || parsedDate.before(minEdt))  && null != parsedDate) {
				minEdt = parsedDate;
			}
		}
		response.setEstimatedDeliveryTime(estDate);
		response.setNewEstimatedDeliveryTime(estDate);
		if (null == estDate) {
			response.setEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			response.setNewEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
		}
	}

}
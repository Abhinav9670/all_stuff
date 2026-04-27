package org.styli.services.order.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.component.GcpStorage;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.RMAHelper;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaReason;
import org.styli.services.order.model.rma.AmastyRmaReasonStore;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.AmastyRmaStatus;
import org.styli.services.order.model.rma.AmastyRmaStatusStore;
import org.styli.services.order.model.rma.AmastyRmaTracking;
import org.styli.services.order.model.rma.MagentoReturnDropOffAPIResponse;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrderPayment;
import org.styli.services.order.model.sales.SplitSubSalesOrder;
import org.styli.services.order.model.sales.SubSalesOrderItem;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.QuantityReturned;
import org.styli.services.order.pojo.SalesOrderPaymentInformation;
import org.styli.services.order.pojo.TotalItemsReturnedResponse;
import org.styli.services.order.pojo.cancel.MagentoAPIResponse;
import org.styli.services.order.pojo.cancel.MagentoReturnDropOffRequest;
import org.styli.services.order.pojo.cancel.Reason;
import org.styli.services.order.pojo.consul.oms.base.Configs;
import org.styli.services.order.pojo.mulin.GalleryItem;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.order.BreakDownItem;
import org.styli.services.order.pojo.order.CancelRMARequest;
import org.styli.services.order.pojo.order.RMAOrderInitV2Response;
import org.styli.services.order.pojo.order.RMAOrderInitV2ResponseDTO;
import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2RequestWrapper;
import org.styli.services.order.pojo.order.RMAOrderV2SubRequest;
import org.styli.services.order.pojo.request.Order.ReturnItemViewRequest;
import org.styli.services.order.pojo.request.Order.ReturnListRequest;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.Order.OrderListRequest;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderItemRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaStatusRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.CoreConfigDataService;
import org.styli.services.order.service.SalesOrderRMAService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.Regions;
import org.styli.services.order.utility.ReturnConstants;
import org.styli.services.order.utility.UtilityConstant;
import org.styli.services.order.utility.consulValues.ConsulValues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * @author Umesh, 27/05/2020
 * @project product-service
 */

@Component
public class SalesOrderRMAServiceImpl implements SalesOrderRMAService {

	private static final Log LOGGER = LogFactory.getLog(SalesOrderRMAServiceImpl.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private static final DecimalFormat df_2 = new DecimalFormat();

	private static final String SIMPLE_PRODUCT_TYPE= "simple";

	private static final String SPEND_COIN = "{{spend_coin}}";

	@Autowired
	MulinHelper mulinHelper;

	@Autowired
	RMAHelper rmaHelper;

	@Autowired
	ConfigService configService;

	@Autowired
	CoreConfigDataService coreConfigdataService;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	AmastyRmaStatusRepository amastyRmaStatusRepository;

	@Autowired
	SubSalesOrderItemRepository subSalesOrderItemRepository;;

	@Autowired
	AmastyRmaReasonRepository amastyRmaReasonRepository;

	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	AmastyRmaTrackingRepository amastyRmaTrackingRepository;

	@Autowired
	SalesOrderServiceV3Impl salesOrderServiceV3Impl;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	@Lazy
	private EASServiceImpl eASServiceImpl;

	@Autowired
	private GcpStorage gcpStorage;

	@Value("${kaleyra.url}")
	private String kaleyraUrl;

	@Value("${env}")
	private String env;

	@Value("${magento.integration.token}")
	private String magentoIntegrationToken;

	@Value("${magento.base.url}")
	private String magentoBaseUrl;

	@Value("${region.value}")
	private String region;

	@PostConstruct
	protected void init() {
		df_2.setMaximumFractionDigits(2);
		df_2.setMinimumFractionDigits(2);
		df_2.setGroupingUsed(false);
	}

	@Override
	@Transactional
	public OrderResponseDTO cancelRMAOrder(CancelRMARequest request) {
		OrderResponseDTO resp = new OrderResponseDTO();
		if (request.getCustomerId() == null || request.getStoreId() == null || request.getRequestId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing for RMA request!");
			return resp;
		}
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);
		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg(Constants.STORE_NOT_FOUND_MSG);
			return resp;
		}
		AmastyRmaRequest amastyRmaRequest = amastyRmaRequestRepository
				.findByRequestIdAndCustomerId(request.getRequestId(), request.getCustomerId());
		if (amastyRmaRequest == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("RMA request not found!");
			return resp;
		}

		MagentoAPIResponse magentoAPIResponse = rmaHelper.returnAWBCancellationFromMagento(amastyRmaRequest);

		if (magentoAPIResponse.getStatus().booleanValue() && magentoAPIResponse.getStatusCode() == 200) {
			amastyRmaRequest.setStatus(12);
			amastyRmaRequest.setModifiedAt(new Timestamp(new Date().getTime()));
			amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);
			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Successfully canceled RMA request: " + amastyRmaRequest.getRequestId());
		} else {
			resp.setStatusMsg("Failed to cancel RMA request: " + amastyRmaRequest.getRequestId() + " with message: "
					+ magentoAPIResponse.getStatusMsg());
		}
		return resp;
	}

	@Override
	public RMAOrderInitV2ResponseDTO rmaOrderVersionTwoInit(RMAOrderV2Request request, String xClientVersion) {

		RMAOrderInitV2ResponseDTO resp = new RMAOrderInitV2ResponseDTO();
		RMAOrderInitV2Response rmaOrderInitV2Response = new RMAOrderInitV2Response();
		List<RMAItem> items = new ArrayList<>();

		if (request.getCustomerId() == null || request.getOrderId() == null || request.getStoreId() == null
				|| CollectionUtils.isEmpty(request.getItems())) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing for RMA order version!");
			return resp;
		}

		Stores store = Constants.getStoresList().stream().filter(e -> e != null &&
				StringUtils.isNotEmpty(e.getStoreId()) &&
				e.getStoreId().equalsIgnoreCase(request.getStoreId().toString())).findFirst().orElse(null);

		SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(), request.getCustomerId());
		if (order == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Order not found!");
			return resp;
		}

		boolean isSplitOrder = order.getIsSplitOrder() != null && order.getIsSplitOrder() == 1;
		BigDecimal totalItemsReturnedAlready = BigDecimal.ZERO;
		List<Integer> allIds = new ArrayList<>();
		BigDecimal totalShukranCoinsBurned = BigDecimal.ZERO;
		int rmaCountVal = 0;
		double refundAmountToBeDeducted = 0.0;
		BigDecimal totalAmountToBeRefunded = BigDecimal.ZERO;
		BigDecimal taxFactor = BigDecimal.valueOf(1);
		if (store != null && store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal decimalTaxValue = store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
			taxFactor = taxFactor.add(decimalTaxValue);
		}
		BigDecimal totalAmountToBeRefundedAsCoins = BigDecimal.ZERO;
		BigDecimal totalAmountToBeRefundedAsCredit = BigDecimal.ZERO;
		BigDecimal totalAmountToBeRefundedAsOnline = BigDecimal.ZERO;
		BigDecimal totalAmountToBeRefundedAsShukran = BigDecimal.ZERO;
		BigDecimal totalShukranPointsToBeReturned = new BigDecimal(0);
		BigDecimal totalReturnableItems = BigDecimal.ZERO;
		BigDecimal returnableQuantity = BigDecimal.ZERO;
		Map<String, ProductResponseBody> productsFromMulin;
		SplitSalesOrder deliveredSplitOrder = null;
		List<SplitSalesOrder> deliveredSplitOrders = null;
		SplitSubSalesOrder splitSubSalesOrder = null;
		if (isSplitOrder) {
			// Use split tables for all data
			deliveredSplitOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId())
				.stream().filter(split -> "delivered".equalsIgnoreCase(split.getStatus())).collect(Collectors.toList());
			if (CollectionUtils.isEmpty(deliveredSplitOrders)) {
				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("No delivered split orders found!");
				return resp;
			}
			// For simplicity, use the first delivered split order for payment, etc. (adjust as needed)
			deliveredSplitOrder = deliveredSplitOrders.get(0);
			splitSubSalesOrder = deliveredSplitOrder.getSplitSubSalesOrder();
			if (splitSubSalesOrder != null && splitSubSalesOrder.getTotalShukranCoinsBurned() != null) {
				totalShukranCoinsBurned = splitSubSalesOrder.getTotalShukranCoinsBurned();
			}
			productsFromMulin = mulinHelper.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
		} else {
			// Use standard tables
			if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null) {
				totalShukranCoinsBurned = order.getSubSalesOrder().getTotalShukranCoinsBurned();
			}
			productsFromMulin = mulinHelper.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
		}
		if (totalShukranCoinsBurned.compareTo(BigDecimal.ZERO) > 0) {
			TotalItemsReturnedResponse totalItemsReturnedResponse = totalItemsReturned(order, null);
			totalItemsReturnedAlready = totalItemsReturnedResponse.getTotalQuantity();
			allIds = totalItemsReturnedResponse.getAllIds();
		}
		rmaOrderInitV2Response.setOrderId(request.getOrderId());
		if (Constants.orderCredentials.getBlockShukranSecondRefund() && totalShukranCoinsBurned.compareTo(BigDecimal.ZERO) > 0) {
			refundAmountToBeDeducted = 0.0;
		} else {
			if (Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() != null && Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() > 0) {
				int rmaClubbingHours = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();
				rmaCountVal = amastyRmaRequestRepository.getRMACount(request.getOrderId(), rmaClubbingHours);
				if (rmaCountVal == 0) {
					String requestId = amastyRmaRequestRepository.getLastRequestId(request.getOrderId(), rmaClubbingHours);
					if (StringUtils.isNotEmpty(requestId) && StringUtils.isNotBlank(requestId)) {
						int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
						if (trackingCount > 0) {
							rmaCountVal = 1;
						}
					}
				}
			}
		}
		if (rmaCountVal >= 1) {
			boolean isAppVersionSufficient = false;
			if (StringUtils.isNotEmpty(xClientVersion) && StringUtils.isNotBlank(xClientVersion) && StringUtils.isNotBlank(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion()) && StringUtils.isNotEmpty(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion())) {
				Long mobileAppVersion = Constants.decodeAppVersion(xClientVersion);
				Long secondReturnThresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion());
				if (secondReturnThresholdVersion != null && mobileAppVersion != null && secondReturnThresholdVersion <= mobileAppVersion) {
					isAppVersionSufficient = true;
				}
			}
			if (isAppVersionSufficient) {
				Double refundValue = configService.getWebsiteRefundByStoreId(request.getStoreId());
				if (refundValue != null && refundValue > 0) {
					refundAmountToBeDeducted = refundValue;
				} else {
					rmaCountVal = 0;
				}
			} else {
				rmaCountVal = 0;
			}
		}
		rmaOrderInitV2Response.setRmaCount(rmaCountVal);
		rmaOrderInitV2Response.setReturnAmountFee(refundAmountToBeDeducted);
		rmaOrderInitV2Response.setCustomerId(String.valueOf(order.getCustomerId()));
		rmaOrderInitV2Response.setOrderIncrementId(String.valueOf(order.getIncrementId()));

		List<String> productIds = new ArrayList<>();

		for (SalesOrderItem item : order.getSalesOrderItem()) {
			if (item.getProductType().equalsIgnoreCase(SIMPLE_PRODUCT_TYPE)) {
				BigDecimal itemSubTotal = item.getOriginalPrice()
						.divide(taxFactor, 6, RoundingMode.HALF_UP)
						.multiply(item.getQtyOrdered());

				BigDecimal itemDiscount1 = (item.getOriginalPrice()
						.subtract(item.getPriceInclTax()))
						.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(item.getQtyOrdered());

				BigDecimal discountAmount = BigDecimal.ZERO;

				if (item.getParentOrderItem() != null) {
					BigDecimal subSalesOrderDiscountAmount = getSubSalesOrderDiscount(isSplitOrder, deliveredSplitOrder, order, item.getParentOrderItem().getItemId());
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
				BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor);

				totalAmountToBeRefunded = totalAmountToBeRefunded.add(itemFinalPrice);
				if(item.getReturnable() != null && item.getReturnable() == 1 && item.getShukranCoinsBurned()!=null && item.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
					totalShukranPointsToBeReturned = totalShukranPointsToBeReturned.add(item.getShukranCoinsBurned());
					totalReturnableItems = totalReturnableItems.add(item.getQtyShipped());
				}
			}
		}
		int totalShukranPoints=0;
		if(totalShukranPointsToBeReturned.compareTo(BigDecimal.ZERO)>0){
			rmaOrderInitV2Response.setIsShukranPaymentInOrder(true);
			totalShukranPoints = totalShukranPointsToBeReturned.intValue();
		}
		SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		String paymentMethod = null;

		if (null != orderPayment) {

			paymentMethod = orderPayment.getMethod();

		}
		LOGGER.info("Total Amount To Be Refunded" + totalAmountToBeRefunded);


		// Get values from appropriate sub sales order (split or regular)
		BigDecimal easValueInCurrency = BigDecimal.ZERO;
		BigDecimal shukranBurnedValueInCurrency = BigDecimal.ZERO;
		BigDecimal storecreditAmount = BigDecimal.ZERO;
		
		if (isSplitOrder) {
			if (splitSubSalesOrder != null) {
				if (splitSubSalesOrder.getEasValueInCurrency() != null) {
					easValueInCurrency = splitSubSalesOrder.getEasValueInCurrency();
				}
				if (splitSubSalesOrder.getTotalShukranBurnedValueInCurrency() != null) {
					shukranBurnedValueInCurrency = splitSubSalesOrder.getTotalShukranBurnedValueInCurrency();
				}
			}
			if (deliveredSplitOrder.getAmstorecreditAmount() != null) {
				storecreditAmount = deliveredSplitOrder.getAmstorecreditAmount();
			}
		} else {
			if (order.getSubSalesOrder() != null) {
				if (order.getSubSalesOrder().getEasValueInCurrency() != null) {
					easValueInCurrency = order.getSubSalesOrder().getEasValueInCurrency();
				}
				if (order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null) {
					shukranBurnedValueInCurrency = order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
				}
			}
			if (order.getAmstorecreditAmount() != null) {
				storecreditAmount = order.getAmstorecreditAmount();
			}
		}

		if (null != paymentMethod && (OrderConstants.checkPaymentMethod(paymentMethod) || OrderConstants.checkBNPLPaymentMethods(paymentMethod) || PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod))) {
			totalAmountToBeRefundedAsOnline = totalAmountToBeRefunded;
			if (easValueInCurrency.compareTo(new BigDecimal(0)) > 0) {
				totalAmountToBeRefundedAsCoins = easValueInCurrency;
				totalAmountToBeRefundedAsOnline = totalAmountToBeRefundedAsOnline.subtract(totalAmountToBeRefundedAsCoins);
			}
			if(totalShukranCoinsBurned.compareTo(BigDecimal.ZERO)>0){
				totalAmountToBeRefundedAsShukran = shukranBurnedValueInCurrency;
				totalAmountToBeRefundedAsOnline= totalAmountToBeRefundedAsOnline.subtract(shukranBurnedValueInCurrency);
			}
			if (storecreditAmount.compareTo(new BigDecimal(0)) > 0) {
				totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit.add(storecreditAmount);
			}
			if (totalAmountToBeRefundedAsCredit.compareTo(BigDecimal.ZERO) > 0) {
				totalAmountToBeRefundedAsOnline = totalAmountToBeRefundedAsOnline.subtract(totalAmountToBeRefundedAsCredit);
			}
		} else {
			totalAmountToBeRefundedAsCredit = totalAmountToBeRefunded;
			if (easValueInCurrency.compareTo(new BigDecimal(0)) > 0) {
				totalAmountToBeRefundedAsCoins = easValueInCurrency;
				totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit.subtract(totalAmountToBeRefundedAsCoins);
			}
			if(totalShukranCoinsBurned.compareTo(BigDecimal.ZERO)>0){
				totalAmountToBeRefundedAsCredit= totalAmountToBeRefundedAsCredit.subtract(shukranBurnedValueInCurrency);
				totalAmountToBeRefundedAsShukran = shukranBurnedValueInCurrency;
			}
		}


		BigDecimal refundGrandTotal = new BigDecimal(0);

		BigDecimal refundShukranTotalPoints= new BigDecimal(0);
		List<String> unavailableItems = new ArrayList<>();
		boolean hasUnavailableItems = false;
		
		for (RMAOrderItemV2Request item : request.getItems()) {
			// Find the correct split order for this item based on parentOrderItemId
			if (isSplitOrder && deliveredSplitOrders != null) {
				deliveredSplitOrder = findSplitOrderForItem(deliveredSplitOrders, item);
			}
			AbstractMap.SimpleEntry<SalesOrderItem, SalesOrderItem> itemPair = 
				findParentAndChildItems(isSplitOrder, deliveredSplitOrder, order, item);
			final SalesOrderItem parentItem = itemPair.getKey();
			final SalesOrderItem childOrderItem = itemPair.getValue();
			
			if (parentItem == null || childOrderItem == null) {
				resp.setStatus(false);
				resp.setStatusCode("203");
				resp.setStatusMsg("Order Item(s) not found!");
				return resp;
			}

			// Check if quantity requested for return is available from ordered quantity

			QuantityReturned quantityReturned = omsorderentityConverter.getQtyReturned(childOrderItem.getItemId(),
					false, false);
			BigDecimal qtyReturned = quantityReturned.getQtyReturned();
			BigDecimal allowableQtyToReturn = new BigDecimal(0);

			if (parentItem.getQtyOrdered() != null) {
				allowableQtyToReturn = parentItem.getQtyOrdered().subtract(qtyReturned);
				if (ObjectUtils.isNotEmpty(parentItem.getQtyCanceled()))
					allowableQtyToReturn = allowableQtyToReturn.subtract(parentItem.getQtyCanceled());
				if (null != item.getReturnQuantity()) {
					BigDecimal requestedReturnQty = new BigDecimal(item.getReturnQuantity());
					if (requestedReturnQty.compareTo(allowableQtyToReturn) > 0) {
						unavailableItems.add(parentItem.getName() + " (SKU: " + parentItem.getSku() + ")");
						hasUnavailableItems = true;
						continue; // Skip this item but continue processing others
					}
				} else {
					resp.setStatus(false);
					resp.setStatusCode("209");
					resp.setStatusMsg("Return qtu is null!");
					return resp;
				}
			}
			productIds.add(parentItem.getProductId());
			RMAItem rmaItem = new RMAItem();
			rmaItem.setAvailableQty(parseNullStr(allowableQtyToReturn));
			rmaItem.setReturnedQty(parseNullStr(qtyReturned));
			BigDecimal sellableprice = parentItem.getPriceInclTax();
			BigDecimal productDiscountPrice = new BigDecimal(0);
			BigDecimal bagDiscountPrice = new BigDecimal(0);


			if (CollectionUtils.isNotEmpty(parentItem.getSubSalesOrderItem())) {
				productDiscountPrice = parentItem.getSubSalesOrderItem().stream().map(SubSalesOrderItem::getDiscount)
						.reduce(BigDecimal.ZERO, BigDecimal::add);
			}
			sellableprice = sellableprice.subtract(productDiscountPrice);
			if (null != parentItem.getOriginalPrice()) {
				bagDiscountPrice = parentItem.getOriginalPrice().subtract(parentItem.getPriceInclTax());
			}


			BigDecimal discountSumValue = productDiscountPrice.add(bagDiscountPrice);

			rmaItem.setParentOrderItemId(parseNullStr(item.getParentOrderItemId()));
			rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));
			rmaItem.setName(parseNullStr(parentItem.getName()));
			rmaItem.setSku(parseNullStr(parentItem.getSku()));
			rmaItem.setOrderedQty(parseNullStr(parentItem.getQtyOrdered()));
			if (parentItem.getPriceInclTax() != null && parentItem.getOriginalPrice() != null) {
				rmaItem.setPrice(sellableprice.toString());
				rmaItem.setOriginalPrice(parentItem.getOriginalPrice().toString());
				double discountAmount = (parentItem.getOriginalPrice().subtract(discountSumValue)).doubleValue();
				DecimalFormat df = new DecimalFormat("#.##");

				if (discountAmount == 0.0 && !parentItem.getOriginalPrice().equals(parentItem.getPriceInclTax())) {

					rmaItem.setDiscount("100.00");

				} else {

					double discount = Double.parseDouble(
							df.format((discountAmount / Double.parseDouble(rmaItem.getOriginalPrice())) * 100));

					rmaItem.setDiscount(parseNullStr(discount));
				}

			}
			BigDecimal itemSubTotal = BigDecimal.ZERO;
			BigDecimal itemDiscount1 = BigDecimal.ZERO;
			if(childOrderItem.getOriginalPrice() != null) {
				itemSubTotal = childOrderItem.getOriginalPrice()
						.divide(taxFactor, 6, RoundingMode.HALF_UP)
						.multiply(childOrderItem.getQtyOrdered());
				itemDiscount1 = (childOrderItem.getOriginalPrice()
						.subtract(childOrderItem.getPriceInclTax()))
						.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(childOrderItem.getQtyOrdered());
			}
			BigDecimal discountAmount = BigDecimal.ZERO;
			if (order.getEntityId() != null && childOrderItem.getParentOrderItem() != null) {
				BigDecimal subSalesOrderDiscountAmount = getSubSalesOrderDiscount(isSplitOrder, deliveredSplitOrder, order, childOrderItem.getParentOrderItem().getItemId());
				if (subSalesOrderDiscountAmount != null) {
					discountAmount = subSalesOrderDiscountAmount;
				}
			} else if (childOrderItem.getSubSalesOrderItem() != null) {

				for (SubSalesOrderItem i : childOrderItem.getSubSalesOrderItem()) {
					if (i.isGiftVoucher()) {
						discountAmount = i.getDiscount();
					}
				}

			}
			LOGGER.info("discountAmount: " + discountAmount);

			BigDecimal itemDiscount2 = (childOrderItem.getDiscountAmount()
					.subtract(discountAmount))
					.divide(taxFactor, 6, RoundingMode.HALF_UP);
			BigDecimal itemFinalDiscount = itemDiscount1.add(itemDiscount2);
			BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemFinalDiscount);
			BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor);
			BigDecimal indivisualValue = itemFinalPrice.divide(childOrderItem.getQtyOrdered(), 6, RoundingMode.HALF_UP);

			indivisualValue = indivisualValue.multiply(BigDecimal.valueOf(item.getReturnQuantity()))
					.setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

			returnableQuantity = returnableQuantity.add(BigDecimal.valueOf(item.getReturnQuantity()));

			rmaItem.setGrandTotal(parseNullStr(indivisualValue.setScale(2, RoundingMode.HALF_UP)));
			if (null != indivisualValue) {
				refundGrandTotal = refundGrandTotal.add(indivisualValue);
			}

			if(childOrderItem.getShukranCoinsBurned() != null && childOrderItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
				refundShukranTotalPoints = refundShukranTotalPoints.add(childOrderItem.getShukranCoinsBurned().divide(childOrderItem.getQtyOrdered(), 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(item.getReturnQuantity()))
						.setScale(2, RoundingMode.HALF_UP));
			}

			items.add(rmaItem);
		}
		BigDecimal divideValue = new BigDecimal(100);
		BigDecimal refundStoreCreditTotal = new BigDecimal(0);
		BigDecimal refundStoreCoinsTotal = new BigDecimal(0);
		BigDecimal refundPrepaidTotal = new BigDecimal(0);
		BigDecimal refundShukranValue= BigDecimal.ZERO;

		if (refundGrandTotal.compareTo(new BigDecimal(0)) > 0) {

			BigDecimal percentageShareOfRefundGrandTotalCompareToOrderOriginal = refundGrandTotal.divide(totalAmountToBeRefunded, 6, RoundingMode.HALF_UP).multiply(divideValue);

			if (totalAmountToBeRefundedAsCredit.compareTo(BigDecimal.ZERO) > 0) {
				refundStoreCreditTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal.multiply(totalAmountToBeRefundedAsCredit).divide(divideValue, 6, RoundingMode.HALF_UP);
			}

			if (totalAmountToBeRefundedAsCoins.compareTo(BigDecimal.ZERO) > 0) {
				refundStoreCoinsTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal.multiply(totalAmountToBeRefundedAsCoins).divide(divideValue, 6, RoundingMode.HALF_UP);
			}

			if (totalAmountToBeRefundedAsOnline.compareTo(BigDecimal.ZERO) > 0) {
				refundPrepaidTotal = percentageShareOfRefundGrandTotalCompareToOrderOriginal.multiply(totalAmountToBeRefundedAsOnline).divide(divideValue, 6, RoundingMode.HALF_UP);
			}
			if(totalAmountToBeRefundedAsShukran.compareTo(BigDecimal.ZERO)>0){
				refundShukranValue= percentageShareOfRefundGrandTotalCompareToOrderOriginal.multiply(totalAmountToBeRefundedAsShukran).divide(divideValue, 6, RoundingMode.HALF_UP);
			}

		}


		for (RMAItem item : items) {
			for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
				ProductResponseBody productDetailsFromMulin = entry.getValue();
				Variant variant = productDetailsFromMulin.getVariants().stream()
						.filter(e -> e.getSku().equals(item.getSku())).findAny().orElse(null);
				if (variant != null) {
					if (variant.getSizeLabels() != null)
						item.setSize(parseNullStr(variant.getSizeLabels().getEn()));
					if (productDetailsFromMulin.getMediaGallery() != null
							&& !productDetailsFromMulin.getMediaGallery().isEmpty()) {
						GalleryItem galleryItem = productDetailsFromMulin.getMediaGallery().get(0);
						if (galleryItem != null)
							item.setImage(galleryItem.getValue());
					}
				}
			}
		}

		rmaOrderInitV2Response.setItems(items);

		ObjectMapper mapper = new ObjectMapper();
		String paymentInformation = null;
		SalesOrderPayment salesOrderPayment = null;
		
		if (isSplitOrder) {
			// For split orders, get payment from split order payments
			SplitSalesOrderPayment splitPayment = deliveredSplitOrder.getSplitSalesOrderPayments().stream().findFirst().orElse(null);
			if (splitPayment != null) {
				paymentInformation = splitPayment.getAdditionalInformation();
				rmaOrderInitV2Response.setPaymentMethod(splitPayment.getMethod());
			}
		} else {
			// Normal flow: get payment from regular order
			salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
			if (salesOrderPayment != null) {
				paymentInformation = salesOrderPayment.getAdditionalInformation();
				rmaOrderInitV2Response.setPaymentMethod(salesOrderPayment.getMethod());
			}
		}

		// Expected to fetch card type and number in case of prepaid, apple_pay
		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInformation,
						SalesOrderPaymentInformation.class);
				rmaOrderInitV2Response.setCardNumber(parseNullStr(salesOrderPaymentInformation.getCardNumber()));
				rmaOrderInitV2Response.setPaymentOption(parseNullStr(salesOrderPaymentInformation.getPaymentOption()));
			} catch (IOException e) {
				LOGGER.error("jackson mapper error!");
			}
		}

		for (SalesOrderAddress shippingAddress : order.getSalesOrderAddress()) {
			if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {
				OrderAddress orderAddress = new OrderAddress();
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
                orderAddress.setBuildingNumber(parseNullStr(shippingAddress.getBuildingNumber()));
				orderAddress.setLandmark(parseNullStr(shippingAddress.getNearestLandmark()));
				rmaOrderInitV2Response.setPickupAddress(orderAddress);
			}
		}

		List<Reason> reasons = new ArrayList<>();
		List<AmastyRmaReason> amastyRmaReasons = amastyRmaReasonRepository.findByStatusAndIsDeletedOrderByPositionAsc(1,
				0);
		if (CollectionUtils.isNotEmpty(amastyRmaReasons)) {
			for (AmastyRmaReason amastyRmaReason : amastyRmaReasons) {
				String reasonLabel = amastyRmaReason.getTitle();
				if (!request.getOmsRequest().booleanValue()) {
					for (AmastyRmaReasonStore amastyRmaReasonStore : amastyRmaReason.getAmastyRmaReasonStores()) {
						if (amastyRmaReasonStore.getStoreId().equals(request.getStoreId())
								&& amastyRmaReasonStore.getLabel() != null
								&& !amastyRmaReasonStore.getLabel().isEmpty()) {
							reasonLabel = amastyRmaReasonStore.getLabel();
						}
					}
				}
				if (amastyRmaReason.getReasonId() != null) {
					Reason reason = new Reason(amastyRmaReason.getReasonId().toString(), reasonLabel);
					reasons.add(reason);
				}
			}
		}

		/** check for return type **/

		AmastyRmaRequest amastyRmaRequest = amastyRmaRequestRepository
				.findFirstByCustomerIdAndOrderIdOrderByCreatedAtDesc(request.getCustomerId(), request.getOrderId());

		if (null != amastyRmaRequest) {
			if (null != amastyRmaRequest.getReturnType() && amastyRmaRequest.getReturnType().equals(1)
					&& null != amastyRmaRequest.getStatus()) {
				if (!Constants.RETURN_TYPE_PROCESS_STATUS.contains(amastyRmaRequest.getStatus())) {

					rmaOrderInitV2Response.setIsRunningDropOffExists(true);
					rmaOrderInitV2Response.setReturnRequestId(amastyRmaRequest.getRmaIncId());
				}
			} else {

				if (!Constants.RETURN_TYPE_PROCESS_STATUS.contains(amastyRmaRequest.getStatus())) {

					rmaOrderInitV2Response.setIsRunningpickupExists(true);

				}
			}
		}
		int refundShukranTotal= refundShukranTotalPoints.intValue();

		if(returnableQuantity.compareTo(totalReturnableItems.subtract(totalItemsReturnedAlready)) == 0 && allIds != null && !allIds.isEmpty()){
			BigDecimal totalShukranReturned = amastyRmaRequestRepository.getAllShukranPoints(allIds);
			if(totalShukranReturned == null){
				totalShukranReturned = BigDecimal.ZERO;
			}
			boolean isOtherReturnPending= BigDecimal.valueOf(totalShukranPoints).subtract(totalShukranReturned.add(BigDecimal.valueOf(5))).compareTo(BigDecimal.valueOf(refundShukranTotal))>0;
			if(!isOtherReturnPending){
				refundShukranTotal= totalShukranPoints-totalShukranReturned.intValue();
			}
		}

		rmaOrderInitV2Response = setupReturnTotalAndBreakdown(rmaOrderInitV2Response, order, request.getStoreId(),
				refundGrandTotal, refundStoreCreditTotal, refundPrepaidTotal, refundStoreCoinsTotal, refundAmountToBeDeducted, store, refundShukranTotal, refundShukranValue);

		rmaOrderInitV2Response.setReasons(reasons);
		resp.setResponse(rmaOrderInitV2Response);
		
		// Handle response based on item availability
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg(Constants.STATUS_KEY);
		return resp;

	}

	@Override
	public RMAOrderInitV2ResponseDTO rmaOrderVersionTwoInitWrapper(List<RMAOrderV2RequestWrapper> wrappers, String xClientVersion) {
		
		RMAOrderInitV2ResponseDTO combinedResponse = null;
		List<RMAItem> allItems = new ArrayList<>();
		List<BreakDownItem> allBreakDownItems = new ArrayList<>();
		BigDecimal totalRefundGrandTotal = BigDecimal.ZERO;
		BigDecimal totalRefundStoreCreditTotal = BigDecimal.ZERO;
		BigDecimal totalRefundPrepaidTotal = BigDecimal.ZERO;
		double totalReturnAmountFee = 0.0;
		double totalReturnAmountToBePay = 0.0;
		BigDecimal totalSubTotal = BigDecimal.ZERO;
		int totalRmaCount = 0;
		boolean isRunningPickupExists = false;
		boolean isRunningDropOffExists = false;
		String returnRequestId = null;
		
		// Process all wrappers in the array
		for (RMAOrderV2RequestWrapper wrapper : wrappers) {
			// Process each split order within the wrapper
			for (RMAOrderV2SubRequest splitOrder : wrapper.getOrders()) {
				RMAOrderV2Request request = createRMAOrderRequest(wrapper, splitOrder);
				RMAOrderInitV2ResponseDTO response = rmaOrderVersionTwoInit(request, xClientVersion);
				
				// Check if the response is successful
				if (response != null && response.getStatus() != null && response.getStatus()) {
					RMAOrderInitV2Response responseData = response.getResponse();
					
					if (responseData != null) {
						// Aggregate items
						if (responseData.getItems() != null) {
							allItems.addAll(responseData.getItems());
						}
						
						// Aggregate breakdown items
						if (responseData.getReturnBreakDown() != null) {
							allBreakDownItems.addAll(responseData.getReturnBreakDown());
						}
						
						// Aggregate monetary values
						if (responseData.getRefundGrandTotal() != null) {
							try {
								totalRefundGrandTotal = totalRefundGrandTotal.add(new BigDecimal(responseData.getRefundGrandTotal()));
							} catch (NumberFormatException e) {
								LOGGER.warn("Could not parse refundGrandTotal: " + responseData.getRefundGrandTotal());
							}
						}
						
						if (responseData.getRefundStoreCreditTotal() != null) {
							try {
								totalRefundStoreCreditTotal = totalRefundStoreCreditTotal.add(new BigDecimal(responseData.getRefundStoreCreditTotal()));
							} catch (NumberFormatException e) {
								LOGGER.warn("Could not parse refundStoreCreditTotal: " + responseData.getRefundStoreCreditTotal());
							}
						}
						
						if (responseData.getRefundPrepaidTotal() != null) {
							try {
								totalRefundPrepaidTotal = totalRefundPrepaidTotal.add(new BigDecimal(responseData.getRefundPrepaidTotal()));
							} catch (NumberFormatException e) {
								LOGGER.warn("Could not parse refundPrepaidTotal: " + responseData.getRefundPrepaidTotal());
							}
						}
						
						// Aggregate fees and amounts
						totalReturnAmountFee += responseData.getReturnAmountFee();
						totalReturnAmountToBePay += responseData.getReturnAmountToBePay();
						
						// Aggregate subtotal
						if (responseData.getSubTotal() != null) {
							totalSubTotal = totalSubTotal.add(responseData.getSubTotal());
						}
						
						// Aggregate RMA count
						totalRmaCount += responseData.getRmaCount();
						
						// OR the boolean flags
						isRunningPickupExists = isRunningPickupExists || (responseData.getIsRunningpickupExists() != null && responseData.getIsRunningpickupExists());
						isRunningDropOffExists = isRunningDropOffExists || (responseData.getIsRunningDropOffExists() != null && responseData.getIsRunningDropOffExists());
						
						// Set return request ID from the first response that has one
						if (returnRequestId == null && responseData.getReturnRequestId() != null) {
							returnRequestId = responseData.getReturnRequestId();
						}
						
						// Use the first successful response as the base for other fields
						if (combinedResponse == null) {
							combinedResponse = response;
						}
					}
				} else {
					// If any response fails, return the error response
					if (response != null) {
						return response;
					} else {
						// Create error response if response is null
						RMAOrderInitV2ResponseDTO errorResponse = new RMAOrderInitV2ResponseDTO();
						errorResponse.setStatus(false);
						errorResponse.setStatusCode("500");
						errorResponse.setStatusMsg("Failed to process RMA request");
						return errorResponse;
					}
				}
			}
		}
		
		// If we have a successful combined response, update it with aggregated data
		if (combinedResponse != null && combinedResponse.getResponse() != null) {
			RMAOrderInitV2Response responseData = combinedResponse.getResponse();
			
			// Update with aggregated values
			responseData.setItems(allItems);
			responseData.setReturnBreakDown(allBreakDownItems);
			responseData.setRefundGrandTotal(getFormattedDecimalString(totalRefundGrandTotal));
			responseData.setRefundStoreCreditTotal(getFormattedDecimalString(totalRefundStoreCreditTotal));
			responseData.setRefundPrepaidTotal(getFormattedDecimalString(totalRefundPrepaidTotal));
			responseData.setReturnAmountFee(totalReturnAmountFee);
			responseData.setReturnAmountToBePay(totalReturnAmountToBePay);
			responseData.setSubTotal(totalSubTotal);
			responseData.setRmaCount(totalRmaCount);
			responseData.setIsRunningpickupExists(isRunningPickupExists);
			responseData.setIsRunningDropOffExists(isRunningDropOffExists);
			
			// Set return request ID if we have one
			if (returnRequestId != null) {
				responseData.setReturnRequestId(returnRequestId);
			}
		}
		
		return combinedResponse;
	}

	@Override
	public RMAOrderInitV2ResponseDTO rmaOrderVersionTwoInitSingle(RMAOrderV2RequestWrapper wrapper, String xClientVersion) {
		// Delegate to the batch method with a single-element list
		return rmaOrderVersionTwoInitWrapper(List.of(wrapper), xClientVersion);
	}
	
	private RMAOrderV2Request createRMAOrderRequest(RMAOrderV2RequestWrapper wrapper, RMAOrderV2SubRequest splitOrder) {
		RMAOrderV2Request request = new RMAOrderV2Request();
		request.setCustomerId(wrapper.getCustomerId());
		request.setOrderId(wrapper.getOrderId());
		request.setStoreId(wrapper.getStoreId());
		request.setSplitOrderId(splitOrder.getSplitOrderId());
		request.setItems(splitOrder.getItems());
		request.setIsDropOffRequest(wrapper.getIsDropOffRequest());
		request.setOmsRequest(wrapper.getOmsRequest());
		request.setRmaPaymentMethod(wrapper.getRmaPaymentMethod());
		request.setRefundAmountDebited(wrapper.getRefundAmountDebited());
		request.setRefundAmountCredited(wrapper.getRefundAmountCredited());
		request.setTotalRefundAmount(wrapper.getTotalRefundAmount());
		request.setDropOffDetails(wrapper.getDropOffDetails());
		request.setCityName(wrapper.getCityName());
		request.setCpId(wrapper.getCpId());
		request.setReturnIncPayfortId(wrapper.getReturnIncPayfortId());
		request.setReturnFeeAmount(wrapper.getReturnFeeAmount());
		return request;
	}
	


	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public OrderResponseDTO rmaOrderVersionTwo(RMAOrderV2Request request, String xClientVersion) {

		OrderResponseDTO resp = new OrderResponseDTO();

		if (request.getCustomerId() == null || request.getOrderId() == null || request.getStoreId() == null
				|| CollectionUtils.isEmpty(request.getItems())) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing for RMA Version!");
			return resp;
		}

		for (RMAOrderItemV2Request item : request.getItems()) {
			if (item.getReasonId() == null) {
				resp.setStatus(false);
				resp.setStatusCode("201");
				resp.setStatusMsg("Reason(s) missing!");
				return resp;
			}
			if (item.getReturnQuantity() == null) {
				resp.setStatus(false);
				resp.setStatusCode("208");
				resp.setStatusMsg("item Qty missing!");
				return resp;
			}
		}

		List<Stores> stores = Constants.getStoresList();
		OrderResponse response = new OrderResponse();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store not found for RMA Order version!");
			return resp;
		}

		SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(), request.getCustomerId());
		if (order == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Order not found!");
			return resp;
		}

		boolean isSplitOrder = order.getIsSplitOrder() != null && order.getIsSplitOrder() == 1;
		List<SplitSalesOrder> deliveredSplitOrders = null;
		SplitSalesOrder deliveredSplitOrder = null;
		if (isSplitOrder) {
			deliveredSplitOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId())
				.stream().filter(split -> "delivered".equalsIgnoreCase(split.getStatus())).collect(Collectors.toList());
			if (CollectionUtils.isEmpty(deliveredSplitOrders)) {
				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("No delivered split orders found!");
				return resp;
			}
		}

		// Now process items using split tables if isSplitOrder, else normal tables
		for (RMAOrderItemV2Request item : request.getItems()) {
			// Find the correct split order for this item based on parentOrderItemId
			if (isSplitOrder && deliveredSplitOrders != null) {
				deliveredSplitOrder = findSplitOrderForItem(deliveredSplitOrders, item);
			}
			AbstractMap.SimpleEntry<SalesOrderItem, SalesOrderItem> itemPair = 
				findParentAndChildItems(isSplitOrder, deliveredSplitOrder, order, item);
			final SalesOrderItem parentItem = itemPair.getKey();
			final SalesOrderItem childOrderItem = itemPair.getValue();

			if (parentItem == null || childOrderItem == null) {
				resp.setStatus(false);
				resp.setStatusCode("203");
				resp.setStatusMsg("Order Item(s) not found!");
				return resp;
			}

			// Check if quantity requested for return is available from ordered quantity
			QuantityReturned quantityReturned = omsorderentityConverter.getQtyReturned(childOrderItem.getItemId(), false, false);
			BigDecimal qtyReturned = quantityReturned.getQtyReturned();

			if (parentItem.getQtyOrdered() != null) {
				BigDecimal allowableQtyToReturn = parentItem.getQtyOrdered().subtract(qtyReturned);
				if (ObjectUtils.isNotEmpty(parentItem.getQtyCanceled()))
					allowableQtyToReturn = allowableQtyToReturn.subtract(parentItem.getQtyCanceled());

				if (null != item.getReturnQuantity()) {
					BigDecimal requestedReturnQty = new BigDecimal(item.getReturnQuantity());
					if (requestedReturnQty.compareTo(allowableQtyToReturn) > 0) {
						resp.setStatus(false);
						resp.setStatusCode("206");
						resp.setStatusMsg("Qty not available to return!");
						return resp;
					}
				} else {
					resp.setStatus(false);
					resp.setStatusCode("206");
					resp.setStatusMsg("Request Qty is null !!");
					return resp;
				}
			}
		}

		AmastyRmaRequest amastyRmaRequest;
		int rmaThresholdhrs = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();

		try {
			amastyRmaRequest = amastyRmaRequestRepository
					.findFirstByCustomerIdAndOrderIdOrderByCreatedAtDesc(request.getCustomerId(), request.getOrderId());

			if (null != request.getIsDropOffRequest() && request.getIsDropOffRequest()) {
				amastyRmaRequest = null;
			} else if (amastyRmaRequest != null) {
				List<AmastyRmaTracking> amastyTrackingList = amastyRmaTrackingRepository
						.findByRequestId(amastyRmaRequest.getRequestId());
				Instant hrsAgo = Instant.now().minus(rmaThresholdhrs, ChronoUnit.HOURS);
				Timestamp timestampHrsAgo = Timestamp.from(hrsAgo);
				if (amastyRmaRequest.getCreatedAt().compareTo(timestampHrsAgo) < 0
						|| (amastyRmaRequest.getStatus().equals(12) || amastyRmaRequest.getStatus().equals(13))
						|| CollectionUtils.isNotEmpty(amastyTrackingList)) {
					amastyRmaRequest = null;
				}
			}

			if (amastyRmaRequest == null) {
				if (isSplitOrder) {
					// Set the split order ID in the request for RMA creation
					request.setSplitOrderId(deliveredSplitOrder.getEntityId());
					// Pass the split order object to get correct Shukran data
					amastyRmaRequest = rmaHelper.createReturnRequestV2(request, order, deliveredSplitOrder, xClientVersion);
				} else {
					amastyRmaRequest = rmaHelper.createReturnRequestV2(request, order, xClientVersion);
				}
				salesOrderServiceV3Impl.setStatusMessageForRmaUpdate(amastyRmaRequest, "create");
			}
			List<AmastyRmaRequestItem> amastyRmaRequestItems = amastyRmaRequest.getAmastyRmaRequestItems().stream().toList();
			response.setReturnId(amastyRmaRequest.getRequestId());
			response.setRmaIncId(amastyRmaRequest.getRmaIncId());
			BigDecimal returnAmount = new BigDecimal(0);
			for (RMAOrderItemV2Request item : request.getItems()) {
				// Find the correct split order for this item based on parentOrderItemId
				if (isSplitOrder && deliveredSplitOrders != null) {
					deliveredSplitOrder = findSplitOrderForItem(deliveredSplitOrders, item);
				}
				AbstractMap.SimpleEntry<SalesOrderItem, SalesOrderItem> itemPair = 
					findParentAndChildItems(isSplitOrder, deliveredSplitOrder, order, item);
				final SalesOrderItem parentItem = itemPair.getKey();
				final SalesOrderItem childOrderItem = itemPair.getValue();

				if (parentItem != null && parentItem.getQtyOrdered().compareTo(BigDecimal.ZERO) != 0) {
					returnAmount = returnAmount
							.add(parentItem.getRowTotalInclTax().subtract(parentItem.getDiscountAmount())
									.divide(parentItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
									.multiply(new BigDecimal(item.getReturnQuantity())));

					if (childOrderItem != null) {
						Optional<AmastyRmaRequest> optionalAmastyRmaRequest = Optional.of(amastyRmaRequest);
						AmastyRmaRequestItem amastyRmaRequestItem = amastyRmaRequestItems.stream()
								.filter(e -> e.getOrderItemId().equals(childOrderItem.getItemId()) && e.getRequestId().equals(optionalAmastyRmaRequest.get().getRequestId()))
								.findFirst().orElse(null);
						BigDecimal returnQty = BigDecimal.ZERO;
						if(null == amastyRmaRequestItem) {
							amastyRmaRequestItem = new AmastyRmaRequestItem();
							returnQty = new BigDecimal(item.getReturnQuantity());
						}else{
							returnQty =  amastyRmaRequestItem.getQty().add(new BigDecimal(item.getReturnQuantity()));

						}


						amastyRmaRequestItem.setRequestId(amastyRmaRequest.getRequestId());
						amastyRmaRequestItem.setOrderItemId(childOrderItem.getItemId());
						amastyRmaRequestItem.setQty(returnQty);
						amastyRmaRequestItem.setRequestQty(returnQty);
						amastyRmaRequestItem.setReasonId(item.getReasonId());
						amastyRmaRequestItem.setConditionId(2);
						amastyRmaRequestItem.setResolutionId(2);
						amastyRmaRequestItem.setItemStatus(4);
						amastyRmaRequestItem = amastyRmaRequestItemRepository.saveAndFlush(amastyRmaRequestItem);
						amastyRmaRequest.getAmastyRmaRequestItems().add(amastyRmaRequestItem);
					}
				}
			}
			amastyRmaRequest.setRmaPaymentMethod(request.getRmaPaymentMethod());
			if (request.getDropOffDetails() != null && request.getCpId() != null && request.getCityName() != null) {
				response.setCpId(request.getCpId());
				response.setCityName(request.getCityName());
				response.setDropOffDetails(request.getDropOffDetails());
				amastyRmaRequest.setCityName(request.getCityName());
				amastyRmaRequest.setCpId(request.getCpId());
				amastyRmaRequest.setDropOffDetails(request.getDropOffDetails());
			}
			if(request.getReturnIncPayfortId()!= null){
				amastyRmaRequest.setReturnIncPayfortId(request.getReturnIncPayfortId());
			}
			if(request.getReturnFeeAmount()>0){
				amastyRmaRequest.setReturnInvoiceAmount(request.getReturnFeeAmount());
			}
			amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);
		} catch (DataAccessException e) {
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error creating RMA request with message: " + e.getMessage());
			return resp;
		}
		resp.setResponse(response);
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Successfully created RMA request");
		return resp;
	}

	@Override
	public void dropoffCall(OrderResponseDTO resp) {
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set("Authorization", "Bearer " + magentoIntegrationToken);

		MagentoReturnDropOffRequest magentoRefundOrderRequest = new MagentoReturnDropOffRequest();
		magentoRefundOrderRequest.setRequestId(resp.getResponse().getReturnId());

		HttpEntity<MagentoReturnDropOffRequest> requestBody = new HttpEntity<>(magentoRefundOrderRequest,
				requestHeaders);
		String url = magentoBaseUrl + "/en/rest/V1/create-return-awb";
		LOGGER.info("magento url for drop off: " + url + " Body : " + magentoRefundOrderRequest.toString());
		try {
			ResponseEntity<MagentoReturnDropOffAPIResponse[]> response = restTemplate.exchange(url, HttpMethod.POST,
					requestBody, MagentoReturnDropOffAPIResponse[].class);
			if (response.getStatusCode() == HttpStatus.OK) {
				LOGGER.info("reposne Body:" + objectMapper.writeValueAsString(response.getBody()));
				MagentoReturnDropOffAPIResponse[] apiResponseArray = response.getBody();
				if (apiResponseArray != null && apiResponseArray.length > 0) {
					MagentoReturnDropOffAPIResponse returnResponse = apiResponseArray[0];
					if (null != returnResponse && null != returnResponse.getWaybill()
							&& null != returnResponse.getStatusCode() && returnResponse.getStatusCode().equals(200)) {
						OrderResponse orderRes = resp.getResponse();
						orderRes.setAwbNumber(returnResponse.getWaybill());
                        try {
							if(StringUtils.isNotBlank(returnResponse.getLabel())){
								orderRes.setReturnInvoiceLink(gcpStorage.generateSignedUrl(returnResponse.getLabel()));
							}
                        } catch (Exception e) {
							LOGGER.error("Error generating signed URL for label: " + returnResponse.getLabel(), e);
							throw new RuntimeException("Failed to generate return invoice link", e);
						}
                        resp.setResponse(orderRes);
					} else {
						amastyRmaRequestRepository.deleteByItemId(resp.getResponse().getReturnId());
						ErrorType errorType = new ErrorType();
						if (null != returnResponse && null != returnResponse.getStatusCode()) {
							errorType.setErrorCode(returnResponse.getStatusCode().toString());
						}
						if (null != returnResponse) {
							errorType.setErrorMessage(returnResponse.getStatusMsg());
						}
						resp.setResponse(null);
						resp.setStatus(false);
						resp.setStatusCode("205");
						resp.setStatusMsg(Constants.ERROR_FOUND_MSG);
						resp.setError(errorType);
					}
				}
			}
		} catch (RestClientException e) {
			LOGGER.error("exception occoured during magento call:" + e.getMessage());
			amastyRmaRequestRepository.deleteByItemId(resp.getResponse().getReturnId());
			ErrorType errorType = new ErrorType();
			errorType.setErrorCode("209");
			errorType.setErrorMessage(e.getMessage());
			resp.setError(errorType);
			resp.setResponse(null);
			resp.setStatusCode("205");
			resp.setStatus(false);
			resp.setStatusMsg("ERROR");
		} catch (JsonProcessingException e1) {
			LOGGER.error("Exception occoured during parse json:"+e1.getMessage());
			amastyRmaRequestRepository.deleteByItemId(resp.getResponse().getReturnId());
			ErrorType errorType = new ErrorType();
			errorType.setErrorCode("208");
			errorType.setErrorMessage(e1.getMessage());
			resp.setError(errorType);
			resp.setStatusCode("205");
			resp.setResponse(null);
			resp.setStatus(false);
			resp.setStatusMsg("ERROR");
		}

	}

	@Override
	public RMAOrderResponseDTO getCustomerReturnsCount(OrderListRequest request) {
		RMAOrderResponseDTO resp = new RMAOrderResponseDTO();
		if (request.getCustomerId() == null || request.getStoreId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId())).findAny()
				.orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store not found!");
			return resp;
		}

		List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());
		long count = amastyRmaRequestRepository.countByCustomerIdAndStoreIdIn(request.getCustomerId(), storeIds);

		resp.setTotalCount(count);
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg(Constants.STATUS_KEY);
		return resp;
	}

	@Override
	@Transactional(readOnly = true)
	public RMAOrderResponseDTO getcustomerReturns(OrderListRequest request) {
		RMAOrderResponseDTO resp = new RMAOrderResponseDTO();
		if (request.getCustomerId() == null || request.getStoreId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing!");
			return resp;
		}
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId())).findAny()
				.orElse(null);
		LOGGER.info("storeData "+store);
		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store not found!");
			return resp;
		}
		String language = "en";
		if(StringUtils.isNotEmpty(store.getStoreLanguage()) && StringUtils.isNotBlank(store.getStoreLanguage())){
			if(store.getStoreLanguage().contains(language)){
				language = "en";
			}else{
				language ="ar";
			}
		}

		Map<Integer, RMAOrderResponse> responses = new HashMap<>();
		List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());
		Pageable pageableSizeSortedByCreatedAtDesc = PageRequest.of(request.getOffSet(), request.getPageSize(),
				Sort.by("createdAt").descending());
		List<AmastyRmaRequest> amastyRmaRequests = amastyRmaRequestRepository
				.findByCustomerIdAndStoreIdIn(request.getCustomerId(), storeIds, pageableSizeSortedByCreatedAtDesc);
		List<Integer> orderIds = amastyRmaRequests.stream().map(AmastyRmaRequest::getOrderId).collect(Collectors.toList());
		List<SalesOrder> salesOrders = salesOrderRepository.findByEntityIdIn(orderIds);
		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(salesOrders, restTemplate);
		BigDecimal taxFactor = BigDecimal.valueOf(1);
		if(store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO)>0){
			BigDecimal decimalTaxValue= store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
			taxFactor= taxFactor.add(decimalTaxValue);
		}
		for (AmastyRmaRequest amastyRmaRequest : amastyRmaRequests) {
			Integer orderId = amastyRmaRequest.getOrderId();
			Integer requestId = amastyRmaRequest.getRequestId();
			LOGGER.info("Order Data In Get Customer Returns Order Id: {} " + orderId+"Customer Id "+amastyRmaRequest.getCustomerId());
			SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(orderId,amastyRmaRequest.getCustomerId());
			// first find the total amount to be refunded and coin value and storeCredit

			BigDecimal totalAmountToBeRefunded = BigDecimal.ZERO;

			BigDecimal totalAmountToBeRefundedAsCoins= BigDecimal.ZERO;
			BigDecimal totalAmountToBeRefundedAsCredit= BigDecimal.ZERO;
			BigDecimal totalAmountToBeRefundedAsOnline= BigDecimal.ZERO;
			BigDecimal totalShukranPointsToBeRefunded= BigDecimal.ZERO;

			if(order != null) {
				if (CollectionUtils.isNotEmpty(order.getSalesOrderItem())) {
					for (SalesOrderItem item : order.getSalesOrderItem()) {
						if (ObjectUtils.isNotEmpty(item) && StringUtils.isNotEmpty(item.getProductType()) && StringUtils.isNotBlank(item.getProductType()) && item.getProductType().equalsIgnoreCase(SIMPLE_PRODUCT_TYPE)) {


							BigDecimal originalPrice = item.getOriginalPrice();
							BigDecimal priceInclTax = item.getPriceInclTax();
							BigDecimal qtyOrdered = item.getQtyOrdered();
							BigDecimal itemSubTotal = BigDecimal.ZERO;
							BigDecimal itemDiscount1 = BigDecimal.ZERO;

							if (originalPrice != null && qtyOrdered != null && taxFactor != null) {
								itemSubTotal = originalPrice
										.divide(taxFactor, 6, RoundingMode.HALF_UP)
										.multiply(qtyOrdered);
							} else {
								LOGGER.info("Null value encountered: originalPrice, qtyOrdered, or taxFactor is null for ") ;
							}

							if (originalPrice != null && priceInclTax != null && qtyOrdered != null && taxFactor != null) {
								itemDiscount1 = (originalPrice.subtract(priceInclTax))
										.divide(taxFactor, 6, RoundingMode.HALF_UP)
										.multiply(qtyOrdered);
							} else {
								LOGGER.info("Null value encountered: originalPrice, priceInclTax, qtyOrdered, or taxFactor is null for ");
							}

							BigDecimal discountAmount = BigDecimal.ZERO;
							if (item.getParentOrderItem() != null) {
								BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(), item.getParentOrderItem().getItemId());
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
							BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor);

							totalAmountToBeRefunded = totalAmountToBeRefunded.add(itemFinalPrice);
							if(item.getShukranCoinsBurned() != null && item.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && item.getReturnable()==1){
								totalShukranPointsToBeRefunded = totalShukranPointsToBeRefunded.add(item.getShukranCoinsBurned());
							}
						}
					}
				}
				LOGGER.info("totalAmountToBeRefunded" + totalAmountToBeRefunded);

				if (order.getAmstorecreditAmount() != null && order.getAmstorecreditAmount().compareTo(new BigDecimal(0)) > 0) {

					totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit.add(order.getAmstorecreditAmount());
				}
				if (order.getSubSalesOrder() != null) {
					if (order.getSubSalesOrder().getEasValueInCurrency() != null && order.getSubSalesOrder().getEasValueInCurrency().compareTo(new BigDecimal(0)) > 0) {
						totalAmountToBeRefundedAsCoins = order.getSubSalesOrder().getEasValueInCurrency();
					}
				}



				SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
				String PaymentMethod = null;
				if (orderPayment != null && orderPayment.getMethod() != null) {
					PaymentMethod = orderPayment.getMethod();
				}

				BigDecimal totalAmountToBeRefundedAsShukran= BigDecimal.ZERO;
				if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0){
					totalAmountToBeRefundedAsShukran = order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
				}
				if (PaymentMethod != null && (OrderConstants.checkPaymentMethod(PaymentMethod) || OrderConstants.checkBNPLPaymentMethods(PaymentMethod) || PaymentConstants.CASHFREE.equalsIgnoreCase(PaymentMethod))) {
					totalAmountToBeRefundedAsOnline = totalAmountToBeRefunded.subtract(totalAmountToBeRefundedAsCoins).subtract(totalAmountToBeRefundedAsCredit).subtract(totalAmountToBeRefundedAsShukran);
				} else {
					totalAmountToBeRefundedAsCredit = totalAmountToBeRefunded.subtract(totalAmountToBeRefundedAsCoins).subtract(totalAmountToBeRefundedAsShukran);
				}
				LOGGER.info("totalAmountToBeRefunded " + totalAmountToBeRefunded + " " + totalAmountToBeRefundedAsOnline + " " + totalAmountToBeRefundedAsCredit + " " + totalAmountToBeRefundedAsCoins+" "+totalAmountToBeRefundedAsShukran);

				List<AmastyRmaTracking> amastyTracking = amastyRmaTrackingRepository.findByRequestId(requestId);

				RMAOrderResponse response = responses.get(orderId);
				double refundAmountToBeDeducted = 0.0;
				if (amastyRmaRequest.getReturnFee() != null && amastyRmaRequest.getReturnFee() > 0) {
					refundAmountToBeDeducted = amastyRmaRequest.getReturnFee();
				}
				if (response == null) {
					response = setRMAOrderResponse(order, refundAmountToBeDeducted);
				}


				String statusLabel = null;
				String description = null;
				String statusColor = null;
				boolean cancelCTA = false;
				AmastyRmaStatus amastyRmaStatus = amastyRmaStatusRepository.findByStatusId(amastyRmaRequest.getStatus());
				if (amastyRmaStatus != null) {
					statusLabel = amastyRmaStatus.getTitle();
					statusColor = amastyRmaStatus.getColor();
					if (amastyRmaStatus.getState() < 4) {
						cancelCTA = true;
					}
					for (AmastyRmaStatusStore amastyRmaStatusStore : amastyRmaStatus.getAmastyRmaStatusStores()) {
						if (amastyRmaStatusStore.getStoreId().equals(request.getStoreId())) {
							if (amastyRmaStatusStore.getLabel() != null && !amastyRmaStatusStore.getLabel().isEmpty()) {
								statusLabel = amastyRmaStatusStore.getLabel();
							}
							if (amastyRmaStatusStore.getDescription() != null && !amastyRmaStatusStore.getDescription().isEmpty()) {
								description = amastyRmaStatusStore.getDescription();
							}
						}
					}
				}

				setrmaItems(response, amastyRmaRequest, request, order, statusLabel, description,
						statusColor, cancelCTA, amastyTracking, productsFromMulin, totalAmountToBeRefunded, totalAmountToBeRefundedAsCoins, totalAmountToBeRefundedAsCredit, totalAmountToBeRefundedAsOnline, language, taxFactor, totalAmountToBeRefundedAsShukran, store);

				responses.put(orderId, response);
			}
		}

		Map<Integer, RMAOrderResponse> reverseSortedMap = new LinkedHashMap<>();
		responses.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
				.forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

		resp.setResponse(reverseSortedMap);
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg(Constants.STATUS_KEY);
		return resp;
	}

	@Override
	@Transactional(readOnly = true)
	public RMAOrderResponseDTO getCustomerReturnsV2(ReturnListRequest request) {
		RMAOrderResponseDTO resp = new RMAOrderResponseDTO();
		if (request.getCustomerId() == null || request.getStoreId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing!");
			return resp;
		}
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId())).findAny()
				.orElse(null);
		LOGGER.info("storeData "+store);
		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store not found!");
			return resp;
		}
		String language = "en";
		if(StringUtils.isNotEmpty(store.getStoreLanguage()) && StringUtils.isNotBlank(store.getStoreLanguage())){
			if(store.getStoreLanguage().contains(language)){
				language = "en";
			}else{
				language ="ar";
			}
		}

		Map<Integer, RMAOrderResponse> responses = new HashMap<>();
		List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());
		Integer count = amastyRmaRequestRepository.getOrderCountForReturnList(request.getCustomerId(), storeIds);
		resp.setTotalCount(count.longValue());
		List<Integer> orderIds= amastyRmaRequestRepository.getOrderIdsForReturnList(request.getCustomerId(), storeIds, request.getPageSize(), request.getOffSet());
		List<AmastyRmaRequest> amastyRmaRequests = amastyRmaRequestRepository.findByOrderIdIn(orderIds);
		List<SalesOrder> salesOrders = salesOrderRepository.findByEntityIdIn(orderIds);
		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(salesOrders, restTemplate);
		BigDecimal taxFactor = BigDecimal.valueOf(1);
		if(store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO)>0){
			BigDecimal decimalTaxValue= store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
			taxFactor= taxFactor.add(decimalTaxValue);
		}
		for (AmastyRmaRequest amastyRmaRequest : amastyRmaRequests) {
			Integer orderId = amastyRmaRequest.getOrderId();
			Integer requestId = amastyRmaRequest.getRequestId();
			LOGGER.info("Order Data In Get Customer Returns Order Id: {} " + orderId+"Customer Id "+amastyRmaRequest.getCustomerId());
			SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(orderId,amastyRmaRequest.getCustomerId());
			if(order != null) {
				RMAOrderResponse response = responses.get(orderId);
				double refundAmountToBeDeducted = 0.0;
				if (amastyRmaRequest.getReturnFee() != null && amastyRmaRequest.getReturnFee() > 0) {
					refundAmountToBeDeducted = amastyRmaRequest.getReturnFee();
				}
				if (response == null) {
					response = setRMAOrderResponse(order, refundAmountToBeDeducted);
				}
				String statusLabel = null;
				String description = null;
				String statusColor = null;
				boolean cancelCTA = false;
				AmastyRmaStatus amastyRmaStatus = amastyRmaStatusRepository.findByStatusId(amastyRmaRequest.getStatus());

				if (amastyRmaStatus != null) {
					statusLabel = amastyRmaStatus.getTitle();
					statusColor = amastyRmaStatus.getColor();
					if (amastyRmaStatus.getState() < 4) {
						cancelCTA = true;
					}
					for (AmastyRmaStatusStore amastyRmaStatusStore : amastyRmaStatus.getAmastyRmaStatusStores()) {
						if (amastyRmaStatusStore.getStoreId().equals(request.getStoreId())) {
							if (amastyRmaStatusStore.getLabel() != null && !amastyRmaStatusStore.getLabel().isEmpty()) {
								statusLabel = amastyRmaStatusStore.getLabel();
							}
							if (amastyRmaStatusStore.getDescription() != null && !amastyRmaStatusStore.getDescription().isEmpty()) {
								description = amastyRmaStatusStore.getDescription();
							}
						}
					}
				}
				List<AmastyRmaTracking> amastyTracking = amastyRmaTrackingRepository.findByRequestId(requestId);
				setrmaItemsV2(response, amastyRmaRequest, request, order, productsFromMulin, taxFactor, amastyRmaStatus, statusLabel, description, statusColor, cancelCTA, amastyTracking);

				responses.put(orderId, response);
			}
		}

		Map<Integer, RMAOrderResponse> reverseSortedMap = new LinkedHashMap<>();
		responses.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
				.forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

		resp.setResponse(reverseSortedMap);
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg(Constants.STATUS_KEY);
		return resp;
	}

	@Override
	@Transactional(readOnly = true)
	public ReturnItemViewResponseDTO getCustomerReturnItemView(ReturnItemViewRequest request){
		ReturnItemViewResponseDTO resp= new ReturnItemViewResponseDTO();
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId())).findAny()
				.orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store Not Found!");
			return resp;
		}
		String language = "en";
		if(StringUtils.isNotEmpty(store.getStoreLanguage()) && StringUtils.isNotBlank(store.getStoreLanguage())){
			if(store.getStoreLanguage().contains(language)){
				language = "en";
			}else{
				language ="ar";
			}
		}

		AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRequestId(request.getRequestId());

		if(ObjectUtils.isEmpty(rmaRequest)){
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Rma Request Not Found!");
			return resp;
		}

		if(rmaRequest.getAmastyRmaRequestItems() == null || rmaRequest.getAmastyRmaRequestItems().isEmpty()){
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Rma Request Items Not Found!");
			return resp;
		}

		SalesOrder order = salesOrderRepository.findByIncrementId(request.getOrderIncrementId());

		if(ObjectUtils.isEmpty(order)){
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Order Not Found!");
			return resp;
		}

		BigDecimal totalAmountToBeRefunded = BigDecimal.ZERO;
		BigDecimal totalAmountToBeRefundedAsCoins= BigDecimal.ZERO;
		BigDecimal totalAmountToBeRefundedAsCredit= BigDecimal.ZERO;
		BigDecimal totalAmountToBeRefundedAsOnline= BigDecimal.ZERO;
		BigDecimal totalShukranPointsToBeRefunded= BigDecimal.ZERO;

		BigDecimal taxFactor = BigDecimal.valueOf(1);
		if(store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO)>0){
			BigDecimal decimalTaxValue= store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
			taxFactor= taxFactor.add(decimalTaxValue);
		}

		if (CollectionUtils.isNotEmpty(order.getSalesOrderItem())) {
			for (SalesOrderItem item : order.getSalesOrderItem()) {
				if (ObjectUtils.isNotEmpty(item) && StringUtils.isNotEmpty(item.getProductType()) && StringUtils.isNotBlank(item.getProductType()) && item.getProductType().equalsIgnoreCase(SIMPLE_PRODUCT_TYPE) && item.getQtyOrdered().subtract(item.getQtyCanceled()).compareTo(BigDecimal.ZERO)>0 && item.getOriginalPrice() != null && item.getOriginalPrice().compareTo(BigDecimal.ZERO)>0) {

					BigDecimal itemSubTotal = item.getOriginalPrice()
							.divide(taxFactor, 6, RoundingMode.HALF_UP)
							.multiply(item.getQtyOrdered());

					BigDecimal itemDiscount1 = (item.getOriginalPrice().subtract(item.getPriceInclTax()))
							.divide(taxFactor, 6, RoundingMode.HALF_UP)
							.multiply(item.getQtyOrdered());

					BigDecimal discountAmount = BigDecimal.ZERO;
					if (item.getParentOrderItem() != null) {
						BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(), item.getParentOrderItem().getItemId());
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
					BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor).multiply(item.getQtyOrdered().subtract(item.getQtyCanceled())).divide(item.getQtyOrdered(), 6, RoundingMode.HALF_UP);
					totalAmountToBeRefunded = totalAmountToBeRefunded.add(itemFinalPrice);
					if(item.getShukranCoinsBurned() != null && item.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && item.getReturnable()==1){
						totalShukranPointsToBeRefunded = totalShukranPointsToBeRefunded.add(item.getShukranCoinsBurned().multiply(item.getQtyOrdered().subtract(item.getQtyCanceled())).divide(item.getQtyOrdered(), 6, RoundingMode.HALF_UP));
					}
				}
			}
		}
		LOGGER.info("totalAmountToBeRefunded" + totalAmountToBeRefunded);

		if (order.getAmstorecreditAmount() != null && order.getAmstorecreditAmount().compareTo(new BigDecimal(0)) > 0) {

			totalAmountToBeRefundedAsCredit = totalAmountToBeRefundedAsCredit.add(order.getAmstorecreditAmount());
		}
		if (order.getSubSalesOrder() != null) {
			if (order.getSubSalesOrder().getEasValueInCurrency() != null && order.getSubSalesOrder().getEasValueInCurrency().compareTo(new BigDecimal(0)) > 0) {
				totalAmountToBeRefundedAsCoins = order.getSubSalesOrder().getEasValueInCurrency();
			}
		}

		SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		String PaymentMethod = null;
		if (orderPayment != null && orderPayment.getMethod() != null) {
			PaymentMethod = orderPayment.getMethod();
		}

		BigDecimal totalAmountToBeRefundedAsShukran= BigDecimal.ZERO;
		if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0){
			totalAmountToBeRefundedAsShukran = order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
		}
		if (PaymentMethod != null && (OrderConstants.checkPaymentMethod(PaymentMethod) || OrderConstants.checkBNPLPaymentMethods(PaymentMethod) || PaymentConstants.CASHFREE.equalsIgnoreCase(PaymentMethod))) {
			totalAmountToBeRefundedAsOnline = totalAmountToBeRefunded.subtract(totalAmountToBeRefundedAsCoins).subtract(totalAmountToBeRefundedAsCredit).subtract(totalAmountToBeRefundedAsShukran);
		} else {
			totalAmountToBeRefundedAsCredit = totalAmountToBeRefunded.subtract(totalAmountToBeRefundedAsCoins).subtract(totalAmountToBeRefundedAsShukran);
		}
		List<SalesOrder> salesOrders= new ArrayList<>();
		salesOrders.add(order);
		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(salesOrders, restTemplate);
		LOGGER.info("totalAmountToBeRefunded " + totalAmountToBeRefunded + " " + totalAmountToBeRefundedAsOnline + " " + totalAmountToBeRefundedAsCredit + " " + totalAmountToBeRefundedAsCoins+" "+totalAmountToBeRefundedAsShukran);
		RMAItem rmaItem= setRmaItem(store, order, rmaRequest, request, productsFromMulin, taxFactor, language, totalAmountToBeRefunded, totalAmountToBeRefundedAsOnline, totalAmountToBeRefundedAsCredit, totalAmountToBeRefundedAsCoins, totalAmountToBeRefundedAsShukran );

		if(ObjectUtils.isNotEmpty(rmaItem)){
			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Success");
			resp.setItemData(rmaItem);
		}
		return resp;
	}

	private RMAOrderResponse setRMAOrderResponse(SalesOrder order, double refundAmountToBeDeducted) {

		RMAOrderResponse response = new RMAOrderResponse();
		response.setOrderIncrementId(parseNullStr(order.getIncrementId()));
		response.setCustomerId(parseNullStr(order.getCustomerId()));
		response.setCreatedAt(parseNullStr(order.getCreatedAt()));
		response.setDeliveredAt(parseNullStr(order.getDeliveredAt()));
		response.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		response.setReturnAmountFee(refundAmountToBeDeducted);
		if(refundAmountToBeDeducted >0){
			response.setIsSecondReturn(true);
		}
		OrderAddress pickupAddress = setPickupAddress(order);
		response.setPickupAddress(pickupAddress);

		ObjectMapper mapper = new ObjectMapper();
		String paymentInformation = null;
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		if (salesOrderPayment != null) {
			paymentInformation = salesOrderPayment.getAdditionalInformation();
			response.setPaymentMethod(salesOrderPayment.getMethod());    }

		// Expected to fetch card type and number in case of prepaid, apple_pay
		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInformation,
						SalesOrderPaymentInformation.class);
				response.setCardNumber(parseNullStr(salesOrderPaymentInformation.getCardNumber()));
				response.setPaymentOption(parseNullStr(salesOrderPaymentInformation.getPaymentOption()));
			} catch (IOException e) {
				LOGGER.error("jackson mapper error!");
			}
		}

		return response;
	}

	public OrderAddress setPickupAddress(SalesOrder order) {
		for (SalesOrderAddress shippingAddress : order.getSalesOrderAddress()) {
			if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {
				OrderAddress orderAddress = new OrderAddress();
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
                orderAddress.setUnitNumber(shippingAddress.getUnitNumber());
                orderAddress.setPostalCode(shippingAddress.getPostalCode());
                orderAddress.setShortAddress(shippingAddress.getShortAddress());
                orderAddress.setKsaAddressComplaint(shippingAddress.getKsaAddressComplaint());
                orderAddress.setBuildingNumber(parseNullStr(shippingAddress.getBuildingNumber()));
				return orderAddress;
			}
		}
		return null;
	}





	private void setrmaItems(RMAOrderResponse response, AmastyRmaRequest amastyRmaRequest, OrderListRequest request,
							 SalesOrder order, String statusLabel, String description, String statusColor,
							 boolean cancelCTA, List<AmastyRmaTracking> amastyRmaTrackingList,
							 Map<String, ProductResponseBody> productsFromMulin, BigDecimal totalAmountToBeRefunded, BigDecimal totalAmountToBeRefundedAsCoins, BigDecimal totalAmountToBeRefundedAsCredit, BigDecimal totalAmountToBeRefundedAsOnline, String language, BigDecimal taxFactor, BigDecimal totalAmountToBeRefundedAsShukran, Stores store) {

		LOGGER.info("totalAmountToBerefunded"+ totalAmountToBeRefunded);
		String mainRmaStatusLabel = statusLabel;
		SalesCreditmemo salesCreditmemo = null;
		if (amastyRmaRequest.getRequestId() != null && amastyRmaRequest.getRequestId() != 0) {
			List<SalesCreditmemo> creditmemos = salesCreditmemoRepository
					.findByRmaNumber(amastyRmaRequest.getRequestId().toString());
			if (CollectionUtils.isNotEmpty(creditmemos)) {
				for (SalesCreditmemo memo : creditmemos) {
					if (memo != null) {
						salesCreditmemo = memo;
						break;
					}
				}
			}
		}

		LOGGER.info("order Currencies+ "+ order.getOrderCurrencyCode());
		String expiryDate = findPaymentLinkExpiryDate(amastyRmaRequest);
		BigDecimal[] totalSubTotalValue = {BigDecimal.ZERO};
		BigDecimal totalShukranRefundPoints= BigDecimal.ZERO;
		BigDecimal totalShukranRefundValuePoints=BigDecimal.ZERO;
		boolean isShukranAvailable= false;
		for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequest.getAmastyRmaRequestItems()) {
			ArrayList<BreakDownItem> breakDownItems = new ArrayList<>();
			RMAItem rmaItem = new RMAItem();
			String originalStatiusLabel = "";
			rmaItem.setRequestId(parseNullStr(amastyRmaRequest.getRequestId()));
			rmaItem.setRmaIncrementId(parseNullStr(amastyRmaRequest.getRmaIncId()));
			rmaItem.setRequestItemId(parseNullStr(amastyRmaRequestItem.getRequestItemId()));
			rmaItem.setQty(parseNullStr(amastyRmaRequestItem.getQty()));
			rmaItem.setRmaPaymentMethod(amastyRmaRequest.getRmaPaymentMethod());
			rmaItem.setRmaPaymentLink(amastyRmaRequest.getUrlHash());
			rmaItem.setPaymentExpireOn(expiryDate);
			rmaItem.setRefundTrasactionNumber(
					Objects.nonNull(salesCreditmemo) ? salesCreditmemo.getReconciliationReference() : "");
			QuantityReturned quantityReturned = omsorderentityConverter
					.getQtyReturned(amastyRmaRequestItem.getRequestItemId(), true, true);
			AmastyRmaStatus amastyRmaStatus = amastyRmaStatusRepository
					.findByStatusId(amastyRmaRequestItem.getItemStatus());
			rmaItem.setReturnOutletType(amastyRmaRequest.getCpId());
			rmaItem.setReturnCreatedAt(parseNullStr(amastyRmaRequest.getCreatedAt()));
			rmaItem.setReturnModifiedAt(parseNullStr(amastyRmaRequest.getModifiedAt()));


			if (null != amastyRmaRequest.getShortPickup() && amastyRmaRequest.getShortPickup().equals(1)) {

				if (amastyRmaStatus != null) {

					statusLabel = amastyRmaStatus.getTitle();
					statusColor = amastyRmaStatus.getColor();
					if (amastyRmaStatus.getState() < 4) {
						cancelCTA = true;
					}
					for (AmastyRmaStatusStore amastyRmaStatusStore : amastyRmaStatus.getAmastyRmaStatusStores()) {
						if (amastyRmaStatusStore.getStoreId().equals(request.getStoreId())) {
							if (amastyRmaStatusStore.getLabel() != null && !amastyRmaStatusStore.getLabel().isEmpty()) {
								statusLabel = amastyRmaStatusStore.getLabel();
							}
							if (amastyRmaStatusStore.getDescription() != null
									&& !amastyRmaStatusStore.getDescription().isEmpty()) {
								description = amastyRmaStatusStore.getDescription();
							}
						}
					}
				}
				rmaItem.setStatus(parseNullStr(amastyRmaRequest.getStatus()));
				rmaItem.setStatusLabel(parseNullStr(statusLabel));
			} else {

				rmaItem.setStatus(parseNullStr(amastyRmaRequestItem.getItemStatus()));
				rmaItem.setStatusLabel(mainRmaStatusLabel);
			}

			if (ObjectUtils.isEmpty(amastyRmaRequestItem.getItemStatus()) || amastyRmaRequestItem.getItemStatus() == 1
					|| amastyRmaRequestItem.getItemStatus() == 0) {
				rmaItem.setStatus(parseNullStr(amastyRmaRequest.getStatus()));
				rmaItem.setStatusLabel(mainRmaStatusLabel);
			}

			originalStatiusLabel = rmaItem.getStatusLabel();

			if (null != quantityReturned) {

				rmaItem.setReturnedQty(parseNullStr(quantityReturned.getQtyReturned()));
				rmaItem.setQcFailed(parseNullStr(quantityReturned.getQcFaildQty()));
				boolean shortmessageFlag = false;
				boolean qcFailedFlag = false;
				String message = null;
				ConsulValues orderConsulValues = new ConsulValues();

				String msgValue = Constants.getOrderConsulValues().get("orderConsulKeys");

				try {
					orderConsulValues = objectMapper.readValue(msgValue, ConsulValues.class);
					LOGGER.info("orderConsulValues:" + orderConsulValues.getReturnQcFailedMultiplePick());
				} catch (JsonProcessingException e1) {
					LOGGER.error("error in parse");
				}

				message = orderConsulValues.getReturnShortSinglepickSms();
				if (null != amastyRmaRequestItem.getQty() && null != amastyRmaRequestItem.getActualQuantyReturned()
						&& amastyRmaRequestItem.getActualQuantyReturned().intValue() != amastyRmaRequestItem.getQty()
						.intValue()) {
					int missQty = 0;
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() == 0) {
						missQty = amastyRmaRequestItem.getQty().intValue()
								- quantityReturned.getQcFaildQty().intValue();
					} else if(26 == amastyRmaRequestItem.getItemStatus()) { // Partial Return
						missQty = quantityReturned.getQtyMissing();
					}else {
						missQty = amastyRmaRequestItem.getActualQuantyReturned().intValue();
					}
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() <= 1) {
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortMultipickAR();
						}
						message = StringUtils.replace(message, "X", String.valueOf(missQty));
					} else {
						message = orderConsulValues.getReturnShortMultipickSms();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortMultipickAR();
						}
						message = StringUtils.replace(message, "X", String.valueOf(missQty));
					}
					statusLabel = message;
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue()
							+ quantityReturned.getQcFaildQty().intValue() == amastyRmaRequestItem.getQty().intValue()) {
						shortmessageFlag = false;
					} else {
						shortmessageFlag = true;
					}

				}
				if (null != quantityReturned && quantityReturned.getQcFaildQty().intValue() > 0) {
					if (quantityReturned.getQcFaildQty().intValue() == 1) {
						message = orderConsulValues.getReturnQcFailedSinglePick();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnQcFailedSinglePickAr();
						}
						message = StringUtils.replace(message, "X", quantityReturned.getQcFaildQty().toString());
					} else {
						message = orderConsulValues.getReturnQcFailedMultiplePick();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnQcFailedMultiplePickAr();
						}
						message = StringUtils.replace(message, "X", quantityReturned.getQcFaildQty().toString());
					}
					statusLabel = message;
					qcFailedFlag = true;
				}

				if (qcFailedFlag && shortmessageFlag) {
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() == 1) {
						message = orderConsulValues.getReturnShortQcFailedSinglePick();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortQcFailedSinglePickAr();
						}
					} else {
						message = orderConsulValues.getReturnShortQcFailedMultiplePic();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortQcFailedMultiplePicAr();
						}
					}

					int missQty = 0;
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() == 0) {
						missQty = amastyRmaRequestItem.getQty().intValue()
								- quantityReturned.getQcFaildQty().intValue();
					} else {

						missQty = amastyRmaRequestItem.getActualQuantyReturned().intValue();
					}

					message = StringUtils.replace(message, "X", String.valueOf(missQty));
					message = StringUtils.replace(message, "Y", quantityReturned.getQcFaildQty().toString());
					statusLabel = message;
				}

				if (!qcFailedFlag && !shortmessageFlag) {
					statusLabel = originalStatiusLabel;
				}
				if (null != amastyRmaRequestItem.getQty() && null != quantityReturned.getQcFaildQty()
						&& null != amastyRmaRequestItem.getActualQuantyReturned() && amastyRmaRequestItem
						.getActualQuantyReturned().intValue() != amastyRmaRequestItem.getQty().intValue()) {
					rmaItem.setNonRefundMessage(true);
				}
				if (null != amastyRmaRequestItem.getQty() && null != quantityReturned.getQcFaildQty()
						&& quantityReturned.getQcFaildQty().intValue() > 0
						&& amastyRmaRequestItem.getQty().intValue() == quantityReturned.getQcFaildQty().intValue()) {

					rmaItem.setNonRefundMessage(true);

				}
				if (null != amastyRmaRequestItem.getQty()
						&& ObjectUtils.isNotEmpty(amastyRmaRequestItem.getActualQuantyReturned())
						&& amastyRmaRequestItem.getActualQuantyReturned().intValue() > 0 && amastyRmaRequestItem
						.getQty().intValue() > amastyRmaRequestItem.getActualQuantyReturned().intValue()) {

					rmaItem.setPartiallyRefundMessage(true);

				}
				if (null != amastyRmaRequestItem.getQty() && ObjectUtils.isNotEmpty(quantityReturned.getQcFaildQty())
						&& quantityReturned.getQcFaildQty().intValue() > 0
						&& amastyRmaRequestItem.getQty().intValue() > quantityReturned.getQcFaildQty().intValue()) {

					rmaItem.setPartiallyRefundMessage(true);
				}
			}

			rmaItem.setStatusLabel(statusLabel);
			rmaItem.setDescription(parseNullStr(description));
			rmaItem.setStatusColor(parseNullStr(statusColor));
			rmaItem.setCancelCTA(parseNullStr(cancelCTA));

			boolean zeroQty = false;
			if (amastyRmaRequestItem.getQty().toString().equals("0.0000")) {
				zeroQty = true;
			}

			AmastyRmaReason amastyRmaReason = amastyRmaReasonRepository
					.findByReasonId(amastyRmaRequestItem.getReasonId());
			if (amastyRmaReason != null) {
				String reasonTitle = amastyRmaReason.getTitle();
				for (AmastyRmaReasonStore amastyRmaReasonStore : amastyRmaReason.getAmastyRmaReasonStores()) {
					if (amastyRmaReasonStore.getStoreId().equals(request.getStoreId())
							&& amastyRmaReasonStore.getLabel() != null && !amastyRmaReasonStore.getLabel().isEmpty()) {
						reasonTitle = amastyRmaReasonStore.getLabel();
					}
				}
				rmaItem.setReason(reasonTitle);
			}

			if (salesCreditmemo != null && salesCreditmemo.getCreatedAt() != null) {

				rmaItem.setRefundAt(
						UtilityConstant.ConvertTimeZone(salesCreditmemo.getCreatedAt(), amastyRmaRequest.getStoreId()));
			}

			SalesOrderItem childItem = order.getSalesOrderItem().stream()
					.filter(e -> e.getItemId().equals(amastyRmaRequestItem.getOrderItemId())).findFirst().orElse(null);
			if (childItem != null) {
				SalesOrderItem parentItem = order.getSalesOrderItem().stream()
						.filter(e -> null != childItem.getParentOrderItem()
								&& e.getItemId().equals(childItem.getParentOrderItem().getItemId()))
						.findFirst().orElse(null);

				if (parentItem != null) {
					rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));
					rmaItem.setName(parseNullStr(parentItem.getName()));
					rmaItem.setSku(parseNullStr(parentItem.getSku()));
					rmaItem.setOrderedQty(parseNullStr(parentItem.getQtyOrdered()));

					if (parentItem.getPriceInclTax() != null && parentItem.getOriginalPrice() != null) {
						BigDecimal itemOriginalPrice= parentItem.getOriginalPrice().multiply(amastyRmaRequestItem.getQty()).setScale(2, RoundingMode.HALF_UP);
						rmaItem.setOriginalPrice(parseNullStr(itemOriginalPrice));
						BigDecimal itemSubTotal = parentItem.getOriginalPrice().divide(taxFactor, 6, RoundingMode.HALF_UP)
								.multiply(parentItem.getQtyOrdered());;
						BigDecimal itemDiscount1 = (parentItem.getOriginalPrice()
								.subtract(parentItem.getPriceInclTax()))
								.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered());

						BigDecimal discountAmount= BigDecimal.ZERO;

						if(parentItem.getParentOrderItem()!=null) {
							BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(), parentItem.getParentOrderItem().getItemId());
							if (subSalesOrderDiscountAmount != null) {
								discountAmount = subSalesOrderDiscountAmount;
							}
						}else if(parentItem.getSubSalesOrderItem() != null){

							for(SubSalesOrderItem i: parentItem.getSubSalesOrderItem()){
								if(i.isGiftVoucher()){
									discountAmount = i.getDiscount();
								}
							}

						}
						LOGGER.info("discountAmount: "+ discountAmount);

						BigDecimal itemDiscount2 = (parentItem.getDiscountAmount()
								.subtract(discountAmount))
								.divide(taxFactor, 6, RoundingMode.HALF_UP);
						BigDecimal itemTotalDiscount= itemDiscount1.add(itemDiscount2);
						BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemTotalDiscount);
						BigDecimal ItemFinalPrice = itemTaxablePrice.multiply(taxFactor);
						BigDecimal ItemIndividualPrice= ItemFinalPrice.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_DOWN);
						BigDecimal ItemIndividualDiscount= itemTotalDiscount.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_UP);
						BigDecimal ItemGrandPrice= ItemIndividualPrice.multiply(amastyRmaRequestItem.getQty());
						if(parentItem.getShukranCoinsBurned() !=null && parentItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
							totalShukranRefundPoints = totalShukranRefundPoints.add(parentItem.getShukranCoinsBurned().divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_DOWN).multiply(amastyRmaRequestItem.getQty()).setScale(2, RoundingMode.HALF_UP));
						}

						rmaItem.setGrandTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						LOGGER.info(parentItem.getPrice());

						BigDecimal divideValue= new BigDecimal(100);
						BigDecimal ItemCoinValue= BigDecimal.ZERO;
						BigDecimal ItemStoreCreditValue = BigDecimal.ZERO;
						BigDecimal ItemPrepaidValue= BigDecimal.ZERO;
						BigDecimal itemShukranValue= BigDecimal.ZERO;
						if(totalAmountToBeRefunded.compareTo(BigDecimal.ZERO)>0) {
							BigDecimal ItemPercentageToOriginalPrice = ItemGrandPrice.multiply(divideValue).divide(totalAmountToBeRefunded, 6, RoundingMode.HALF_UP);
							ItemCoinValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsCoins).divide(divideValue, 6, RoundingMode.HALF_UP);
							ItemStoreCreditValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsCredit).divide(divideValue, 6, RoundingMode.HALF_UP);
							ItemPrepaidValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsOnline).divide(divideValue, 6, RoundingMode.HALF_UP);
							itemShukranValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsShukran).divide(divideValue, 6, RoundingMode.HALF_UP);
						}
						rmaItem.setCoinValue(parseNullStr(ItemCoinValue.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setStoreCreditRefundValue(parseNullStr(ItemStoreCreditValue.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setSubTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setReturnGrandTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setPrice(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setShukranValue(itemShukranValue);
						BigDecimal itemDiscountPercentage= BigDecimal.ZERO;
						if(itemOriginalPrice.compareTo(BigDecimal.ZERO)>0) {
							itemDiscountPercentage = ItemGrandPrice.multiply(divideValue).divide(itemOriginalPrice, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
						}
						rmaItem.setDiscount(parseNullStr(divideValue.subtract(itemDiscountPercentage).setScale(2,RoundingMode.HALF_UP)));

						if(rmaItem.getRmaIncrementId().equalsIgnoreCase(amastyRmaRequest.getRmaIncId())) {
							totalSubTotalValue[0] = totalSubTotalValue[0].add(ItemGrandPrice);
						}
						if(ItemStoreCreditValue.compareTo(new BigDecimal(0))>0){
							String label="Styli Credit";
							if(language.equals("ar")){
								label="ائتمان ستايلي";
							}
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(label);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_STORE_CREDIT);
							breakDownItem.setPattern("");
							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + ItemStoreCreditValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
						}
						if(itemShukranValue.compareTo(new BigDecimal(0))>0){
							Configs configs = ((Constants.getOmsBaseConfigs() != null &&
									Constants.getOmsBaseConfigs().getConfigs() != null)
									? Constants.getOmsBaseConfigs().getConfigs() : new Configs());
							String lang = "en";
							if (store != null && StringUtils.isNotBlank(store.getStoreLanguage())) {
								String[] chunk = store.getStoreLanguage().split("_");
								lang = (chunk.length > 0 && StringUtils.isNotBlank(chunk[0])) ? chunk[0].toLowerCase() : lang;
							}
							Integer shukranPointsToDisplay= itemShukranValue.divide(store.getShukranPointConversion(), 6, RoundingMode.HALF_DOWN).intValue();
							rmaItem.setShukranValueInCoins(shukranPointsToDisplay);
							String coinLabel = configs.getTranslationValue(configs.getPaymentMethodTranslations(), "shukran_coin", lang, "Shukran Points (" + shukranPointsToDisplay + ")");
							coinLabel = coinLabel.replace(SPEND_COIN, String.valueOf(shukranPointsToDisplay));
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(coinLabel);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_SHUKRAN_POINTS);
							breakDownItem.setPattern("");

							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + itemShukranValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
							totalShukranRefundValuePoints= totalShukranRefundValuePoints.add(itemShukranValue);
							isShukranAvailable = true;
							rmaItem.setIsShukranPaymentInOrder(true);
						}
						if(ItemCoinValue.compareTo(new BigDecimal(0))>0){
							String label="Styli Cash";
							if(language.equals("ar")){
								label="نقد ستايلي";
							}
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(label);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_STYLI_COIN);
							breakDownItem.setPattern("");
							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + ItemCoinValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
						}
						if(ItemPrepaidValue.compareTo(new BigDecimal(0))>0){
							String label="Card Payment";
							if(language.equals("ar")){
								label="بطاقه ائتمان";
							}
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(label);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_PREPAID);
							breakDownItem.setPattern("");
							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + ItemPrepaidValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
						}
						rmaItem.setReturnBreakDown(breakDownItems);
					}

					boolean returnCategoryRestriction = false;
					for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
						ProductResponseBody productDetailsFromMulin = entry.getValue();
						Variant variant = productDetailsFromMulin.getVariants().stream()
								.filter(e -> e.getSku().equals(parentItem.getSku())).findAny().orElse(null);
						if (variant != null) {
							if (variant.getSizeLabels() != null)
								rmaItem.setSize(parseNullStr(variant.getSizeLabels().getEn()));

							returnCategoryRestriction = !productDetailsFromMulin.getIsReturnApplicable();
							rmaItem.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));

							if (productDetailsFromMulin.getMediaGallery() != null
									&& !productDetailsFromMulin.getMediaGallery().isEmpty()) {
								GalleryItem galleryItem = productDetailsFromMulin.getMediaGallery().get(0);
								if (galleryItem != null)
									rmaItem.setImage(galleryItem.getValue());
							}
						}
					}
					rmaItem.setAvailableNow(true);
					rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));

				}
			}
			if (CollectionUtils.isNotEmpty(amastyRmaTrackingList) && null != amastyRmaRequestItem.getItemStatus()
					&& !(amastyRmaRequestItem.getItemStatus().equals(12)
					|| amastyRmaRequestItem.getItemStatus().equals(13))) {

				rmaItem.setAwbNumber(amastyRmaTrackingList.get(0).getTrackingNumber());
			}
			if (null != amastyRmaRequest.getReturnType() && amastyRmaRequest.getReturnType().equals(1)) {

				rmaItem.setIsReturnTypeDropOff(true);
			}
			String shippingLabel = amastyRmaRequest.getShippingLabel();
			if (!StringUtils.startsWith(shippingLabel, "https://")) {
				shippingLabel = StringUtils.replace(shippingLabel, "http://", "https://");
			}
            try {
				if(StringUtils.isNotBlank(shippingLabel)){
					rmaItem.setReturnInvoiceLink(gcpStorage.generateSignedUrl(shippingLabel));
				}
            } catch (Exception e) {
				LOGGER.error("Error generating signed URL for shipping label: " + shippingLabel, e);
				throw new RuntimeException("Failed to generate shipping label link", e);
			}
            if(rmaItem.getStatus().equals("25")){
				rmaItem.setNonRefundMessage(true);
				rmaItem.setRefundTrasactionNumber(null);
				rmaItem.setRefundAt(null);
			}

			String color= amastyRmaStatusRepository.findColorByStatusId(rmaItem.getStatus());
			LOGGER.info("Final Rma Item: "+ rmaItem + "color " + color);
			rmaItem.setStatusColor(parseNullStr(color));

			rmaItem.setReturnChargeApplicable(false);
			rmaItem.setReturnAmountToBePay(parseNullStr(BigDecimal.ZERO));
			response.getItems().add(rmaItem);
		}

		BigDecimal total = totalSubTotalValue[0];
		if (amastyRmaRequest.getReturnFee() != null && amastyRmaRequest.getReturnFee() > 0 && total.compareTo(BigDecimal.ZERO)>0) {
			BigDecimal divideValue= new BigDecimal(100);
			// Access the accumulated value
			for (RMAItem item: response.getItems()){
				if(item.getRmaIncrementId().equalsIgnoreCase(amastyRmaRequest.getRmaIncId())){
					String subTotal = item.getSubTotal();
					BigDecimal returnFee= BigDecimal.valueOf(amastyRmaRequest.getReturnFee());
					if (StringUtils.isNotBlank(subTotal) && amastyRmaRequest.getRmaIncId().equalsIgnoreCase(item.getRmaIncrementId())) {
						BigDecimal subTotalValue = new BigDecimal(subTotal);
						if (subTotalValue.compareTo(BigDecimal.ZERO) > 0) {
							BigDecimal percentageOfSubTotalToOriginal= subTotalValue.divide(total, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(4, RoundingMode.HALF_UP);
							BigDecimal returnFeePortion= percentageOfSubTotalToOriginal.divide(divideValue, 6, RoundingMode.HALF_DOWN).multiply(returnFee).setScale(2,RoundingMode.HALF_DOWN).setScale(4, RoundingMode.HALF_DOWN);
							if(returnFeePortion.compareTo(returnFee)>0){
								returnFeePortion= returnFee;
							}
							item.setReturnAmountFee(parseNullStr(returnFeePortion.setScale(2, RoundingMode.HALF_DOWN)));
							BigDecimal newSubTotal= BigDecimal.ZERO;
							BigDecimal subTotalValueToShow = BigDecimal.ZERO;
							BigDecimal newPrepaidTotal= BigDecimal.ZERO;
							if(isShukranAvailable){
								subTotalValueToShow = subTotalValue;
								subTotalValue = subTotalValue.subtract(item.getShukranValue());
								item.setShukranValue(item.getShukranValue().setScale(2, RoundingMode.HALF_UP));
							}
							if(returnFeePortion.compareTo(subTotalValue)>0){
								item.setReturnChargeApplicable(true);

								item.getReturnBreakDown().clear();
								item.setCoinValue(parseNullStr(BigDecimal.ZERO));
								item.setStoreCreditRefundValue(parseNullStr(BigDecimal.ZERO));
								BigDecimal chargeAbleAmount= returnFeePortion.subtract(subTotalValue);
								item.setReturnAmountToBePay(parseNullStr(chargeAbleAmount.setScale(2, RoundingMode.HALF_UP)));
								item.setSubTotal(String.valueOf(subTotalValue.setScale(2, RoundingMode.HALF_UP)));
								subTotalValueToShow = item.getShukranValue();
								item.setShukranValue(item.getShukranValue().setScale(2, RoundingMode.HALF_UP));
							}else{
								newSubTotal= subTotalValue.subtract(returnFeePortion);
								newPrepaidTotal= subTotalValue.subtract(returnFeePortion);
								subTotalValueToShow = subTotalValueToShow.subtract(returnFeePortion);
							}
							if(StringUtils.isNotBlank(amastyRmaRequest.getReturnIncPayfortId()) && StringUtils.isNotEmpty(amastyRmaRequest.getReturnIncPayfortId())){
								item.getReturnBreakDown().clear();
								item.setCoinValue(parseNullStr(BigDecimal.ZERO));
								item.setStoreCreditRefundValue(parseNullStr(BigDecimal.ZERO));
								item.setReturnFeeMerchantReference(amastyRmaRequest.getReturnIncPayfortId());
								item.setReturnFeePaymentMode("Card");
								item.setSubTotal(String.valueOf(subTotalValue.setScale(2, RoundingMode.HALF_UP)));
								subTotalValueToShow = item.getShukranValue();
								item.setShukranValue(item.getShukranValue().setScale(2, RoundingMode.HALF_UP));
							}
							if(StringUtils.isNotBlank(item.getStoreCreditRefundValue()) && StringUtils.isNotEmpty(item.getStoreCreditRefundValue()) && new BigDecimal(item.getStoreCreditRefundValue()).compareTo(new BigDecimal(0))>0){
								BigDecimal storeCreditValue= new BigDecimal(item.getStoreCreditRefundValue());
								BigDecimal storeCreditValuePercentageToOriginal= storeCreditValue.divide(subTotalValue, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
								BigDecimal newStoreCreditValue= storeCreditValuePercentageToOriginal.divide(divideValue, 6, RoundingMode.HALF_UP).multiply(newSubTotal).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
								newPrepaidTotal= newPrepaidTotal.subtract(newStoreCreditValue);
								Integer indexValue= item.getReturnBreakDown().indexOf(new BreakDownItem(null,ReturnConstants.BREAKDOWN_TYPE_STORE_CREDIT, null,null ));
								LOGGER.info("IndexValue "+ indexValue);
								if(indexValue>-1){

									item.getReturnBreakDown().get(indexValue).setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + newStoreCreditValue.setScale(2,RoundingMode.HALF_UP));
								}
							}

							if(StringUtils.isNotBlank(item.getCoinValue()) && StringUtils.isNotEmpty(item.getCoinValue()) && new BigDecimal(item.getCoinValue()).compareTo(new BigDecimal(0))>0){
								BigDecimal storeCoinValue= new BigDecimal(item.getCoinValue());
								BigDecimal storeCoinValuePercentageToOriginal= storeCoinValue.divide(subTotalValue, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
								BigDecimal newStoreCoinValue=  storeCoinValuePercentageToOriginal.divide(divideValue, 6, RoundingMode.HALF_UP).multiply(newSubTotal).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
								newPrepaidTotal= newPrepaidTotal.subtract(newStoreCoinValue);
								Integer indexValue= item.getReturnBreakDown().indexOf(new BreakDownItem(null,ReturnConstants.BREAKDOWN_TYPE_STYLI_COIN, null,null ));
								LOGGER.info("IndexValue "+ indexValue);
								if(indexValue>-1){
									item.getReturnBreakDown().get(indexValue).setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + newStoreCoinValue.setScale(2,RoundingMode.HALF_UP));
								}
							}
							if(newPrepaidTotal.compareTo(new BigDecimal(0))>0){
								Integer indexValue= item.getReturnBreakDown().indexOf(new BreakDownItem(null,ReturnConstants.BREAKDOWN_TYPE_PREPAID, null,null ));
								if(indexValue>-1){
									item.getReturnBreakDown().get(indexValue).setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + newPrepaidTotal.setScale(2,RoundingMode.HALF_UP));
								}
							}
							item.setReturnGrandTotal(subTotalValueToShow.compareTo(BigDecimal.ZERO)>0 ? parseNullStr(subTotalValueToShow.setScale(2,RoundingMode.HALF_UP)) : parseNullStr(newSubTotal.setScale(2,RoundingMode.HALF_UP)));
						}
					}
				}

			};

		}

	}

	private RMAItem setRmaItem(Stores store, SalesOrder order, AmastyRmaRequest amastyRmaRequest, ReturnItemViewRequest request, Map<String, ProductResponseBody> productsFromMulin, BigDecimal taxFactor, String language, BigDecimal totalAmountToBeRefunded, BigDecimal totalAmountToBeRefundedAsOnline, BigDecimal totalAmountToBeRefundedAsCredit, BigDecimal totalAmountToBeRefundedAsCoins, BigDecimal totalAmountToBeRefundedAsShukran){
		RMAItem rmaItem = new RMAItem();
		ArrayList<BreakDownItem> breakDownItems = new ArrayList<>();
		String statusLabel = null;
		String description = null;
		String statusColor = null;
		boolean cancelCTA = false;
		AmastyRmaStatus amastyRmaStatus = amastyRmaStatusRepository.findByStatusId(amastyRmaRequest.getStatus());

		SalesCreditmemo salesCreditmemo = null;
		if (amastyRmaRequest.getRequestId() != null && amastyRmaRequest.getRequestId() != 0) {
			List<SalesCreditmemo> creditMemos = salesCreditmemoRepository
					.findByRmaNumber(amastyRmaRequest.getRequestId().toString());
			if (CollectionUtils.isNotEmpty(creditMemos)) {
				for (SalesCreditmemo memo : creditMemos) {
					if (memo != null) {
						salesCreditmemo = memo;
						break;
					}
				}
			}
		}

		if (salesCreditmemo != null && salesCreditmemo.getCreatedAt() != null) {
			rmaItem.setRefundAt(UtilityConstant.ConvertTimeZone(salesCreditmemo.getCreatedAt(), amastyRmaRequest.getStoreId()));
		}

		LOGGER.info("order Currencies+ "+ order.getOrderCurrencyCode());
		String expiryDate = findPaymentLinkExpiryDate(amastyRmaRequest);
		rmaItem.setRequestId(parseNullStr(amastyRmaRequest.getRequestId()));
		rmaItem.setRmaIncrementId(parseNullStr(amastyRmaRequest.getRmaIncId()));
		rmaItem.setRmaPaymentMethod(amastyRmaRequest.getRmaPaymentMethod());
		rmaItem.setReturnOutletType(amastyRmaRequest.getCpId());
		rmaItem.setReturnCreatedAt(parseNullStr(amastyRmaRequest.getCreatedAt()));
		rmaItem.setReturnModifiedAt(parseNullStr(amastyRmaRequest.getModifiedAt()));
		rmaItem.setRmaPaymentLink(amastyRmaRequest.getUrlHash());
		rmaItem.setPaymentExpireOn(expiryDate);
		rmaItem.setRefundTrasactionNumber(Objects.nonNull(salesCreditmemo) ? salesCreditmemo.getReconciliationReference() : "");

		BigDecimal totalSubTotalValue = BigDecimal.ZERO;
		for(AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequest.getAmastyRmaRequestItems()) {
			boolean isRightItem= amastyRmaRequestItem.getRequestItemId().equals(request.getRequestItemId());
			if(isRightItem) {
				rmaItem.setRequestItemId(parseNullStr(amastyRmaRequestItem.getRequestItemId()));
				rmaItem.setQty(parseNullStr(amastyRmaRequestItem.getQty()));

				QuantityReturned quantityReturned = omsorderentityConverter.getQtyReturned(amastyRmaRequestItem.getRequestItemId(), true, true);
				String status = parseNullStr(amastyRmaRequestItem.getItemStatus());
				if (amastyRmaStatus != null) {
					statusLabel = amastyRmaStatus.getTitle();
					statusColor = amastyRmaStatus.getColor();
					if (amastyRmaStatus.getState() < 4) {
						cancelCTA = true;
					}
					for (AmastyRmaStatusStore amastyRmaStatusStore : amastyRmaStatus.getAmastyRmaStatusStores()) {
						if (amastyRmaStatusStore.getStoreId().equals(request.getStoreId())) {
							if (amastyRmaStatusStore.getLabel() != null && !amastyRmaStatusStore.getLabel().isEmpty()) {
								statusLabel = amastyRmaStatusStore.getLabel();
							}
							if (amastyRmaStatusStore.getDescription() != null && !amastyRmaStatusStore.getDescription().isEmpty()) {
								description = amastyRmaStatusStore.getDescription();
							}
						}
					}
					status = (parseNullStr(amastyRmaRequest.getStatus()));

				}

				rmaItem.setStatusLabel(statusLabel);
				rmaItem.setDescription(parseNullStr(description));
				rmaItem.setStatusColor(parseNullStr(statusColor));
				rmaItem.setCancelCTA(parseNullStr(cancelCTA));
				AmastyRmaReason amastyRmaReason = amastyRmaReasonRepository.findByReasonId(amastyRmaRequestItem.getReasonId());
				if (amastyRmaReason != null) {
					String reasonTitle = amastyRmaReason.getTitle();
					for (AmastyRmaReasonStore amastyRmaReasonStore : amastyRmaReason.getAmastyRmaReasonStores()) {
						if (amastyRmaReasonStore.getStoreId().equals(request.getStoreId())
								&& amastyRmaReasonStore.getLabel() != null && !amastyRmaReasonStore.getLabel().isEmpty()) {
							reasonTitle = amastyRmaReasonStore.getLabel();
						}
					}
					rmaItem.setReason(reasonTitle);
				}
				rmaItem.setStatus(parseNullStr(amastyRmaRequestItem.getItemStatus()));
				if (null != amastyRmaRequest.getShortPickup() && amastyRmaRequest.getShortPickup().equals(1)) {
					rmaItem.setStatus(status);
				}
			}
			SalesOrderItem childItem = order.getSalesOrderItem().stream().filter(e -> e.getItemId().equals(amastyRmaRequestItem.getOrderItemId())).findFirst().orElse(null);
			if (childItem != null) {
				SalesOrderItem parentItem = order.getSalesOrderItem().stream()
						.filter(e -> null != childItem.getItemId()
								&& e.getItemId().equals(childItem.getItemId()))
						.findFirst().orElse(null);

				if (parentItem != null) {
					if(isRightItem) {
						rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));
						rmaItem.setName(parseNullStr(parentItem.getName()));
						rmaItem.setSku(parseNullStr(parentItem.getSku()));
						rmaItem.setOrderedQty(parseNullStr(parentItem.getQtyOrdered()));
					}

					if (parentItem.getPriceInclTax() != null && parentItem.getOriginalPrice() != null) {
						BigDecimal itemOriginalPrice= parentItem.getOriginalPrice().multiply(amastyRmaRequestItem.getQty()).setScale(2, RoundingMode.HALF_UP);

						BigDecimal itemSubTotal = parentItem.getOriginalPrice().divide(taxFactor, 6, RoundingMode.HALF_UP)
								.multiply(parentItem.getQtyOrdered());;
						BigDecimal itemDiscount1 = (parentItem.getOriginalPrice()
								.subtract(parentItem.getPriceInclTax()))
								.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered());

						BigDecimal discountAmount= BigDecimal.ZERO;

						if(parentItem.getParentOrderItem()!=null) {
							BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(), parentItem.getParentOrderItem().getItemId());
							if (subSalesOrderDiscountAmount != null) {
								discountAmount = subSalesOrderDiscountAmount;
							}
						}else if(parentItem.getSubSalesOrderItem() != null){

							for(SubSalesOrderItem i: parentItem.getSubSalesOrderItem()){
								if(i.isGiftVoucher()){
									discountAmount = i.getDiscount();
								}
							}

						}
						LOGGER.info("discountAmount: "+ discountAmount);

						BigDecimal itemDiscount2 = (parentItem.getDiscountAmount()
								.subtract(discountAmount))
								.divide(taxFactor, 6, RoundingMode.HALF_UP);
						BigDecimal itemTotalDiscount= itemDiscount1.add(itemDiscount2);
						BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemTotalDiscount);
						BigDecimal ItemFinalPrice = itemTaxablePrice.multiply(taxFactor);
						BigDecimal ItemIndividualPrice= ItemFinalPrice.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_DOWN);
						BigDecimal ItemIndividualDiscount= itemTotalDiscount.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_UP);
						BigDecimal ItemGrandPrice= ItemIndividualPrice.multiply(amastyRmaRequestItem.getQty());
						BigDecimal totalShukranRefundPoints = BigDecimal.ZERO;
						if(parentItem.getShukranCoinsBurned() !=null && parentItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
							totalShukranRefundPoints = totalShukranRefundPoints.add(parentItem.getShukranCoinsBurned().divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_DOWN).multiply(amastyRmaRequestItem.getQty()).setScale(2, RoundingMode.HALF_UP));
						}

						rmaItem.setGrandTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						LOGGER.info(parentItem.getPrice());

						BigDecimal divideValue= new BigDecimal(100);
						BigDecimal ItemCoinValue= BigDecimal.ZERO;
						BigDecimal ItemStoreCreditValue = BigDecimal.ZERO;
						BigDecimal ItemPrepaidValue= BigDecimal.ZERO;
						BigDecimal itemShukranValue= BigDecimal.ZERO;
						if(totalAmountToBeRefunded.compareTo(BigDecimal.ZERO)>0) {
							BigDecimal ItemPercentageToOriginalPrice = ItemGrandPrice.multiply(divideValue).divide(totalAmountToBeRefunded, 6, RoundingMode.HALF_UP);
							ItemCoinValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsCoins).divide(divideValue, 6, RoundingMode.HALF_UP);
							ItemStoreCreditValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsCredit).divide(divideValue, 6, RoundingMode.HALF_UP);
							ItemPrepaidValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsOnline).divide(divideValue, 6, RoundingMode.HALF_UP);
							itemShukranValue = ItemPercentageToOriginalPrice.multiply(totalAmountToBeRefundedAsShukran).divide(divideValue, 6, RoundingMode.HALF_UP);
						}
						if(isRightItem) {
							rmaItem.setCoinValue(parseNullStr(ItemCoinValue.setScale(2, RoundingMode.HALF_UP)));
							rmaItem.setStoreCreditRefundValue(parseNullStr(ItemStoreCreditValue.setScale(2, RoundingMode.HALF_UP)));
							rmaItem.setSubTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
							rmaItem.setReturnGrandTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
							rmaItem.setPrice(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
							rmaItem.setOriginalPrice(parseNullStr(itemOriginalPrice));
							rmaItem.setShukranValue(itemShukranValue);
							String shippingLabel = amastyRmaRequest.getShippingLabel();
							if (!StringUtils.startsWith(shippingLabel, "https://")) {
								shippingLabel = StringUtils.replace(shippingLabel, "http://", "https://");
							}
                            try {
								if (StringUtils.isNotBlank(shippingLabel)) {
									rmaItem.setReturnInvoiceLink(gcpStorage.generateSignedUrl(shippingLabel));
								}
							}catch (Exception e) {
								LOGGER.error("Error generating signed URL for RMA item shipping label: " + shippingLabel, e);
								throw new RuntimeException("Failed to generate RMA item shipping label link", e);
							}
                        }
						BigDecimal itemDiscountPercentage= BigDecimal.ZERO;
						if(itemOriginalPrice.compareTo(BigDecimal.ZERO)>0) {
							itemDiscountPercentage = ItemGrandPrice.multiply(divideValue).divide(itemOriginalPrice, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
						}
						if(isRightItem) {
							rmaItem.setDiscount(parseNullStr(divideValue.subtract(itemDiscountPercentage).setScale(2, RoundingMode.HALF_UP)));
						}


						totalSubTotalValue = totalSubTotalValue.add(ItemGrandPrice);

						if(ItemStoreCreditValue.compareTo(new BigDecimal(0))>0 && isRightItem){
							String label="Styli Credit";
							if(language.equals("ar")){
								label="ائتمان ستايلي";
							}
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(label);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_STORE_CREDIT);
							breakDownItem.setPattern("");
							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + ItemStoreCreditValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
						}
						if(itemShukranValue.compareTo(new BigDecimal(0))>0 && isRightItem){
							Configs configs = ((Constants.getOmsBaseConfigs() != null &&
									Constants.getOmsBaseConfigs().getConfigs() != null)
									? Constants.getOmsBaseConfigs().getConfigs() : new Configs());
							String lang = "en";
							if (store != null && StringUtils.isNotBlank(store.getStoreLanguage())) {
								String[] chunk = store.getStoreLanguage().split("_");
								lang = (chunk.length > 0 && StringUtils.isNotBlank(chunk[0])) ? chunk[0].toLowerCase() : lang;
							}
							String shukranPointsToDisplay= String.valueOf(itemShukranValue.divide(store.getShukranPointConversion(), 6, RoundingMode.HALF_DOWN).intValue());
							String coinLabel = configs.getTranslationValue(configs.getPaymentMethodTranslations(), "shukran_coin", lang, "Shukran Points (" + shukranPointsToDisplay + ")");
							coinLabel = coinLabel.replace("{{spend_coin}}", shukranPointsToDisplay);
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(coinLabel);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_SHUKRAN_POINTS);
							breakDownItem.setPattern("");

							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + itemShukranValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
//							totalShukranRefundValuePoints= totalShukranRefundValuePoints.add(itemShukranValue);
						}
						if(ItemCoinValue.compareTo(new BigDecimal(0))>0 && isRightItem){
							String label="Styli Cash";
							if(language.equals("ar")){
								label="نقد ستايلي";
							}
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(label);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_STYLI_COIN);
							breakDownItem.setPattern("");
							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + ItemCoinValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
						}
						if(ItemPrepaidValue.compareTo(new BigDecimal(0))>0 && isRightItem){
							String label="Card Payment";
							if(language.equals("ar")){
								label="بطاقه ائتمان";
							}
							BreakDownItem breakDownItem= new BreakDownItem();
							breakDownItem.setLabel(label);
							breakDownItem.setType(ReturnConstants.BREAKDOWN_TYPE_PREPAID);
							breakDownItem.setPattern("");
							breakDownItem.setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + ItemPrepaidValue.setScale(2, RoundingMode.HALF_UP));
							breakDownItems.add(breakDownItem);
						}
						if(isRightItem) {
							rmaItem.setReturnBreakDown(breakDownItems);
						}
					}

					boolean returnCategoryRestriction = false;
					for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
						ProductResponseBody productDetailsFromMulin = entry.getValue();
						Variant variant = productDetailsFromMulin.getVariants().stream()
								.filter(e -> e.getSku().equals(parentItem.getSku())).findAny().orElse(null);
						if (variant != null && isRightItem) {
							if (variant.getSizeLabels() != null)
								rmaItem.setSize(parseNullStr(variant.getSizeLabels().getEn()));

							returnCategoryRestriction = !productDetailsFromMulin.getIsReturnApplicable();
							rmaItem.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));

							if (productDetailsFromMulin.getMediaGallery() != null
									&& !productDetailsFromMulin.getMediaGallery().isEmpty()) {
								GalleryItem galleryItem = productDetailsFromMulin.getMediaGallery().get(0);
								if (galleryItem != null)
									rmaItem.setImage(galleryItem.getValue());
							}
						}
					}
					rmaItem.setAvailableNow(true);
					rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));

				}
			}
			List<AmastyRmaTracking> amastyRmaTrackingList = amastyRmaTrackingRepository.findByRequestId(amastyRmaRequest.getRequestId());
			if (CollectionUtils.isNotEmpty(amastyRmaTrackingList) && null != amastyRmaRequestItem.getItemStatus()
					&& !(amastyRmaRequestItem.getItemStatus().equals(12)
					|| amastyRmaRequestItem.getItemStatus().equals(13))) {

				rmaItem.setAwbNumber(amastyRmaTrackingList.get(0).getTrackingNumber());
			}
		}

		if (null != amastyRmaRequest.getReturnType() && amastyRmaRequest.getReturnType().equals(1)) {

			rmaItem.setIsReturnTypeDropOff(true);

		}
		if(rmaItem.getShukranValue() != null && rmaItem.getShukranValue().compareTo(BigDecimal.ZERO)>0){
			rmaItem.setShukranValueInCoins(rmaItem.getShukranValue().divide(store.getShukranPointConversion(), 2, RoundingMode.HALF_UP).intValue());
			rmaItem.setIsShukranPaymentInOrder(true);
		}
		String color= amastyRmaStatusRepository.findColorByStatusId(rmaItem.getStatus());
		LOGGER.info("Final Rma Item: "+ rmaItem + "color " + color);
		rmaItem.setStatusColor(parseNullStr(color));


		if (amastyRmaRequest.getReturnFee() != null && amastyRmaRequest.getReturnFee() > 0 && totalSubTotalValue.compareTo(BigDecimal.ZERO)>0) {
			BigDecimal divideValue= new BigDecimal(100);
			// Access the accumulated value
			String subTotal = rmaItem.getSubTotal();
			BigDecimal returnFee= BigDecimal.valueOf(amastyRmaRequest.getReturnFee());
			if (StringUtils.isNotBlank(subTotal) && amastyRmaRequest.getRmaIncId().equalsIgnoreCase(rmaItem.getRmaIncrementId())) {
				BigDecimal subTotalValue = new BigDecimal(subTotal);
				if (subTotalValue.compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal percentageOfSubTotalToOriginal= subTotalValue.divide(totalSubTotalValue, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(4, RoundingMode.HALF_UP);
					BigDecimal returnFeePortion= percentageOfSubTotalToOriginal.divide(divideValue, 6, RoundingMode.HALF_DOWN).multiply(returnFee).setScale(2,RoundingMode.HALF_DOWN).setScale(4, RoundingMode.HALF_DOWN);
					if(returnFeePortion.compareTo(returnFee)>0){
						returnFeePortion= returnFee;
					}
					rmaItem.setReturnAmountFee(parseNullStr(returnFeePortion.setScale(2, RoundingMode.HALF_DOWN)));
					BigDecimal newSubTotal= BigDecimal.ZERO;
					BigDecimal newPrepaidTotal= BigDecimal.ZERO;
					if(returnFeePortion.compareTo(subTotalValue)>0){
						rmaItem.getReturnBreakDown().clear();
						rmaItem.setCoinValue(parseNullStr(BigDecimal.ZERO));
						rmaItem.setStoreCreditRefundValue(parseNullStr(BigDecimal.ZERO));
						BigDecimal chargeAbleAmount= returnFeePortion.subtract(subTotalValue);
						rmaItem.setReturnAmountToBePay(parseNullStr(chargeAbleAmount.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setReturnChargeApplicable(true);
					}else{
						newSubTotal= subTotalValue.subtract(returnFeePortion);
						newPrepaidTotal= subTotalValue.subtract(returnFeePortion);
					}
					if(StringUtils.isNotBlank(amastyRmaRequest.getReturnIncPayfortId()) && StringUtils.isNotEmpty(amastyRmaRequest.getReturnIncPayfortId())){
						rmaItem.getReturnBreakDown().clear();
						rmaItem.setCoinValue(parseNullStr(BigDecimal.ZERO));
						rmaItem.setStoreCreditRefundValue(parseNullStr(BigDecimal.ZERO));
						rmaItem.setReturnFeeMerchantReference(amastyRmaRequest.getReturnIncPayfortId());
						rmaItem.setReturnFeePaymentMode("Card");
					}
					if(StringUtils.isNotBlank(rmaItem.getStoreCreditRefundValue()) && StringUtils.isNotEmpty(rmaItem.getStoreCreditRefundValue()) && new BigDecimal(rmaItem.getStoreCreditRefundValue()).compareTo(new BigDecimal(0))>0){
						BigDecimal storeCreditValue= new BigDecimal(rmaItem.getStoreCreditRefundValue());
						BigDecimal storeCreditValuePercentageToOriginal= storeCreditValue.divide(subTotalValue, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						BigDecimal newStoreCreditValue= storeCreditValuePercentageToOriginal.divide(divideValue, 6, RoundingMode.HALF_UP).multiply(newSubTotal).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						newPrepaidTotal= newPrepaidTotal.subtract(newStoreCreditValue);
						Integer indexValue= rmaItem.getReturnBreakDown().indexOf(new BreakDownItem(null,ReturnConstants.BREAKDOWN_TYPE_STORE_CREDIT, null,null ));
						LOGGER.info("IndexValue "+ indexValue);
						if(indexValue>-1){

							rmaItem.getReturnBreakDown().get(indexValue).setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + newStoreCreditValue.setScale(2,RoundingMode.HALF_UP));
						}
					}

					if(StringUtils.isNotBlank(rmaItem.getCoinValue()) && StringUtils.isNotEmpty(rmaItem.getCoinValue()) && new BigDecimal(rmaItem.getCoinValue()).compareTo(new BigDecimal(0))>0){
						BigDecimal storeCoinValue= new BigDecimal(rmaItem.getCoinValue());
						BigDecimal storeCoinValuePercentageToOriginal= storeCoinValue.divide(subTotalValue, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						BigDecimal newStoreCoinValue=  storeCoinValuePercentageToOriginal.divide(divideValue, 6, RoundingMode.HALF_UP).multiply(newSubTotal).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						newPrepaidTotal= newPrepaidTotal.subtract(newStoreCoinValue);
						Integer indexValue= rmaItem.getReturnBreakDown().indexOf(new BreakDownItem(null,ReturnConstants.BREAKDOWN_TYPE_STYLI_COIN, null,null ));
						LOGGER.info("IndexValue "+ indexValue);
						if(indexValue>-1){

							rmaItem.getReturnBreakDown().get(indexValue).setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + newStoreCoinValue.setScale(2,RoundingMode.HALF_UP));
						}
					}
					if(newPrepaidTotal.compareTo(new BigDecimal(0))>0){
						Integer indexValue= rmaItem.getReturnBreakDown().indexOf(new BreakDownItem(null,ReturnConstants.BREAKDOWN_TYPE_PREPAID, null,null ));
						if(indexValue>-1){
							rmaItem.getReturnBreakDown().get(indexValue).setValue(((StringUtils.isNotEmpty(order.getOrderCurrencyCode())) ? order.getOrderCurrencyCode() + " " : "") + newPrepaidTotal.setScale(2,RoundingMode.HALF_UP));
						}
					}
					rmaItem.setReturnGrandTotal(parseNullStr(newSubTotal.setScale(2,RoundingMode.HALF_UP)));

				}



			};

		}
		return rmaItem;
	}

	private void setrmaItemsV2(RMAOrderResponse response, AmastyRmaRequest amastyRmaRequest, ReturnListRequest request,
							 SalesOrder order, Map<String, ProductResponseBody> productsFromMulin, BigDecimal taxFactor, AmastyRmaStatus amastyRmaStatus, String statusLabel, String description,  String statusColor,  boolean cancelCTA, List<AmastyRmaTracking> amastyTracking) {

		String mainRmaStatusLabel = statusLabel;
		SalesCreditmemo salesCreditmemo = null;
		if (amastyRmaRequest.getRequestId() != null && amastyRmaRequest.getRequestId() != 0) {
			List<SalesCreditmemo> creditmemos = salesCreditmemoRepository
					.findByRmaNumber(amastyRmaRequest.getRequestId().toString());
			if (CollectionUtils.isNotEmpty(creditmemos)) {
				for (SalesCreditmemo memo : creditmemos) {
					if (memo != null) {
						salesCreditmemo = memo;
						break;
					}
				}
			}
		}

		LOGGER.info("order Currencies+ "+ order.getOrderCurrencyCode());
		String expiryDate = findPaymentLinkExpiryDate(amastyRmaRequest);

		for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequest.getAmastyRmaRequestItems()) {
			RMAItem rmaItem = new RMAItem();
			String originalStatiusLabel = "";
			rmaItem.setRequestId(parseNullStr(amastyRmaRequest.getRequestId()));
			rmaItem.setRmaIncrementId(parseNullStr(amastyRmaRequest.getRmaIncId()));
			rmaItem.setRequestItemId(parseNullStr(amastyRmaRequestItem.getRequestItemId()));
			rmaItem.setQty(parseNullStr(amastyRmaRequestItem.getQty()));
			rmaItem.setRmaPaymentMethod(amastyRmaRequest.getRmaPaymentMethod());
			rmaItem.setRmaPaymentLink(amastyRmaRequest.getUrlHash());
			rmaItem.setPaymentExpireOn(expiryDate);
			rmaItem.setRefundTrasactionNumber(
					Objects.nonNull(salesCreditmemo) ? salesCreditmemo.getReconciliationReference() : "");
			QuantityReturned quantityReturned = omsorderentityConverter
					.getQtyReturned(amastyRmaRequestItem.getRequestItemId(), true, true);
			rmaItem.setReturnOutletType(amastyRmaRequest.getCpId());
			rmaItem.setReturnCreatedAt(parseNullStr(amastyRmaRequest.getCreatedAt()));
			rmaItem.setReturnModifiedAt(parseNullStr(amastyRmaRequest.getModifiedAt()));

			if (null != amastyRmaRequest.getShortPickup() && amastyRmaRequest.getShortPickup().equals(1)) {

				if (amastyRmaStatus != null) {

					statusLabel = amastyRmaStatus.getTitle();
					statusColor = amastyRmaStatus.getColor();
					if (amastyRmaStatus.getState() < 4) {
						cancelCTA = true;
					}
					for (AmastyRmaStatusStore amastyRmaStatusStore : amastyRmaStatus.getAmastyRmaStatusStores()) {
						if (amastyRmaStatusStore.getStoreId().equals(request.getStoreId())) {
							if (amastyRmaStatusStore.getLabel() != null && !amastyRmaStatusStore.getLabel().isEmpty()) {
								statusLabel = amastyRmaStatusStore.getLabel();
							}
							if (amastyRmaStatusStore.getDescription() != null
									&& !amastyRmaStatusStore.getDescription().isEmpty()) {
								description = amastyRmaStatusStore.getDescription();
							}
						}
					}
				}
				rmaItem.setStatus(parseNullStr(amastyRmaRequest.getStatus()));
				rmaItem.setStatusLabel(parseNullStr(statusLabel));
			} else {

				rmaItem.setStatus(parseNullStr(amastyRmaRequestItem.getItemStatus()));
				rmaItem.setStatusLabel(mainRmaStatusLabel);
			}

			if (ObjectUtils.isEmpty(amastyRmaRequestItem.getItemStatus()) || amastyRmaRequestItem.getItemStatus() == 1
					|| amastyRmaRequestItem.getItemStatus() == 0) {
				rmaItem.setStatus(parseNullStr(amastyRmaRequest.getStatus()));
				rmaItem.setStatusLabel(mainRmaStatusLabel);
			}

			originalStatiusLabel = rmaItem.getStatusLabel();

			if (null != quantityReturned) {

				rmaItem.setReturnedQty(parseNullStr(quantityReturned.getQtyReturned()));
				rmaItem.setQcFailed(parseNullStr(quantityReturned.getQcFaildQty()));
				boolean shortmessageFlag = false;
				boolean qcFailedFlag = false;
				String message = null;
				ConsulValues orderConsulValues = new ConsulValues();

				String msgValue = Constants.getOrderConsulValues().get("orderConsulKeys");

				try {
					orderConsulValues = objectMapper.readValue(msgValue, ConsulValues.class);
					LOGGER.info("orderConsulValues:" + orderConsulValues.getReturnQcFailedMultiplePick());
				} catch (JsonProcessingException e1) {
					LOGGER.error("error in parse");
				}

				message = orderConsulValues.getReturnShortSinglepickSms();
				if (null != amastyRmaRequestItem.getQty() && null != amastyRmaRequestItem.getActualQuantyReturned()
						&& amastyRmaRequestItem.getActualQuantyReturned().intValue() != amastyRmaRequestItem.getQty()
						.intValue()) {
					int missQty = 0;
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() == 0) {
						missQty = amastyRmaRequestItem.getQty().intValue()
								- quantityReturned.getQcFaildQty().intValue();
					} else if(26 == amastyRmaRequestItem.getItemStatus()) { // Partial Return
						missQty = quantityReturned.getQtyMissing();
					}else {
						missQty = amastyRmaRequestItem.getActualQuantyReturned().intValue();
					}
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() <= 1) {
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortMultipickAR();
						}
						message = StringUtils.replace(message, "X", String.valueOf(missQty));
					} else {
						message = orderConsulValues.getReturnShortMultipickSms();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortMultipickAR();
						}
						message = StringUtils.replace(message, "X", String.valueOf(missQty));
					}
					statusLabel = message;
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue()
							+ quantityReturned.getQcFaildQty().intValue() == amastyRmaRequestItem.getQty().intValue()) {
						shortmessageFlag = false;
					} else {
						shortmessageFlag = true;
					}

				}
				if (null != quantityReturned && quantityReturned.getQcFaildQty().intValue() > 0) {
					if (quantityReturned.getQcFaildQty().intValue() == 1) {
						message = orderConsulValues.getReturnQcFailedSinglePick();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnQcFailedSinglePickAr();
						}
						message = StringUtils.replace(message, "X", quantityReturned.getQcFaildQty().toString());
					} else {
						message = orderConsulValues.getReturnQcFailedMultiplePick();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnQcFailedMultiplePickAr();
						}
						message = StringUtils.replace(message, "X", quantityReturned.getQcFaildQty().toString());
					}
					statusLabel = message;
					qcFailedFlag = true;
				}

				if (qcFailedFlag && shortmessageFlag) {
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() == 1) {
						message = orderConsulValues.getReturnShortQcFailedSinglePick();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortQcFailedSinglePickAr();
						}
					} else {
						message = orderConsulValues.getReturnShortQcFailedMultiplePic();
						if (Constants.ARABIC_STORES.contains(request.getStoreId())) {
							message = orderConsulValues.getReturnShortQcFailedMultiplePicAr();
						}
					}

					int missQty = 0;
					if (amastyRmaRequestItem.getActualQuantyReturned().intValue() == 0) {
						missQty = amastyRmaRequestItem.getQty().intValue()
								- quantityReturned.getQcFaildQty().intValue();
					} else {

						missQty = amastyRmaRequestItem.getActualQuantyReturned().intValue();
					}

					message = StringUtils.replace(message, "X", String.valueOf(missQty));
					message = StringUtils.replace(message, "Y", quantityReturned.getQcFaildQty().toString());
					statusLabel = message;
				}

				if (!qcFailedFlag && !shortmessageFlag) {
					statusLabel = originalStatiusLabel;
				}
				if (null != amastyRmaRequestItem.getQty() && null != quantityReturned.getQcFaildQty()
						&& null != amastyRmaRequestItem.getActualQuantyReturned() && amastyRmaRequestItem
						.getActualQuantyReturned().intValue() != amastyRmaRequestItem.getQty().intValue()) {
					rmaItem.setNonRefundMessage(true);
				}
				if (null != amastyRmaRequestItem.getQty() && null != quantityReturned.getQcFaildQty()
						&& quantityReturned.getQcFaildQty().intValue() > 0
						&& amastyRmaRequestItem.getQty().intValue() == quantityReturned.getQcFaildQty().intValue()) {

					rmaItem.setNonRefundMessage(true);

				}
				if (null != amastyRmaRequestItem.getQty()
						&& ObjectUtils.isNotEmpty(amastyRmaRequestItem.getActualQuantyReturned())
						&& amastyRmaRequestItem.getActualQuantyReturned().intValue() > 0 && amastyRmaRequestItem
						.getQty().intValue() > amastyRmaRequestItem.getActualQuantyReturned().intValue()) {

					rmaItem.setPartiallyRefundMessage(true);

				}
				if (null != amastyRmaRequestItem.getQty() && ObjectUtils.isNotEmpty(quantityReturned.getQcFaildQty())
						&& quantityReturned.getQcFaildQty().intValue() > 0
						&& amastyRmaRequestItem.getQty().intValue() > quantityReturned.getQcFaildQty().intValue()) {

					rmaItem.setPartiallyRefundMessage(true);
				}
			}

			rmaItem.setStatusLabel(statusLabel);
			rmaItem.setDescription(parseNullStr(description));
			rmaItem.setStatusColor(parseNullStr(statusColor));
			rmaItem.setCancelCTA(parseNullStr(cancelCTA));

			boolean zeroQty = false;
			if (amastyRmaRequestItem.getQty().toString().equals("0.0000")) {
				zeroQty = true;
			}

			AmastyRmaReason amastyRmaReason = amastyRmaReasonRepository
					.findByReasonId(amastyRmaRequestItem.getReasonId());
			if (amastyRmaReason != null) {
				String reasonTitle = amastyRmaReason.getTitle();
				for (AmastyRmaReasonStore amastyRmaReasonStore : amastyRmaReason.getAmastyRmaReasonStores()) {
					if (amastyRmaReasonStore.getStoreId().equals(request.getStoreId())
							&& amastyRmaReasonStore.getLabel() != null && !amastyRmaReasonStore.getLabel().isEmpty()) {
						reasonTitle = amastyRmaReasonStore.getLabel();
					}
				}
				rmaItem.setReason(reasonTitle);
			}

			if (salesCreditmemo != null && salesCreditmemo.getCreatedAt() != null) {

				rmaItem.setRefundAt(
						UtilityConstant.ConvertTimeZone(salesCreditmemo.getCreatedAt(), amastyRmaRequest.getStoreId()));
			}

			SalesOrderItem childItem = order.getSalesOrderItem().stream()
					.filter(e -> e.getItemId().equals(amastyRmaRequestItem.getOrderItemId())).findFirst().orElse(null);
			if (childItem != null) {
				SalesOrderItem parentItem = order.getSalesOrderItem().stream()
						.filter(e -> null != childItem.getItemId()
								&& e.getItemId().equals(childItem.getItemId()))
						.findFirst().orElse(null);

				if (parentItem != null) {
					rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));
					rmaItem.setName(parseNullStr(parentItem.getName()));
					rmaItem.setSku(parseNullStr(parentItem.getSku()));
					rmaItem.setOrderedQty(parseNullStr(parentItem.getQtyOrdered()));

					if (parentItem.getPriceInclTax() != null && parentItem.getOriginalPrice() != null) {
						BigDecimal itemOriginalPrice= parentItem.getOriginalPrice().multiply(amastyRmaRequestItem.getQty()).setScale(2, RoundingMode.HALF_UP);
						rmaItem.setOriginalPrice(parseNullStr(itemOriginalPrice));
						BigDecimal itemSubTotal = parentItem.getOriginalPrice().divide(taxFactor, 6, RoundingMode.HALF_UP)
								.multiply(parentItem.getQtyOrdered());;
						BigDecimal itemDiscount1 = (parentItem.getOriginalPrice()
								.subtract(parentItem.getPriceInclTax()))
								.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered());

						BigDecimal discountAmount= BigDecimal.ZERO;

						if(parentItem.getParentOrderItem()!=null) {
							BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(), parentItem.getParentOrderItem().getItemId());
							if (subSalesOrderDiscountAmount != null) {
								discountAmount = subSalesOrderDiscountAmount;
							}
						}else if(parentItem.getSubSalesOrderItem() != null){

							for(SubSalesOrderItem i: parentItem.getSubSalesOrderItem()){
								if(i.isGiftVoucher()){
									discountAmount = i.getDiscount();
								}
							}

						}
						LOGGER.info("discountAmount: "+ discountAmount);

						BigDecimal itemDiscount2 = (parentItem.getDiscountAmount()
								.subtract(discountAmount))
								.divide(taxFactor, 6, RoundingMode.HALF_UP);
						BigDecimal itemTotalDiscount= itemDiscount1.add(itemDiscount2);
						BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemTotalDiscount);
						BigDecimal ItemFinalPrice = itemTaxablePrice.multiply(taxFactor);
						BigDecimal ItemIndividualPrice= ItemFinalPrice.divide(parentItem.getQtyOrdered(), 6, RoundingMode.HALF_DOWN);

						BigDecimal ItemGrandPrice= ItemIndividualPrice.multiply(amastyRmaRequestItem.getQty());


						rmaItem.setGrandTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						LOGGER.info(parentItem.getPrice());

						BigDecimal divideValue= new BigDecimal(100);

						rmaItem.setSubTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setReturnGrandTotal(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));
						rmaItem.setPrice(parseNullStr(ItemGrandPrice.setScale(2, RoundingMode.HALF_UP)));

						BigDecimal itemDiscountPercentage= BigDecimal.ZERO;
						if(itemOriginalPrice.compareTo(BigDecimal.ZERO)>0) {
							itemDiscountPercentage = ItemGrandPrice.multiply(divideValue).divide(itemOriginalPrice, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
						}
						rmaItem.setDiscount(parseNullStr(divideValue.subtract(itemDiscountPercentage).setScale(2,RoundingMode.HALF_UP)));

					}

					boolean returnCategoryRestriction = false;
					for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
						ProductResponseBody productDetailsFromMulin = entry.getValue();
						Variant variant = productDetailsFromMulin.getVariants().stream()
								.filter(e -> e.getSku().equals(parentItem.getSku())).findAny().orElse(null);
						if (variant != null) {
							if (variant.getSizeLabels() != null)
								rmaItem.setSize(parseNullStr(variant.getSizeLabels().getEn()));

							returnCategoryRestriction = !productDetailsFromMulin.getIsReturnApplicable();
							rmaItem.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));

							if (productDetailsFromMulin.getMediaGallery() != null
									&& !productDetailsFromMulin.getMediaGallery().isEmpty()) {
								GalleryItem galleryItem = productDetailsFromMulin.getMediaGallery().get(0);
								if (galleryItem != null)
									rmaItem.setImage(galleryItem.getValue());
							}
						}
					}
					rmaItem.setAvailableNow(true);
					rmaItem.setParentProductId(parseNullStr(parentItem.getProductId()));

				}
			}
			if (CollectionUtils.isNotEmpty(amastyTracking) && null != amastyRmaRequestItem.getItemStatus()
					&& !(amastyRmaRequestItem.getItemStatus().equals(12)
					|| amastyRmaRequestItem.getItemStatus().equals(13))) {

				rmaItem.setAwbNumber(amastyTracking.get(0).getTrackingNumber());
			}
			if (null != amastyRmaRequest.getReturnType() && amastyRmaRequest.getReturnType().equals(1)) {

				rmaItem.setIsReturnTypeDropOff(true);
			}
			String shippingLabel = amastyRmaRequest.getShippingLabel();
			if (!StringUtils.startsWith(shippingLabel, "https://")) {
				shippingLabel = StringUtils.replace(shippingLabel, "http://", "https://");
			}
            try {
				if (StringUtils.isNotBlank(shippingLabel)) {
					rmaItem.setReturnInvoiceLink(gcpStorage.generateSignedUrl(shippingLabel));
				}
			} catch (Exception e) {
				LOGGER.error("Error generating signed URL for V2 RMA item shipping label: " + shippingLabel, e);
				throw new RuntimeException("Failed to generate V2 RMA item shipping label link", e);
			}
            if(rmaItem.getStatus().equals("25")){
				rmaItem.setNonRefundMessage(true);
				rmaItem.setRefundTrasactionNumber(null);
				rmaItem.setRefundAt(null);
			}

			String color= amastyRmaStatusRepository.findColorByStatusId(rmaItem.getStatus());
			LOGGER.info("Final Rma Item: "+ rmaItem + "color " + color);
			rmaItem.setStatusColor(parseNullStr(color));

			rmaItem.setReturnChargeApplicable(false);
			rmaItem.setReturnAmountToBePay(parseNullStr(BigDecimal.ZERO));
			response.getItems().add(rmaItem);
		}
	}

	private String findPaymentLinkExpiryDate(AmastyRmaRequest amastyRmaRequest) {
		try {
			if (!Regions.INDIA.equals(region) || Objects.isNull(amastyRmaRequest.getRmaPaymentExpireOn()))
				return "";
			String rmaPaymentExpireOn = amastyRmaRequest.getRmaPaymentExpireOn();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Date expDate = sdf.parse(rmaPaymentExpireOn);
			SimpleDateFormat sdf1 = new SimpleDateFormat("d MMM");
			String expiryDate = sdf1.format(expDate);
			return "link expires on " + expiryDate;
		} catch (Exception e) {
			LOGGER.error("Error in parsing cashfree payment link expiry date. " + e);
		}
		return "";

	}

	private RMAOrderInitV2Response setupReturnTotalAndBreakdown(
			RMAOrderInitV2Response rmaOrderInitV2Response,
			SalesOrder order,
			Integer storeId,
			BigDecimal refundGrandTotal,
			BigDecimal refundStoreCreditTotal,
			BigDecimal refundPrepaidTotal,
			BigDecimal refundStoreCoinsTotal,
			double refundAmountToBeDeducted, Stores store, int refundShukranTotal, BigDecimal refundShukranValue) {

		ArrayList<BreakDownItem> breakDownItems = new ArrayList<>();
		try {
			if(order != null && rmaOrderInitV2Response != null) {

				BigDecimal newReturnFee= BigDecimal.valueOf(refundAmountToBeDeducted);
				BigDecimal divideValue= new BigDecimal(100);
				String lang = "en";
				if (store != null && StringUtils.isNotBlank(store.getStoreLanguage())) {
					String[] chunk = store.getStoreLanguage().split("_");
					lang = (chunk.length > 0 && StringUtils.isNotBlank(chunk[0])) ? chunk[0].toLowerCase() : lang;
				}

				Stores orderStore = Constants.getStoresList().stream().filter(e -> e != null &&
								StringUtils.isNotEmpty(e.getStoreId()) &&
								e.getStoreId().equalsIgnoreCase(
										(order.getStoreId() != null)? order.getStoreId().toString(): ""))
						.findFirst().orElse(null);

				String currency = "";
				if (orderStore != null && StringUtils.isNotBlank(orderStore.getStoreCurrency())) {
					currency = orderStore.getStoreCurrency();
				}

				Configs configs = ((Constants.getOmsBaseConfigs() != null &&
						Constants.getOmsBaseConfigs().getConfigs() != null)
						? Constants.getOmsBaseConfigs().getConfigs() : new Configs());


				// Styli Cash

				rmaOrderInitV2Response.setSubTotal(refundGrandTotal.setScale(2, RoundingMode.HALF_UP));
				BigDecimal refundGrandTotalToShow = refundGrandTotal;
				boolean isShukranPayment = false;
				// Styli Shukran
				if(refundShukranTotal > 0 && store != null && store.getShukranPointConversion() != null && store.getShukranPointConversion().compareTo(BigDecimal.ZERO)>0) {
					String coinLabel = configs.getTranslationValue(configs.getPaymentMethodTranslations(), "shukran_coin", lang, "Shukran Points (" + refundShukranTotal + ")");

					coinLabel = coinLabel.replace(SPEND_COIN, String.valueOf(refundShukranTotal)  );
					breakDownItems.add(new BreakDownItem(
									"" + coinLabel,
									"" + ReturnConstants.BREAKDOWN_TYPE_SHUKRAN_POINTS,
									"",
									currency + " " + refundShukranValue.setScale(2, RoundingMode.HALF_UP)
							)
					);
					refundGrandTotal = refundGrandTotal.subtract(refundShukranValue);
					isShukranPayment = true;
				}
				// Remaining Prepaid
				if(refundPrepaidTotal != null && refundPrepaidTotal.compareTo(new BigDecimal(0)) > 0) {
					SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

					String pattern = "";
					String label = "";
					String paymentMethod = salesOrderPayment !=null && StringUtils.isNotBlank(salesOrderPayment.getMethod()) && StringUtils.isNotEmpty(salesOrderPayment.getMethod()) ?salesOrderPayment.getMethod(): "";
					if (salesOrderPayment != null
							&& StringUtils.isNotEmpty(salesOrderPayment.getAdditionalInformation())
							&& !paymentMethod.equals(OrderConstants.PAYMENT_METHOD_COD)) {
						try {
							SalesOrderPaymentInformation salesOrderPaymentInformation = objectMapper.readValue(
									salesOrderPayment.getAdditionalInformation(), SalesOrderPaymentInformation.class);


							if (OrderConstants.checkPaymentMethod(paymentMethod)) {

								pattern = salesOrderPaymentInformation.getPaymentOption() != null
										? salesOrderPaymentInformation.getPaymentOption()
										: "";
								if (StringUtils.isNotBlank(salesOrderPaymentInformation.getCardNumber())
										&& (salesOrderPaymentInformation.getCardNumber().length() > 9)) {
									label = salesOrderPaymentInformation.getCardNumber()
											.substring(salesOrderPaymentInformation.getCardNumber().length() - 4);
									if (!label.isEmpty()) {
										label = "XXXX-" + label;
									}

								}else{
									label = configs.getTranslationValue(configs.getPaymentMethodTranslations(),
											paymentMethod, lang, paymentMethod);
								}

							} else if ((OrderConstants.checkBNPLPaymentMethods(paymentMethod)
									|| PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod))) {
								label = configs.getTranslationValue(configs.getPaymentMethodTranslations(),
										paymentMethod, lang, paymentMethod);
							}

							if(refundAmountToBeDeducted >0){
								if(newReturnFee.compareTo(refundGrandTotal)>0){
									refundPrepaidTotal=BigDecimal.ZERO;
									rmaOrderInitV2Response.setSubTotal(refundGrandTotal.setScale(2, RoundingMode.HALF_UP));
								} else{
									if(refundPrepaidTotal.compareTo(refundGrandTotal)==0) {
										refundPrepaidTotal = refundPrepaidTotal.subtract(newReturnFee);
									}else{
										BigDecimal percentageRefundPrepaid= refundPrepaidTotal
												.divide(refundGrandTotal, 6, RoundingMode.HALF_UP).multiply(divideValue).setScale(2, RoundingMode.HALF_UP)
												.setScale(4, RoundingMode.HALF_UP);
										BigDecimal newRefundPrepaidAmountToBeDeducted=newReturnFee.divide(divideValue, 4, RoundingMode.HALF_UP)
												.multiply(percentageRefundPrepaid).setScale(2, RoundingMode.HALF_UP)
												.setScale(4, RoundingMode.HALF_UP);
										refundPrepaidTotal= refundPrepaidTotal.subtract(newRefundPrepaidAmountToBeDeducted);
									}
								}
							}

							refundPrepaidTotal = refundPrepaidTotal.setScale(2, RoundingMode.HALF_UP);
							rmaOrderInitV2Response.setRefundPrepaidTotal(parseNullStr(refundPrepaidTotal));
							if(refundPrepaidTotal.compareTo(new BigDecimal(0)) > 0 ) {
								if(StringUtils.isEmpty(label) || StringUtils.isBlank(label)){
									label="Card Payment";
									if(lang.equals("ar")){
										label="بطاقه ائتمان";
									}
								}
								if(!lang.equals("ar") && label.contains("_")){
									label =label.replace("_", " ").substring(0, 1).toUpperCase() + label.replace("_", " ").substring(1);
								}
								breakDownItems.add(new BreakDownItem(
										label,
										ReturnConstants.BREAKDOWN_TYPE_PREPAID,
										pattern,
										((StringUtils.isNotEmpty(currency)) ? currency + " " : "")
												+ getFormattedDecimalString(refundPrepaidTotal)
								));
							}
						} catch (Exception e) {
							LOGGER.error("error salesOrderPayment.getAdditionalInformation() : " + e.getMessage());
						}
					}


				}
				// Styli Credit
				if(refundStoreCreditTotal != null && refundStoreCreditTotal.compareTo(new BigDecimal(0)) > 0) {

					if(refundAmountToBeDeducted >0){
						if(newReturnFee.compareTo(refundGrandTotal)>0){
							refundStoreCreditTotal=BigDecimal.ZERO;
							rmaOrderInitV2Response.setSubTotal(refundGrandTotal.setScale(2, RoundingMode.HALF_UP));
						} else{
							if(refundStoreCreditTotal.compareTo(refundGrandTotal)==0) {
								refundStoreCreditTotal = refundStoreCreditTotal.subtract(newReturnFee);
							}else{
								BigDecimal percentageRefundCredit= refundStoreCreditTotal
										.divide(refundGrandTotal, 6, RoundingMode.HALF_UP).multiply(divideValue)
										.setScale(4, RoundingMode.HALF_UP);
								BigDecimal newRefundCreditAmountToBeDeducted=newReturnFee.divide(divideValue, 4, RoundingMode.HALF_UP)
										.multiply(percentageRefundCredit).setScale(2, RoundingMode.HALF_UP)
										.setScale(4, RoundingMode.HALF_UP);
								refundStoreCreditTotal= refundStoreCreditTotal.subtract(newRefundCreditAmountToBeDeducted);
							}
						}
					}
					refundStoreCreditTotal = refundStoreCreditTotal.setScale(2, RoundingMode.HALF_UP);
					rmaOrderInitV2Response.setRefundStoreCreditTotal(parseNullStr(refundStoreCreditTotal));
					if(refundStoreCreditTotal.compareTo(new BigDecimal(0)) > 0 ) {
						breakDownItems.add(new BreakDownItem(
								configs.getTranslationValue(
										configs.getPaymentMethodTranslations(), "free", lang, "Styli Credits"),
								ReturnConstants.BREAKDOWN_TYPE_STORE_CREDIT,
								"",
								((StringUtils.isNotEmpty(currency)) ? currency + " " : "")
										+ getFormattedDecimalString(refundStoreCreditTotal)
						));
					}
				}
				// Styli Cash
				if(refundStoreCoinsTotal != null && refundStoreCoinsTotal.compareTo(new BigDecimal(0)) > 0) {

					if(refundAmountToBeDeducted >0){
						if(newReturnFee.compareTo(refundGrandTotal)>0){
							refundStoreCoinsTotal=BigDecimal.ZERO;
							rmaOrderInitV2Response.setSubTotal(refundGrandTotal.setScale(2, RoundingMode.HALF_UP));
						} else{
							if(refundStoreCoinsTotal.compareTo(refundGrandTotal)==0) {
								refundStoreCoinsTotal = refundStoreCoinsTotal.subtract(newReturnFee);
							}else{
								BigDecimal percentageRefundCoin= refundStoreCoinsTotal
										.divide(refundGrandTotal, 6, RoundingMode.HALF_UP).multiply(divideValue)
										.setScale(4, RoundingMode.HALF_UP);
								BigDecimal newRefundCoinAmountToBeDeducted=newReturnFee.divide(divideValue, 4, RoundingMode.HALF_UP)
										.multiply(percentageRefundCoin).setScale(2, RoundingMode.HALF_UP)
										.setScale(4, RoundingMode.HALF_UP);
								refundStoreCoinsTotal= refundStoreCoinsTotal.subtract(newRefundCoinAmountToBeDeducted);
							}
						}
					}
					BigDecimal coinCount = refundStoreCoinsTotal.multiply(new BigDecimal(10)).setScale(0, RoundingMode.HALF_UP);
					String coinLabel = configs.getTranslationValue(configs.getPaymentMethodTranslations(),
							"styli_coin", lang, "Styli Cash (" + coinCount.intValue() + ")");

					coinLabel = coinLabel.replace(SPEND_COIN, String.valueOf(coinCount.intValue()));
					LOGGER.info("RMA Breakdown - Currency: " + refundStoreCoinsTotal + " SAR → Coins: " + coinCount.intValue() + " → Label: " + coinLabel);
					refundStoreCoinsTotal = refundStoreCoinsTotal.setScale(2, RoundingMode.HALF_UP);

					if(refundStoreCoinsTotal.compareTo(new BigDecimal(0)) > 0) {
						breakDownItems.add(new BreakDownItem(
										coinLabel,
										ReturnConstants.BREAKDOWN_TYPE_STYLI_COIN,
										"",
										currency + " " + refundStoreCoinsTotal
								)
						);
					}
				}

				if(refundAmountToBeDeducted >0){
					if(BigDecimal.valueOf(refundAmountToBeDeducted).compareTo(refundGrandTotal)>0){
						rmaOrderInitV2Response.setReturnAmountToBePay(new BigDecimal(Double.toString((refundAmountToBeDeducted - refundGrandTotal.doubleValue()))).setScale(2, RoundingMode.HALF_UP).doubleValue());
						refundGrandTotal= BigDecimal.ZERO;
						rmaOrderInitV2Response.setReturnChargeApplicable(true);
					} else{
						refundGrandTotal = refundGrandTotal.subtract(BigDecimal.valueOf(refundAmountToBeDeducted));
						if(isShukranPayment){
							refundGrandTotal= refundGrandTotalToShow.subtract(BigDecimal.valueOf(refundAmountToBeDeducted));
						}
					}
				} else if(isShukranPayment){
					refundGrandTotal= refundGrandTotalToShow;
				}

				rmaOrderInitV2Response.setRefundGrandTotal(parseNullStr(refundGrandTotal.setScale(2, RoundingMode.HALF_UP)));
				rmaOrderInitV2Response.setReturnBreakDown(breakDownItems);
			}
		} catch (Exception e) {
			LOGGER.error("error getReturnBreakdown() : " + e.getMessage());
		}
		return rmaOrderInitV2Response;
	}

	private String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}

	private BigDecimal getNotNullDecimal(BigDecimal input) {
		return ((input != null) ? input : new BigDecimal(0));
	}

	private String getFormattedDecimalString(BigDecimal input) {
		String result = "";
		try {
			final BigDecimal newInput = getNotNullDecimal(input);
			result = df_2.format(newInput.setScale(2, BigDecimal.ROUND_DOWN));
		} catch (Exception e) {
			LOGGER.error("error getFormattedDecimalString("
					+((input != null)? input.toString() : "null")+") : " + e.getMessage());
		}
		return result;
	}

	public TotalItemsReturnedResponse totalItemsReturned(SalesOrder order, AmastyRmaRequest rmaRequest) {
		TotalItemsReturnedResponse totalItemsReturnedResponse= new TotalItemsReturnedResponse();
		  BigDecimal totalQuantity= BigDecimal.ZERO;
          List<Integer> allIds= new ArrayList<>();

		  if(rmaRequest != null){
			  allIds =amastyRmaRequestRepository.getAllRequestIdsByOrderIdsAndAmastyIds(order.getEntityId(), rmaRequest.getRequestId());
		  }else{
			  allIds= amastyRmaRequestRepository.getAllRequestIdsByOrderIds(order.getEntityId());
		  }
		  if(allIds != null && !allIds.isEmpty()){
			  totalQuantity = amastyRmaRequestRepository.getAllReturnedQuantity(allIds);
			  LOGGER.info("sjdgfjgsjfgjgfj"+ totalQuantity);
		  }
		  totalItemsReturnedResponse.setAllIds(allIds);
		  totalItemsReturnedResponse.setTotalQuantity(totalQuantity);
          return totalItemsReturnedResponse;
	}

	@Override
	public OrderResponseDTO rmaOrderVersionTwoWrapper(List<RMAOrderV2RequestWrapper> requestWrappers, String xClientVersion) {
		OrderResponseDTO combinedResponse = null;
		final String RETURN = "return";
		
		// Process all wrappers in the array
		for (RMAOrderV2RequestWrapper wrapper : requestWrappers) {
			// Process each split order within the wrapper
			for (RMAOrderV2SubRequest splitOrder : wrapper.getOrders()) {
				RMAOrderV2Request request = createRMAOrderRequest(wrapper, splitOrder);
				OrderResponseDTO resp = rmaOrderVersionTwo(request, xClientVersion);

				if (null != resp && resp.getStatus() && null != wrapper.getIsDropOffRequest()
						&& !wrapper.getIsDropOffRequest().booleanValue() && null != resp.getResponse()) {

					salesOrderServiceV3Impl.sendSms(resp.getResponse().getRmaIncId(), RETURN,
							OrderConstants.SMS_TEMPLATE_RETURN_CREATE, null);
				} else if (null != resp && resp.getStatus() && null != wrapper.getIsDropOffRequest()
						&& wrapper.getIsDropOffRequest().booleanValue() && null != resp.getResponse()) {
					salesOrderServiceV3Impl.createDropOff(resp.getResponse().getRmaIncId(), RETURN,
							OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF, resp);
					salesOrderServiceV3Impl.sendSms(resp.getResponse().getRmaIncId(), RETURN,
							OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF, resp);
				}

				// Use the last response as the combined response
				combinedResponse = resp;
			}
		}

		return combinedResponse;
	}

	@Override
	public OrderResponseDTO rmaOrderVersionTwoSingle(RMAOrderV2RequestWrapper wrapper, String xClientVersion) {
		// Delegate to the batch method with a single-element list
		return rmaOrderVersionTwoWrapper(List.of(wrapper), xClientVersion);
	}

	/**
	 * Helper method to find parent and child order items for normal orders.
	 */
	private AbstractMap.SimpleEntry<SalesOrderItem, SalesOrderItem> findNormalOrderItems(
			SalesOrder order, RMAOrderItemV2Request item) {
		
		SalesOrderItem parentItem = order.getSalesOrderItem().stream()
				.filter(e -> e.getItemId().equals(item.getParentOrderItemId())).findAny().orElse(null);

		SalesOrderItem childOrderItem = null;
		if (parentItem != null) {
			final Integer parentItemId = parentItem.getItemId();
			childOrderItem = order.getSalesOrderItem().stream().filter(e -> e.getParentOrderItem() != null)
					.filter(e -> e.getParentOrderItem().getItemId().equals(parentItemId)).findFirst()
					.orElse(null);
		}
		
		return new AbstractMap.SimpleEntry<>(parentItem, childOrderItem);
	}

	/**
	 * Helper method to find the correct split order for a given item based on parentOrderItemId.
	 * This replaces the fragile index-based approach with a robust lookup.
	 */
	private SplitSalesOrder findSplitOrderForItem(List<SplitSalesOrder> deliveredSplitOrders, RMAOrderItemV2Request item) {
		if (deliveredSplitOrders == null || deliveredSplitOrders.isEmpty()) {
			return null;
		}
		
		// Find the split order that contains the item with the matching parentOrderItemId
		return deliveredSplitOrders.stream()
				.filter(splitOrder -> splitOrder.getSplitSalesOrderItems().stream()
						.anyMatch(splitItem -> splitItem.getItemId().equals(item.getParentOrderItemId())))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Helper method to find parent and child order items for split orders.
	 */
	private AbstractMap.SimpleEntry<SplitSalesOrderItem, SplitSalesOrderItem> findSplitOrderItems(
			SplitSalesOrder splitOrder, RMAOrderItemV2Request item) {
		
		SplitSalesOrderItem splitParentItem = splitOrder.getSplitSalesOrderItems().stream()
				.filter(e -> e.getItemId().equals(item.getParentOrderItemId()))
				.findAny().orElse(null);
		
		SplitSalesOrderItem splitChildItem = null;
		if (splitParentItem != null) {
			SplitSalesOrderItem parentItem = splitParentItem;
			// Find child item in split order
			final Integer parentItemId = parentItem.getItemId();
			splitChildItem = splitOrder.getSplitSalesOrderItems().stream()
					.filter(e -> e.getItemId().equals(parentItemId))
					.findFirst().orElse(null);
		}
		
		return new AbstractMap.SimpleEntry<>(splitParentItem, splitChildItem);
	}

	/**
	 * Helper method to find parent and child order items based on whether it's a split order flow or normal order flow.
	 * This eliminates code duplication across multiple methods.
	 * 
	 * @param isSplitOrderFlow whether this is a split order flow
	 * @param splitOrder the split order (can be null for normal orders)
	 * @param order the main order
	 * @param item the RMA order item request
	 * @return a pair containing (parentItem, childOrderItem) - both can be null if not found
	 */
	private AbstractMap.SimpleEntry<SalesOrderItem, SalesOrderItem> findParentAndChildItems(
			boolean isSplitOrderFlow, SplitSalesOrder splitOrder, SalesOrder order, RMAOrderItemV2Request item) {
		
		if (isSplitOrderFlow) {
			// For split orders, convert SplitSalesOrderItem to SalesOrderItem
			AbstractMap.SimpleEntry<SplitSalesOrderItem, SplitSalesOrderItem> splitItems = findSplitOrderItems(splitOrder, item);
			SalesOrderItem parentItem = splitItems.getKey() != null ? splitItems.getKey().getSalesOrderItem() : null;
			SalesOrderItem childOrderItem = splitItems.getValue() != null ? splitItems.getValue().getSalesOrderItem() : null;
			return new AbstractMap.SimpleEntry<>(parentItem, childOrderItem);
		} else {
			// Normal flow: find items in regular order collection
			return findNormalOrderItems(order, item);
		}
	}


	private BigDecimal getSubSalesOrderDiscount(boolean isSplitOrder, SplitSalesOrder deliveredSplitOrder, SalesOrder order, int parentItemId) {
		if (isSplitOrder) {
			return subSalesOrderItemRepository.findSplitDiscountByParentOrderIdAndMainItemId(deliveredSplitOrder.getEntityId(), parentItemId);
		} else {
			return subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(order.getEntityId(), parentItemId);
		}
	}

}

package org.styli.services.order.converter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.Arrays;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;
import org.styli.services.order.model.SalesOrder.SellerOrderInfo;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.DiscountData;
import org.styli.services.order.pojo.mulin.GalleryItem;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.order.StatusMessage;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.CustomerAddrees;
import org.styli.services.order.pojo.response.ExpressShipment;
import org.styli.services.order.pojo.response.OrderStatusResponse;
import org.styli.services.order.pojo.response.OrderTotal;
import org.styli.services.order.pojo.response.Order.OrderDetails;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.lang.Float.parseFloat;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class OmsorderentityConverter {

	private static final String ORDER_TYPE = "Order";

	private static final String SHIPMENT_TPYE = "Shipment";

	private static final String INVOICE_TYPE = "Invoice";
	
	private static final String EMAIL_TYPE = "Email";

	private static final Log LOGGER = LogFactory.getLog(OmsorderentityConverter.class);

	@Value("${magento.base.url}")
	private String magentoBaseUrl;

	@Autowired
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OrderEntityConverter orderEntityConverter;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	SalesShipmentRepository salesShipmentRepository;
	
	@Autowired
	OrderHelper orderHelper;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
    ShipmentTrackerRepository shipmentTrackerRepository;

	@Autowired
	org.styli.services.order.repository.SalesOrder.SplitSalesOrderItemRepository splitSalesOrderItemRepository;

	private static final ObjectMapper mapper = new ObjectMapper();

	public OrdersDetailsResponsedto convertOrderObject(SalesOrder order, List<SplitSalesOrder> splitSalesOrders,
			Map<String, ProductResponseBody> productsFromMulin, Stores store, String tabScreen,
			boolean rmaItemQtyProcessed) {
		OrdersDetailsResponsedto resp = new OrdersDetailsResponsedto();
		
		
		resp.setExtOrderId(order.getExtOrderId());

		resp.setOrderId(order.getEntityId());
		resp.setSplitOrder(Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder()));
		resp.setStatus(parseNullStr(order.getStatus()));

		resp.setShippingDescription(parseNullStr(order.getShippingDescription()));
		resp.setStoreId(parseNullStr(order.getStoreId()));
		resp.setCustomerId(order.getCustomerId());
		resp.setItemCount(parseNullStr(order.getTotalItemCount()));
		if (order.getSubSalesOrder() == null)
			resp.setQuoteId(parseNullStr(order.getQuoteId()));
		else
			resp.setQuoteId(parseNullStr(order.getSubSalesOrder().getExternalQuoteId()));
		resp.setShippingAddressId(order.getShippingAddressId());
		resp.setOrderIncrementId(parseNullStr(order.getIncrementId()));
		resp.setShippingMethod(parseNullStr(order.getShippingMethod()));
		resp.setOrderIncrementId(parseNullStr(order.getIncrementId()));
		resp.setCreatedAt(convertTimeZone(order.getCreatedAt(), order.getStoreId()));
		resp.setUpdatedAt(convertTimeZone(order.getUpdatedAt(), order.getStoreId()));
		resp.setOrderUpdatedAt(convertTimeZone(order.getUpdatedAt(), order.getStoreId()));
		resp.setOrderCreatedAt(convertTimeZone(order.getCreatedAt(), order.getStoreId()));
		if(null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getFreeShipmentTypeOrder()
				&& order.getSubSalesOrder().getFreeShipmentTypeOrder()==1) {
			
			resp.setFirstFreeShippingOrder(true);
		}
		
		if (null != order.getSubSalesOrder()) {
			resp.setClientSource(order.getSubSalesOrder().getClientSource());

		}
		resp.setHasWmsPushed(null != order.getWmsStatus() && order.getWmsStatus().equals(1));

		resp.setDeliveredAt(convertTimeZone(order.getDeliveredAt(), Integer.parseInt(store.getStoreId())));
		if (order.getEstimatedDeliveryTime() != null) {
		    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		    
		    String formattedTime = sdf.format(order.getEstimatedDeliveryTime());
		    resp.setEstimatedDeliveryTime(formattedTime);
		} else {
		    resp.setEstimatedDeliveryTime(null);
		}				

		String clickpostMessage = order.getClickpostMessage();
		if (clickpostMessage != null) {
			try {
				ClickpostMessageJSON[] clickpostMessageJSONArray = mapper.readValue(clickpostMessage,
						ClickpostMessageJSON[].class);
				for (ClickpostMessageJSON message : clickpostMessageJSONArray) {
					if (message.getType().equalsIgnoreCase("unDelivered")) {
						resp.setShippingDescription(parseNullStr(message.getValue()));
					}
				}
			} catch (IOException e) {
				LOGGER.error("exception occurred during parsing courier msg");
			}
		}

		setOrderStatusCallToActionFlag(resp, order);

		Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
		if (statusStatesMap != null) {
			resp.setStatusStepValue(statusStatesMap.get(order.getStatus()));
		}

		Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
		if (statusColorsMap != null) {
			resp.setStatusColorStepValue(statusColorsMap.get(order.getStatus()));
		}

		SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
		key.setStatus(order.getStatus());
		key.setStoreId(Integer.parseInt(store.getStoreId()));
		SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);

		if (label != null) {
			resp.setStatusLabel(label.getLabel());
		} else {
			resp.setStatusLabel(order.getStatus());
		}
		TrackingDetails orderTracking = null;
		String encryptedAWB = null;
		if (order.getSalesShipmentTrack() != null && !order.getSalesShipmentTrack().isEmpty()) {
			for (SalesShipmentTrack salesShipmentTrack : order.getSalesShipmentTrack()) {
				if (salesShipmentTrack != null && 
					StringUtils.isNotBlank(salesShipmentTrack.getTrackNumber())) {
					
					orderTracking = new TrackingDetails();
					orderTracking.setCarrierCode(salesShipmentTrack.getCarrierCode());
					orderTracking.setTitle(salesShipmentTrack.getTitle());
					orderTracking.setTrackNumber(salesShipmentTrack.getTrackNumber());
					try {
						encryptedAWB = OrderEntityConverter.encryptAWB(salesShipmentTrack.getTrackNumber());
						orderTracking.setEncryptedTrackNumber(encryptedAWB);
					} catch (Exception e) {
						LOGGER.error("Error in encrypting AWB for track number: " + salesShipmentTrack.getTrackNumber(), e);
					}
					break; // Only take the first valid tracking record
				}
			}
		}

		resp.setTrackings(orderTracking);

		List<OrderHistory> historyList = new ArrayList<>();

		if (tabScreen.equals(ORDER_TYPE) || tabScreen.equals(INVOICE_TYPE) || tabScreen.equals(SHIPMENT_TPYE)) {
			for (SalesOrderStatusHistory history : order.getSalesOrderStatusHistory()) {
				OrderHistory orderhistory = new OrderHistory();
				orderhistory.setStatus(history.getStatus());
				orderhistory.setMessage(history.getComment());
				orderhistory.setDate(convertTimeZone(history.getCreatedAt(), order.getStoreId()));
				orderhistory.setCustomerNotified(
						null != history.getCustomerNotified() && history.getCustomerNotified().equals(1));
				historyList.add(orderhistory);
			}
		}
		resp.setHistories(historyList);


		resp.setLocalProducts(new ArrayList<>());
		resp.setGlobalProducts(new ArrayList<>());
		resp.setOrders(new ArrayList<>());
		resp.setProducts(new ArrayList<>());
		Map<Integer, Boolean> orderReturnFlag = new HashMap<>();

		OrderTotal totals = new OrderTotal();
		if (tabScreen.equals(ORDER_TYPE)) {
			setOrderItems(order, splitSalesOrders, orderReturnFlag, productsFromMulin, resp, totals, store, historyList);
			setOrderTotals(order, resp, totals);
		}
		if (tabScreen.equals(EMAIL_TYPE)) {
			boolean result = setInvoiceItems(order, splitSalesOrders, productsFromMulin, resp, totals, store, historyList);
			if (!result)
				return null;
			setInvoiceTotals(order, resp, totals);
		}
		if (tabScreen.equals(INVOICE_TYPE)) {
			boolean result = setInvoiceItems(order, splitSalesOrders, productsFromMulin, resp, totals, store, historyList);
			if (!result)
				return null;
			setInvoiceTotals(order, resp, totals);
		}

		if (tabScreen.equals(SHIPMENT_TPYE)) {
			boolean result = setShipmentItems(order, splitSalesOrders, productsFromMulin, resp, historyList);
			if (!result)
				return null;
		}

		PaymentInformation paymentInformation = new PaymentInformation();

		String paymentInfo = null;
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream()
				.sorted((f1, f2) -> Long.compare(f2.getEntityId(), f1.getEntityId()))
				.findFirst().orElse(null);
		if (salesOrderPayment != null) {
			paymentInfo = salesOrderPayment.getAdditionalInformation();
			paymentInformation.setPaymentMethod(salesOrderPayment.getMethod());
			paymentInformation.setAmount(parseNullStr(salesOrderPayment.getAmountPaid()));
		}

		// Expected to fetch card type and number in case of prepaid, apple_pay
		if (paymentInfo != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInfo,
						SalesOrderPaymentInformation.class);
				paymentInformation.setCcNumber(parseNullStr(salesOrderPaymentInformation.getCardNumber()));
				paymentInformation.setCcType(parseNullStr(salesOrderPaymentInformation.getPaymentOption()));
				paymentInformation
						.setPaymentResponseMessage(parseNullStr(salesOrderPaymentInformation.getResponseMessage()));
				LOGGER.info("Customer Ip in apple_pay/card : " +order.getRemoteIp());
				if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
					paymentInformation.setCustomerIp(order.getRemoteIp());
				}
				paymentInformation.setAmount(parseNullStr(salesOrderPaymentInformation.getAmount()));
				paymentInformation
						.setAuthorizationCode(parseNullStr(salesOrderPaymentInformation.getAuthorizationCode()));
				paymentInformation
						.setMerchantReference(parseNullStr(salesOrderPaymentInformation.getMerchantReference()));
			} catch (IOException e) {
				LOGGER.error("jackson mapper error!");
			}
		}
		if (Objects.nonNull(salesOrderPayment)
				&& (salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue())
				|| salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.TABBY_PAYLATER.getValue())
				|| PaymentConstants.TAMARA_INSTALMENTS_3.equalsIgnoreCase(salesOrderPayment.getMethod())
				|| PaymentConstants.TAMARA_INSTALMENTS_6.equalsIgnoreCase(salesOrderPayment.getMethod())
				|| PaymentConstants.CASHFREE.equalsIgnoreCase(salesOrderPayment.getMethod()))) {
			if (salesOrderPayment != null) {
				paymentInfo = salesOrderPayment.getAdditionalInformation();
				paymentInformation.setPaymentMethod(salesOrderPayment.getMethod());
				paymentInformation.setAmount(parseNullStr(salesOrderPayment.getAmountOrdered()));
			}
			try {
				if (paymentInfo != null) {
					PaymentDTO tabbyPayment = mapper.readValue(paymentInfo, PaymentDTO.class);
					if (null != tabbyPayment) {
						paymentInformation.setPaymentResponseMessage(parseNullStr(tabbyPayment.getStatus()));
					}
				} else {
					LOGGER.info("Null receive from salesOrderPayment additional information");
				}

			} catch (Exception e) {
				LOGGER.error("Error in Get order details from Tabby For OMS!");
			}
			paymentInformation.setCcNumber(paymentInformation.getPaymentMethod());
			paymentInformation.setCcType(paymentInformation.getPaymentMethod());
			if (null != order) {
				LOGGER.info("Customer Ip : " +order.getRemoteIp());
				if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
					paymentInformation.setCustomerIp(order.getRemoteIp());
				}
				paymentInformation.setMerchantReference(order.getMerchantReferance());
			}
			if (order.getSubSalesOrder() != null) {
				paymentInformation.setAuthorizationCode(order.getSubSalesOrder().getPaymentId());
			}
			if (null != salesOrderPayment) {
				paymentInformation.setAmount(salesOrderPayment.getAmountOrdered().toString());
			}
			
		} else {
			String paymentMethod = null;
			if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
				for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
					paymentMethod = payment.getMethod();
				}
			}
			if (paymentMethod != null && OrderConstants.checkPaymentMethod(paymentMethod)) {
				LOGGER.info("OmsorderentityConverter convertOrderObject payfortAuthorized: "
						+ order.getPayfortAuthorized());
				LOGGER.info("OmsorderentityConverter convertOrderObject authorizationCapture: "
						+ order.getAuthorizationCapture());
				LOGGER.info("OmsorderentityConverter convertOrderObject paymentMethod: " + paymentMethod);
				if (null != order.getPayfortAuthorized()
						&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)
						&& (null == order.getAuthorizationCapture()
								|| String.valueOf(order.getAuthorizationCapture()).equalsIgnoreCase(Constants.ZERO))) {
					LOGGER.info("OmsorderentityConverter convertOrderObject inside if");
					paymentInformation.setCommandType("AUTHORIZATION");
				} else if (null != order.getPayfortAuthorized()
						&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)
						&& (null != order.getAuthorizationCapture()
								|| String.valueOf(order.getAuthorizationCapture()).equalsIgnoreCase(Constants.ONE))) {
					LOGGER.info("OmsorderentityConverter convertOrderObject inside else if");
					paymentInformation.setCommandType("CAPTURE");
				} else {
					LOGGER.info("OmsorderentityConverter convertOrderObject inside else");
					paymentInformation.setCommandType("PURCHASE");
				}
			}
		}

		resp.setPaymentInformation(paymentInformation);

		CustomerAddrees orderAddress = new CustomerAddrees();
		CustomerEntity custEntity = orderHelper.getCustomerDetails(order.getCustomerId(),null);
		// Address details
		for (SalesOrderAddress shippingAddress : order.getSalesOrderAddress()) {
			if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {

				if (ObjectUtils.isNotEmpty(custEntity) && null != custEntity.getEntityId()) {
					orderAddress.setFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setLastName(parseNullStr(shippingAddress.getLastname()));
					resp.setEmail(parseNullStr(custEntity.getEmail()));
					orderAddress.setCustomerAccountFirstName(parseNullStr(custEntity.getFirstName()));
					orderAddress.setCustomerAccountLastName(parseNullStr(custEntity.getLastName()));
				} else {
					orderAddress.setFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setLastName(parseNullStr(shippingAddress.getLastname()));
					resp.setEmail(parseNullStr(order.getCustomerEmail()));
					orderAddress.setCustomerAccountFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setCustomerAccountLastName(shippingAddress.getLastname());
				}

				orderAddress.setMobileNumber(parseNullStr(shippingAddress.getTelephone()));
				orderAddress.setCity(parseNullStr(shippingAddress.getCity()));
				orderAddress.setStreetAddress(parseNullStr(shippingAddress.getStreet()));
				orderAddress.setCountry(parseNullStr(shippingAddress.getCountryId()));
				orderAddress.setRegion(parseNullStr(shippingAddress.getRegion()));
				orderAddress.setPostCode(parseNullStr(shippingAddress.getPostcode()));
				orderAddress.setRegionId(shippingAddress.getRegionId());
				orderAddress.setArea(parseNullStr(shippingAddress.getArea()));
				orderAddress.setLandMark(parseNullStr(shippingAddress.getNearestLandmark()));
                orderAddress.setUnitNumber(shippingAddress.getUnitNumber());
                orderAddress.setShortAddress(shippingAddress.getShortAddress());
                orderAddress.setPostalCode(shippingAddress.getPostalCode());
                orderAddress.setKsaAddressComplaint(shippingAddress.getKsaAddressComplaint());
				orderAddress.setAddressId(shippingAddress.getEntityId());
                orderAddress.setBuildingNumber(parseNullStr(shippingAddress.getBuildingNumber()));
			}
		}

		resp.setShippingAddress(orderAddress);

		if (null != custEntity) {
			resp.setCustomerGroup(Constants.CUSTOMER_GROUP);
			try {
				if (ObjectUtils.isNotEmpty(custEntity.getIsActive())) {
					resp.setCustomerActiveStatus(CustomerStatus.findByAbbr(custEntity.getIsActive()));
				}
			} catch (Exception ecs) {
				LOGGER.error(ecs.getMessage());
			}
		} else {
			resp.setCustomerGroup("GUEST");
		}

		if (tabScreen.equals(SHIPMENT_TPYE)) {
			boolean isSplitOrder = Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder());
			if (!isSplitOrder) {
				if(CollectionUtils.isNotEmpty(order.getSalesShipments())){
					SalesShipment shipment = order.getSalesShipments().stream().findFirst().orElse(null);
					resp.setIsShipmentGenerated(true);

					if (null != shipment) {
						resp.setShipmentId(shipment.getEntityId());
						resp.setIncrementId(parseNullStr(shipment.getIncrementId()));
						resp.setCreatedAt(convertTimeZone(shipment.getCreatedAt(), order.getStoreId()));
						resp.setUpdatedAt(convertTimeZone(shipment.getUpdatedAt(), order.getStoreId()));
					}
				} else {
					resp.setIsShipmentGenerated(false);
				}
			} else {
				resp.setIsShipmentGenerated(true);
				List<SplitSalesOrder> splitSalesOrderList = splitSalesOrderRepository.findByOrderId(order.getEntityId());
				splitSalesOrderList.forEach(splitSalesOrder -> {
					if (CollectionUtils.isEmpty(splitSalesOrder.getSplitSalesShipments())) {
						resp.setIsShipmentGenerated(false);
					}
				});
			}
		}

		if (tabScreen.equals(INVOICE_TYPE) && CollectionUtils.isNotEmpty(order.getSalesInvoices())) {

			resp.setIsInvoicedGenerated(true);

			SalesInvoice invoice = order.getSalesInvoices().stream().findFirst().orElse(null);
			if (null != invoice) {

				resp.setInvoiceId(invoice.getEntityId());
				resp.setIncrementId(parseNullStr(invoice.getIncrementId()));
				resp.setCreatedAt(convertTimeToKSA(invoice.getCreatedAt()));
				resp.setOrderCreatedAt(convertTimeToKSA(order.getCreatedAt()));
				resp.setUpdatedAt(convertTimeZone(invoice.getUpdatedAt(), 1));
				resp.setZatcaQrCode(invoice.getZatcaQRCode());
				resp.setZatcaStatus(invoice.getZatcaStatus());
			}
		} else {

			resp.setIsInvoicedGenerated(false);

		}
		resp.setClientVersion(order.getAppVersion());
		if (order.getSource() == 0) {
			resp.setSource(SourceType.WEB.value);
		} else if (order.getSource() == 1) {
			resp.setSource(SourceType.APP.value);
		} else if (order.getSource() == 2) {
			resp.setSource(SourceType.MSITE.value);
		} else if (order.getSource() == 3) {
			resp.setSource(SourceType.ADMIN.value);
		}
		LOGGER.info("Customer Ip set in response: " +order.getRemoteIp());
		if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
			resp.setCustomerIp(order.getRemoteIp());
		}
		resp.setPurchasedFrom(orderEntityConverter.getStoreName(order.getStoreId()));
		
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getRetryPayment()
				&& order.getSubSalesOrder().getRetryPayment().equals(1)) {
			resp.setPaymentExpiresAt(formatTimezone(order.getSubSalesOrder().getOrderExpiredAt(), order.getStoreId()));
			resp.setCanRetryPayment(true);
		}

		List<OrderId> orderIds = new ArrayList<>();

		if(splitSalesOrders.isEmpty()){
			OrderId orderId    = new OrderId();
			orderId.setOrderId(order.getEntityId());
			orderId.setIncrementId(order.getIncrementId());
			orderId.setOrderStatus(order.getStatus());
			orderId.setReturnStatus(orderReturnFlag.get(order.getEntityId()));
			
			// Set shipmentId for regular orders
			if(CollectionUtils.isNotEmpty(order.getSalesShipments())){
				SalesShipment shipment = order.getSalesShipments().stream().findFirst().orElse(null);
				if(shipment != null) {
					orderId.setShipmentId(shipment.getEntityId());
				}
			}
			
			orderIds.add(orderId);
		}
		else {
			splitSalesOrders.forEach(splitSalesOrder -> {
				OrderId orderId = new OrderId();
				orderId.setOrderId(splitSalesOrder.getEntityId());
				orderId.setIncrementId(splitSalesOrder.getIncrementId());
				orderId.setOrderStatus(order.getStatus());
				orderId.setReturnStatus(orderReturnFlag.get(order.getEntityId()));
				
				// Set shipmentId for split orders
				if(CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesShipments())){
					SalesShipment shipment = splitSalesOrder.getSplitSalesShipments().iterator().next();
					if(shipment != null) {
						orderId.setShipmentId(shipment.getEntityId());
					}
				}
				
				// Set estimatedDeliveryDate for split orders
				String estimatedDeliveryDate = fetchEstimatedDeliveryDateForShipment(splitSalesOrder.getEntityId());
				if (StringUtils.isNotBlank(estimatedDeliveryDate)) {
					orderId.setEstimatedDeliveryDate(estimatedDeliveryDate);
					LOGGER.info("Set estimatedDeliveryDate: " + estimatedDeliveryDate + " for splitOrderId: " + splitSalesOrder.getEntityId());
				}
				
				orderIds.add(orderId);
			});
		}
		resp.setOrderIds(orderIds);
		
		LOGGER.info("OmsorderentityConverter convertOrderObject resp.getPaymentInformation.getCommandType: "+resp.getPaymentInformation().getCommandType());
	

		return resp;
	}

	/**
	 * Convert order object for old response format (used for shipping/details and oms/details APIs)
	 * This method maintains the old response structure while the new invoice format uses the above method
	 */
	public OrdersDetailsResponsedto convertOrderObjectOldFormat(SalesOrder order, List<SplitSalesOrder> splitSalesOrders,
			Map<String, ProductResponseBody> productsFromMulin, Stores store, String tabScreen,
			boolean rmaItemQtyProcessed) {
		OrdersDetailsResponsedto resp = new OrdersDetailsResponsedto();
		
		
		resp.setExtOrderId(order.getExtOrderId());

		resp.setOrderId(order.getEntityId());
		resp.setSplitOrder(Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder()));
		resp.setStatus(parseNullStr(order.getStatus()));

		resp.setShippingDescription(parseNullStr(order.getShippingDescription()));
		resp.setStoreId(parseNullStr(order.getStoreId()));
		resp.setCustomerId(order.getCustomerId());
		resp.setItemCount(parseNullStr(order.getTotalItemCount()));
		if (order.getSubSalesOrder() == null)
			resp.setQuoteId(parseNullStr(order.getQuoteId()));
		else
			resp.setQuoteId(parseNullStr(order.getSubSalesOrder().getExternalQuoteId()));
		resp.setShippingAddressId(order.getShippingAddressId());
		resp.setOrderIncrementId(parseNullStr(order.getIncrementId()));
		resp.setShippingMethod(parseNullStr(order.getShippingMethod()));
		resp.setOrderIncrementId(parseNullStr(order.getIncrementId()));
		resp.setCreatedAt(convertTimeZone(order.getCreatedAt(), order.getStoreId()));
		resp.setUpdatedAt(convertTimeZone(order.getUpdatedAt(), order.getStoreId()));
		resp.setOrderUpdatedAt(convertTimeZone(order.getUpdatedAt(), order.getStoreId()));
		resp.setOrderCreatedAt(convertTimeZone(order.getCreatedAt(), order.getStoreId()));
		if(null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getFreeShipmentTypeOrder()
				&& order.getSubSalesOrder().getFreeShipmentTypeOrder()==1) {
			
			resp.setFirstFreeShippingOrder(true);
		}
		
		if (null != order.getSubSalesOrder()) {
			resp.setClientSource(order.getSubSalesOrder().getClientSource());

		}
		resp.setHasWmsPushed(null != order.getWmsStatus() && order.getWmsStatus().equals(1));

		resp.setDeliveredAt(convertTimeZone(order.getDeliveredAt(), Integer.parseInt(store.getStoreId())));
		if (order.getEstimatedDeliveryTime() != null) {
		    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		    
		    String formattedTime = sdf.format(order.getEstimatedDeliveryTime());
		    resp.setEstimatedDeliveryTime(formattedTime);
		} else {
		    resp.setEstimatedDeliveryTime(null);
		}				

		String clickpostMessage = order.getClickpostMessage();
		if (clickpostMessage != null) {
			try {
				ClickpostMessageJSON[] clickpostMessageJSONArray = mapper.readValue(clickpostMessage,
						ClickpostMessageJSON[].class);
				for (ClickpostMessageJSON message : clickpostMessageJSONArray) {
					if (message.getType().equalsIgnoreCase("unDelivered")) {
						resp.setShippingDescription(parseNullStr(message.getValue()));
					}
				}
			} catch (IOException e) {
				LOGGER.error("exception occurred during parsing courier msg");
			}
		}

		setOrderStatusCallToActionFlag(resp, order);

		Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
		if (statusStatesMap != null) {
			resp.setStatusStepValue(statusStatesMap.get(order.getStatus()));
		}

		Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
		if (statusColorsMap != null) {
			resp.setStatusColorStepValue(statusColorsMap.get(order.getStatus()));
		}

		SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
		key.setStatus(order.getStatus());
		key.setStoreId(Integer.parseInt(store.getStoreId()));
		SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);

		if (label != null) {
			resp.setStatusLabel(label.getLabel());
		} else {
			resp.setStatusLabel(order.getStatus());
		}
		TrackingDetails orderTracking = null;
		String encryptedAWB = null;
        List<SalesShipmentTrack> shipmentTracks = getSalesShipmentTracks(order);
		for (SalesShipmentTrack salesShipmentTrack : shipmentTracks) {
			orderTracking = new TrackingDetails();
			orderTracking.setCarrierCode(salesShipmentTrack.getCarrierCode());
			orderTracking.setTitle(salesShipmentTrack.getTitle());
			orderTracking.setTrackNumber(salesShipmentTrack.getTrackNumber());
			try {
				encryptedAWB  = OrderEntityConverter.encryptAWB(salesShipmentTrack.getTrackNumber());
			} catch (Exception e) {
				LOGGER.info("Error in encrypting AWB");
			}
			orderTracking.setEncryptedTrackNumber(encryptedAWB);
		}
		resp.setTrackings(orderTracking);

		List<OrderHistory> historyList = new ArrayList<>();

		if (tabScreen.equals(ORDER_TYPE) || tabScreen.equals(INVOICE_TYPE) || tabScreen.equals(SHIPMENT_TPYE)) {
			for (SalesOrderStatusHistory history : order.getSalesOrderStatusHistory()) {
				OrderHistory orderhistory = new OrderHistory();
				orderhistory.setStatus(history.getStatus());
				orderhistory.setMessage(history.getComment());
				orderhistory.setDate(convertTimeZone(history.getCreatedAt(), order.getStoreId()));
				orderhistory.setCustomerNotified(
						null != history.getCustomerNotified() && history.getCustomerNotified().equals(1));
				historyList.add(orderhistory);
			}
		}
		resp.setHistories(historyList);


		resp.setProducts(new ArrayList<>());
		Map<Integer, Boolean> orderReturnFlag = new HashMap<>();

		OrderTotal totals = new OrderTotal();
		if (tabScreen.equals(ORDER_TYPE)) {
			setOrderItemsOldFormat(order, splitSalesOrders, orderReturnFlag, productsFromMulin, resp, totals, store, historyList, orderTracking);
			setOrderTotalsOldFormat(order, resp, totals);
		}
		if (tabScreen.equals(EMAIL_TYPE)) {
			boolean result = setInvoiceItemsOldFormat(order, productsFromMulin, resp, totals, store);
			if (!result)
				return null;
			setInvoiceTotalsOldFormat(order, resp, totals);
		}
		if (tabScreen.equals(INVOICE_TYPE)) {
			boolean result = setInvoiceItemsOldFormat(order, productsFromMulin, resp, totals, store);
			if (!result)
				return null;
			setInvoiceTotalsOldFormat(order, resp, totals);
		}

		if (tabScreen.equals(SHIPMENT_TPYE)) {
			setShipmentItemsOldFormat(order, splitSalesOrders, productsFromMulin, resp, historyList, orderTracking);
		}

		PaymentInformation paymentInformation = new PaymentInformation();

		String paymentInfo = null;
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream()
				.sorted((f1, f2) -> Long.compare(f2.getEntityId(), f1.getEntityId()))
				.findFirst().orElse(null);
		if (salesOrderPayment != null) {
			paymentInfo = salesOrderPayment.getAdditionalInformation();
			paymentInformation.setPaymentMethod(salesOrderPayment.getMethod());
			paymentInformation.setAmount(parseNullStr(salesOrderPayment.getAmountPaid()));
		}

		// Expected to fetch card type and number in case of prepaid, apple_pay
		if (paymentInfo != null) {
			try {
				SalesOrderPaymentInformation salesOrderPaymentInformation = mapper.readValue(paymentInfo,
						SalesOrderPaymentInformation.class);
				paymentInformation.setCcNumber(parseNullStr(salesOrderPaymentInformation.getCardNumber()));
				paymentInformation.setCcType(parseNullStr(salesOrderPaymentInformation.getPaymentOption()));
				paymentInformation
						.setPaymentResponseMessage(parseNullStr(salesOrderPaymentInformation.getResponseMessage()));
				LOGGER.info("Customer Ip in apple_pay/card : " +order.getRemoteIp());
				if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
					paymentInformation.setCustomerIp(order.getRemoteIp());
				}
				paymentInformation.setAmount(parseNullStr(salesOrderPaymentInformation.getAmount()));
				paymentInformation
						.setAuthorizationCode(parseNullStr(salesOrderPaymentInformation.getAuthorizationCode()));
				paymentInformation
						.setMerchantReference(parseNullStr(salesOrderPaymentInformation.getMerchantReference()));
			} catch (IOException e) {
				LOGGER.error("jackson mapper error!");
			}
		}
		if (Objects.nonNull(salesOrderPayment)
				&& (salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue())
				|| salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.TABBY_PAYLATER.getValue())
				|| PaymentConstants.TAMARA_INSTALMENTS_3.equalsIgnoreCase(salesOrderPayment.getMethod())
				|| PaymentConstants.TAMARA_INSTALMENTS_6.equalsIgnoreCase(salesOrderPayment.getMethod())
				|| PaymentConstants.CASHFREE.equalsIgnoreCase(salesOrderPayment.getMethod()))) {
			if (salesOrderPayment != null) {
				paymentInfo = salesOrderPayment.getAdditionalInformation();
				paymentInformation.setPaymentMethod(salesOrderPayment.getMethod());
				paymentInformation.setAmount(parseNullStr(salesOrderPayment.getAmountOrdered()));
			}
			try {
				if (paymentInfo != null) {
					PaymentDTO tabbyPayment = mapper.readValue(paymentInfo, PaymentDTO.class);
					if (null != tabbyPayment) {
						paymentInformation.setPaymentResponseMessage(parseNullStr(tabbyPayment.getStatus()));
					}
				} else {
					LOGGER.info("Null receive from salesOrderPayment additional information");
				}

			} catch (Exception e) {
				LOGGER.error("Error in Get order details from Tabby For OMS!");
			}
			paymentInformation.setCcNumber(paymentInformation.getPaymentMethod());
			paymentInformation.setCcType(paymentInformation.getPaymentMethod());
			if (null != order) {
				LOGGER.info("Customer Ip : " +order.getRemoteIp());
				if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
					paymentInformation.setCustomerIp(order.getRemoteIp());
				}
				paymentInformation.setMerchantReference(order.getMerchantReferance());
			}
			if (order.getSubSalesOrder() != null) {
				paymentInformation.setAuthorizationCode(order.getSubSalesOrder().getPaymentId());
			}
			if (null != salesOrderPayment) {
				paymentInformation.setAmount(salesOrderPayment.getAmountOrdered().toString());
			}
			
		} else {
			String paymentMethod = null;
			if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
				for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
					paymentMethod = payment.getMethod();
				}
			}
			if (paymentMethod != null && OrderConstants.checkPaymentMethod(paymentMethod)) {
				LOGGER.info("OmsorderentityConverter convertOrderObject payfortAuthorized: "
						+ order.getPayfortAuthorized());
				LOGGER.info("OmsorderentityConverter convertOrderObject authorizationCapture: "
						+ order.getAuthorizationCapture());
				LOGGER.info("OmsorderentityConverter convertOrderObject paymentMethod: " + paymentMethod);
				if (null != order.getPayfortAuthorized()
						&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)
						&& (null == order.getAuthorizationCapture()
								|| String.valueOf(order.getAuthorizationCapture()).equalsIgnoreCase(Constants.ZERO))) {
					LOGGER.info("OmsorderentityConverter convertOrderObject inside if");
					paymentInformation.setCommandType("AUTHORIZATION");
				} else if (null != order.getPayfortAuthorized()
						&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)
						&& (null != order.getAuthorizationCapture()
								|| String.valueOf(order.getAuthorizationCapture()).equalsIgnoreCase(Constants.ONE))) {
					LOGGER.info("OmsorderentityConverter convertOrderObject inside else if");
					paymentInformation.setCommandType("CAPTURE");
				} else {
					LOGGER.info("OmsorderentityConverter convertOrderObject inside else");
					paymentInformation.setCommandType("PURCHASE");
				}
			}
		}

		resp.setPaymentInformation(paymentInformation);

		CustomerAddrees orderAddress = new CustomerAddrees();
		CustomerEntity custEntity = orderHelper.getCustomerDetails(order.getCustomerId(),null);
		// Address details
		for (SalesOrderAddress shippingAddress : order.getSalesOrderAddress()) {
			if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {

				if (ObjectUtils.isNotEmpty(custEntity) && null != custEntity.getEntityId()) {
					orderAddress.setFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setLastName(parseNullStr(shippingAddress.getLastname()));
					resp.setEmail(parseNullStr(custEntity.getEmail()));
					orderAddress.setCustomerAccountFirstName(parseNullStr(custEntity.getFirstName()));
					orderAddress.setCustomerAccountLastName(parseNullStr(custEntity.getLastName()));
				} else {
					orderAddress.setFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setLastName(parseNullStr(shippingAddress.getLastname()));
					resp.setEmail(parseNullStr(order.getCustomerEmail()));
					orderAddress.setCustomerAccountFirstName(parseNullStr(shippingAddress.getFirstname()));
					orderAddress.setCustomerAccountLastName(shippingAddress.getLastname());
				}

				orderAddress.setMobileNumber(parseNullStr(shippingAddress.getTelephone()));
				orderAddress.setCity(parseNullStr(shippingAddress.getCity()));
				orderAddress.setStreetAddress(parseNullStr(shippingAddress.getStreet()));
				orderAddress.setCountry(parseNullStr(shippingAddress.getCountryId()));
				orderAddress.setRegion(parseNullStr(shippingAddress.getRegion()));
				orderAddress.setPostCode(parseNullStr(shippingAddress.getPostcode()));
				orderAddress.setRegionId(shippingAddress.getRegionId());
				orderAddress.setArea(parseNullStr(shippingAddress.getArea()));
				orderAddress.setLandMark(parseNullStr(shippingAddress.getNearestLandmark()));
                orderAddress.setKsaAddressComplaint(shippingAddress.getKsaAddressComplaint());
                orderAddress.setPostalCode(shippingAddress.getPostalCode());
                orderAddress.setUnitNumber(shippingAddress.getUnitNumber());
                orderAddress.setShortAddress(shippingAddress.getShortAddress());
				orderAddress.setAddressId(shippingAddress.getEntityId());
                orderAddress.setBuildingNumber(parseNullStr(shippingAddress.getBuildingNumber()));
			}
		}

		resp.setShippingAddress(orderAddress);

		if (null != custEntity) {
			resp.setCustomerGroup(Constants.CUSTOMER_GROUP);
			try {
				if (ObjectUtils.isNotEmpty(custEntity.getIsActive())) {
					resp.setCustomerActiveStatus(CustomerStatus.findByAbbr(custEntity.getIsActive()));
				}
			} catch (Exception ecs) {
				LOGGER.error(ecs.getMessage());
			}
		} else {
			resp.setCustomerGroup("GUEST");
		}

		if (tabScreen.equals(SHIPMENT_TPYE)) {
			boolean isSplitOrder = Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder());
			if (!isSplitOrder) {
				if(CollectionUtils.isNotEmpty(order.getSalesShipments())){
					SalesShipment shipment = order.getSalesShipments().stream().findFirst().orElse(null);
					resp.setIsShipmentGenerated(true);

					if (null != shipment) {
						resp.setShipmentId(shipment.getEntityId());
						resp.setIncrementId(parseNullStr(shipment.getIncrementId()));
						resp.setCreatedAt(convertTimeZone(shipment.getCreatedAt(), order.getStoreId()));
						resp.setUpdatedAt(convertTimeZone(shipment.getUpdatedAt(), order.getStoreId()));
					}
				} else {
					resp.setIsShipmentGenerated(false);
				}
			} else {
				resp.setIsShipmentGenerated(true);
				List<SplitSalesOrder> splitSalesOrderList = splitSalesOrderRepository.findByOrderId(order.getEntityId());
				splitSalesOrderList.forEach(splitSalesOrder -> {
					if (CollectionUtils.isEmpty(splitSalesOrder.getSplitSalesShipments())) {
						resp.setIsShipmentGenerated(false);
					}
				});
			}
		}

		if (tabScreen.equals(INVOICE_TYPE) && CollectionUtils.isNotEmpty(order.getSalesInvoices())) {

			resp.setIsInvoicedGenerated(true);

			SalesInvoice invoice = order.getSalesInvoices().stream().findFirst().orElse(null);
			if (null != invoice) {

				resp.setInvoiceId(invoice.getEntityId());
				resp.setIncrementId(parseNullStr(invoice.getIncrementId()));
				resp.setCreatedAt(convertTimeToKSA(invoice.getCreatedAt()));
				resp.setOrderCreatedAt(convertTimeToKSA(order.getCreatedAt()));
				resp.setUpdatedAt(convertTimeZone(invoice.getUpdatedAt(), 1));
				resp.setZatcaQrCode(invoice.getZatcaQRCode());
				resp.setZatcaStatus(invoice.getZatcaStatus());
			}
		} else {

			resp.setIsInvoicedGenerated(false);

		}
		resp.setClientVersion(order.getAppVersion());
		if (order.getSource() == 0) {
			resp.setSource(SourceType.WEB.value);
		} else if (order.getSource() == 1) {
			resp.setSource(SourceType.APP.value);
		} else if (order.getSource() == 2) {
			resp.setSource(SourceType.MSITE.value);
		} else if (order.getSource() == 3) {
			resp.setSource(SourceType.ADMIN.value);
		}
		LOGGER.info("Customer Ip set in response: " +order.getRemoteIp());
		if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
			resp.setCustomerIp(order.getRemoteIp());
		}
		resp.setPurchasedFrom(orderEntityConverter.getStoreName(order.getStoreId()));
		
		if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getRetryPayment()
				&& order.getSubSalesOrder().getRetryPayment().equals(1)) {
			resp.setPaymentExpiresAt(formatTimezone(order.getSubSalesOrder().getOrderExpiredAt(), order.getStoreId()));
			resp.setCanRetryPayment(true);
		}

		List<OrderId> orderIds = new ArrayList<>();

		if(splitSalesOrders.isEmpty()){
			OrderId orderId    = new OrderId();
			orderId.setOrderId(order.getEntityId());
			orderId.setIncrementId(order.getIncrementId());
			orderId.setOrderStatus(order.getStatus());
			orderId.setReturnStatus(orderReturnFlag.get(order.getEntityId()));
			
			// Set shipmentId for regular orders
			if(CollectionUtils.isNotEmpty(order.getSalesShipments())){
				SalesShipment shipment = order.getSalesShipments().stream().findFirst().orElse(null);
				if(shipment != null) {
					orderId.setShipmentId(shipment.getEntityId());
				}
			}
			
			orderIds.add(orderId);
		}
		else {
			splitSalesOrders.forEach(splitSalesOrder -> {
				OrderId orderId = new OrderId();
				orderId.setOrderId(splitSalesOrder.getEntityId());
				orderId.setIncrementId(splitSalesOrder.getIncrementId());
				orderId.setOrderStatus(splitSalesOrder.getStatus());
				orderId.setReturnStatus(orderReturnFlag.get(splitSalesOrder.getEntityId()));
				
				// Set shipmentId for split orders
				if(CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesShipments())){
					SalesShipment shipment = splitSalesOrder.getSplitSalesShipments().iterator().next();
					if(shipment != null) {
						orderId.setShipmentId(shipment.getEntityId());
					}
				}
				
				// Set estimatedDeliveryDate for split orders
				String estimatedDeliveryDate = fetchEstimatedDeliveryDateForShipment(splitSalesOrder.getEntityId());
				if (StringUtils.isNotBlank(estimatedDeliveryDate)) {
					orderId.setEstimatedDeliveryDate(estimatedDeliveryDate);
					LOGGER.info("Set estimatedDeliveryDate: " + estimatedDeliveryDate + " for splitOrderId: " + splitSalesOrder.getEntityId());
				}
				
				orderIds.add(orderId);
			});
		}
		resp.setOrderIds(orderIds);
		
		LOGGER.info("OmsorderentityConverter convertOrderObject resp.getPaymentInformation.getCommandType: "+resp.getPaymentInformation().getCommandType());
	

		return resp;
	}

    @Transactional(readOnly = true)
    private List<SalesShipmentTrack> getSalesShipmentTracks(SalesOrder order) {
        return shipmentTrackerRepository.findByOrderId(order.getEntityId());
    }

	private void setInvoiceTotals(SalesOrder order, OrdersDetailsResponsedto resp, OrderTotal totals) {

		LOGGER.info("set Invoice details for OrderId " + order.getEntityId());
		BigDecimal totalTaxAmount = (totals.getTaxAmount() != null && !totals.getTaxAmount().isEmpty()) 
			? new BigDecimal(totals.getTaxAmount()) : BigDecimal.ZERO;
		BigDecimal taxFactor = totals.getTaxFactor() != null ? totals.getTaxFactor() : BigDecimal.ONE;
		BigDecimal voucher = BigDecimal.ZERO;
		voucher = order.getGiftVoucherDiscount();
		
		BigDecimal totalTaxableAmount = totals.getTotalTaxableAmount();
		
		totals.setCurrency(parseNullStr(order.getOrderCurrencyCode()));

		BigDecimal couponDiscount = order.getDiscountAmount();

		if (null != couponDiscount && null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getExternalAutoCouponAmount()) {

			couponDiscount = couponDiscount.abs().subtract(order.getSubSalesOrder().getExternalAutoCouponAmount());
		}
		if(null != couponDiscount) {
			couponDiscount = couponDiscount.subtract(voucher);
		}

		totals.setCouponDiscountAmount(parseNullStr(couponDiscount));
		totals.setCouponCode(parseNullStr(order.getCouponCode()));

		if (null != order.getSubSalesOrder() && StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData())) {
			List<DiscountData> discountDataList = null;
			try {
				discountDataList = Arrays
						.asList(mapper.readValue(order.getSubSalesOrder().getDiscountData(), DiscountData[].class));
			} catch (JsonProcessingException e) {
				LOGGER.error("exception occurred during convert offer string to object" + e.getMessage());
			}
			totals.setDiscountData(discountDataList);
		}

		if (!order.getSalesInvoices().isEmpty()) {
			SalesInvoice invoice = order.getSalesInvoices().iterator().next();
			totals.setStoreCreditAmount(parseNullStr(invoice.getAmstorecreditAmount()));
			totals.setBaseStoreCreditAmount(parseNullStr(invoice.getAmstorecreditBaseAmount()));
			totals.setSubtotalInclTax(parseNullStr(invoice.getSubtotalInclTax()));
			totals.setBaseSubtotalInclTax(parseNullStr(invoice.getBaseSubtotalInclTax()));
			
			BigDecimal totalDiscount = BigDecimal.ZERO;
			if(null != invoice.getZatcaStatus()) {
				if(null != totals.getTotalProductLevelDiscountExclTax() && !totals.getTotalProductLevelDiscountExclTax().isEmpty()) {
					totalDiscount = totalDiscount.add(new BigDecimal(totals.getTotalProductLevelDiscountExclTax()));
				}
				if(null != totals.getTotalCouponDiscountExclTax() && !totals.getTotalCouponDiscountExclTax().isEmpty()) {
					totalDiscount = totalDiscount.add(new BigDecimal(totals.getTotalCouponDiscountExclTax()));
				}
				totalDiscount = totalDiscount.subtract(voucher);
			}else {
				totalDiscount = invoice.getDiscountAmount();
			}
			// Format BigDecimal to avoid scientific notation (e.g., "0E-8")
			if (totalDiscount != null && totalDiscount.abs().compareTo(new BigDecimal("0.01")) < 0) {
				totals.setDiscountAmount("0.00");
			} else {
				totals.setDiscountAmount(parseNullStr(totalDiscount != null ? totalDiscount.setScale(2, RoundingMode.HALF_UP) : null));
			}
			totals.setBaseDiscountAmount(parseNullStr(getBaseValueDecimal(totalDiscount, order.getStoreToBaseRate())));
			
			totals.setTaxAmount(parseNullStr(invoice.getTaxAmount()));
			totals.setBaseTaxAmount(parseNullStr(invoice.getBaseTaxAmount()));
			totals.setStoreCreditAmount(parseNullStr(invoice.getAmstorecreditAmount()));
			totals.setBaseStoreCreditAmount(parseNullStr(invoice.getAmstorecreditBaseAmount()));
			
			if (null != order.getSubSalesOrder()) {
				totals.setDonationAmount(parseNullStr(order.getSubSalesOrder().getDonationAmount()));
				totals.setBaseDonationAmount(parseNullStr(order.getSubSalesOrder().getBaseDonationAmount()));
			}
			if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getWarehouseLocationId()) {

				resp.setWarehouseId(order.getSubSalesOrder().getWarehouseLocationId().toString());
			}
			
			if (null != order.getCashOnDeliveryFee() && order.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) != 0) {
				BigDecimal codChargesOrder = order.getCashOnDeliveryFee().divide(taxFactor, 2, RoundingMode.HALF_UP);
				totals.setCodCharges(parseNullStr(codChargesOrder));
				totals.setBaseCodCharges(parseNullStr(getBaseValueDecimal(codChargesOrder, order.getStoreToBaseRate())));
				totals.setCodTaxCharges(parseNullStr(order.getCashOnDeliveryFee().subtract(codChargesOrder)));
				totalTaxAmount = totalTaxAmount.add(order.getCashOnDeliveryFee().subtract(codChargesOrder));
				totalTaxableAmount = totalTaxableAmount.add(codChargesOrder);
			}
			if (null != order.getImportFee() && order.getImportFee().compareTo(BigDecimal.ZERO) != 0) {
				BigDecimal importFeeOrder = order.getImportFee().divide(taxFactor, 2, RoundingMode.HALF_UP);
				totals.setImportFeesAmount(parseNullStr(importFeeOrder));
				totals.setBaseImportFeesAmount(parseNullStr(getBaseValueDecimal(importFeeOrder, order.getStoreToBaseRate())));				
				totalTaxAmount = totalTaxAmount.add(order.getImportFee().subtract(importFeeOrder));
				totalTaxableAmount = totalTaxableAmount.add(importFeeOrder);
			}
			if (null != order.getShippingAmount() && order.getShippingAmount().compareTo(BigDecimal.ZERO) != 0) {		
				BigDecimal shippingAmountOrder = order.getShippingAmount().divide(taxFactor, 2, RoundingMode.HALF_UP);
				totals.setShippingAmount(parseNullStr(shippingAmountOrder));
				totals.setBaseShippingAmount(parseNullStr(getBaseValueDecimal(shippingAmountOrder, order.getStoreToBaseRate())));
				totals.setShippingTaxAmount(parseNullStr(order.getShippingAmount().subtract(shippingAmountOrder)));
				totalTaxAmount = totalTaxAmount.add(order.getShippingAmount().subtract(shippingAmountOrder));
				totalTaxableAmount = totalTaxableAmount.add(shippingAmountOrder);
			}
			
			// totals.setTaxAmount(parseNullStr(totalTaxAmount));
			// totals.setBaseTaxAmount(parseNullStr(getBaseValueDecimal(totalTaxAmount, order.getStoreToBaseRate())));
			
			BigDecimal coinInCurrency = BigDecimal.ZERO;
			if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getInitialEasCoins()) {
				LOGGER.info("EAS coin added to invoice initial coins: " + order.getSubSalesOrder().getInitialEasCoins() + ", coins" + order.getSubSalesOrder().getEasCoins());
				totals.setSpendCoin(order.getSubSalesOrder().getEasCoins());
				totals.setCoinToCurrency(parseNullStr(order.getSubSalesOrder().getEasValueInCurrency()));
				totals.setCoinToBaseCurrency(parseNullStr(order.getSubSalesOrder().getEasValueInBaseCurrency()));
				totals.setInitialSpendCoin(order.getSubSalesOrder().getInitialEasCoins());
				totals.setInitialCoinToCurrency(parseNullStr(order.getSubSalesOrder().getInitialEasValueInCurrency()));
				totals.setInitialCoinToBaseCurrency(parseNullStr(order.getSubSalesOrder().getInitialEasValueInBaseCurrency()));
				coinInCurrency = coinInCurrency.add(order.getSubSalesOrder().getEasValueInCurrency());
			}
			if(order.getSubSalesOrder() != null){
				if(order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
					totals.setTotalShukranBurnedValueInCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
					totals.setTotalShukranBurnedPoints(order.getSubSalesOrder().getTotalShukranCoinsBurned());
					totals.setTotalShukranBurnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
				}
				if(order.getSubSalesOrder().getTotalShukranCoinsEarned() != null && order.getSubSalesOrder().getTotalShukranCoinsEarned().compareTo(BigDecimal.ZERO)>0){
					totals.setTotalShukranEarnedPoints(order.getSubSalesOrder().getTotalShukranCoinsEarned());
					totals.setTotalShukranEarnedValueInCurrency(order.getSubSalesOrder().getTotalShukranEarnedValueInCurrency());
					totals.setTotalShukranEarnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranEarnedValueInBaseCurrency());
				}
				if(StringUtils.isNotEmpty(order.getSubSalesOrder().getTierName()) && StringUtils.isNotBlank(order.getSubSalesOrder().getTierName())){
					totals.setShukranTierName(order.getSubSalesOrder().getTierName());
				}
				if(StringUtils.isNotEmpty(order.getSubSalesOrder().getShukranPhoneNumber()) && StringUtils.isNotBlank(order.getSubSalesOrder().getShukranPhoneNumber())){
					totals.setShukranPhoneNumber(order.getSubSalesOrder().getShukranPhoneNumber());
				}
			}

			totals.setTotalTaxableAmount(totalTaxableAmount);
			
			updateGrandTotal(totals, invoice.getGrandTotal(), invoice.getAmstorecreditAmount(), coinInCurrency, voucher, order.getStoreToBaseRate());
		}
		totals.setGiftVoucherAmount(parseNullStr(order.getGiftVoucherDiscount()));
		resp.setTotals(totals);
	}

    private void setInvoiceTotalsForSplitOrder(SplitSalesOrder order, OrdersDetailsResponsedto resp, OrderTotal totals) {

        LOGGER.info("set Invoice details for OrderId " + order.getEntityId());
        BigDecimal totalTaxAmount = (totals.getTaxAmount() != null && !totals.getTaxAmount().isEmpty()) 
			? new BigDecimal(totals.getTaxAmount()) : BigDecimal.ZERO;
        BigDecimal taxFactor = totals.getTaxFactor() != null ? totals.getTaxFactor() : BigDecimal.ONE;
        BigDecimal voucher = Optional.ofNullable(order.getGiftVoucherDiscount()).orElse(BigDecimal.ZERO);

        BigDecimal totalTaxableAmount = totals.getTotalTaxableAmount();

        totals.setCurrency(parseNullStr(order.getOrderCurrencyCode()));

        BigDecimal couponDiscount = order.getDiscountAmount();

        if (null != couponDiscount && null != order.getSplitSubSalesOrder()
                && null != order.getSplitSubSalesOrder().getExternalAutoCouponAmount()) {

            couponDiscount = couponDiscount.abs().subtract(order.getSplitSubSalesOrder().getExternalAutoCouponAmount());
        }
        if(null != couponDiscount) {
            couponDiscount = couponDiscount.subtract(voucher);
        }

        totals.setCouponDiscountAmount(parseNullStr(couponDiscount));
        totals.setCouponCode(parseNullStr(order.getCouponCode()));

        if (null != order.getSplitSubSalesOrder() && StringUtils.isNotBlank(order.getSplitSubSalesOrder().getDiscountData())) {
            List<DiscountData> discountDataList = null;
            try {
                discountDataList = Arrays
                        .asList(mapper.readValue(order.getSplitSubSalesOrder().getDiscountData(), DiscountData[].class));
            } catch (JsonProcessingException e) {
                LOGGER.error("exception occurred during convert offer string to object" + e.getMessage());
            }
            totals.setDiscountData(discountDataList);
        }

        if (!order.getSplitSalesInvoices().isEmpty()) {
            SalesInvoice invoice = order.getSplitSalesInvoices().iterator().next();
            totals.setStoreCreditAmount(parseNullStr(invoice.getAmstorecreditAmount()));
            totals.setBaseStoreCreditAmount(parseNullStr(invoice.getAmstorecreditBaseAmount()));
            totals.setSubtotalInclTax(parseNullStr(invoice.getSubtotalInclTax()));
            totals.setBaseSubtotalInclTax(parseNullStr(invoice.getBaseSubtotalInclTax()));

            BigDecimal totalDiscount = BigDecimal.ZERO;
            if(null != invoice.getZatcaStatus()) {
                if(null != totals.getTotalProductLevelDiscountExclTax() && !totals.getTotalProductLevelDiscountExclTax().isEmpty()) {
                    totalDiscount = totalDiscount.add(new BigDecimal(totals.getTotalProductLevelDiscountExclTax()));
                }
                if(null != totals.getTotalCouponDiscountExclTax() && !totals.getTotalCouponDiscountExclTax().isEmpty()) {
                    totalDiscount = totalDiscount.add(new BigDecimal(totals.getTotalCouponDiscountExclTax()));
                }
                totalDiscount = totalDiscount.subtract(voucher);
            }else {
                totalDiscount = invoice.getDiscountAmount();
            }
            // Format BigDecimal to avoid scientific notation (e.g., "0E-8")
            if (totalDiscount != null && totalDiscount.abs().compareTo(new BigDecimal("0.01")) < 0) {
                totals.setDiscountAmount("0.00");
            } else {
                totals.setDiscountAmount(parseNullStr(totalDiscount != null ? totalDiscount.setScale(2, RoundingMode.HALF_UP) : null));
            }
            totals.setBaseDiscountAmount(parseNullStr(getBaseValueDecimal(totalDiscount, order.getStoreToBaseRate())));

            totals.setTaxAmount(parseNullStr(invoice.getTaxAmount()));
            totals.setBaseTaxAmount(parseNullStr(invoice.getBaseTaxAmount()));
            totals.setStoreCreditAmount(parseNullStr(invoice.getAmstorecreditAmount()));
            totals.setBaseStoreCreditAmount(parseNullStr(invoice.getAmstorecreditBaseAmount()));

            if (null != order.getSplitSubSalesOrder()) {
                totals.setDonationAmount(parseNullStr(order.getSplitSubSalesOrder().getDonationAmount()));
                totals.setBaseDonationAmount(parseNullStr(order.getSplitSubSalesOrder().getBaseDonationAmount()));
            }
            if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getWarehouseLocationId()) {

                resp.setWarehouseId(order.getSplitSubSalesOrder().getWarehouseLocationId().toString());
            }

            if (null != order.getCashOnDeliveryFee() && order.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal codChargesOrder = order.getCashOnDeliveryFee().divide(taxFactor, 2, RoundingMode.HALF_UP);
                totals.setCodCharges(parseNullStr(codChargesOrder));
                totals.setBaseCodCharges(parseNullStr(getBaseValueDecimal(codChargesOrder, order.getStoreToBaseRate())));
                totals.setCodTaxCharges(parseNullStr(order.getCashOnDeliveryFee().subtract(codChargesOrder)));
                totalTaxAmount = totalTaxAmount.add(order.getCashOnDeliveryFee().subtract(codChargesOrder));
                totalTaxableAmount = totalTaxableAmount.add(codChargesOrder);
            }
            if (null != order.getImportFee() && order.getImportFee().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal importFeeOrder = order.getImportFee().divide(taxFactor, 2, RoundingMode.HALF_UP);
                totals.setImportFeesAmount(parseNullStr(importFeeOrder));
                totals.setBaseImportFeesAmount(parseNullStr(getBaseValueDecimal(importFeeOrder, order.getStoreToBaseRate())));
                totalTaxAmount = totalTaxAmount.add(order.getImportFee().subtract(importFeeOrder));
                totalTaxableAmount = totalTaxableAmount.add(importFeeOrder);
            }
            if (null != order.getShippingAmount() && order.getShippingAmount().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal shippingAmountOrder = order.getShippingAmount().divide(taxFactor, 2, RoundingMode.HALF_UP);
                totals.setShippingAmount(parseNullStr(shippingAmountOrder));
                totals.setBaseShippingAmount(parseNullStr(getBaseValueDecimal(shippingAmountOrder, order.getStoreToBaseRate())));
                totals.setShippingTaxAmount(parseNullStr(order.getShippingAmount().subtract(shippingAmountOrder)));
                totalTaxAmount = totalTaxAmount.add(order.getShippingAmount().subtract(shippingAmountOrder));
                totalTaxableAmount = totalTaxableAmount.add(shippingAmountOrder);
            }

            // totals.setTaxAmount(parseNullStr(totalTaxAmount));
            // totals.setBaseTaxAmount(parseNullStr(getBaseValueDecimal(totalTaxAmount, order.getStoreToBaseRate())));

            BigDecimal coinInCurrency = BigDecimal.ZERO;
            if (null != order.getSplitSubSalesOrder() && null != order.getSplitSubSalesOrder().getInitialEasCoins()) {
                LOGGER.info("EAS coin added to invoice initial coins: " + order.getSplitSubSalesOrder().getInitialEasCoins() + ", coins" + order.getSplitSubSalesOrder().getEasCoins());
                totals.setSpendCoin(order.getSplitSubSalesOrder().getEasCoins());
                totals.setCoinToCurrency(parseNullStr(order.getSplitSubSalesOrder().getEasValueInCurrency()));
                totals.setCoinToBaseCurrency(parseNullStr(order.getSplitSubSalesOrder().getEasValueInBaseCurrency()));
                totals.setInitialSpendCoin(order.getSplitSubSalesOrder().getInitialEasCoins());
                totals.setInitialCoinToCurrency(parseNullStr(order.getSplitSubSalesOrder().getInitialEasValueInCurrency()));
                totals.setInitialCoinToBaseCurrency(parseNullStr(order.getSplitSubSalesOrder().getInitialEasValueInBaseCurrency()));
                coinInCurrency = coinInCurrency.add(order.getSplitSubSalesOrder().getEasValueInCurrency());
            }
            if(order.getSplitSubSalesOrder() != null){
                if(order.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
                    totals.setTotalShukranBurnedValueInCurrency(order.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency());
                    totals.setTotalShukranBurnedPoints(order.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
                    totals.setTotalShukranBurnedValueInBaseCurrency(order.getSplitSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
                }
                if(order.getSplitSubSalesOrder().getTotalShukranCoinsEarned() != null && order.getSplitSubSalesOrder().getTotalShukranCoinsEarned().compareTo(BigDecimal.ZERO)>0){
                    totals.setTotalShukranEarnedPoints(order.getSplitSubSalesOrder().getTotalShukranCoinsEarned());
                    totals.setTotalShukranEarnedValueInCurrency(order.getSplitSubSalesOrder().getTotalShukranEarnedValueInCurrency());
                    totals.setTotalShukranEarnedValueInBaseCurrency(order.getSplitSubSalesOrder().getTotalShukranEarnedValueInBaseCurrency());
                }
                if(StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getTierName()) && StringUtils.isNotBlank(order.getSplitSubSalesOrder().getTierName())){
                    totals.setShukranTierName(order.getSplitSubSalesOrder().getTierName());
                }
                if(StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getShukranPhoneNumber()) && StringUtils.isNotBlank(order.getSplitSubSalesOrder().getShukranPhoneNumber())){
                    totals.setShukranPhoneNumber(order.getSplitSubSalesOrder().getShukranPhoneNumber());
                }
            }

            totals.setTotalTaxableAmount(totalTaxableAmount);

            updateGrandTotal(totals, invoice.getGrandTotal(), invoice.getAmstorecreditAmount(), coinInCurrency, voucher, order.getStoreToBaseRate());
        }
        totals.setGiftVoucherAmount(parseNullStr(order.getGiftVoucherDiscount()));
        resp.setTotals(totals);
    }

    private void setOrderTotals(SalesOrder order, OrdersDetailsResponsedto resp, OrderTotal totals) {
		BigDecimal totalTaxAmount = (totals.getTaxAmount() != null && !totals.getTaxAmount().isEmpty()) 
			? new BigDecimal(totals.getTaxAmount()) : BigDecimal.ZERO;
		BigDecimal taxFactor = totals.getTaxFactor() != null ? totals.getTaxFactor() : BigDecimal.ONE;
		BigDecimal voucher = order.getGiftVoucherDiscount();
		
		BigDecimal totalTaxableAmount = totals.getTotalTaxableAmount();

		BigDecimal totalBagDiscount = new BigDecimal(0);
		if (totalBagDiscount.doubleValue() < 0) {
			totals.setDiscountAmount("0.0");
		} else {
			totals.setDiscountAmount(parseNullStr(totalBagDiscount.doubleValue()));
			totals.setBaseDiscountAmount("");
		}

		BigDecimal couponDiscount = order.getDiscountAmount();
		if (null != couponDiscount && null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getExternalAutoCouponAmount()) {

			couponDiscount = couponDiscount.abs().subtract(order.getSubSalesOrder().getExternalAutoCouponAmount());
		}
		if(null != couponDiscount) {
			couponDiscount = couponDiscount.subtract(voucher);
		}
		totals.setCouponDiscountAmount(parseNullStr(couponDiscount));
		totals.setCouponCode(parseNullStr(order.getCouponCode()));

		if (null != order.getSubSalesOrder()) {
			if(StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData())) {
				List<DiscountData> discountDataList = null;
				try {
					discountDataList = Arrays
							.asList(mapper.readValue(order.getSubSalesOrder().getDiscountData(), DiscountData[].class));
				} catch (JsonProcessingException e) {
					LOGGER.error("exception occurred during convert offer string to object" + e.getMessage());
				}
				totals.setDiscountData(discountDataList);
			}
			if(order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
				totals.setTotalShukranBurnedPoints(order.getSubSalesOrder().getTotalShukranCoinsBurned());
				totals.setTotalShukranBurnedValueInCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
				totals.setTotalShukranBurnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
			}
			if(order.getSubSalesOrder().getTotalShukranCoinsEarned() != null && order.getSubSalesOrder().getTotalShukranCoinsEarned().compareTo(BigDecimal.ZERO)>0){
				totals.setTotalShukranEarnedPoints(order.getSubSalesOrder().getTotalShukranCoinsEarned());
				totals.setTotalShukranEarnedValueInCurrency(order.getSubSalesOrder().getTotalShukranEarnedValueInCurrency());
				totals.setTotalShukranEarnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranEarnedValueInBaseCurrency());
			}
			if(StringUtils.isNotEmpty(order.getSubSalesOrder().getTierName()) && StringUtils.isNotBlank(order.getSubSalesOrder().getTierName())){
				totals.setShukranTierName(order.getSubSalesOrder().getTierName());
			}
			if(StringUtils.isNotEmpty(order.getSubSalesOrder().getShukranPhoneNumber()) && StringUtils.isNotBlank(order.getSubSalesOrder().getShukranPhoneNumber())){
				totals.setShukranPhoneNumber(order.getSubSalesOrder().getShukranPhoneNumber());
			}
		}


		totals.setSubtotal(parseNullStr(order.getSubtotal()));
		totals.setBaseSubtotal(parseNullStr(order.getBaseSubtotal()));
		totals.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		totals.setStoreCreditAmount(parseNullStr(order.getAmstorecreditAmount()));
		totals.setBaseStoreCreditAmount(parseNullStr(order.getAmstorecreditBaseAmount()));

		BigDecimal localCodCharges  = null!=order.getCashOnDeliveryFee() ? order.getCashOnDeliveryFee() : BigDecimal.ZERO;
		BigDecimal globalCodCharges = null!=order.getGlobalCashOnDeliveryFee() ? order.getGlobalCashOnDeliveryFee() : BigDecimal.ZERO;
		if (localCodCharges.compareTo(BigDecimal.ZERO) != 0 || globalCodCharges.compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal localCodChargesOrder  = localCodCharges.divide(taxFactor, 2, RoundingMode.HALF_UP);
			BigDecimal globalCodChargesOrder = globalCodCharges.divide(taxFactor, 2, RoundingMode.HALF_UP);
			BigDecimal codChargesOrder = localCodChargesOrder.add(globalCodChargesOrder);
			totals.setCodCharges(parseNullStr(codChargesOrder));
			totals.setBaseCodCharges(parseNullStr(getBaseValueDecimal(codChargesOrder, order.getStoreToBaseRate())));
			BigDecimal totalCodCharges = localCodCharges.add(globalCodCharges);
			totalTaxAmount = totalTaxAmount.add(totalCodCharges.subtract(codChargesOrder));
			totalTaxableAmount = totalTaxableAmount.add(codChargesOrder);
		}
		if (null != order.getImportFee() && order.getImportFee().compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal importFeeOrder = order.getImportFee().divide(taxFactor, 2, RoundingMode.HALF_UP);
			totals.setImportFeesAmount(parseNullStr(importFeeOrder));
			totals.setBaseImportFeesAmount(parseNullStr(getBaseValueDecimal(importFeeOrder, order.getStoreToBaseRate())));
			
			totalTaxAmount = totalTaxAmount.add(order.getImportFee().subtract(importFeeOrder));
			totalTaxableAmount = totalTaxableAmount.add(importFeeOrder);
		}
		BigDecimal localShippingAmount  = null!=order.getShippingAmount() ? order.getShippingAmount() : BigDecimal.ZERO;
		BigDecimal globalShippingAmount = null!=order.getGlobalShippingAmount() ? order.getGlobalShippingAmount() : BigDecimal.ZERO;
		if (localShippingAmount.compareTo(BigDecimal.ZERO) != 0 || globalShippingAmount.compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal localShippingOrder  = localShippingAmount.divide(taxFactor, 2, RoundingMode.HALF_UP);
			BigDecimal globalShippingOrder = globalShippingAmount.divide(taxFactor, 2, RoundingMode.HALF_UP);
			BigDecimal shippingAmountOrder = localShippingOrder.add(globalShippingOrder);
			totals.setShippingAmount(parseNullStr(shippingAmountOrder));
			totals.setBaseShippingAmount(parseNullStr(getBaseValueDecimal(shippingAmountOrder, order.getStoreToBaseRate())));
			BigDecimal totalShippingAmount = localShippingAmount.add(globalShippingAmount);
			totalTaxAmount     = totalTaxAmount.add(totalShippingAmount.subtract(shippingAmountOrder));
			totalTaxableAmount = totalTaxableAmount.add(shippingAmountOrder);
		}
		
		totals.setTaxAmount(parseNullStr(totalTaxAmount));
		totals.setBaseTaxAmount(parseNullStr(getBaseValueDecimal(totalTaxAmount, order.getStoreToBaseRate())));
		
		totals.setTotalPaid(parseNullStr(order.getGrandTotal()));
		totals.setBaseTotalPaid(parseNullStr(order.getBaseGrandTotal()));
		totals.setSubtotalInclTax(parseNullStr(order.getSubtotalInclTax()));
		totals.setBaseSubtotalInclTax(parseNullStr(order.getBaseSubtotalInclTax()));
		
		

		List<SalesCreditmemo> creditmemoList = salesCreditmemoRepository.findByOrderId(order.getEntityId());
		if (CollectionUtils.isNotEmpty(creditmemoList)) {
			BigDecimal totalRefunded = BigDecimal.ZERO;
			BigDecimal totalRefundedCoins = BigDecimal.ZERO;
			BigDecimal totalRefundedCoinCurrency = BigDecimal.ZERO;
			BigDecimal totalRefundedCoinBaseCurrency = BigDecimal.ZERO;
			BigDecimal totalVoucherRefunded = BigDecimal.ZERO;

			for (SalesCreditmemo creditMemo : creditmemoList) {

				totalRefunded = totalRefunded.add(creditMemo.getGrandTotal());
				if (null != creditMemo.getAmstorecreditAmount()) {
					totalRefunded = totalRefunded.add(creditMemo.getAmstorecreditAmount());
				}
				if(null != creditMemo.getEasCoins()) {
					totalRefundedCoins = totalRefundedCoins.add(new BigDecimal(creditMemo.getEasCoins()));
				}
				if(null != creditMemo.getEasValueInCurrency()) {
					totalRefundedCoinCurrency = totalRefundedCoinCurrency.add(creditMemo.getEasValueInCurrency());
				}
				if(null != creditMemo.getEasValueInBaseCurrency()) {
					totalRefundedCoinBaseCurrency = totalRefundedCoinBaseCurrency.add(creditMemo.getEasValueInBaseCurrency());
				}
				if(null != creditMemo.getVoucherAmount()) {
					totalVoucherRefunded = totalVoucherRefunded.add(creditMemo.getVoucherAmount());		
				}
				
			}
			if (totalRefunded.compareTo(BigDecimal.ZERO) != 0) {

				BigDecimal baseRefunded = totalRefunded.multiply(order.getStoreToBaseRate()).setScale(4,
						RoundingMode.HALF_UP);
				totals.setTotalBaseRefunded(parseNullStr(baseRefunded));
				totals.setTotalRefunded(parseNullStr(totalRefunded));

			}
			if (totalRefundedCoins.compareTo(BigDecimal.ZERO) != 0) {
				totals.setRefundedCoin(totalRefundedCoins.intValue());
				totals.setRefundedCoinToCurrency(totalRefundedCoinCurrency.toString());
				totals.setRefundedCoinToBaseCurrency(totalRefundedCoinBaseCurrency.toString());
			}
			
			totals.setRefundedVoucherAmount(parseNullStr(totalVoucherRefunded));
		}
		
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {
			totals.setDonationAmount(parseNullStr(order.getSubSalesOrder().getDonationAmount()));
			totals.setBaseDonationAmount(parseNullStr(order.getSubSalesOrder().getBaseDonationAmount()));
		}

		//EAS coins in invoice detail.
		BigDecimal coinInCurrency = BigDecimal.ZERO;
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getInitialEasCoins()) {
			totals.setInitialSpendCoin(order.getSubSalesOrder().getInitialEasCoins());
			totals.setInitialCoinToCurrency(parseNullStr(order.getSubSalesOrder().getInitialEasValueInCurrency()));
			totals.setInitialCoinToBaseCurrency(parseNullStr(order.getSubSalesOrder().getInitialEasValueInCurrency()));
			
			totals.setSpendCoin(order.getSubSalesOrder().getEasCoins());
			totals.setCoinToCurrency(parseNullStr(order.getSubSalesOrder().getEasValueInCurrency()));
			totals.setCoinToBaseCurrency(parseNullStr(order.getSubSalesOrder().getEasValueInBaseCurrency()));
			coinInCurrency = coinInCurrency.add(order.getSubSalesOrder().getEasValueInCurrency());	
		}
		
		totals.setTotalTaxableAmount(totalTaxableAmount);
		
		updateGrandTotal(totals, order.getGrandTotal(), order.getAmstorecreditAmount(), coinInCurrency, voucher, order.getStoreToBaseRate());
		
		if (!order.getSalesInvoices().isEmpty()) {
			SalesInvoice invoice = order.getSalesInvoices().iterator().next();
			resp.setZatcaStatus(invoice.getZatcaStatus());
		}
		resp.setTotals(totals);
	}
	/**
	 * Old format method for setting invoice totals
	 */
	private void setInvoiceTotalsOldFormat(SalesOrder order, OrdersDetailsResponsedto resp, OrderTotal totals) {
		// Use the same logic as the original setInvoiceTotals method
		setInvoiceTotals(order, resp, totals);
	}

	/**
	 * Old format method for setting order totals
	 */
	private void setOrderTotalsOldFormat(SalesOrder order, OrdersDetailsResponsedto resp, OrderTotal totals) {
		// Use the same logic as the original setOrderTotals method
		setOrderTotals(order, resp, totals);
	}

	private boolean setShipmentItems(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, Map<String, ProductResponseBody> productsFromMulin,
		OrdersDetailsResponsedto resp, List<OrderHistory> histories) {

		ShipmentItemBuilder builder = new ShipmentItemBuilder(productsFromMulin, this);
		List<OmsProduct> shipmentItems = builder.buildShipmentItems(order, splitSalesOrders, histories, new TrackingDetails());
		
		resp.getProducts().addAll(shipmentItems);
		return true;
	}

	/**
	 * Old format method for setting order items
	 */
	private void setOrderItemsOldFormat(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin, OrdersDetailsResponsedto resp, OrderTotal totals, Stores store, List<OrderHistory> histories, TrackingDetails trackingDetails) {
		// Initialize arrays with BigDecimal.ZERO to avoid null pointer exceptions
		BigDecimal[] totalShukranBurnedPointsToShowInUI = {BigDecimal.ZERO};
		BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI = {BigDecimal.ZERO};
		BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI = {BigDecimal.ZERO};
		BigDecimal[] totalTaxAmount = {BigDecimal.ZERO};
		BigDecimal[] totalProductLevelDiscountExclTax = {BigDecimal.ZERO};
		BigDecimal[] totalCouponDiscountExclTax = {BigDecimal.ZERO};
		BigDecimal[] totalPriceExclTax = {BigDecimal.ZERO};
		BigDecimal[] totaltaxablePrice = {BigDecimal.ZERO};

		if(splitSalesOrders.isEmpty()){
			setProductForOrderOldFormat(order, orderReturnFlag, productsFromMulin, resp, totals, totalShukranBurnedPointsToShowInUI, totalShukranBurnedPointsToShowInBaseCurrencyInUI, totalShukranBurnedPointsToShowInCurrencyInUI, totalTaxAmount, totalProductLevelDiscountExclTax, totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice, histories, trackingDetails);
		}
		else {
			setProductForSplitOrderOldFormat(order.getEntityId(), splitSalesOrders, orderReturnFlag, productsFromMulin, resp, totals, histories, totalShukranBurnedPointsToShowInUI, totalShukranBurnedPointsToShowInBaseCurrencyInUI, totalShukranBurnedPointsToShowInCurrencyInUI, totalTaxAmount, totalProductLevelDiscountExclTax, totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice);
			
			// Set orders array with shipment-wise ETA for split orders
			List<OrderDetailsResponse> ordersList = createSplitOrderDetailsForOldFormat(order, splitSalesOrders, store, histories);
			resp.setOrders(ordersList);
		}
	}

	private void setProductForSplitOrderOldFormat(Integer orderId, List<SplitSalesOrder> splitSalesOrders, Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin, OrdersDetailsResponsedto resp, OrderTotal totals, List<OrderHistory> histories, BigDecimal[] totalShukranBurnedPointsToShowInUI, BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI, BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI, BigDecimal[] totalTaxAmount, BigDecimal[] totalProductLevelDiscountExclTax, BigDecimal[] totalCouponDiscountExclTax, BigDecimal[] totalPriceExclTax, BigDecimal[] totaltaxablePrice) {
		ProductBuilder productBuilder = new ProductBuilder(productsFromMulin, orderHelper, this);
        Map<String, List<StatusMessage>> skuStatusMap = populateSkuStatusMapFromOTS(splitSalesOrders.get(0).getSalesOrder().getEntityId());
		BigDecimal[] taxFactor = {BigDecimal.ZERO};
		splitSalesOrders.forEach(splitSalesOrder -> {
			List<SplitSalesOrderItem> splitSalesOrderItems = splitSalesOrder.getSplitSalesOrderItems().stream().toList();
			for (SplitSalesOrderItem splitSalesOrderItem : splitSalesOrderItems) {
				if (splitSalesOrderItem.getSplitSalesOrderItem() == null) {
					OmsProduct productDTO = productBuilder.buildProductFromSplitSalesOrderItem(splitSalesOrderItem, orderId, splitSalesOrder.getIncrementId(), resp);

					// Add Shukran points to arrays
					if (splitSalesOrderItem.getShukranCoinsBurned() != null && splitSalesOrderItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
						totalShukranBurnedPointsToShowInUI[0] = totalShukranBurnedPointsToShowInUI[0].add(splitSalesOrderItem.getShukranCoinsBurned());
						totalShukranBurnedPointsToShowInBaseCurrencyInUI[0] = totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].add(splitSalesOrderItem.getShukranCoinsBurnedValueInBaseCurrency());
						totalShukranBurnedPointsToShowInCurrencyInUI[0] = totalShukranBurnedPointsToShowInCurrencyInUI[0].add(splitSalesOrderItem.getShukranCoinsBurnedValueInCurrency());
					}

					taxFactor[0] = orderHelper.getExclTaxfactor(splitSalesOrderItem.getTaxPercent());
					BigDecimal unitPriceExclTax = splitSalesOrderItem.getOriginalPrice().divide(taxFactor[0], 2, RoundingMode.HALF_UP);
					totalPriceExclTax[0] = totalPriceExclTax[0].add(unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered()));
					
					BigDecimal discountProductLevelExclTax = splitSalesOrderItem.getOriginalPrice().subtract(splitSalesOrderItem.getPriceInclTax());
					discountProductLevelExclTax = discountProductLevelExclTax.multiply(splitSalesOrderItem.getQtyOrdered());
					if(discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
						discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor[0], 2, RoundingMode.HALF_UP);
					}

					BigDecimal giftVoucherAmount = BigDecimal.ZERO;
					if(null != splitSalesOrderItem.getSplitSubSalesOrderItem()) {
						SplitSubSalesOrderItem splitSubSalesOrderItem = splitSalesOrderItem.getSplitSubSalesOrderItem().stream().filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
						if(null != splitSubSalesOrderItem) {
							giftVoucherAmount = splitSubSalesOrderItem.getDiscount();
						}
					}
					
					BigDecimal discountCouponExclTaxProduct = splitSalesOrderItem.getDiscountAmount().subtract(giftVoucherAmount);
					discountCouponExclTaxProduct = discountCouponExclTaxProduct.divide(taxFactor[0], 2, RoundingMode.HALF_UP);
					
					BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);

					BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered());
					taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
					.subtract(discountCouponExclTaxProduct)
					.setScale(2, RoundingMode.HALF_UP);

					totaltaxablePrice[0] = totaltaxablePrice[0].add(taxablePriceProduct);
					totalCouponDiscountExclTax[0] = totalCouponDiscountExclTax[0].add(discountCouponExclTaxProduct);
					totalProductLevelDiscountExclTax[0] = totalProductLevelDiscountExclTax[0].add(discountProductLevelExclTax);
					
					// Set return quantities
					splitSalesOrder.getSplitSalesOrderItems().stream()
							.filter(e -> e.getSplitSalesOrderItem() != null && e.getSplitSalesOrderItem().getItemId() != null
									&& e.getSplitSalesOrderItem().getItemId().equals(splitSalesOrderItem.getItemId()))
							.findFirst().ifPresent(childOrderItem -> {
								QuantityReturned quantityReturned = getSalesQtyReturned(childOrderItem.getSplitSalesOrderItem().getSalesOrderItem().getItemId(), false);
								productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
								productDTO.setQtyReturnedInProcess(parseNullStr(childOrderItem.getQtyRefunded()));
							});

					// Set return flag
					String status = splitSalesOrder.getStatus().toLowerCase();
					if (!Objects.equals(status, "delivered")) {
						orderReturnFlag.put(splitSalesOrder.getEntityId(), false);
					} else {
						boolean isReturnable = parseFloat(productDTO.getQty()) > parseFloat(productDTO.getQtyCanceled()) + parseFloat(productDTO.getQtyReturned());
						orderReturnFlag.put(splitSalesOrder.getEntityId(), isReturnable);
					}

					if (splitSalesOrder.getIncrementId() != null) {
						productDTO.setType(splitSalesOrder.getIncrementId().contains(OrderConstants.LOCAL_ORDER_SUFFIX) ? OrderConstants.PRODUCT_TYPE_LOCAL : OrderConstants.PRODUCT_TYPE_GLOBAL);
					}

					setSellerInfo(productDTO, splitSalesOrderItem.getSalesOrderItem());

					List<OrderHistory> historyList = createOrderHistoryFromStatusMessages(skuStatusMap, splitSalesOrderItem.getSku());
					productDTO.setHistories(historyList);
					productDTO.setTrackingDetails(setTrackingDetails(splitSalesOrder));
					resp.getProducts().add(productDTO);
				}
			}
		});

		// Apply totals to the calculator - old format doesn't need store conversion
		if (totalShukranBurnedPointsToShowInUI[0].compareTo(BigDecimal.ZERO) > 0) {
			totals.setTotalShukranBurnedPointsToShowInUI(totalShukranBurnedPointsToShowInUI[0].toBigInteger().intValue());
			totals.setTotalShukranBurnedValueInBaseCurrencyInUI(totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].setScale(2, RoundingMode.HALF_UP));
			totals.setTotalShukranBurnedValueInCurrencyInUI(totalShukranBurnedPointsToShowInCurrencyInUI[0].setScale(2, RoundingMode.HALF_UP));
		}
		totals.setTotalProductLevelDiscountExclTax(parseNullStr(totalProductLevelDiscountExclTax[0]));
		totals.setTotalCouponDiscountExclTax(parseNullStr(totalCouponDiscountExclTax[0]));
		totals.setTotalPriceExclTax(parseNullStr(totalPriceExclTax[0].setScale(2, RoundingMode.HALF_UP)));
		totals.setTaxAmount(parseNullStr(totalTaxAmount[0]));
        System.out.println("setTotalTaxableAmount: " + totaltaxablePrice[0]);

		totals.setTotalTaxableAmount(totaltaxablePrice[0]);
		totals.setTaxFactor(taxFactor[0]);
	}

	private void setProductForOrderOldFormat(SalesOrder order, Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin, OrdersDetailsResponsedto resp, OrderTotal totals, BigDecimal[] totalShukranBurnedPointsToShowInUI, BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI, BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI, BigDecimal[] totalTaxAmount, BigDecimal[] totalProductLevelDiscountExclTax, BigDecimal[] totalCouponDiscountExclTax, BigDecimal[] totalPriceExclTax, BigDecimal[] totaltaxablePrice, List<OrderHistory> histories, TrackingDetails trackingDetails) {
		ProductBuilder productBuilder = new ProductBuilder(productsFromMulin, orderHelper, this);
		Map<String, List<StatusMessage>> skuStatusMap = populateSkuStatusMapFromOTS(order.getEntityId());

		BigDecimal[] taxFactor = {BigDecimal.ZERO};

		for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {
			if (salesOrderItem.getParentOrderItem() == null) {
				OmsProduct productDTO = productBuilder.buildProductFromSalesOrderItem(salesOrderItem, resp, null);

                // Add Shukran points to arrays
				if (salesOrderItem.getShukranCoinsBurned() != null && salesOrderItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
					totalShukranBurnedPointsToShowInUI[0] = totalShukranBurnedPointsToShowInUI[0].add(salesOrderItem.getShukranCoinsBurned());
					totalShukranBurnedPointsToShowInBaseCurrencyInUI[0] = totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].add(salesOrderItem.getShukranCoinsBurnedValueInBaseCurrency());
					totalShukranBurnedPointsToShowInCurrencyInUI[0] = totalShukranBurnedPointsToShowInCurrencyInUI[0].add(salesOrderItem.getShukranCoinsBurnedValueInCurrency());
				}

				taxFactor[0] = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());

				BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor[0], 2, RoundingMode.HALF_UP);
				totalPriceExclTax[0] = totalPriceExclTax[0].add(unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered()));
					
				BigDecimal discountProductLevelExclTax = salesOrderItem.getOriginalPrice().subtract(salesOrderItem.getPriceInclTax());
				discountProductLevelExclTax = discountProductLevelExclTax.multiply(salesOrderItem.getQtyOrdered());
				if(discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
					discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor[0], 2, RoundingMode.HALF_UP);
				}

				BigDecimal giftVoucherAmount = BigDecimal.ZERO;
				if(null != salesOrderItem.getSubSalesOrderItem()) {
					SubSalesOrderItem subSalesOrderItem = salesOrderItem.getSubSalesOrderItem().stream().filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
					if(null != subSalesOrderItem) {
						giftVoucherAmount = subSalesOrderItem.getDiscount();
					}
				}
					
					BigDecimal discountCouponExclTaxProduct = salesOrderItem.getDiscountAmount().subtract(giftVoucherAmount);
					discountCouponExclTaxProduct = discountCouponExclTaxProduct.divide(taxFactor[0], 2, RoundingMode.HALF_UP);
					
					BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);

					BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered());
					taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
					.subtract(discountCouponExclTaxProduct)
					.setScale(2, RoundingMode.HALF_UP);

					totaltaxablePrice[0] = totaltaxablePrice[0].add(taxablePriceProduct);
					totalCouponDiscountExclTax[0] = totalCouponDiscountExclTax[0].add(discountCouponExclTaxProduct);
					totalProductLevelDiscountExclTax[0] = totalProductLevelDiscountExclTax[0].add(discountProductLevelExclTax);
					

				// Set return quantities
				order.getSalesOrderItem().stream()
						.filter(e -> e.getParentOrderItem() != null && e.getParentOrderItem().getItemId() != null
								&& e.getParentOrderItem().getItemId().equals(salesOrderItem.getItemId()))
						.findFirst().ifPresent(childOrderItem -> {
							QuantityReturned quantityReturned = getSalesQtyReturned(childOrderItem.getItemId(), false);
							productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
							productDTO.setQtyReturnedInProcess(parseNullStr(childOrderItem.getQtyRefunded()));
						});

				// Set return flag
				String status = order.getStatus().toLowerCase();
				if (!Objects.equals(status, "delivered")) {
					orderReturnFlag.put(order.getEntityId(), false);
				} else {
					boolean isReturnable = parseFloat(productDTO.getQty()) > parseFloat(productDTO.getQtyCanceled()) + parseFloat(productDTO.getQtyReturned());
					orderReturnFlag.put(order.getEntityId(), isReturnable);
				}

				productDTO.setSplitOrderId(order.getEntityId());
				productDTO.setSplitOrderIncrementId(order.getIncrementId());

				setSellerInfo(productDTO, salesOrderItem);
				
				List<OrderHistory> historyList = createOrderHistoryFromStatusMessages(skuStatusMap, salesOrderItem.getSku());
				productDTO.setHistories(historyList);
				productDTO.setTrackingDetails(trackingDetails);

				resp.getProducts().add(productDTO);
			}
		}

		// Apply totals to the calculator - old format doesn't need store conversion
		if (totalShukranBurnedPointsToShowInUI[0].compareTo(BigDecimal.ZERO) > 0) {
			totals.setTotalShukranBurnedPointsToShowInUI(totalShukranBurnedPointsToShowInUI[0].toBigInteger().intValue());
			totals.setTotalShukranBurnedValueInBaseCurrencyInUI(totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].setScale(2, RoundingMode.HALF_UP));
			totals.setTotalShukranBurnedValueInCurrencyInUI(totalShukranBurnedPointsToShowInCurrencyInUI[0].setScale(2, RoundingMode.HALF_UP));
		}
		totals.setTotalProductLevelDiscountExclTax(parseNullStr(totalProductLevelDiscountExclTax[0]));
		totals.setTotalCouponDiscountExclTax(parseNullStr(totalCouponDiscountExclTax[0]));
		totals.setTotalPriceExclTax(parseNullStr(totalPriceExclTax[0].setScale(2, RoundingMode.HALF_UP)));
		totals.setTaxAmount(parseNullStr(totalTaxAmount[0]));
		totals.setTotalTaxableAmount(totaltaxablePrice[0]);
        totals.setTaxFactor(taxFactor[0]);
	}

	/**
	 * Old format method for setting invoice items
	 */
	private boolean setInvoiceItemsOldFormat(SalesOrder order, Map<String, ProductResponseBody> productsFromMulin, OrdersDetailsResponsedto resp, OrderTotal totals, Stores store) {
		// Use the same logic as the original setInvoiceItems method
		return setInvoiceItems(order, new ArrayList<>(), productsFromMulin, resp, totals, store, new ArrayList<>());
	}

	/**
	 * Old format method for setting shipment items
	 */
	private boolean setShipmentItemsOldFormat(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, Map<String, ProductResponseBody> productsFromMulin, OrdersDetailsResponsedto resp, List<OrderHistory> histories, TrackingDetails trackingDetails) {
		ShipmentItemBuilder builder = new ShipmentItemBuilder(productsFromMulin, this);
		List<OmsProduct> shipmentItems = builder.buildShipmentItems(order, splitSalesOrders, histories, trackingDetails);
		
		if (shipmentItems.isEmpty()) {
			return false;
		}
		
		resp.getProducts().addAll(shipmentItems);
		return true;
	}

	private boolean setInvoiceItems(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, Map<String, ProductResponseBody> productsFromMulin,
			OrdersDetailsResponsedto resp, OrderTotal totals, Stores store, List<OrderHistory> historyList) {

		if (Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder())) {
			List<OrderDetailsResponse> orderDetailsList = new ArrayList<>();
			for(SplitSalesOrder splitSalesOrder : splitSalesOrders) {
				OrderDetailsResponse orderDetails = processSplitOrderInvoiceItems(splitSalesOrder, productsFromMulin, resp, totals, store, historyList);
				if(orderDetails == null) continue;
                orderDetailsList.add(orderDetails);
			}
            if(orderDetailsList.isEmpty()) {
                return false;
            }
			resp.setOrders(orderDetailsList);
			return true;
		} else if (order.getSalesInvoices().isEmpty()) {
			return false;
		}

		return processRegularOrderInvoiceItems(order, productsFromMulin, resp, totals, store, historyList);
	}

	private boolean processRegularOrderInvoiceItems(SalesOrder order, Map<String, ProductResponseBody> productsFromMulin,
			OrdersDetailsResponsedto resp, OrderTotal totals, Stores store, List<OrderHistory> historyList) {
		
		SalesInvoice invoice = order.getSalesInvoices().iterator().next();
		List<SalesOrderItem> salesOrderItems = order.getSalesOrderItem().stream()
				.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

		InvoiceCalculationResult calculationResult = calculateInvoiceTotals(invoice, salesOrderItems, totals, store);
		List<OmsProduct> productList = buildProductList(invoice, salesOrderItems, productsFromMulin, calculationResult, order);
		
		Set<String> skuSet = new HashSet<>();
		List<OmsProduct> productBySkuList = productList.stream()
				.filter(e -> skuSet.add(e.getSku()))
				.collect(Collectors.toList());
		
		OrderDetailsResponse orderDetails = createOrderDetailsResponse(order, order, historyList);
		orderDetails.setInvoiceId(invoice.getEntityId());
		orderDetails.setInvoiceIncrementId(invoice.getIncrementId());
		orderDetails.setTotals(totals);
		orderDetails.setProducts(productBySkuList);
		
		resp.setOrders(Arrays.asList(orderDetails));
		return true;
	}

	private OrderDetailsResponse processSplitOrderInvoiceItems(SplitSalesOrder order, Map<String, ProductResponseBody> productsFromMulin,
	OrdersDetailsResponsedto resp, OrderTotal totals, Stores store, List<OrderHistory> historyList) {

        if (order.getSplitSalesInvoices().isEmpty()) {
            LOGGER.warn("Split order " + order.getEntityId() + " has no invoices. Skipping invoice processing for this split order.");
            return null; // Or handle as per business logic, e.g., return null and check in the caller.
        }
        SalesInvoice invoice = order.getSplitSalesInvoices().iterator().next();
		List<SplitSalesOrderItem> salesOrderItems = order.getSplitSalesOrderItems().stream()
				.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

		// Create a new OrderTotal for this split order to avoid sharing state between split orders
		OrderTotal splitOrderTotals = new OrderTotal();
		InvoiceCalculationResult calculationResult = calculateInvoiceTotalsForSplitOrder(invoice, salesOrderItems, splitOrderTotals, store);
		List<OmsProduct> productList = buildProductListForSplitOrder(invoice, salesOrderItems, productsFromMulin, calculationResult, order);

		Set<String> skuSet = new HashSet<>();
		List<OmsProduct> productBySkuList = productList.stream()
				.filter(e -> skuSet.add(e.getSku()))
				.collect(Collectors.toList());

		OrderDetailsResponse orderDetails = createOrderDetailsResponse(order, order.getSalesOrder(), historyList);
		orderDetails.setInvoiceId(invoice.getEntityId());
		orderDetails.setInvoiceIncrementId(invoice.getIncrementId());
		
		// Set invoice totals for this split order (includes shukran points and grand total calculation)
		setInvoiceTotalsForSplitOrder(order, resp, splitOrderTotals);
		orderDetails.setTotals(splitOrderTotals);
		orderDetails.setProducts(productBySkuList);

		return orderDetails;
	}


	private static TrackingDetails setTrackingDetails(SplitSalesOrder splitSalesOrder) {
		TrackingDetails orderTracking = null;
		String encryptedAWB = null;
		if (splitSalesOrder.getSalesShipmentTrack() != null && !splitSalesOrder.getSalesShipmentTrack().isEmpty()) {
			for (SalesShipmentTrack salesShipmentTrack : splitSalesOrder.getSalesShipmentTrack()) {
				if (salesShipmentTrack != null &&
						StringUtils.isNotBlank(salesShipmentTrack.getTrackNumber())) {

					orderTracking = new TrackingDetails();
					orderTracking.setCarrierCode(salesShipmentTrack.getCarrierCode());
					orderTracking.setTitle(salesShipmentTrack.getTitle());
					orderTracking.setTrackNumber(salesShipmentTrack.getTrackNumber());
					try {
						encryptedAWB = OrderEntityConverter.encryptAWB(salesShipmentTrack.getTrackNumber());
						orderTracking.setEncryptedTrackNumber(encryptedAWB);
					} catch (Exception e) {
						LOGGER.error("Error in encrypting AWB for track number: " + salesShipmentTrack.getTrackNumber(), e);
					}
					break; // Only take the first valid tracking record
				}
			}
		}
		return orderTracking;
	}
	/**
	 * Creates an OrderDetailsResponse with common properties set
	 */
	private OrderDetailsResponse createOrderDetailsResponse(Object orderObj, SalesOrder order, List<OrderHistory> historyList) {
		OrderDetailsResponse orderDetails = new OrderDetailsResponse();
		
		if (orderObj instanceof SplitSalesOrder) {
			SplitSalesOrder splitOrder = (SplitSalesOrder) orderObj;
			orderDetails.setOrderId(splitOrder.getEntityId());
			orderDetails.setOrderIncrementId(splitOrder.getIncrementId());
			orderDetails.setIsInvoiceGenerated(!splitOrder.getSplitSalesInvoices().isEmpty());
		} else {
			orderDetails.setOrderId(order.getEntityId());
			orderDetails.setOrderIncrementId(order.getIncrementId());
			orderDetails.setIsInvoiceGenerated(!order.getSalesInvoices().isEmpty());
		}
		
		orderDetails.setTrackings(createTrackingDetails(order));
		orderDetails.setHistories(historyList);
		
		return orderDetails;
	}

	/**
	 * Helper method to extract the first tracking detail from a set of SalesShipmentTrack elements.
	 * This method is used when only the first tracking record is needed.
	 * 
	 * @param salesShipmentTracks Set of SalesShipmentTrack elements
	 * @return TrackingDetails object for the first tracking record, or null if no tracking records exist
	 */
	private TrackingDetails getFirstTrackingDetail(Set<SalesShipmentTrack> salesShipmentTracks) {
		if (salesShipmentTracks == null || salesShipmentTracks.isEmpty()) {
			return null;
		}
		
		SalesShipmentTrack firstTrack = salesShipmentTracks.iterator().next();
		TrackingDetails orderTracking = new TrackingDetails();
		orderTracking.setCarrierCode(firstTrack.getCarrierCode());
		orderTracking.setTitle(firstTrack.getTitle());
		orderTracking.setTrackNumber(firstTrack.getTrackNumber());
		
		try {
			String encryptedAWB = OrderEntityConverter.encryptAWB(firstTrack.getTrackNumber());
			orderTracking.setEncryptedTrackNumber(encryptedAWB);
		} catch (Exception e) {
			LOGGER.info("Error encrypting AWB: " + e.getMessage());
		}
		
		return orderTracking;
	}

	private TrackingDetails createTrackingDetails(SalesOrder order) {
		return getFirstTrackingDetail(order.getSalesShipmentTrack());
	}

	private InvoiceCalculationResult calculateInvoiceTotals(SalesInvoice invoice, List<SalesOrderItem> salesOrderItems, 
			OrderTotal totals, Stores store) {
		
		BigDecimal totalTaxAmount = BigDecimal.ZERO;
		BigDecimal totalProductLevelDiscountExclTax = BigDecimal.ZERO;
		BigDecimal totalCouponDiscountExclTax = BigDecimal.ZERO;
		BigDecimal totalPriceExclTax = BigDecimal.ZERO;
		BigDecimal totaltaxablePrice = BigDecimal.ZERO;
		
		// Map to store pre-computed InvoiceItemCalculation results
		Map<Integer, InvoiceItemCalculation> itemCalculations = new HashMap<>();
		
		for (SalesInvoiceItem salesInvoiceItem : invoice.getSalesInvoiceItem()) {
			SalesOrderItem salesOrderItem = salesOrderItems.stream()
					.filter(e -> e.getItemId().equals(salesInvoiceItem.getOrderItemId()))
					.findFirst().orElse(null);
			
			if (salesOrderItem != null) {
				InvoiceItemCalculation itemCalc = calculateInvoiceItem(salesInvoiceItem, salesOrderItem);
				
				// Store the calculation result for reuse
				itemCalculations.put(salesInvoiceItem.getOrderItemId(), itemCalc);
				
				// Set the tax factor in totals (this was previously done as a side effect)
				totals.setTaxFactor(itemCalc.getTaxFactor());
				
				totalTaxAmount = totalTaxAmount.add(itemCalc.getTotalTaxAmountProduct());
				totalProductLevelDiscountExclTax = totalProductLevelDiscountExclTax.add(itemCalc.getDiscountProductLevelExclTax());
				totalCouponDiscountExclTax = totalCouponDiscountExclTax.add(itemCalc.getDiscountCouponExclTaxProduct());
				totalPriceExclTax = totalPriceExclTax.add(itemCalc.getUnitPriceExclTax().multiply(salesInvoiceItem.getQuantity()));
				totaltaxablePrice = totaltaxablePrice.add(itemCalc.getTaxablePriceProduct());
			}
		}
		
		setInvoiceTotals(totals, totalProductLevelDiscountExclTax, totalCouponDiscountExclTax, 
				totalPriceExclTax, totalTaxAmount, totaltaxablePrice, store);
		
		return new InvoiceCalculationResult(totalTaxAmount, totalProductLevelDiscountExclTax, 
				totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice, itemCalculations);
	}

    private InvoiceCalculationResult calculateInvoiceTotalsForSplitOrder(SalesInvoice invoice, List<SplitSalesOrderItem> salesOrderItems,
                                                            OrderTotal totals, Stores store) {

        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        BigDecimal totalProductLevelDiscountExclTax = BigDecimal.ZERO;
        BigDecimal totalCouponDiscountExclTax = BigDecimal.ZERO;
        BigDecimal totalPriceExclTax = BigDecimal.ZERO;
        BigDecimal totaltaxablePrice = BigDecimal.ZERO;

        // Map to store pre-computed InvoiceItemCalculation results
        Map<Integer, InvoiceItemCalculation> itemCalculations = new HashMap<>();

        for (SalesInvoiceItem salesInvoiceItem : invoice.getSalesInvoiceItem()) {
            SplitSalesOrderItem salesOrderItem = salesOrderItems.stream()
                    .filter(e -> e.getItemId().equals(salesInvoiceItem.getSplitOrderItemId()))
                    .findFirst().orElse(null);

            if (salesOrderItem != null) {
                InvoiceItemCalculation itemCalc = calculateInvoiceItemForSplitOrder(salesInvoiceItem, salesOrderItem);

                // Store the calculation result for reuse
                itemCalculations.put(salesInvoiceItem.getSplitOrderItemId(), itemCalc);

                // Set the tax factor in totals (this was previously done as a side effect)
                totals.setTaxFactor(itemCalc.getTaxFactor());

                totalTaxAmount = totalTaxAmount.add(itemCalc.getTotalTaxAmountProduct());
                totalProductLevelDiscountExclTax = totalProductLevelDiscountExclTax.add(itemCalc.getDiscountProductLevelExclTax());
                totalCouponDiscountExclTax = totalCouponDiscountExclTax.add(itemCalc.getDiscountCouponExclTaxProduct());
                totalPriceExclTax = totalPriceExclTax.add(itemCalc.getUnitPriceExclTax().multiply(salesInvoiceItem.getQuantity()));
                totaltaxablePrice = totaltaxablePrice.add(itemCalc.getTaxablePriceProduct());
            }
        }

        setInvoiceTotals(totals, totalProductLevelDiscountExclTax, totalCouponDiscountExclTax,
                totalPriceExclTax, totalTaxAmount, totaltaxablePrice, store);

        return new InvoiceCalculationResult(totalTaxAmount, totalProductLevelDiscountExclTax,
                totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice, itemCalculations);
    }

	private void setInvoiceTotals(OrderTotal totals, BigDecimal totalProductLevelDiscountExclTax, 
			BigDecimal totalCouponDiscountExclTax, BigDecimal totalPriceExclTax, BigDecimal totalTaxAmount, 
			BigDecimal totaltaxablePrice, Stores store) {
		
		totals.setTotalProductLevelDiscountExclTax(parseNullStr(totalProductLevelDiscountExclTax));
		totals.setTotalBaseProductLevelDiscountExclTax(parseNullStr(getBaseValueDecimal(totalProductLevelDiscountExclTax, store.getCurrencyConversionRate())));
		totals.setTotalCouponDiscountExclTax(parseNullStr(totalCouponDiscountExclTax));
		totals.setTotalBaseCouponDiscountExclTax(parseNullStr(getBaseValueDecimal(totalCouponDiscountExclTax, store.getCurrencyConversionRate())));
		totals.setTotalPriceExclTax(parseNullStr(totalPriceExclTax.setScale(2, RoundingMode.HALF_UP)));
		totals.setTotalBasePriceExclTax(parseNullStr(getBaseValueDecimal(totalPriceExclTax.setScale(2, RoundingMode.HALF_UP), store.getCurrencyConversionRate())));
		totals.setTaxAmount(parseNullStr(totalTaxAmount));
		totals.setTotalTaxableAmount(totaltaxablePrice);
	}

	private List<OmsProduct> buildProductList(SalesInvoice invoice, List<SalesOrderItem> salesOrderItems, 
			Map<String, ProductResponseBody> productsFromMulin, InvoiceCalculationResult calculationResult, SalesOrder order) {
		
		List<OmsProduct> productList = new ArrayList<>();
		Map<Integer, InvoiceItemCalculation> itemCalculations = calculationResult.getItemCalculations();
		
		for (SalesInvoiceItem salesInvoiceItem : invoice.getSalesInvoiceItem()) {
			SalesOrderItem salesOrderItem = salesOrderItems.stream()
					.filter(e -> e.getItemId().equals(salesInvoiceItem.getOrderItemId()))
					.findFirst().orElse(null);
			
			if (salesOrderItem != null) {
				InvoiceItemCalculation itemCalc = itemCalculations.get(salesInvoiceItem.getOrderItemId());
				OmsProduct productDTO = buildProductDTO(salesInvoiceItem, salesOrderItem, productsFromMulin, itemCalc, order);
				productList.add(productDTO);
			}
		}
		
		return productList;
	}

	private List<OmsProduct> buildProductListForSplitOrder(SalesInvoice invoice, List<SplitSalesOrderItem> salesOrderItems, 
			Map<String, ProductResponseBody> productsFromMulin, InvoiceCalculationResult calculationResult, SplitSalesOrder order) {
		
		List<OmsProduct> productList = new ArrayList<>();
		Map<Integer, InvoiceItemCalculation> itemCalculations = calculationResult.getItemCalculations();
		
		for (SalesInvoiceItem salesInvoiceItem : invoice.getSalesInvoiceItem()) {
			SplitSalesOrderItem salesOrderItem = salesOrderItems.stream()
					.filter(e -> e.getItemId().equals(salesInvoiceItem.getSplitOrderItemId()))
					.findFirst().orElse(null);
			
			if (salesOrderItem != null) {
				InvoiceItemCalculation itemCalc = itemCalculations.get(salesInvoiceItem.getSplitOrderItemId());
				OmsProduct productDTO = buildProductDTOForSplitOrder(salesInvoiceItem, salesOrderItem, productsFromMulin, itemCalc, order);
				productList.add(productDTO);
			}
		}
		
		return productList;
	}

	private OmsProduct buildProductDTO(SalesInvoiceItem salesInvoiceItem, SalesOrderItem salesOrderItem, 
			Map<String, ProductResponseBody> productsFromMulin, InvoiceItemCalculation itemCalc, SalesOrder order) {
		OmsProduct productDTO = buildCommonProduct(salesOrderItem, order, productsFromMulin, parseNullStr(salesInvoiceItem.getQuantity()));
		productDTO.setQty(parseNullStr(salesInvoiceItem.getQuantity()));
		
		if (salesInvoiceItem.getRowTotalInclTax() != null && salesInvoiceItem.getDiscountAmount() != null) {
			productDTO.setFinalPrice(parseNullStr(salesInvoiceItem.getRowTotalInclTax().subtract(salesInvoiceItem.getDiscountAmount())));
		} else {
			productDTO.setFinalPrice(parseNullStr(salesInvoiceItem.getRowTotalInclTax()));
		}
		if (StringUtils.isNotBlank(salesInvoiceItem.getHsnCode())) {
			productDTO.setHsnCode(parseNullStr(salesInvoiceItem.getHsnCode()));
		}
		setProductTaxes(salesInvoiceItem, productDTO);
		setProductZatcaDetails(salesInvoiceItem, salesOrderItem, productDTO, itemCalc);
		
		return productDTO;
	}

	private OmsProduct buildProductDTOForSplitOrder(SalesInvoiceItem salesInvoiceItem, SplitSalesOrderItem salesOrderItem, 
			Map<String, ProductResponseBody> productsFromMulin, InvoiceItemCalculation itemCalc, SplitSalesOrder order) {
		OmsProduct productDTO = buildCommonProductForSplitOrder(salesOrderItem, order, productsFromMulin, parseNullStr(salesInvoiceItem.getQuantity()));
		productDTO.setQty(parseNullStr(salesInvoiceItem.getQuantity()));
		
		if (salesInvoiceItem.getRowTotalInclTax() != null && salesInvoiceItem.getDiscountAmount() != null) {
			productDTO.setFinalPrice(parseNullStr(salesInvoiceItem.getRowTotalInclTax().subtract(salesInvoiceItem.getDiscountAmount())));
		} else {
			productDTO.setFinalPrice(parseNullStr(salesInvoiceItem.getRowTotalInclTax()));
		}
		if (StringUtils.isNotBlank(salesInvoiceItem.getHsnCode())) {
			productDTO.setHsnCode(parseNullStr(salesInvoiceItem.getHsnCode()));
		}
		setProductTaxes(salesInvoiceItem, productDTO);
		setProductZatcaDetailsForSplitOrder(salesInvoiceItem, salesOrderItem, productDTO, itemCalc);
		
		return productDTO;
	}

	private void setProductTaxes(SalesInvoiceItem salesInvoiceItem, OmsProduct productDTO) {
		List<OmsProductTax> omsProductTaxs = new ArrayList<>();
		for (SalesInvoiceItemTax salesInvoiceItemtax : salesInvoiceItem.getSalesInvoiceItemTax()) {
			OmsProductTax omsProductTax = new OmsProductTax();
			omsProductTax.setTaxType(salesInvoiceItemtax.getTaxType());
			omsProductTax.setTaxPercentage(salesInvoiceItemtax.getTaxPercentage().toString());
			omsProductTax.setTaxAmount(salesInvoiceItemtax.getTaxAmount().toString());
			omsProductTaxs.add(omsProductTax);
		}
		productDTO.setTaxObjects(omsProductTaxs);
	}

	private void setProductZatcaDetails(SalesInvoiceItem salesInvoiceItem, SalesOrderItem salesOrderItem, OmsProduct productDTO, InvoiceItemCalculation itemCalc) {
		BigDecimal giftVoucherAmount = getGiftVoucherAmount(salesOrderItem);
		productDTO.setGiftVoucherAmount(parseNullStr(giftVoucherAmount));
		
		productDTO.setPrice(parseNullStr(salesInvoiceItem.getPriceInclTax()));
		productDTO.setDiscount(parseNullStr(salesInvoiceItem.getDiscountAmount()));
		productDTO.setTaxAmount(parseNullStr(salesInvoiceItem.getTaxAmount()));
		productDTO.setTaxPercentage(parseNullStr(salesOrderItem.getTaxPercent()));
		
		BigDecimal taxFactor = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());
		BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);
		productDTO.setUnitPriceExclTax(parseNullStr(unitPriceExclTax));
		productDTO.setSubTotalExclTax(parseNullStr(unitPriceExclTax.multiply(salesInvoiceItem.getQuantity())));
		
		// Use pre-computed calculations instead of recalculating
		productDTO.setTotalDiscountExclTaxProduct(parseNullStr(itemCalc.getDiscountExclTaxProduct()));
		productDTO.setTaxablePriceProduct(parseNullStr(itemCalc.getTaxablePriceProduct()));
		productDTO.setTotalTaxAmountProduct(parseNullStr(itemCalc.getTotalTaxAmountProduct()));
		productDTO.setTotalPriceInclTaxProduct(parseNullStr(itemCalc.getTotalPriceInclTaxProduct()));
	}

	private void setProductZatcaDetailsForSplitOrder(SalesInvoiceItem salesInvoiceItem, SplitSalesOrderItem salesOrderItem, OmsProduct productDTO, InvoiceItemCalculation itemCalc) {
		BigDecimal giftVoucherAmount = getGiftVoucherAmountForSplitOrder(salesOrderItem);
		productDTO.setGiftVoucherAmount(parseNullStr(giftVoucherAmount));
		
		productDTO.setPrice(parseNullStr(salesInvoiceItem.getPriceInclTax()));
		productDTO.setDiscount(parseNullStr(salesInvoiceItem.getDiscountAmount()));
		productDTO.setTaxAmount(parseNullStr(salesInvoiceItem.getTaxAmount()));
		productDTO.setTaxPercentage(parseNullStr(salesOrderItem.getTaxPercent()));
		
		BigDecimal taxFactor = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());
		BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);
		productDTO.setUnitPriceExclTax(parseNullStr(unitPriceExclTax));
		productDTO.setSubTotalExclTax(parseNullStr(unitPriceExclTax.multiply(salesInvoiceItem.getQuantity())));
		
		// Use pre-computed calculations instead of recalculating
		productDTO.setTotalDiscountExclTaxProduct(parseNullStr(itemCalc.getDiscountExclTaxProduct()));
		productDTO.setTaxablePriceProduct(parseNullStr(itemCalc.getTaxablePriceProduct()));
		productDTO.setTotalTaxAmountProduct(parseNullStr(itemCalc.getTotalTaxAmountProduct()));
		productDTO.setTotalPriceInclTaxProduct(parseNullStr(itemCalc.getTotalPriceInclTaxProduct()));
	}

	private BigDecimal getGiftVoucherAmount(SalesOrderItem salesOrderItem) {
		if (salesOrderItem.getSubSalesOrderItem() != null) {
			SubSalesOrderItem subSalesOrderItem = salesOrderItem.getSubSalesOrderItem().stream()
					.filter(e -> e.isGiftVoucher())
					.findFirst().orElse(null);
			if (subSalesOrderItem != null) {
				return subSalesOrderItem.getDiscount();
			}
		}
		return BigDecimal.ZERO;
	}

	private BigDecimal getGiftVoucherAmountForSplitOrder(SplitSalesOrderItem salesOrderItem) {
		if (salesOrderItem.getSplitSubSalesOrderItem() != null) {
			SplitSubSalesOrderItem subSalesOrderItem = salesOrderItem.getSplitSubSalesOrderItem().stream()
					.filter(e -> e.isGiftVoucher())
					.findFirst().orElse(null);
			if (subSalesOrderItem != null) {
				return subSalesOrderItem.getDiscount();
			}
		}
		return BigDecimal.ZERO;
	}

	private InvoiceItemCalculation calculateInvoiceItem(SalesInvoiceItem salesInvoiceItem, SalesOrderItem salesOrderItem) {
		BigDecimal taxFactor = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());
		
		BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);
		BigDecimal giftVoucherAmount = getGiftVoucherAmount(salesOrderItem);
		
		BigDecimal discountProductLevelExclTax = calculateDiscountProductLevelExclTax(salesOrderItem, salesInvoiceItem, taxFactor);
		BigDecimal discountCouponExclTaxProduct = calculateDiscountCouponExclTaxProduct(salesInvoiceItem, giftVoucherAmount, taxFactor);
		BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);
		
		BigDecimal taxablePriceProduct = calculateTaxablePriceProduct(unitPriceExclTax, salesInvoiceItem, discountProductLevelExclTax, discountCouponExclTaxProduct);
		BigDecimal totalTaxAmountProduct = calculateTotalTaxAmountProduct(taxablePriceProduct, salesOrderItem);
		BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2, RoundingMode.HALF_UP);
		
		return new InvoiceItemCalculation(unitPriceExclTax, discountProductLevelExclTax, discountCouponExclTaxProduct, 
				discountExclTaxProduct, taxablePriceProduct, totalTaxAmountProduct, totalPriceInclTaxProduct, taxFactor);
	}

	private InvoiceItemCalculation calculateInvoiceItemForSplitOrder(SalesInvoiceItem salesInvoiceItem, SplitSalesOrderItem salesOrderItem) {
		BigDecimal taxFactor = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());
		
		BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);
		BigDecimal giftVoucherAmount = getGiftVoucherAmountForSplitOrder(salesOrderItem);
		
		BigDecimal discountProductLevelExclTax = calculateDiscountProductLevelExclTaxForSplitOrder(salesOrderItem, salesInvoiceItem, taxFactor);
		BigDecimal discountCouponExclTaxProduct = calculateDiscountCouponExclTaxProduct(salesInvoiceItem, giftVoucherAmount, taxFactor);
		BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);
		
		BigDecimal taxablePriceProduct = calculateTaxablePriceProduct(unitPriceExclTax, salesInvoiceItem, discountProductLevelExclTax, discountCouponExclTaxProduct);
		BigDecimal totalTaxAmountProduct = calculateTotalTaxAmountProductForSplitOrder(taxablePriceProduct, salesOrderItem);
		BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2, RoundingMode.HALF_UP);
		
		return new InvoiceItemCalculation(unitPriceExclTax, discountProductLevelExclTax, discountCouponExclTaxProduct, 
				discountExclTaxProduct, taxablePriceProduct, totalTaxAmountProduct, totalPriceInclTaxProduct, taxFactor);
	}

	private BigDecimal calculateDiscountProductLevelExclTaxForSplitOrder(SplitSalesOrderItem salesOrderItem, SalesInvoiceItem salesInvoiceItem, BigDecimal taxFactor) {
		BigDecimal discountProductLevelExclTax = salesOrderItem.getOriginalPrice().subtract(salesInvoiceItem.getPriceInclTax());
		discountProductLevelExclTax = discountProductLevelExclTax.multiply(salesInvoiceItem.getQuantity());
		if (discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
			discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
		}
		return discountProductLevelExclTax;
	}

	private BigDecimal calculateDiscountProductLevelExclTax(SalesOrderItem salesOrderItem, SalesInvoiceItem salesInvoiceItem, BigDecimal taxFactor) {
		BigDecimal discountProductLevelExclTax = salesOrderItem.getOriginalPrice().subtract(salesInvoiceItem.getPriceInclTax());
		discountProductLevelExclTax = discountProductLevelExclTax.multiply(salesInvoiceItem.getQuantity());
		if (discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
			discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
		}
		return discountProductLevelExclTax;
	}

	private BigDecimal calculateDiscountCouponExclTaxProduct(SalesInvoiceItem salesInvoiceItem, BigDecimal giftVoucherAmount, BigDecimal taxFactor) {
		BigDecimal discountAmount = salesInvoiceItem.getDiscountAmount();
		BigDecimal discountCouponExclTaxProduct = (discountAmount != null ? discountAmount : BigDecimal.ZERO)
				.subtract(giftVoucherAmount != null ? giftVoucherAmount : BigDecimal.ZERO);
		return discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);
	}

	private BigDecimal calculateTaxablePriceProduct(BigDecimal unitPriceExclTax, SalesInvoiceItem salesInvoiceItem, 
			BigDecimal discountProductLevelExclTax, BigDecimal discountCouponExclTaxProduct) {
		BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(salesInvoiceItem.getQuantity());
		taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
				.subtract(discountCouponExclTaxProduct)
				.setScale(2, RoundingMode.HALF_UP);
		if (taxablePriceProduct.compareTo(BigDecimal.ZERO) < 0) {
			taxablePriceProduct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return taxablePriceProduct;
	}

	private BigDecimal calculateTotalTaxAmountProduct(BigDecimal taxablePriceProduct, SalesOrderItem salesOrderItem) {
		BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
		return totalTaxAmountProduct.multiply(salesOrderItem.getTaxPercent()).setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal calculateTotalTaxAmountProductForSplitOrder(BigDecimal taxablePriceProduct, SplitSalesOrderItem salesOrderItem) {
		BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
		return totalTaxAmountProduct.multiply(salesOrderItem.getTaxPercent()).setScale(2, RoundingMode.HALF_UP);
	}


	private static class InvoiceCalculationResult {
		private final Map<Integer, InvoiceItemCalculation> itemCalculations;

		public InvoiceCalculationResult(BigDecimal totalTaxAmount, BigDecimal totalProductLevelDiscountExclTax,
				BigDecimal totalCouponDiscountExclTax, BigDecimal totalPriceExclTax, BigDecimal totaltaxablePrice,
				Map<Integer, InvoiceItemCalculation> itemCalculations) {
			this.itemCalculations = itemCalculations;
		}
		
		// Getter methods
		public Map<Integer, InvoiceItemCalculation> getItemCalculations() { return itemCalculations; }
	}

		private static class InvoiceItemCalculation {
		private final BigDecimal unitPriceExclTax;
		private final BigDecimal discountProductLevelExclTax;
		private final BigDecimal discountCouponExclTaxProduct;
		private final BigDecimal discountExclTaxProduct;
		private final BigDecimal taxablePriceProduct;
		private final BigDecimal totalTaxAmountProduct;
		private final BigDecimal totalPriceInclTaxProduct;
		private final BigDecimal taxFactor;

		public InvoiceItemCalculation(BigDecimal unitPriceExclTax, BigDecimal discountProductLevelExclTax,
				BigDecimal discountCouponExclTaxProduct, BigDecimal discountExclTaxProduct, BigDecimal taxablePriceProduct,
				BigDecimal totalTaxAmountProduct, BigDecimal totalPriceInclTaxProduct, BigDecimal taxFactor) {
			this.unitPriceExclTax = unitPriceExclTax;
			this.discountProductLevelExclTax = discountProductLevelExclTax;
			this.discountCouponExclTaxProduct = discountCouponExclTaxProduct;
			this.discountExclTaxProduct = discountExclTaxProduct;
			this.taxablePriceProduct = taxablePriceProduct;
			this.totalTaxAmountProduct = totalTaxAmountProduct;
			this.totalPriceInclTaxProduct = totalPriceInclTaxProduct;
			this.taxFactor = taxFactor;
		}
		
		public BigDecimal getUnitPriceExclTax() { return unitPriceExclTax; }
		public BigDecimal getDiscountProductLevelExclTax() { return discountProductLevelExclTax; }
		public BigDecimal getDiscountCouponExclTaxProduct() { return discountCouponExclTaxProduct; }
		public BigDecimal getDiscountExclTaxProduct() { return discountExclTaxProduct; }
		public BigDecimal getTaxablePriceProduct() { return taxablePriceProduct; }
		public BigDecimal getTotalTaxAmountProduct() { return totalTaxAmountProduct; }
		public BigDecimal getTotalPriceInclTaxProduct() { return totalPriceInclTaxProduct; }
		public BigDecimal getTaxFactor() { return taxFactor; }
	}

	/**
	 * Creates an OrderDetailsResponse with basic properties set
	 */
	private OrderDetailsResponse createBasicOrderDetailsResponse(Integer orderId, String incrementId, SalesOrder order) {
		OrderDetailsResponse orderDetails = new OrderDetailsResponse();
		orderDetails.setOrderId(orderId);
		orderDetails.setOrderIncrementId(incrementId);
		orderDetails.setTrackings(createTrackingDetails(order));
		orderDetails.setIsInvoiceGenerated(!order.getSalesInvoices().isEmpty());
		return orderDetails;
	}

	/**
	 * Handles the creation of a regular order (non-split)
	 */
	private OrderDetailsResponse createRegularOrderDetails(SalesOrder order, Map<Integer, Boolean> orderReturnFlag, 
			Map<String, ProductResponseBody> productsFromMulin, Stores store, List<OrderHistory> histories,
			BigDecimal[] totalShukranBurnedPointsToShowInUI, BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI, 
			BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI, BigDecimal[] totalTaxAmount, 
			BigDecimal[] totalProductLevelDiscountExclTax, BigDecimal[] totalCouponDiscountExclTax, 
			BigDecimal[] totalPriceExclTax, BigDecimal[] totaltaxablePrice) {
		
		OrderDetailsResponse orderDetails = createBasicOrderDetailsResponse(order.getEntityId(), order.getIncrementId(), order);
		
		// Set totals for this order
		OrderTotal orderTotals = new OrderTotal();
		setMainOrderTotals(order, orderTotals, store);
		orderDetails.setTotals(orderTotals);
		
		// Set products for this order
		List<OmsProduct> orderProducts = new ArrayList<>();
		setProductForOrderWithProducts(order, orderReturnFlag, productsFromMulin, orderProducts, orderTotals, 
				totalShukranBurnedPointsToShowInUI, totalShukranBurnedPointsToShowInBaseCurrencyInUI, 
				totalShukranBurnedPointsToShowInCurrencyInUI, totalTaxAmount, totalProductLevelDiscountExclTax, 
				totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice);
		orderDetails.setProducts(orderProducts);
		
		return orderDetails;
	}

	/**
	 * Handles the creation of split order details
	 */
	private List<OrderDetailsResponse> createSplitOrderDetails(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, 
			Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin, 
			Stores store, List<OrderHistory> histories, BigDecimal[] totalShukranBurnedPointsToShowInUI, 
			BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI, BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI, 
			BigDecimal[] totalTaxAmount, BigDecimal[] totalProductLevelDiscountExclTax, BigDecimal[] totalCouponDiscountExclTax, 
			BigDecimal[] totalPriceExclTax, BigDecimal[] totaltaxablePrice) {
		
		List<OrderDetailsResponse> ordersList = new ArrayList<>();
		
		// Group split orders by entity_id
		Map<Integer, List<SplitSalesOrder>> splitOrdersByEntityId = splitSalesOrders.stream()
			.collect(Collectors.groupingBy(SplitSalesOrder::getEntityId));
		
		for (Map.Entry<Integer, List<SplitSalesOrder>> entry : splitOrdersByEntityId.entrySet()) {
			Integer entityId = entry.getKey();
			List<SplitSalesOrder> splitOrdersForEntity = entry.getValue();
			
			// Use the first split order for order details (they should have same entity_id)
			SplitSalesOrder firstSplitOrder = splitOrdersForEntity.get(0);
			
			LOGGER.info("createSplitOrderDetails: Processing split order entityId: " + entityId + ", incrementId: " + firstSplitOrder.getIncrementId());
			
			OrderDetailsResponse orderDetails = createBasicOrderDetailsResponse(entityId, firstSplitOrder.getIncrementId(), order);
			
			// Set split_order_id (entity_id from split_sales_order)
			orderDetails.setSplitOrderId(entityId);
			LOGGER.info("createSplitOrderDetails: Set splitOrderId: " + entityId);
			
			// Fetch and set estimated_delivery_date for this split order
			String estimatedDeliveryDate = fetchEstimatedDeliveryDateForShipment(entityId);
			if (StringUtils.isNotBlank(estimatedDeliveryDate)) {
				orderDetails.setEstimatedDeliveryDate(estimatedDeliveryDate);
				LOGGER.info("createSplitOrderDetails: Set estimatedDeliveryDate: " + estimatedDeliveryDate + " for splitOrderId: " + entityId);
			} else {
				LOGGER.info("createSplitOrderDetails: No estimatedDeliveryDate found for splitOrderId: " + entityId);
			}
			
			orderDetails.setIsInvoiceGenerated(!firstSplitOrder.getSplitSalesInvoices().isEmpty());
			
			// Set totals for this split order group
			OrderTotal splitTotals = new OrderTotal();
			setSplitOrderTotals(firstSplitOrder, splitTotals, store);
			orderDetails.setTotals(splitTotals);
			
			// Set products for this split order group - filter by shipment_type = 'simple'
			List<OmsProduct> splitProducts = new ArrayList<>();
			setProductForSplitOrderWithProductsFiltered(order.getEntityId(), splitOrdersForEntity, orderReturnFlag, 
					productsFromMulin, splitProducts, splitTotals, histories, totalShukranBurnedPointsToShowInUI, 
					totalShukranBurnedPointsToShowInBaseCurrencyInUI, totalShukranBurnedPointsToShowInCurrencyInUI, 
					totalTaxAmount, totalProductLevelDiscountExclTax, totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice);
			orderDetails.setProducts(splitProducts);
			
			ordersList.add(orderDetails);
		}
		
		return ordersList;
	}

	/**
	 * Determines if an order should be treated as a split order and fetches split orders if needed
	 */
	private List<SplitSalesOrder> getEffectiveSplitOrders(SalesOrder order, List<SplitSalesOrder> splitSalesOrders) {
		if (!splitSalesOrders.isEmpty()) {
			return splitSalesOrders;
		}
		
		// Check if this is actually a split order but splitSalesOrders list is empty
		if (Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder())){
			return splitSalesOrderRepository.findByOrderId(order.getEntityId());
		}
		
		return new ArrayList<>();
	}

	private void setOrderItems(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin,
			OrdersDetailsResponsedto resp, OrderTotal totals, Stores store, List<OrderHistory> histories) {
		final BigDecimal[] totalTaxAmount = {BigDecimal.ZERO};
		final BigDecimal[] totalProductLevelDiscountExclTax = {BigDecimal.ZERO};
		final BigDecimal[] totalCouponDiscountExclTax = {BigDecimal.ZERO};
		final BigDecimal[] totalPriceExclTax = {BigDecimal.ZERO};
		final BigDecimal[] totaltaxablePrice = {BigDecimal.ZERO};
		final BigDecimal[] totalShukranBurnedPointsToShowInUI = {BigDecimal.ZERO};
		final BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI = {BigDecimal.ZERO};
		final BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI = {BigDecimal.ZERO};
		
		List<OrderDetailsResponse> ordersList = new ArrayList<>();
		
		// Get effective split orders (either provided or fetched)
		List<SplitSalesOrder> effectiveSplitOrders = getEffectiveSplitOrders(order, splitSalesOrders);
		
		if (!effectiveSplitOrders.isEmpty()) {
			// Handle split orders
			ordersList = createSplitOrderDetails(order, effectiveSplitOrders, orderReturnFlag, productsFromMulin, 
					store, histories, totalShukranBurnedPointsToShowInUI, totalShukranBurnedPointsToShowInBaseCurrencyInUI, 
					totalShukranBurnedPointsToShowInCurrencyInUI, totalTaxAmount, totalProductLevelDiscountExclTax, 
					totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice);
		} else {
			// Handle regular orders - create a single order entry
			OrderDetailsResponse orderDetails = createRegularOrderDetails(order, orderReturnFlag, productsFromMulin, 
					store, histories, totalShukranBurnedPointsToShowInUI, totalShukranBurnedPointsToShowInBaseCurrencyInUI, 
					totalShukranBurnedPointsToShowInCurrencyInUI, totalTaxAmount, totalProductLevelDiscountExclTax, 
					totalCouponDiscountExclTax, totalPriceExclTax, totaltaxablePrice);
			ordersList.add(orderDetails);
		}
		
		// Set gift product flag based on all orders
		boolean hasGiftProduct = ordersList.stream()
			.anyMatch(orderDetails -> orderDetails.getProducts().stream().anyMatch(product -> product.isGiftProduct()));
		resp.setHasGiftProduct(hasGiftProduct);
		
		resp.setOrders(ordersList);
		
		// Set main response totals
		setMainOrderTotals(order, totals, store);
		
		if(totalShukranBurnedPointsToShowInUI[0].compareTo(BigDecimal.ZERO)>0){
			totals.setTotalShukranBurnedPointsToShowInUI(totalShukranBurnedPointsToShowInUI[0].toBigInteger().intValue());
			totals.setTotalShukranBurnedValueInBaseCurrencyInUI(totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].setScale(2, RoundingMode.HALF_UP));
			totals.setTotalShukranBurnedValueInCurrencyInUI(totalShukranBurnedPointsToShowInCurrencyInUI[0].setScale(2, RoundingMode.HALF_UP));
		}
		totals.setTotalProductLevelDiscountExclTax(parseNullStr(totalProductLevelDiscountExclTax[0]));
		totals.setTotalBaseProductLevelDiscountExclTax(parseNullStr(getBaseValueDecimal(totalProductLevelDiscountExclTax[0], store.getCurrencyConversionRate())));
		totals.setTotalCouponDiscountExclTax(parseNullStr(totalCouponDiscountExclTax[0]));
		totals.setTotalBaseCouponDiscountExclTax(parseNullStr(getBaseValueDecimal(totalCouponDiscountExclTax[0], store.getCurrencyConversionRate())));
		totals.setTotalPriceExclTax(parseNullStr(totalPriceExclTax[0].setScale(2, RoundingMode.HALF_UP)));
		totals.setTotalBasePriceExclTax(parseNullStr(getBaseValueDecimal(totalPriceExclTax[0].setScale(2, RoundingMode.HALF_UP), store.getCurrencyConversionRate())));
		totals.setTaxAmount(parseNullStr(totalTaxAmount[0]));
		totals.setTotalTaxableAmount(totaltaxablePrice[0]);
	}

	private void setProductForSplitOrderWithProductsFiltered(Integer orderId, List<SplitSalesOrder> splitSalesOrders, Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin, List<OmsProduct> splitProducts, OrderTotal totals, List<OrderHistory> histories, BigDecimal[] totalShukranBurnedPointsToShowInUI, BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI, BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI, BigDecimal[] totalTaxAmount, BigDecimal[] totalProductLevelDiscountExclTax, BigDecimal[] totalCouponDiscountExclTax, BigDecimal[] totalPriceExclTax, BigDecimal[] totaltaxablePrice) {
		// Collect all split sales order items from all split orders, filtered by shipment_type = 'simple'
		List<SplitSalesOrderItem> allSplitItems = new ArrayList<>();
		for (SplitSalesOrder splitSalesOrder : splitSalesOrders) {
			if (splitSalesOrder.getSplitSalesOrderItems() != null) {
				allSplitItems.addAll(splitSalesOrder.getSplitSalesOrderItems().stream()
					.filter(item -> "simple".equalsIgnoreCase(item.getProductType()))
					.collect(Collectors.toList()));
			}
		}
		
		// Process each split sales order item using the existing pattern
		for (SplitSalesOrderItem splitSalesOrderItem : allSplitItems) {
			OmsProduct productDTO = new OmsProduct();
			
			// Use the existing pattern from the original method
			productDTO.setSplitOrderIncrementId(splitSalesOrderItem.getSplitSalesOrder().getIncrementId());
			productDTO.setSplitOrderId(orderId);
			productDTO.setSku(splitSalesOrderItem.getSku());
			productDTO.setName(splitSalesOrderItem.getName());
			productDTO.setParentOrderItemId(splitSalesOrderItem.getItemId());
			
			if (splitSalesOrderItem.getQtyOrdered() != null) {
				productDTO.setQty(splitSalesOrderItem.getQtyOrdered().toString());
			}
			if (splitSalesOrderItem.getQtyCanceled() != null) {
				productDTO.setQtyCanceled(splitSalesOrderItem.getQtyCanceled().toString());
			}
			if (splitSalesOrderItem.getQtyShipped() != null) {
				productDTO.setQtyShipped(splitSalesOrderItem.getQtyShipped().toString());
			}
			
			// Update Shukran points
			if(splitSalesOrderItem.getShukranCoinsBurned() != null && splitSalesOrderItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
				totalShukranBurnedPointsToShowInUI[0] = totalShukranBurnedPointsToShowInUI[0].add(splitSalesOrderItem.getShukranCoinsBurned());
				totalShukranBurnedPointsToShowInBaseCurrencyInUI[0] = totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].add(splitSalesOrderItem.getShukranCoinsBurnedValueInBaseCurrency());
				totalShukranBurnedPointsToShowInCurrencyInUI[0] = totalShukranBurnedPointsToShowInCurrencyInUI[0].add(splitSalesOrderItem.getShukranCoinsBurnedValueInCurrency());
			}
			
			// Set product details from Mulin
			setDetailsFromMulin(productsFromMulin, splitSalesOrderItem.getSku(), productDTO);
			
			if(ObjectUtils.isNotEmpty(splitSalesOrderItem.getReturnable())) {
				productDTO.setReturnCategoryRestriction(splitSalesOrderItem.getReturnable() == 1 ? "false": "true");
			}
			
			productDTO.setParentProductId(splitSalesOrderItem.getSku());
			productDTO.setTaxAmount(parseNullStr(splitSalesOrderItem.getTaxAmount()));
			productDTO.setBaseTaxAmount(parseNullStr(splitSalesOrderItem.getBaseTaxAmount()));
			productDTO.setRowTotal(parseNullStr(splitSalesOrderItem.getRowTotal()));
			productDTO.setBaseRowTotal(parseNullStr(splitSalesOrderItem.getBaseRowTotal()));
			productDTO.setRowTotalInclTax(parseNullStr(splitSalesOrderItem.getRowTotalInclTax()));
			productDTO.setBaseRowTotalInclTax(parseNullStr(splitSalesOrderItem.getBaseRowTotalInclTax()));
			productDTO.setQtyInvoiced(parseNullStr(splitSalesOrderItem.getQtyInvoiced()));
			productDTO.setSubtotal(parseNullStr(splitSalesOrderItem.getRowTotal()));
			productDTO.setBaseSubtotal(parseNullStr(splitSalesOrderItem.getBaseRowTotal()));
			productDTO.setTaxPercentage(parseNullStr(splitSalesOrderItem.getTaxPercent()));
			productDTO.setActualPrice(parseNullStr(splitSalesOrderItem.getWeeeTaxAppliedAmount()));
			
			if (null != splitSalesOrderItem.getGiftMessageAvailable()
					&& splitSalesOrderItem.getGiftMessageAvailable().equals(1)) {
				productDTO.setGiftProduct(true);
			}
			
			BigDecimal giftVoucherAmount = BigDecimal.ZERO;
			if(null != splitSalesOrderItem.getSplitSubSalesOrderItem()) {
				SplitSubSalesOrderItem splitSubSalesOrderItem = splitSalesOrderItem.getSplitSubSalesOrderItem().stream().filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
				if(null != splitSubSalesOrderItem) {
					giftVoucherAmount = splitSubSalesOrderItem.getDiscount();
				}
			}
			productDTO.setGiftVoucherAmount(parseNullStr(giftVoucherAmount));
			
			// ZATCA changes
			productDTO.setPrice(parseNullStr(splitSalesOrderItem.getPriceInclTax()));
			productDTO.setDiscount(parseNullStr(splitSalesOrderItem.getDiscountAmount()));
			productDTO.setOriginalPrice(parseNullStr(splitSalesOrderItem.getOriginalPrice()));
			
			productDTO.setHistories(histories);
			
			BigDecimal taxFactor = orderHelper.getExclTaxfactor(splitSalesOrderItem.getTaxPercent());

			totals.setTaxFactor(taxFactor);
			BigDecimal unitPriceExclTax = splitSalesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);
			
			BigDecimal discountProductLevelExclTax = splitSalesOrderItem.getOriginalPrice().subtract(splitSalesOrderItem.getPriceInclTax());
			discountProductLevelExclTax = discountProductLevelExclTax.multiply(splitSalesOrderItem.getQtyOrdered());
			if(discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
				discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
			}
			
			BigDecimal discountCouponExclTaxProduct = splitSalesOrderItem.getDiscountAmount().subtract(giftVoucherAmount);
			discountCouponExclTaxProduct = discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);
			
			BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);
			
			BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered());
			taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
					.subtract(discountCouponExclTaxProduct)
					.setScale(2, RoundingMode.HALF_UP);
			if(taxablePriceProduct.compareTo(BigDecimal.ZERO) < 0) {
				taxablePriceProduct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
			}
			
			if(StringUtils.isNotBlank(splitSalesOrderItem.getItemBrandName()) && StringUtils.isNotEmpty(splitSalesOrderItem.getItemBrandName())) {
				productDTO.setBrandName(splitSalesOrderItem.getItemBrandName());
			}
			
			BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
			totalTaxAmountProduct = totalTaxAmountProduct.multiply(splitSalesOrderItem.getTaxPercent()).setScale(2, RoundingMode.HALF_UP);
			
			BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2, RoundingMode.HALF_UP);
			
			productDTO.setUnitPriceExclTax(parseNullStr(unitPriceExclTax));
			productDTO.setSubTotalExclTax(parseNullStr(unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered())));
			productDTO.setTaxablePriceProduct(parseNullStr(taxablePriceProduct));
			productDTO.setTotalDiscountExclTaxProduct(parseNullStr(discountExclTaxProduct));
			productDTO.setTotalTaxAmountProduct(parseNullStr(totalTaxAmountProduct));
			productDTO.setTotalPriceInclTaxProduct(parseNullStr(totalPriceInclTaxProduct));
			
			totalTaxAmount[0] = totalTaxAmount[0].add(totalTaxAmountProduct);
			totalProductLevelDiscountExclTax[0] = totalProductLevelDiscountExclTax[0].add(discountProductLevelExclTax);
			totalCouponDiscountExclTax[0] = totalCouponDiscountExclTax[0].add(discountCouponExclTaxProduct);
			totalPriceExclTax[0] = totalPriceExclTax[0].add(unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered()));
			
			totaltaxablePrice[0] = totaltaxablePrice[0].add(taxablePriceProduct);
			
			BigDecimal finalPrice = splitSalesOrderItem.getRowTotalInclTax()
					.subtract(splitSalesOrderItem.getDiscountAmount());
			BigDecimal baseFinalPrice = splitSalesOrderItem.getBaseRowTotalInclTax()
					.subtract(splitSalesOrderItem.getBaseDiscountAmount());
			productDTO.setFinalPrice(parseNullStr(finalPrice));
			productDTO.setBaseFinalPrice(parseNullStr(baseFinalPrice));
			productDTO.setTaxObjects(getTaxObjects(splitSalesOrderItem));
			
			productDTO.setHsnCode(parseNullStr(splitSalesOrderItem.getHsnCode()));
			
			String status = splitSalesOrderItem.getSplitSalesOrder().getStatus().toLowerCase();
			if(!Objects.equals(status, "delivered")) orderReturnFlag.put(splitSalesOrderItem.getSplitSalesOrder().getEntityId(), false);
			else {
				boolean isReturnable = parseFloat(productDTO.getQty()) > parseFloat(productDTO.getQtyCanceled()) + parseFloat(productDTO.getQtyReturned());
				orderReturnFlag.put(splitSalesOrderItem.getSplitSalesOrder().getEntityId(), isReturnable);
			}
			
			splitProducts.add(productDTO);
		}
	}

	private void setProductForOrderWithProducts(SalesOrder order, Map<Integer, Boolean> orderReturnFlag, Map<String, ProductResponseBody> productsFromMulin, List<OmsProduct> orderProducts, OrderTotal totals, BigDecimal[] totalShukranBurnedPointsToShowInUI, BigDecimal[] totalShukranBurnedPointsToShowInBaseCurrencyInUI, BigDecimal[] totalShukranBurnedPointsToShowInCurrencyInUI, BigDecimal[] totalTaxAmount, BigDecimal[] totalProductLevelDiscountExclTax, BigDecimal[] totalCouponDiscountExclTax, BigDecimal[] totalPriceExclTax, BigDecimal[] totaltaxablePrice) {
		for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {
			if (salesOrderItem.getParentOrderItem() == null) {

				OmsProduct productDTO = new OmsProduct();
				productDTO.setSplitOrderId(order.getEntityId());
				productDTO.setSplitOrderIncrementId(order.getIncrementId());
				productDTO.setSku(salesOrderItem.getSku());
				productDTO.setName(salesOrderItem.getName());
				productDTO.setParentOrderItemId(salesOrderItem.getItemId());
				if (salesOrderItem.getQtyOrdered() != null) {
					productDTO.setQty(salesOrderItem.getQtyOrdered().toString());
				}
				if (salesOrderItem.getQtyCanceled() != null) {
					productDTO.setQtyCanceled(salesOrderItem.getQtyCanceled().toString());
				}

				if (salesOrderItem.getQtyShipped() != null) {
					productDTO.setQtyShipped(salesOrderItem.getQtyShipped().toString());
				}

				if(salesOrderItem.getShukranCoinsBurned() != null && salesOrderItem.getShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
					totalShukranBurnedPointsToShowInUI[0] = totalShukranBurnedPointsToShowInUI[0].add(salesOrderItem.getShukranCoinsBurned());
					totalShukranBurnedPointsToShowInBaseCurrencyInUI[0] = totalShukranBurnedPointsToShowInBaseCurrencyInUI[0].add(salesOrderItem.getShukranCoinsBurnedValueInBaseCurrency());
					totalShukranBurnedPointsToShowInCurrencyInUI[0] = totalShukranBurnedPointsToShowInCurrencyInUI[0].add(salesOrderItem.getShukranCoinsBurnedValueInCurrency());
				}


				order.getSalesOrderItem().stream()
						.filter(e -> e.getParentOrderItem() != null && e.getParentOrderItem().getItemId() != null
								&& e.getParentOrderItem().getItemId().equals(salesOrderItem.getItemId()))
						.findFirst().ifPresent(childOrderItem -> {
							QuantityReturned quantityReturned = getSalesQtyReturned(childOrderItem.getItemId(),false);
							productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
							productDTO.setQtyReturnedInProcess(parseNullStr(childOrderItem.getQtyRefunded()));
						});



				setDetailsFromMulin(productsFromMulin, salesOrderItem.getSku(), productDTO);

				if(ObjectUtils.isNotEmpty(salesOrderItem.getReturnable())) {
					productDTO.setReturnCategoryRestriction(salesOrderItem.getReturnable() == 1 ? "false": "true");
				}

				productDTO.setParentProductId(salesOrderItem.getSku());
				productDTO.setTaxAmount(parseNullStr(salesOrderItem.getTaxAmount()));
				productDTO.setBaseTaxAmount(parseNullStr(salesOrderItem.getBaseTaxAmount()));
				productDTO.setRowTotal(parseNullStr(salesOrderItem.getRowTotal()));
				productDTO.setBaseRowTotal(parseNullStr(salesOrderItem.getBaseRowTotal()));
				productDTO.setRowTotalInclTax(parseNullStr(salesOrderItem.getRowTotalInclTax()));
				productDTO.setBaseRowTotalInclTax(parseNullStr(salesOrderItem.getBaseRowTotalInclTax()));
				productDTO.setQtyInvoiced(parseNullStr(salesOrderItem.getQtyInvoiced()));
				productDTO.setSubtotal(parseNullStr(salesOrderItem.getRowTotal()));
				productDTO.setBaseSubtotal(parseNullStr(salesOrderItem.getBaseRowTotal()));
				productDTO.setTaxPercentage(parseNullStr(salesOrderItem.getTaxPercent()));
				productDTO.setActualPrice(parseNullStr(salesOrderItem.getActualPrice()));
				if (null != salesOrderItem.getGiftMessageAvailable()
						&& salesOrderItem.getGiftMessageAvailable().equals(1)) {
					productDTO.setGiftProduct(true);
				}
				BigDecimal giftVoucherAmount = BigDecimal.ZERO;
				if(null != salesOrderItem.getSubSalesOrderItem()) {
					SubSalesOrderItem subSalesOrderItem = salesOrderItem.getSubSalesOrderItem().stream().filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
					if(null != subSalesOrderItem) {
						giftVoucherAmount = subSalesOrderItem.getDiscount();
					}
				}
				productDTO.setGiftVoucherAmount(parseNullStr(giftVoucherAmount));

				// ZATCA changes
				productDTO.setPrice(parseNullStr(salesOrderItem.getPriceInclTax()));
				productDTO.setDiscount(parseNullStr(salesOrderItem.getDiscountAmount()));
				productDTO.setOriginalPrice(parseNullStr(salesOrderItem.getOriginalPrice()));

				BigDecimal taxFactor = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());
				totals.setTaxFactor(taxFactor);
				BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);

				BigDecimal discountProductLevelExclTax = salesOrderItem.getOriginalPrice().subtract(salesOrderItem.getPriceInclTax());
				discountProductLevelExclTax = discountProductLevelExclTax.multiply(salesOrderItem.getQtyOrdered());
				if(discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
					discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
				}

				BigDecimal discountCouponExclTaxProduct = salesOrderItem.getDiscountAmount().subtract(giftVoucherAmount);
				discountCouponExclTaxProduct = discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);

				BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);

				BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered());
				taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
						.subtract(discountCouponExclTaxProduct)
						.setScale(2, RoundingMode.HALF_UP);
				if(taxablePriceProduct.compareTo(BigDecimal.ZERO) < 0) {
					taxablePriceProduct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
				}

				if(StringUtils.isNotBlank(salesOrderItem.getItemBrandName()) && StringUtils.isNotEmpty(salesOrderItem.getItemBrandName())) {
					productDTO.setBrandName(salesOrderItem.getItemBrandName());
				}

				BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
				totalTaxAmountProduct = totalTaxAmountProduct.multiply(salesOrderItem.getTaxPercent()).setScale(2, RoundingMode.HALF_UP);

				BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2, RoundingMode.HALF_UP);

				productDTO.setUnitPriceExclTax(parseNullStr(unitPriceExclTax));
				productDTO.setSubTotalExclTax(parseNullStr(unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered())));
				productDTO.setTaxablePriceProduct(parseNullStr(taxablePriceProduct));
				productDTO.setTotalDiscountExclTaxProduct(parseNullStr(discountExclTaxProduct));
				productDTO.setTotalTaxAmountProduct(parseNullStr(totalTaxAmountProduct));
				productDTO.setTotalPriceInclTaxProduct(parseNullStr(totalPriceInclTaxProduct));

				totalTaxAmount[0] = totalTaxAmount[0].add(totalTaxAmountProduct);
				totalProductLevelDiscountExclTax[0] = totalProductLevelDiscountExclTax[0].add(discountProductLevelExclTax);
				totalCouponDiscountExclTax[0] = totalCouponDiscountExclTax[0].add(discountCouponExclTaxProduct);
				totalPriceExclTax[0] = totalPriceExclTax[0].add(unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered()));
				totaltaxablePrice[0] = totaltaxablePrice[0].add(taxablePriceProduct);

				BigDecimal finalPrice = salesOrderItem.getRowTotalInclTax()
						.subtract(salesOrderItem.getDiscountAmount());
				BigDecimal baseFinalPrice = salesOrderItem.getBaseRowTotalInclTax()
						.subtract(salesOrderItem.getBaseDiscountAmount());
				productDTO.setFinalPrice(parseNullStr(finalPrice));
				productDTO.setBaseFinalPrice(parseNullStr(baseFinalPrice));
			productDTO.setTaxObjects(getTaxObjects(salesOrderItem));

			productDTO.setHsnCode(parseNullStr(salesOrderItem.getHsnCode()));
			
			// Set option_id: for configurable products, product_id contains the option_id
			if (OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(salesOrderItem.getProductType())) {
				productDTO.setOptionId(salesOrderItem.getProductId());
			}

			orderProducts.add(productDTO);

				String status = order.getStatus().toLowerCase();
				if(!Objects.equals(status, "delivered")) orderReturnFlag.put(order.getEntityId(), false);
				else {
					boolean isReturnable = parseFloat(productDTO.getQty()) > parseFloat(productDTO.getQtyCanceled()) + parseFloat(productDTO.getQtyReturned());
					orderReturnFlag.put(order.getEntityId(), isReturnable);
				}
			}
		}
	}


	public QuantityReturned getQtyReturned(Integer itemId , boolean isReturnView , boolean isReturnItemId) {
		QuantityReturned quantityReturned = new QuantityReturned();
		BigDecimal qtyReturned = BigDecimal.ZERO;
		BigDecimal qtyReturnedInProcess = BigDecimal.ZERO;
		BigDecimal qcFailedQty = BigDecimal.ZERO;
		Integer qtyMissing = 0;
		List<AmastyRmaRequestItem> amastyRmaRequestItems  = null;
		if(isReturnItemId) {
			amastyRmaRequestItems = amastyRmaRequestItemRepository.findByRequestItemId(itemId);
		}else {
			amastyRmaRequestItems = amastyRmaRequestItemRepository.findByOrderItemId(itemId);
		}
		
		if (CollectionUtils.isNotEmpty(amastyRmaRequestItems)) {
			for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequestItems) {

				boolean noShortPickup = true;
				if (amastyRmaRequestItem.getItemStatus() == 12 || amastyRmaRequestItem.getItemStatus() == 13)
					continue;

				BigDecimal currentReturnedQty = BigDecimal.ZERO;
				if (amastyRmaRequestItem.getItemStatus() == 25) {
					if (ObjectUtils.isNotEmpty(amastyRmaRequestItem.getActualQuantyReturned()))
						currentReturnedQty = new BigDecimal(amastyRmaRequestItem.getActualQuantyReturned());
					noShortPickup = false;
				} else {
					currentReturnedQty = amastyRmaRequestItem.getQty();
				}
				if(null != amastyRmaRequestItem.getQcFailedQty()) {
					
					qcFailedQty = qcFailedQty.add(new BigDecimal(amastyRmaRequestItem.getQcFailedQty().toString()));	
							
				}
				if(!isReturnView) {
					
					qtyReturned = qtyReturned.add(currentReturnedQty);
					
					if (qcFailedQty.intValue() > 0) {
						
						qtyReturned = qcFailedQty;
					}
					
					if (amastyRmaRequestItem.getItemStatus() != 4) {
						qtyReturnedInProcess = qtyReturnedInProcess.add(currentReturnedQty).add(qcFailedQty);

					}
				} else if(amastyRmaRequestItem.getItemStatus() == 26) { //Partial Return
					qtyReturned = Objects.nonNull(amastyRmaRequestItem.getActualQuantyReturned())
							? BigDecimal.valueOf(amastyRmaRequestItem.getActualQuantyReturned())
							: BigDecimal.ZERO;
					qtyMissing = amastyRmaRequestItem.getRequestQty().intValue() - qtyReturned.intValue();	
				} else {
					
					if(amastyRmaRequestItem.getItemStatus() != 4) {
                		
     					qtyReturned = qtyReturned.add(currentReturnedQty);

                	}else if (noShortPickup && qcFailedQty.intValue() > 0 && amastyRmaRequestItem.getItemStatus() != 4) {
						qtyReturnedInProcess = qtyReturnedInProcess.add(qcFailedQty).add(currentReturnedQty);
						qtyReturned =  BigDecimal.ZERO;
								
					}else {
						
						qtyReturnedInProcess = qtyReturnedInProcess.add(currentReturnedQty);
					}
				}
				
				
			}

		}

		quantityReturned.setQtyReturned(qtyReturned);
		quantityReturned.setQtyReturnedInProcess(qtyReturnedInProcess);
		quantityReturned.setQcFaildQty(qcFailedQty);
		quantityReturned.setQtyMissing(qtyMissing);
		return quantityReturned;
	}
	
	public QuantityReturned getSalesQtyReturned(Integer requestItemId,boolean isReturnView) {
		QuantityReturned quantityReturned = new QuantityReturned();
		BigDecimal qtyReturned = BigDecimal.ZERO;
		BigDecimal qtyReturnedInProcess = BigDecimal.ZERO;
		BigDecimal qcFailedQty = BigDecimal.ZERO;

		List<AmastyRmaRequestItem> amastyRmaRequestItems = amastyRmaRequestItemRepository.findByOrderItemId(requestItemId);
		if (CollectionUtils.isNotEmpty(amastyRmaRequestItems)) {
			for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequestItems) {
				boolean noShortPickup = true;
				if (amastyRmaRequestItem.getItemStatus() == 12 || amastyRmaRequestItem.getItemStatus() == 13)
					continue;

				BigDecimal currentReturnedQty = BigDecimal.ZERO;
				if (amastyRmaRequestItem.getItemStatus() == 25) {
					if (ObjectUtils.isNotEmpty(amastyRmaRequestItem.getActualQuantyReturned()))
						currentReturnedQty = new BigDecimal(amastyRmaRequestItem.getActualQuantyReturned());
					noShortPickup = false;
				} else {
					currentReturnedQty = amastyRmaRequestItem.getQty();
				}
				if(null != amastyRmaRequestItem.getQcFailedQty()) {
					
					qcFailedQty = qcFailedQty.add(new BigDecimal(amastyRmaRequestItem.getQcFailedQty().toString()));	
							
				}

				if(!isReturnView) {
					qtyReturned = qtyReturned.add(currentReturnedQty).add(qcFailedQty);
					
					if (amastyRmaRequestItem.getItemStatus() != 4) {
						qtyReturnedInProcess = qtyReturnedInProcess.add(currentReturnedQty).add(qcFailedQty);

					}
				}else {
					
                	if(amastyRmaRequestItem.getItemStatus() != 4) {
                		
     					qtyReturned = qtyReturned.add(currentReturnedQty);

                	}else if (noShortPickup && qcFailedQty.intValue() > 0 && amastyRmaRequestItem.getItemStatus() != 4) {
						qtyReturnedInProcess = qtyReturnedInProcess.add(qcFailedQty).add(currentReturnedQty);
						qtyReturned =  BigDecimal.ZERO;
								
					}else {
						
						qtyReturnedInProcess = qtyReturnedInProcess.add(currentReturnedQty);
					}
				}

			}

		}

		quantityReturned.setQtyReturned(qtyReturned);
		quantityReturned.setQtyReturnedInProcess(qtyReturnedInProcess);
		quantityReturned.setQcFaildQty(qcFailedQty);
		return quantityReturned;
	}

	private void setDetailsFromMulin(Map<String, ProductResponseBody> productsFromMulin, String sku,
			OmsProduct productDTO) {
		boolean returnCategoryRestriction = false;
		for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
			ProductResponseBody productDetailsFromMulin = entry.getValue();
			Variant variant = productDetailsFromMulin.getVariants().stream().filter(e -> e.getSku().equals(sku))
					.findAny().orElse(null);
			if (variant != null) {
				if (variant.getSizeLabels() != null)
					productDTO.setSize(parseNullStr(variant.getSizeLabels().getEn()));

				returnCategoryRestriction = !productDetailsFromMulin.getIsReturnApplicable();
				productDTO.setReturnCategoryRestriction(parseNullStr(returnCategoryRestriction));

				if (productDetailsFromMulin.getMediaGallery() != null
						&& !productDetailsFromMulin.getMediaGallery().isEmpty()) {
					GalleryItem galleryItem = productDetailsFromMulin.getMediaGallery().get(0);
					if (galleryItem != null)
						productDTO.setImage(galleryItem.getValue());
				}
				if(null != productDetailsFromMulin.getAttributes().getName().getArabic()) {
					productDTO.setNameAr(productDetailsFromMulin.getAttributes().getName().getArabic());
					productDTO.setName(productDetailsFromMulin.getAttributes().getName().getEnglish());	
				}
			}
		}
	}

	public BigDecimal getBaseValueDecimal(BigDecimal value, BigDecimal currencyConversionRate) {
		if(null != value && null != currencyConversionRate) {
			return value.multiply(currencyConversionRate).setScale(4, RoundingMode.HALF_UP);
		}
		return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
	}

	private void setOrderStatusCallToActionFlag(OrdersDetailsResponsedto resp, SalesOrder order) {

		switch (order.getStatus()) {
		case OrderConstants.FAILED_ORDER_STATUS:
			if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getExternalQuoteStatus() != null
					&& order.getSubSalesOrder().getExternalQuoteStatus() == 1) {
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
		case OrderConstants.CLOSED_ORDER_STATUS:
		case OrderConstants.REFUNDED_ORDER_STATUS:
			resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_REORDER);
			break;
		case OrderConstants.UNDELIVERED_ORDER_STATUS:
			resp.setCallToActionFlag(OrderConstants.CALL_TO_ACTION_FLAG_TRACK_SHIPMENT);
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

	public String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}
	
	public String convertTimeToKSA(Timestamp datetime) {

		LocalDateTime utcDateTime = datetime.toLocalDateTime();

        // Convert to KSA time zone
		ZoneId utcZoneId = ZoneId.of("UTC");
        ZoneId ksaZoneId = ZoneId.of("Asia/Riyadh");
        LocalDateTime ksaDateTime = utcDateTime.atZone(utcZoneId).withZoneSameInstant(ksaZoneId).toLocalDateTime();


        // Format LocalDateTime to string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return ksaDateTime.format(formatter);
	}

	public String convertTimeZone(Timestamp datetime, Integer storeId) {

		if (null != datetime) {
			Calendar calendar = Calendar.getInstance();

			Date dateTime = new Date(datetime.getTime());
			calendar.setTime(dateTime);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
	
	public List<OmsProductTax> getTaxObjects(SalesOrderItem salesOrderItem) {
		List<OmsProductTax> omsProductTaxs = new ArrayList<>();
		for (SalesOrderItemTax salesOrderItemtax : salesOrderItem.getSalesOrderItemTax()) {
			OmsProductTax omsProductTax = new OmsProductTax();
			omsProductTax.setTaxType(salesOrderItemtax.getTaxType());
			omsProductTax.setTaxPercentage(salesOrderItemtax.getTaxPercentage().toString());
			omsProductTax.setTaxAmount(salesOrderItemtax.getTaxAmount().toString());
			omsProductTaxs.add(omsProductTax);
		}
		return omsProductTaxs;
	}

	public List<OmsProductTax> getTaxObjects(SplitSalesOrderItem splitSalesOrderItem) {
		List<OmsProductTax> omsProductTaxs = new ArrayList<>();
        if(splitSalesOrderItem.getSalesOrderItem() == null) return omsProductTaxs;
		for (SalesOrderItemTax salesOrderItemtax : splitSalesOrderItem.getSalesOrderItem().getSalesOrderItemTax()) {
			OmsProductTax omsProductTax = new OmsProductTax();
			omsProductTax.setTaxType(salesOrderItemtax.getTaxType());
			omsProductTax.setTaxPercentage(salesOrderItemtax.getTaxPercentage().toString());
			omsProductTax.setTaxAmount(salesOrderItemtax.getTaxAmount().toString());
			omsProductTaxs.add(omsProductTax);
		}
		return omsProductTaxs;
	}
	
	public List<OmsProductTax> getTaxObjects(SalesOrderItem salesOrderItem,  boolean indivisual) {
		LOGGER.info("Tax object calculation : " + indivisual);
		List<OmsProductTax> omsProductTaxs = new ArrayList<>();
		for (SalesOrderItemTax salesOrderItemtax  : salesOrderItem.getSalesOrderItemTax()) {
			OmsProductTax omsProductTax = new OmsProductTax();
			omsProductTax.setTaxType(salesOrderItemtax.getTaxType());
			if(salesOrderItem.getQtyOrdered().compareTo(BigDecimal.ZERO) != 0 
					&& salesOrderItemtax.getTaxAmount().compareTo(BigDecimal.ZERO) != 0  
						&& null != salesOrderItemtax.getTaxAmount()) {	
				omsProductTax.setTaxAmount(salesOrderItemtax.getTaxAmount().divide(salesOrderItem.getQtyOrdered()).toString());
			}else {
				omsProductTax.setTaxAmount(BigDecimal.ZERO.toString());
			}
			omsProductTax.setTaxPercentage(salesOrderItemtax.getTaxPercentage().toString());
			omsProductTaxs.add(omsProductTax);
		}
		return omsProductTaxs;
	}

	public List<OmsProductTax> getTaxObjects(SplitSalesOrderItem salesOrderItem,  boolean indivisual) {
		LOGGER.info("Tax object calculation : " + indivisual);
		List<OmsProductTax> omsProductTaxs = new ArrayList<>();
		for (SalesOrderItemTax salesOrderItemtax : salesOrderItem.getSalesOrderItem().getSalesOrderItemTax()) {
			OmsProductTax omsProductTax = new OmsProductTax();
			omsProductTax.setTaxType(salesOrderItemtax.getTaxType());
			if(salesOrderItem.getQtyOrdered().compareTo(BigDecimal.ZERO) != 0 
					&& salesOrderItemtax.getTaxAmount().compareTo(BigDecimal.ZERO) != 0  
						&& null != salesOrderItemtax.getTaxAmount()) {	
				omsProductTax.setTaxAmount(salesOrderItemtax.getTaxAmount().divide(salesOrderItem.getQtyOrdered()).toString());
			}else {
				omsProductTax.setTaxAmount(BigDecimal.ZERO.toString());
			}
			omsProductTax.setTaxPercentage(salesOrderItemtax.getTaxPercentage().toString());
			omsProductTaxs.add(omsProductTax);
		}
		return omsProductTaxs;
	}
	
	private String formatTimezone(Timestamp datetime, Integer storeId) {

		if (null != datetime) {
			Calendar calendar = Calendar.getInstance();

			Date dateTime = new Date(datetime.getTime());
			calendar.setTime(dateTime);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
	
	private void updateGrandTotal(OrderTotal totals, BigDecimal grandTotalPaid, BigDecimal storeCredit, BigDecimal coinInCurrency, BigDecimal voucher, BigDecimal storeToBaseRate) {
		
		// Ensure all BigDecimal parameters are not null
	    grandTotalPaid = grandTotalPaid != null ? grandTotalPaid : BigDecimal.ZERO; // Added null check
	    storeCredit = storeCredit != null ? storeCredit : BigDecimal.ZERO; // Added null check
	    coinInCurrency = coinInCurrency != null ? coinInCurrency : BigDecimal.ZERO; // Added null check
	    voucher = voucher != null ? voucher : BigDecimal.ZERO; // Added null check
	    storeToBaseRate = storeToBaseRate != null ? storeToBaseRate : BigDecimal.ONE; // Default to 1 to avoid division by zero or null issues // Added null check
				
		BigDecimal actualAmountpaid = grandTotalPaid;
		
		BigDecimal actualInvoicedAmount = totals.getTotalTaxableAmount() != null ? totals.getTotalTaxableAmount() : BigDecimal.ZERO;
		
		if(null != totals.getCoinToCurrency() && !totals.getCoinToCurrency().isEmpty()) {
			grandTotalPaid = grandTotalPaid.add(new BigDecimal(totals.getCoinToCurrency()));
		}	
		
		BigDecimal taxFactor = totals.getTaxFactor() != null ? totals.getTaxFactor() : BigDecimal.ONE;

		BigDecimal totalTaxAmount = actualInvoicedAmount.multiply(taxFactor.subtract(BigDecimal.ONE)).setScale(2, RoundingMode.HALF_UP);

		totals.setTaxAmount(parseNullStr(totalTaxAmount));
		totals.setBaseTaxAmount(parseNullStr(getBaseValueDecimal(totalTaxAmount, storeToBaseRate)));
		actualInvoicedAmount = actualInvoicedAmount.add(totalTaxAmount);
		if(null != totals.getDonationAmount() && !totals.getDonationAmount().isEmpty()) {
			actualInvoicedAmount = actualInvoicedAmount.add(new BigDecimal(totals.getDonationAmount()));
		}
				
		if (storeCredit != null) {
			grandTotalPaid = grandTotalPaid.add(storeCredit);
		}
		if (voucher != null) {
			// actualInvoicedAmount = actualInvoicedAmount.subtract(voucher);
			grandTotalPaid = grandTotalPaid.add(voucher);
		}
		// Subtract shukran burned value from grand total if present
		if (null != totals.getTotalShukranBurnedValueInCurrency() && totals.getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal shukranBurnedValue = totals.getTotalShukranBurnedValueInCurrency();
			grandTotalPaid = grandTotalPaid.subtract(shukranBurnedValue);
			actualInvoicedAmount = actualInvoicedAmount.subtract(shukranBurnedValue);
		}
		BigDecimal roundingAmount = grandTotalPaid.subtract(actualInvoicedAmount);
		
		grandTotalPaid = grandTotalPaid.setScale(2, RoundingMode.HALF_UP);
		roundingAmount = roundingAmount.setScale(2, RoundingMode.HALF_UP);
		
		totals.setGrandTotal(parseNullStr(actualInvoicedAmount));
		totals.setBaseGrandTotal(parseNullStr(getBaseValueDecimal(actualInvoicedAmount, storeToBaseRate)));
		totals.setRoundingAmount(roundingAmount);
		totals.setInvoicedAmount(parseNullStr(grandTotalPaid));
		totals.setBaseInvoicedAmount(parseNullStr(getBaseValueDecimal(grandTotalPaid, storeToBaseRate)));
		totals.setGrandTotal(parseNullStr(actualAmountpaid));
		totals.setBaseGrandTotal(parseNullStr(getBaseValueDecimal(actualAmountpaid, storeToBaseRate)));
		BigDecimal totalTaxableAmount = totals.getTotalTaxableAmount();
		if(null != totals.getDonationAmount() && !totals.getDonationAmount().isEmpty()) {
			totalTaxableAmount = totalTaxableAmount.add(new BigDecimal(totals.getDonationAmount()));
		}
		totals.setTotalTaxableAmount(totalTaxableAmount);
		totals.setBaseTotalTaxableAmount(getBaseValueDecimal(totalTaxableAmount, storeToBaseRate));
		totals.setGiftVoucherAmount(parseNullStr(voucher.setScale(2, RoundingMode.HALF_UP)));	
	}

	private OrderTotal setSplitOrderTotals(SplitSalesOrder splitSalesOrder, OrderTotal totals, Stores store) {
		// Set basic totals from split sales order
		totals.setGrandTotal(parseNullStr(splitSalesOrder.getGrandTotal()));
		totals.setBaseGrandTotal(parseNullStr(splitSalesOrder.getBaseGrandTotal()));
		totals.setSubtotal(parseNullStr(splitSalesOrder.getSubtotal()));
		totals.setBaseSubtotal(parseNullStr(splitSalesOrder.getBaseSubtotal()));
		totals.setTaxAmount(parseNullStr(splitSalesOrder.getTaxAmount()));
		totals.setBaseTaxAmount(parseNullStr(splitSalesOrder.getBaseTaxAmount()));
		totals.setShippingAmount(parseNullStr(splitSalesOrder.getShippingAmount()));
		totals.setBaseShippingAmount(parseNullStr(splitSalesOrder.getBaseShippingAmount()));
		totals.setDiscountAmount(parseNullStr(splitSalesOrder.getDiscountAmount()));
		totals.setBaseDiscountAmount(parseNullStr(splitSalesOrder.getBaseDiscountAmount()));
		totals.setCurrency(parseNullStr(splitSalesOrder.getOrderCurrencyCode()));
		totals.setBaseCurrency(parseNullStr(splitSalesOrder.getBaseCurrencyCode()));
		
		if (splitSalesOrder.getSplitSubSalesOrder() != null) {
			SplitSubSalesOrder splitSubSalesOrder = splitSalesOrder.getSplitSubSalesOrder();
			
			// Set donation amounts
			totals.setDonationAmount(parseNullStr(splitSubSalesOrder.getDonationAmount()));
			totals.setBaseDonationAmount(parseNullStr(splitSubSalesOrder.getBaseDonationAmount()));
			
			// Handle discount data
			if (StringUtils.isNotBlank(splitSubSalesOrder.getDiscountData())) {
				List<DiscountData> discountDataList = null;
				try {
					discountDataList = Arrays.asList(mapper.readValue(splitSubSalesOrder.getDiscountData(), DiscountData[].class));
				} catch (JsonProcessingException e) {
					LOGGER.error("exception occurred during convert offer string to object" + e.getMessage());
				}
				totals.setDiscountData(discountDataList);
			}
			
			// Handle external auto coupon
			if (splitSubSalesOrder.getExternalAutoCouponAmount() != null) {
				BigDecimal couponDiscount = splitSalesOrder.getDiscountAmount();
				if (couponDiscount != null) {
					couponDiscount = couponDiscount.abs().subtract(splitSubSalesOrder.getExternalAutoCouponAmount());
					totals.setCouponDiscountAmount(parseNullStr(couponDiscount));
				}
			}
		}
		
		if (!splitSalesOrder.getSplitSalesOrderPayments().isEmpty()) {
			SplitSalesOrderPayment splitPayment = splitSalesOrder.getSplitSalesOrderPayments().stream().findFirst().orElse(null);
			if (splitPayment != null) {
				// Set total paid amounts from payment
				totals.setTotalPaid(parseNullStr(splitPayment.getAmountPaid()));
				totals.setBaseTotalPaid(parseNullStr(splitPayment.getBaseAmountPaid()));
			}
		}
		
		// Handle loyalty points and store credits from SplitSubSalesOrder
		if (splitSalesOrder.getSplitSubSalesOrder() != null) {
			SplitSubSalesOrder splitSubSalesOrder = splitSalesOrder.getSplitSubSalesOrder();
			
			// EAS Coins
			if (splitSubSalesOrder.getEasCoins() != null) {
				totals.setSpendCoin(splitSubSalesOrder.getEasCoins());
				totals.setCoinToCurrency(parseNullStr(splitSubSalesOrder.getEasValueInCurrency()));
				totals.setCoinToBaseCurrency(parseNullStr(splitSubSalesOrder.getEasValueInBaseCurrency()));
			}
			
			// Shukran Points
			if (splitSubSalesOrder.getTotalShukranCoinsBurned() != null && splitSubSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
				totals.setTotalShukranBurnedValueInCurrency(splitSubSalesOrder.getTotalShukranBurnedValueInCurrency());
				totals.setTotalShukranBurnedPoints(splitSubSalesOrder.getTotalShukranCoinsBurned());
				totals.setTotalShukranBurnedValueInBaseCurrency(splitSubSalesOrder.getTotalShukranBurnedValueInBaseCurrency());
			}
			
			if (splitSubSalesOrder.getTotalShukranCoinsEarned() != null && splitSubSalesOrder.getTotalShukranCoinsEarned().compareTo(BigDecimal.ZERO) > 0) {
				totals.setTotalShukranEarnedPoints(splitSubSalesOrder.getTotalShukranCoinsEarned());
				totals.setTotalShukranEarnedValueInCurrency(splitSubSalesOrder.getTotalShukranEarnedValueInCurrency());
				totals.setTotalShukranEarnedValueInBaseCurrency(splitSubSalesOrder.getTotalShukranEarnedValueInBaseCurrency());
			}
			
			// Shukran tier and phone
			if (StringUtils.isNotEmpty(splitSubSalesOrder.getTierName())) {
				totals.setShukranTierName(splitSubSalesOrder.getTierName());
			}
			if (StringUtils.isNotEmpty(splitSubSalesOrder.getShukranPhoneNumber())) {
				totals.setShukranPhoneNumber(splitSubSalesOrder.getShukranPhoneNumber());
			}
		}
		
		// Handle additional charges
		BigDecimal taxFactor = totals.getTaxFactor() != null ? totals.getTaxFactor() : BigDecimal.ONE;
		
		// COD Charges
		if (splitSalesOrder.getCashOnDeliveryFee() != null && splitSalesOrder.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal codChargesOrder = splitSalesOrder.getCashOnDeliveryFee();
			totals.setCodCharges(parseNullStr(codChargesOrder));
			totals.setBaseCodCharges(parseNullStr(getBaseValueDecimal(codChargesOrder, splitSalesOrder.getStoreToBaseRate())));
			totals.setCodTaxCharges(parseNullStr(splitSalesOrder.getCashOnDeliveryFee().subtract(codChargesOrder)));
		}
		
		// Import Fees
		if (splitSalesOrder.getImportFee() != null && splitSalesOrder.getImportFee().compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal importFeeOrder = taxFactor != null ? splitSalesOrder.getImportFee().divide(taxFactor, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
			totals.setImportFeesAmount(parseNullStr(importFeeOrder));
			totals.setBaseImportFeesAmount(parseNullStr(getBaseValueDecimal(importFeeOrder, splitSalesOrder.getStoreToBaseRate())));
		}
		
		// Shipping Tax
		if (splitSalesOrder.getShippingAmount() != null && splitSalesOrder.getShippingAmount().compareTo(BigDecimal.ZERO) != 0) {
			BigDecimal shippingAmountOrder = taxFactor != null ? splitSalesOrder.getShippingAmount().divide(taxFactor, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
			totals.setShippingTaxAmount(parseNullStr(splitSalesOrder.getShippingAmount().subtract(shippingAmountOrder)));
		}
		
		// Gift Voucher
		BigDecimal voucher = splitSalesOrder.getGiftVoucherDiscount() != null ? splitSalesOrder.getGiftVoucherDiscount() : BigDecimal.ZERO;
		totals.setGiftVoucherAmount(parseNullStr(voucher));
		
		// Update grand total with all components
		updateGrandTotal(totals, splitSalesOrder.getGrandTotal(), splitSalesOrder.getAmstorecreditAmount(),
				splitSalesOrder.getSplitSubSalesOrder() != null ? splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency() : BigDecimal.ZERO, 
				voucher, splitSalesOrder.getStoreToBaseRate());

        return totals;
	}

	private void setSplitOrderProducts(SplitSalesOrder splitSalesOrder, Map<String, ProductResponseBody> productsFromMulin, 
			List<OmsProduct> splitProducts, Stores store, List<OrderHistory> histories) {
		
		for (SplitSalesOrderItem splitOrderItem : splitSalesOrder.getSplitSalesOrderItems()) {
			// Filter by product_type = 'simple'
			if ("simple".equalsIgnoreCase(splitOrderItem.getProductType())) {
				OmsProduct productDTO = new OmsProduct();
				
				// Set split order identifiers
				productDTO.setSplitOrderId(splitSalesOrder.getEntityId());
				productDTO.setSplitOrderIncrementId(splitSalesOrder.getIncrementId());
				
				// Basic product info
				productDTO.setSku(splitOrderItem.getSku());
				productDTO.setName(splitOrderItem.getName());
				productDTO.setQty(parseNullStr(splitOrderItem.getQtyOrdered()));
				
				productDTO.setParentOrderItemId(splitOrderItem.getItemId());
				productDTO.setParentProductId(splitOrderItem.getSku());
				
				// Quantity fields from splitOrderItem
				productDTO.setQtyCanceled(parseNullStr(splitOrderItem.getQtyCanceled()));
				productDTO.setQtyInvoiced(parseNullStr(splitOrderItem.getQtyInvoiced()));
				productDTO.setQtyShipped(parseNullStr(splitOrderItem.getQtyShipped()));
				
				// Before the loop that iterates over split order items
				Map<Integer, SalesOrderItem> childItemsByParentId = splitSalesOrder.getSalesOrder().getSalesOrderItem().stream()
				.filter(e -> e.getParentOrderItem() != null && e.getParentOrderItem().getItemId() != null)
				.collect(Collectors.toMap(e -> e.getParentOrderItem().getItemId(), e -> e, (e1, e2) -> e1));

				// Inside your loop over split order items:
				if (splitOrderItem.getSalesOrderItem() != null) {
					SalesOrderItem originalOrderItem = splitOrderItem.getSalesOrderItem();
					SalesOrderItem childOrderItem = childItemsByParentId.get(originalOrderItem.getItemId());
					if (childOrderItem != null) {
						QuantityReturned quantityReturned = getSalesQtyReturned(childOrderItem.getItemId(), false);
						productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
						productDTO.setQtyReturnedInProcess(parseNullStr(childOrderItem.getQtyRefunded()));
					}
				}
				
				productDTO.setPrice(parseNullStr(splitOrderItem.getPrice()));
				productDTO.setOriginalPrice(parseNullStr(splitOrderItem.getOriginalPrice()));
				productDTO.setTaxAmount(parseNullStr(splitOrderItem.getTaxAmount()));
				productDTO.setBaseTaxAmount(parseNullStr(splitOrderItem.getBaseTaxAmount()));
				productDTO.setTaxPercentage(parseNullStr(splitOrderItem.getTaxPercent()));
				
				productDTO.setRowTotal(parseNullStr(splitOrderItem.getRowTotal()));
				productDTO.setBaseRowTotal(parseNullStr(splitOrderItem.getBaseRowTotal()));
				productDTO.setRowTotalInclTax(parseNullStr(splitOrderItem.getRowTotalInclTax()));
				productDTO.setBaseRowTotalInclTax(parseNullStr(splitOrderItem.getBaseRowTotalInclTax()));
				productDTO.setSubtotal(parseNullStr(splitOrderItem.getRowTotal()));
				productDTO.setBaseSubtotal(parseNullStr(splitOrderItem.getBaseRowTotal()));
				
				BigDecimal finalPrice = splitOrderItem.getRowTotalInclTax().subtract(splitOrderItem.getDiscountAmount());
				BigDecimal baseFinalPrice = splitOrderItem.getBaseRowTotalInclTax().subtract(splitOrderItem.getBaseDiscountAmount());
				productDTO.setFinalPrice(parseNullStr(finalPrice));
				productDTO.setBaseFinalPrice(parseNullStr(baseFinalPrice));
				
				// Get additional fields from the original sales order item if available
				if (splitOrderItem.getSalesOrderItem() != null) {
					SalesOrderItem originalOrderItem = splitOrderItem.getSalesOrderItem();
					
					// Actual price from original item
					productDTO.setActualPrice(parseNullStr(originalOrderItem.getActualPrice()));
					
					// Gift product flag
					if (null != originalOrderItem.getGiftMessageAvailable() && originalOrderItem.getGiftMessageAvailable().equals(1)) {
						productDTO.setGiftProduct(true);
					}
					
					// Return category restriction
					if (ObjectUtils.isNotEmpty(originalOrderItem.getReturnable())) {
						productDTO.setReturnCategoryRestriction(originalOrderItem.getReturnable() == 1 ? "false" : "true");
					}
					
					// HSN Code
					productDTO.setHsnCode(parseNullStr(originalOrderItem.getHsnCode()));
				}
				
				// Brand name from split order item
				if (StringUtils.isNotBlank(splitOrderItem.getItemBrandName())) {					
					productDTO.setBrandName(splitOrderItem.getItemBrandName());
				}
				
				// Set product details from Mulin
				setDetailsFromMulin(productsFromMulin, splitOrderItem.getSku(), productDTO);
				
				List<OmsProductTax> taxObjects = getTaxObjects(splitOrderItem);
				productDTO.setTaxObjects(taxObjects);
				
				// Set histories for this product
				productDTO.setHistories(histories);
				
				splitProducts.add(productDTO);
			}
		}
	}

	private void setMainOrderTotals(SalesOrder order, OrderTotal totals, Stores store) {
		// Set main order totals for backward compatibility
		totals.setGrandTotal(parseNullStr(order.getGrandTotal()));
		totals.setBaseGrandTotal(parseNullStr(order.getBaseGrandTotal()));
		totals.setSubtotal(parseNullStr(order.getSubtotal()));
		totals.setBaseSubtotal(parseNullStr(order.getBaseSubtotal()));
		totals.setTaxAmount(parseNullStr(order.getTaxAmount()));
		totals.setBaseTaxAmount(parseNullStr(order.getBaseTaxAmount()));
		totals.setShippingAmount(parseNullStr(order.getShippingAmount()));
		totals.setBaseShippingAmount(parseNullStr(order.getBaseShippingAmount()));
		totals.setDiscountAmount(parseNullStr(order.getDiscountAmount()));
		totals.setBaseDiscountAmount(parseNullStr(order.getBaseDiscountAmount()));
		totals.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		totals.setBaseCurrency(parseNullStr(order.getBaseCurrencyCode()));
	}

	private boolean setShipmentItems(SalesOrder order, Map<String, ProductResponseBody> productsFromMulin,
			OrdersDetailsResponsedto resp) {

		boolean isSplitOrder = Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder());
		List<OmsProduct> omsProductList = new ArrayList<>();

		if(!isSplitOrder) {
			if (order.getSalesShipments().isEmpty()) return false;

			SalesShipment shipment = order.getSalesShipments().iterator().next();

			shipment.getSalesShipmentItem().forEach(ex -> {
				OmsProduct productDTO = new OmsProduct();
				productDTO.setSplitOrderId(order.getEntityId());
				productDTO.setSplitOrderIncrementId(order.getIncrementId());
				productDTO.setSku(ex.getSku());
				productDTO.setName(ex.getName());
				productDTO.setQty(parseNullStr(ex.getQuantity()));
				setDetailsFromMulin(productsFromMulin, ex.getSku(), productDTO);
			});
			return true;
		}

		List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId());
		splitSalesOrders.forEach(splitSalesOrder -> {
			if(splitSalesOrder.getSplitSalesShipments().isEmpty()) return;
			SalesShipment shipment = splitSalesOrder.getSplitSalesShipments().iterator().next();
			shipment.getSalesShipmentItem().forEach(ex -> {
				OmsProduct productDTO = new OmsProduct();
				productDTO.setSplitOrderId(splitSalesOrder.getEntityId());
				productDTO.setSplitOrderIncrementId(splitSalesOrder.getIncrementId());
				productDTO.setSku(ex.getSku());
				productDTO.setName(ex.getName());
				productDTO.setQty(parseNullStr(ex.getQuantity()));
				setDetailsFromMulin(productsFromMulin, ex.getSku(), productDTO);
				productDTO.setShipmentId(shipment.getEntityId());
				productDTO.setShipmentIncrementId(shipment.getIncrementId());
				productDTO.setTrackingDetails(setTrackingDetails(splitSalesOrder));
				omsProductList.add(productDTO);
			});
		});

		Set<String> skuSet = new HashSet<>();
		List<OmsProduct> productBySkuList = omsProductList.stream().filter(e -> skuSet.add(e.getSku()))
				.collect(Collectors.toList());

		return true;
	}

	/**
	 * Helper class to build shipment items and reduce duplication
	 */
	private static class ShipmentItemBuilder {
		private final Map<String, ProductResponseBody> productsFromMulin;
		private final OmsorderentityConverter converter;

		public ShipmentItemBuilder(Map<String, ProductResponseBody> productsFromMulin, OmsorderentityConverter converter) {
			this.productsFromMulin = productsFromMulin;
			this.converter = converter;
		}

		public List<OmsProduct> buildShipmentItems(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, List<OrderHistory> histories, TrackingDetails trackingDetails) {
			List<OmsProduct> omsProductList = new ArrayList<>();
			boolean isSplitOrder = Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder());

			if (!isSplitOrder) {
				buildRegularOrderShipmentItems(order, omsProductList, histories, trackingDetails);
			} else {
				buildSplitOrderShipmentItems(splitSalesOrders, omsProductList, histories);
			}

			return deduplicateBySku(omsProductList);
		}

		private void buildRegularOrderShipmentItems(SalesOrder order, List<OmsProduct> omsProductList, List<OrderHistory> histories, TrackingDetails trackingDetails) {
			if (order.getSalesShipments().isEmpty()) return;

			SalesShipment shipment = order.getSalesShipments().iterator().next();
			shipment.getSalesShipmentItem().forEach(ex -> {
				OmsProduct productDTO = createBasicShipmentProduct(ex);
				productDTO.setSplitOrderId(order.getEntityId());
				productDTO.setSplitOrderIncrementId(order.getIncrementId());
				productDTO.setShipmentId(shipment.getEntityId());
				productDTO.setShipmentIncrementId(shipment.getIncrementId());
				productDTO.setHistories(histories);
                productDTO.setTrackingDetails(trackingDetails);
				omsProductList.add(productDTO);
			});
		}

		private void buildSplitOrderShipmentItems(List<SplitSalesOrder> splitSalesOrders, List<OmsProduct> omsProductList, List<OrderHistory> histories) {
			splitSalesOrders.forEach(splitSalesOrder -> {
				if (splitSalesOrder.getSplitSalesShipments().isEmpty()) return;
				
				SalesShipment shipment = splitSalesOrder.getSplitSalesShipments().iterator().next();
				shipment.getSalesShipmentItem().forEach(ex -> {
					String type = splitSalesOrder.getIncrementId().contains(OrderConstants.LOCAL_ORDER_SUFFIX) ? "local" : "global";
					OmsProduct productDTO = createBasicShipmentProduct(ex);
					productDTO.setSplitOrderId(splitSalesOrder.getEntityId());
					productDTO.setSplitOrderIncrementId(splitSalesOrder.getIncrementId());
					productDTO.setShipmentId(shipment.getEntityId());
					productDTO.setShipmentIncrementId(shipment.getIncrementId());
					productDTO.setTrackingDetails(setTrackingDetails(splitSalesOrder));
					productDTO.setType(type);
					productDTO.setHistories(histories);
					omsProductList.add(productDTO);
				});
			});
		}

		private OmsProduct createBasicShipmentProduct(SalesShipmentItem shipmentItem) {
			OmsProduct productDTO = new OmsProduct();
			productDTO.setSku(shipmentItem.getSku());
			productDTO.setName(shipmentItem.getName());
			productDTO.setQty(converter.parseNullStr(shipmentItem.getQuantity()));
			converter.setDetailsFromMulin(productsFromMulin, shipmentItem.getSku(), productDTO);
			return productDTO;
		}

		private List<OmsProduct> deduplicateBySku(List<OmsProduct> products) {
			Set<String> skuSet = new HashSet<>();
			return products.stream()
					.filter(e -> skuSet.add(e.getSku()))
					.collect(Collectors.toList());
		}
	}

	/**
	 * Helper class to build products and reduce duplication
	 */
	private static class ProductBuilder {
		private final Map<String, ProductResponseBody> productsFromMulin;
		private final OrderHelper orderHelper;
		private final OmsorderentityConverter converter;

		public ProductBuilder(Map<String, ProductResponseBody> productsFromMulin, OrderHelper orderHelper, OmsorderentityConverter converter) {
			this.productsFromMulin = productsFromMulin;
			this.orderHelper = orderHelper;
			this.converter = converter;
		}

		public OmsProduct buildProductFromSalesOrderItem(SalesOrderItem salesOrderItem, OrdersDetailsResponsedto resp, TotalsCalculator calculator) {
			OmsProduct productDTO = new OmsProduct();
			
			setBasicProductInfo(productDTO, salesOrderItem);
			setProductDetails(productDTO, salesOrderItem, resp);
			setZatcaDetails(productDTO, salesOrderItem, calculator);
			
			return productDTO;
		}

		public OmsProduct buildProductFromSplitSalesOrderItem(SplitSalesOrderItem splitSalesOrderItem, Integer orderId, 
				String splitOrderIncrementId, OrdersDetailsResponsedto resp) {
			OmsProduct productDTO = new OmsProduct();
			
			setBasicSplitProductInfo(productDTO, splitSalesOrderItem, orderId, splitOrderIncrementId);
			setSplitProductDetails(productDTO, splitSalesOrderItem, resp);
			setSplitZatcaDetails(productDTO, splitSalesOrderItem);
			
			return productDTO;
		}

		private void setBasicProductInfo(OmsProduct productDTO, SalesOrderItem salesOrderItem) {
			productDTO.setSku(salesOrderItem.getSku());
			productDTO.setName(salesOrderItem.getName());
			productDTO.setParentOrderItemId(salesOrderItem.getItemId());
			setQuantityInfo(productDTO, salesOrderItem.getQtyOrdered(), salesOrderItem.getQtyCanceled(), salesOrderItem.getQtyShipped());
		}

		private void setBasicSplitProductInfo(OmsProduct productDTO, SplitSalesOrderItem splitSalesOrderItem, 
				Integer orderId, String splitOrderIncrementId) {
			productDTO.setSplitOrderIncrementId(splitOrderIncrementId);
			productDTO.setSplitOrderId(orderId);
			productDTO.setSku(splitSalesOrderItem.getSku());
			productDTO.setName(splitSalesOrderItem.getName());
			productDTO.setParentOrderItemId(splitSalesOrderItem.getItemId());
			setQuantityInfo(productDTO, splitSalesOrderItem.getQtyOrdered(), splitSalesOrderItem.getQtyCanceled(), splitSalesOrderItem.getQtyShipped());
		}

		private void setQuantityInfo(OmsProduct productDTO, BigDecimal qtyOrdered, BigDecimal qtyCanceled, BigDecimal qtyShipped) {
			if (qtyOrdered != null) {
				productDTO.setQty(qtyOrdered.toString());
			}
			if (qtyCanceled != null) {
				productDTO.setQtyCanceled(qtyCanceled.toString());
			}
			if (qtyShipped != null) {
				productDTO.setQtyShipped(qtyShipped.toString());
			}
		}

		private void setProductDetails(OmsProduct productDTO, SalesOrderItem salesOrderItem, OrdersDetailsResponsedto resp) {
			converter.setDetailsFromMulin(productsFromMulin, salesOrderItem.getSku(), productDTO);
			
			if (ObjectUtils.isNotEmpty(salesOrderItem.getReturnable())) {
				productDTO.setReturnCategoryRestriction(salesOrderItem.getReturnable() == 1 ? "false" : "true");
			}

			productDTO.setParentProductId(salesOrderItem.getSku());
			productDTO.setTaxAmount(converter.parseNullStr(salesOrderItem.getTaxAmount()));
			productDTO.setBaseTaxAmount(converter.parseNullStr(salesOrderItem.getBaseTaxAmount()));
			productDTO.setRowTotal(converter.parseNullStr(salesOrderItem.getRowTotal()));
			productDTO.setBaseRowTotal(converter.parseNullStr(salesOrderItem.getBaseRowTotal()));
			productDTO.setRowTotalInclTax(converter.parseNullStr(salesOrderItem.getRowTotalInclTax()));
			productDTO.setBaseRowTotalInclTax(converter.parseNullStr(salesOrderItem.getBaseRowTotalInclTax()));
			productDTO.setQtyInvoiced(converter.parseNullStr(salesOrderItem.getQtyInvoiced()));
			productDTO.setSubtotal(converter.parseNullStr(salesOrderItem.getRowTotal()));
			productDTO.setBaseSubtotal(converter.parseNullStr(salesOrderItem.getBaseRowTotal()));
			productDTO.setTaxPercentage(converter.parseNullStr(salesOrderItem.getTaxPercent()));
			productDTO.setActualPrice(converter.parseNullStr(salesOrderItem.getActualPrice()));

			if (null != salesOrderItem.getGiftMessageAvailable() && salesOrderItem.getGiftMessageAvailable().equals(1)) {
				productDTO.setGiftProduct(true);
				resp.setHasGiftProduct(true);
			}

			if (StringUtils.isNotBlank(salesOrderItem.getItemBrandName()) && StringUtils.isNotEmpty(salesOrderItem.getItemBrandName())) {
				productDTO.setBrandName(salesOrderItem.getItemBrandName());
			}

			productDTO.setTaxObjects(converter.getTaxObjects(salesOrderItem));
			productDTO.setHsnCode(converter.parseNullStr(salesOrderItem.getHsnCode()));
			
			// Set option_id: for configurable products, product_id contains the option_id
			if (OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(salesOrderItem.getProductType())) {
				productDTO.setOptionId(salesOrderItem.getProductId());
			}
		}

		private void setSplitProductDetails(OmsProduct productDTO, SplitSalesOrderItem splitSalesOrderItem, OrdersDetailsResponsedto resp) {
			converter.setDetailsFromMulin(productsFromMulin, splitSalesOrderItem.getSku(), productDTO);
			
			if (ObjectUtils.isNotEmpty(splitSalesOrderItem.getReturnable())) {
				productDTO.setReturnCategoryRestriction(splitSalesOrderItem.getReturnable() == 1 ? "false" : "true");
			}

			productDTO.setParentProductId(splitSalesOrderItem.getSku());
			productDTO.setTaxAmount(converter.parseNullStr(splitSalesOrderItem.getTaxAmount()));
			productDTO.setBaseTaxAmount(converter.parseNullStr(splitSalesOrderItem.getBaseTaxAmount()));
			productDTO.setRowTotal(converter.parseNullStr(splitSalesOrderItem.getRowTotal()));
			productDTO.setBaseRowTotal(converter.parseNullStr(splitSalesOrderItem.getBaseRowTotal()));
			productDTO.setRowTotalInclTax(converter.parseNullStr(splitSalesOrderItem.getRowTotalInclTax()));
			productDTO.setBaseRowTotalInclTax(converter.parseNullStr(splitSalesOrderItem.getBaseRowTotalInclTax()));
			productDTO.setQtyInvoiced(converter.parseNullStr(splitSalesOrderItem.getQtyInvoiced()));
			productDTO.setSubtotal(converter.parseNullStr(splitSalesOrderItem.getRowTotal()));
			productDTO.setBaseSubtotal(converter.parseNullStr(splitSalesOrderItem.getBaseRowTotal()));
			productDTO.setTaxPercentage(converter.parseNullStr(splitSalesOrderItem.getTaxPercent()));
			productDTO.setActualPrice(converter.parseNullStr(splitSalesOrderItem.getWeeeTaxAppliedAmount()));

			if (null != splitSalesOrderItem.getGiftMessageAvailable() && splitSalesOrderItem.getGiftMessageAvailable().equals(1)) {
				productDTO.setGiftProduct(true);
				resp.setHasGiftProduct(true);
			}

			if (StringUtils.isNotBlank(splitSalesOrderItem.getItemBrandName()) && StringUtils.isNotEmpty(splitSalesOrderItem.getItemBrandName())) {
				productDTO.setBrandName(splitSalesOrderItem.getItemBrandName());
			}

			productDTO.setTaxObjects(converter.getTaxObjects(splitSalesOrderItem));
			productDTO.setHsnCode(converter.parseNullStr(splitSalesOrderItem.getHsnCode()));
			
			// Set option_id: for configurable products, product_id contains the option_id
			if (OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(splitSalesOrderItem.getProductType())) {
				productDTO.setOptionId(splitSalesOrderItem.getProductId());
			}
		}

		private void setZatcaDetails(OmsProduct productDTO, SalesOrderItem salesOrderItem, TotalsCalculator calculator) {
			BigDecimal giftVoucherAmount = getGiftVoucherAmount(salesOrderItem);
			productDTO.setGiftVoucherAmount(converter.parseNullStr(giftVoucherAmount));

			productDTO.setPrice(converter.parseNullStr(salesOrderItem.getPriceInclTax()));
			productDTO.setDiscount(converter.parseNullStr(salesOrderItem.getDiscountAmount()));
			productDTO.setOriginalPrice(converter.parseNullStr(salesOrderItem.getOriginalPrice()));

			BigDecimal taxFactor = orderHelper.getExclTaxfactor(salesOrderItem.getTaxPercent());
			BigDecimal unitPriceExclTax = salesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);

			BigDecimal discountProductLevelExclTax = calculateDiscountProductLevelExclTax(salesOrderItem, taxFactor);
			BigDecimal discountCouponExclTaxProduct = calculateDiscountCouponExclTaxProduct(salesOrderItem, giftVoucherAmount, taxFactor);
			BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);

			BigDecimal taxablePriceProduct = calculateTaxablePriceProduct(unitPriceExclTax, salesOrderItem, discountProductLevelExclTax, discountCouponExclTaxProduct);
			BigDecimal totalTaxAmountProduct = calculateTotalTaxAmountProduct(taxablePriceProduct, salesOrderItem);
			BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2, RoundingMode.HALF_UP);

			productDTO.setUnitPriceExclTax(converter.parseNullStr(unitPriceExclTax));
			productDTO.setSubTotalExclTax(converter.parseNullStr(unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered())));
			productDTO.setTaxablePriceProduct(converter.parseNullStr(taxablePriceProduct));
			productDTO.setTotalDiscountExclTaxProduct(converter.parseNullStr(discountExclTaxProduct));
			productDTO.setTotalTaxAmountProduct(converter.parseNullStr(totalTaxAmountProduct));
			productDTO.setTotalPriceInclTaxProduct(converter.parseNullStr(totalPriceInclTaxProduct));

			BigDecimal finalPrice = salesOrderItem.getRowTotalInclTax().subtract(salesOrderItem.getDiscountAmount());
			BigDecimal baseFinalPrice = salesOrderItem.getBaseRowTotalInclTax().subtract(salesOrderItem.getBaseDiscountAmount());
			productDTO.setFinalPrice(converter.parseNullStr(finalPrice));
			productDTO.setBaseFinalPrice(converter.parseNullStr(baseFinalPrice));

			// Update calculator with totals only if calculator is not null
			if (calculator != null) {
				calculator.addTaxAmount(totalTaxAmountProduct);
				calculator.addProductLevelDiscount(discountProductLevelExclTax);
				calculator.addCouponDiscount(discountCouponExclTaxProduct);
				calculator.addPriceExclTax(unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered()));
				calculator.addTaxablePrice(taxablePriceProduct);
			}
		}

		private void setSplitZatcaDetails(OmsProduct productDTO, SplitSalesOrderItem splitSalesOrderItem) {
			BigDecimal giftVoucherAmount = getSplitGiftVoucherAmount(splitSalesOrderItem);
			productDTO.setGiftVoucherAmount(converter.parseNullStr(giftVoucherAmount));

			productDTO.setPrice(converter.parseNullStr(splitSalesOrderItem.getPriceInclTax()));
			productDTO.setDiscount(converter.parseNullStr(splitSalesOrderItem.getDiscountAmount()));
			productDTO.setOriginalPrice(converter.parseNullStr(splitSalesOrderItem.getOriginalPrice()));

			BigDecimal taxFactor = orderHelper.getExclTaxfactor(splitSalesOrderItem.getTaxPercent());
			BigDecimal unitPriceExclTax = splitSalesOrderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);

			BigDecimal discountProductLevelExclTax = calculateSplitDiscountProductLevelExclTax(splitSalesOrderItem, taxFactor);
			BigDecimal discountCouponExclTaxProduct = calculateSplitDiscountCouponExclTaxProduct(splitSalesOrderItem, giftVoucherAmount, taxFactor);
			BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct);

			BigDecimal taxablePriceProduct = calculateSplitTaxablePriceProduct(unitPriceExclTax, splitSalesOrderItem, discountProductLevelExclTax, discountCouponExclTaxProduct);
			BigDecimal totalTaxAmountProduct = calculateSplitTotalTaxAmountProduct(taxablePriceProduct, splitSalesOrderItem);
			BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2, RoundingMode.HALF_UP);

			productDTO.setUnitPriceExclTax(converter.parseNullStr(unitPriceExclTax));
			productDTO.setSubTotalExclTax(converter.parseNullStr(unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered())));
			productDTO.setTaxablePriceProduct(converter.parseNullStr(taxablePriceProduct));
			productDTO.setTotalDiscountExclTaxProduct(converter.parseNullStr(discountExclTaxProduct));
			productDTO.setTotalTaxAmountProduct(converter.parseNullStr(totalTaxAmountProduct));
			productDTO.setTotalPriceInclTaxProduct(converter.parseNullStr(totalPriceInclTaxProduct));

			BigDecimal finalPrice = splitSalesOrderItem.getRowTotalInclTax().subtract(splitSalesOrderItem.getDiscountAmount());
			BigDecimal baseFinalPrice = splitSalesOrderItem.getBaseRowTotalInclTax().subtract(splitSalesOrderItem.getBaseDiscountAmount());
			productDTO.setFinalPrice(converter.parseNullStr(finalPrice));
			productDTO.setBaseFinalPrice(converter.parseNullStr(baseFinalPrice));
		}

		private BigDecimal getGiftVoucherAmount(SalesOrderItem salesOrderItem) {
			if (null != salesOrderItem.getSubSalesOrderItem()) {
				SubSalesOrderItem subSalesOrderItem = salesOrderItem.getSubSalesOrderItem().stream()
						.filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
				if (null != subSalesOrderItem) {
					return subSalesOrderItem.getDiscount();
				}
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal getSplitGiftVoucherAmount(SplitSalesOrderItem splitSalesOrderItem) {
			if (null != splitSalesOrderItem.getSplitSubSalesOrderItem()) {
				SplitSubSalesOrderItem splitSubSalesOrderItem = splitSalesOrderItem.getSplitSubSalesOrderItem().stream()
						.filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
				if (null != splitSubSalesOrderItem) {
					return splitSubSalesOrderItem.getDiscount();
				}
			}
			return BigDecimal.ZERO;
		}

		private BigDecimal calculateDiscountProductLevelExclTax(SalesOrderItem salesOrderItem, BigDecimal taxFactor) {
			BigDecimal discountProductLevelExclTax = salesOrderItem.getOriginalPrice().subtract(salesOrderItem.getPriceInclTax());
			discountProductLevelExclTax = discountProductLevelExclTax.multiply(salesOrderItem.getQtyOrdered());
			if (discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
				discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
			}
			return discountProductLevelExclTax;
		}

		private BigDecimal calculateSplitDiscountProductLevelExclTax(SplitSalesOrderItem splitSalesOrderItem, BigDecimal taxFactor) {
			BigDecimal discountProductLevelExclTax = splitSalesOrderItem.getOriginalPrice().subtract(splitSalesOrderItem.getPriceInclTax());
			discountProductLevelExclTax = discountProductLevelExclTax.multiply(splitSalesOrderItem.getQtyOrdered());
			if (discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
				discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
			}
			return discountProductLevelExclTax;
		}

		private BigDecimal calculateDiscountCouponExclTaxProduct(SalesOrderItem salesOrderItem, BigDecimal giftVoucherAmount, BigDecimal taxFactor) {
			BigDecimal discountCouponExclTaxProduct = salesOrderItem.getDiscountAmount().subtract(giftVoucherAmount);
			return discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);
		}

		private BigDecimal calculateSplitDiscountCouponExclTaxProduct(SplitSalesOrderItem splitSalesOrderItem, BigDecimal giftVoucherAmount, BigDecimal taxFactor) {
			BigDecimal discountCouponExclTaxProduct = splitSalesOrderItem.getDiscountAmount().subtract(giftVoucherAmount);
			return discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);
		}

		private BigDecimal calculateTaxablePriceProduct(BigDecimal unitPriceExclTax, SalesOrderItem salesOrderItem, 
				BigDecimal discountProductLevelExclTax, BigDecimal discountCouponExclTaxProduct) {
			BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(salesOrderItem.getQtyOrdered());
			taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
					.subtract(discountCouponExclTaxProduct)
					.setScale(2, RoundingMode.HALF_UP);
			if (taxablePriceProduct.compareTo(BigDecimal.ZERO) < 0) {
				taxablePriceProduct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
			}
			return taxablePriceProduct;
		}

		private BigDecimal calculateSplitTaxablePriceProduct(BigDecimal unitPriceExclTax, SplitSalesOrderItem splitSalesOrderItem, 
				BigDecimal discountProductLevelExclTax, BigDecimal discountCouponExclTaxProduct) {
			BigDecimal taxablePriceProduct = unitPriceExclTax.multiply(splitSalesOrderItem.getQtyOrdered());
			taxablePriceProduct = taxablePriceProduct.subtract(discountProductLevelExclTax)
					.subtract(discountCouponExclTaxProduct)
					.setScale(2, RoundingMode.HALF_UP);
			if (taxablePriceProduct.compareTo(BigDecimal.ZERO) < 0) {
				taxablePriceProduct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
			}
			return taxablePriceProduct;
		}

		private BigDecimal calculateTotalTaxAmountProduct(BigDecimal taxablePriceProduct, SalesOrderItem salesOrderItem) {
			BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
			return totalTaxAmountProduct.multiply(salesOrderItem.getTaxPercent()).setScale(2, RoundingMode.HALF_UP);
		}

		private BigDecimal calculateSplitTotalTaxAmountProduct(BigDecimal taxablePriceProduct, SplitSalesOrderItem splitSalesOrderItem) {
			BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
			return totalTaxAmountProduct.multiply(splitSalesOrderItem.getTaxPercent()).setScale(2, RoundingMode.HALF_UP);
		}
	}

	/**
	 * Helper class to calculate totals and reduce duplication
	 */
	private static class TotalsCalculator {
		private BigDecimal totalTaxAmount = BigDecimal.ZERO;
		private BigDecimal totalProductLevelDiscountExclTax = BigDecimal.ZERO;
		private BigDecimal totalCouponDiscountExclTax = BigDecimal.ZERO;
		private BigDecimal totalPriceExclTax = BigDecimal.ZERO;
		private BigDecimal totaltaxablePrice = BigDecimal.ZERO;
		private BigDecimal totalShukranBurnedPointsToShowInUI = BigDecimal.ZERO;
		private BigDecimal totalShukranBurnedPointsToShowInBaseCurrencyInUI = BigDecimal.ZERO;
		private BigDecimal totalShukranBurnedPointsToShowInCurrencyInUI = BigDecimal.ZERO;

		public void addShukranPoints(BigDecimal points, BigDecimal baseCurrency, BigDecimal currency) {
			if (points != null && points.compareTo(BigDecimal.ZERO) > 0) {
				totalShukranBurnedPointsToShowInUI = totalShukranBurnedPointsToShowInUI.add(points);
				totalShukranBurnedPointsToShowInBaseCurrencyInUI = totalShukranBurnedPointsToShowInBaseCurrencyInUI.add(baseCurrency);
				totalShukranBurnedPointsToShowInCurrencyInUI = totalShukranBurnedPointsToShowInCurrencyInUI.add(currency);
			}
		}

		public void addTaxAmount(BigDecimal amount) {
			totalTaxAmount = totalTaxAmount.add(amount);
		}

		public void addProductLevelDiscount(BigDecimal amount) {
			totalProductLevelDiscountExclTax = totalProductLevelDiscountExclTax.add(amount);
		}

		public void addCouponDiscount(BigDecimal amount) {
			totalCouponDiscountExclTax = totalCouponDiscountExclTax.add(amount);
		}

		public void addPriceExclTax(BigDecimal amount) {
			totalPriceExclTax = totalPriceExclTax.add(amount);
		}

		public void addTaxablePrice(BigDecimal amount) {
			totaltaxablePrice = totaltaxablePrice.add(amount);
		}

		public void applyToOrderTotal(OrderTotal totals, Stores store, OmsorderentityConverter converter) {
			if (totalShukranBurnedPointsToShowInUI.compareTo(BigDecimal.ZERO) > 0) {
				totals.setTotalShukranBurnedPointsToShowInUI(totalShukranBurnedPointsToShowInUI.toBigInteger().intValue());
				totals.setTotalShukranBurnedValueInBaseCurrencyInUI(totalShukranBurnedPointsToShowInBaseCurrencyInUI.setScale(2, RoundingMode.HALF_UP));
				totals.setTotalShukranBurnedValueInCurrencyInUI(totalShukranBurnedPointsToShowInCurrencyInUI.setScale(2, RoundingMode.HALF_UP));
			}
			totals.setTotalProductLevelDiscountExclTax(converter.parseNullStr(totalProductLevelDiscountExclTax));
			totals.setTotalBaseProductLevelDiscountExclTax(converter.parseNullStr(converter.getBaseValueDecimal(totalProductLevelDiscountExclTax, store.getCurrencyConversionRate())));
			totals.setTotalCouponDiscountExclTax(converter.parseNullStr(totalCouponDiscountExclTax));
			totals.setTotalBaseCouponDiscountExclTax(converter.parseNullStr(converter.getBaseValueDecimal(totalCouponDiscountExclTax, store.getCurrencyConversionRate())));
			totals.setTotalPriceExclTax(converter.parseNullStr(totalPriceExclTax.setScale(2, RoundingMode.HALF_UP)));
			totals.setTotalBasePriceExclTax(converter.parseNullStr(converter.getBaseValueDecimal(totalPriceExclTax.setScale(2, RoundingMode.HALF_UP), store.getCurrencyConversionRate())));
			totals.setTaxAmount(converter.parseNullStr(totalTaxAmount));
			totals.setTotalTaxableAmount(totaltaxablePrice);
		}
	}


	private OmsProduct buildCommonProduct(SalesOrderItem salesOrderItem, SalesOrder order, 
			Map<String, ProductResponseBody> productsFromMulin, String qty) {
		
		OmsProduct productDTO = new OmsProduct();
		
		// Basic product info
		productDTO.setSku(salesOrderItem.getSku());
		productDTO.setName(salesOrderItem.getName());
		productDTO.setQty(qty != null ? qty : parseNullStr(salesOrderItem.getQtyOrdered()));
		
		productDTO.setParentOrderItemId(salesOrderItem.getItemId());
		productDTO.setParentProductId(salesOrderItem.getSku());

		// Quantity fields from salesOrderItem
		productDTO.setQtyCanceled(parseNullStr(salesOrderItem.getQtyCanceled()));
		productDTO.setQtyInvoiced(parseNullStr(salesOrderItem.getQtyInvoiced()));
		productDTO.setQtyShipped(parseNullStr(salesOrderItem.getQtyShipped()));
		
		Map<Integer, SalesOrderItem> childItemsByParentId = order.getSalesOrderItem().stream()
		.filter(e -> e.getParentOrderItem() != null && e.getParentOrderItem().getItemId() != null)
		.collect(Collectors.toMap(e -> e.getParentOrderItem().getItemId(), e -> e, (e1, e2) -> e1));
		SalesOrderItem childOrderItem = childItemsByParentId.get(salesOrderItem.getItemId());

		if (childOrderItem != null) {
			QuantityReturned quantityReturned = getSalesQtyReturned(childOrderItem.getItemId(), false);
			productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
			productDTO.setQtyReturnedInProcess(parseNullStr(childOrderItem.getQtyRefunded()));
		}
		productDTO.setTaxAmount(parseNullStr(salesOrderItem.getTaxAmount()));
		productDTO.setBaseTaxAmount(parseNullStr(salesOrderItem.getBaseTaxAmount()));
		productDTO.setTaxPercentage(parseNullStr(salesOrderItem.getTaxPercent()));
		productDTO.setActualPrice(parseNullStr(salesOrderItem.getActualPrice()));
		
		productDTO.setOriginalPrice(parseNullStr(salesOrderItem.getOriginalPrice()));
		productDTO.setPrice(parseNullStr(salesOrderItem.getPrice()));
		
		productDTO.setRowTotal(parseNullStr(salesOrderItem.getRowTotal()));
		productDTO.setBaseRowTotal(parseNullStr(salesOrderItem.getBaseRowTotal()));
		productDTO.setRowTotalInclTax(parseNullStr(salesOrderItem.getRowTotalInclTax()));
		productDTO.setBaseRowTotalInclTax(parseNullStr(salesOrderItem.getBaseRowTotalInclTax()));
		productDTO.setSubtotal(parseNullStr(salesOrderItem.getRowTotal()));
		productDTO.setBaseSubtotal(parseNullStr(salesOrderItem.getBaseRowTotal()));
		
		BigDecimal finalPrice = Optional.ofNullable(salesOrderItem.getRowTotalInclTax()).orElse(BigDecimal.ZERO)
				.subtract(Optional.ofNullable(salesOrderItem.getDiscountAmount()).orElse(BigDecimal.ZERO));
		BigDecimal baseFinalPrice = Optional.ofNullable(salesOrderItem.getBaseRowTotalInclTax()).orElse(BigDecimal.ZERO)
				.subtract(Optional.ofNullable(salesOrderItem.getBaseDiscountAmount()).orElse(BigDecimal.ZERO));
		productDTO.setFinalPrice(parseNullStr(finalPrice));
		productDTO.setBaseFinalPrice(parseNullStr(baseFinalPrice));
		
		// Gift product flag
		if (null != salesOrderItem.getGiftMessageAvailable() && salesOrderItem.getGiftMessageAvailable().equals(1)) {
			productDTO.setGiftProduct(true);
		}
		
		// Brand name
		if (StringUtils.isNotBlank(salesOrderItem.getItemBrandName())) {
			productDTO.setBrandName(salesOrderItem.getItemBrandName());
		}
		
		// Return category restriction
		if (ObjectUtils.isNotEmpty(salesOrderItem.getReturnable())) {
			productDTO.setReturnCategoryRestriction(salesOrderItem.getReturnable() == 1 ? "false" : "true");
		}
		
		// Set product details from Mulin
		setDetailsFromMulin(productsFromMulin, salesOrderItem.getSku(), productDTO);
		
		// Set tax objects using order item data
		productDTO.setTaxObjects(getTaxObjects(salesOrderItem));
		productDTO.setHsnCode(parseNullStr(salesOrderItem.getHsnCode()));
		
		// Set option_id: for configurable products, product_id contains the option_id
		if (OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(salesOrderItem.getProductType())) {
			productDTO.setOptionId(salesOrderItem.getProductId());
		}
		
		return productDTO;
	}

		private OmsProduct buildCommonProductForSplitOrder(SplitSalesOrderItem salesOrderItem, SplitSalesOrder order, 
			Map<String, ProductResponseBody> productsFromMulin, String qty) {
		
		OmsProduct productDTO = new OmsProduct();
		
		// Basic product info
		productDTO.setSku(salesOrderItem.getSku());
		productDTO.setName(salesOrderItem.getName());
		productDTO.setQty(qty != null ? qty : parseNullStr(salesOrderItem.getQtyOrdered()));
		
		productDTO.setParentOrderItemId(salesOrderItem.getItemId());
		productDTO.setParentProductId(salesOrderItem.getSku());

		// Quantity fields from salesOrderItem
		productDTO.setQtyCanceled(parseNullStr(salesOrderItem.getQtyCanceled()));
		productDTO.setQtyInvoiced(parseNullStr(salesOrderItem.getQtyInvoiced()));
		productDTO.setQtyShipped(parseNullStr(salesOrderItem.getQtyShipped()));
		
		Map<Integer, SplitSalesOrderItem> childItemsByParentId = order.getSplitSalesOrderItems().stream()
		.filter(e -> e.getSplitSalesOrderItem() != null && e.getSplitSalesOrderItem().getItemId() != null)
		.collect(Collectors.toMap(e -> e.getSplitSalesOrderItem().getItemId(), e -> e, (e1, e2) -> e1));
		SplitSalesOrderItem childOrderItem = childItemsByParentId.get(salesOrderItem.getItemId());

		if (childOrderItem != null) {
			QuantityReturned quantityReturned = getSalesQtyReturned(childOrderItem.getItemId(), false);
			productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
			productDTO.setQtyReturnedInProcess(parseNullStr(childOrderItem.getQtyRefunded()));
		}
		productDTO.setTaxAmount(parseNullStr(salesOrderItem.getTaxAmount()));
		productDTO.setBaseTaxAmount(parseNullStr(salesOrderItem.getBaseTaxAmount()));
		productDTO.setTaxPercentage(parseNullStr(salesOrderItem.getTaxPercent()));
		productDTO.setActualPrice(parseNullStr(salesOrderItem.getWeeeTaxAppliedAmount()));
		
		productDTO.setOriginalPrice(parseNullStr(salesOrderItem.getOriginalPrice()));
		productDTO.setPrice(parseNullStr(salesOrderItem.getPrice()));
		
		productDTO.setRowTotal(parseNullStr(salesOrderItem.getRowTotal()));
		productDTO.setBaseRowTotal(parseNullStr(salesOrderItem.getBaseRowTotal()));
		productDTO.setRowTotalInclTax(parseNullStr(salesOrderItem.getRowTotalInclTax()));
		productDTO.setBaseRowTotalInclTax(parseNullStr(salesOrderItem.getBaseRowTotalInclTax()));
		productDTO.setSubtotal(parseNullStr(salesOrderItem.getRowTotal()));
		productDTO.setBaseSubtotal(parseNullStr(salesOrderItem.getBaseRowTotal()));
		
		BigDecimal finalPrice = Optional.ofNullable(salesOrderItem.getRowTotalInclTax()).orElse(BigDecimal.ZERO)
				.subtract(Optional.ofNullable(salesOrderItem.getDiscountAmount()).orElse(BigDecimal.ZERO));
		BigDecimal baseFinalPrice = Optional.ofNullable(salesOrderItem.getBaseRowTotalInclTax()).orElse(BigDecimal.ZERO)
				.subtract(Optional.ofNullable(salesOrderItem.getBaseDiscountAmount()).orElse(BigDecimal.ZERO));
		productDTO.setFinalPrice(parseNullStr(finalPrice));
		productDTO.setBaseFinalPrice(parseNullStr(baseFinalPrice));
		
		// Gift product flag
		if (null != salesOrderItem.getGiftMessageAvailable() && salesOrderItem.getGiftMessageAvailable().equals(1)) {
			productDTO.setGiftProduct(true);
		}
		
		// Brand name
		if (StringUtils.isNotBlank(salesOrderItem.getItemBrandName())) {
			productDTO.setBrandName(salesOrderItem.getItemBrandName());
		}
		
		// Return category restriction
		if (ObjectUtils.isNotEmpty(salesOrderItem.getReturnable())) {
			productDTO.setReturnCategoryRestriction(salesOrderItem.getReturnable() == 1 ? "false" : "true");
		}
		
		// Set product details from Mulin
		setDetailsFromMulin(productsFromMulin, salesOrderItem.getSku(), productDTO);
		
		// Set tax objects using order item data
		productDTO.setTaxObjects(getTaxObjects(salesOrderItem));
		productDTO.setHsnCode(parseNullStr(salesOrderItem.getHsnCode()));
		
		// Set option_id: for configurable products, product_id contains the option_id
		if (OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(salesOrderItem.getProductType())) {
			productDTO.setOptionId(salesOrderItem.getProductId());
		}
		
		return productDTO;
	}

    /**
     * Helper method to populate SKU status map from OTS (Order Tracking Service)
     * 
     * @param parentOrderId The parent order ID to fetch status for
     * @return Map containing SKU to status message list mappings
     */
    private Map<String, List<StatusMessage>> populateSkuStatusMapFromOTS(Integer parentOrderId) {
        Map<String, List<StatusMessage>> skuStatusMap = new HashMap<>();
        
        try {
            OrderStatusResponse orderStatusResponse = orderHelper.fetchOrderStatusOMS(parentOrderId);
            
            ExpressShipment expressShipment = orderStatusResponse.getData().getExpress();
            ExpressShipment globalShipment = orderStatusResponse.getData().getGlobal();

            // Process Express Shipment SKUs
            if (expressShipment != null && expressShipment.getSkus() != null) {
                expressShipment.getSkus().forEach(skuItem -> {
                    skuStatusMap.put(skuItem.getSku(), skuItem.getStatusMessage());
                });
            }
            
            // Process Global Shipment SKUs
            if (globalShipment != null && globalShipment.getSkus() != null) {
                globalShipment.getSkus().forEach(skuItem -> {
                    skuStatusMap.put(skuItem.getSku(), skuItem.getStatusMessage());
                });
            }
            
        } catch (Exception e) {
            LOGGER.error("Error fetching order status from OTS for parentOrderId: " + parentOrderId, e);
        }
        
        return skuStatusMap;
    }

    /**
     * Helper method to create OrderHistory list from status messages for a specific SKU
     * 
     * @param skuStatusMap Map containing SKU to status message list mappings
     * @param sku The SKU to get status messages for
     * @return List of OrderHistory objects created from status messages
     */
    private List<OrderHistory> createOrderHistoryFromStatusMessages(Map<String, List<StatusMessage>> skuStatusMap, String sku) {
        List<OrderHistory> historyList = new ArrayList<>();
        
        // Check if the SKU exists in the status map and has status messages
        if (skuStatusMap != null && skuStatusMap.containsKey(sku) && skuStatusMap.get(sku) != null) {
            skuStatusMap.get(sku).forEach(statusMessage -> {
                OrderHistory history = new OrderHistory();
                history.setStatus(statusMessage.getStatusId().toString());
                history.setMessage(statusMessage.getMessage());
                history.setDate(statusMessage.getTimestamp().toString());
                historyList.add(history);
            });
        }
        
        return historyList;
    }

	public String parseNullBigDecimalToStr(BigDecimal val) {
		return (val == null) ? null : String.valueOf(val.setScale(2, RoundingMode.HALF_UP));
	}

	private void setSellerInfo(OmsProduct productDTO, SalesOrderItem salesOrderItem) {
		String sku = salesOrderItem.getSku();
		List<SplitSellerOrderItem> matchingSellerItems = salesOrderItem.getSplitSellerOrderItems().stream()
					.filter(item -> item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE) && sku.equals(item.getSku()))
					.toList();

		if (!matchingSellerItems.isEmpty()) {
			List<SellerOrderInfo> sellerOrderInfos = new ArrayList<>(matchingSellerItems.stream()
					.collect(Collectors.toMap(
								item -> item.getSplitSellerOrder().getEntityId() + "_" + item.getSplitSellerOrder().getIncrementId(),
								item -> new SellerOrderInfo(item.getSplitSellerOrder().getEntityId(), item.getSplitSellerOrder().getIncrementId()),
								(existing, replacement) -> existing,
								LinkedHashMap::new
					)).values());

			productDTO.setSellerId(matchingSellerItems.get(0).getSellerId());
			productDTO.setSellerName(matchingSellerItems.get(0).getSellerName());
			productDTO.setSellerOrderInfo(sellerOrderInfos);
			String whId = matchingSellerItems.get(0).getWarehouseId();
				if(whId != null && !whId.isEmpty() && !whId.equals("110")) {
					productDTO.setHasSellerOrder(true);
				}
			findIsDangerousProduct(productDTO,salesOrderItem);
		}
	}
	private void findIsDangerousProduct(OmsProduct productDTO, SalesOrderItem salesOrderItem) {
		try {
			String productAttributesJson = salesOrderItem.getProductAttributes();
			if (productAttributesJson == null || productAttributesJson.isEmpty()) {
				productDTO.setIsDangerousProduct(false);
				return;
			}
			Map<String, String> productAttributes = mapper.readValue(productAttributesJson, new TypeReference<Map<String, String>>() {});
			// Extract is_dangerous_product
			productDTO.setIsDangerousProduct(Boolean.parseBoolean(productAttributes.getOrDefault("is_dangerous_product", "false")));
		} catch (Exception e) {
			LOGGER.error("Error parsing product attributes for dangerous product flag: " + e.getMessage(),e);
			productDTO.setIsDangerousProduct(false);
		}
	}

	/**
	 * Creates split order details for old format response (with shipment-wise ETA)
	 * This method groups split orders by entity_id and creates OrderDetailsResponse objects with ETA
	 */
	private List<OrderDetailsResponse> createSplitOrderDetailsForOldFormat(SalesOrder order, List<SplitSalesOrder> splitSalesOrders, Stores store, List<OrderHistory> histories) {
		List<OrderDetailsResponse> ordersList = new ArrayList<>();
		
		// Group split orders by entity_id (each entity_id represents a shipment: L1 or G1)
		Map<Integer, List<SplitSalesOrder>> splitOrdersByEntityId = splitSalesOrders.stream()
			.collect(Collectors.groupingBy(SplitSalesOrder::getEntityId));
		
		for (Map.Entry<Integer, List<SplitSalesOrder>> entry : splitOrdersByEntityId.entrySet()) {
			Integer entityId = entry.getKey();
			List<SplitSalesOrder> splitOrdersForEntity = entry.getValue();
			
			// Use the first split order for order details (they should have same entity_id)
			SplitSalesOrder firstSplitOrder = splitOrdersForEntity.get(0);
			
			LOGGER.info("createSplitOrderDetailsForOldFormat: Processing split order entityId: " + entityId + ", incrementId: " + firstSplitOrder.getIncrementId());
			
			OrderDetailsResponse orderDetails = createBasicOrderDetailsResponse(entityId, firstSplitOrder.getIncrementId(), order);
			
			// Set split_order_id (entity_id from split_sales_order)
			orderDetails.setSplitOrderId(entityId);
			LOGGER.info("createSplitOrderDetailsForOldFormat: Set splitOrderId: " + entityId);
			
			// Fetch and set estimated_delivery_date for this split order (shipment)
			String estimatedDeliveryDate = fetchEstimatedDeliveryDateForShipment(entityId);
			if (StringUtils.isNotBlank(estimatedDeliveryDate)) {
				orderDetails.setEstimatedDeliveryDate(estimatedDeliveryDate);
				LOGGER.info("createSplitOrderDetailsForOldFormat: Set estimatedDeliveryDate: " + estimatedDeliveryDate + " for splitOrderId: " + entityId);
			} else {
				LOGGER.info("createSplitOrderDetailsForOldFormat: No estimatedDeliveryDate found for splitOrderId: " + entityId);
			}
			
			orderDetails.setIsInvoiceGenerated(!firstSplitOrder.getSplitSalesInvoices().isEmpty());
			orderDetails.setHistories(histories);
			
			// Set totals for this split order group
			OrderTotal splitTotals = new OrderTotal();
			setSplitOrderTotals(firstSplitOrder, splitTotals, store);
			orderDetails.setTotals(splitTotals);
			
			// Products are already set in resp.getProducts() by setProductForSplitOrderOldFormat
			// So we don't need to set products here - they're already in the main response
			orderDetails.setProducts(new ArrayList<>());
			
			ordersList.add(orderDetails);
		}
		
		return ordersList;
	}

	/**
	 * Fetches estimated delivery date from split_sales_order_item table for a shipment (split order)
	 * All products in the same shipment will have the same ETA
	 * @param splitOrderId Split order ID (entity_id from split_sales_order = split_order_id in split_sales_order_item)
	 * @return Estimated delivery date as string, or null if not found
	 */
	private String fetchEstimatedDeliveryDateForShipment(Integer splitOrderId) {
		LOGGER.info("fetchEstimatedDeliveryDateForShipment: Starting fetch for splitOrderId: " + splitOrderId);
		
		if (splitOrderId == null) {
			LOGGER.warn("fetchEstimatedDeliveryDateForShipment: splitOrderId is null, cannot fetch ETA");
			return null;
		}
		
		try {
			// Get all split sales order items for this split order (shipment)
			// splitOrderId = entity_id from split_sales_order = split_order_id in split_sales_order_item
			LOGGER.info("fetchEstimatedDeliveryDateForShipment: Fetching split sales order items for splitOrderId: " + splitOrderId);
			List<SplitSalesOrderItem> splitOrderItems = splitSalesOrderItemRepository.findBySplitSalesOrderEntityId(splitOrderId);
			
			if (splitOrderItems == null || splitOrderItems.isEmpty()) {
				LOGGER.info("fetchEstimatedDeliveryDateForShipment: No split sales order items found for splitOrderId: " + splitOrderId);
				return null;
			}
			
			LOGGER.info("fetchEstimatedDeliveryDateForShipment: Found " + splitOrderItems.size() + " split sales order item(s) for shipment");
			
			// Get ETA from the first item that has estimated_delivery_date (all items in same shipment should have same ETA)
			// If multiple items have different ETAs, we take the latest one
			Timestamp latestEstimatedDate = null;
			for (SplitSalesOrderItem item : splitOrderItems) {
				Timestamp estimatedDeliveryDate = item.getEstimatedDeliveryDate();
				if (estimatedDeliveryDate != null) {
					if (latestEstimatedDate == null || estimatedDeliveryDate.after(latestEstimatedDate)) {
						latestEstimatedDate = estimatedDeliveryDate;
						LOGGER.info("fetchEstimatedDeliveryDateForShipment: Found ETA in item ID: " + item.getItemId() + ", ETA: " + estimatedDeliveryDate);
					}
				}
			}
			
			if (latestEstimatedDate != null) {
				// Format timestamp to string
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String formattedDate = dateFormat.format(latestEstimatedDate);
				LOGGER.info("fetchEstimatedDeliveryDateForShipment: Returning ETA for shipment (splitOrderId: " + splitOrderId + "): " + formattedDate);
				return formattedDate;
			} else {
				LOGGER.info("fetchEstimatedDeliveryDateForShipment: No estimated_delivery_date found in any items for splitOrderId: " + splitOrderId);
				return null;
			}
			
		} catch (Exception e) {
			LOGGER.error("fetchEstimatedDeliveryDateForShipment: Error fetching ETA for splitOrderId: " + splitOrderId + ". Error: " + e.getMessage(), e);
			return null;
		}
	}
}


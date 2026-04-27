package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.SalesOrder.SellerCommissionDetails;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.eas.EASPartialCancelRefundResponse;
import org.styli.services.order.pojo.request.Address;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.sellercentral.SellerCentralOrder;
import org.styli.services.order.pojo.sellercentral.SellerCentralOrderAddress;
import org.styli.services.order.pojo.sellercentral.SellerCentralOrderItem;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.EmailService;
import org.styli.services.order.service.SellerConfigService;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.PubSubServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.PaymentUtility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class OrderpushHelper {

	
	 private static final Log LOGGER = LogFactory.getLog(OrderpushHelper.class);
	 private static final String SIMPLE_PRODUCT_TYPE= "simple";
	 private static final String DEFAULT_SELLER_ID = "0001";
	 private static final String DEFAULT_SELLER_NAME = "styli";
	 private static final String STYLI_WAREHOUSE_ID = "110";
	 private static final ObjectMapper mapper = new ObjectMapper();


	    @Autowired
	    SalesOrderRepository salesOrderRepository;

		@Autowired
		SplitSalesOrderRepository splitSalesOrderRepository;

        @Autowired
        SplitSellerOrderRepository splitSellerOrderRepository;

		@Autowired
		SellerConfigRepository sellerConfigRepository;

		@Autowired
		SellerConfigService sellerConfigService;

		@Autowired
		CommonServiceImpl commonService;

	    @Autowired
	    @Qualifier("withoutEureka")
	    private RestTemplate restTemplate;

	    @Autowired
	    PaymentRefundHelper paymentDtfRefundHelper;
	    
	    @Autowired
	    SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;
	    
        @Autowired
		SubSalesOrderItemRepository subSalesOrderItemRepository;

		@Autowired
	    OmsOrderoutboundresponse omsOrderoutboundresponse;

		@Autowired
		SubSalesOrderRepository subSalesOrderRepository;
	    
	    @Autowired
	    OrderHelper orderHelper;
	    
		@Autowired
		OrderShipmentHelper orderShipmentHelper;
	    
	    @Autowired
		StaticComponents staticComponents;
	    
	    @Autowired
	    RefundHelper refundHelper;
	    
	    @Autowired
	    SalesOrderGridRepository salesOrderGridRepository;
	    
	    @Autowired
	    private PaymentUtility paymentUtility;
	    
	    @Autowired
		TabbyHelper tabbyHelper;
		
		@Autowired
		TamaraHelper tamaraHelper;
		
		@Autowired
		ProxyOrderRepository proxyOrderRepository;

    @Autowired
    private SellerCommissionDetailsRepository sellerCommissionDetailsRepository;



    @Autowired
    SellerBackOrderRepository sellerBackOrderRepository;

    @Autowired
    SellerBackOrderItemRepository sellerBackOrderItemRepository;
		
	@Autowired
	@Lazy
	EASServiceImpl eASServiceImpl;
		
		@Autowired
		AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

		@Autowired
		SplitOrderpushHelper splitOrderpushHelper;
		
		@Autowired
		EmailService emailService;

		@Autowired
        SalesOrderServiceV2Impl salesOrderServiceV2Impl;
		
		@Value("${env}")
		private String env;

		@Autowired
		PubSubServiceImpl pubSubServiceImpl;

	@Value("${pubsub.topic.seller.central.create.order}")
	private String sellerCentralCreateOrderTopic;
	
	/**
	 * Retrieves or persists the owner seller ID for orders from the Styli warehouse.
	 * If the owner seller ID is already set, it returns it directly. Otherwise, for Styli
	 * warehouse orders, it looks up the original seller from a non-Styli warehouse order
	 * with the same SKU and persists the owner seller ID if that seller is configured
	 * to push to Seller Central.
	 * 
	 * @param sellerOrder The split seller order to process
	 * @return The owner seller ID if found and applicable, null otherwise
	 */
	@Transactional
	public String getOrPersistOwnerSellerIdForStyliWH(SplitSellerOrder sellerOrder) {
		if(sellerOrder.getOwnerSellerId() != null) {
			return sellerOrder.getOwnerSellerId();
		}

		SplitSellerOrderItem splitSellerOrderItem = sellerOrder.getSplitSellerOrderItems().stream().findFirst().orElse(null);
		if(splitSellerOrderItem == null) {
			return null;
		}
		String sku = splitSellerOrderItem.getSku();
		String warehouseId = sellerOrder.getWarehouseId();
		
		if(!STYLI_WAREHOUSE_ID.equals(warehouseId)) {
			return null;
		}

		SplitSellerOrder splitSellerOrder = splitSellerOrderRepository.findByNoStyliWarehouseAndSku(DEFAULT_SELLER_ID, sku);
		
		if(splitSellerOrder == null) {
			return null;
		}

		SellerConfig sellerConfig = sellerConfigService.getAllSellerConfigs().stream()
			.filter(config -> config.getSellerId().equals(splitSellerOrder.getSellerId()))
			.findFirst()
			.orElse(null);

		if(sellerConfig == null) {
			return null;
		}

		boolean isPushToSellerCentral = sellerConfig.getBasicSettings() != null && Boolean.TRUE.equals(sellerConfig.getBasicSettings().getPushToSellerCentral());

		if(isPushToSellerCentral) {
			sellerOrder.setOwnerSellerId(splitSellerOrder.getSellerId());
			splitSellerOrderRepository.saveAndFlush(sellerOrder);
			return sellerOrder.getOwnerSellerId();
		}
		return null;
    }

	/**
	 * Batch version of getOrPersistOwnerSellerIdForStyliWH.
	 * Processes multiple seller orders at once to reduce database calls.
	 * 
	 * @param sellerOrders List of split seller orders to process
	 * @return Map of entityId to ownerSellerId for orders that have/need owner seller ID
	 */
	@Transactional
	public Map<Integer, String> getOrPersistOwnerSellerIdForStyliWHBatch(List<SplitSellerOrder> sellerOrders) {
		Map<Integer, String> result = new HashMap<>();
		
		if (CollectionUtils.isEmpty(sellerOrders)) {
			return result;
		}

		// Pre-fetch all seller configs once
		List<SellerConfig> allSellerConfigs = sellerConfigService.getAllSellerConfigs();
		Map<String, SellerConfig> sellerConfigMap = allSellerConfigs.stream()
			.filter(config -> config.getSellerId() != null)
			.collect(Collectors.toMap(SellerConfig::getStyliWarehouseId, config -> config, (a, b) -> a));

		// Separate orders that already have ownerSellerId vs those that need lookup
		Map<String, SplitSellerOrder> skuToOrderMap = new HashMap<>();

		for (SplitSellerOrder sellerOrder : sellerOrders) {
			// If already has ownerSellerId, add to result directly
			if (sellerOrder.getOwnerSellerId() != null) {
				result.put(sellerOrder.getEntityId(), sellerOrder.getOwnerSellerId());
				continue;
			}

			// Only process Styli warehouse orders
			if (!STYLI_WAREHOUSE_ID.equals(sellerOrder.getWarehouseId())) {
				continue;
			}

			if(sellerOrder.getSplitSellerOrderItems() != null) {
				for(SplitSellerOrderItem item : sellerOrder.getSplitSellerOrderItems()) {
					if (item.getSku() != null) {
						skuToOrderMap.put(item.getSku(), sellerOrder);
					}
				}
			}
		}

		if (skuToOrderMap.isEmpty()) {
			return result;
		}

		// Batch query: find all non-Styli warehouse orders matching these SKUs (100 at a time)
		List<String> skus = new ArrayList<>(skuToOrderMap.keySet());
		List<SplitSellerOrder> matchingOrders = new ArrayList<>();
		
		int batchSize = Constants.orderCredentials.getSkuBatchSizeForMatchingOrders();
		for (int i = 0; i < skus.size(); i += batchSize) {
			List<String> skuBatch = skus.subList(i, Math.min(i + batchSize, skus.size()));
			List<SplitSellerOrder> batchResults = splitSellerOrderRepository.findByNoStyliWarehouseAndSkuIn(DEFAULT_SELLER_ID, skuBatch);
			matchingOrders.addAll(batchResults);
		}
		LOGGER.info("[OrderpushHelper] Fetched matching orders for " + skus.size() + " SKUs in " + ((skus.size() + batchSize - 1) / batchSize) + " batches");

		// Build SKU to sellerId map from matching orders
		Map<String, String> skuToSellerIdMap = new HashMap<>();
		for (SplitSellerOrder matchingOrder : matchingOrders) {
			if (matchingOrder.getSplitSellerOrderItems() != null) {
				for (SplitSellerOrderItem item : matchingOrder.getSplitSellerOrderItems()) {
					if (item.getSku() != null && skuToOrderMap.containsKey(item.getSku())) {
						skuToSellerIdMap.put(item.getSku(), matchingOrder.getSellerId());
					}
				}
			}
		}

		// Process orders and batch update
		List<SplitSellerOrder> ordersToUpdate = new ArrayList<>();
		for (Map.Entry<String, SplitSellerOrder> entry : skuToOrderMap.entrySet()) {
			String sku = entry.getKey();
			SplitSellerOrder sellerOrder = entry.getValue();
			String matchedSellerId = skuToSellerIdMap.get(sku);

			if (matchedSellerId == null) {
				continue;
			}

			SellerConfig sellerConfig = sellerConfigMap.get(matchedSellerId);
			if (sellerConfig == null) {
				continue;
			}

			boolean isPushToSellerCentral = sellerConfig.getBasicSettings() != null 
				&& Boolean.TRUE.equals(sellerConfig.getBasicSettings().getPushToSellerCentral());

			if (isPushToSellerCentral) {
				sellerOrder.setOwnerSellerId(matchedSellerId);
				ordersToUpdate.add(sellerOrder);
				result.put(sellerOrder.getEntityId(), matchedSellerId);
			}
		}

		// Batch save all orders that need updating
		if (!ordersToUpdate.isEmpty()) {
			splitSellerOrderRepository.saveAll(ordersToUpdate);
			splitSellerOrderRepository.flush();
			LOGGER.info("[OrderpushHelper] Batch updated ownerSellerId for " + ordersToUpdate.size() + " Styli warehouse orders");
		}

		return result;
	}
	    
	    /**
	     * @param orderList
	     * @return
	     */
		@Transactional
        public OmsOrderresponsedto orderpushTowms(List<SalesOrder> orderList) {


            OmsOrderresponsedto response = new OmsOrderresponsedto();
            try {

                for(SalesOrder order : orderList) {

                    if(null != order.getStatus() && order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
                            && (order.getRetryPayment() == null || (null != order.getRetryPayment() && order.getRetryPayment().equals(0)) )) {

                        LOGGER.info("order is in payment pending: " + order.getIncrementId());
                        continue;
                    }

                    LOGGER.info("order push processing started: " + order.getStatus() + " WmsStatus: " + order.getWmsStatus() + ",orderId: " + order.getIncrementId());
                    OrderPushRequest orderPushRequest = new OrderPushRequest();

                    SalesOrderPayment orderPayment= order.getSalesOrderPayment().stream()
                            .findFirst().orElse(null);

                    if(null !=orderPayment && null != orderPayment.getMethod()
                            && orderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

                        orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_PAYMENT_COD);
                    } else {

                        orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_NOT_PAYMENT_COD);
                    }
                    /** convert order date format to WMS required date format **/
                    DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
                    Date createDate = new Date(order.getCreatedAt().getTime());

                    /** add dispatch delivery time **/
                    Date currentDate = new Date();
                    Calendar c = Calendar.getInstance();
                    c.setTime(currentDate);
                    if(null != Constants.orderCredentials.getWms()
                            && null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
                        c.add(Calendar.HOUR, Constants.orderCredentials.getWms().getWmsdispatchHoursNumber());
                    }

                    Date dispatchDeliveryDate = c.getTime();

                    orderPushRequest.setOrderType(OrderConstants.ORDER_PUSH_OMS_TYPE);
                    if(null != order.getSubSalesOrder().getWarehouseLocationId()) {
                        orderPushRequest.setLocationCode(order.getSubSalesOrder().getWarehouseLocationId().toString());
                    }else if(order.getStoreId().equals(1) || order.getStoreId().equals(3) &&
                            null != Constants.orderCredentials.getWms()
                            && null!= Constants.orderCredentials.getInventoryMapping().get(0)
                            && null != Constants.orderCredentials.getInventoryMapping().get(0).getWareHouseId()) {

                        orderPushRequest.setLocationCode(Constants.orderCredentials.getInventoryMapping().get(0).getWareHouseId());
                    }else if(CollectionUtils.isNotEmpty(Constants.orderCredentials.getInventoryMapping())
                            && null != Constants.orderCredentials.getInventoryMapping().get(1)) {

                        orderPushRequest.setLocationCode(Constants.orderCredentials.getInventoryMapping().get(1).getWareHouseId());
                    }
                    String orderTime = dateFormat.format(createDate).toString();
                    orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
                    String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
                    dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);

                    LOGGER.info("wms order time:"+orderTime);
                    LOGGER.info("wms dispatch time :"+dispatchTime);
                    orderPushRequest.setOrderTime(orderTime);
                    orderPushRequest.setStartProcessingTime(orderTime);
                    if(StringUtils.isNotBlank(order.getStatus())
                            &&  order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
                            && null != order.getSubSalesOrder().getRetryPayment() && order.getSubSalesOrder().getRetryPayment().equals(1) ) {
                        orderPushRequest.setOnHold(true);
                    }else {

                        orderPushRequest.setOnHold(false);
                    }
                    orderPushRequest.setQcStatus(OrderConstants.ORDER_PUSH_OMS_QC_STATUS);
                    orderPushRequest.setDispatchByTime(dispatchTime);
                    orderPushRequest.setOrderCode(order.getIncrementId());

                    List<SalesOrderItem> salesorderParentItem =
                            order.getSalesOrderItem().stream().filter(e-> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
                                    .collect(Collectors.toList());

                    if(CollectionUtils.isNotEmpty(salesorderParentItem)) {
                        List<OrderPushItem> orderPushItemsList = new ArrayList<>();

                        for(SalesOrderItem orderItem : salesorderParentItem) {


                            OrderPushItem orderPushItem = new OrderPushItem();

                            orderPushItem.setChannelSkuCode(orderItem.getSku());
                            orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
                            if(null !=orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() > 0) {

                                BigDecimal qytCancelled = orderItem.getQtyCanceled();
                                BigDecimal qtyOrdered = orderItem.getQtyOrdered();
                                BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);

                                orderPushItem.setQuantity(actualQty.intValue());
                                orderPushItem.setMrpPerUnit(orderItem.getPriceInclTax() != null ? orderItem.getPriceInclTax().intValue() : 0);

                            }else if(null != orderItem.getQtyOrdered()) {
                                orderPushItem.setQuantity(orderItem.getQtyOrdered().intValue());
                                orderPushItem.setMrpPerUnit(orderItem.getPriceInclTax() != null ? orderItem.getPriceInclTax().intValue() : 0);
                            }
                            orderPushItem.setSellerDiscountPerUnit(orderItem.getDiscountAmount());
							// SFP-209 don't send
							//orderPushItem.setSellingPricePerUnit(orderItem.getPoPrice() != null ? orderItem.getPoPrice().setScale(2, RoundingMode.DOWN) : BigDecimal.ZERO.setScale(2));
							// Don't send 0 selling price to WMS
							if (orderItem.getPoPrice() != null
									&& orderItem.getPoPrice().compareTo(BigDecimal.ZERO) > 0) {
								orderPushItem.setSellingPricePerUnit(orderItem.getPoPrice());
							} else {
								orderPushItem.setSellingPricePerUnit(new BigDecimal("1.00"));
							}
							orderPushItem.setShippingChargePerUnit(null);

                            GiftOption giftOption = new GiftOption();

                            giftOption.setGiftwrapRequired(false);
                            giftOption.setGiftMessage(false);

                            orderPushItem.setGiftOptions(giftOption);

                            orderPushItemsList.add(orderPushItem);
                        }

                        orderPushRequest.setOrderItems(orderPushItemsList);
                    }

                    SalesOrder updateOrder= salesOrderRepository.findByEntityId(order.getEntityId());

                    SubSalesOrder subSalesOrder = updateOrder.getSubSalesOrder();
                    if (null != subSalesOrder && Objects.nonNull(subSalesOrder.getFasterDelivery())){
                        orderPushRequest.setPriority(subSalesOrder.getFasterDelivery() == 1);
                    }

                    if(Objects.nonNull(updateOrder)) {
                        checkIfCancel(orderPushRequest, updateOrder);
                    }

                }

                response.setStatus(true);
                response.setStatusCode("200");
                response.setStatusMsg("pushed successfully");

            }catch (Exception e) {

                LOGGER.error("exception during push to oms:");
            }
            return response;
        }

		/**
	     * @param order
	     * @return
	     */
	@Transactional
    public List<SplitSellerOrder> orderpushToApparelWms(SalesOrder order) {
		try {
	    		if (shouldSkipOrder(order)) {
	    			return null;
	    		}
                if (order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
                    return createAndSaveSplitSellerOrder(order,null);
                }
	    
	    	}catch (Exception e) {
	    		LOGGER.error("[OrderpushHelper] exception during sales order apparel push to oms:", e);
			}
	    	return null;
	    }
	    
	    private boolean shouldSkipOrder(SalesOrder order) {
	    	return null != order.getStatus() && order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
	    			&& (order.getRetryPayment() == null || (null != order.getRetryPayment() && order.getRetryPayment().equals(0)));
	    }
	    
	    private OrderPushRequest buildOrderPushRequest(SalesOrder order) {
	    	OrderPushRequest orderPushRequest = new OrderPushRequest();
			setLocationFromInventory(order, orderPushRequest);
			if (StringUtils.isNotBlank(orderPushRequest.getLocationCode()) && CollectionUtils.isNotEmpty(orderPushRequest.getOrderItems())) {
				LOGGER.info("[OrderpushHelper] Mapped warehouse location found: " + orderPushRequest.getLocationCode() + " for order: " + order.getIncrementId());
			} else {
				LOGGER.warn("[OrderpushHelper] No mapped warehouse location found for order: " + order.getIncrementId() + ", skipping WMS push.");
				return null;
			}

	    	// Set payment method
	    	setPaymentMethod(order, orderPushRequest);
	    	
	    	// Set order times
	    	setOrderTimes(order, orderPushRequest);
	    	
	    	// Set order details
	    	setOrderDetails(order, orderPushRequest);
	    	
	    	return orderPushRequest;
	    }
	    
	    private void setPaymentMethod(SalesOrder order, OrderPushRequest orderPushRequest) {
	    	SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream()
	    			.findFirst().orElse(null);
	    	
	    	if (null != orderPayment && null != orderPayment.getMethod()
	    			&& orderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {
	    		orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_PAYMENT_COD);
	    	} else {
	    		orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_NOT_PAYMENT_COD);
	    	}
	    }
	    
	    private void setOrderTimes(SalesOrder order, OrderPushRequest orderPushRequest) {
	    	DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
	    	Date createDate = new Date(order.getCreatedAt().getTime());
	    	
	    	Date currentDate = new Date();
	    	Calendar c = Calendar.getInstance();
	    	c.setTime(currentDate);
	    	if (null != Constants.orderCredentials.getWms() 
	    			&& null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
	    		c.add(Calendar.HOUR, Constants.orderCredentials.getWms().getWmsdispatchHoursNumber());
	    	}
	    	
	    	Date dispatchDeliveryDate = c.getTime();
	    	
	    	String orderTime = dateFormat.format(createDate).toString();
	    	orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
	    	String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
	    	dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
	    	
	    	LOGGER.info("[OrderpushHelper] wms order time:" + orderTime);
	    	LOGGER.info("[OrderpushHelper] wms dispatch time :" + dispatchTime);
	    	
	    	orderPushRequest.setOrderTime(orderTime);
	    	orderPushRequest.setStartProcessingTime(orderTime);
	    	orderPushRequest.setDispatchByTime(dispatchTime);
	    }
	    
	    private void setLocationFromInventory(SalesOrder order, OrderPushRequest orderPushRequest) {

			 List<SalesOrderItem> salesOrderItems = order.getSalesOrderItem().stream()
					 .filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					 .filter(item -> null!=item.getWarehouseLocationId()).toList();
			 if(CollectionUtils.isEmpty(salesOrderItems)) {
				 LOGGER.warn("[OrderpushHelper] No valid sales order items with warehouse location found for order: " + order.getIncrementId());
				 return;
			 }
			List<OrderPushItem> orderPushItemsList = new ArrayList<>();
			for (SalesOrderItem salesOrderItem : salesOrderItems)	 {
				SellerConfig config = sellerConfigService.getSellerConfigForWarehouse(salesOrderItem.getWarehouseLocationId());
				if (config != null && config.getSellerWarehouseId() != null) {
					// Check pushToWms flag for both Database and Consul sources
					boolean shouldPush = config.getBasicSettings() != null && 
						Boolean.TRUE.equals(config.getBasicSettings().getPushToWms());
					if (shouldPush) {
						orderPushRequest.setLocationCode(config.getSellerWarehouseId());
						OrderPushItem orderPushItem = createOrderPushItem(salesOrderItem);
						orderPushItemsList.add(orderPushItem);
					}
				}
			}
			orderPushRequest.setOrderItems(orderPushItemsList);
	    }
	    
	    private void setOrderDetails(SalesOrder order, OrderPushRequest orderPushRequest) {
	    	orderPushRequest.setOrderType(OrderConstants.ORDER_PUSH_OMS_TYPE);
	    	orderPushRequest.setOrderCode(order.getIncrementId());
	    	
	    	if (StringUtils.isNotBlank(order.getStatus())
	    			&& order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
	    			&& null != order.getSubSalesOrder().getRetryPayment() && order.getSubSalesOrder().getRetryPayment().equals(1)) {
	    		orderPushRequest.setOnHold(true);
	    	} else {
	    		orderPushRequest.setOnHold(false);
	    	}
	    	
	    	orderPushRequest.setQcStatus(OrderConstants.ORDER_PUSH_OMS_QC_STATUS);
	    }

	    private OrderPushItem createOrderPushItem(SalesOrderItem orderItem) {
	    	OrderPushItem orderPushItem = new OrderPushItem();
	    	
	    	orderPushItem.setChannelSkuCode(orderItem.getSku());
	    	orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
	    	
	    	if (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() > 0) {
	    		BigDecimal qytCancelled = orderItem.getQtyCanceled();
	    		BigDecimal qtyOrdered = orderItem.getQtyOrdered();
	    		BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);
	    		
	    		orderPushItem.setQuantity(actualQty.intValue());
	    		orderPushItem.setMrpPerUnit(orderItem.getPriceInclTax() != null ? orderItem.getPriceInclTax().intValue() : 0);
	    	} else if (null != orderItem.getQtyOrdered()) {
	    		orderPushItem.setQuantity(orderItem.getQtyOrdered().intValue());
	    		orderPushItem.setMrpPerUnit(orderItem.getPriceInclTax() != null ? orderItem.getPriceInclTax().intValue() : 0);
	    	}
	    	
			
	    	orderPushItem.setSellerDiscountPerUnit(orderItem.getDiscountAmount());
			orderPushItem.setSellingPricePerUnit(new BigDecimal("1.00"));
	    	orderPushItem.setShippingChargePerUnit(null);
	    	
	    	GiftOption giftOption = new GiftOption();
	    	giftOption.setGiftwrapRequired(false);
	    	giftOption.setGiftMessage(false);
	    	orderPushItem.setGiftOptions(giftOption);
	    	
	    	return orderPushItem;
	    }
	    
	    private void processOrderPushRequest(SalesOrder order, OrderPushRequest orderPushRequest) {
	    	SalesOrder updateOrder = salesOrderRepository.findByEntityId(order.getEntityId());
	    	
	    	SubSalesOrder subSalesOrder = updateOrder.getSubSalesOrder();
	    	if (null != subSalesOrder && Objects.nonNull(subSalesOrder.getFasterDelivery())) {
	    		orderPushRequest.setPriority(subSalesOrder.getFasterDelivery() == 1);
	    	}
	    	
	    	if (Objects.nonNull(updateOrder)) {
	    		checkIfCancelApparel(orderPushRequest, updateOrder);
	    	}
	    }
	    
	    
	    private void checkIfCancel(OrderPushRequest orderPushRequest, SalesOrder updateOrder) {
			LOGGER.info("during push to oms:check the details of the order , if cancel or not! ");
			if((updateOrder.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS) ||
					updateOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS))
					&& (updateOrder.getWmsStatus() == null)) {
				setPushorderAddressdetails(orderPushRequest, updateOrder);
				restcallForwmsorderpush(orderPushRequest, updateOrder);
			}
		}

		private void checkIfCancelApparel(OrderPushRequest orderPushRequest, SalesOrder updateOrder) {
			LOGGER.info("[OrderpushHelper] during push to oms:check the details of the order , if cancel or not! ");
			if(updateOrder.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS) ||
					updateOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
				setPushorderAddressdetails(orderPushRequest, updateOrder);
				// Create and save SplitSellerOrder after successful WMS push
				List<SplitSellerOrder> splitSellerOrders = createAndSaveSplitSellerOrder(updateOrder,orderPushRequest);
				restcallForwmsorderpushApparel(orderPushRequest, updateOrder,splitSellerOrders);
			}
		}

		/**
		 * Creates and saves SplitSellerOrder with all details gathered from SalesOrder and associated tables
		 * @param salesOrder The main SalesOrder
		 */
		private List<SplitSellerOrder> createAndSaveSplitSellerOrder(SalesOrder salesOrder,OrderPushRequest orderPushRequest) {
			try {
				List<SplitSellerOrder> sellerOrders = new ArrayList<>();
                LOGGER.info("[OrderpushHelper] Creating SplitSellerOrder for SalesOrder: " + salesOrder.getIncrementId());
                Set<SalesOrderItem>  salesOrderItems= salesOrder.getSalesOrderItem();
				
				// Use service to get all seller configs based on config source (DB or Consul)
				List<SellerConfig> sellerInventoryMappings = sellerConfigService.getAllSellerConfigs();

				List<InventoryMapping> inventoryMappings = Constants.orderCredentials.getInventoryMapping();
				Set<String> invetoryWarehouseIds = Optional.ofNullable(inventoryMappings)
						.orElse(Collections.emptyList())
						.stream()
						.map(InventoryMapping::getWareHouseId)
						.collect(Collectors.toSet());
				//  Precompute mapped KEYS per (sellerId|warehouseId) where both flags are TRUE
				Set<String> mappedSellerKeys = sellerInventoryMappings.stream()
						.filter(config -> config.getBasicSettings() != null
								&& Boolean.TRUE.equals(config.getBasicSettings().getPushOrderForSku()))
						.map(config -> String.valueOf(config.getStyliWarehouseId()))  // normalize to String
						.collect(Collectors.toSet());
                //  Grouping: include SKU only if (sellerId|warehouseId) is mapped with both flags true
                Map<String, List<SalesOrderItem>> grouped = salesOrderItems.stream()
                        .collect(Collectors.groupingBy(item -> {
                            String warehouseId = item.getWarehouseLocationId();
                            String sku = item.getSku();
                            // Rule 1: if seller is mapped (and has both flags true) => include SKU
                            // Rule 2: if seller is not mapped => do NOT include SKU
                            boolean pairMapped = mappedSellerKeys.contains(warehouseId);
                            return pairMapped
                                    ? warehouseId + "|" + sku
                                    : warehouseId;
                        }));
				AtomicInteger sequenceNo = new AtomicInteger(1);
                for (Map.Entry<String, List<SalesOrderItem>> entry : grouped.entrySet()) {
                    String[] keys = entry.getKey().split("\\|");
                    String warehouseId = keys[0];
                    String skuKey       = (keys.length > 1) ? keys[1] : null; // present only when grouped by SKU
                    String backOrderSellerId = resolveSellerIdForWarehouse(warehouseId);
                    //If warehouse is mapped in seller mapping, push only those SKUs for each quantity
					if (StringUtils.isNotBlank(skuKey)) {
                        LOGGER.info("Back-order creation for sellerId=" + backOrderSellerId + ", warehouseId=" + warehouseId);
                        sellerOrders.addAll(buildSellerOrdersQuantity(salesOrder,entry,warehouseId,sequenceNo));
                        if (shouldCreateBackOrder(backOrderSellerId, warehouseId)) {
                            upsertSellerBackOrderAndItems(salesOrder.getSplitSellerOrders());
                        } else {
                            LOGGER.info("Skipping back-order creation for sellerId=" + backOrderSellerId + ", warehouseId=" + warehouseId + " as seller_config missing or is_b2b_seller != true");
                        }
                    }
					//If warehouse is mapped in inventory mapping but not in seller mapping, push all SKUs to that warehouse
					else if(invetoryWarehouseIds.contains(warehouseId)) {
                        LOGGER.info("Back-order creation for sellerId=" + backOrderSellerId + ", warehouseId=" + warehouseId);
						sellerOrders.addAll(buildSellerOrderForAllSkus(salesOrder,entry,warehouseId,sequenceNo));
                        if (shouldCreateBackOrder(backOrderSellerId, warehouseId)) {
                            upsertSellerBackOrderAndItems(salesOrder.getSplitSellerOrders());
                        } else {
                            LOGGER.info("Skipping back-order creation for sellerId=" + backOrderSellerId + ", warehouseId=" + warehouseId + " as seller_config missing or is_b2b_seller != true");
                        }
                    } else {
						// create seller orders per item
                        LOGGER.info("Back-order creation for sellerId=" + backOrderSellerId + ", warehouseId=" + warehouseId);
						sellerOrders.addAll(buildSellerOrdersPerItem(salesOrder,entry,warehouseId,sequenceNo));
                        if (shouldCreateBackOrder(backOrderSellerId, warehouseId)) {
                            upsertSellerBackOrderAndItems(salesOrder.getSplitSellerOrders());
                        } else {
                            LOGGER.info("Skipping back-order creation for sellerId=" + backOrderSellerId + ", warehouseId=" + warehouseId + " as seller_config missing or is_b2b_seller != true");
                        }
					}
                }
				pushToSellerCentral(sellerOrders);
				return sellerOrders;
			} catch (Exception e) {
				LOGGER.error("[OrderpushHelper] Error creating SplitSellerOrder for SalesOrder: " + salesOrder.getIncrementId(), e);
				throw new RuntimeException("Failed to create SplitSellerOrder for SalesOrder: " + salesOrder.getIncrementId(), e);
			}
		}

		private void pushToSellerCentral(List<SplitSellerOrder> sellerOrders) {
			try {
				// push seller order to pubsub topic for seller central
				List<SellerCentralOrder> sellerCentralOrders = new ArrayList<>();

				// Batch process: Get all owner seller IDs for Styli warehouse orders at once
				Map<Integer, String> ownerSellerIdMap = getOrPersistOwnerSellerIdForStyliWHBatch(sellerOrders);
				LOGGER.info("[OrderpushHelper] Batch fetched ownerSellerIds for " + ownerSellerIdMap.size() + " orders");

				// Pre-cache seller configs by warehouse ID to avoid repeated service calls
				Map<String, SellerConfig> warehouseConfigCache = new HashMap<>();
				for (SplitSellerOrder sellerOrder : sellerOrders) {
					String whId = sellerOrder.getWarehouseId();
					if (whId != null && !warehouseConfigCache.containsKey(whId)) {
						warehouseConfigCache.put(whId, sellerConfigService.getSellerConfigForWarehouse(whId));
					}
				}

				for (SplitSellerOrder sellerOrder: sellerOrders){
					// Use cached seller config
					SellerConfig mapping = warehouseConfigCache.get(sellerOrder.getWarehouseId());
					
					// Check if pushToSellerCentral flag is enabled
					boolean pushToSellerCentral = mapping != null && mapping.getBasicSettings() != null && 
						Boolean.TRUE.equals(mapping.getBasicSettings().getPushToSellerCentral());
			
					// Skip pending payment orders for all warehouses (including Styli warehouse 110)
					if (OrderConstants.PENDING_PAYMENT_ORDER_STATUS.equalsIgnoreCase(sellerOrder.getStatus())) {
						LOGGER.info("Skipping to push seller central for pending payment order. Warehouse: " + sellerOrder.getWarehouseId() + ", seller order id: " + sellerOrder.getEntityId());
						continue;
					}

					// Use batch-fetched owner seller ID instead of individual lookup
					String sellerIdForStyliWH = ownerSellerIdMap.get(sellerOrder.getEntityId());
			
					// Push if Styli warehouse OR if mapping exists with pushToSellerCentral flag
					if (sellerIdForStyliWH != null || pushToSellerCentral) {
						String warehouseId = mapping != null ? mapping.getStyliWarehouseId() : STYLI_WAREHOUSE_ID;
						String sellerId = mapping != null ? mapping.getSellerId() : sellerOrder.getSellerId();
						LOGGER.info("Pushing order to seller central for warehouse: " + warehouseId + " and sellerId: " + sellerId);
						SellerCentralOrder sellerCentralOrder = buildSellerCentralOrderPayload(sellerOrder, mapping, sellerIdForStyliWH);
						sellerCentralOrders.add(sellerCentralOrder);
					} else {
						LOGGER.warn("Skipping to push seller central for order status: " + sellerOrder.getStatus() + 
						" and warehouse: " + sellerOrder.getWarehouseId() + 
						" seller order id: " + sellerOrder.getEntityId() + 
								" (OR) as no valid mapping found or pushToSellerCentral is false.");
						continue;
					}

				}
				
				if(CollectionUtils.isNotEmpty(sellerCentralOrders)) {
					Map<String,Object> requestPayload = new HashMap<>();
					requestPayload.put("type","create");
					requestPayload.put("payload",sellerCentralOrders);

					LOGGER.info("Publishing split order pubsub for seller orders:  and pubsubPayload: " + mapper.writeValueAsString(requestPayload));
					pubSubServiceImpl.publishSellerCentralPubSub(sellerCentralCreateOrderTopic,requestPayload);
					sellerCentralOrders.stream().forEach(sellerCentralOrder ->
					{
						LOGGER.info("Published split seller order to seller central Pub/Sub for seller order increment id: " + sellerCentralOrder.getSellerOrderId());
						List<SplitSellerOrder> sellerOrderList = splitSellerOrderRepository.findBySellerOrdersByIncrementId(sellerCentralOrder.getSellerOrderId());
						if(CollectionUtils.isNotEmpty(sellerOrderList))
						{
							SplitSellerOrder splitSellerOrder = sellerOrderList.get(0);
							try {
								String timelines = null != sellerCentralOrder.getTimelines() ? mapper.writeValueAsString(sellerCentralOrder.getTimelines()) : null;
								//update timelines
								if (null != timelines){
									splitSellerOrderRepository.updateTimeLines(timelines,splitSellerOrder.getEntityId());
								}
							} catch (JsonProcessingException e) {
								LOGGER.error("Error while converting timelines to string for seller order increment id: " + sellerCentralOrder.getSellerOrderId(), e);
							}
							//update wms status to 1 - pushed to seller central
							splitSellerOrderRepository.updateWMSStatus(1,splitSellerOrder.getEntityId());

							LOGGER.info("Updated SplitSellerOrder sellerCentralPushStatus to 1 for seller order increment id: " + sellerCentralOrder.getSellerOrderId());
						}
					});
				} else {
					LOGGER.warn("No valid seller central orders to push, skipping pub/sub publish.");
				}
			}catch (Exception e) {
				LOGGER.error("Error in publishing split order to seller central Pub/Sub", e);
			}
		}

        private List<SplitSellerOrder> buildSellerOrderForAllSkus(SalesOrder salesOrder,Map.Entry<String, List<SalesOrderItem>> entry,String warehouseId,AtomicInteger sequenceNo) {
		List<SplitSellerOrder> sellerOrders = new ArrayList<>();
		Timestamp now = new Timestamp(System.currentTimeMillis());
		
		// Use service to get seller config and extract sellerId/sellerName
		SellerInfo sellerInfo = sellerConfigService.getSellerIdAndNameForWarehouse(warehouseId);
		final String sellerId = sellerInfo.getSellerId();
		final String sellerName = sellerInfo.getSellerName();
		
		LOGGER.info("[OrderpushHelper] Creating SplitSellerOrder buildSellerOrderForAllSkus sellerId : " + sellerId);

			// Create SplitSellerOrder
			SplitSellerOrder splitSellerOrder = new SplitSellerOrder();

			// Set basic order information
			splitSellerOrder.setStatus(salesOrder.getStatus());
			splitSellerOrder.setWmsStatus(0); // Set to 1 after successful WMS push
			splitSellerOrder.setWmsPullStatus(0);
			splitSellerOrder.setExtOrderId(salesOrder.getExtOrderId());
			// Generate increment ID following pattern: {salesOrderIncrementId}-{shipmentType}-{sellerId}-{sequenceNumber}
			String shipmentType = "L1"; // L1 for local, G1 for global - using L1 for apparel orders

			// Create increment ID with or without sellerId
			String incrementId;
			int nextSeq = sequenceNo.getAndIncrement();
			if (sellerId != null) {
				incrementId = salesOrder.getIncrementId() + "-" + shipmentType + "-" + sellerId + "-"+nextSeq;
			} else {
				// If sellerId is null, create increment ID without sellerId part
				incrementId = salesOrder.getIncrementId() + "-" + shipmentType + "-"+nextSeq;
				LOGGER.warn("[OrderpushHelper] sellerId is null for SalesOrder: " + salesOrder.getIncrementId() + ", creating increment ID without sellerId: " + incrementId);
			}

			splitSellerOrder.setIncrementId(incrementId);
			splitSellerOrder.setSellerId(sellerId);
			splitSellerOrder.setWarehouseId(warehouseId);
			splitSellerOrder.setShipmentMode(Constants.LOCAL_SHIPMENT);
			splitSellerOrder.setHasGlobalShipment(false);
			splitSellerOrder.setEstimateDelivery(null!=salesOrder.getEstimatedDeliveryTime()?salesOrder.getEstimatedDeliveryTime():null);
			splitSellerOrder.setCreatedAt(now);
			splitSellerOrder.setUpdatedAt(now);

			// Set relationships
			splitSellerOrder.setSalesOrder(salesOrder);

			List<SalesOrderItem> groupedItems =  entry.getValue().stream()
					.sorted(Comparator.comparing(SalesOrderItem::getItemId))
					.collect(Collectors.toList());
			for (SalesOrderItem salesOrderItem :   groupedItems) {
				SplitSellerOrderItem splitSellerOrderItem = new SplitSellerOrderItem();
				splitSellerOrderItem.setSku(salesOrderItem.getSku());
				splitSellerOrderItem.setProductType(salesOrderItem.getProductType());
				splitSellerOrderItem.setQtyOrdered(salesOrderItem.getQtyOrdered());
				splitSellerOrderItem.setQtyCanceled(salesOrderItem.getQtyCanceled());
				splitSellerOrderItem.setQtyShipped(salesOrderItem.getQtyShipped());
				splitSellerOrderItem.setSellerId(sellerId);
				splitSellerOrderItem.setSellerName(sellerName);
				splitSellerOrderItem.setWarehouseId(warehouseId);
				splitSellerOrderItem.setStoreId(salesOrder.getStoreId());
				splitSellerOrderItem.setCreatedAt(new Timestamp(new Date().getTime()));
				splitSellerOrderItem.setUpdatedAt(new Timestamp(new Date().getTime()));

				// Set relationships
				splitSellerOrderItem.setSplitSellerOrder(splitSellerOrder);
				splitSellerOrderItem.setSalesOrderItem(salesOrderItem);
				splitSellerOrderItem.setMainOrder(salesOrder);
				// Set warehouse details
				splitSellerOrder.setFirstMileWarehouseName(salesOrderItem.getFirstMileFcName());
				splitSellerOrder.setMidmileWarehouseId(salesOrderItem.getMidMileFc());
				splitSellerOrder.setMidmileWarehouseName(salesOrderItem.getMidMileFcName());
				splitSellerOrder.setLastmileWarehouseId(salesOrderItem.getLastMileFc());
				splitSellerOrder.setLastmileWarehouseName(salesOrderItem.getLastMileFcName());

				if (salesOrderItem.getParentOrderItem() != null) {
					Optional<SplitSellerOrderItem> parentItemOptional = splitSellerOrder.getSplitSellerOrderItems().stream()
							.filter(splitSellOrderItem ->
									splitSellOrderItem.getSku().equals(salesOrderItem.getParentOrderItem().getSku())
							)
							.findFirst();
					parentItemOptional.ifPresent(splitSellerOrderItem::setSplitSellerOrderItem);
				}
				splitSellerOrder.addSplitSellerOrderItem(splitSellerOrderItem);
			}
			// Create SplitSubSellerOrder
			SplitSubSellerOrder splitSubSellerOrder = new SplitSubSellerOrder();
			splitSubSellerOrder.setSalesOrder(salesOrder);
			splitSubSellerOrder.setSplitSellerOrder(splitSellerOrder);
			splitSubSellerOrder.setShipmentMode(Constants.LOCAL_SHIPMENT);
			splitSubSellerOrder.setHasGlobalShipment(false);
			splitSubSellerOrder.setPaymentId(salesOrder.getSubSalesOrder() != null ? salesOrder.getSubSalesOrder().getPaymentId() : null);
			splitSubSellerOrder.setCreatedAt(new Timestamp(new Date().getTime()));
			splitSubSellerOrder.setUpdatedAt(new Timestamp(new Date().getTime()));

			splitSellerOrder.setSplitSubSellerOrders(Set.of(splitSubSellerOrder));

			List<SplitSellerOrder> existingList = splitSellerOrderRepository.findBySellerOrdersByIncrementId(incrementId);
			// Save SplitSellerOrder
			if (CollectionUtils.isNotEmpty(existingList)) {
				LOGGER.info("[OrderpushHelper] SplitSellerOrder with increment ID: " + incrementId + " already exists. Skipping save.");
			} else {
				SplitSellerOrder savedSplitSellerOrder = splitSellerOrderRepository.saveAndFlush(splitSellerOrder);
				sellerOrders.add(savedSplitSellerOrder);
				LOGGER.info("[OrderpushHelper]  buildSellerOrderForAllSkus created SplitSellerOrder with ID: " + savedSplitSellerOrder.getEntityId() +
						" for SalesOrder: " + salesOrder.getIncrementId());
                // Back-order creation gated by seller_config (is_b2b_seller = true)
                LOGGER.info("Back-order creation for sellerId=" + sellerId + ", warehouseId=" + warehouseId);
                if (shouldCreateBackOrder(sellerId, warehouseId)) {
                    upsertSellerBackOrderAndItems(salesOrder.getSplitSellerOrders());
                } else {
                    LOGGER.info("Skipping back-order creation for sellerId=" + sellerId + ", warehouseId=" + warehouseId + " as seller_config missing or is_b2b_seller != true");
                }
			}
			return sellerOrders;
		}

		private List<SplitSellerOrder> buildSellerOrdersQuantity (SalesOrder salesOrder,Map.Entry<String, List<SalesOrderItem>> entry,String warehouseId,AtomicInteger sequenceNo) {
            List<SplitSellerOrder> sellerOrders = new ArrayList<>();
            Timestamp now = new Timestamp(System.currentTimeMillis());

			
			// Use service to get seller config and extract sellerId/sellerName with fallback
			SellerInfo sellerInfo = sellerConfigService.getSellerIdAndNameForWarehouse(warehouseId);
			final String sellerId = sellerInfo.getSellerId();
			final String sellerName = sellerInfo.getSellerName();
            LOGGER.info("[OrderpushHelper] Creating SplitSellerOrder buildSellerOrdersQuantity sellerId : " + sellerId);

            List<SalesOrderItem> parentItems =   entry.getValue().stream()
                    .filter(item -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE
                            .equalsIgnoreCase(item.getProductType()))
                    .filter(e -> null!=e.getWarehouseLocationId()
                            && e.getWarehouseLocationId().equalsIgnoreCase(warehouseId))
                    .sorted(Comparator.comparing(SalesOrderItem::getItemId))
                    .toList();
            List<SalesOrderItem> groupedItems =   entry.getValue().stream()
                    .filter(e -> null!=e.getWarehouseLocationId()
                            && e.getWarehouseLocationId().equalsIgnoreCase(warehouseId))
                    .sorted(Comparator.comparing(SalesOrderItem::getItemId))
                    .toList();
            for (SalesOrderItem parentItem: parentItems) {
                if (parentItem.getQtyOrdered() != null && parentItem.getQtyOrdered().compareTo(BigDecimal.ZERO) > 0) {
                    for (int i = 0; i < parentItem.getQtyOrdered().intValue(); i++) {
                        LOGGER.info("[OrderpushHelper] Parent Item SKU: " + parentItem.getSku() + ", Item ID: " + parentItem.getItemId() +
                                ", Qty Ordered: " + parentItem.getQtyOrdered() + ", Processing unit: " + (i + 1));

                        // Create SplitSellerOrder
                        SplitSellerOrder splitSellerOrder = new SplitSellerOrder();

                        // Set basic order information
                        splitSellerOrder.setStatus(salesOrder.getStatus());
                        splitSellerOrder.setWmsStatus(0); // Set to 1 after successful WMS push
                        splitSellerOrder.setWmsPullStatus(0);
                        splitSellerOrder.setExtOrderId(salesOrder.getExtOrderId());
                        // Generate increment ID following pattern: {salesOrderIncrementId}-{shipmentType}-{sellerId}-{sequenceNumber}
                        String shipmentType = "L1"; // L1 for local, G1 for global - using L1 for apparel orders

                        // Create increment ID with or without sellerId
                        String incrementId;
						int nextSeq = sequenceNo.getAndIncrement();
                        if (sellerId != null) {
                            incrementId = salesOrder.getIncrementId() + "-" + shipmentType + "-" + sellerId + "-" + nextSeq;
                        } else {
                            // If sellerId is null, create increment ID without sellerId part
                            incrementId = salesOrder.getIncrementId() + "-" + shipmentType + "-" + nextSeq;
                            LOGGER.warn("[OrderpushHelper] sellerId is null for SalesOrder: " + salesOrder.getIncrementId() + ", creating increment ID without sellerId: " + incrementId);
                        }

                        splitSellerOrder.setIncrementId(incrementId);
                        splitSellerOrder.setSellerId(sellerId);
                        splitSellerOrder.setWarehouseId(warehouseId);
                        splitSellerOrder.setShipmentMode(Constants.LOCAL_SHIPMENT);
                        splitSellerOrder.setHasGlobalShipment(false);
						splitSellerOrder.setEstimateDelivery(null!=salesOrder.getEstimatedDeliveryTime()?salesOrder.getEstimatedDeliveryTime():null);
                        splitSellerOrder.setCreatedAt(now);
                        splitSellerOrder.setUpdatedAt(now);

                        // Set relationships
                        splitSellerOrder.setSalesOrder(salesOrder);

                        List<SalesOrderItem>  skuItems= groupedItems.stream().filter(item -> item.getSku().equals(parentItem.getSku()))
                                .sorted(Comparator.comparing(SalesOrderItem::getItemId)).toList();
                        for (SalesOrderItem salesOrderItem : skuItems) {
                            SplitSellerOrderItem splitSellerOrderItem = new SplitSellerOrderItem();
                            splitSellerOrderItem.setSku(salesOrderItem.getSku());
                            splitSellerOrderItem.setProductType(salesOrderItem.getProductType());
                            splitSellerOrderItem.setQtyOrdered(BigDecimal.ONE); // Set to 1 as we're creating one item per iteration
                            splitSellerOrderItem.setQtyCanceled(salesOrderItem.getQtyCanceled());
                            splitSellerOrderItem.setQtyShipped(salesOrderItem.getQtyShipped());
                            splitSellerOrderItem.setSellerId(sellerId);
                            splitSellerOrderItem.setSellerName(sellerName);
                            splitSellerOrderItem.setWarehouseId(warehouseId);
                            splitSellerOrderItem.setStoreId(salesOrder.getStoreId());
                            splitSellerOrderItem.setCreatedAt(new Timestamp(new Date().getTime()));
                            splitSellerOrderItem.setUpdatedAt(new Timestamp(new Date().getTime()));

                            // Set relationships
                            splitSellerOrderItem.setSplitSellerOrder(splitSellerOrder);
                            splitSellerOrderItem.setSalesOrderItem(salesOrderItem);
							// Set warehouse details
							splitSellerOrder.setFirstMileWarehouseName(salesOrderItem.getFirstMileFcName());
							splitSellerOrder.setMidmileWarehouseId(salesOrderItem.getMidMileFc());
							splitSellerOrder.setMidmileWarehouseName(salesOrderItem.getMidMileFcName());
							splitSellerOrder.setLastmileWarehouseId(salesOrderItem.getLastMileFc());
							splitSellerOrder.setLastmileWarehouseName(salesOrderItem.getLastMileFcName());
                            splitSellerOrderItem.setMainOrder(salesOrder);

							if (salesOrderItem.getParentOrderItem() != null) {
								Optional<SplitSellerOrderItem> parentItemOptional = splitSellerOrder.getSplitSellerOrderItems().stream()
										.filter(splitSellOrderItem ->
												splitSellOrderItem.getSku().equals(salesOrderItem.getParentOrderItem().getSku())
										)
										.findFirst();
								parentItemOptional.ifPresent(splitSellerOrderItem::setSplitSellerOrderItem);
							}
							splitSellerOrder.addSplitSellerOrderItem(splitSellerOrderItem);
                        }

                        // Create SplitSubSellerOrder
                        SplitSubSellerOrder splitSubSellerOrder = new SplitSubSellerOrder();
                        splitSubSellerOrder.setSalesOrder(salesOrder);
                        splitSubSellerOrder.setSplitSellerOrder(splitSellerOrder);
                        splitSubSellerOrder.setShipmentMode(Constants.LOCAL_SHIPMENT);
                        splitSubSellerOrder.setHasGlobalShipment(false);
                        splitSubSellerOrder.setPaymentId(salesOrder.getSubSalesOrder() != null ? salesOrder.getSubSalesOrder().getPaymentId() : null);
                        splitSubSellerOrder.setCreatedAt(new Timestamp(new Date().getTime()));
                        splitSubSellerOrder.setUpdatedAt(new Timestamp(new Date().getTime()));

                        splitSellerOrder.setSplitSubSellerOrders(Set.of(splitSubSellerOrder));

						List<SplitSellerOrder> existingList = splitSellerOrderRepository.findBySellerOrdersByIncrementId(incrementId);
						// Save SplitSellerOrder
						if (CollectionUtils.isNotEmpty(existingList)) {
							LOGGER.info("[OrderpushHelper] SplitSellerOrder with increment ID: " + incrementId + " already exists. Skipping save.");
						} else {
							SplitSellerOrder savedSplitSellerOrder = splitSellerOrderRepository.saveAndFlush(splitSellerOrder);
							sellerOrders.add(savedSplitSellerOrder);
							LOGGER.info("[OrderpushHelper] buildSellerOrdersQuantity created SplitSellerOrder with ID: " + savedSplitSellerOrder.getEntityId() +
									" for SalesOrder: " + salesOrder.getIncrementId());
                            // Back-order creation gated by seller_config (is_b2b_seller = true)
                            if (shouldCreateBackOrder(sellerId, warehouseId)) {
                                upsertSellerBackOrderAndItems(salesOrder.getSplitSellerOrders());
                            } else {
                                LOGGER.info("Skipping back-order creation for sellerId=" + sellerId + ", warehouseId=" + warehouseId + " as seller_config missing or is_b2b_seller != true");
                            }
						}
                    }
                }
            }

            return sellerOrders;
        }

	private List<SplitSellerOrder> buildSellerOrdersPerItem (SalesOrder salesOrder,Map.Entry<String, List<SalesOrderItem>> entry,String warehouseId,AtomicInteger sequenceNo) {
		List<SplitSellerOrder> sellerOrders = new ArrayList<>();
		Timestamp now = new Timestamp(System.currentTimeMillis());

		// Use service to get seller config and extract sellerId/sellerName with fallback
		SellerInfo sellerInfo = sellerConfigService.getSellerIdAndNameForWarehouse(warehouseId);
		final String sellerId = sellerInfo.getSellerId();
		final String sellerName = sellerInfo.getSellerName();
		
		LOGGER.info("[OrderpushHelper] Creating SplitSellerOrder buildSellerOrdersQuantity sellerId : " + sellerId);

		List<SalesOrderItem> parentItems =   entry.getValue().stream()
				.filter(item -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE
						.equalsIgnoreCase(item.getProductType()))
				.filter(e -> null!=e.getWarehouseLocationId()
						&& e.getWarehouseLocationId().equalsIgnoreCase(warehouseId))
				.sorted(Comparator.comparing(SalesOrderItem::getItemId))
				.toList();
		List<SalesOrderItem> groupedItems =   entry.getValue().stream()
				.filter(e -> null!=e.getWarehouseLocationId()
						&& e.getWarehouseLocationId().equalsIgnoreCase(warehouseId))
				.sorted(Comparator.comparing(SalesOrderItem::getItemId))
				.toList();
		for (SalesOrderItem parentItem: parentItems) {
					LOGGER.info("[OrderpushHelper] Parent Item SKU: " + parentItem.getSku() + ", Item ID: " + parentItem.getItemId() +
							", Qty Ordered: " + parentItem.getQtyOrdered());

					// Create SplitSellerOrder
					SplitSellerOrder splitSellerOrder = new SplitSellerOrder();

					// Set basic order information
					splitSellerOrder.setStatus(salesOrder.getStatus());
					splitSellerOrder.setWmsStatus(0); // Set to 1 after successful WMS push
					splitSellerOrder.setWmsPullStatus(0);
					splitSellerOrder.setExtOrderId(salesOrder.getExtOrderId());
					// Generate increment ID following pattern: {salesOrderIncrementId}-{shipmentType}-{sellerId}-{sequenceNumber}
					String shipmentType = "L1"; // L1 for local, G1 for global - using L1 for apparel orders

					// Create increment ID with or without sellerId
					String incrementId;
					int nextSeq = sequenceNo.getAndIncrement();
					if (sellerId != null) {
						incrementId = salesOrder.getIncrementId() + "-" + shipmentType + "-" + sellerId + "-" + nextSeq;
					} else {
						// If sellerId is null, create increment ID without sellerId part
						incrementId = salesOrder.getIncrementId() + "-" + shipmentType + "-" + nextSeq;
						LOGGER.warn("[OrderpushHelper] sellerId is null for SalesOrder: " + salesOrder.getIncrementId() + ", creating increment ID without sellerId: " + incrementId);
					}

					splitSellerOrder.setIncrementId(incrementId);
					splitSellerOrder.setSellerId(sellerId);
					splitSellerOrder.setWarehouseId(warehouseId);
					splitSellerOrder.setShipmentMode(Constants.LOCAL_SHIPMENT);
					splitSellerOrder.setHasGlobalShipment(false);
					splitSellerOrder.setEstimateDelivery(null!=salesOrder.getEstimatedDeliveryTime()?salesOrder.getEstimatedDeliveryTime():null);
					splitSellerOrder.setCreatedAt(now);
					splitSellerOrder.setUpdatedAt(now);

					// Set relationships
					splitSellerOrder.setSalesOrder(salesOrder);

					List<SalesOrderItem>  skuItems= groupedItems.stream().filter(item -> item.getSku().equals(parentItem.getSku()))
							.sorted(Comparator.comparing(SalesOrderItem::getItemId)).toList();
					for (SalesOrderItem salesOrderItem : skuItems) {
						SplitSellerOrderItem splitSellerOrderItem = new SplitSellerOrderItem();
						splitSellerOrderItem.setSku(salesOrderItem.getSku());
						splitSellerOrderItem.setProductType(salesOrderItem.getProductType());
						splitSellerOrderItem.setQtyOrdered(salesOrderItem.getQtyOrdered());
						splitSellerOrderItem.setQtyCanceled(salesOrderItem.getQtyCanceled());
						splitSellerOrderItem.setQtyShipped(salesOrderItem.getQtyShipped());
						splitSellerOrderItem.setSellerId(sellerId);
						splitSellerOrderItem.setSellerName(sellerName);
						splitSellerOrderItem.setWarehouseId(warehouseId);
						splitSellerOrderItem.setStoreId(salesOrder.getStoreId());
						splitSellerOrderItem.setCreatedAt(new Timestamp(new Date().getTime()));
						splitSellerOrderItem.setUpdatedAt(new Timestamp(new Date().getTime()));

						// Set relationships
						splitSellerOrderItem.setSplitSellerOrder(splitSellerOrder);
						splitSellerOrderItem.setSalesOrderItem(salesOrderItem);
						splitSellerOrderItem.setMainOrder(salesOrder);
						// Set warehouse details
						splitSellerOrder.setFirstMileWarehouseName(salesOrderItem.getFirstMileFcName());
						splitSellerOrder.setMidmileWarehouseId(salesOrderItem.getMidMileFc());
						splitSellerOrder.setMidmileWarehouseName(salesOrderItem.getMidMileFcName());
						splitSellerOrder.setLastmileWarehouseId(salesOrderItem.getLastMileFc());
						splitSellerOrder.setLastmileWarehouseName(salesOrderItem.getLastMileFcName());

						if (salesOrderItem.getParentOrderItem() != null) {
							Optional<SplitSellerOrderItem> parentItemOptional = splitSellerOrder.getSplitSellerOrderItems().stream()
									.filter(splitSellOrderItem ->
											splitSellOrderItem.getSku().equals(salesOrderItem.getParentOrderItem().getSku())
									)
									.findFirst();
							parentItemOptional.ifPresent(splitSellerOrderItem::setSplitSellerOrderItem);
						}
						splitSellerOrder.addSplitSellerOrderItem(splitSellerOrderItem);
					}

					// Create SplitSubSellerOrder
					SplitSubSellerOrder splitSubSellerOrder = new SplitSubSellerOrder();
					splitSubSellerOrder.setSalesOrder(salesOrder);
					splitSubSellerOrder.setSplitSellerOrder(splitSellerOrder);
					splitSubSellerOrder.setShipmentMode(Constants.LOCAL_SHIPMENT);
					splitSubSellerOrder.setHasGlobalShipment(false);
					splitSubSellerOrder.setPaymentId(salesOrder.getSubSalesOrder() != null ? salesOrder.getSubSalesOrder().getPaymentId() : null);
					splitSubSellerOrder.setCreatedAt(new Timestamp(new Date().getTime()));
					splitSubSellerOrder.setUpdatedAt(new Timestamp(new Date().getTime()));

					splitSellerOrder.setSplitSubSellerOrders(Set.of(splitSubSellerOrder));

			List<SplitSellerOrder> existingList = splitSellerOrderRepository.findBySellerOrdersByIncrementId(incrementId);
			// Save SplitSellerOrder
			if (CollectionUtils.isNotEmpty(existingList)) {
				LOGGER.info("[OrderpushHelper] SplitSellerOrder with increment ID: " + incrementId + " already exists. Skipping save.");
			} else {
				SplitSellerOrder savedSplitSellerOrder = splitSellerOrderRepository.saveAndFlush(splitSellerOrder);
				sellerOrders.add(savedSplitSellerOrder);
				LOGGER.info("[OrderpushHelper] buildSellerOrdersQuantity created SplitSellerOrder with ID: " + savedSplitSellerOrder.getEntityId() +
						" for SalesOrder: " + salesOrder.getIncrementId());
                // Back-order creation gated by seller_config (is_b2b_seller = true)
                if (shouldCreateBackOrder(sellerId, warehouseId)) {
                    upsertSellerBackOrderAndItems(salesOrder.getSplitSellerOrders());
                } else {
                    LOGGER.info("Skipping back-order creation for sellerId=" + sellerId + ", warehouseId=" + warehouseId + " as seller_config missing or is_b2b_seller != true");
                }
			}
		}

		return sellerOrders;
	}


	/**
		 * @param orderPushRequest
		 * @param order
		 */
	private void setPushorderAddressdetails(OrderPushRequest orderPushRequest, SalesOrder order
			) {

		SalesOrderAddress salesorderAddress = null;
		if (CollectionUtils.isNotEmpty(order.getSalesOrderAddress())) {

			salesorderAddress = order.getSalesOrderAddress().stream()
					.filter(e -> e.getAddressType().equalsIgnoreCase(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING))
					.findFirst().orElse(null);

			if (null != salesorderAddress) {

				Address shippingAddress = new Address();

				shippingAddress.setCity(salesorderAddress.getCity());
				shippingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				shippingAddress.setLine1(salesorderAddress.getStreet());
				shippingAddress.setState(salesorderAddress.getRegion());
				shippingAddress.setCountry(OrderConstants.checkCountryName(order.getStoreId().toString())); 
				shippingAddress.setEmail(salesorderAddress.getEmail());
				shippingAddress.setPhone(salesorderAddress.getTelephone());
				shippingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				Address billingAddress = new Address();

				billingAddress.setCity(salesorderAddress.getCity());
				billingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				billingAddress.setLine1(salesorderAddress.getStreet());
				billingAddress.setState(salesorderAddress.getRegion());
				billingAddress.setCountry(OrderConstants.checkCountryName(order.getStoreId().toString())); 
				billingAddress.setEmail(salesorderAddress.getEmail());
				billingAddress.setPhone(salesorderAddress.getTelephone());
				billingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				orderPushRequest.setShippingAddress(shippingAddress);
				orderPushRequest.setBillingAddress(billingAddress);
			}

		}

	}
		
		
	private void restcallForwmsordercancel(OrderCancelPushRequest payload, SalesOrder createdOrder) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		
		if(null !=createdOrder.getSubSalesOrder().getWarehouseLocationId()
				&& createdOrder.getSubSalesOrder().getWarehouseLocationId().intValue()== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			
		}else if(createdOrder.getStoreId().equals(1) || createdOrder.getStoreId().equals(3) || createdOrder.getStoreId().equals(51)) {
			
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}else {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}
		
		HttpEntity<OrderCancelPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

		
		String url = null;
		
		 url = Constants.orderCredentials.getOrderDetails().getWmsUrl()+"/orders/{orderCode}/cancel";
		Map<String, Object> parameters = new HashMap<>();

		parameters.put("orderCode", createdOrder.getIncrementId());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("WMS  URl:" + builder.buildAndExpand(parameters).toUri());
		
		LOGGER.info("wms cancel url:" + url);

		try {

			LOGGER.info("wms cancel request body for incrementId:" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("wms cancel response body for incrementId:" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(response));
			
			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {

				LOGGER.info("response Body for incrementId:" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(response.getBody()));

			}
			if(createdOrder.getStatus().equals(OrderConstants.CLOSED_ORDER_STATUS) || createdOrder.getStatus().equals(OrderConstants.CANCELLED_ORDER_STATUS)) {
				salesOrderRepository.updateWMSStatus(3, createdOrder.getEntityId());
			}
		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("Exception occurred  during cancel WMS call for incrementId:" + createdOrder.getIncrementId() + " : " + e.getMessage());
		}
	}

	private void restcallForwmsordercancelForSplitOrder(OrderCancelPushRequest payload, SplitSalesOrder createdOrder) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

        if (null != createdOrder.getSplitSubSalesOrder().getWarehouseLocationId()) {
            payload.setLocationCode(createdOrder.getSplitSubSalesOrder().getWarehouseLocationId().toString());
        } else {
            payload.setLocationCode(OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE.toString());
        }


        if(null !=createdOrder.getSplitSubSalesOrder().getWarehouseLocationId()
				&& createdOrder.getSplitSubSalesOrder().getWarehouseLocationId().intValue()== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			
		}else if(createdOrder.getStoreId().equals(1) || createdOrder.getStoreId().equals(3) || createdOrder.getStoreId().equals(51)) {
	
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}else {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}
		
		HttpEntity<OrderCancelPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = null;
		
		 url = Constants.orderCredentials.getOrderDetails().getWmsUrl()+"/orders/{orderCode}/cancel";
		Map<String, Object> parameters = new HashMap<>();

		parameters.put("orderCode", createdOrder.getIncrementId());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("WMS  URl:" + builder.buildAndExpand(parameters).toUri());
		
		LOGGER.info("wms cancel url:" + url);
		try {

			LOGGER.info("wms cancel split order request body for incrementId:" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("wms cancel split order response body for incrementId:" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(response));
			
			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {

				LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));

			}
			if(createdOrder.getStatus().equals(OrderConstants.CLOSED_ORDER_STATUS) || createdOrder.getStatus().equals(OrderConstants.CANCELLED_ORDER_STATUS)) {
				splitSalesOrderRepository.updateWMSStatus(3, createdOrder.getEntityId());
			}
		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("Exception occurred during cancel split order WMS call for incrementId:" + createdOrder.getIncrementId(), e);
		}
	}

	private void restcallForSellerwmsordercancel(OrderCancelPushRequest payload, SplitSellerOrder splitSellerOrder) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
	
		// Use service to get seller config
		LOGGER.info("Using config source: " + sellerConfigService.getConfigSource() + " for warehouse id: " + splitSellerOrder.getWarehouseId());
		SellerConfig sellerConfig = sellerConfigService.getSellerConfigForWarehouse(splitSellerOrder.getWarehouseId());
		
		if (sellerConfig == null) {
			LOGGER.warn("Seller config not found for warehouse id: " + splitSellerOrder.getWarehouseId());
			return;
		}
		
		boolean pushToSellerCentral = false;
		String locationCode = sellerConfig.getSellerWarehouseId();
		String wmsUserName = null;
		String wmsPassword = null;
		String cancelUrl = null;

		// Null-safe access to BasicSettings
		if (sellerConfig.getBasicSettings() != null) {
			pushToSellerCentral = Boolean.TRUE.equals(sellerConfig.getBasicSettings().getPushToSellerCentral());
		}
		
		// Null-safe access to Configuration
		if (sellerConfig.getConfiguration() != null) {
			wmsUserName = sellerConfig.getConfiguration().getWmsWarehouseHeaderUserName();
			wmsPassword = sellerConfig.getConfiguration().getWmsWarehouseHeaderPassword();
			cancelUrl = sellerConfig.getConfiguration().getWmsWareHouseOrderCancel();
		}

		if(pushToSellerCentral) {
			LOGGER.info("Pushing cancel order to seller central for increment id: " + splitSellerOrder.getIncrementId());
			pushCancelOrderToSellerCentral(splitSellerOrder);
			LOGGER.info("Cancel order pushed to seller central for increment id: " + splitSellerOrder.getIncrementId());
			splitSellerOrderRepository.updateWMSStatus(3, splitSellerOrder.getEntityId());
			return;
		}

		// Prepare the payload for cancel order
		List<OrderPushItem> ordercancelitemList = new ArrayList<>();
		List<SplitSellerOrderItem> splitSellerOrderItems = splitSellerOrder.getSplitSellerOrderItems().stream()
			.filter(item -> !item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
			.collect(Collectors.toList());
		
		for (SplitSellerOrderItem orderItem : splitSellerOrderItems) {
			OrderPushItem orderPushItem = new OrderPushItem();
			orderPushItem.setChannelSkuCode(orderItem.getSku());
			orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
			orderPushItem.setCancelledQuantity(orderItem.getQtyOrdered());
			ordercancelitemList.add(orderPushItem);
		}

		payload.setOrderItems(ordercancelitemList);
		payload.setLocationCode(locationCode);

		requestHeaders.add(Constants.WMS_USER_HEADER_NAME, wmsUserName);
		requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, wmsPassword);
		
		HttpEntity<OrderCancelPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
		
		String url = cancelUrl;
		Map<String, Object> parameters = new HashMap<>();

		parameters.put("orderCode", splitSellerOrder.getIncrementId());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("WMS  URl:" + builder.buildAndExpand(parameters).toUri());
		
		LOGGER.info("wms cancel url:" + url);
		try {

			LOGGER.info("wms cancel seller order request body for incrementId:" + splitSellerOrder.getIncrementId() + " : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("wms cancel response body for incrementId:" + splitSellerOrder.getIncrementId() + " : " + mapper.writeValueAsString(response));
			
			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {

				LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));

			}
			splitSellerOrderRepository.updateWMSStatus(3, splitSellerOrder.getEntityId());
		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("Exception occurred during cancel seller order WMS call for incrementId:" + splitSellerOrder.getIncrementId(), e);
		}
	}

	/**
	 * REST call to seller WMS for back order cancellation.
	 * This method is specifically for cancelling back orders and includes backOrderId in the payload.
	 * On successful (200 OK) response, closes the back order items connected to the split seller order.
	 *
	 * @param payload cancel request with orderItems: channelSkuCode + orderItemCode (WMS line id; use SKU for back orders), cancelledQuantity
	 * @param splitSellerOrder the split seller order being cancelled
	 * @param backOrderItemsToCloseOnSuccess back order items linked to this split seller order; closed when WMS returns 200 (can be null/empty)
	 */
	private void restcallForSellerwmsBackOrdercancel(OrderCancelPushRequest payload, SplitSellerOrder splitSellerOrder,
			List<SellerBackOrderItem> backOrderItemsToCloseOnSuccess) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		String warehouseId = splitSellerOrder.getWarehouseId();
		LOGGER.info("[BackOrder] Loading seller_config from DB by styli_warehouse_id: " + warehouseId);
		SellerConfig sellerConfig = sellerConfigRepository.findSellerConfigForBackOrderByStyliWarehouseId(warehouseId);

		if (sellerConfig == null) {
			throw new RuntimeException("Seller config not found for warehouse id: " + splitSellerOrder.getWarehouseId());
		}

		LOGGER.info("[BackOrder] Seller config resolved: configId=" + sellerConfig.getId()
				+ ", sellerId=" + sellerConfig.getSellerId()
				+ ", styliWarehouseId=" + sellerConfig.getStyliWarehouseId()
				+ ", sellerWarehouseId(WMS)=" + sellerConfig.getSellerWarehouseId()
				+ ", sellerType=" + sellerConfig.getSellerType());

		boolean pushToSellerCentral = false;
		if (sellerConfig.getBasicSettings() != null) {
			pushToSellerCentral = Boolean.TRUE.equals(sellerConfig.getBasicSettings().getPushToSellerCentral());
			LOGGER.info("[BackOrder] basicSettings present: pushToSellerCentral=" + pushToSellerCentral);
		} else {
			LOGGER.info("[BackOrder] basicSettings is null; pushToSellerCentral defaults to false");
		}

		if (pushToSellerCentral) {
			int backOrderItemCount = CollectionUtils.isEmpty(backOrderItemsToCloseOnSuccess) ? 0 : backOrderItemsToCloseOnSuccess.size();
			LOGGER.info("[BackOrder] Routing cancel to Seller Central (not direct WMS): splitSellerOrder entityId="
					+ splitSellerOrder.getEntityId() + ", incrementId=" + splitSellerOrder.getIncrementId()
					+ ", backOrderItemsToCloseOnSuccess count=" + backOrderItemCount);
			LOGGER.info("[BackOrder] Pushing back order cancel to seller central for increment id: " + splitSellerOrder.getIncrementId());
			pushCancelOrderToSellerCentral(splitSellerOrder);
			LOGGER.info("[BackOrder] Back order cancel pushed to seller central for increment id: " + splitSellerOrder.getIncrementId());
			splitSellerOrderRepository.updateWMSStatus(3, splitSellerOrder.getEntityId());
			return;
		}

		SellerConfiguration conf = sellerConfig.getConfiguration();
		LOGGER.info("[BackOrder] WMS path: seller JSON configuration is " + (conf == null ? "null" : "present")
				+ ", wmsWareHouseOrderCancel template is " + (conf == null || conf.getWmsWareHouseOrderCancel() == null ? "null/missing" : "set"));
		if (conf == null || conf.getWmsWareHouseOrderCancel() == null) {
			throw new RuntimeException("Seller configuration or WMS cancel URL not found for warehouse id: " + splitSellerOrder.getWarehouseId());
		}

		payload.setLocationCode(sellerConfig.getSellerWarehouseId());
		LOGGER.info("[BackOrder] Payload locationCode set from seller_config.seller_warehouse_id: " + payload.getLocationCode());

		String user = conf.getWmsWarehouseHeaderUserName();
		String pass = conf.getWmsWarehouseHeaderPassword();
		requestHeaders.add(Constants.WMS_USER_HEADER_NAME, user);
		requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, pass);
		LOGGER.info("[BackOrder] WMS request headers: " + Constants.WMS_USER_HEADER_NAME + "=" + user
				+ ", " + Constants.WMS_USER_HEADER_PASSWORD + "=<set, not logged>");

		HttpEntity<OrderCancelPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = conf.getWmsWareHouseOrderCancel();
		Map<String, Object> parameters = new HashMap<>();

		// For back-order cancel: use backOrderId in URL in place of orderCode; otherwise use seller order increment id
		if (payload.getBackOrderId() != null) {
			parameters.put("orderCode", payload.getBackOrderId());
			LOGGER.info("[BackOrder] URL orderCode from payload.backOrderId: " + payload.getBackOrderId());
		} else {
			parameters.put("orderCode", splitSellerOrder.getIncrementId());
			LOGGER.warn("[BackOrder] payload.backOrderId was null; URL orderCode fallback to splitSellerOrder.incrementId: "
					+ splitSellerOrder.getIncrementId());
		}

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
		LOGGER.info("[BackOrder] WMS cancel URL template: " + url);
		LOGGER.info("[BackOrder] WMS cancel URL path parameters: " + parameters);

		LOGGER.info("[BackOrder] WMS URL: " + builder.buildAndExpand(parameters).toUri());
		LOGGER.info("[BackOrder] BackOrderId (body): " + payload.getBackOrderId());

		try {
			LOGGER.info("[BackOrder] WMS cancel request body for incrementId: " + splitSellerOrder.getIncrementId() + " : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("[BackOrder] WMS cancel response body for incrementId: " + splitSellerOrder.getIncrementId() + " : " + mapper.writeValueAsString(response));

			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {
				LOGGER.info("[BackOrder] Response Body: " + mapper.writeValueAsString(response.getBody()));
				// Close the back order items connected to this split seller order.
				// backOrderItemsToCloseOnSuccess is the same list from orderBackOrderCancelpushTowmsForSellerOrder,
				// built in SalesOrderCancelServiceImpl from findBySplitSellerOrderAndStatusNot(sellerOrder, "CLOSED"),
				// so each item has seller_order_id = splitSellerOrder.getEntityId() in seller_back_order_item table.
				if (CollectionUtils.isNotEmpty(backOrderItemsToCloseOnSuccess)) {
					List<Integer> closedEntityIds = new ArrayList<>();
					for (SellerBackOrderItem item : backOrderItemsToCloseOnSuccess) {
						item.setStatus("CLOSED");
						sellerBackOrderItemRepository.save(item);
						closedEntityIds.add(item.getEntityId());
					}
					LOGGER.info("[BackOrder] Closed " + backOrderItemsToCloseOnSuccess.size() + " back order item(s) for split seller order: " + splitSellerOrder.getIncrementId()
							+ " (seller_back_order_item.entity_id in DB: " + closedEntityIds + ")");
				}
			}
			splitSellerOrderRepository.updateWMSStatus(3, splitSellerOrder.getEntityId());

		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("[BackOrder] Exception occurred during back order cancel WMS call for incrementId: " + splitSellerOrder.getIncrementId(), e);
			throw new RuntimeException("Failed to push back order cancellation to WMS for order: " + splitSellerOrder.getIncrementId(), e);
		}
	}
	
	public void restcallForwmsorderpush(OrderPushRequest payload, SalesOrder createdOrder) {

		LOGGER.info("[Increff] Order outward push start: incrementId=" + createdOrder.getIncrementId()
				+ ", entityId=" + createdOrder.getEntityId()
				+ ", storeId=" + createdOrder.getStoreId()
				+ ", orderCode=" + (payload != null ? payload.getOrderCode() : null)
				+ ", locationCode=" + (payload != null ? payload.getLocationCode() : null)
				+ ", lineItems=" + (payload != null && payload.getOrderItems() != null ? payload.getOrderItems().size() : 0));

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		String increffAuthBranch = null;
		String increffAuthUser = null;
		if(null !=createdOrder.getSubSalesOrder().getWarehouseLocationId()
				&& createdOrder.getSubSalesOrder().getWarehouseLocationId().intValue()== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			increffAuthBranch = "warehouseLocationId==OMS_CODE -> inventoryMapping[0]";
			increffAuthUser = inventoryMapping.getWmsHeaderUsrName();
			
		}else if(createdOrder.getStoreId().equals(1) || createdOrder.getStoreId().equals(3) || createdOrder.getStoreId().equals(51)) {
			
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			increffAuthBranch = "storeId in (1,3,51) -> inventoryMapping[0]";
			increffAuthUser = inventoryMapping.getWmsHeaderUsrName();
		}else {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			increffAuthBranch = "default -> inventoryMapping[1]";
			increffAuthUser = inventoryMapping.getWmsHeaderUsrName();
		}
		LOGGER.info("[Increff] Order outward auth: " + increffAuthBranch + ", " + Constants.WMS_USER_HEADER_NAME + "=" + increffAuthUser
				+ ", " + Constants.WMS_USER_HEADER_PASSWORD + "=<set, not logged>");

		HttpEntity<OrderPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = null;
		if(null != Constants.orderCredentials.getOrderDetails()
				&& null != Constants.orderCredentials.getOrderDetails().getWmsUrl()) {
			
			 url =Constants.orderCredentials.getOrderDetails().getWmsUrl()+ "/orders/outward";
		}

		LOGGER.info("[Increff] Order outward POST url=" + url + ", wmsBaseFromCredentials="
				+ (Constants.orderCredentials.getOrderDetails() != null ? Constants.orderCredentials.getOrderDetails().getWmsUrl() : null));
		try {

			LOGGER.info("[Increff] Order outward request body incrementId=" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(url,
					HttpMethod.POST, requestBody, Object.class);
			LOGGER.info("[Increff] Order outward HTTP response incrementId=" + createdOrder.getIncrementId()
					+ ", status=" + (response != null ? response.getStatusCode() : null));

			if (response != null && response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200 ) {

				LOGGER.info("[Increff] Order outward response body incrementId=" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(response.getBody()));

			}
			if (payload != null && Objects.nonNull(payload.getOnHold()) && payload.getOnHold().booleanValue()
					&& Objects.nonNull(createdOrder.getStoreId()) && 51 != createdOrder.getStoreId()) {
				/** set ext_order_id=1 for hold status **/
				salesOrderRepository.updateHoldOrderPushStatus(1, createdOrder.getEntityId());
				salesOrderRepository.updateWMSStatus(7, createdOrder.getEntityId()); 
			} else {
				salesOrderRepository.updateWMSStatus(1, createdOrder.getEntityId()); 
			}
			LOGGER.info("[Increff] Order outward WMS DB status updated for incrementId=" + createdOrder.getIncrementId());
			
		} catch (Exception e ) {
			LOGGER.error("[Increff] Order outward push failed incrementId=" + createdOrder.getIncrementId() + " : " + e.getMessage(), e);
		}

	}

	@Transactional
	public void orderCancelpushTowms(Map.Entry<Integer,List<WarehouseItem>> order) {
		Integer wareHouseLocationid= order.getValue().get(0).getWarehouseId();
		OrderCancelPushRequest ordercancelPushRequest = new OrderCancelPushRequest();
		if (null != wareHouseLocationid) {
			ordercancelPushRequest.setLocationCode(wareHouseLocationid.toString());
		} else {
			ordercancelPushRequest.setLocationCode(OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE.toString());
		}

		List<OrderPushItem> cancelItems = order.getValue().stream()
				.filter(Objects::nonNull)
				.filter(i -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(i.getProductType()))
				.map(i -> {
					OrderPushItem p = new OrderPushItem();
					p.setChannelSkuCode(i.getSku());
					p.setOrderItemCode(String.valueOf(i.getItemId()));
					p.setCancelledQuantity(i.getQtyOrdered());
					return p;
				})
				.collect(Collectors.toList());

		if (!cancelItems.isEmpty()) {
			ordercancelPushRequest.setOrderItems(cancelItems);
			restcallForwmsordercancelIncreff(ordercancelPushRequest,wareHouseLocationid, order.getKey(), order.getValue().get(0));
		}
	}

	private void restcallForwmsordercancelIncreff(OrderCancelPushRequest payload,Integer wareHouseId, Integer entityId, WarehouseItem createdOrder) {
		LOGGER.info("[Increff] Warehouse cancel start: salesOrderEntityId=" + entityId
				+ ", incrementId=" + createdOrder.getIncrementId()
				+ ", wareHouseLocationId=" + wareHouseId
				+ ", storeId=" + createdOrder.getStoreId()
				+ ", payload.locationCode=" + (payload != null ? payload.getLocationCode() : null)
				+ ", cancelLineItems=" + (payload != null && payload.getOrderItems() != null ? payload.getOrderItems().size() : 0));

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		String increffCancelAuthBranch = null;
		String increffCancelAuthUser = null;
		if(null !=wareHouseId
				&& wareHouseId== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {

			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			increffCancelAuthBranch = "wareHouseId==OMS_CODE -> inventoryMapping[0]";
			increffCancelAuthUser = inventoryMapping.getWmsHeaderUsrName();

		}else if(createdOrder.getStoreId().equals(1) || createdOrder.getStoreId().equals(3) || createdOrder.getStoreId().equals(51)) {


			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			increffCancelAuthBranch = "storeId in (1,3,51) -> inventoryMapping[0]";
			increffCancelAuthUser = inventoryMapping.getWmsHeaderUsrName();
		}else {

			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			increffCancelAuthBranch = "default -> inventoryMapping[1]";
			increffCancelAuthUser = inventoryMapping.getWmsHeaderUsrName();
		}
		LOGGER.info("[Increff] Warehouse cancel auth: " + increffCancelAuthBranch + ", " + Constants.WMS_USER_HEADER_NAME + "=" + increffCancelAuthUser
				+ ", " + Constants.WMS_USER_HEADER_PASSWORD + "=<set, not logged>");

		HttpEntity<OrderCancelPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);


		String url = null;

		url = Constants.orderCredentials.getOrderDetails().getWmsUrl()+"/orders/{orderCode}/cancel";
		Map<String, Object> parameters = new HashMap<>();

		parameters.put("orderCode", createdOrder.getIncrementId());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("[Increff] Warehouse cancel PUT expandedUrl=" + builder.buildAndExpand(parameters).toUri());

		LOGGER.info("[Increff] Warehouse cancel urlTemplate=" + url + ", pathParameters=" + parameters);

		try {

			LOGGER.info("[Increff] Warehouse cancel request body incrementId=" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("[Increff] Warehouse cancel HTTP response incrementId=" + createdOrder.getIncrementId()
					+ ", status=" + (response != null ? response.getStatusCode() : null));
			LOGGER.info("[Increff] Warehouse cancel response wrapper incrementId=" + createdOrder.getIncrementId() + " : "
					+ (response != null ? mapper.writeValueAsString(response) : "null"));

			if (response != null && response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {

				LOGGER.info("[Increff] Warehouse cancel response body incrementId=" + createdOrder.getIncrementId() + " : " + mapper.writeValueAsString(response.getBody()));


				salesOrderRepository.updateWMSStatus(3, entityId);
			}
		} catch (RestClientException | JsonProcessingException e) {
			if (((HttpClientErrorException.BadRequest) e).getMessage().contains("Invalid channelOrderId")) {
				LOGGER.info("[Increff] Warehouse cancel: Invalid channelOrderId treated as never posted; closing entityId=" + entityId
						+ ", incrementId=" + createdOrder.getIncrementId());
				salesOrderRepository.updateWMSStatus(3, entityId);
			} else {
				LOGGER.error("[Increff] Warehouse cancel failed incrementId=" + createdOrder.getIncrementId() + " : " + e.getMessage(), e);
			}
		}
	}

	// This will be used in case of seller cancellation only
	@Transactional
	public void orderCancelpushTowmsV2(SalesOrder order, List<SalesOrderItem> itemList, List<SplitSellerOrder> sellerOrders) {

		OrderCancelPushRequest ordercancelPushRequest = new OrderCancelPushRequest();
		if (null != order.getSubSalesOrder().getWarehouseLocationId()) {
			ordercancelPushRequest.setLocationCode(order.getSubSalesOrder().getWarehouseLocationId().toString());
		} else {
			ordercancelPushRequest.setLocationCode(OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE.toString());
		}

		List<SalesOrderItem> salesorderParentItem = itemList != null && CollectionUtils.isNotEmpty(itemList) ? itemList : order.getSalesOrderItem().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

		if (CollectionUtils.isNotEmpty(salesorderParentItem)) {

			List<OrderPushItem> ordercancelitemList = new ArrayList<>();
			for (SalesOrderItem orderItem : salesorderParentItem) {
				OrderPushItem orderPushItem = new OrderPushItem();
				orderPushItem.setChannelSkuCode(orderItem.getSku());
				orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
				orderPushItem.setCancelledQuantity(orderItem.getQtyCanceled());
				ordercancelitemList.add(orderPushItem);
			}
			ordercancelPushRequest.setOrderItems(ordercancelitemList);
		}
		if (CollectionUtils.isNotEmpty(ordercancelPushRequest.getOrderItems())) {
			restcallForwmsordercancel(ordercancelPushRequest, order);
		}
		// set wms status to 3 for the seller orders that are being cancelled
		for(SplitSellerOrder sellerOrder : sellerOrders) {
			splitSellerOrderRepository.updateWMSStatus(3, sellerOrder.getEntityId());
		}
	}

	@Transactional
	public void orderCancelpushTowmsForSplitOrder(SplitSalesOrder order, List<SplitSalesOrderItem> itemList, List<SplitSellerOrder> sellerOrders) {

		OrderCancelPushRequest ordercancelPushRequest = new OrderCancelPushRequest();

		List<SplitSalesOrderItem> salesorderParentItem = (itemList != null && CollectionUtils.isNotEmpty(itemList)) ? itemList : order.getSplitSalesOrderItems().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

        List<OrderPushItem> ordercancelitemList = new ArrayList<>();
        for (SplitSalesOrderItem orderItem : salesorderParentItem) {
            OrderPushItem orderPushItem = new OrderPushItem();
            orderPushItem.setChannelSkuCode(orderItem.getSku());
            orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
            BigDecimal qtyCanceled = OrderConstants.FAILED_ORDER_STATUS.equals(order.getStatus()) ? orderItem.getQtyOrdered() : orderItem.getQtyCanceled();
            orderPushItem.setCancelledQuantity(qtyCanceled);
            ordercancelitemList.add(orderPushItem);
        }
        ordercancelPushRequest.setOrderItems(ordercancelitemList);

		if (CollectionUtils.isNotEmpty(ordercancelPushRequest.getOrderItems())) {
			restcallForwmsordercancelForSplitOrder(ordercancelPushRequest, order);
		}

		// set wms status to 3 for the seller orders that are being cancelled
		for(SplitSellerOrder sellerOrder : sellerOrders) {
			splitSellerOrderRepository.updateWMSStatus(3, sellerOrder.getEntityId());
		}
	}

	@Transactional
	public void orderCancelpushTowmsForSellerOrder(SplitSellerOrder order) {
		OrderCancelPushRequest ordercancelPushRequest = new OrderCancelPushRequest();

			List<SplitSellerOrderItem> salesorderParentItem = order.getSplitSellerOrderItems().stream()
					.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

        List<OrderPushItem> ordercancelitemList = new ArrayList<>();
        for (SplitSellerOrderItem orderItem : salesorderParentItem) {
            OrderPushItem orderPushItem = new OrderPushItem();
            orderPushItem.setChannelSkuCode(orderItem.getSku());
            orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
			BigDecimal qtyCanceled = OrderConstants.FAILED_ORDER_STATUS.equals(order.getStatus()) ? orderItem.getQtyOrdered() : orderItem.getQtyCanceled();
            orderPushItem.setCancelledQuantity(qtyCanceled);
            ordercancelitemList.add(orderPushItem);
        }
        ordercancelPushRequest.setOrderItems(ordercancelitemList);

		if (CollectionUtils.isNotEmpty(ordercancelPushRequest.getOrderItems())) {
			restcallForSellerwmsordercancel(ordercancelPushRequest, order);
		}
	}

	/**
	 * Pushes back order cancellation to seller WMS. Sends quantity per item via cancelledQuantity in orderItems.
	 * On WMS 200 OK, back order items linked to this split seller order are closed in this helper.
	 */
	public void orderBackOrderCancelpushTowmsForSellerOrder(SplitSellerOrder order, List<SellerBackOrderItem> backOrderItems, String backOrderId) {
		OrderCancelPushRequest ordercancelPushRequest = new OrderCancelPushRequest();

		List<OrderPushItem> ordercancelitemList = new ArrayList<>();
		for (SellerBackOrderItem backOrderItem : backOrderItems) {
			OrderPushItem orderPushItem = new OrderPushItem();
			orderPushItem.setChannelSkuCode(backOrderItem.getSku());
			// WMS (Increff) expects orderItemCode to match the line id on the channel order — same as channelSkuCode for
			// back-order cancel (see assure-magic2 cancel API). entity_id is our DB PK and is rejected (e.g. "2141" not in order).
			orderPushItem.setOrderItemCode(backOrderItem.getSku());
			orderPushItem.setCancelledQuantity(BigDecimal.valueOf(backOrderItem.getQty()));
			ordercancelitemList.add(orderPushItem);
		}
		ordercancelPushRequest.setOrderItems(ordercancelitemList);

		// Resolve back order increment id from database for payload and URL
		String backOrderIncrementId = resolveBackOrderIncrementIdFromDb(backOrderItems, backOrderId);
		ordercancelPushRequest.setBackOrderId(backOrderIncrementId);

		if (CollectionUtils.isNotEmpty(ordercancelPushRequest.getOrderItems())) {
			restcallForSellerwmsBackOrdercancel(ordercancelPushRequest, order, backOrderItems);
		}
	}

	/**
	 * Resolves back order increment id from database using the parent back order of the given items.
	 * Falls back to the passed backOrderId if fetch fails or returns null.
	 */
	private String resolveBackOrderIncrementIdFromDb(List<SellerBackOrderItem> backOrderItems, String fallbackBackOrderId) {
		if (CollectionUtils.isEmpty(backOrderItems)) {
			return fallbackBackOrderId;
		}
		SellerBackOrder parent = backOrderItems.get(0).getSellerBackOrder();
		if (parent == null || parent.getEntityId() == null) {
			LOGGER.warn("[BackOrder] Cannot resolve back order id from DB - parent back order or entity id is null, using fallback");
			return fallbackBackOrderId;
		}
		return sellerBackOrderRepository.findById(parent.getEntityId())
				.map(SellerBackOrder::getBackOrderIncrementid)
				.orElse(fallbackBackOrderId);
	}

	/**
	 * @param order
	 * @return order cancel response
	 */
	public OmsUnfulfilmentResponse cancelUnfulfiorder(SalesOrder order , OrderunfulfilmentRequest request
			, Map<String, String> httpRequestHeadrs) {

		try {
			List<Stores> stores = Constants.getStoresList();
			Boolean isFullyCancellation = false;
			String orderStatus = null;
			OmsUnfulfilmentResponse response = new OmsUnfulfilmentResponse();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
					.orElse(null);

			String fortId = null;
			String paymentMethod = null;
			if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
				for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
					paymentMethod = payment.getMethod();
					fortId = payment.getCcTransId();
				}
			}

			boolean isValidate = validateRequest(order, request);
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
			//Find import fee of order based on available items of order
			BigDecimal currentOrderValue = paymentDtfRefundHelper.findCurrentOrderValue(order);
			boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();
			LOGGER.info("In cancelUnfulfilOrder Non KSA Seller Cancellation Flag: " + nonKsaSellerCancellation);
			//If non KSA seller cancellation is enabled, allow partial cancellation for all countries except COD with Styli credit or Gift Voucher
			if (nonKsaSellerCancellation) {
				if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& null != order.getAmstorecreditAmount()
						&& order.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with styli credit.");
					return response;
				} else if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& null != order.getGiftVoucherDiscount()
						&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with Gift Voucher.");
					return response;
				}
			}
			// If non KSA seller cancellation is disabled, apply old KSA rules
			if (!nonKsaSellerCancellation) {
				if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& !(order.getStoreId().equals(1) || order.getStoreId().equals(3) || order.getStoreId().equals(51))) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled because of its payment method.");
					return response;
				} else if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& (order.getStoreId().equals(1) || order.getStoreId().equals(3) || order.getStoreId().equals(51))
						&& null != order.getAmstorecreditAmount()
						&& order.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with styli credit.");
					return response;
				} else if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& (order.getStoreId().equals(1) || order.getStoreId().equals(3) || order.getStoreId().equals(51))
						&& null != order.getGiftVoucherDiscount()
						&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with Gift Voucher.");
					return response;
				}
			}

			if (!isValidate) {

				response.setHasError(true);
				response.setErrorMessage("some item(s) quantity not availble to cancel!");
				return response;

			}
			if(!nonKsaSellerCancellation && !(order.getStoreId().equals(1) || order.getStoreId().equals(3) || order.getStoreId().equals(51))) {
					response.setHasError(true);
					response.setErrorMessage("This store order is order not availble for seller cancellation!");
					return response;
			}

			BigDecimal customerTotalPoints = null;
			if (order != null && order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && order.getSubSalesOrder().getShukranLocked() != null && order.getSubSalesOrder().getShukranLocked().equals(0)) {
				customerTotalPoints= commonService.customerShukranBalance(order.getSubSalesOrder().getCustomerProfileId());
				if(customerTotalPoints == null || customerTotalPoints.compareTo(BigDecimal.ZERO)<0){
					response.setHasError(true);
					response.setErrorMessage("Customer Does Not Have Enough Shukran Points");
					return response;
				}
			}
			if (null != order && CollectionUtils.isNotEmpty(request.getOrderItems())) {

				List<SalesOrderItem> itemList = new ArrayList<>();
				BigDecimal totalAmountToRefund = new BigDecimal(0);

				BigDecimal beforeCancelledAmount = paymentDtfRefundHelper.getCanceledItemQty(order);
				Map<String, BigDecimal> skumApList = new HashMap<>();
				Map<String, BigDecimal> actualskumApList = new HashMap<>();
				for (OrderPushItem canceledItem : request.getOrderItems()) {

					if (null != canceledItem.getCancelledQuantity() && null != canceledItem.getOrderItemCode()
							&& canceledItem.getCancelledQuantity().intValue() != 0) {

						calculateCancelledValue(order, itemList, skumApList, canceledItem, actualskumApList);

					}

				}

				itemList.removeAll(Collections.singleton(null));

				if (CollectionUtils.isEmpty(itemList)) {

					response.setHasError(false);
					response.setErrorMessage("Already Cancelled.");

					return response;
				}


				BigDecimal sumOrderedQty = order.getSalesOrderItem().stream()
						.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.map(x -> x.getQtyOrdered())
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				BigDecimal sumOrderedCancelled = order.getSalesOrderItem().stream()
						.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.map(x -> x.getQtyCanceled())
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				Integer cancelledCount = 0;
				if (sumOrderedQty.intValue() == sumOrderedCancelled.intValue()) {


					cancelledCount = 2;
					orderStatus = OrderConstants.CLOSED_ORDER_STATUS;
					order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
					order.setWmsPullStatus(7); /**for seller cancellation **/
					isFullyCancellation = true;
				} else {

					orderStatus = order.getStatus();
					cancelledCount = 1;
					order.setWmsPullStatus(7);
				}

				order.getSubSalesOrder().setIsUnfulfilmentOrder(cancelledCount);


				totalAmountToRefund = BigDecimal.ZERO;
				DecimalFormat df = new DecimalFormat(".##");
				BigDecimal totalVoucherToRefund = BigDecimal.ZERO;

				BigDecimal totalShukranCoinsNeedsToBeLockedAgain = BigDecimal.ZERO;
				// Initialize with all burned points from the order, regardless of customerTotalPoints check
				if (order != null && order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal initialBurnedPoints = order.getSubSalesOrder().getTotalShukranCoinsBurned();
					totalShukranCoinsNeedsToBeLockedAgain = initialBurnedPoints;
					LOGGER.info("cancelUnfulfiorder: Initialized totalShukranCoinsNeedsToBeLockedAgain with all burned points: " + initialBurnedPoints + " for orderId: " + order.getEntityId());
					
					// Only unlock if customer balance check passed
					if (customerTotalPoints != null && customerTotalPoints.compareTo(BigDecimal.ZERO) >= 0) {
						LOGGER.info("cancelUnfulfiorder: Unlocking all burned points - Total burned points: " + initialBurnedPoints);
						commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(), initialBurnedPoints.toString(), order.getSubSalesOrder().getQuoteId(), false, order, store, "Cancel Shukran Burned Points On Unfulfilled Order", "");
						SubSalesOrder subSalesOrder = order.getSubSalesOrder();
						subSalesOrder.setShukranLocked(1);
						subSalesOrderRepository.saveAndFlush(subSalesOrder);
						LOGGER.info("cancelUnfulfiorder: Successfully unlocked all points and set ShukranLocked=1");
					} else {
						LOGGER.info("cancelUnfulfiorder: Customer balance check failed or customerTotalPoints is null - Skipping unlock, but will still calculate points to lock back for non-cancelled items");
					}
				} else {
					LOGGER.info("cancelUnfulfiorder: No burned points found in order - totalShukranCoinsNeedsToBeLockedAgain remains 0");
				}
				for (SalesOrderItem item : itemList) {

					BigDecimal priceIncludeTax = item.getPriceInclTax();
					BigDecimal originpriceIncludeTax = item.getPriceInclTax();
					BigDecimal qtyCancelled = actualskumApList.get(item.getSku());

					if (null != item.getDiscountAmount() && !(item.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {

						BigDecimal totalProductValue = BigDecimal.ZERO;
						BigDecimal qtyOrdered = item.getQtyOrdered();
						BigDecimal Indivisualdiscount = item.getDiscountAmount().divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(2,
								RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						BigDecimal cancelDiscountVal = BigDecimal.ZERO;
						if (null != qtyCancelled) {
							cancelDiscountVal = Indivisualdiscount.multiply(qtyCancelled).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

						}
						priceIncludeTax = priceIncludeTax.multiply(qtyCancelled).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP).subtract(cancelDiscountVal);

						/**for match **/
						totalProductValue = originpriceIncludeTax.multiply(qtyOrdered).subtract(item.getDiscountAmount());
						BigDecimal difference = priceIncludeTax.multiply(qtyOrdered).subtract(totalProductValue).setScale(2, RoundingMode.HALF_UP);
						BigDecimal diffentConstantAmount = new BigDecimal("0.01");
						if (difference.compareTo(diffentConstantAmount) == 0) {

							priceIncludeTax = priceIncludeTax.subtract(diffentConstantAmount);

						}


						totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);
						SubSalesOrderItem subSalesOrderItem = null;
						if (null != item.getParentOrderItem()) {
							subSalesOrderItem = item.getParentOrderItem().getSubSalesOrderItem().stream()
									.filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
						}

						if (Objects.nonNull(subSalesOrderItem)) {
							if (!subSalesOrderItem.getDiscount().equals(BigDecimal.ZERO)) {
								BigDecimal indivisualVoucherAmount = subSalesOrderItem.getDiscount()
										.divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
										.setScale(4, RoundingMode.HALF_UP);
								BigDecimal cancelVoucherAmount = indivisualVoucherAmount.multiply(qtyCancelled)
										.setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
								totalVoucherToRefund = totalVoucherToRefund.add(cancelVoucherAmount);
							}
						}
					} else {

						priceIncludeTax = priceIncludeTax.multiply(qtyCancelled).setScale(2, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);

					}


					if (store != null && item.getShukranCoinsBurned() != null && item.getShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
						BigDecimal itemTotalBurned = item.getShukranCoinsBurned();
						BigDecimal itemQtyOrdered = item.getQtyOrdered();
						BigDecimal itemPointsToRefund = itemTotalBurned.divide(itemQtyOrdered, 6, RoundingMode.HALF_UP).multiply(qtyCancelled).setScale(2, RoundingMode.HALF_UP);
						BigDecimal itemValueToRefund = itemPointsToRefund.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
						
						LOGGER.info("cancelUnfulfiorder: Item Shukran calculation - SKU: " + item.getSku() + 
								", TotalBurned: " + itemTotalBurned + 
								", QtyOrdered: " + itemQtyOrdered + 
								", QtyCancelled: " + qtyCancelled + 
								", PointsToRefund: " + itemPointsToRefund + 
								", ValueToRefund: " + itemValueToRefund);
						
						BigDecimal beforeSubtract = totalShukranCoinsNeedsToBeLockedAgain;
						totalAmountToRefund = totalAmountToRefund.subtract(itemValueToRefund);
						totalShukranCoinsNeedsToBeLockedAgain = totalShukranCoinsNeedsToBeLockedAgain.subtract(itemPointsToRefund);
						
						LOGGER.info("cancelUnfulfiorder: After item calculation - Before subtract: " + beforeSubtract + 
								", Points subtracted: " + itemPointsToRefund + 
								", Remaining to lock: " + totalShukranCoinsNeedsToBeLockedAgain);
					}

				}

				EASPartialCancelRefundResponse eASPartialCancelRefundResponse = new EASPartialCancelRefundResponse();
				if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
					// EAS coins subtract for partial cancellation
					eASPartialCancelRefundResponse = eASServiceImpl.easPartialCancelRefund(order, request.getOrderItems(), httpRequestHeadrs.get(Constants.deviceId));
				}


				response.setTotalCodCancelledAmount(df.format(totalAmountToRefund).toString());

				BigDecimal storeCreditAmount = order.getAmstorecreditAmount();

				if (OrderConstants.checkPaymentMethod(paymentMethod)) {
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund, eASPartialCancelRefundResponse);
					//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
					BigDecimal calcultedcancelAmount = onlineForward(order, store, fortId, paymentMethod, storeCreditAmount, totalAmountToRefund,
							beforeCancelledAmount, skumApList, isFullyCancellation, itemList, actualskumApList, totalVoucherToRefund, currentOrderValue);
					LOGGER.info(paymentMethod+ " and cancel amount:" + calcultedcancelAmount);
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {

					CancelDetails details = new CancelDetails();
					details.setCurrentOrderValue(currentOrderValue);
					/** EAS Coin amount to be subtracted **/

					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund, eASPartialCancelRefundResponse);

					BigDecimal calcultedcancelAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order, totalAmountToRefund,
							storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);

					LOGGER.info("tamrara cancel amount:" + calcultedcancelAmount);

					tamaraHelper.cancelPayment(order, calcultedcancelAmount.toString());

					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addStoreCredit(order, storeCreditAmount, details);

					}
					setOrderGrid(order, isFullyCancellation, orderStatus);
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (OrderConstants.checkTabbyPaymentMethod(paymentMethod) && isFullyCancellation) {
					totalVoucherToRefund = BigDecimal.ZERO;// Voucher not refunded for Full cancellation
					LOGGER.info("tabyy fullycancellation");
					tabbyHelper.closePayment(order.getSubSalesOrder().getPaymentId());
					CancelDetails details = new CancelDetails();
					details.setCurrentOrderValue(currentOrderValue);
					// EAS Coin amount to be subtracted
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund, eASPartialCancelRefundResponse);
					BigDecimal calcultedcancelAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order, totalAmountToRefund,
							storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);
					LOGGER.info(paymentMethod+ " cancelled amount:" + calcultedcancelAmount);

					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addStoreCredit(order, storeCreditAmount, details);

					}

					setOrderGrid(order, isFullyCancellation, orderStatus);
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (OrderConstants.checkTabbyPaymentMethod(paymentMethod) &&
						null != storeCreditAmount && !(storeCreditAmount.compareTo(BigDecimal.ZERO) == 0)
						&& !isFullyCancellation) {

					LOGGER.info("tabyy partially fullycancellation");
					CancelDetails details = new CancelDetails();
					details.setCurrentOrderValue(currentOrderValue);
					// EAS Coin amount to be subtracted
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund, eASPartialCancelRefundResponse);
					BigDecimal calcultedcancelAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order, totalAmountToRefund, storeCreditAmount,
							details, isFullyCancellation, paymentMethod, totalVoucherToRefund);
					LOGGER.info(paymentMethod+ " canceled amount:" + calcultedcancelAmount);

					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addStoreCredit(order, storeCreditAmount, details);
					}
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
					CancelDetails details = new CancelDetails();
					details.setCurrentOrderValue(currentOrderValue);
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund,
							eASPartialCancelRefundResponse);
					BigDecimal calcultedcancelAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order, totalAmountToRefund,
							storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);

					LOGGER.info("cashfree cancel amount:" + calcultedcancelAmount);

					paymentUtility.initiateRefund(order, calcultedcancelAmount.toString(), paymentMethod);
					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addStoreCredit(order, storeCreditAmount, details);
					}
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else {
					if (null != storeCreditAmount && !(storeCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
						totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund,
								eASPartialCancelRefundResponse);
						BigDecimal calculatedStoreCredit = paymentDtfRefundHelper.getCancelledStoreCreditWithCurrentOrderValue(order,
								store, totalAmountToRefund, beforeCancelledAmount, isFullyCancellation, paymentMethod,currentOrderValue);
						if (null != calculatedStoreCredit && !(calculatedStoreCredit.compareTo(BigDecimal.ZERO) == 0)) {

							response.setTotalCodCancelledAmount(calculatedStoreCredit.toString());

							//	String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());

							SalesOrderGrid grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);

							grid.setStatus(orderStatus);
//						SalesCreditmemo memo = refundHelper.createCreditMemo(order, memoIncrementId, store, null,
//								calculatedStoreCredit, paymentMethod, skumApList,isFullyCancellation);
//						refundHelper.createCancelCreditmemoItems(order, memo, skumApList,itemList);
//						refundHelper.createCreditmemoComment(memo, calculatedStoreCredit);
//						refundHelper.createCreditmemoGrid(order, memo, memoIncrementId, grid, order.getBaseGrandTotal());

							BigDecimal calculatedBaseStoreCredit = calculatedStoreCredit.multiply(order.getStoreToBaseRate())
									.setScale(4, RoundingMode.HALF_UP);
							Boolean isVoucherApplied = false;
							if (Boolean.TRUE.equals(!isFullyCancellation) && !totalVoucherToRefund.equals(BigDecimal.ZERO)) {
								calculatedBaseStoreCredit = calculatedBaseStoreCredit.add(totalVoucherToRefund);
								isVoucherApplied = true;
							}
							//refundHelper.releaseStoreCredit(order, calculatedBaseStoreCredit);
							paymentDtfRefundHelper.addStoreCredit(order, calculatedBaseStoreCredit, isVoucherApplied);
							salesOrderGridRepository.saveAndFlush(grid);

						} else {
							if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(totalAmountToRefund));
							SalesOrderGrid grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
							grid.setStatus(orderStatus);
							salesOrderGridRepository.saveAndFlush(grid);
						}


					} else {
						CancelDetails details = new CancelDetails();
						details.setCurrentOrderValue(currentOrderValue);
						totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, order, totalAmountToRefund, eASPartialCancelRefundResponse);
						BigDecimal calcultedcancelAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order, totalAmountToRefund,
								storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);
						if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
						setOrderGrid(order, isFullyCancellation, orderStatus);

					}

					if (order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
						order.setWmsPullStatus(2);
						orderHelper.updateStatusHistory(order, false, false, false, true, false);
					}


					salesOrderRepository.saveAndFlush(order);
				}
				// EAS call for Partial cancellation moved to API call easPartialCancelRefund
				// eASServiceImpl.publishCancelPartialOrderToKafka(order, request.getOrderItems());
				String skuListString;
				try {
					skuListString = mapper.writeValueAsString(skumApList);
					skuListString = OrderConstants.SELLER_CANCELLED_MSG + skuListString;
					updateOrderStatusHistory(order, skuListString, "order", order.getStatus());

				} catch (JsonProcessingException e1) {

					LOGGER.error("error during sku string parse");
				}
				LOGGER.info("cancelUnfulfiorder: After processing all items - isFullyCancellation: " + isFullyCancellation + 
						", totalShukranCoinsNeedsToBeLockedAgain: " + totalShukranCoinsNeedsToBeLockedAgain);
				
				// Lock back points for non-cancelled items (partial cancellation only)
				if (!isFullyCancellation && store != null && totalShukranCoinsNeedsToBeLockedAgain.compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal pointsToLock = totalShukranCoinsNeedsToBeLockedAgain.setScale(0, RoundingMode.HALF_UP);
					LOGGER.info("cancelUnfulfiorder: Locking back remaining Shukran points for non-cancelled items - Points: " + pointsToLock);
					
					// Only lock if we previously unlocked (i.e., customerTotalPoints check passed)
					if (customerTotalPoints != null && customerTotalPoints.compareTo(BigDecimal.ZERO) >= 0) {
						commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(), pointsToLock.toBigInteger().toString(), order.getSubSalesOrder().getQuoteId(), true, order, store, "Shukran Burned Points On Unfulfilled Order", "Seller Cancelation Api");
						LOGGER.info("cancelUnfulfiorder: Successfully called lockUnlockShukranData to lock back " + pointsToLock + " points");
					} else {
						LOGGER.info("cancelUnfulfiorder: Skipping lockUnlockShukranData call (customerTotalPoints check failed), but will still update SubSalesOrder");
					}

					// Always update SubSalesOrder with remaining burned points
					SubSalesOrder subSalesOrder = order.getSubSalesOrder();
					subSalesOrder.setTotalShukranCoinsBurned(new BigDecimal(pointsToLock.toBigInteger()));
					subSalesOrder.setTotalShukranBurnedValueInBaseCurrency(totalShukranCoinsNeedsToBeLockedAgain.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP));
					subSalesOrder.setTotalShukranBurnedValueInCurrency(totalShukranCoinsNeedsToBeLockedAgain.multiply(store.getShukranPointConversion() != null ? store.getShukranPointConversion() : BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP));
					subSalesOrder.setShukranLocked(0);
					subSalesOrderRepository.saveAndFlush(subSalesOrder);
					
					LOGGER.info("cancelUnfulfiorder: Successfully updated SubSalesOrder with remaining burned points: " + pointsToLock);
				} else if (isFullyCancellation) {
					LOGGER.info("cancelUnfulfiorder: Full cancellation - All Shukran points remain unlocked (not locking back)");
				} else if (totalShukranCoinsNeedsToBeLockedAgain.compareTo(BigDecimal.ZERO) <= 0) {
					LOGGER.warn("cancelUnfulfiorder: No points to lock back - totalShukranCoinsNeedsToBeLockedAgain: " + totalShukranCoinsNeedsToBeLockedAgain + ". This might indicate all items were cancelled or calculation issue.");
				} else if (store == null) {
					LOGGER.warn("cancelUnfulfiorder: Cannot lock back points - store is null");
				}

				// For split orders: Lock back burned points from other shipments that are not cancelled
				if (Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder()) && store != null && !isFullyCancellation) {
					try {
						LOGGER.info("cancelUnfulfiorder: Checking for other shipments in split order. Parent orderId: " + order.getEntityId());
						List<SplitSalesOrder> allSplitOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId());
						
						if (CollectionUtils.isNotEmpty(allSplitOrders)) {
							BigDecimal otherShipmentsBurnedPoints = BigDecimal.ZERO;
							
							for (SplitSalesOrder splitOrder : allSplitOrders) {
								// Check if this split order (shipment) is NOT cancelled
								if (splitOrder.getStatus() == null || !splitOrder.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
									// Get burned points from this split order's SplitSubSalesOrder
									if (splitOrder.getSplitSubSalesOrder() != null 
											&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null
											&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
										otherShipmentsBurnedPoints = otherShipmentsBurnedPoints.add(splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
										LOGGER.info("cancelUnfulfiorder: Found non-cancelled shipment (splitOrderId: " + splitOrder.getEntityId() 
												+ ", incrementId: " + splitOrder.getIncrementId() 
												+ ") with burned points: " + splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
									}
								} else {
									LOGGER.info("cancelUnfulfiorder: Skipping cancelled shipment (splitOrderId: " + splitOrder.getEntityId() 
											+ ", incrementId: " + splitOrder.getIncrementId() + ", status: " + splitOrder.getStatus() + ")");
								}
							}
							
							// Lock back points from other shipments if any
							if (otherShipmentsBurnedPoints.compareTo(BigDecimal.ZERO) > 0) {
								LOGGER.info("cancelUnfulfiorder: Locking back burned points from other shipments. Total points: " + otherShipmentsBurnedPoints);
								commonService.lockUnlockShukranData(
										order.getSubSalesOrder().getCustomerProfileId(), 
										otherShipmentsBurnedPoints.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString(), 
										order.getSubSalesOrder().getQuoteId(), 
										true, // true = LOCK
										order, 
										store, 
										"Shukran Burned Points On Other Shipments (Not Cancelled)", 
										"Seller Cancelation Api - Split Order"
								);
								LOGGER.info("cancelUnfulfiorder: Successfully locked back " + otherShipmentsBurnedPoints + " points from other shipments");
							} else {
								LOGGER.info("cancelUnfulfiorder: No burned points found in other shipments to lock back");
							}
						} else {
							LOGGER.info("cancelUnfulfiorder: No split orders found for parent orderId: " + order.getEntityId());
						}
					} catch (Exception e) {
						LOGGER.error("cancelUnfulfiorder: Error while locking back points from other shipments in split order. OrderId: " + order.getEntityId() + ". Error: " + e.getMessage(), e);
						// Don't throw exception - continue with cancellation even if this fails
					}
				}

				orderHelper.releaseInventoryQty(order, skumApList, false, OrderConstants.RELEASE_SELLER_CANCELLATION);
				if (store != null && store.getIsShukranEnable() && order.getSubSalesOrder() != null && order.getEntityId() != null && StringUtils.isNotEmpty(order.getSubSalesOrder().getTenders()) && StringUtils.isNotBlank(order.getSubSalesOrder().getTenders())) {
					resetShukranTenders(order.getSubSalesOrder().getTenders(), order.getEntityId(), store);
				}

			} else {

				response.setHasError(true);
				response.setErrorMessage("bad request");
				return response;
			}
			orderHelper.cancelSellerOrders(order, null, request);

			// EAS Service Call for Styli Coins Refund on Seller Cancellation
			if (order != null) {
				eASServiceImpl.handleSellerCancellationStyliCoinsRefund(
					order.getIncrementId(),
					order.getCustomerId(),
					order.getCustomerIsGuest(),
					store,
					request.getOrderItems(),
					httpRequestHeadrs.get(Constants.deviceId),
					false, // isSplitOrder
					null,  // splitSalesOrder
					order   // salesOrder
				);
			}

			response.setHasError(false);
			return response;
		} catch (Exception e) {
			LOGGER.info("Cancel Unfulfilment Order "+ e.getMessage());
			throw new RuntimeException(e);
		}

	}

	private void setOrderGrid(SalesOrder order, Boolean isFullyCancellation, String orderStatus) {
		SalesOrderGrid grid = salesOrderGridRepository.findByEntityId(order.getEntityId());

		if (grid != null && isFullyCancellation) {
			order.getSubSalesOrder().setIsUnfulfilmentOrder(2);
			order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			salesOrderGridRepository.saveAndFlush(grid);
			paymentUtility.publishToSplitPubSubOTSForSalesOrder(order,"4.0","Closed");
		} else if (null != grid) {
			grid.setStatus(orderStatus);
			order.setStatus(orderStatus);
			salesOrderGridRepository.saveAndFlush(grid);
		}
	}


	private void addStoreCredit(SalesOrder order, BigDecimal storeCreditAmount, CancelDetails details) {
		BigDecimal amastyBaseAmount = details.getAmastyBaseStoreCredit();
		
		BigDecimal cancelledAmastyAmount = new BigDecimal("0");
		String OrderActionData = "[\"".concat(order.getIncrementId()).concat("\"]");
		LOGGER.info("OrderActionData:" + OrderActionData);

		List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
				.findByActionDataAndAction(OrderActionData,0);
		
		if(CollectionUtils.isNotEmpty(amastyHistoryList)) {
			
			for(AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {
				
				cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
			}
			
			BigDecimal newAmastyAount = new BigDecimal("0");
			
			newAmastyAount = newAmastyAount.add(cancelledAmastyAmount).add(amastyBaseAmount);
			
			BigDecimal difference = order.getAmstorecreditBaseAmount().subtract(newAmastyAount);
			BigDecimal constantDifference = new BigDecimal("0.3");
			if(difference.compareTo(constantDifference)== 1) {
				
				amastyBaseAmount = amastyBaseAmount.add(difference);
			}
			
		}
		
		if (null != storeCreditAmount && amastyBaseAmount.compareTo(BigDecimal.ZERO) != 0) {
			paymentDtfRefundHelper.addStoreCredit(order, amastyBaseAmount, details.isGiftVoucher());

		}
	}


	private void releaseStoreCredit(SalesOrder order, Boolean isFullyCancellation, OmsUnfulfilmentResponse response,
			Stores store, String paymentMethod, BigDecimal totalAmountToRefund, BigDecimal beforeCancelledAmount) {
		BigDecimal calculatedStoreCredit = paymentDtfRefundHelper.getCancelledStoreCredit(order,
				 store,totalAmountToRefund,beforeCancelledAmount, isFullyCancellation,paymentMethod);
		if(null != calculatedStoreCredit && calculatedStoreCredit.compareTo(BigDecimal.ZERO) !=0 ) {
			response.setTotalCodCancelledAmount(calculatedStoreCredit.toString());
			BigDecimal calculatedBaseStoreCredit = calculatedStoreCredit.multiply(order.getStoreToBaseRate())
					.setScale(4,RoundingMode.HALF_UP);
			refundHelper.releaseStoreCredit(order, calculatedBaseStoreCredit);
		}
	}


	/**
	 * @param order
	 * @param itemList
	 * @param skumApList
	 * @param canceledItem
	 * @param actualskumApList
	 */
	private void calculateCancelledValue(SalesOrder order, List<SalesOrderItem> itemList,
										 Map<String, BigDecimal> skumApList, OrderPushItem canceledItem, Map<String, BigDecimal> actualskumApList) {
		for (SalesOrderItem item : order.getSalesOrderItem()) {

			LOGGER.info("item:" + item.getItemId());
			LOGGER.info("canceledItem:" + canceledItem.getOrderItemCode());
			
			if (item.getItemId().equals(Integer.parseInt(canceledItem.getOrderItemCode()))) {

				BigDecimal qtyOrdered = item.getQtyOrdered();
				BigDecimal qtyCanclled = item.getQtyCanceled();
					
				if (null != qtyOrdered && null != qtyCanclled
						&& qtyOrdered.intValue() > qtyCanclled.intValue()
						&& null !=canceledItem.getCancelledQuantity()
						&& null != item.getSellerQtyCancelled()
						&& item.getSellerQtyCancelled().compareTo(canceledItem.getCancelledQuantity()) != 0) {

					final BigDecimal previousCancelVal = item.getSellerQtyCancelled();

					order.getSalesOrderItem().stream().forEach(i -> {
						if (i.getSku().equalsIgnoreCase(canceledItem.getChannelSkuCode())) {

							i.setQtyCanceled(canceledItem.getCancelledQuantity());

							BigDecimal totalSellerCancelled = BigDecimal.ZERO;

							totalSellerCancelled = previousCancelVal.subtract(canceledItem.getCancelledQuantity())
									.abs();

							i.setSellerQtyCancelled(canceledItem.getCancelledQuantity());
							skumApList.put(canceledItem.getChannelSkuCode(), totalSellerCancelled);
							actualskumApList.put(canceledItem.getChannelSkuCode(), totalSellerCancelled);
							

						}
					});

					itemList.add(item);
					break;

				}else if(null != qtyOrdered
						&& (null == item.getSellerQtyCancelled() || item.getSellerQtyCancelled().intValue() == 0 )) {
					
					final BigDecimal previousCancelVal = new BigDecimal(0);

					order.getSalesOrderItem().stream().forEach(i -> {
						if (i.getSku().equalsIgnoreCase(canceledItem.getChannelSkuCode())) {

							i.setQtyCanceled(canceledItem.getCancelledQuantity());

							BigDecimal totalSellerCancelled = BigDecimal.ZERO;

							totalSellerCancelled = previousCancelVal.subtract(canceledItem.getCancelledQuantity())
									.abs();

							i.setSellerQtyCancelled(totalSellerCancelled);
							skumApList.put(canceledItem.getChannelSkuCode(), totalSellerCancelled);
							actualskumApList.put(canceledItem.getChannelSkuCode(), canceledItem.getCancelledQuantity());
							

						}
					});
					itemList.add(item);
					break;
				}

			}
		}
	}
	
	/**
	 * @param request
	 * @param order
	 */
	/**
	 * @param order
	 * @param message
	 * @param entity
	 * @param status
	 */
	public void updateOrderStatusHistory( SalesOrder order, String message
			,String entity, String status) {
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


	private boolean validateRequest(SalesOrder order, OrderunfulfilmentRequest request) {

		for (OrderPushItem canceledItem : request.getOrderItems()) {

			if (null != canceledItem.getCancelledQuantity() && null != canceledItem.getOrderItemCode()) {

				for (SalesOrderItem item : order.getSalesOrderItem()) {

					LOGGER.info("item id:" + item.getItemId());
					LOGGER.info("canceledItem:" + canceledItem.getOrderItemCode());
					if (item.getItemId().equals(Integer.parseInt(canceledItem.getOrderItemCode()))) {

						BigDecimal qtyOrdered = item.getQtyOrdered();

						if (null != item.getQtyCanceled()) {

							BigDecimal totalCancelledQty = canceledItem.getCancelledQuantity();

							if (totalCancelledQty.intValue() > qtyOrdered.intValue()) {

								return false;

							}

						}

					}
				}

			}

		}

		return true;
	}


	private BigDecimal onlineForward(SalesOrder order, Stores store, String fortId, String paymentMethod,
			BigDecimal storeCreditAmount, BigDecimal calcultedcancelAmount, BigDecimal beforeCancelledAmount
			, Map<String, BigDecimal> skuMapList, Boolean isFullyCancellation, List<SalesOrderItem> itemList
			,Map<String, BigDecimal> actualSkuMapList, BigDecimal totalVoucherToRefund,BigDecimal currentOrderValue) {
		
		
		boolean payfortRefundOnSellerCancellation = Constants.orderCredentials.getPayfort().isPayfortRefundOnSellerCancellation();
		boolean isMadaTransaction = false;

		if (order != null && order.getEntityId() != null) {
		    int count = salesOrderRepository.checkIfMadaTransaction(order.getEntityId());
		    isMadaTransaction = count > 0;
		}
		String orderIncrementId = order != null ? order.getIncrementId() : "null";
		LOGGER.info("Seller cancellation check — Order " + orderIncrementId + " consul flag is : " + payfortRefundOnSellerCancellation);
		LOGGER.info("Seller cancellation check — Order " + orderIncrementId + " is MADA transaction: " + isMadaTransaction);
		
		boolean isSellerCancelDoneBefore = false;
		    if (order != null && order.getEntityId() != null) {
		        int sellerCancelCount = salesOrderRepository.checkIfSellerCancelExists(order.getEntityId());
		        isSellerCancelDoneBefore = sellerCancelCount > 0;
		    }
		    LOGGER.info("Seller cancellation check — Order " + orderIncrementId + " has previous seller cancellations: " + isSellerCancelDoneBefore);

		CancelDetails details = new CancelDetails();
		details.setCurrentOrderValue(currentOrderValue);
		calcultedcancelAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order, calcultedcancelAmount, storeCreditAmount,details
				,isFullyCancellation,paymentMethod, totalVoucherToRefund);
		

		
//		RefundPaymentRespone paymentresponse = paymentDtfRefundHelper.payfortRefundcall(order, calcultedcancelAmount,
//				fortId, paymentMethod);
		
//		RefundPaymentRespone paymentresponse = new RefundPaymentRespone();
//		paymentresponse.setStatusCode("200");
		
		if (order == null) {
			return calcultedcancelAmount;
		}
		PayfortConfiguration configuration = new PayfortConfiguration();
		paymentDtfRefundHelper.getPayfortConfDetails(order.getStoreId().toString(), paymentMethod, configuration);
		try {
			LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
		} catch (JsonProcessingException e) {
			LOGGER.error("error during write configuration:" + e.getMessage());
		}
		
		
		RefundPaymentRespone paymentresponse = new RefundPaymentRespone();

		if (order.getPayfortAuthorized() != null && order.getPayfortAuthorized() == 1 && isFullyCancellation && !isSellerCancelDoneBefore) {
		    // Case 1: Fully cancelled, not seller-cancelled earlier — do a void authorization
		    paymentresponse = paymentDtfRefundHelper.payfortVoidAuthorizationcall(order, fortId, paymentMethod);

		} else if (order.getPayfortAuthorized() != null && order.getPayfortAuthorized() == 1 && isMadaTransaction && payfortRefundOnSellerCancellation) {
		    // Case 2: Authorized Mada transaction — capture first, then refund
		    LOGGER.info("Seller cancellation check — Order " + order.getIncrementId() +
		        " has payfort authorised flag: " + order.getPayfortAuthorized() +
		        " for payment method: " + paymentMethod + 
		        " where refund amount is: " + calcultedcancelAmount);

		    // Prepare and send capture request
		    PayfortPaymentCaptureRequest captureRequest = paymentUtility.preparePayfortCaptureRequest(configuration, order, calcultedcancelAmount, fortId);
		    PayfortReposne captureResponse = paymentUtility.triggerPayfortPaymentCaptureRestApiCall(captureRequest, order, configuration);

		    if (captureResponse != null && captureResponse.isStatus()) {
		        LOGGER.info("Payfort capture successful for order " + order.getIncrementId() + ", proceeding to refund");
		        paymentresponse = paymentDtfRefundHelper.payfortRefundcall(order, calcultedcancelAmount, fortId, paymentMethod);
		    } else {
		        LOGGER.error("Payfort capture failed for order " + order.getIncrementId() + ", skipping refund.");
		    }

		} else if (order.getPayfortAuthorized() != null && order.getPayfortAuthorized() != 1) {
		    paymentresponse = paymentDtfRefundHelper.payfortRefundcall(order, calcultedcancelAmount, fortId, paymentMethod);
		}

		
		if (null != paymentresponse.getStatusCode() && paymentresponse.getStatusCode().equals("200")) {

			//String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
			SalesOrderGrid grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
			grid.setStatus(order.getStatus());
//			SalesCreditmemo memo = refundHelper.createCreditMemo(order, memoIncrementId, store, calcultedcancelAmount,
//					details.getAmasyStoreCredit(), paymentMethod, skuMapList,isFullyCancellation);
//			refundHelper.createCancelCreditmemoItems(order, memo, skuMapList, itemList);
//			refundHelper.createCreditmemoComment(memo, details.getAmasyStoreCredit());
//			refundHelper.createCreditmemoGrid(order, memo, memoIncrementId, grid, order.getBaseGrandTotal());

			if (null != details.getAmastyBaseStoreCredit()
					&& !(details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) == 0)) {

				//refundHelper.releaseStoreCredit(order, details.getAmastyBaseStoreCredit());
				if(!isFullyCancellation) {
					
				}
				paymentDtfRefundHelper.addStoreCredit(order, details.getAmastyBaseStoreCredit(), details.isGiftVoucher());

			}
//			if (null != order.getGiftVoucherDiscount()
//					&& !(order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) == 0)
//					&& isFullyCancellation) {
//				paymentDtfRefundHelper.addStoreCredit(order, order.getGiftVoucherDiscount(),true);
//				paymentDtfRefundHelper.setReturnVoucherValueInDB(order, order.getGiftVoucherDiscount());
//			}

			if (!order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				order.setWmsPullStatus(2);

			}

			salesOrderRepository.saveAndFlush(order);
		}else if (null != order.getPayfortAuthorized() && order.getPayfortAuthorized() == 1 && !isFullyCancellation
				&& null != details.getAmastyBaseStoreCredit()
				&& !(details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) == 0)) {
			paymentDtfRefundHelper.addStoreCredit(order, details.getAmastyBaseStoreCredit(),details.isGiftVoucher() );
			
			if (!order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				order.setWmsPullStatus(2);

			}
			salesOrderRepository.saveAndFlush(order);
		}

		if (isFullyCancellation) {
			SalesOrderGrid grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
			salesOrderGridRepository.saveAndFlush(grid);
		}

		return calcultedcancelAmount;
	}
	
	private BigDecimal coinSubtractOncancel(Boolean isFullyCancellation, SalesOrder order, BigDecimal totalAmountToRefund, EASPartialCancelRefundResponse eASPartialCancelRefundResponse) {
		if(Objects.isNull(eASPartialCancelRefundResponse.getCoinAmountRefunded())) {
			return totalAmountToRefund;
		}
		BigDecimal newTotalAmountToRefund = totalAmountToRefund;
		if(isFullyCancellation) {
			LOGGER.info("EAS easPartialCancelRefund isFullyCancellation");
			if(null != order.getSubSalesOrder() && null != eASPartialCancelRefundResponse.getEasValueInBaseCurrency()) {
				LOGGER.info("EAS easPartialCancelRefund coinAmountRefunded: " + eASPartialCancelRefundResponse.getEasValueInBaseCurrency() + ", totalAmountToRefund: " + totalAmountToRefund);
				newTotalAmountToRefund = totalAmountToRefund.subtract(eASPartialCancelRefundResponse.getEasValueInBaseCurrency());
			}
		}else {
			LOGGER.info("EAS easPartialCancelRefund coinAmountRefunded: " + eASPartialCancelRefundResponse.getCoinAmountRefunded() + ", totalAmountToRefund: " + totalAmountToRefund);
			newTotalAmountToRefund = totalAmountToRefund.subtract(eASPartialCancelRefundResponse.getCoinAmountRefunded());
		}
		return newTotalAmountToRefund;
	}


	public OmsOrderresponsedto ordersHoldwmspush(List<SalesOrder> orderList, List<SplitSalesOrder> splitSalesOrderList) {
		
		OmsOrderresponsedto response = new OmsOrderresponsedto();
		for (SalesOrder order : orderList) {
			if (CollectionUtils.isNotEmpty(orderList)) {
				restcallForwmsUnholdorderPush(order);
			}
		}

		for (SplitSalesOrder splitSalesOrder : splitSalesOrderList) {
			if (CollectionUtils.isNotEmpty(splitSalesOrderList)) {
				restcallForwmsUnholdorderPushForSplitOrder(splitSalesOrder);
			}
		}
		response.setStatus(true);
		response.setStatusCode("200");
		response.setStatusMsg("pushed successfully");

		return response;
	}
	
	private void restcallForwmsUnholdorderPush(SalesOrder createdOrder) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		
		if(null !=createdOrder.getSubSalesOrder().getWarehouseLocationId()
				&& createdOrder.getSubSalesOrder().getWarehouseLocationId().intValue()== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			
		}else if(createdOrder.getStoreId().equals(1) || createdOrder.getStoreId().equals(3) || createdOrder.getStoreId().equals(51)) {
			
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}else {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}
		
		OrderPushRequest orderPushRequest = new OrderPushRequest();
		
		DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
		Date createDate = new Date(createdOrder.getCreatedAt().getTime());

		/** add dispatch delivery time **/
		Date currentDate = new Date();
		Calendar c = Calendar.getInstance();
		c.setTime(currentDate);
		if(null != Constants.orderCredentials.getWms() 
				&& null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
			c.add(Calendar.DATE, Constants.orderCredentials.getWms().getWmsdispatchDaysNumber());
		}

		Date dispatchDeliveryDate = c.getTime();
		
		String orderTime = dateFormat.format(createDate).toString();
		orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
		dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		
		LOGGER.info("wms order time:"+orderTime);
		LOGGER.info("wms dispatch time :"+dispatchTime);
		orderPushRequest.setStartProcessingTime(orderTime);
		orderPushRequest.setOnHold(false);
		orderPushRequest.setDispatchByTime(dispatchTime);
		
		if(null != createdOrder.getSubSalesOrder().getWarehouseLocationId()) {
    		orderPushRequest.setLocationCode(createdOrder.getSubSalesOrder().getWarehouseLocationId().toString());
		}
		
		HttpEntity<OrderPushRequest> requestBody = new HttpEntity<>(orderPushRequest, requestHeaders);
	
		String url = null;
		
		 url = Constants.orderCredentials.getOrderDetails().getWmsUrl()+"/orders/{orderCode}";
		Map<String, Object> parameters = new HashMap<>();

		parameters.put("orderCode", createdOrder.getIncrementId());
		

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("WMS unhold  URl:" + builder.buildAndExpand(parameters).toUri());

		try {

			LOGGER.info("wms unhold request body:" + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("wms unhold response body:" + mapper.writeValueAsString(response));
			
			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {

				LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));

			}
			salesOrderRepository.updateHoldOrderPushStatus(0, createdOrder.getEntityId());
			salesOrderRepository.updateWMSStatus(1, createdOrder.getEntityId());
		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("Exception occurred  during unhold WMS call:" + createdOrder.getIncrementId());
			LOGGER.error("Exception occurred  during unhold REST call:" + e.getMessage());

		}

	}
	

	private void restcallForwmsUnholdorderPushForSplitOrder(SplitSalesOrder splitSalesOrder) {

		SalesOrder createdOrder = splitSalesOrder.getSalesOrder();

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		
		if(null !=createdOrder.getSubSalesOrder().getWarehouseLocationId()
				&& createdOrder.getSubSalesOrder().getWarehouseLocationId().intValue()== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			
		}else if(createdOrder.getStoreId().equals(1) || createdOrder.getStoreId().equals(3) || createdOrder.getStoreId().equals(51)) {
			
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}else {
			
			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}
		
		OrderPushRequest orderPushRequest = new OrderPushRequest();
		
		DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
		Date createDate = new Date(createdOrder.getCreatedAt().getTime());

		/** add dispatch delivery time **/
		Date currentDate = new Date();
		Calendar c = Calendar.getInstance();
		c.setTime(currentDate);
		if(null != Constants.orderCredentials.getWms() 
				&& null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
			c.add(Calendar.DATE, Constants.orderCredentials.getWms().getWmsdispatchDaysNumber());
		}

		Date dispatchDeliveryDate = c.getTime();
		
		String orderTime = dateFormat.format(createDate).toString();
		orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
		dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		
		LOGGER.info("wms order time:"+orderTime);
		LOGGER.info("wms dispatch time :"+dispatchTime);
		orderPushRequest.setStartProcessingTime(orderTime);
		orderPushRequest.setOnHold(false);
		orderPushRequest.setDispatchByTime(dispatchTime);
		
		if(null != createdOrder.getSubSalesOrder().getWarehouseLocationId()) {
    		orderPushRequest.setLocationCode(createdOrder.getSubSalesOrder().getWarehouseLocationId().toString());
		}
		
		HttpEntity<OrderPushRequest> requestBody = new HttpEntity<>(orderPushRequest, requestHeaders);
	
		String url = null;
		
		 url = Constants.orderCredentials.getOrderDetails().getWmsUrl()+"/orders/{orderCode}";
		Map<String, Object> parameters = new HashMap<>();

		parameters.put("orderCode", splitSalesOrder.getIncrementId());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("WMS unhold  URl for split order:" + builder.buildAndExpand(parameters).toUri());

		try {

			LOGGER.info("wms unhold request body for split order:" + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
					HttpMethod.PUT, requestBody, Object.class);

			LOGGER.info("wms unhold response body for split order:" + mapper.writeValueAsString(response));
			
			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200) {

				LOGGER.info("response Body for split order:" + mapper.writeValueAsString(response.getBody()));

			}
			splitSalesOrderRepository.updateHoldOrderPushStatusForSplitOrder(0, splitSalesOrder.getEntityId());
		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("Exception occurred  during split order unhold WMS call:" + splitSalesOrder.getIncrementId());
			LOGGER.error("Exception occurred  during split order unhold REST call:" + e.getMessage());

		}

	}
	

	public OmsOrderresponsedto orderHoldFalseInWms(List<SalesOrder> orderList) {
		OmsOrderresponsedto response = new OmsOrderresponsedto();
		List<String> incrementIds = new ArrayList<>();

		try {
			 if (CollectionUtils.isNotEmpty(orderList)) {
		            for (SalesOrder order : orderList) {
		                LOGGER.info("Orders to be processed for setting hold false in WMS: Entity ID: " + order.getEntityId());
		                incrementIds.add(order.getIncrementId());
		                salesOrderRepository.updateHoldOrderFalseInWms(order.getEntityId());
		            }

		            if (!incrementIds.isEmpty()) {
		                String ids = incrementIds.stream().collect(Collectors.joining(", "));
		                LOGGER.info("All Increment IDs processed for setting hold false in WMS: " + ids);
		                System.out.println("All Increment IDs processed for setting hold false in WMS: " + ids);
		                
					String body = "Hi,\nPlease find the orders processed for setting hold false in WMS.\n"
							+ ids + ".\nThanks";
					emailService.sendText(Constants.orderCredentials.getWms().getTechSupportEmail(),
							env + " Orders processed for setting hold false in WMS", body);
				}
				response.setStatus(true);
				response.setStatusCode("200");
				response.setStatusMsg("WMS hold set to false successfully for " + incrementIds.stream().collect(Collectors.joining(", ")));
			} else {
				response.setStatus(false);
				response.setStatusCode("204");
				response.setStatusMsg("Order list is empty");
			}
		} catch (Exception e) {
			response.setStatus(false);
			response.setStatusCode("500");
			response.setStatusMsg("An error occurred: " + e.getMessage());
		}

		return response;
	}
	
	public OmsOrderresponsedto triggerEmailForOrdersNotRefunded(List<String> orderList) {
		OmsOrderresponsedto response = new OmsOrderresponsedto();
		WmsDetails email = Constants.orderCredentials.getWms();
		try {
			if (orderList != null) {
				LOGGER.info("Please find the pending refund orders list: " + orderList);
				String body = "Hi,\nPlease find the pending refund orders list.\n" + orderList + ".\nThanks";
				emailService.sendText(email.getTechSupportEmail(),env + " Pending refund orders list ", body);

				response.setStatus(true);
				response.setStatusCode("200");
				response.setStatusMsg(
						"Email sent for pending refund orders " + orderList.stream().collect(Collectors.joining(", ")));
			} else {
				response.setStatus(false);
				response.setStatusCode("204");
				response.setStatusMsg("Order list is empty");
			}
		} catch (Exception e) {
			response.setStatus(false);
			response.setStatusCode("500");
			response.setStatusMsg("An error occurred: " + e.getMessage());
		}
		return response;
	}

	public void resetShukranTenders(String tenders, Integer orderId, Stores store){
		String response= tenders;
		ObjectMapper objectMapper= new ObjectMapper();
		SalesOrder order= salesOrderRepository.findByEntityId(orderId);
		if(order != null && order.getSubSalesOrder() != null && order.getSalesOrderItem() != null) {
			try {
				BigDecimal taxFactor = new BigDecimal(1);
				if (store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
					taxFactor = taxFactor.add(store.getTaxPercentage().divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
				}
				BigDecimal totalTenderValue = BigDecimal.ZERO;
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
						BigDecimal itemDiscount2 = (item.getDiscountAmount()
								.subtract(discountAmount))
								.divide(taxFactor, 6, RoundingMode.HALF_UP);

						BigDecimal itemTaxablePrice = itemSubTotal.subtract(itemDiscount1).subtract(itemDiscount2);
						BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor).divide(item.getQtyOrdered(), 6, RoundingMode.HALF_UP).multiply(item.getQtyOrdered().subtract(item.getQtyCanceled())).setScale(2, RoundingMode.HALF_UP);
						totalTenderValue = totalTenderValue.add(itemFinalPrice);
					}
				}

				List<ShukranTenders> shukranTenders = objectMapper.readValue(order.getSubSalesOrder().getTenders(), new TypeReference<List<ShukranTenders>>() {});
				List<ShukranTenders> shukranTenders1 = new ArrayList<>();
				BigDecimal originalTotalTenderValue = BigDecimal.ZERO;
				for (ShukranTenders t : shukranTenders) {
					if (t.getTenderAmount() != null) {
						originalTotalTenderValue = originalTotalTenderValue.add(t.getTenderAmount());
					}
				}

				BigDecimal finalOriginalTotalTenderValue = originalTotalTenderValue;
				BigDecimal finalTotalTenderValue = totalTenderValue;
				shukranTenders.forEach(e -> {
					BigDecimal tenderAmount = e.getTenderCode().equals(Constants.orderCredentials.getShukranTenderMappings().getCashOnDelivery()) ? e.getTenderAmount().subtract(order.getCashOnDeliveryFee()) : e.getTenderAmount();
					if (finalOriginalTotalTenderValue.compareTo(BigDecimal.ZERO) > 0) {
						BigDecimal newTenderAmount = tenderAmount.divide(finalOriginalTotalTenderValue, 6, RoundingMode.HALF_UP).multiply(finalTotalTenderValue).setScale(2, RoundingMode.HALF_UP);
						LOGGER.info("new tender amount " + newTenderAmount);
						newTenderAmount = e.getTenderCode().equals(Constants.orderCredentials.getShukranTenderMappings().getCashOnDelivery()) ? newTenderAmount.add(order.getCashOnDeliveryFee()) : newTenderAmount;
						e.setTenderAmount(newTenderAmount);
					}
					shukranTenders1.add(e);
				});
				response = objectMapper.writeValueAsString(shukranTenders1);

			} catch (JsonProcessingException e) {
				LOGGER.info("Error In Resetting Tenders" + e.getMessage());
			}
			SubSalesOrder subSalesOrder = order.getSubSalesOrder();
			subSalesOrder.setTenders(response);
			subSalesOrderRepository.saveAndFlush(subSalesOrder);
		}
	}



	/**
	 * @param orderList
	 * @return
	 */
	@Transactional
	public OmsOrderresponsedto orderpushTowmsv2(List<SplitSalesOrder> orderList) {


		OmsOrderresponsedto response = new OmsOrderresponsedto();
		try {

			for(SplitSalesOrder splitSalesOrder : orderList) {

				if(null != splitSalesOrder.getStatus() && splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
						&& (splitSalesOrder.getRetryPayment() == null || (null != splitSalesOrder.getRetryPayment() && splitSalesOrder.getRetryPayment().equals(0)) )) {

					LOGGER.info("splitSalesOrder is in payment pending: " + splitSalesOrder.getIncrementId());
					continue;
				}

				LOGGER.info("splitSalesOrder push processing started: " + splitSalesOrder.getStatus() + " WmsStatus: " + splitSalesOrder.getWmsStatus() + ",splitSalesOrder orderId: " + splitSalesOrder.getIncrementId());
				OrderPushRequest orderPushRequest = new OrderPushRequest();

				SplitSalesOrderPayment orderPayment= splitSalesOrder.getSplitSalesOrderPayments().stream()
						.findFirst().orElse(null);

				if(null !=orderPayment && null != orderPayment.getMethod()
						&& orderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

					orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_PAYMENT_COD);
				} else {

					orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_NOT_PAYMENT_COD);
				}
				/** convert order date format to WMS required date format **/
				DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
				Date createDate = new Date(splitSalesOrder.getCreatedAt().getTime());

				/** add dispatch delivery time **/
				Date currentDate = new Date();
				Calendar c = Calendar.getInstance();
				c.setTime(currentDate);
				if(null != Constants.orderCredentials.getWms()
						&& null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
					c.add(Calendar.HOUR, Constants.orderCredentials.getWms().getWmsdispatchHoursNumber());
				}

				Date dispatchDeliveryDate = c.getTime();

				orderPushRequest.setOrderType(OrderConstants.ORDER_PUSH_OMS_TYPE);
				if(null != splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId()) {
					orderPushRequest.setLocationCode(splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId().toString());
				}else if(splitSalesOrder.getStoreId().equals(1) || splitSalesOrder.getStoreId().equals(3) &&
						null != Constants.orderCredentials.getWms()
						&& null!= Constants.orderCredentials.getInventoryMapping().get(0)
						&& null != Constants.orderCredentials.getInventoryMapping().get(0).getWareHouseId()) {

					orderPushRequest.setLocationCode(Constants.orderCredentials.getInventoryMapping().get(0).getWareHouseId());
				}else if(CollectionUtils.isNotEmpty(Constants.orderCredentials.getInventoryMapping())
						&& null != Constants.orderCredentials.getInventoryMapping().get(1)) {

					orderPushRequest.setLocationCode(Constants.orderCredentials.getInventoryMapping().get(1).getWareHouseId());
				}
				String orderTime = dateFormat.format(createDate).toString();
				orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
				String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
				dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);

				LOGGER.info("wms splitSalesOrder time:"+orderTime);
				LOGGER.info("wms dispatch time :"+dispatchTime);
				orderPushRequest.setOrderTime(orderTime);
				orderPushRequest.setStartProcessingTime(orderTime);
				if(StringUtils.isNotBlank(splitSalesOrder.getStatus())
						&&  splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
						&& null != splitSalesOrder.getSplitSubSalesOrder().getRetryPayment() && splitSalesOrder.getSplitSubSalesOrder().getRetryPayment().equals(1) ) {
					orderPushRequest.setOnHold(true);
				}else {

					orderPushRequest.setOnHold(false);
				}
				orderPushRequest.setQcStatus(OrderConstants.ORDER_PUSH_OMS_QC_STATUS);
				orderPushRequest.setDispatchByTime(dispatchTime);
				orderPushRequest.setOrderCode(splitSalesOrder.getIncrementId());

				List<SplitSalesOrderItem> salesorderParentItem =
						splitSalesOrder.getSplitSalesOrderItems().stream().filter(e-> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
								.collect(Collectors.toList());

				if(CollectionUtils.isNotEmpty(salesorderParentItem)) {
					List<OrderPushItem> orderPushItemsList = new ArrayList<>();

					for(SplitSalesOrderItem orderItem : salesorderParentItem) {

						OrderPushItem orderPushItem = new OrderPushItem();

						orderPushItem.setChannelSkuCode(orderItem.getSku());
						orderPushItem.setOrderItemCode(orderItem.getItemId().toString());
						if(null !=orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() > 0) {

							BigDecimal qytCancelled = orderItem.getQtyCanceled();
							BigDecimal qtyOrdered = orderItem.getQtyOrdered();
							BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);

							orderPushItem.setQuantity(actualQty.intValue());
							orderPushItem.setMrpPerUnit(orderItem.getPriceInclTax() != null ? orderItem.getPriceInclTax().intValue() : 0);

						}else if(null != orderItem.getQtyOrdered()) {
							orderPushItem.setQuantity(orderItem.getQtyOrdered().intValue());
							orderPushItem.setMrpPerUnit(orderItem.getPriceInclTax() != null ? orderItem.getPriceInclTax().intValue() : 0);
						}
					orderPushItem.setSellerDiscountPerUnit(orderItem.getDiscountAmount());

					BigDecimal poPrice = null;
					if (orderItem.getSalesOrderItem() != null && orderItem.getSalesOrderItem().getPoPrice() != null) {
						poPrice = orderItem.getSalesOrderItem().getPoPrice().setScale(2, RoundingMode.DOWN);
					}
					// Don't send 0 selling price to WMS
					if (null!= poPrice && poPrice.compareTo(BigDecimal.ZERO) > 0) {
						orderPushItem.setSellingPricePerUnit(poPrice);
					} else {
						orderPushItem.setSellingPricePerUnit(new BigDecimal("1.00"));
					}
					orderPushItem.setShippingChargePerUnit(null);

						GiftOption giftOption = new GiftOption();

						giftOption.setGiftwrapRequired(false);
						giftOption.setGiftMessage(false);

						orderPushItem.setGiftOptions(giftOption);

						orderPushItemsList.add(orderPushItem);
					}

					orderPushRequest.setOrderItems(orderPushItemsList);
				}

				SplitSalesOrder updateOrder= splitSalesOrderRepository.findByEntityId(splitSalesOrder.getEntityId());

				SplitSubSalesOrder SplitSubSalesOrder = updateOrder.getSplitSubSalesOrder();
				if (null != SplitSubSalesOrder && Objects.nonNull(SplitSubSalesOrder.getFasterDelivery())){
					orderPushRequest.setPriority(SplitSubSalesOrder.getFasterDelivery() == 1);
				}

				if(Objects.nonNull(updateOrder)) {
					checkIfCancelV2(orderPushRequest, updateOrder);
				}

			}

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Split Sales Order pushed successfully");

		}catch (Exception e) {

			LOGGER.error("exception during push to splitSalesOrder oms:");
		}
		return response;
	}


	/**
	 * @param orderList
	 * @return
	 */
	@Transactional
	public OmsOrderresponsedto orderpushTowmsv3(List<SplitSellerOrder> orderList) {


		OmsOrderresponsedto response = new OmsOrderresponsedto();
		try {

			for(SplitSellerOrder splitSellerOrder : orderList) {

				if (splitSellerOrder == null) {
					LOGGER.warn("[OrderpushHelper] splitSellerOrder is null, skipping this order.");
					continue;
				}
				
				if (splitSellerOrder.getSplitOrder() == null) {
					LOGGER.info("[OrderpushHelper] SplitOrder is null, using SalesOrder for splitSellerOrder: " + splitSellerOrder.getIncrementId());
					OrderPushRequest orderPushRequest = new OrderPushRequest();
					processSplitSellerOrderWithSalesOrder(splitSellerOrder, orderPushRequest);
					continue;
				}

				if(null != splitSellerOrder.getSplitOrder().getStatus() && splitSellerOrder.getSplitOrder().getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
						&& (splitSellerOrder.getSplitOrder().getRetryPayment() == null || (null != splitSellerOrder.getSplitOrder().getRetryPayment() && splitSellerOrder.getSplitOrder().getRetryPayment().equals(0)) )) {

					LOGGER.info("[OrderpushHelper] splitSellerOrder is in payment pending: " + splitSellerOrder.getIncrementId());
					continue;
				}

				LOGGER.info("[OrderpushHelper] splitSellerOrder push processing started: " + splitSellerOrder.getSplitOrder().getStatus() + " WmsStatus: " + splitSellerOrder.getSplitOrder().getWmsStatus() + ",splitSellerOrder orderId: " + splitSellerOrder.getIncrementId());
				OrderPushRequest orderPushRequest = new OrderPushRequest();

				SplitSalesOrderPayment orderPayment= splitSellerOrder.getSplitOrder().getSplitSalesOrderPayments().stream()
						.findFirst().orElse(null);

				if(null !=orderPayment && null != orderPayment.getMethod()
						&& orderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

					orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_PAYMENT_COD);
				} else {

					orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_NOT_PAYMENT_COD);
				}
				/** convert order date format to WMS required date format **/
				DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
				Date createDate = new Date(splitSellerOrder.getCreatedAt().getTime());

				/** add dispatch delivery time **/
				Date currentDate = new Date();
				Calendar c = Calendar.getInstance();
				c.setTime(currentDate);
				if(null != Constants.orderCredentials.getWms()
						&& null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
					c.add(Calendar.HOUR, Constants.orderCredentials.getWms().getWmsdispatchHoursNumber());
				}

				Date dispatchDeliveryDate = c.getTime();

				orderPushRequest.setOrderType(OrderConstants.ORDER_PUSH_OMS_TYPE);

			// Set location code by matching seller_id and warehouse_id
			//Question -> Multiple items with multiple combinations of seller_id and warehouse_id, and only one orderPushRequest, which location code should be set?
			Set<SplitSellerOrderItem> sellerOrderItems = splitSellerOrder.getSplitSellerOrderItems();

                boolean isSellerOrderPush = false;
				
				for (SplitSellerOrderItem item : sellerOrderItems) {
					if (item.getSellerId() != null && item.getWarehouseId() != null) {
						// Use service to get seller config by sellerId and warehouseId
						SellerConfig matchingMapping = sellerConfigService.getSellerConfigBySellerIdAndWarehouse(item.getSellerId(), item.getWarehouseId());
						
						if (matchingMapping != null && matchingMapping.getSellerWarehouseId() != null && 
							matchingMapping.getBasicSettings() != null && Boolean.TRUE.equals(matchingMapping.getBasicSettings().getPushToWms())) {
							orderPushRequest.setLocationCode(matchingMapping.getSellerWarehouseId());
							isSellerOrderPush = true;
							break;
						}
					}
				}

                if(!isSellerOrderPush){
                    continue;
                }
				
				String orderTime = dateFormat.format(createDate).toString();
				orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
				String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
				dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);

				LOGGER.info("[OrderpushHelper] wms splitSellerOrder time:"+orderTime);
				LOGGER.info("[OrderpushHelper] wms dispatch time :"+dispatchTime);
				orderPushRequest.setOrderTime(orderTime);
				orderPushRequest.setStartProcessingTime(orderTime);
				orderPushRequest.setOnHold(false);
				orderPushRequest.setQcStatus(OrderConstants.ORDER_PUSH_OMS_QC_STATUS);
				orderPushRequest.setDispatchByTime(dispatchTime);
				orderPushRequest.setOrderCode(splitSellerOrder.getIncrementId());
				orderPushRequest.setParentOrderCode(splitSellerOrder.getIncrementId());

				List<SplitSellerOrderItem> splitSellerOrderItems = splitSellerOrder.getSplitSellerOrderItems() != null ?
                        splitSellerOrder.getSplitSellerOrderItems().stream()
								.filter(e -> e != null && e.getProductType() != null && 
										!e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
								.collect(Collectors.toList()) : new ArrayList<>();

				if(CollectionUtils.isNotEmpty(splitSellerOrderItems)) {
					List<OrderPushItem> orderPushItemsList = new ArrayList<>();

					for(SplitSellerOrderItem sellerOrderItem : splitSellerOrderItems) {
						SplitSalesOrderItem splitSalesOrderItem = sellerOrderItem.getSplitSalesOrderItem();

						OrderPushItem orderPushItem = new OrderPushItem();

						orderPushItem.setChannelSkuCode(sellerOrderItem.getSku());
						orderPushItem.setOrderItemCode(sellerOrderItem.getItemId().toString());
						if(null !=sellerOrderItem.getQtyCanceled() && sellerOrderItem.getQtyCanceled().intValue() > 0) {

							BigDecimal qytCancelled = sellerOrderItem.getQtyCanceled();
							BigDecimal qtyOrdered = sellerOrderItem.getQtyOrdered();
							BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);

							orderPushItem.setQuantity(actualQty.intValue());
							orderPushItem.setMrpPerUnit(splitSalesOrderItem.getPriceInclTax() != null ? splitSalesOrderItem.getPriceInclTax().intValue() : 0);

						}else if(null != sellerOrderItem.getQtyOrdered()) {
							orderPushItem.setQuantity(sellerOrderItem.getQtyOrdered().intValue());
							orderPushItem.setMrpPerUnit(splitSalesOrderItem.getPriceInclTax() != null ? splitSalesOrderItem.getPriceInclTax().intValue() : 0);
						}
						orderPushItem.setSellerDiscountPerUnit(splitSalesOrderItem.getDiscountAmount());

						orderPushItem.setSellingPricePerUnit(new BigDecimal("1.00"));
						orderPushItem.setShippingChargePerUnit(null);

						GiftOption giftOption = new GiftOption();

						giftOption.setGiftwrapRequired(false);
						giftOption.setGiftMessage(false);

						orderPushItem.setGiftOptions(giftOption);

						orderPushItemsList.add(orderPushItem);
					}

					orderPushRequest.setOrderItems(orderPushItemsList);
				}

                //This part remains same as SplitSellerOrderItem does not have a ProductType, SellerQtyCancelled, QtyOrdered, DiscountAmount

				SplitSellerOrder updateOrder= splitSellerOrderRepository.findByEntityId(splitSellerOrder.getEntityId());

				//Get faster delivery from parent SalesOrder
				if (null != updateOrder.getSalesOrder() && null != updateOrder.getSalesOrder().getSubSalesOrder() 
						&& Objects.nonNull(updateOrder.getSalesOrder().getSubSalesOrder().getFasterDelivery())){
					orderPushRequest.setPriority(updateOrder.getSalesOrder().getSubSalesOrder().getFasterDelivery() == 1);
				}

				// SFP-681 Add Currency Changes in Order Service
				if(Objects.nonNull(splitSellerOrder) && Objects.nonNull(splitSellerOrder.getSalesOrder())
						&& Objects.nonNull( splitSellerOrder.getSalesOrder().getOrderCurrencyCode())) {
					OrderCustomAttributes orderCustomAttributes = new OrderCustomAttributes();
					orderCustomAttributes.setCurrency(splitSellerOrder.getSalesOrder().getOrderCurrencyCode());
					orderPushRequest.setOrderCustomAttributes(orderCustomAttributes);
				}

				if(Objects.nonNull(updateOrder)) {
					checkIfCancelV3(orderPushRequest, updateOrder);
				}

			}

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Split Seller Order pushed successfully");

		}catch (Exception e) {

			LOGGER.error("[OrderpushHelper] exception during push to splitSellerOrder oms:");
		}
		return response;
	}
	
	private void processSplitSellerOrderWithSalesOrder(SplitSellerOrder splitSellerOrder, OrderPushRequest orderPushRequest) {
		LOGGER.info("[OrderpushHelper] Processing SplitSellerOrder using SalesOrder data for: " + splitSellerOrder.getIncrementId());
		
		SalesOrder salesOrder = splitSellerOrder.getSalesOrder();
		if (salesOrder == null) {
			LOGGER.warn("[OrderpushHelper] SalesOrder is also null for SplitSellerOrder: " + splitSellerOrder.getIncrementId());
			return;
		}
		
		// Set payment method from SalesOrder
		SalesOrderPayment orderPayment = salesOrder.getSalesOrderPayment().stream()
				.findFirst().orElse(null);
		
		if (null != orderPayment && null != orderPayment.getMethod()
				&& orderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {
			orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_PAYMENT_COD);
		} else {
			orderPushRequest.setPaymentMethod(OrderConstants.ORDER_PUSH_OMS_NOT_PAYMENT_COD);
		}
		
		// Set order times from SalesOrder
		DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
		Date createDate = new Date(salesOrder.getCreatedAt().getTime());
		
		Date currentDate = new Date();
		Calendar c = Calendar.getInstance();
		c.setTime(currentDate);
		if (null != Constants.orderCredentials.getWms()
				&& null != Constants.orderCredentials.getWms().getWmsdispatchDaysNumber()) {
			c.add(Calendar.HOUR, Constants.orderCredentials.getWms().getWmsdispatchHoursNumber());
		}
		
		Date dispatchDeliveryDate = c.getTime();
		
		String orderTime = dateFormat.format(createDate).toString();
		orderTime = orderTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		String dispatchTime = dateFormat.format(dispatchDeliveryDate).toString();
		dispatchTime = dispatchTime.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		
		orderPushRequest.setOrderTime(orderTime);
		orderPushRequest.setStartProcessingTime(orderTime);
		orderPushRequest.setDispatchByTime(dispatchTime);
		
		// Set order details from SalesOrder
		orderPushRequest.setOrderType(OrderConstants.ORDER_PUSH_OMS_TYPE);
		orderPushRequest.setOrderCode(splitSellerOrder.getIncrementId());
		orderPushRequest.setParentOrderCode(splitSellerOrder.getIncrementId());

		orderPushRequest.setOnHold(false);
		
		orderPushRequest.setQcStatus(OrderConstants.ORDER_PUSH_OMS_QC_STATUS);
		
		// Set location code by matching seller_id and warehouse_id
		Set<SplitSellerOrderItem> sellerOrderItems = splitSellerOrder.getSplitSellerOrderItems();
		
		boolean isSellerOrderPush = false;
		
		for (SplitSellerOrderItem item : sellerOrderItems) {
			if (item.getSellerId() != null && item.getWarehouseId() != null) {
				// Use service to get seller config by sellerId and warehouseId
				SellerConfig matchingMapping = sellerConfigService.getSellerConfigBySellerIdAndWarehouse(item.getSellerId(), item.getWarehouseId());
				
				if (matchingMapping != null && matchingMapping.getSellerWarehouseId() != null && 
					matchingMapping.getBasicSettings() != null && Boolean.TRUE.equals(matchingMapping.getBasicSettings().getPushToWms())) {
					orderPushRequest.setLocationCode(matchingMapping.getSellerWarehouseId());
					isSellerOrderPush = true;
					break;
				}
			}
		}
		
		if (!isSellerOrderPush) {
			LOGGER.warn("[OrderpushHelper] No matching seller inventory mapping found for SplitSellerOrder: " + splitSellerOrder.getIncrementId());
			return;
		}

		// SFP-681 Add Currency Changes in Order Service
		if(Objects.nonNull(splitSellerOrder) && Objects.nonNull(splitSellerOrder.getSalesOrder())
				&& Objects.nonNull( splitSellerOrder.getSalesOrder().getOrderCurrencyCode())) {
			OrderCustomAttributes orderCustomAttributes = new OrderCustomAttributes();
			orderCustomAttributes.setCurrency(splitSellerOrder.getSalesOrder().getOrderCurrencyCode());
			orderPushRequest.setOrderCustomAttributes(orderCustomAttributes);
		}

        List<SplitSellerOrderItem> splitSellerOrderItems = sellerOrderItems.stream()
                        .filter(e -> e != null && e.getProductType() != null &&
                                !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
                        .collect(Collectors.toList());

        if(CollectionUtils.isNotEmpty(splitSellerOrderItems)) {
            List<OrderPushItem> orderPushItemsList = new ArrayList<>();

            for(SplitSellerOrderItem sellerOrderItem : splitSellerOrderItems) {
                SalesOrderItem salesOrderItem = sellerOrderItem.getSalesOrderItem();
                OrderPushItem orderPushItem = new OrderPushItem();

                orderPushItem.setChannelSkuCode(sellerOrderItem.getSku());
                orderPushItem.setOrderItemCode(sellerOrderItem.getItemId().toString());
                if(null !=sellerOrderItem.getQtyCanceled() && sellerOrderItem.getQtyCanceled().intValue() > 0) {

                    BigDecimal qytCancelled = sellerOrderItem.getQtyCanceled();
                    BigDecimal qtyOrdered = sellerOrderItem.getQtyOrdered();
                    BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);

                    orderPushItem.setQuantity(actualQty.intValue());
                    orderPushItem.setMrpPerUnit(salesOrderItem.getPriceInclTax() != null ? salesOrderItem.getPriceInclTax().intValue() : 0);

                }else if(null != sellerOrderItem.getQtyOrdered()) {
                    orderPushItem.setQuantity(sellerOrderItem.getQtyOrdered().intValue());
                    orderPushItem.setMrpPerUnit(salesOrderItem.getPriceInclTax() != null ? salesOrderItem.getPriceInclTax().intValue() : 0);
                }
                orderPushItem.setSellerDiscountPerUnit(salesOrderItem.getDiscountAmount());

                orderPushItem.setSellingPricePerUnit(new BigDecimal("1.00"));
                orderPushItem.setShippingChargePerUnit(null);

                GiftOption giftOption = new GiftOption();

                giftOption.setGiftwrapRequired(false);
                giftOption.setGiftMessage(false);

                orderPushItem.setGiftOptions(giftOption);

                orderPushItemsList.add(orderPushItem);
            }

            orderPushRequest.setOrderItems(orderPushItemsList);
        }
		
		// Set priority from SalesOrder
		if (null != salesOrder.getSubSalesOrder() && Objects.nonNull(salesOrder.getSubSalesOrder().getFasterDelivery())) {
			orderPushRequest.setPriority(salesOrder.getSubSalesOrder().getFasterDelivery() == 1);
		}
		
		// Process the order push request
		SplitSellerOrder updateOrder = splitSellerOrderRepository.findByEntityId(splitSellerOrder.getEntityId());
		if (Objects.nonNull(updateOrder)) {
			checkIfCancelV3SalesOrder(orderPushRequest, updateOrder);
		}
	}

	private void checkIfCancelV2(OrderPushRequest orderPushRequest, SplitSalesOrder updateOrder) {
		LOGGER.info("during push to oms:check the details of the split sales order , if cancel or not! ");
		if((updateOrder.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS) ||
				updateOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS))
				&& (updateOrder.getWmsStatus() == null)) {
			setPushorderAddressdetailsV2(orderPushRequest, updateOrder);
			restcallForwmsorderpushV2(orderPushRequest, updateOrder);
		}
	}


	private void checkIfCancelV3(OrderPushRequest orderPushRequest, SplitSellerOrder updateOrder) {
		LOGGER.info("[OrderpushHelper] during push to oms:check the details of the split seller order , if cancel or not! ");
		if((updateOrder.getSplitOrder().getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS) ||
				updateOrder.getSplitOrder().getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS))
				&& (updateOrder.getWmsStatus() == null || updateOrder.getWmsStatus() == 0)) {
			setPushorderAddressdetailsV3(orderPushRequest, updateOrder);
			restcallForwmsorderpushV3(orderPushRequest, updateOrder);
		}
	}

	private void checkIfCancelV3SalesOrder(OrderPushRequest orderPushRequest, SplitSellerOrder updateOrder) {
		LOGGER.info("[OrderpushHelper] during push to oms:check the details of the split sales order , if cancel or not! ");
		if((updateOrder.getSalesOrder().getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS) ||
				updateOrder.getSalesOrder().getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS))
				&& (updateOrder.getWmsStatus() == null || updateOrder.getWmsStatus() == 0)) {
			setPushorderAddressdetailsV3SalesOrder(orderPushRequest, updateOrder);
			restcallForwmsorderpushV3(orderPushRequest, updateOrder);
		}
	}

	/**
	 * @param orderPushRequest
	 * @param splitSalesOrder
	 */
	private void setPushorderAddressdetailsV2(OrderPushRequest orderPushRequest, SplitSalesOrder splitSalesOrder
	) {

		SalesOrderAddress salesorderAddress = null;
		if (CollectionUtils.isNotEmpty(splitSalesOrder.getSalesOrder().getSalesOrderAddress())) {

			salesorderAddress = splitSalesOrder.getSalesOrder().getSalesOrderAddress().stream()
					.filter(e -> e.getAddressType().equalsIgnoreCase(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING))
					.findFirst().orElse(null);

			if (null != salesorderAddress) {

				Address shippingAddress = new Address();

				shippingAddress.setCity(salesorderAddress.getCity());
				shippingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				shippingAddress.setLine1(salesorderAddress.getStreet());
				shippingAddress.setState(salesorderAddress.getRegion());
				shippingAddress.setCountry(OrderConstants.checkCountryName(splitSalesOrder.getStoreId().toString()));
				shippingAddress.setEmail(salesorderAddress.getEmail());
				shippingAddress.setPhone(salesorderAddress.getTelephone());
				shippingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				Address billingAddress = new Address();

				billingAddress.setCity(salesorderAddress.getCity());
				billingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				billingAddress.setLine1(salesorderAddress.getStreet());
				billingAddress.setState(salesorderAddress.getRegion());
				billingAddress.setCountry(OrderConstants.checkCountryName(splitSalesOrder.getStoreId().toString()));
				billingAddress.setEmail(salesorderAddress.getEmail());
				billingAddress.setPhone(salesorderAddress.getTelephone());
				billingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				orderPushRequest.setShippingAddress(shippingAddress);
				orderPushRequest.setBillingAddress(billingAddress);
			}

		}

	}

	/**
	 * @param orderPushRequest
	 * @param splitSellerOrder
	 */
	private void setPushorderAddressdetailsV3(OrderPushRequest orderPushRequest, SplitSellerOrder splitSellerOrder
	) {

		SalesOrderAddress salesorderAddress = null;
		if (CollectionUtils.isNotEmpty(splitSellerOrder.getSalesOrder().getSalesOrderAddress())) {

			salesorderAddress = splitSellerOrder.getSalesOrder().getSalesOrderAddress().stream()
					.filter(e -> e.getAddressType().equalsIgnoreCase(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING))
					.findFirst().orElse(null);

			if (null != salesorderAddress) {

				Address shippingAddress = new Address();

				shippingAddress.setCity(salesorderAddress.getCity());
				shippingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				shippingAddress.setLine1(salesorderAddress.getStreet());
				shippingAddress.setState(salesorderAddress.getRegion());
				shippingAddress.setCountry(OrderConstants.checkCountryName(splitSellerOrder.getSplitOrder().getStoreId().toString()));
				shippingAddress.setEmail(salesorderAddress.getEmail());
				shippingAddress.setPhone(salesorderAddress.getTelephone());
				shippingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				Address billingAddress = new Address();

				billingAddress.setCity(salesorderAddress.getCity());
				billingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				billingAddress.setLine1(salesorderAddress.getStreet());
				billingAddress.setState(salesorderAddress.getRegion());
				billingAddress.setCountry(OrderConstants.checkCountryName(splitSellerOrder.getSplitOrder().getStoreId().toString()));
				billingAddress.setEmail(salesorderAddress.getEmail());
				billingAddress.setPhone(salesorderAddress.getTelephone());
				billingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				orderPushRequest.setShippingAddress(shippingAddress);
				orderPushRequest.setBillingAddress(billingAddress);
			}

		}

	}

	/**
	 * @param orderPushRequest
	 * @param splitSellerOrder
	 */
	private void setPushorderAddressdetailsV3SalesOrder(OrderPushRequest orderPushRequest, SplitSellerOrder splitSellerOrder
	) {

		SalesOrderAddress salesorderAddress = null;
		if (CollectionUtils.isNotEmpty(splitSellerOrder.getSalesOrder().getSalesOrderAddress())) {

			salesorderAddress = splitSellerOrder.getSalesOrder().getSalesOrderAddress().stream()
					.filter(e -> e.getAddressType().equalsIgnoreCase(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING))
					.findFirst().orElse(null);

			if (null != salesorderAddress) {

				Address shippingAddress = new Address();

				shippingAddress.setCity(salesorderAddress.getCity());
				shippingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				shippingAddress.setLine1(salesorderAddress.getStreet());
				shippingAddress.setState(salesorderAddress.getRegion());
				shippingAddress.setCountry(OrderConstants.checkCountryName(splitSellerOrder.getSalesOrder().getStoreId().toString()));
				shippingAddress.setEmail(salesorderAddress.getEmail());
				shippingAddress.setPhone(salesorderAddress.getTelephone());
				shippingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				Address billingAddress = new Address();

				billingAddress.setCity(salesorderAddress.getCity());
				billingAddress.setName(salesorderAddress.getFirstname() + " " + salesorderAddress.getLastname());
				billingAddress.setLine1(salesorderAddress.getStreet());
				billingAddress.setState(salesorderAddress.getRegion());
				billingAddress.setCountry(OrderConstants.checkCountryName(splitSellerOrder.getSalesOrder().getStoreId().toString()));
				billingAddress.setEmail(salesorderAddress.getEmail());
				billingAddress.setPhone(salesorderAddress.getTelephone());
				billingAddress.setZip(OrderConstants.WMS_ORDER_PUSH_ZIP);

				orderPushRequest.setShippingAddress(shippingAddress);
				orderPushRequest.setBillingAddress(billingAddress);
			}

		}

	}

	


	public void restcallForwmsorderpushV2(OrderPushRequest payload, SplitSalesOrder splitSalesOrder) {

		LOGGER.info("inside wms push split sales order rest controller");
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		if(null !=splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId()
				&& splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId().intValue()== OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE) {

			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());

		}else if(splitSalesOrder.getStoreId().equals(1) || splitSalesOrder.getStoreId().equals(3) || splitSalesOrder.getStoreId().equals(51)) {


			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}else {

			InventoryMapping  inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(1);
			requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
		}


		HttpEntity<OrderPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = null;
		if(null != Constants.orderCredentials.getOrderDetails()
				&& null != Constants.orderCredentials.getOrderDetails().getWmsUrl()) {

			url =Constants.orderCredentials.getOrderDetails().getWmsUrl()+ "/orders/outward";
		}

		LOGGER.info("splitSalesOrder WMS  push URl:" + url);
		try {

			LOGGER.info("oms splitSalesOrder push request body:" + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<Object> response = restTemplate.exchange(url,
					HttpMethod.POST, requestBody, Object.class);


			if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200 ) {

				LOGGER.info("oms splitSalesOrder push response Body:" + mapper.writeValueAsString(response.getBody()));

			}
			if (Objects.nonNull(payload.getOnHold()) && payload.getOnHold().booleanValue()
					&& Objects.nonNull(splitSalesOrder.getStoreId()) && 51 != splitSalesOrder.getStoreId()) {
				/** set ext_order_id=1 for hold status **/
				splitSalesOrderRepository.updateHoldOrderPushStatus(1, splitSalesOrder.getEntityId());
			}
			splitSalesOrderRepository.updateWMSStatus(1, splitSalesOrder.getEntityId());
			LOGGER.info("WMS status splitSalesOrder updated successfully for :: " + splitSalesOrder.getIncrementId());

		} catch (Exception e ) {
			LOGGER.error("Exception occurred  during push WMS splitSalesOrder call:" + splitSalesOrder.getIncrementId());
			LOGGER.error("Exception occurred  during REST splitSalesOrder call:" + e.getMessage());
		}

	}

    public void restcallForwmsorderpushV3(OrderPushRequest payload, SplitSellerOrder splitSellerOrder) {

        LOGGER.info("[OrderpushHelper] inside wms push split seller order rest controller");
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
        if(null !=splitSellerOrder.getWarehouseId()) {
			// Use service to get seller config based on config source (DB or Consul)
			SellerConfig inventoryMapping = sellerConfigService.getSellerConfigForWarehouse(splitSellerOrder.getWarehouseId());
			
			// Verify pushToWms flag is enabled
			if (inventoryMapping != null && inventoryMapping.getBasicSettings() != null
				&& !Boolean.TRUE.equals(inventoryMapping.getBasicSettings().getPushToWms())) {
				inventoryMapping = null;
			}

            if (inventoryMapping != null && inventoryMapping.getConfiguration() != null) {
                requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getConfiguration().getWmsWarehouseHeaderUserName());
                requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getConfiguration().getWmsWarehouseHeaderPassword());
            }

            HttpEntity<OrderPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

            String url = null;
            if(null != inventoryMapping
                    && null != inventoryMapping.getConfiguration()
                    && null != inventoryMapping.getConfiguration().getWmsWareHouseOutwardOrder()) {

                url = inventoryMapping.getConfiguration().getWmsWareHouseOutwardOrder();
            }

            // Check if URL is valid before making REST call
            if (url == null) {
                LOGGER.error("[OrderpushHelper] WMS URL is null for splitSellerOrder: " + splitSellerOrder.getIncrementId());
                return;
            }

            LOGGER.info("[OrderpushHelper] splitSellerOrder WMS  push URl:" + url);
            try {

                LOGGER.info("[OrderpushHelper] oms splitSellerOrder push request body:" + mapper.writeValueAsString(requestBody.getBody()));
                ResponseEntity<Object> response = restTemplate.exchange(url,
                        HttpMethod.POST, requestBody, Object.class);


                if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200 ) {

                    LOGGER.info("[OrderpushHelper] oms splitSellerOrder push response Body:" + mapper.writeValueAsString(response.getBody()));

                }
                if (Objects.nonNull(payload.getOnHold()) && payload.getOnHold().booleanValue()) {
                    /** set ext_order_id=1 for hold status **/
                    splitSellerOrderRepository.updateHoldOrderPushStatus(1, splitSellerOrder.getEntityId());
                }
                    splitSellerOrderRepository.updateWMSStatus(1, splitSellerOrder.getEntityId());
                
                LOGGER.info("[OrderpushHelper] WMS status splitSellerOrder updated successfully for :: " + splitSellerOrder.getIncrementId());

            } catch (Exception e ) {
                LOGGER.error("[OrderpushHelper] Exception occurred  during push WMS splitSellerOrder call:" + splitSellerOrder.getIncrementId());
                LOGGER.error("[OrderpushHelper] Exception occurred  during REST splitSellerOrder call:" + e.getMessage());
            }
        }
	}

	public void restcallForwmsorderpushApparel(OrderPushRequest payload, SalesOrder salesOrder,List<SplitSellerOrder> splitSellerOrders) {
		String warehouseId = payload.getLocationCode();
        LOGGER.info("[OrderpushHelper] Seller WMS inside seller wms push sales order rest call");
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
        if(null !=salesOrder && null!=warehouseId) {
				// Use service to get seller config based on config source (DB or Consul)
				SellerConfig inventoryMapping = sellerConfigService.getSellerConfigForWarehouse(warehouseId);
				
				// Verify pushToWms flag is enabled
				if (inventoryMapping != null && inventoryMapping.getBasicSettings() != null 
					&& !Boolean.TRUE.equals(inventoryMapping.getBasicSettings().getPushToWms())) {
					inventoryMapping = null;
				}
            
            if (inventoryMapping != null && inventoryMapping.getConfiguration() != null) {
                requestHeaders.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getConfiguration().getWmsWarehouseHeaderUserName());
                requestHeaders.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getConfiguration().getWmsWarehouseHeaderPassword());
            }

            HttpEntity<OrderPushRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

            String url = null;
            if(null != inventoryMapping
                    && null != inventoryMapping.getConfiguration()
                    && null != inventoryMapping.getConfiguration().getWmsWareHouseOutwardOrder()) {

                url = inventoryMapping.getConfiguration().getWmsWareHouseOutwardOrder();
            }

            // Check if URL is valid before making REST call
            if (url == null) {
                LOGGER.error("[OrderpushHelper] WMS URL is null for salesOrder: " + salesOrder.getIncrementId());
                return;
            }

            LOGGER.info("[OrderpushHelper] salesOrder WMS  push URl:" + url);
            try {

                LOGGER.info("[OrderpushHelper] Seller WMS oms salesOrder push request body:" + mapper.writeValueAsString(requestBody.getBody()));
                ResponseEntity<Object> response = restTemplate.exchange(url,
                        HttpMethod.POST, requestBody, Object.class);

				List<Integer> sellerEntityIds = splitSellerOrders.stream().map(SplitSellerOrder::getEntityId).toList();
				if (response.getStatusCode() == HttpStatus.OK && response.getStatusCodeValue() == 200 ) {
					LOGGER.info("Seller oms order push response Body:" + mapper.writeValueAsString(response.getBody()));
					// Update WMS status to 1 SplitSellerOrder
					if (Objects.nonNull(payload.getOnHold()) && payload.getOnHold().booleanValue()
							&& Objects.nonNull(salesOrder.getStoreId()) && 51 != salesOrder.getStoreId()) {
						// set ext_order_id=1 for hold status
						splitSellerOrderRepository.updateHoldOrderPushStatusOfSellerOrders(1, sellerEntityIds);
					}
					splitSellerOrderRepository.updateWMSStatusOfSellerOrders(1, sellerEntityIds);
                }
                LOGGER.info("[OrderpushHelper] Seller WMS status salesOrder updated successfully for :: " + salesOrder.getIncrementId());

            } catch (Exception e ) {
                LOGGER.error("[OrderpushHelper] Seller WMS Exception occurred during push WMS salesOrder call for order " + salesOrder.getIncrementId(), e);
            }

        } else {
			LOGGER.warn("[OrderpushHelper] FAILED apparel wms push sales order rest call: warehouseId:  "+ warehouseId);
		}
	}

	private SellerCentralOrder buildSellerCentralOrderPayload(
			SplitSellerOrder sellerOrder, SellerConfig mapping, String sellerIdForStyliWH) {

		SellerCentralOrder payload = new SellerCentralOrder();
		boolean isStyliWarehouse = sellerIdForStyliWH != null;

		// Handle Styli warehouse specific properties first
		if(isStyliWarehouse){
            InventoryMapping inventoryMapping = Constants.orderCredentials.getInventoryMapping().stream()
					.filter(i -> i.getWareHouseId().equals(sellerOrder.getWarehouseId()))
					.findFirst()
					.orElse(null);
            if(null!=inventoryMapping){
                List<String> invoiceAddressEn = inventoryMapping.getInvoiceAddressEn();
                if(!CollectionUtils.isEmpty(invoiceAddressEn)){
                    payload.setShipFrom(invoiceAddressEn.get(0));
                }
            }
            payload.setPickedUpByStyli(true);
			payload.setLastMileAddress(payload.getShipFrom());
            payload.setShipTo(payload.getShipFrom());
            payload.setFulfillmentBy(DEFAULT_SELLER_NAME);
			payload.setSellerId(sellerIdForStyliWH);
        }

		// ---------- ORDER LEVEL ----------
		payload.setSellerOrderId(sellerOrder.getIncrementId());
		payload.setMainOrderId(null!=sellerOrder.getSalesOrder()?sellerOrder.getSalesOrder().getIncrementId():null);
		// As split order id is not available in SplitSellerOrder entity, setting sales order increment id
		payload.setSplitOrderId(null!=sellerOrder.getSalesOrder()?sellerOrder.getSalesOrder().getIncrementId():null);
		payload.setWarehouseId(sellerOrder.getWarehouseId());
		payload.setShipmentMode(sellerOrder.getShipmentMode());
		payload.setStatus(OrderConstants.PENDING_ORDER_STATUS);
		payload.setOrderCreatedAt(sellerOrder.getCreatedAt());
		payload.setCreatedAt(sellerOrder.getCreatedAt());
		payload.setUpdatedAt(sellerOrder.getUpdatedAt());
		if (mapping != null && mapping.getBasicSettings() != null) {
			payload.setHasGlobalShipment(Boolean.TRUE.equals(sellerOrder.getHasGlobalShipment()) ? 
				sellerOrder.getHasGlobalShipment() : mapping.getBasicSettings().getHasGlobalShipment());

			if(!isStyliWarehouse){
				payload.setSellerId(sellerOrder.getSellerId());
			}
			// Only override fulfillmentBy and shipTo if not Styli warehouse or not already set
			if (!isStyliWarehouse) {
				payload.setFulfillmentBy(mapping.getBasicSettings().getDefaultFullfilmentBy());
				payload.setShipTo(mapping.getBasicSettings().getDefaultShipTo());
				payload.setShipFrom(mapping.getBasicSettings().getWarehouseName());
			}

			payload.setPickedUpByStyli(Boolean.TRUE.equals(mapping.getBasicSettings().getPickedUpByStyli()));
			
			
			// Only set timelines if configuration is available
			if (mapping.getConfiguration() != null) {
				SellerCentralOrder.Timelines timelines = new SellerCentralOrder.Timelines();
				// Base time = order created time
				if (Boolean.TRUE.equals(mapping.getConfiguration().getOrderStatusGovernance())) {
					Timestamp baseTime = sellerOrder.getCreatedAt();
					timelines.setAccSla(addHours(baseTime, safeInt(mapping.getConfiguration().getAcknowledgementSlaHrs())).toString());
					timelines.setPackSla(addHours(baseTime, safeInt(mapping.getConfiguration().getPackedSlaHrs())).toString());
					timelines.setShipSla(addHours(baseTime, safeInt(mapping.getConfiguration().getShippedSlaHrs())).toString());
					timelines.setMaxAccSla(addHours(baseTime, safeInt(mapping.getConfiguration().getAcknowledgementSlaHrs()) + safeInt(mapping.getConfiguration().getMaxAcknowledgementBuffer())).toString());
					timelines.setMaxPackSla(addHours(baseTime, safeInt(mapping.getConfiguration().getPackedSlaHrs()) + safeInt(mapping.getConfiguration().getMaxPackedBuffer())).toString());
					timelines.setMaxShipSla(addHours(baseTime, safeInt(mapping.getConfiguration().getShippedSlaHrs()) + safeInt(mapping.getConfiguration().getMaxShippedBuffer())).toString());
					payload.setTimelines(timelines);
				}
			}
			
			payload.setFirstmileWarehouseName(StringUtils.isNotBlank(sellerOrder.getFirstMileWarehouseName()) ? 
				sellerOrder.getFirstMileWarehouseName() : mapping.getBasicSettings().getWarehouseName());
			payload.setLastmileWarehouseName(StringUtils.isNotBlank(sellerOrder.getLastmileWarehouseName()) ? 
				sellerOrder.getLastmileWarehouseName() : mapping.getBasicSettings().getDefaultShipTo());
			payload.setLastmileWarehouseId(StringUtils.isNotBlank(sellerOrder.getLastmileWarehouseId()) ? 
				sellerOrder.getLastmileWarehouseId() : mapping.getBasicSettings().getDefaultShipToWarehouseId());
			payload.setMidmileWarehouseId(StringUtils.isNotBlank(sellerOrder.getMidmileWarehouseId()) ? 
				sellerOrder.getMidmileWarehouseId() : mapping.getBasicSettings().getDefaultShipToWarehouseId());
			payload.setMidmileWarehouseName(StringUtils.isNotBlank(sellerOrder.getMidmileWarehouseName()) ? 
				sellerOrder.getMidmileWarehouseName() : mapping.getBasicSettings().getDefaultShipTo());
		}
		// ---------- ADDRESS LEVEL ----------
		Set<SalesOrderAddress> addresses = null!=sellerOrder.getSalesOrder()?sellerOrder.getSalesOrder().getSalesOrderAddress():null;
		if (addresses != null && CollectionUtils.isNotEmpty(addresses)) {
			List<SellerCentralOrderAddress> sellerCentralOrderAddresses = addresses.stream().map (salesOrderAddress -> {
				SellerCentralOrderAddress scAddress = new SellerCentralOrderAddress();
				scAddress.setSellerOrderId(sellerOrder.getIncrementId());
				scAddress.setRegionId(salesOrderAddress.getRegionId());
				scAddress.setFax(salesOrderAddress.getFax());
				scAddress.setFirstName(salesOrderAddress.getFirstname());
				scAddress.setLastName(salesOrderAddress.getLastname());
				scAddress.setMiddleName(salesOrderAddress.getMiddlename());
				scAddress.setAddressType(salesOrderAddress.getAddressType());
				scAddress.setArea(salesOrderAddress.getArea());
				scAddress.setFormattedAddress(salesOrderAddress.getFormattedAddress());
				scAddress.setCountryId(salesOrderAddress.getCountryId());
				scAddress.setPostcode(salesOrderAddress.getPostcode());
				scAddress.setCity(salesOrderAddress.getCity());
				scAddress.setStreet(salesOrderAddress.getStreet());
				scAddress.setTelephone(salesOrderAddress.getTelephone());
				scAddress.setEmail(salesOrderAddress.getEmail());
				scAddress.setLatitude(salesOrderAddress.getLatitude());
				scAddress.setLongitude(salesOrderAddress.getLongitude());
                scAddress.setUnitNumber(salesOrderAddress.getUnitNumber());
                scAddress.setShortAddress(salesOrderAddress.getShortAddress());
                scAddress.setPostalCode(salesOrderAddress.getPostalCode());
                scAddress.setKsaAddressComplaint(salesOrderAddress.getKsaAddressComplaint());
				return scAddress;
			}).collect(Collectors.toList());
			payload.setAddresses(sellerCentralOrderAddresses);
		}

		// ---------- ITEMS ----------
		List<SellerCentralOrderItem> scItems = sellerOrder.getSplitSellerOrderItems().stream()
				.filter(item -> item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(i -> {
			SellerCentralOrderItem item = new SellerCentralOrderItem();

			item.setItemId(i.getItemId());
			item.setMainOrderId(null!=sellerOrder.getSalesOrder()?sellerOrder.getSalesOrder().getIncrementId():null);
			item.setSplitOrderId(null!=sellerOrder.getSplitOrder()?sellerOrder.getSplitOrder().getIncrementId():null);
			item.setSellerOrderId(sellerOrder.getIncrementId());
			item.setStoreId(i.getStoreId());
			item.setSku(i.getSku());
			item.setSellerId(i.getSellerId());
			item.setSellerName(i.getSellerName());
			item.setWarehouseId(i.getWarehouseId());
			item.setQtyOrdered(i.getQtyOrdered());
			item.setQtyShipped(i.getQtyShipped());
			item.setQtyCanceled(i.getQtyCanceled());
			item.setShipmentType(sellerOrder.getShipmentMode());
			item.setCreatedAt(i.getCreatedAt());
			item.setUpdatedAt(i.getUpdatedAt());
			item.setCancelledBy(i.getCancelledBy());
			item.setSubtotal(i.getSalesOrderItem().getRowTotal());
			item.setSellingPrice(i.getSalesOrderItem().getPrice());
			item.setActualPrice(i.getSalesOrderItem().getActualPrice());
			item.setTax(i.getSalesOrderItem().getTaxAmount());
			item.setTotal(i.getSalesOrderItem().getRowTotalInclTax());
			item.setProductName(i.getSalesOrderItem().getName());
			item.setVendorSku(i.getSalesOrderItem().getVendorSku());
			//image url
			item.setImageUrl(null);
			payload.setSubtotal(i.getSalesOrderItem().getRowTotal());
			payload.setTax(i.getSalesOrderItem().getTaxAmount());
			payload.setTotal(i.getSalesOrderItem().getRowTotalInclTax());

					return item;
		}).toList();

		payload.setItems(scItems);
		return payload;
	}

	private int safeInt(Integer val) {
		return val == null ? 0 : val;
	}

	private Timestamp addHours(Timestamp base, Integer hours) {
		try {
			if (base == null || hours == null) {
				return null;
			}
			long millisToAdd = hours.longValue() * 60L * 60L * 1000L;
			return new Timestamp(base.getTime() + millisToAdd);
		} catch (Exception e) {
			LOGGER.error("[addHours] Error in adding hours : "+hours+" to timestamp: "+base+" error : " + e.getMessage(), e);
			return base;
		}
	}

	private List<SellerCentralOrder> buildSellerCentralCancelOrderPayload(SplitSellerOrder splitSellerOrder) {
		List<SellerCentralOrder> sellerCentralOrders = new ArrayList<>();
        List<SplitSellerOrderItem> items = splitSellerOrder.getSplitSellerOrderItems().stream().filter(item -> item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)).collect(Collectors.toList());
		for(SplitSellerOrderItem item : items) {
			SellerCentralOrder sellerCentralOrder = new SellerCentralOrder();
			sellerCentralOrder.setSellerOrderId(splitSellerOrder.getIncrementId());
			sellerCentralOrder.setSku(item.getSku());
			sellerCentralOrder.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			sellerCentralOrder.setCancelledBy(item.getCancelledBy());
			sellerCentralOrder.setCancellationReason(splitSellerOrder.getCancellationReason());
			sellerCentralOrders.add(sellerCentralOrder);
		}
		return sellerCentralOrders;
	}

	private void pushCancelOrderToSellerCentral(SplitSellerOrder splitSellerOrder) {
		try {
			// push seller order to pubsub topic for seller central
			List<SellerCentralOrder> sellerCentralOrders = buildSellerCentralCancelOrderPayload(splitSellerOrder);
			if(CollectionUtils.isNotEmpty(sellerCentralOrders)) {
			Map<String,Object> requestPayload = new HashMap<>();
					requestPayload.put("type","update");
					requestPayload.put("payload",sellerCentralOrders);

					LOGGER.info("Publishing cancel order pubsub for seller orders:  and pubsubPayload: " + mapper.writeValueAsString(requestPayload));
					pubSubServiceImpl.publishSellerCentralPubSub(sellerCentralCreateOrderTopic,requestPayload);
			}
		} catch (Exception e) {
			LOGGER.error("Error in publishing cancel order to seller central Pub/Sub for increment id: " + splitSellerOrder.getIncrementId(), e);
		}
	}

	private List<SellerCentralOrder> buildSellerCentralReceivedOrderPayload(SalesOrder order) {
		List<SellerCentralOrder> sellerCentralOrders = new ArrayList<>();
		List<SplitSellerOrder> splitSellerOrders = order.getSplitSellerOrders().stream().filter(splitSellerOrder -> 
			!splitSellerOrder.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS) 
			&& !splitSellerOrder.getStatus().equalsIgnoreCase(OrderConstants.DELIVERED_ORDER_STATUS)
			&& !splitSellerOrder.getWarehouseId().equalsIgnoreCase(STYLI_WAREHOUSE_ID)
		).collect(Collectors.toList());

		for(SplitSellerOrder splitSellerOrder : splitSellerOrders) {
			// Use service to get seller config based on config source (DB or Consul)
			SellerConfig mapping = sellerConfigService.getSellerConfigForWarehouse(splitSellerOrder.getWarehouseId());
			
			if(mapping == null) {
				LOGGER.error("Seller inventory mapping not found for warehouse id: " + splitSellerOrder.getWarehouseId());
				continue;
			}
			if (mapping.getBasicSettings() == null || !Boolean.TRUE.equals(mapping.getBasicSettings().getPushToSellerCentral())) {
				continue;
			}
			List<SplitSellerOrderItem> splitSellerOrderItems = splitSellerOrder.getSplitSellerOrderItems().stream().filter(splitSellerOrderItem -> splitSellerOrderItem.getQtyShipped().intValue() > 0 && splitSellerOrderItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)).collect(Collectors.toList());
			for(SplitSellerOrderItem splitSellerOrderItem : splitSellerOrderItems) {
				SellerCentralOrder sellerCentralOrder = new SellerCentralOrder();
				sellerCentralOrder.setSellerOrderId(splitSellerOrder.getIncrementId());
				sellerCentralOrder.setSku(splitSellerOrderItem.getSku());
				sellerCentralOrder.setStatus(OrderConstants.RECEIVED_ORDER_STATUS);
				orderHelper.buildOTSPayloadForSellerOrderAndPublishToPubSub(splitSellerOrder, "Seller Order Delivered", "7.0");
				sellerCentralOrders.add(sellerCentralOrder);
			}
		}
		return sellerCentralOrders;
	}
	/**
	 * Convert SellerInventoryMapping from Consul to SellerConfig for compatibility
	 */

	public List<SellerCentralOrder> buildSellerCentralStyliOrderPackedPayload(SalesOrder order) {
		return order.getSplitSellerOrders().stream()
			.filter(splitSellerOrder -> splitSellerOrder.getWarehouseId().equalsIgnoreCase(STYLI_WAREHOUSE_ID))
			.flatMap(splitSellerOrder -> splitSellerOrder.getSplitSellerOrderItems().stream()
				.filter(splitSellerOrderItem -> splitSellerOrderItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(splitSellerOrderItem -> {
					SellerCentralOrder sellerCentralOrder = new SellerCentralOrder();
					sellerCentralOrder.setSellerOrderId(splitSellerOrder.getIncrementId());
					sellerCentralOrder.setSku(splitSellerOrderItem.getSku());
					sellerCentralOrder.setStatus(OrderConstants.PACKED_ORDER_STATUS);
					return sellerCentralOrder;
				}))
			.collect(Collectors.toList());
	}

	public void pushReceivedOrderToSellerCentral(SalesOrder order) {
		List<SellerCentralOrder> sellerCentralOrders = buildSellerCentralReceivedOrderPayload(order);
		pushOrderToSellerCentral(order, sellerCentralOrders, "received order");
	}

	public void pushStyliOrderPackedToSellerCentral(SalesOrder order) {
		List<SellerCentralOrder> sellerCentralOrders = buildSellerCentralStyliOrderPackedPayload(order);
		pushOrderToSellerCentral(order, sellerCentralOrders, "styli order packed");
	}
	
	/**
	 * Common method to push order updates to Seller Central via Pub/Sub
	 * 
	 * @param order The sales order
	 * @param sellerCentralOrders List of seller central orders to publish
	 * @param eventDescription Description of the event for logging
	 */
	private void pushOrderToSellerCentral(SalesOrder order, List<SellerCentralOrder> sellerCentralOrders, String eventDescription) {
		LOGGER.info("Pushing " + eventDescription + " to seller central for increment id: " + order.getIncrementId());
		
		try {
			if (CollectionUtils.isEmpty(sellerCentralOrders)) {
				LOGGER.warn("No seller central orders to publish for " + eventDescription + ", increment id: " + order.getIncrementId());
				return;
			}
			
			Map<String, Object> requestPayload = new HashMap<>();
			requestPayload.put("type", "update");
			requestPayload.put("payload", sellerCentralOrders);
			
			LOGGER.info("Publishing " + eventDescription + " pubsub for seller orders and pubsubPayload: " + 
				mapper.writeValueAsString(requestPayload));
			
			pubSubServiceImpl.publishSellerCentralPubSub(sellerCentralCreateOrderTopic, requestPayload);
			
			LOGGER.info(eventDescription + " pushed to seller central for increment id: " + order.getIncrementId());
			
		} catch (JsonProcessingException e) {
			LOGGER.error("Failed to serialize " + eventDescription + " payload to JSON for seller central Pub/Sub. " +
				"Order increment id: " + order.getIncrementId() + ". " +
				"This indicates an issue with the SellerCentralOrder object structure.", e);
		} catch (NullPointerException e) {
			LOGGER.error("Null reference encountered while publishing " + eventDescription + " to seller central. " +
				"Order increment id: " + order.getIncrementId() + ". " +
				"This may indicate missing order data, configuration, or uninitialized objects.", e);
		} catch (IllegalArgumentException e) {
			LOGGER.error("Invalid argument provided while publishing " + eventDescription + " to seller central. " +
				"Order increment id: " + order.getIncrementId() + ". " +
				"Check topic name validity and payload format.", e);
		} catch (RuntimeException e) {
			// Catch any other runtime exceptions that may occur during pub/sub operations
			// This includes exceptions from Spring's pub/sub template or infrastructure issues
			LOGGER.error("Unexpected runtime error while publishing " + eventDescription + " to seller central Pub/Sub. " +
				"Order increment id: " + order.getIncrementId() + ". " +
				"Error type: " + e.getClass().getName() + ", Message: " + e.getMessage(), e);
		}
	}

    /**
     * Upsert header in seller_back_order per seller (OPEN), and append items for given sales items.
     * If OPEN header exists, reuse; else create a new one.
     */
    private void upsertSellerBackOrderAndItems(Set<SplitSellerOrder> sellerOrders) {
        try {
            if (org.springframework.util.CollectionUtils.isEmpty(sellerOrders)) {
                return;
            }

            Timestamp now = new Timestamp(System.currentTimeMillis());

            for (SplitSellerOrder sso : sellerOrders) {
                if (sso == null || !shouldCreateBackOrder(sso.getSellerId(), sso.getWarehouseId())) {
                    continue;
                }

                SalesOrder order = sso.getSalesOrder();

                if (order == null
                        || org.apache.commons.lang3.StringUtils.isBlank(order.getStatus())
                        || sso == null
                        || org.apache.commons.lang3.StringUtils.isBlank(sso.getStatus())
                        || !order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)
                        || !sso.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
                    LOGGER.info("[BackOrder][Guard] Skipping back-order; order/sso not PROCESSING or missing status"
                            + " (orderId=" + (order != null ? order.getEntityId() : "null")
                            + ", incrementId=" + (order != null ? order.getIncrementId() : "null")
                            + ", orderStatus=" + (order != null ? order.getStatus() : "null")
                            + ", ssoId=" + (sso != null ? sso.getEntityId() : "null")
                            + ", ssoStatus=" + (sso != null ? sso.getStatus() : "null") + ")");
                    continue;
                }

                // Derive sellerId from the SplitSellerOrder; fallback to warehouse mapping
                String sellerIdStr = sso.getSellerId();
                if (org.apache.commons.lang3.StringUtils.isBlank(sellerIdStr)) {
                    sellerIdStr = resolveSellerIdForWarehouse(sso.getWarehouseId());
                }

                Integer sellerId;
                try {
                    sellerId = Integer.valueOf(sellerIdStr);
                } catch (NumberFormatException ex) {
                    LOGGER.warn("[BackOrder] Skipping back-order creation. Non-numeric sellerId: " + sellerIdStr
                            + " for SSO incrementId=" + sso.getIncrementId());
                    continue;
                }

                // Find or create OPEN header per seller
                SellerBackOrder header = sellerBackOrderRepository
                        .findTopBySellerIdAndStatusOrderByEntityIdDesc(sellerId, "OPEN")
                        .orElseGet(() -> {
                            SellerBackOrder h = new SellerBackOrder();
                            h.setSellerId(sellerId);
                            h.setStatus("OPEN");
                            h.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                            String inc = sellerId + "-" + System.currentTimeMillis();
                            h.setBackOrderIncrementid(inc);
                            return sellerBackOrderRepository.saveAndFlush(h);
                        });

                // Log the mapping asked for lines 4234-4237
                LOGGER.info("[BackOrder] Mapping for SSO: sellerOrderId=" + sso.getEntityId()
                        + ", sellerId=" + sellerId
                        + ", warehouseId=" + org.apache.commons.lang3.StringUtils.defaultString(sso.getWarehouseId())
                        + ", backOrderIncrementId=" + header.getBackOrderIncrementid());


                if (org.springframework.util.CollectionUtils.isEmpty(sso.getSplitSellerOrderItems())) {
                    continue;
                }

                for (SplitSellerOrderItem item : sso.getSplitSellerOrderItems()) {
                    if (item == null
                            || OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equalsIgnoreCase(item.getProductType())) {
                        continue;
                    }

					
					// Prevent duplicate back-order items: check existence by (sellerId, mainOrderId, sku)
					try {
						Integer existingMainOrderId = (item.getMainOrder() != null) ? item.getMainOrder().getEntityId() : null;
						if (sellerId != null && existingMainOrderId != null
								&& sellerBackOrderItemRepository.existsBySellerBackOrder_SellerIdAndMainOrder_EntityIdAndSku(
										sellerId, existingMainOrderId, item.getSku())) {
							LOGGER.info("[BackOrder] Duplicate detected. Skipping back-order item. sellerId=" + sellerId
									+ ", mainOrderId=" + existingMainOrderId + ", sku=" + item.getSku());
							continue;
						}
					} catch (Exception existenceCheckEx) {
						// Non-fatal: if existence check fails, proceed; DB unique key will still protect
						LOGGER.warn("[BackOrder] Existence check failed; proceeding with insert. sellerId=" + sellerId
								+ ", sku=" + (item != null ? item.getSku() : null)
								+ ", err=" + existenceCheckEx.toString());
					}

					
                    SellerBackOrderItem boItem = new SellerBackOrderItem();
                    boItem.setSellerBackOrder(header);
                    boItem.setSplitSellerOrder(item.getSplitSellerOrder());
                    boItem.setMainOrder(item.getMainOrder());
                    boItem.setSplitOrder(item.getSplitSellerOrder() .getSplitOrder());
                    boItem.setSku(item.getSku());
                    SplitSalesOrderItem ssoi = item.getSplitSalesOrderItem();
                    SalesOrderItem soi = item.getSalesOrderItem();
                    boolean useSplitSellerFlow = (item.getSplitSellerOrder() != null && ssoi != null);
                    Integer qtyOrdered = 0;
                    if (useSplitSellerFlow) {
                        if (item.getQtyOrdered() != null) {
                            qtyOrdered = item.getQtyOrdered().intValue();
                        } else if (ssoi != null && ssoi.getQtyOrdered() != null) {
                            qtyOrdered = ssoi.getQtyOrdered().intValue();
                        }
                    } else if (soi != null && soi.getQtyOrdered() != null) {
                        qtyOrdered = soi.getQtyOrdered().intValue();
                    }
                    boItem.setQty(qtyOrdered);

                    SalesOrder so = useSplitSellerFlow
                            ? (ssoi != null ? ssoi.getSalesOrder() : (soi != null ? soi.getSalesOrder() : null))
                            : (soi != null ? soi.getSalesOrder() : null);

                    BigDecimal qtyBd = BigDecimal.valueOf(qtyOrdered <= 0 ? 1 : qtyOrdered);

                    // New pricing based on provided mapping and formula:
                    // ((Product Price - TD) - (Contri to NTD) * NTD) / (1 + Tax rate) = Pre Tax (per unit)
                    //
                    // Mappings:
                    // - (Product Price - TD) -> row_total_incl_tax (per row) → per-unit by dividing by qty
                    // - Contri to NTD -> discount_amount used as a proportional factor vs row_total_incl_tax
                    // - NTD -> SalesOrder.giftVoucherDiscount (absolute, order-level, positive)
                    // - Tax rate -> tax_percent / 100 (fallback to computed if missing)

                    // Row totals (incl tax) to derive per-unit "Product Price - TD"
                    BigDecimal rowIncl = null;
                    if (useSplitSellerFlow) {
                        if (ssoi != null && ssoi.getRowTotalInclTax() != null) rowIncl = ssoi.getRowTotalInclTax();
                    } else if (soi != null && soi.getRowTotalInclTax() != null) {
                        rowIncl = soi.getRowTotalInclTax(); //This is my product price - td
                    }
                    // Fallback to previous derivation if rowIncl missing
                    if (rowIncl == null) {
                        BigDecimal unitPriceIncl = calculateUnitPriceInclTax(useSplitSellerFlow, ssoi, soi);
                        rowIncl = unitPriceIncl.multiply(qtyBd);
                    }
                    BigDecimal productMinusTdPerUnit = rowIncl.divide(qtyBd, 6, RoundingMode.HALF_UP); // per price

                    // Contri to NTD factor derived from discount_amount relative to row_total_incl_tax
                    BigDecimal lineDiscount = null;
                    if (useSplitSellerFlow) {
                        if (ssoi != null && ssoi.getDiscountAmount() != null) lineDiscount = ssoi.getDiscountAmount();
                    } else if (soi != null && soi.getDiscountAmount() != null) {
                        lineDiscount = soi.getDiscountAmount();
                    }
                    if (lineDiscount == null) lineDiscount = BigDecimal.ZERO;
                    BigDecimal contriToNtdFactor = BigDecimal.ZERO;
                    if (rowIncl.compareTo(BigDecimal.ZERO) > 0) {
                        contriToNtdFactor = lineDiscount.abs();
                    }
                    String sellerIdStr2 = sso != null ? sso.getSellerId() : null;
                    Integer sellerIdForPct = null;
                    if (sellerIdStr2 != null) {
                        try { sellerIdForPct = Integer.valueOf(sellerIdStr2.trim()); } catch (Exception ignore) {
							LOGGER.error("[BackOrder][NTD] Invalid sellerId format for commission percentage. sellerIdStr=" + sellerIdStr2, ignore);
                        }
                    }

                    // NTD total from order (absolute)
                    String l4 = useSplitSellerFlow
                            ? (ssoi != null ? ssoi.getShukranL4Category() : null)
                            : (soi != null ? soi.getShukranL4Category() : null);
                    BigDecimal ntdPct = (sellerIdForPct != null) ? getCommissionNtdPercentage(sellerIdForPct, l4) : BigDecimal.ZERO;
                    // Convert percentage to fraction
                    BigDecimal ntdPctFraction = ntdPct.movePointLeft(2);
                    // Allocate NTD to this line then to per-unit
                    BigDecimal ntdSharePerLine = ntdPctFraction.multiply(contriToNtdFactor);
                    BigDecimal ntdSharePerUnit = ntdSharePerLine.divide(qtyBd, 6, RoundingMode.HALF_UP);

                    // Tax rate from item tax_percent/100; fallback to previous computation
                    BigDecimal taxPercent = null;
                    if (useSplitSellerFlow) {
                        if (ssoi != null && ssoi.getTaxPercent() != null) taxPercent = ssoi.getTaxPercent();
                    } else if (soi != null && soi.getTaxPercent() != null) {
                        taxPercent = soi.getTaxPercent();
                    }
                    BigDecimal taxRate;
                    if (taxPercent == null || taxPercent.compareTo(BigDecimal.ZERO) == 0) {
                        // Resolve currency from order to fetch region-level tax percentage
                        String currencyCode = useSplitSellerFlow
                                ? (ssoi != null && ssoi.getSplitSalesOrder() != null ? ssoi.getSplitSalesOrder().getOrderCurrencyCode() : null)
                                : (soi != null && soi.getSalesOrder() != null ? soi.getSalesOrder().getOrderCurrencyCode() : null);
                        BigDecimal regionPct = null;
                        try {
                            if (Constants.orderCredentials != null
                                    && Constants.orderCredentials.getRegionLevelTaxPercentage() != null
                                    && currencyCode != null) {
                                regionPct = Constants.orderCredentials.getRegionLevelTaxPercentage()
                                        .get(currencyCode.trim().toUpperCase());
                            }
                        } catch (Exception ignore) {}
                        if (regionPct == null) {
                            regionPct = BigDecimal.valueOf(5); // default
                        }
                        taxRate = regionPct.movePointLeft(2);
                    } else {
                        taxRate = taxPercent.movePointLeft(2); // percent to fraction
                    }

                    // Compute Pre-tax per unit
                    BigDecimal preTaxUnit = productMinusTdPerUnit.subtract(ntdSharePerUnit)
                            .divide(BigDecimal.ONE.add(taxRate), 6, RoundingMode.HALF_UP);

                    // Log computed price components for audit with new formula
                    try {
                        LOGGER.info("[BackOrder][PriceCalc2] orderCode=" + (so != null ? so.getIncrementId() : null)
                                + ", itemId=" + (ssoi != null ? ssoi.getItemId() : (soi != null ? soi.getItemId() : null))
                                + ", sku=" + item.getSku()
                                + ", qty=" + qtyBd
                                + ", rowIncl=" + rowIncl
                                + ", discountAmt=" + lineDiscount
                                + ", contriToNtdFactor=" + contriToNtdFactor
                                + ", ntdPct=" + ntdPctFraction
                                + ", taxRate=" + taxRate
                                + ", preTaxUnit=" + preTaxUnit.setScale(2, RoundingMode.HALF_UP));
                    } catch (Exception ignore) {}
                    boItem.setPrice(preTaxUnit.setScale(2, RoundingMode.HALF_UP));
                    boItem.setStatus("OPEN");
                    boItem.setShipmentCode(null);
                    boItem.setAsnCode(item.getAsnNumber());
                    boItem.setCreatedAt(now);
					// Idempotent DB upsert to avoid duplicate key rollback of outer transaction
					try {
						Integer mainOrderIdForUpsert = (boItem.getMainOrder() != null) ? boItem.getMainOrder().getEntityId() : null;
						if (mainOrderIdForUpsert != null) {
							sellerBackOrderItemRepository.upsertItem(
									header.getEntityId(),
									(boItem.getSplitSellerOrder() != null ? boItem.getSplitSellerOrder().getEntityId() : null),
									mainOrderIdForUpsert,
									(boItem.getSplitOrder() != null ? boItem.getSplitOrder().getEntityId() : null),
									boItem.getSku(),
									boItem.getQty(),
									boItem.getPrice(),
									boItem.getStatus(),
									boItem.getShipmentCode(),
									boItem.getAsnCode(),
									boItem.getCreatedAt()
							);
						} else {
							// Fallback: if main order is unexpectedly null, keep previous behavior
							sellerBackOrderItemRepository.save(boItem);
						}
					} catch (Exception upsertEx) {
						// Swallow all exceptions to avoid poisoning the outer transaction
						LOGGER.warn("[BackOrder] Upsert failed (ignored) for sku=" + boItem.getSku()
								+ ", sellerId=" + header.getSellerId()
								+ ", mainOrderId=" + (boItem.getMainOrder() != null ? boItem.getMainOrder().getEntityId() : null)
								+ " err=" + upsertEx.toString());
					}
                }
            }
        } catch (Exception ex) {
            LOGGER.error("[BackOrder] Error while upserting back-order and items: " + ex.getMessage(), ex);
        }
    }

    public List<SellerConfig> getSellerConfigBySellerIdAndStyliWarehouseId(String sellerId, String styliWarehouseId) {
        return sellerConfigRepository.findBySellerIdAndStyliWarehouseId(sellerId, styliWarehouseId);
    }

    /**
     * Compute sellerId for a given warehouse using the same precedence as builder methods.
     */
    private String resolveSellerIdForWarehouse(String warehouseId) {
        SellerInventoryMapping mapping = Optional.ofNullable(Constants.orderCredentials)
                .map(c -> c.getSellerInventoryMapping())
                .orElse(Collections.emptyList())
                .stream()
                .filter(m -> m.getWareHouseId().equalsIgnoreCase(warehouseId))
                .findFirst()
                .orElse(null);
        if (mapping != null) {
            return mapping.getSellerId();
        }
        if (Constants.orderCredentials != null && Constants.orderCredentials.getUnicommerceInventoryMapping() != null) {
            UnicommereceInventoryMapping unicommerceMapping = Constants.orderCredentials.getUnicommerceInventoryMapping().stream()
                    .filter(m -> m.getWareHouseId().equalsIgnoreCase(warehouseId))
                    .findFirst()
                    .orElse(null);
            if (unicommerceMapping != null) {
                return unicommerceMapping.getSellerId();
            }
        }
        if (Constants.orderCredentials != null && Constants.orderCredentials.getInventoryMapping() != null) {
            InventoryMapping invMapping = Constants.orderCredentials.getInventoryMapping().stream()
                    .filter(m -> m.getWareHouseId().equalsIgnoreCase(warehouseId))
                    .findFirst()
                    .orElse(null);
            return invMapping != null ? invMapping.getSellerId() : DEFAULT_SELLER_ID;
        }
        return DEFAULT_SELLER_ID;
    }

    private boolean shouldCreateBackOrder(String sellerId, String styliWarehouseId) {
        try {
            if (StringUtils.isBlank(sellerId) || StringUtils.isBlank(styliWarehouseId)) {
                return false;
            }
            List<SellerConfig> cfg = sellerConfigRepository.findBySellerIdAndStyliWarehouseId(sellerId, styliWarehouseId);
            return CollectionUtils.isNotEmpty(cfg) && Boolean.TRUE.equals(cfg.get(0).getIsB2BSeller());
        } catch (Exception ex) {
            LOGGER.error("Error while checking seller_config for sellerId=" + sellerId + ", warehouseId=" + styliWarehouseId + ": " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Calculate unit price inclusive of tax, falling back to price + taxPercent when needed.
     */
    private BigDecimal calculateUnitPriceInclTax(boolean useSplitSellerFlow, SplitSalesOrderItem ssoi, SalesOrderItem soi) {
        BigDecimal unitPriceIncl = BigDecimal.ZERO;
        if (useSplitSellerFlow) {
            if (ssoi != null && ssoi.getPriceInclTax() != null) {
                unitPriceIncl = ssoi.getPriceInclTax();
            } else if (ssoi != null && ssoi.getPrice() != null && ssoi.getTaxPercent() != null) {
                unitPriceIncl = ssoi.getPrice().multiply(BigDecimal.ONE.add(ssoi.getTaxPercent().movePointLeft(2)));
            }
        } else if (soi != null) {
            if (soi.getPriceInclTax() != null) {
                unitPriceIncl = soi.getPriceInclTax();
            } else if (soi.getPrice() != null && soi.getTaxPercent() != null) {
                unitPriceIncl = soi.getPrice().multiply(BigDecimal.ONE.add(soi.getTaxPercent().movePointLeft(2)));
            }
        }
        return unitPriceIncl;
    }

    /**
     * Fetch NTD percentage for seller and L4 category from commission table, preserving logging behavior.
     */
    private BigDecimal getCommissionNtdPercentage(Integer sellerIdForPct, String l4) {
        BigDecimal ntdPct = BigDecimal.ZERO;
        SellerCommissionDetails scd = null;
        try {
            try {
                LOGGER.info("[BackOrder][NTD] Commission lookup - sellerIdForPct=" + sellerIdForPct + ", l4=" + (l4 != null ? l4 : ""));
            } catch (Exception ignore) {}
            java.util.Optional<SellerCommissionDetails> opt = sellerCommissionDetailsRepository
                    .findTopBySellerIdAndL4CategoryOrderByIdDesc(sellerIdForPct, l4 != null ? l4 : "");
            if (opt != null && opt.isPresent()) {
                scd = opt.get();
            } else {
                scd = sellerCommissionDetailsRepository
                        .findTopBySellerIdOrderByIdDesc(sellerIdForPct)
                        .orElse(null);
                LOGGER.info("[BackOrder][NTD] Commission fallback lookup - sellerIdForPct=" + sellerIdForPct + ", scd=" + scd);
            }
        } catch (Exception e) {
            LOGGER.error("[BackOrder][NTD] Error fetching seller commission details for sellerId: " + sellerIdForPct, e);
        }
        if (scd != null && scd.getCommissionDetails() != null) {
            try {
                Map<String, Object> cdet = mapper.readValue(scd.getCommissionDetails(), new TypeReference<Map<String, Object>>() {});
                Object ntd = (cdet.get("ntd_percentage") != null ? cdet.get("ntd_percentage")
                        : (cdet.get("ntd") != null ? cdet.get("ntd")
                        :(cdet.get("ntd") != null ? cdet.get("ntd")
                        :(cdet.get("NTD") != null ? cdet.get("NTD")
                        : cdet.get("ntd_discount_percentage")))));
                if (ntd != null) {
                    ntdPct = new BigDecimal(String.valueOf(ntd));
                }
            } catch (Exception ignore) {}
        }
        return ntdPct;
    }

}

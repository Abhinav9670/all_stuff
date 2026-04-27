package org.styli.services.order.service.impl.child;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.validation.Valid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.exception.WmsException;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderpushHelper;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.helper.RefundHelper;
import org.styli.services.order.helper.TabbyHelper;
import org.styli.services.order.helper.TamaraHelper;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.OrderPushItem;
import org.styli.services.order.pojo.OrderunfulfilmentRequest;
import org.styli.services.order.pojo.order.OTSOrderRequest;
import org.styli.services.order.pojo.order.SkuItem;
import org.styli.services.order.pojo.order.StatusMessage;
import org.styli.services.order.pojo.request.OtsTrackingRequest;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.request.Order.PackboxDetails;
import org.styli.services.order.pojo.request.Order.ShipmentOrderV2;
import org.styli.services.order.pojo.request.Order.SkuQuantityData;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;
import org.styli.services.order.pojo.response.BulkShipmentResponse;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.OrdershipmentResponse;
import org.styli.services.order.pojo.response.OtsTrackingResponse;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.response.shipmentItem;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.response.Order.OrderItem;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.SalesShipmentPackDetailsRepository;
import org.styli.services.order.repository.SalesShipmentPackDetailsItemRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SellerShipmentPackDetailsRepository;
import org.styli.services.order.repository.SellerShipmentPackDetailsItemRepository;
import org.styli.services.order.service.impl.PubSubServiceImpl;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;

@Component
public class OrderShipmentHelper {

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OrderEntityConverter orderEntityConverter;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSellerOrderRepository splitSellerOrderRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesShipmentRepository salesshipmentRepository;

	@Autowired
	SalesShipmentPackDetailsRepository salesshipmentPackDetailsRepository;

	@Autowired
	SalesShipmentPackDetailsItemRepository salesshipmentPackDetailsItemRepository;

	@Autowired
	SplitSellerShipmentRepository splitSellerShipmentRepository;

	@Autowired
	SalesInvoiceRepository salesInvoiceRepository;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	PaymentUtility paymentUtility;
	RefundHelper refundHelper;

	@Autowired
	TabbyHelper tabbyHelper;

	@Autowired
	TamaraHelper tamaraHelper;

	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Autowired
	SellerShipmentPackDetailsRepository sellerShipmentPackDetailsRepository;

	@Autowired
	SellerShipmentPackDetailsItemRepository sellerShipmentPackDetailsItemRepository;

	@Autowired
	ZatcaServiceImpl zatcaServiceImpl;

	@Value("${region}")
	private String region;

	@Autowired
	PubSubServiceImpl pubSubServiceImpl;

    @Autowired
    SellerBackOrderItemRepository sellerBackOrderItemRepository;

	@Value("${pubsub.topic.split.order.tracking}")
	private String splitOrderTrackingTopic;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	OrderpushHelper orderPushHelper;

	@Autowired
	org.styli.services.order.service.SalesOrderServiceV3 salesOrderServiceV3;

	private static final Log LOGGER = LogFactory.getLog(OrderShipmentHelper.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	@Transactional
	public OmsOrderoutboundresponse createorderShipment(@Valid OrderViewRequest request, SalesOrder order) {
		try{

			OmsOrderoutboundresponse omsOrdershipmentresponse = new OmsOrderoutboundresponse();
			OrdershipmentResponse ordershipmentResponse = new OrdershipmentResponse();
			//boolean bnplStatusFlag = false;
			//boolean captureFlag = false;

			if (null != order && order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)
					&& null != order.getWmsStatus() && order.getWmsStatus().equals(1)
					&& !"1".equalsIgnoreCase(order.getExtOrderId())) {

				// Pessimistic lock on parent order to prevent concurrent duplicate creation
				SalesOrder lockedOrder = salesOrderRepository.lockByEntityId(order.getEntityId());
				order = lockedOrder != null ? lockedOrder : order;

				// Application-level uniqueness guard: prevent duplicate shipment/invoice for (orderId, NULL split)
				long existingShipments = salesshipmentRepository.countByOrderIdAndSplitNull(order.getEntityId());
				if (existingShipments > 0) {
					omsOrdershipmentresponse.setStatus(true);
					omsOrdershipmentresponse.setStatusCode("200");
					omsOrdershipmentresponse.setStatusMsg("Shipment already exists for order");
					return omsOrdershipmentresponse;
				}

				SalesOrderAddress existingAddress = order.getSalesOrderAddress().stream()
						.filter(e -> e.getAddressType().equals(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING)).findFirst()
						.orElse(null);

				SalesShipment salesShipment = new SalesShipment();

				salesShipment.setCreatedAt(new Timestamp(new Date().getTime()));
				salesShipment.setUpdatedAt(new Timestamp(new Date().getTime()));
				salesShipment.setCustomerId(order.getCustomerId());
				salesShipment.setOrderId(order.getEntityId());
				if (null != existingAddress) {
					salesShipment.setShippingAddressId(existingAddress.getEntityId());
				}
				salesShipment.setEmailSent(0);
				salesShipment.setStoreId(order.getStoreId());
				salesShipment.setTotalQty(order.getTotalQtyOrdered());

				List<shipmentItem> shipmentItems = new ArrayList<>();
				CopyOnWriteArrayList<SalesOrderItem> orderListItems = new CopyOnWriteArrayList<>(order.getSalesOrderItem());
				for (SalesOrderItem orderItem : orderListItems) {

					if (null == orderItem.getQtyCanceled()
							|| (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() == 0)) {

						shipmentItem shipmentItem = new shipmentItem();
						SalesShipmentItem salesshipmentItem = new SalesShipmentItem();

						salesshipmentItem.setOrderItemId(orderItem.getItemId());
						salesshipmentItem.setQuantity(orderItem.getQtyOrdered());
						salesshipmentItem.setPrice(orderItem.getPriceInclTax());
						salesshipmentItem.setName(orderItem.getName());
						salesshipmentItem.setSku(orderItem.getSku());
						salesshipmentItem.setRowTotal(orderItem.getRowTotal());
						salesshipmentItem.setProductId(salesshipmentItem.getProductId());

						salesShipment.addSalesShipmentItem(salesshipmentItem);

						shipmentItem.setChannelSkuCode(orderItem.getSku());
						shipmentItem.setOrderItemCode(orderItem.getItemId().toString());
						shipmentItem.setQuantity(orderItem.getQtyOrdered().intValue());
						orderItem.setQtyShipped(orderItem.getQtyOrdered());
						shipmentItems.add(shipmentItem);

					} else if (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() > 0
							&& orderItem.getQtyCanceled().intValue() != orderItem.getQtyOrdered().intValue()) {

						shipmentItem shipmentItem = new shipmentItem();
						SalesShipmentItem salesshipmentItem = new SalesShipmentItem();

						BigDecimal qytCancelled = orderItem.getQtyCanceled();
						BigDecimal qtyOrdered = orderItem.getQtyOrdered();
						BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);
						orderItem.setQtyShipped(actualQty);
						salesshipmentItem.setOrderItemId(orderItem.getItemId());

						salesshipmentItem.setOrderItemId(orderItem.getItemId());
						salesshipmentItem.setQuantity(actualQty);
						salesshipmentItem.setPrice(orderItem.getPriceInclTax());
						salesshipmentItem.setName(orderItem.getName());
						salesshipmentItem.setSku(orderItem.getSku());
						salesshipmentItem.setRowTotal(orderItem.getRowTotal());
						salesshipmentItem.setProductId(salesshipmentItem.getProductId());

						salesShipment.addSalesShipmentItem(salesshipmentItem);

						shipmentItem.setChannelSkuCode(orderItem.getSku());
						shipmentItem.setOrderItemCode(orderItem.getItemId().toString());
						shipmentItem.setQuantity(orderItem.getQtyOrdered().intValue());

						shipmentItems.add(shipmentItem);
					}

				}

				// Check if packboxDetailsList has more than one item to set is_mps flag
				if (request.getPackboxDetailsList() != null && request.getPackboxDetailsList().size() > 1) {
					salesShipment.setIsMps(true);
				}

				SalesShipment newsalesShipment = salesshipmentRepository.saveAndFlush(salesShipment);

				salesShipment.setIncrementId(generateIncrementId(newsalesShipment.getEntityId(), order.getStoreId()));

				// Process packboxDetailsList if present
				if (request.getPackboxDetailsList() != null && !request.getPackboxDetailsList().isEmpty()) {
					processPackboxDetails(request.getPackboxDetailsList(), salesShipment);
				}

				salesshipmentRepository.saveAndFlush(salesShipment);

				// Send Braze notification for dangerous goods shipments immediately after shipment creation
				try {
					if (salesOrderServiceV3 != null) {
						salesOrderServiceV3.sendDangerousGoodsBrazeNotification(order, salesShipment);
					}
				} catch (Exception e) {
					LOGGER.error("Error sending dangerous goods Braze notification after shipment creation: " + e.getMessage(), e);
					// Don't fail shipment creation if Braze notification fails
				}

				if (CollectionUtils.isEmpty(order.getSalesInvoices())) {
					// Guard against duplicate invoice creation for (orderId, NULL split)
					long existingInvoices = salesInvoiceRepository.countByOrderIdAndSplitNull(order.getEntityId());
					if (existingInvoices > 0) {
						LOGGER.info("Invoice already exists for orderId=" + order.getEntityId() + " (NULL split), skipping creation");
					} else {
					/** create invoice **/
					//List<SalesCreditmemo> creditMemoList = salesCreditmemoRepository.findByOrderId(order.getEntityId());
					List<SalesCreditmemo> creditMemoList = orderHelper.getSalesCreditMemoList(order.getEntityId());
					// EAS coins in invoice added inside createInvoiceObjectToPersist
					SalesInvoice salesInvoice = createInvoiceObjectToPersist(order, creditMemoList);
					createInvoiceItems(salesInvoice, order);

					order.addSalesInvoice(salesInvoice);
					}
				}

				order.setState(OrderConstants.MOVE_ORDER_STATE);
				order.setStatus(OrderConstants.PACKED_ORDER_STATUS);
				order.setWmsStatus(2);

				SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());

				salesorderGrid.setStatus(OrderConstants.PACKED_ORDER_STATUS);

				salesOrderGridRepository.saveAndFlush(salesorderGrid);

				SalesOrder updatedOrder = salesOrderRepository.saveAndFlush(order);

				paymentDtfHelper.updateOrderStatusHistory(updatedOrder, OrderConstants.SHIPMENT_CREATE_MESSAGE,
						OrderConstants.SHIPMENT_STATUS_HISTORY_ENTITY, order.getStatus());

				paymentDtfHelper.updateOrderStatusHistory(updatedOrder, OrderConstants.INVOICE_CREATE_MESSAGE,
						OrderConstants.INVOICE_STATUS_HISTORY_ENTITY, order.getStatus());

				if (CollectionUtils.isNotEmpty(updatedOrder.getSalesInvoices())) {

					SalesInvoice invoice = order.getSalesInvoices().stream().findFirst().orElse(null);

					if (null != invoice) {

						invoice.setIncrementId(generateIncrementId(invoice.getEntityId(), order.getStoreId()));
						ObjectMapper mapper= new ObjectMapper();
						LOGGER.info("create shipment invoice data+"+ mapper.writeValueAsString(invoice));
						salesInvoiceRepository.saveAndFlush(invoice);

						Optional<String> paymentMethod = order.getSalesOrderPayment().stream()
								.map(SalesOrderPayment::getMethod).findFirst();

						if(paymentMethod.isPresent() && OrderConstants.checkBNPLPaymentMethods(paymentMethod.get())) {
							paymentUtility.processBNPLPayment(paymentUtility, order, paymentMethod, tabbyHelper, tamaraHelper);
						}else if(paymentMethod.isPresent()
								&& null != order.getPayfortAuthorized()
								&& order.getPayfortAuthorized() == 1) {
							PayfortReposne response = paymentUtility.processPayfortAuthorizedCapturePayment(paymentUtility, order, paymentMethod);
							if(null !=response && response.isStatus()) {
								order.setAuthorizationCapture(1);
								updatedOrder = salesOrderRepository.saveAndFlush(order);
							}
						}
						// ZATCA Start
						if(Constants.getZatcaFlag(order.getStoreId())) {
							zatcaServiceImpl.sendZatcaInvoice(order,false,null);
						}
						// ZATCA Ends
						LOGGER.info("invoice saved successfully");
					}

				}

				orderHelper.releaseInventoryQty(order, new HashMap<>(), false, OrderConstants.SHIPMENT_RELEASE_INVENTORY);

				orderHelper.updateStatusHistory(order, false, false, false, false, true);

				omsOrdershipmentresponse.setShipmentCode(salesShipment.getIncrementId());
				omsOrdershipmentresponse.setShipmentItems(shipmentItems);

				ordershipmentResponse.setShipmentIncid(salesShipment.getIncrementId());
				ordershipmentResponse.setShipmentId(salesShipment.getEntityId());
				omsOrdershipmentresponse.setStatus(true);
				omsOrdershipmentresponse.setStatusCode("200");
				omsOrdershipmentresponse.setStatusMsg("Created Successfully");

				orderPushHelper.pushReceivedOrderToSellerCentral(order);
				orderPushHelper.pushStyliOrderPackedToSellerCentral(order);

				// Update seller orders' status to 'received_at_warehouse' for back orders
				updateSellerOrdersStatusForBackOrders(order);

			} else if (null != order && CollectionUtils.isNotEmpty(order.getSalesShipments())) {

				SalesShipment shipment = order.getSalesShipments().stream().findFirst().orElse(null);

				if (null != shipment) {

					List<shipmentItem> shipmentItems = new ArrayList<>();

					for (SalesShipmentItem item : shipment.getSalesShipmentItem()) {
						shipmentItem shipItem = new shipmentItem();
						shipItem.setChannelSkuCode(item.getSku());
						if (null != item.getItemId()) {
							shipItem.setOrderItemCode(item.getOrderItemId().toString());
						}
						if (null != item.getQuantity()) {
							shipItem.setQuantity(item.getQuantity().intValue());
						}

						shipmentItems.add(shipItem);
					}

					omsOrdershipmentresponse.setShipmentCode(shipment.getIncrementId());
					omsOrdershipmentresponse.setShipmentItems(shipmentItems);

					ordershipmentResponse.setShipmentId(shipment.getEntityId());
					ordershipmentResponse.setShipmentIncid(shipment.getIncrementId());
				}
				omsOrdershipmentresponse.setStatus(true);
				omsOrdershipmentresponse.setStatusCode("200");
				omsOrdershipmentresponse.setStatusMsg("Created Successfully");

				ordershipmentResponse.setOrderId(order.getEntityId());

			} else {

				omsOrdershipmentresponse.setStatus(false);
				omsOrdershipmentresponse.setStatusCode("202");
				omsOrdershipmentresponse.setStatusMsg("Invalid Order!");
				omsOrdershipmentresponse.setHasError(true);
				omsOrdershipmentresponse.setErrorMessage("invalid order !");
				return omsOrdershipmentresponse;
			}
			omsOrdershipmentresponse.setStatus(true);
			omsOrdershipmentresponse.setStatusMsg("Created Successfully!");
			omsOrdershipmentresponse.setStatusCode("200");
			return omsOrdershipmentresponse;
		} catch (JsonProcessingException | RuntimeException e) {
			LOGGER.info("Error Creating Order Shipment"+ e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@Transactional
	public OmsOrderoutboundresponse createSellerOrderShipment(@Valid OrderViewRequest request, SplitSellerOrder order) {
		try{

			OmsOrderoutboundresponse omsOrdershipmentresponse = new OmsOrderoutboundresponse();
			OrdershipmentResponse ordershipmentResponse = new OrdershipmentResponse();
            if (null != order && order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)
				&& order.getWmsStatus() != null && order.getWmsStatus().equals(1)
			) {
			BigDecimal packedQty = request.getPackedQty();

			// Calculate total ordered quantity for comparison
			BigDecimal totalOrderedQty = order.getSplitSellerOrderItems().stream()
				.filter(item -> item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(item -> item.getQtyOrdered())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

			// If packed quantity is less than total ordered, cancel the difference
			if(packedQty != null && totalOrderedQty.compareTo(packedQty) > 0) {
				cancelPartialOrderForPackedQuantity(order, packedQty);
			}
				SalesOrderAddress existingAddress = order.getSalesOrder().getSalesOrderAddress().stream()
						.filter(e -> e.getAddressType().equals(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING)).findFirst()
						.orElse(null);

				SplitSellerShipment salesShipment = new SplitSellerShipment();


				salesShipment.setCreatedAt(new Timestamp(new Date().getTime()));
				salesShipment.setUpdatedAt(new Timestamp(new Date().getTime()));
				salesShipment.setCustomerId(order.getSalesOrder().getCustomerId());
				salesShipment.setOrderId(order.getSalesOrder().getEntityId());
				if(order.getSplitOrder() != null){
					salesShipment.setSplitOrderId(order.getSplitOrder().getEntityId());
				}
				salesShipment.setSellerOrderId(order.getEntityId());
				if (null != existingAddress) {
					salesShipment.setShippingAddressId(existingAddress.getEntityId());
				}
				salesShipment.setEmailSent(0);
				salesShipment.setStoreId(order.getSalesOrder().getStoreId());
				
				BigDecimal totalQty = packedQty != null ? packedQty : order.getSplitSellerOrderItems().stream()
				.filter(item -> (item.getQtyCanceled() == null || item.getQtyCanceled().intValue() == 0) && item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(item -> item.getQtyOrdered())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

				salesShipment.setTotalQty(totalQty);

				List<shipmentItem> shipmentItems = new ArrayList<>();
				CopyOnWriteArrayList<SplitSellerOrderItem> orderListItems = new CopyOnWriteArrayList<>(order.getSplitSellerOrderItems());
				for (SplitSellerOrderItem orderItem : orderListItems) {

					if (null == orderItem.getQtyCanceled()
							|| (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() == 0)) {

						shipmentItem shipmentItem = new shipmentItem();
						SplitSellerShipmentItem salesshipmentItem = new SplitSellerShipmentItem();

					salesshipmentItem.setOrderItemId(orderItem.getItemId());
					salesshipmentItem.setQuantity(totalQty);
					salesshipmentItem.setPrice(orderItem.getSalesOrderItem().getPriceInclTax());
					salesshipmentItem.setName(orderItem.getSalesOrderItem().getName());
					salesshipmentItem.setSku(orderItem.getSalesOrderItem().getSku());
					salesshipmentItem.setRowTotal(orderItem.getSalesOrderItem().getRowTotal());
					salesshipmentItem.setProductId(salesshipmentItem.getProductId());

					salesShipment.addSplitSellerShipmentItem(salesshipmentItem);

					shipmentItem.setChannelSkuCode(orderItem.getSku());
					shipmentItem.setOrderItemCode(orderItem.getItemId().toString());
					shipmentItem.setQuantity(orderItem.getQtyOrdered().intValue());
					// Use packedQty if provided, otherwise use qtyOrdered
					BigDecimal qtyToShip = (packedQty != null && packedQty.compareTo(BigDecimal.ZERO) > 0) 
						? packedQty 
						: orderItem.getQtyOrdered();
					orderItem.setQtyShipped(qtyToShip);
					shipmentItems.add(shipmentItem);

				} else if (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() > 0
							&& orderItem.getQtyCanceled().intValue() != orderItem.getQtyOrdered().intValue()) {

					shipmentItem shipmentItem = new shipmentItem();
					SplitSellerShipmentItem salesshipmentItem = new SplitSellerShipmentItem();

					BigDecimal qytCancelled = orderItem.getQtyCanceled();
					BigDecimal qtyOrdered = orderItem.getQtyOrdered();
					BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);
					// Use packedQty if provided, otherwise use calculated actualQty
					BigDecimal qtyToShip = (packedQty != null && packedQty.compareTo(BigDecimal.ZERO) > 0) 
						? packedQty 
						: actualQty;
					orderItem.setQtyShipped(qtyToShip);
					salesshipmentItem.setOrderItemId(orderItem.getItemId());

					salesshipmentItem.setOrderItemId(orderItem.getItemId());
					salesshipmentItem.setQuantity(qtyToShip);

					salesshipmentItem.setPrice(orderItem.getSalesOrderItem().getPriceInclTax());
					salesshipmentItem.setName(orderItem.getSalesOrderItem().getName());
					salesshipmentItem.setSku(orderItem.getSku());
					salesshipmentItem.setRowTotal(orderItem.getSalesOrderItem().getRowTotal());

					salesshipmentItem.setProductId(salesshipmentItem.getProductId());

					salesShipment.addSplitSellerShipmentItem(salesshipmentItem);

					shipmentItem.setChannelSkuCode(orderItem.getSku());
					shipmentItem.setOrderItemCode(orderItem.getItemId().toString());

					shipmentItem.setQuantity(orderItem.getQtyOrdered().intValue());

					shipmentItems.add(shipmentItem);
					}

				}

				SplitSellerShipment newsalesShipment = splitSellerShipmentRepository.saveAndFlush(salesShipment);

				if (request.getPackboxDetailsList() != null && !request.getPackboxDetailsList().isEmpty()) {
					processPackboxDetailsForSellerShipment(request.getPackboxDetailsList(), salesShipment);
				}

				salesShipment.setIncrementId(generateIncrementId(newsalesShipment.getEntityId(), order.getSalesOrder().getStoreId()));

				splitSellerShipmentRepository.saveAndFlush(salesShipment);

                // After shipment increment id is generated and saved, reflect it on back-order items tied to this seller order
                updateBackOrderShipmentCode(order, salesShipment.getIncrementId());

				orderHelper.buildOTSPayloadForSellerOrderAndPublishToPubSub(order, "Seller Order Packed", "4.0");

			order.setStatus(OrderConstants.PACKED_ORDER_STATUS);
			order.getSplitSellerOrderItems().stream().forEach(item -> {
				item.setQtyPacked(totalQty);
				item.setQtyShipped(totalQty);
			});
			order.setUpdatedAt(new Timestamp(new Date().getTime()));
			splitSellerOrderRepository.saveAndFlush(order);

				omsOrdershipmentresponse.setShipmentCode(salesShipment.getIncrementId());
				omsOrdershipmentresponse.setShipmentItems(shipmentItems);

				ordershipmentResponse.setShipmentIncid(salesShipment.getIncrementId());
				ordershipmentResponse.setShipmentId(salesShipment.getEntityId());
				omsOrdershipmentresponse.setStatus(true);
				omsOrdershipmentresponse.setStatusCode("200");
				omsOrdershipmentresponse.setStatusMsg("Created Successfully");


			}
            else if (null != order && CollectionUtils.isNotEmpty(order.getSplitSellerShipments())) {

				SplitSellerShipment shipment = order.getSplitSellerShipments().stream().findFirst().orElse(null);

				if (null != shipment) {

					List<shipmentItem> shipmentItems = new ArrayList<>();

					for (SplitSellerShipmentItem item : shipment.getSplitSellerShipmentItems()) {
						shipmentItem shipItem = new shipmentItem();
						shipItem.setChannelSkuCode(item.getSku());
						if (null != item.getItemId()) {
							shipItem.setOrderItemCode(item.getOrderItemId().toString());
						}
						if (null != item.getQuantity()) {
							shipItem.setQuantity(item.getQuantity().intValue());
						}

						shipmentItems.add(shipItem);
					}

					omsOrdershipmentresponse.setShipmentCode(shipment.getIncrementId());
					omsOrdershipmentresponse.setShipmentItems(shipmentItems);

					ordershipmentResponse.setShipmentId(shipment.getEntityId());
					ordershipmentResponse.setShipmentIncid(shipment.getIncrementId());
				}
				omsOrdershipmentresponse.setStatus(true);
				omsOrdershipmentresponse.setStatusCode("200");
				omsOrdershipmentresponse.setStatusMsg("Created Successfully");

				ordershipmentResponse.setOrderId(order.getEntityId());

			} else {

				omsOrdershipmentresponse.setStatus(false);
				omsOrdershipmentresponse.setStatusCode("202");
				omsOrdershipmentresponse.setStatusMsg("Invalid Order!");
				omsOrdershipmentresponse.setHasError(true);
				omsOrdershipmentresponse.setErrorMessage("invalid order !");
				return omsOrdershipmentresponse;
			}
			omsOrdershipmentresponse.setStatus(true);
			omsOrdershipmentresponse.setStatusMsg("Created Successfully!");
			omsOrdershipmentresponse.setStatusCode("200");
			return omsOrdershipmentresponse;
		} catch (RuntimeException e) {
			LOGGER.info("Error Creating Order Shipment"+ e.getMessage());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates bulk seller order shipments using optimized bulk database queries.
	 * Returns a single response with overall status and individual shipment results.
	 * 
	 * @param request OrderViewRequest containing list of ShipmentOrderV2
	 * @return BulkShipmentResponse with overall status and individual results
	 */
	@Transactional
	public BulkShipmentResponse createBulkSellerShipmentsV2(@Valid OrderViewRequest request) {
		// Early return for empty request
		if (CollectionUtils.isEmpty(request.getShipmentOrderList())) {
			return createEmptyBulkResponse();
		}
		
		int totalRequested = request.getShipmentOrderList().size();
		LOGGER.info("Processing bulk shipment for " + totalRequested + " orders");
		
		BulkShipmentResponse bulkResponse = new BulkShipmentResponse();
		List<BulkShipmentResponse.ShipmentResult> shipmentResults = new ArrayList<>();
		
		// Step 1: Validate and collect order codes
		Map<String, ShipmentOrderV2> orderCodeToRequestMap = new HashMap<>();
		List<String> validOrderCodes = validateAndCollectOrderCodes(
			request.getShipmentOrderList(), 
			shipmentResults, 
			orderCodeToRequestMap
		);
		
		// Step 2: Bulk fetch all orders in a single DB query
		Map<String, SplitSellerOrder> orderCodeToOrderMap = fetchOrdersInBulk(validOrderCodes);
		
		// Step 3: Process each shipment
		processShipments(validOrderCodes, orderCodeToOrderMap, orderCodeToRequestMap, shipmentResults);
		
		// Step 4: Build summary response
		int successCount = (int) shipmentResults.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
		int failureCount = totalRequested - successCount;
		
		bulkResponse.setShipments(shipmentResults);
		bulkResponse.setTotalRequested(totalRequested);
		bulkResponse.setSuccessCount(successCount);
		bulkResponse.setFailureCount(failureCount);
		
		// Overall status
		if (successCount == totalRequested) {
			bulkResponse.setStatus(true);
			bulkResponse.setStatusCode("200");
			bulkResponse.setStatusMsg("All shipments created successfully");
		} else if (successCount > 0) {
			bulkResponse.setStatus(true);
			bulkResponse.setStatusCode("207"); // Multi-Status
			bulkResponse.setStatusMsg("Partial success: " + successCount + " of " + totalRequested + " shipments created");
		} else {
			bulkResponse.setStatus(false);
			bulkResponse.setStatusCode("400");
			bulkResponse.setStatusMsg("All shipments failed");
		}
		
		LOGGER.info("Bulk shipment processing completed. Success: " + successCount + 
			", Failed: " + failureCount + " out of " + totalRequested);
		
		return bulkResponse;
	}
	
	/**
	 * Creates an empty bulk response for when request is empty
	 */
	private BulkShipmentResponse createEmptyBulkResponse() {
		LOGGER.warn("Bulk shipment request is empty");
		BulkShipmentResponse bulkResponse = new BulkShipmentResponse();
		bulkResponse.setStatus(false);
		bulkResponse.setStatusCode("400");
		bulkResponse.setStatusMsg("Empty request - no shipments to process");
		bulkResponse.setShipments(new ArrayList<>());
		bulkResponse.setTotalRequested(0);
		bulkResponse.setSuccessCount(0);
		bulkResponse.setFailureCount(0);
		return bulkResponse;
	}
	
	/**
	 * Validates order codes and collects them into a map
	 */
	private List<String> validateAndCollectOrderCodes(
			List<ShipmentOrderV2> shipmentOrders,
			List<BulkShipmentResponse.ShipmentResult> shipmentResults,
			Map<String, ShipmentOrderV2> orderCodeToRequestMap) {
		
		List<String> validOrderCodes = new ArrayList<>();
		
		for (ShipmentOrderV2 shipmentOrder : shipmentOrders) {
			if (StringUtils.isBlank(shipmentOrder.getOrderCode())) {
				LOGGER.error("OrderCode is null or empty in bulk shipment request");
				shipmentResults.add(createErrorResult(null, "OrderCode is required"));
			} else {
				validOrderCodes.add(shipmentOrder.getOrderCode());
				orderCodeToRequestMap.put(shipmentOrder.getOrderCode(), shipmentOrder);
			}
		}
		
		return validOrderCodes;
	}
	
	/**
	 * Fetches orders in bulk from database
	 */
	private Map<String, SplitSellerOrder> fetchOrdersInBulk(List<String> validOrderCodes) {
		Map<String, SplitSellerOrder> orderCodeToOrderMap = new HashMap<>();
		
		if (validOrderCodes.isEmpty()) {
			return orderCodeToOrderMap;
		}
		
		List<SplitSellerOrder> orders = splitSellerOrderRepository.findByIncrementIdIn(validOrderCodes);
		LOGGER.info("Fetched " + orders.size() + " orders out of " + validOrderCodes.size() + " requested");
		
		for (SplitSellerOrder order : orders) {
			orderCodeToOrderMap.put(order.getIncrementId(), order);
		}
		
		return orderCodeToOrderMap;
	}
	
	/**
	 * Processes all shipments
	 */
	private void processShipments(
			List<String> validOrderCodes,
			Map<String, SplitSellerOrder> orderCodeToOrderMap,
			Map<String, ShipmentOrderV2> orderCodeToRequestMap,
			List<BulkShipmentResponse.ShipmentResult> shipmentResults) {
		
		for (String orderCode : validOrderCodes) {
			try {
				SplitSellerOrder order = orderCodeToOrderMap.get(orderCode);
				
				if (order == null) {
					LOGGER.error("Order not found for orderCode: " + orderCode);
					shipmentResults.add(createErrorResult(orderCode, "Order not found"));
					continue;
				}
				
				ShipmentOrderV2 shipmentOrder = orderCodeToRequestMap.get(orderCode);
				OmsOrderoutboundresponse response = processSingleShipment(
					order, 
					shipmentOrder.getPackedQty(), 
					shipmentOrder.getPackboxDetailsList()
				);
				
				shipmentResults.add(convertToShipmentResult(orderCode, response));
				
			} catch (Exception e) {
				LOGGER.error("Error processing shipment for orderCode: " + orderCode, e);
				shipmentResults.add(createErrorResult(orderCode, "Processing error: " + e.getMessage()));
			}
		}
	}
	
	/**
	 * Creates an error result
	 */
	private BulkShipmentResponse.ShipmentResult createErrorResult(String orderCode, String errorMessage) {
		BulkShipmentResponse.ShipmentResult errorResult = new BulkShipmentResponse.ShipmentResult();
		errorResult.setOrderCode(orderCode);
		errorResult.setSuccess(false);
		errorResult.setErrorMessage(errorMessage);
		return errorResult;
	}
	
	/**
	 * Converts OmsOrderoutboundresponse to ShipmentResult
	 */
	private BulkShipmentResponse.ShipmentResult convertToShipmentResult(
			String orderCode, 
			OmsOrderoutboundresponse response) {
		
		BulkShipmentResponse.ShipmentResult result = new BulkShipmentResponse.ShipmentResult();
		result.setOrderCode(orderCode);
		result.setSuccess(response.getStatus() != null && response.getStatus());
		result.setShipmentCode(response.getShipmentCode());
		result.setShipmentItems(response.getShipmentItems());
		
		if (response.getResponse() != null) {
			result.setShipmentId(response.getResponse().getShipmentId());
			result.setShipmentIncrementId(response.getResponse().getShipmentIncid());
		}
		
		if (Boolean.FALSE.equals(result.getSuccess())) {
			result.setErrorMessage(response.getErrorMessage());
		}
		
		return result;
	}
	
	/**
	 * Processes a single seller order shipment.
	 * Extracted from createSellerOrderShipmentV2 for reusability in bulk operations.
	 * 
	 * @param order The SplitSellerOrder to create shipment for
	 * @param packedQty The packed quantity
	 * @param packboxDetailsList List of packbox details
	 * @return OmsOrderoutboundresponse containing shipment details
	 */
	private OmsOrderoutboundresponse processSingleShipment(
			SplitSellerOrder order, 
			BigDecimal packedQty, 
			List<PackboxDetails> packboxDetailsList) {
		
		OmsOrderoutboundresponse omsOrdershipmentresponse = new OmsOrderoutboundresponse();
		
		try {
			// Check if order is eligible for new shipment creation
			if (isOrderEligibleForNewShipment(order)) {
				return createNewShipment(order, packedQty, packboxDetailsList, omsOrdershipmentresponse);
			}
			
			// Check if order already has shipments
			if (order != null && CollectionUtils.isNotEmpty(order.getSplitSellerShipments())) {
				return handleExistingShipment(order, omsOrdershipmentresponse);
			}
			
			// Invalid order state
			return createInvalidOrderResponse(omsOrdershipmentresponse);
			
		} catch (Exception e) {
			LOGGER.error("Error creating single shipment for order: " + order.getIncrementId(), e);
			return createErrorResponse(omsOrdershipmentresponse, e.getMessage());
		}
	}
	
	/**
	 * Checks if order is eligible for new shipment creation
	 */
	private boolean isOrderEligibleForNewShipment(SplitSellerOrder order) {
		return order != null 
			&& order.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)
			&& order.getWmsStatus() != null 
			&& order.getWmsStatus().equals(1);
	}
	
	/**
	 * Creates a new shipment for the order
	 */
	private OmsOrderoutboundresponse createNewShipment(
			SplitSellerOrder order,
			BigDecimal packedQty,
			List<PackboxDetails> packboxDetailsList,
			OmsOrderoutboundresponse omsOrdershipmentresponse) {
		
		// Handle partial cancellation if needed
		handlePartialCancellation(order, packedQty);
		
		// Create shipment entity
		SplitSellerShipment salesShipment = createShipmentEntity(order, packedQty);
		
		// Create shipment items
		BigDecimal totalQty = calculateTotalQty(order, packedQty);
		salesShipment.setTotalQty(totalQty);
		List<shipmentItem> shipmentItems = createShipmentItems(order, packedQty, totalQty, salesShipment);
		
		// Save shipment
		SplitSellerShipment newsalesShipment = splitSellerShipmentRepository.saveAndFlush(salesShipment);
		
		// Process packbox details if provided
		if (packboxDetailsList != null && !packboxDetailsList.isEmpty()) {
			processPackboxDetailsForSellerShipment(packboxDetailsList, salesShipment);
		}
		
		// Generate and save increment ID
		salesShipment.setIncrementId(generateIncrementId(newsalesShipment.getEntityId(), order.getSalesOrder().getStoreId()));
		splitSellerShipmentRepository.saveAndFlush(salesShipment);
		
		// Update order status
		updateOrderAfterShipment(order, totalQty);
		
		// Publish OTS event
		orderHelper.buildOTSPayloadForSellerOrderAndPublishToPubSub(order, "Seller Order Packed", "4.0");
		
		// Build response
		return buildSuccessResponse(omsOrdershipmentresponse, salesShipment, shipmentItems);
	}
	
	/**
	 * Handles partial cancellation if packed quantity is less than ordered
	 */
	private void handlePartialCancellation(SplitSellerOrder order, BigDecimal packedQty) {
		BigDecimal totalOrderedQty = order.getSplitSellerOrderItems().stream()
			.filter(item -> item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
			.map(SplitSellerOrderItem::getQtyOrdered)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		
		if (packedQty != null && totalOrderedQty.compareTo(packedQty) > 0) {
			cancelPartialOrderForPackedQuantity(order, packedQty);
		}
	}
	
	/**
	 * Creates the shipment entity with basic properties
	 */
	private SplitSellerShipment createShipmentEntity(SplitSellerOrder order, BigDecimal packedQty) {
		SalesOrderAddress existingAddress = order.getSalesOrder().getSalesOrderAddress().stream()
			.filter(e -> e.getAddressType().equals(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING))
			.findFirst()
			.orElse(null);
		
		SplitSellerShipment salesShipment = new SplitSellerShipment();
		Timestamp now = new Timestamp(new Date().getTime());
		
		salesShipment.setCreatedAt(now);
		salesShipment.setUpdatedAt(now);
		salesShipment.setCustomerId(order.getSalesOrder().getCustomerId());
		salesShipment.setOrderId(order.getSalesOrder().getEntityId());
		
		if (order.getSplitOrder() != null) {
			salesShipment.setSplitOrderId(order.getSplitOrder().getEntityId());
		}
		
		salesShipment.setSellerOrderId(order.getEntityId());
		
		if (existingAddress != null) {
			salesShipment.setShippingAddressId(existingAddress.getEntityId());
		}
		
		salesShipment.setEmailSent(0);
		salesShipment.setStoreId(order.getSalesOrder().getStoreId());
		
		return salesShipment;
	}
	
	/**
	 * Calculates total quantity for the shipment
	 */
	private BigDecimal calculateTotalQty(SplitSellerOrder order, BigDecimal packedQty) {
		if (packedQty != null) {
			return packedQty;
		}
		
		return order.getSplitSellerOrderItems().stream()
			.filter(item -> (item.getQtyCanceled() == null || item.getQtyCanceled().intValue() == 0) 
				&& item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
			.map(SplitSellerOrderItem::getQtyOrdered)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
	
	/**
	 * Creates shipment items from order items
	 */
	private List<shipmentItem> createShipmentItems(
			SplitSellerOrder order,
			BigDecimal packedQty,
			BigDecimal totalQty,
			SplitSellerShipment salesShipment) {
		
		List<shipmentItem> shipmentItems = new ArrayList<>();
		CopyOnWriteArrayList<SplitSellerOrderItem> orderListItems = new CopyOnWriteArrayList<>(order.getSplitSellerOrderItems());
		
		for (SplitSellerOrderItem orderItem : orderListItems) {
			if (isItemFullyOrdered(orderItem)) {
				addFullyOrderedShipmentItem(orderItem, packedQty, totalQty, salesShipment, shipmentItems);
			} else if (isItemPartiallyCancelled(orderItem)) {
				addPartiallyCancelledShipmentItem(orderItem, packedQty, salesShipment, shipmentItems);
			}
		}
		
		return shipmentItems;
	}
	
	/**
	 * Checks if item is fully ordered (not cancelled)
	 */
	private boolean isItemFullyOrdered(SplitSellerOrderItem orderItem) {
		return orderItem.getQtyCanceled() == null 
			|| orderItem.getQtyCanceled().intValue() == 0;
	}
	
	/**
	 * Checks if item is partially cancelled
	 */
	private boolean isItemPartiallyCancelled(SplitSellerOrderItem orderItem) {
		return orderItem.getQtyCanceled() != null 
			&& orderItem.getQtyCanceled().intValue() > 0
			&& orderItem.getQtyCanceled().intValue() != orderItem.getQtyOrdered().intValue();
	}
	
	/**
	 * Adds a fully ordered item to shipment
	 */
	private void addFullyOrderedShipmentItem(
			SplitSellerOrderItem orderItem,
			BigDecimal packedQty,
			BigDecimal totalQty,
			SplitSellerShipment salesShipment,
			List<shipmentItem> shipmentItems) {
		
		BigDecimal qtyToShip = (packedQty != null && packedQty.compareTo(BigDecimal.ZERO) > 0) 
			? packedQty 
			: orderItem.getQtyOrdered();
		
		SplitSellerShipmentItem salesshipmentItem = createSalesShipmentItem(orderItem, totalQty);
		salesShipment.addSplitSellerShipmentItem(salesshipmentItem);
		
		shipmentItem shipItem = createShipmentItem(orderItem, qtyToShip);
		shipmentItems.add(shipItem);
		
		orderItem.setQtyShipped(qtyToShip);
	}
	
	/**
	 * Adds a partially cancelled item to shipment
	 */
	private void addPartiallyCancelledShipmentItem(
			SplitSellerOrderItem orderItem,
			BigDecimal packedQty,
			SplitSellerShipment salesShipment,
			List<shipmentItem> shipmentItems) {
		
		BigDecimal actualQty = orderItem.getQtyOrdered().subtract(orderItem.getQtyCanceled());
		BigDecimal qtyToShip = (packedQty != null && packedQty.compareTo(BigDecimal.ZERO) > 0) 
			? packedQty 
			: actualQty;
		
		SplitSellerShipmentItem salesshipmentItem = createSalesShipmentItem(orderItem, qtyToShip);
		salesShipment.addSplitSellerShipmentItem(salesshipmentItem);
		
		shipmentItem shipItem = createShipmentItem(orderItem, qtyToShip);
		shipmentItems.add(shipItem);
		
		orderItem.setQtyShipped(qtyToShip);
	}
	
	/**
	 * Creates a SplitSellerShipmentItem from order item
	 */
	private SplitSellerShipmentItem createSalesShipmentItem(SplitSellerOrderItem orderItem, BigDecimal quantity) {
		SplitSellerShipmentItem salesshipmentItem = new SplitSellerShipmentItem();
		salesshipmentItem.setOrderItemId(orderItem.getItemId());
		salesshipmentItem.setQuantity(quantity);
		salesshipmentItem.setPrice(orderItem.getSalesOrderItem().getPriceInclTax());
		salesshipmentItem.setName(orderItem.getSalesOrderItem().getName());
		salesshipmentItem.setSku(orderItem.getSalesOrderItem().getSku());
		salesshipmentItem.setRowTotal(orderItem.getSalesOrderItem().getRowTotal());
		salesshipmentItem.setProductId(salesshipmentItem.getProductId());
		return salesshipmentItem;
	}
	
	/**
	 * Creates a shipmentItem for API response
	 */
	private shipmentItem createShipmentItem(SplitSellerOrderItem orderItem, BigDecimal qtyToShip) {
		shipmentItem shipItem = new shipmentItem();
		shipItem.setChannelSkuCode(orderItem.getSku());
		shipItem.setOrderItemCode(orderItem.getItemId().toString());
		shipItem.setQuantity(qtyToShip.intValue());
		return shipItem;
	}
	
	/**
	 * Updates order after shipment creation
	 */
	private void updateOrderAfterShipment(SplitSellerOrder order, BigDecimal totalQty) {
		order.getSplitSellerOrderItems().stream().forEach(item -> {
			item.setQtyPacked(totalQty);
			item.setQtyShipped(totalQty);
		});
		order.setStatus(OrderConstants.PACKED_ORDER_STATUS);
		order.setUpdatedAt(new Timestamp(new Date().getTime()));
		splitSellerOrderRepository.saveAndFlush(order);
	}
	
	/**
	 * Handles existing shipment scenario
	 */
	private OmsOrderoutboundresponse handleExistingShipment(
			SplitSellerOrder order,
			OmsOrderoutboundresponse omsOrdershipmentresponse) {
		
		SplitSellerShipment shipment = order.getSplitSellerShipments().stream().findFirst().orElse(null);
		
		if (shipment == null) {
			return createInvalidOrderResponse(omsOrdershipmentresponse);
		}
		
		List<shipmentItem> shipmentItems = new ArrayList<>();
		for (SplitSellerShipmentItem item : shipment.getSplitSellerShipmentItems()) {
			shipmentItem shipItem = new shipmentItem();
			shipItem.setChannelSkuCode(item.getSku());
			
			if (item.getItemId() != null) {
				shipItem.setOrderItemCode(item.getOrderItemId().toString());
			}
			
			if (item.getQuantity() != null) {
				shipItem.setQuantity(item.getQuantity().intValue());
			}
			
			shipmentItems.add(shipItem);
		}
		
		omsOrdershipmentresponse.setShipmentCode(shipment.getIncrementId());
		omsOrdershipmentresponse.setShipmentItems(shipmentItems);
		omsOrdershipmentresponse.setStatus(true);
		omsOrdershipmentresponse.setStatusCode("200");
		omsOrdershipmentresponse.setStatusMsg("Created Successfully");
		
		OrdershipmentResponse ordershipmentResponse = new OrdershipmentResponse();
		ordershipmentResponse.setShipmentId(shipment.getEntityId());
		ordershipmentResponse.setShipmentIncid(shipment.getIncrementId());
		ordershipmentResponse.setOrderId(order.getEntityId());
		omsOrdershipmentresponse.setResponse(ordershipmentResponse);
		
		return omsOrdershipmentresponse;
	}
	
	/**
	 * Builds success response
	 */
	private OmsOrderoutboundresponse buildSuccessResponse(
			OmsOrderoutboundresponse omsOrdershipmentresponse,
			SplitSellerShipment salesShipment,
			List<shipmentItem> shipmentItems) {
		
		omsOrdershipmentresponse.setShipmentCode(salesShipment.getIncrementId());
		omsOrdershipmentresponse.setShipmentItems(shipmentItems);
		omsOrdershipmentresponse.setStatus(true);
		omsOrdershipmentresponse.setStatusCode("200");
		omsOrdershipmentresponse.setStatusMsg("Created Successfully");
		
		OrdershipmentResponse ordershipmentResponse = new OrdershipmentResponse();
		ordershipmentResponse.setShipmentIncid(salesShipment.getIncrementId());
		ordershipmentResponse.setShipmentId(salesShipment.getEntityId());
		omsOrdershipmentresponse.setResponse(ordershipmentResponse);
		
		return omsOrdershipmentresponse;
	}
	
	/**
	 * Creates invalid order response
	 */
	private OmsOrderoutboundresponse createInvalidOrderResponse(OmsOrderoutboundresponse response) {
		response.setStatus(false);
		response.setStatusCode("202");
		response.setStatusMsg("Invalid Order!");
		response.setHasError(true);
		response.setErrorMessage("invalid order !");
		return response;
	}
	
	/**
	 * Creates error response
	 */
	private OmsOrderoutboundresponse createErrorResponse(OmsOrderoutboundresponse response, String errorMessage) {
		response.setStatus(false);
		response.setStatusCode("500");
		response.setStatusMsg("Failed");
		response.setHasError(true);
		response.setErrorMessage("Error: " + errorMessage);
		return response;
	}

	public String generateIncrementId(Integer newSequenceValue, Integer storeId) {

		Integer incrementStartValue = 1;
		int incrementStepValue = 1;

		String storeIdStr = storeId == 1 ? "" : String.valueOf(storeId);

		return storeIdStr + String.format(OrderConstants.INCREMENT_PADDING,
				((newSequenceValue - incrementStartValue) * incrementStepValue + incrementStartValue));
	}

	private SalesInvoice createInvoiceObjectToPersist(SalesOrder order, List<SalesCreditmemo> creditMemoList) {

		SalesInvoice salesInvoice = new SalesInvoice();
		String paymentMethod = null;
		boolean isCashOnDeliveryPartially = false;
		Map<String, BigDecimal> mapSkuList = new HashMap<>();
		if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
			for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
				paymentMethod = payment.getMethod();
			}
		}
		if (CollectionUtils.isNotEmpty(order.getSalesOrderItem())) {
			for (SalesOrderItem salesItem : order.getSalesOrderItem()) {
				if (null != salesItem.getQtyCanceled() && salesItem.getQtyCanceled().intValue() > 0
						&& null != salesItem.getProductType()
						&& !salesItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {

					mapSkuList.put(salesItem.getSku(), salesItem.getQtyCanceled());
				}
			}
		}

		salesInvoice.setBaseToGlobalRate(new BigDecimal(1));
		salesInvoice.setBaseToOrderRate(order.getBaseToOrderRate());

		salesInvoice.setStoreToBaseRate(order.getStoreToBaseRate());
		salesInvoice.setStoreToOrderRate(new BigDecimal(1));

		salesInvoice.setBaseCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);
		salesInvoice.setGlobalCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);
		salesInvoice.setOrderCurrencyCode(order.getOrderCurrencyCode());
		salesInvoice.setStoreCurrencyCode(order.getStoreCurrencyCode());

		salesInvoice.setCustomerNoteNotify(null);
		salesInvoice.setBillingAddressId(0);
		salesInvoice.setShippingAddressId(0);

		salesInvoice.setCreatedAt(new Timestamp(new Date().getTime()));
		salesInvoice.setUpdatedAt(new Timestamp(new Date().getTime()));
		if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
			salesInvoice.setShukranBurnedPoints(order.getSubSalesOrder().getTotalShukranCoinsBurned());
			salesInvoice.setShukranBurnedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
			salesInvoice.setShukranBurnedValueInCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
		}

		if (CollectionUtils.isNotEmpty(creditMemoList)) {

			BigDecimal baseSubTotal = BigDecimal.ZERO;
			BigDecimal taxAmount = BigDecimal.ZERO;
			BigDecimal baseTaxAmount = BigDecimal.ZERO;
			BigDecimal subTotal = BigDecimal.ZERO;
			BigDecimal subTotalIncludingTax = BigDecimal.ZERO;
			BigDecimal baseSubTotalIncludingTax = BigDecimal.ZERO;
			BigDecimal amastStoreCreditAmount = BigDecimal.ZERO;
			BigDecimal baseAmastStoreCreditAmount = BigDecimal.ZERO;
			BigDecimal grandTotal = BigDecimal.ZERO;
			BigDecimal baseGrandTotal = BigDecimal.ZERO;
			BigDecimal discountAmount = BigDecimal.ZERO;
			BigDecimal baseDiscountAmount = BigDecimal.ZERO;

			for (SalesCreditmemo salesCreditMemo : creditMemoList) {

				subTotal = subTotal.add(salesCreditMemo.getSubtotal());
				baseSubTotal = baseSubTotal.add(salesCreditMemo.getBaseSubtotal());
				grandTotal = grandTotal.add(salesCreditMemo.getGrandTotal());
				baseGrandTotal = baseGrandTotal.add(salesCreditMemo.getBaseGrandTotal());
				amastStoreCreditAmount = amastStoreCreditAmount.add(salesCreditMemo.getAmstorecreditAmount());
				baseAmastStoreCreditAmount = baseAmastStoreCreditAmount
						.add(salesCreditMemo.getAmstorecreditBaseAmount());
				subTotalIncludingTax = subTotalIncludingTax.add(salesCreditMemo.getSubtotalInclTax());
				baseSubTotalIncludingTax = baseSubTotalIncludingTax.add(salesCreditMemo.getBaseSubtotalInclTax());
				taxAmount = taxAmount.add(salesCreditMemo.getTaxAmount());
				baseTaxAmount = baseTaxAmount.add(salesCreditMemo.getBaseTaxAmount());

				discountAmount = discountAmount.add(salesCreditMemo.getDiscountAmount());
				baseDiscountAmount = baseDiscountAmount.add(salesCreditMemo.getBaseDiscountAmount());

			}

			BigDecimal invoiceSubTotal = order.getSubtotal().subtract(subTotal);
			BigDecimal invoiceBaseSubTotal = order.getBaseSubtotal().subtract(baseSubTotal);

			BigDecimal invoicegrandTotal = order.getGrandTotal().subtract(grandTotal);
			BigDecimal invoiceBaseGrandTotal = order.getBaseGrandTotal().subtract(baseGrandTotal);

			if (null != order.getAmstorecreditAmount()) {

				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(order.getIncrementId()).concat("\"]");
				LOGGER.info("OrderActionData: " + orderActionData);

				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData, 0);

				if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

				}

				BigDecimal invoiceAmastStoreCreditAMount = order.getAmstorecreditAmount()
						.subtract(cancelledAmastyAmount);
				BigDecimal invoiceBaseAmastStoreCreditAMount = invoiceAmastStoreCreditAMount
						.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
				salesInvoice.setAmstorecreditAmount(invoiceAmastStoreCreditAMount);
				salesInvoice.setAmstorecreditBaseAmount(invoiceBaseAmastStoreCreditAMount);

			} else {

				salesInvoice.setAmstorecreditAmount(BigDecimal.ZERO);
				salesInvoice.setAmstorecreditBaseAmount(BigDecimal.ZERO);

			}
			BigDecimal invoiceSubtotalIncludingTax = order.getSubtotalInclTax().subtract(subTotalIncludingTax);
			BigDecimal invoiceBaseSubtotalIncludingTax = order.getBaseSubtotalInclTax()
					.subtract(baseSubTotalIncludingTax);

			BigDecimal invoiceTaxAmount = order.getTaxAmount().subtract(taxAmount);
			BigDecimal invoiceBaseTaxAmount = order.getBaseTaxAmount().subtract(baseTaxAmount);

			BigDecimal invoiceDiscountAmount = order.getDiscountAmount().subtract(discountAmount);
			BigDecimal invoiceBaseDiscountAmount = order.getBaseDiscountAmount().subtract(baseDiscountAmount);

			salesInvoice.setSubtotal(invoiceSubTotal);
			salesInvoice.setBaseSubtotal(invoiceBaseSubTotal);
			salesInvoice.setTaxAmount(invoiceTaxAmount);
			salesInvoice.setBaseTaxAmount(invoiceBaseTaxAmount);
			salesInvoice.setSubtotalInclTax(invoiceSubtotalIncludingTax);
			salesInvoice.setBaseSubtotalInclTax(invoiceBaseSubtotalIncludingTax);

			salesInvoice.setGrandTotal(invoicegrandTotal);
			salesInvoice.setBaseGrandTotal(invoiceBaseGrandTotal);
			salesInvoice.setDiscountAmount(invoiceDiscountAmount);
			salesInvoice.setBaseDiscountAmount(invoiceBaseDiscountAmount);
			salesInvoice.setAmstorecreditAmount(amastStoreCreditAmount);

		} else if (StringUtils.isNotBlank(paymentMethod)
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
				&& isCashOnDeliveryPartially) {

			BigDecimal amastStoreCreditAmount = BigDecimal.ZERO;

			BigDecimal subTotal = refundHelper.getCanceledItemSubTotal(order, mapSkuList, null);
			BigDecimal baseSubTotal = subTotal.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
			BigDecimal grandTotal = refundHelper.getGrandTotalAmount(order, mapSkuList);
			BigDecimal baseGrandTotal = grandTotal.multiply(order.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
			BigDecimal subTotalIncludingTax = refundHelper.getCanceledItemQty(order, mapSkuList, null);
			BigDecimal baseSubTotalIncludingTax = subTotalIncludingTax.multiply(order.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
			BigDecimal taxAmount = refundHelper.getCanceledTaxItemQty(order, mapSkuList);
			BigDecimal baseTaxAmount = taxAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);

			BigDecimal discountAmount = refundHelper.getDiscountAmount(order, mapSkuList);
			BigDecimal baseDiscountAmount = discountAmount.multiply(order.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);

			BigDecimal invoiceSubTotal = order.getSubtotal().subtract(subTotal);
			BigDecimal invoiceBaseSubTotal = order.getBaseSubtotal().subtract(baseSubTotal);

			BigDecimal invoicegrandTotal = order.getGrandTotal().subtract(grandTotal);
			BigDecimal invoiceBaseGrandTotal = order.getBaseGrandTotal().subtract(baseGrandTotal);

			if (null != order.getAmstorecreditAmount()) {
				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(order.getIncrementId()).concat("\"]");
				LOGGER.info("OrderActionData : " + orderActionData);
				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData, 0);

				if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

				}

				BigDecimal invoiceAmastStoreCreditAMount = order.getAmstorecreditAmount()
						.subtract(cancelledAmastyAmount);
				BigDecimal invoiceBaseAmastStoreCreditAMount = invoiceAmastStoreCreditAMount
						.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
				salesInvoice.setAmstorecreditAmount(invoiceAmastStoreCreditAMount);
				salesInvoice.setAmstorecreditBaseAmount(invoiceBaseAmastStoreCreditAMount);
			} else {

				salesInvoice.setAmstorecreditAmount(BigDecimal.ZERO);
				salesInvoice.setAmstorecreditBaseAmount(BigDecimal.ZERO);

			}
			BigDecimal invoiceSubtotalIncludingTax = order.getSubtotalInclTax().subtract(subTotalIncludingTax);
			BigDecimal invoiceBaseSubtotalIncludingTax = order.getBaseSubtotalInclTax()
					.subtract(baseSubTotalIncludingTax);

			BigDecimal invoiceTaxAmount = order.getTaxAmount().subtract(taxAmount);
			BigDecimal invoiceBaseTaxAmount = order.getBaseTaxAmount().subtract(baseTaxAmount);

			BigDecimal invoiceDiscountAmount = order.getDiscountAmount().subtract(discountAmount);
			BigDecimal invoiceBaseDiscountAmount = order.getBaseDiscountAmount().subtract(baseDiscountAmount);

			salesInvoice.setSubtotal(invoiceSubTotal);
			salesInvoice.setBaseSubtotal(invoiceBaseSubTotal);
			salesInvoice.setTaxAmount(invoiceTaxAmount);
			salesInvoice.setBaseTaxAmount(invoiceBaseTaxAmount);
			salesInvoice.setSubtotalInclTax(invoiceSubtotalIncludingTax);
			salesInvoice.setBaseSubtotalInclTax(invoiceBaseSubtotalIncludingTax);

			salesInvoice.setGrandTotal(invoicegrandTotal);
			salesInvoice.setBaseGrandTotal(invoiceBaseGrandTotal);
			salesInvoice.setDiscountAmount(invoiceDiscountAmount);
			salesInvoice.setBaseDiscountAmount(invoiceBaseDiscountAmount);
			salesInvoice.setAmstorecreditAmount(amastStoreCreditAmount);
		} else {

			BigDecimal sumOrderedQty = order.getSalesOrderItem().stream()
					.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.map(SalesOrderItem::getQtyOrdered).reduce(BigDecimal.ZERO, BigDecimal::add);

			BigDecimal sumOrderedCancelled = order.getSalesOrderItem().stream()
					.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.map(SalesOrderItem::getQtyCanceled).reduce(BigDecimal.ZERO, BigDecimal::add);

			if (sumOrderedQty.intValue() != sumOrderedCancelled.intValue()
					&& null != order.getAmstorecreditAmount()) {

				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(order.getIncrementId()).concat("\"]");
				LOGGER.info(" OrderActionData:" + orderActionData);

				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData, 0);

				if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

				}

				BigDecimal invoiceAmastStoreCreditAMount = order.getAmstorecreditAmount()
						.subtract(cancelledAmastyAmount);
				BigDecimal invoiceBaseAmastStoreCreditAMount = invoiceAmastStoreCreditAMount
						.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
				salesInvoice.setAmstorecreditAmount(invoiceAmastStoreCreditAMount);
				salesInvoice.setAmstorecreditBaseAmount(invoiceBaseAmastStoreCreditAMount);

			} else {

				salesInvoice.setAmstorecreditAmount(BigDecimal.ZERO);
				salesInvoice.setAmstorecreditBaseAmount(BigDecimal.ZERO);
			}

			salesInvoice.setSubtotal(order.getSubtotal());
			salesInvoice.setBaseSubtotal(order.getBaseSubtotal());
			salesInvoice.setTaxAmount(order.getTaxAmount());
			salesInvoice.setBaseTaxAmount(order.getTaxAmount());
			salesInvoice.setSubtotalInclTax(order.getSubtotalInclTax());
			salesInvoice.setBaseSubtotalInclTax(order.getBaseSubtotalInclTax());

			salesInvoice.setGrandTotal(order.getGrandTotal());
			salesInvoice.setBaseGrandTotal(order.getBaseGrandTotal());
			salesInvoice.setDiscountAmount(order.getDiscountAmount());
			salesInvoice.setBaseDiscountAmount(order.getBaseDiscountAmount());
		}
		salesInvoice.setShippingAmount(order.getShippingAmount());
		salesInvoice.setBaseShippingAmount(order.getBaseShippingAmount());
		salesInvoice.setShippingTaxAmount(new BigDecimal(0));

		salesInvoice.setShippingInclTax(order.getShippingInclTax());
		salesInvoice.setBaseShippingInclTax(order.getBaseShippingInclTax());
		salesInvoice.setCashOnDeliveryFee(order.getCashOnDeliveryFee());
		salesInvoice.setBaseCashOnDeliveryFee(order.getBaseCashOnDeliveryFee());

		salesInvoice.setBaseShippingTaxAmount(new BigDecimal(0));

		// EAS coins update in invoice
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
			salesInvoice.setEasCoins(order.getSubSalesOrder().getEasCoins());
			salesInvoice.setEasValueInCurrency(order.getSubSalesOrder().getEasValueInCurrency());
			salesInvoice.setEasValueInBaseCurrency(order.getSubSalesOrder().getEasValueInBaseCurrency());
		}
		// EAS coins update in invoice

		return salesInvoice;
	}

	/**
	 * @param order
	 * @param invoice
	 */
	private void createInvoiceItems(SalesInvoice salesInvoice, SalesOrder salesOrder) {

		BigDecimal totalDiscount = BigDecimal.ZERO;
		BigDecimal totalbaseDiscount = BigDecimal.ZERO;

		BigDecimal totalTaxcompAmount = BigDecimal.ZERO;
		BigDecimal totalBaseTaxCompAmount = BigDecimal.ZERO;

		BigDecimal totalTaxAmount = BigDecimal.ZERO;
		BigDecimal totalBaseTaxAmount = BigDecimal.ZERO;

		BigDecimal totalrowTotal = BigDecimal.ZERO;
		BigDecimal totalBaseRowTotal = BigDecimal.ZERO;

		BigDecimal totalPriceIncludingTax = BigDecimal.ZERO;
		BigDecimal totalBaseTotalPriceIncludingTax = BigDecimal.ZERO;

		BigDecimal totalrowRotalIncludingTax = BigDecimal.ZERO;
		BigDecimal totalBaseRotalIncludingTax = BigDecimal.ZERO;

		BigDecimal totalSubTotal = BigDecimal.ZERO;
		BigDecimal basetotalSubTotal = BigDecimal.ZERO;

		BigDecimal totaItemQty = BigDecimal.ZERO;

		BigDecimal maxIGSTPrecent = BigDecimal.ZERO;
		BigDecimal maxCGSTPrecent = BigDecimal.ZERO;
		BigDecimal maxSGSTPrecent = BigDecimal.ZERO;

		BigDecimal iGSTCODAmount = BigDecimal.ZERO;
		BigDecimal cGSTCODAmount = BigDecimal.ZERO;
		BigDecimal sGSTCODAmount = BigDecimal.ZERO;

		BigDecimal iGSTShippingAmount = BigDecimal.ZERO;
		BigDecimal cGSTShippingAmount = BigDecimal.ZERO;
		BigDecimal sGSTShippingAmount = BigDecimal.ZERO;
		BigDecimal precentConstant  = new BigDecimal("100");
		BigDecimal inverseConstant  = new BigDecimal("1");



		for (SalesOrderItem orderItem : salesOrder.getSalesOrderItem()) {

			if (null != orderItem.getQtyCanceled() && null != orderItem.getQtyOrdered()
					&& orderItem.getQtyCanceled().intValue() != orderItem.getQtyOrdered().intValue()) {

				SalesInvoiceItem salesInvoiceItem = new SalesInvoiceItem();

				BigDecimal orderedQty = orderItem.getQtyOrdered();

				if (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() > 0) {

					orderedQty = orderedQty.subtract(orderItem.getQtyCanceled());
				}
				totaItemQty = totaItemQty.add(orderedQty);

				BigDecimal discount = BigDecimal.ZERO;
				BigDecimal baseDiscount = BigDecimal.ZERO;

				BigDecimal taxcompAmount = BigDecimal.ZERO;
				BigDecimal baseTaxCompAmount = BigDecimal.ZERO;

				BigDecimal subTotal;
				BigDecimal baseSubTotal;

				BigDecimal indivisualTaxAmount = orderItem.getTaxAmount()
						.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal taxAmount = indivisualTaxAmount.multiply(orderedQty).setScale(4, RoundingMode.HALF_UP);
				BigDecimal baseTaxAmount = taxAmount.multiply(salesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal indivisualRowTotal = orderItem.getRowTotal()
						.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal rowTotal = indivisualRowTotal.multiply(orderedQty).setScale(4, RoundingMode.HALF_UP);
				BigDecimal baseRowTotal = rowTotal.multiply(salesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal priceIncludingTax = orderItem.getPriceInclTax();
				BigDecimal basepriceIncludingTax = priceIncludingTax.multiply(salesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal indivisualRowTotalIncludeTAx = orderItem.getRowTotalInclTax()
						.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal rowRotalIncludingTax = indivisualRowTotalIncludeTAx.multiply(orderedQty).setScale(4,
						RoundingMode.HALF_UP);
				BigDecimal baseRowpriceIncludingTax = rowRotalIncludingTax.multiply(salesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
				subTotal = rowRotalIncludingTax;
				baseSubTotal = baseRowpriceIncludingTax;
				if (null != orderItem.getDiscountAmount()) {

					BigDecimal indivisualDiscount = orderItem.getDiscountAmount()
							.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					discount = indivisualDiscount.multiply(orderedQty).setScale(4, RoundingMode.HALF_UP);

					baseDiscount = discount.multiply(salesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
					rowTotal = rowTotal.subtract(discount);
					baseRowTotal = baseRowTotal.subtract(baseDiscount);
					rowRotalIncludingTax = rowRotalIncludingTax.subtract(discount);
					baseRowpriceIncludingTax = baseRowpriceIncludingTax.subtract(baseDiscount);

				}
				if (null != orderItem.getDiscountTaxCompensationAmount()) {

					taxcompAmount = orderItem.getDiscountTaxCompensationAmount()
							.divide(orderedQty, 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
					baseTaxCompAmount = taxcompAmount.multiply(salesOrder.getStoreToBaseRate()).setScale(4,
							RoundingMode.HALF_UP);
				}
				if (null != orderItem.getProductType()
						&& !orderItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {

					totalDiscount = totalDiscount.add(discount);
					totalbaseDiscount = totalbaseDiscount.add(baseDiscount);

					totalTaxcompAmount = totalTaxcompAmount.add(taxcompAmount);
					totalBaseTaxCompAmount = totalBaseTaxCompAmount.add(baseTaxCompAmount);

					totalTaxAmount = totalTaxAmount.add(taxAmount);
					totalBaseTaxAmount = totalBaseTaxAmount.add(baseTaxAmount);

					totalrowTotal = totalrowTotal.add(rowTotal);
					totalBaseRowTotal = totalBaseRowTotal.add(baseRowTotal);

					totalPriceIncludingTax = totalPriceIncludingTax.add(priceIncludingTax);
					totalBaseTotalPriceIncludingTax = totalBaseTotalPriceIncludingTax.add(basepriceIncludingTax);

					totalrowRotalIncludingTax = totalrowRotalIncludingTax.add(rowRotalIncludingTax);
					totalBaseRotalIncludingTax = totalBaseRotalIncludingTax.add(baseRowpriceIncludingTax);

					totalSubTotal = totalSubTotal.add(subTotal);
					basetotalSubTotal = basetotalSubTotal.add(baseSubTotal);

				}

				orderItem.setQtyInvoiced(orderedQty);
				salesInvoiceItem.setTaxAmount(taxAmount);
				salesInvoiceItem.setBaseTaxAmount(baseTaxAmount);
				salesInvoiceItem.setQuantity(orderedQty);

				salesInvoiceItem.setSku(orderItem.getSku());
				salesInvoiceItem.setName(orderItem.getName());
				salesInvoiceItem.setDescription(null);
				salesInvoiceItem.setAdditionalData(null);

				salesInvoiceItem.setOrderItemId(orderItem.getItemId());
				salesInvoiceItem.setPrice(orderItem.getPrice());
				salesInvoiceItem.setBasePrice(orderItem.getBasePrice());

				salesInvoiceItem.setRowTotal(rowTotal);
				salesInvoiceItem.setBaseRowTotal(baseRowTotal);
				salesInvoiceItem.setPriceInclTax(priceIncludingTax);
				salesInvoiceItem.setBasePriceInclTax(basepriceIncludingTax);
				salesInvoiceItem.setRowTotalInclTax(subTotal);
				salesInvoiceItem.setBaseRowTotalInclTax(baseSubTotal);

				salesInvoiceItem.setDiscountAmount(discount);
				salesInvoiceItem.setBaseDiscountAmount(baseDiscount);

				salesInvoiceItem.setDiscountTaxCompensationAmount(taxcompAmount);
				salesInvoiceItem.setBaseDiscountTaxCompensationAmount(baseTaxCompAmount);

				salesInvoice.addSalesInvoiceItem(salesInvoiceItem);
				salesInvoiceItem.setHsnCode(orderItem.getHsnCode());

				if (null != orderItem.getProductType()
						&& !orderItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE) && "IN".equalsIgnoreCase(region)) {
					for (SalesOrderItemTax salesOrderItemTax : orderItem.getSalesOrderItemTax()) {
						SalesInvoiceItemTax salesInvoiceItemTax = new SalesInvoiceItemTax();
						salesInvoiceItemTax.setTaxType(salesOrderItemTax.getTaxType());
						salesInvoiceItemTax.setTaxAmount(salesOrderItemTax.getTaxAmount());
						salesInvoiceItemTax.setTaxCountry(salesOrderItemTax.getTaxCountry());
						salesInvoiceItemTax.setTaxPercentage(salesOrderItemTax.getTaxPercentage());
						salesInvoiceItemTax.setSalesInvoice(salesInvoice);
						salesInvoiceItem.addSalesInvoiceItemTax(salesInvoiceItemTax);
						if ("IGST".equalsIgnoreCase(salesOrderItemTax.getTaxType())) {
							maxIGSTPrecent = (maxIGSTPrecent.compareTo(salesOrderItemTax.getTaxPercentage()) < 0)
									? salesOrderItemTax.getTaxPercentage()
									: maxIGSTPrecent;
						}
						if ("SGST".equalsIgnoreCase(salesOrderItemTax.getTaxType())) {
							maxSGSTPrecent = (maxSGSTPrecent.compareTo(salesOrderItemTax.getTaxPercentage()) < 0)
									? salesOrderItemTax.getTaxPercentage()
									: maxSGSTPrecent;
						}
						if ("CGST".equalsIgnoreCase(salesOrderItemTax.getTaxType())) {
							maxCGSTPrecent = (maxCGSTPrecent.compareTo(salesOrderItemTax.getTaxPercentage()) < 0)
									? salesOrderItemTax.getTaxPercentage()
									: maxCGSTPrecent;
						}
					}
				}

			}

		}
		salesInvoice.setDiscountAmount(totalDiscount);
		salesInvoice.setBaseDiscountAmount(totalbaseDiscount);
		salesInvoice.setSubtotalInclTax(totalSubTotal);
		salesInvoice.setBaseSubtotalInclTax(basetotalSubTotal);
		salesInvoice.setTaxAmount(totalTaxAmount);
		salesInvoice.setBaseTaxAmount(totalBaseTaxAmount);
		salesInvoice.setTotalQty(totaItemQty);
		if("IN".equalsIgnoreCase(region)) {
			if ((maxIGSTPrecent.compareTo(BigDecimal.ZERO) > 0)) {
				BigDecimal gSTConstant = inverseConstant.divide(precentConstant.add(maxIGSTPrecent), 4,
						RoundingMode.HALF_UP);
				if (salesInvoice.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
					iGSTCODAmount = salesInvoice.getCashOnDeliveryFee().multiply(maxIGSTPrecent).multiply(gSTConstant)
							.setScale(4, RoundingMode.HALF_UP);
				}
				if (salesInvoice.getShippingAmount().compareTo(BigDecimal.ZERO) > 0) {
					iGSTShippingAmount = salesInvoice.getShippingAmount().multiply(maxIGSTPrecent).multiply(gSTConstant)
							.setScale(4, RoundingMode.HALF_UP);
				}
			}
			if ((maxSGSTPrecent.compareTo(BigDecimal.ZERO) > 0) && (maxCGSTPrecent.compareTo(BigDecimal.ZERO) > 0)) {
				BigDecimal gSTConstant = inverseConstant.divide(precentConstant.add(maxSGSTPrecent).add(maxCGSTPrecent),
						4, RoundingMode.HALF_UP);
				if (salesInvoice.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
					sGSTCODAmount = salesInvoice.getCashOnDeliveryFee().multiply(maxSGSTPrecent).multiply(gSTConstant)
							.setScale(4, RoundingMode.HALF_UP);
					cGSTCODAmount = salesInvoice.getCashOnDeliveryFee().multiply(maxCGSTPrecent).multiply(gSTConstant)
							.setScale(4, RoundingMode.HALF_UP);
				}
				if (salesInvoice.getShippingAmount().compareTo(BigDecimal.ZERO) > 0) {
					sGSTShippingAmount = salesInvoice.getShippingAmount().multiply(maxSGSTPrecent).multiply(gSTConstant)
							.setScale(4, RoundingMode.HALF_UP);
					cGSTShippingAmount = salesInvoice.getShippingAmount().multiply(maxCGSTPrecent).multiply(gSTConstant)
							.setScale(4, RoundingMode.HALF_UP);
				}
			}
			salesInvoice.setMaxIgstPercent(maxIGSTPrecent);
			salesInvoice.setMaxSgstPercent(maxSGSTPrecent);
			salesInvoice.setMaxCgstPercent(maxCGSTPrecent);
			salesInvoice.setCashOnDeliveryIgst(iGSTCODAmount);
			salesInvoice.setCashOnDeliverySgst(sGSTCODAmount);
			salesInvoice.setCashOnDeliveryCgst(cGSTCODAmount);

			salesInvoice.setShippingIgst(iGSTShippingAmount);
			salesInvoice.setShippingSgst(sGSTShippingAmount);
			salesInvoice.setShippingCgst(cGSTShippingAmount);

			salesInvoice.setShippingTaxAmount(iGSTShippingAmount.add(sGSTShippingAmount).add(cGSTShippingAmount));
			salesInvoice.setCashOnDeliveryTaxAmount(iGSTCODAmount.add(sGSTCODAmount).add(cGSTCODAmount));
		}

		salesInvoice.setDiscountAmount(totalDiscount);
		BigDecimal grandTotal = totalrowRotalIncludingTax;
		if (null != salesOrder.getShippingAmount()) {
			grandTotal = grandTotal.add(salesOrder.getShippingAmount());
		}
		if (null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getDonationAmount()) {

			grandTotal = grandTotal.add(salesOrder.getSubSalesOrder().getDonationAmount());

		}
		if (null != salesOrder.getCashOnDeliveryFee()) {

			grandTotal = grandTotal.add(salesOrder.getCashOnDeliveryFee());

		}
		if (null != salesOrder.getImportFee()) {

			grandTotal = grandTotal.add(salesOrder.getImportFee());

		}
		if (null != salesOrder.getAmstorecreditAmount() && null != salesInvoice.getAmstorecreditAmount()) {

			grandTotal = grandTotal.subtract(salesInvoice.getAmstorecreditAmount());
		}

		if (null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getEasCoins()) {

			grandTotal = grandTotal.subtract(salesOrder.getSubSalesOrder().getEasValueInCurrency());
		}

		salesInvoice.setGrandTotal(grandTotal);
		salesInvoice.setBaseGrandTotal(
				grandTotal.multiply(salesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));

		BigDecimal totalInvoiced = new BigDecimal(0);
		if (null != salesInvoice.getGrandTotal()) {

			totalInvoiced = salesInvoice.getGrandTotal();

		}
		salesOrder.setTotalInvoiced(totalInvoiced);
		salesOrder.setBaseTotalInvoiced(
				totalInvoiced.multiply(salesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));

		salesOrder.setAmstorecreditInvoicedAmount(salesInvoice.getAmstorecreditAmount());
		salesOrder.setAmstorecreditInvoicedBaseAmount(salesInvoice.getAmstorecreditBaseAmount());
		salesOrder.setDiscountInvoiced(salesInvoice.getDiscountAmount());
		salesOrder.setBaseDiscountInvoiced(salesInvoice.getBaseDiscountAmount());
		salesOrder.setBaseDiscountInvoiced(salesInvoice.getBaseDiscountAmount());
		salesOrder.setDiscountTaxCompensationInvoiced(salesInvoice.getDiscountTaxCompensationAmount());
		salesOrder.setBaseDiscountTaxCompensationInvoiced(salesInvoice.getBaseDiscountTaxCompensationAmount());
		salesOrder.setTaxInvoiced(salesInvoice.getTaxAmount());
		salesOrder.setSubtotalInvoiced(salesInvoice.getSubtotal());
		salesOrder.setBaseSubtotalInvoiced(salesInvoice.getBaseSubtotal());
		salesOrder.setShippingInvoiced(salesInvoice.getShippingAmount());

	}

	public OtsTrackingResponse fetchSellerOrderStatus(SplitSellerOrder order) {
        System.out.println("fetchSellerOrderStatus: " + order.getIncrementId());
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, orderHelper.getAuthorization(internalHeaderBearerToken));
		String url = "";
		OtsTrackingRequest payload = new OtsTrackingRequest();
		try {
			payload.setParentOrderId(order.getSalesOrder().getEntityId());
			if(order.getSplitOrder() != null){
				payload.setSplitOrderId(order.getSplitOrder().getEntityId());
			}
			SplitSellerOrderItem splitSellerOrderItem = order.getSplitSellerOrderItems().stream().findFirst().orElse(null);
			if(splitSellerOrderItem != null){
				payload.setSku(splitSellerOrderItem.getSku());
			}
			HttpEntity<OtsTrackingRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
				if (null != Constants.orderCredentials
						&& null != Constants.orderCredentials.getOrderDetails().getOtsServiceBaseUrl()) {
					url = Constants.orderCredentials.getOrderDetails().getOtsServiceBaseUrl() + "/v1/trackOrderDetails";
				}

				LOGGER.info("OTS tracking URl:" + url);
                System.out.println("url: " + url);
				LOGGER.info(" OTS tracking request body" + mapper.writeValueAsString(requestBody));

				ResponseEntity<OtsTrackingResponse> response = restTemplate.exchange(url, HttpMethod.POST,
						requestBody, OtsTrackingResponse.class);

				LOGGER.info("response ots tracking Body" + mapper.writeValueAsString(response.getBody()));
				if (response.getStatusCode() == HttpStatus.OK) {

					LOGGER.info("ots tracking for:" + mapper.writeValueAsString(requestBody));

				}
				return response.getBody();

			} catch (RestClientException | JsonProcessingException e) {

				LOGGER.error("exception occoured during release inventory:" + e.getMessage());
				
			}
			return null;
	}

/**
	 * Process packbox details and create corresponding database records
	 */
	void processPackboxDetails(List<PackboxDetails> packboxDetailsList, SalesShipment salesShipment) {
		if (packboxDetailsList == null || packboxDetailsList.isEmpty()) {
			return;
		}

		Timestamp currentTime = new Timestamp(new Date().getTime());

		for (PackboxDetails packboxDetails : packboxDetailsList) {
			// Validate required fields
			if (packboxDetails.getBoxId() == null) {
				LOGGER.error("BoxId is required but was null in packboxDetails");
				throw new IllegalArgumentException("BoxId is required for packbox details");
			}
			
			SalesShipmentPackDetails salesShipmentPackDetails = new SalesShipmentPackDetails();
			
			// Set basic pack details
			salesShipmentPackDetails.setShipmentId(salesShipment.getEntityId());
			salesShipmentPackDetails.setLength(packboxDetails.getLength() != null ? BigDecimal.valueOf(packboxDetails.getLength()) : null);
			salesShipmentPackDetails.setBreadth(packboxDetails.getBreadth() != null ? BigDecimal.valueOf(packboxDetails.getBreadth()) : null);
			salesShipmentPackDetails.setHeight(packboxDetails.getHeight() != null ? BigDecimal.valueOf(packboxDetails.getHeight()) : null);
			salesShipmentPackDetails.setWeight(packboxDetails.getWeight() != null ? BigDecimal.valueOf(packboxDetails.getWeight()) : null);
			salesShipmentPackDetails.setBoxId(packboxDetails.getBoxId());
			salesShipmentPackDetails.setBoxCode(packboxDetails.getBoxCode() != null ? packboxDetails.getBoxCode() : packboxDetails.getBoxId().toString());
			salesShipmentPackDetails.setVolWeight(packboxDetails.getVolWeight() != null ? BigDecimal.valueOf(packboxDetails.getVolWeight()) : null);
			// salesShipmentPackDetails.setBoxSkuId(packboxDetails.getBoxSkuId());
			salesShipmentPackDetails.setCreatedAt(currentTime);
			salesShipmentPackDetails.setUpdatedAt(currentTime);

			// Save pack details directly to database
			SalesShipmentPackDetails savedPackDetails = salesshipmentPackDetailsRepository.saveAndFlush(salesShipmentPackDetails);

			// Process SKU quantity data if present
			if (packboxDetails.getSkuQuantityDataList() != null && !packboxDetails.getSkuQuantityDataList().isEmpty()) {
				for (SkuQuantityData skuData : packboxDetails.getSkuQuantityDataList()) {
					SalesShipmentPackDetailsItem packDetailsItem = new SalesShipmentPackDetailsItem();
					packDetailsItem.setPackDetailsId(savedPackDetails.getEntityId());
					packDetailsItem.setGlobalSkuId(skuData.getGlobalSkuId());
					packDetailsItem.setClientSkuId(skuData.getClientSkuId());
					packDetailsItem.setCount(skuData.getCount());

					// Save SKU data directly to database
					salesshipmentPackDetailsItemRepository.saveAndFlush(packDetailsItem);
				}
			}
		}
	}


	void processPackboxDetailsForSellerShipment(List<PackboxDetails> packboxDetailsList, SplitSellerShipment splitSellerShipment) {
		if (packboxDetailsList == null || packboxDetailsList.isEmpty()) {
			return;
		}

		Timestamp currentTime = new Timestamp(new Date().getTime());

		for (PackboxDetails packboxDetails : packboxDetailsList) {
			// Validate required fields
			if (packboxDetails.getBoxId() == null) {
				LOGGER.error("BoxId is required but was null in packboxDetails");
				throw new IllegalArgumentException("BoxId is required for packbox details");
			}
			
			SellerShipmentPackDetails salesShipmentPackDetails = new SellerShipmentPackDetails();
			
			// Set basic pack details
			salesShipmentPackDetails.setShipmentId(splitSellerShipment.getEntityId());
			salesShipmentPackDetails.setLength(packboxDetails.getLength() != null ? BigDecimal.valueOf(packboxDetails.getLength()) : null);
			salesShipmentPackDetails.setBreadth(packboxDetails.getBreadth() != null ? BigDecimal.valueOf(packboxDetails.getBreadth()) : null);
			salesShipmentPackDetails.setHeight(packboxDetails.getHeight() != null ? BigDecimal.valueOf(packboxDetails.getHeight()) : null);
			salesShipmentPackDetails.setWeight(packboxDetails.getWeight() != null ? BigDecimal.valueOf(packboxDetails.getWeight()) : null);
			salesShipmentPackDetails.setBoxId(packboxDetails.getBoxId());
			salesShipmentPackDetails.setBoxCode(packboxDetails.getBoxCode() != null ? packboxDetails.getBoxCode() : packboxDetails.getBoxId().toString());
			salesShipmentPackDetails.setVolWeight(packboxDetails.getVolWeight() != null ? BigDecimal.valueOf(packboxDetails.getVolWeight()) : null);
			// salesShipmentPackDetails.setBoxSkuId(packboxDetails.getBoxSkuId());
			salesShipmentPackDetails.setCreatedAt(currentTime);
			salesShipmentPackDetails.setUpdatedAt(currentTime);

			// Save pack details directly to database
			SellerShipmentPackDetails savedPackDetails = sellerShipmentPackDetailsRepository.saveAndFlush(salesShipmentPackDetails);

			// Process SKU quantity data if present
			if (packboxDetails.getSkuQuantityDataList() != null && !packboxDetails.getSkuQuantityDataList().isEmpty()) {
				for (SkuQuantityData skuData : packboxDetails.getSkuQuantityDataList()) {
					SellerShipmentPackDetailsItem packDetailsItem = new SellerShipmentPackDetailsItem();
					packDetailsItem.setPackDetailsId(savedPackDetails.getEntityId());
					packDetailsItem.setGlobalSkuId(skuData.getGlobalSkuId());
					packDetailsItem.setClientSkuId(skuData.getClientSkuId());
					packDetailsItem.setCount(skuData.getCount());

					// Save SKU data directly to database
					sellerShipmentPackDetailsItemRepository.saveAndFlush(packDetailsItem);
				}
			}
		}
	}

	/**
	 * Handles partial order cancellation when packed quantity is less than total ordered quantity.
	 * Creates an unfulfillment request and cancels the difference between ordered and packed quantities.
	 * 
	 * @param order The SplitSellerOrder to partially cancel
	 * @param packedQty The actual quantity that was packed
	 * @throws RuntimeException if the cancellation fails
	 */
	private void cancelPartialOrderForPackedQuantity(SplitSellerOrder order, BigDecimal packedQty) {
		LOGGER.info("Processing partial cancellation for order: " + order.getIncrementId() 
			+ ", packedQty: " + packedQty);
		
		// Create unfulfillment request
		OrderunfulfilmentRequest orderunfulfilmentRequest = new OrderunfulfilmentRequest();
		orderunfulfilmentRequest.setOrderCode(order.getIncrementId());
		
		// Build list of items to cancel with quantities
		List<OrderPushItem> orderItems = new ArrayList<>();
		List<SplitSellerOrderItem> splitSellerOrderItems = order.getSplitSellerOrderItems()
			.stream()
			.filter(item -> item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
			.toList();
		
		Map<String, BigDecimal> cancelledSellerSkuQtyMap = new HashMap<>();
		
		for (SplitSellerOrderItem item : splitSellerOrderItems) {
			BigDecimal cancelledQty = item.getQtyOrdered().subtract(packedQty);
			
			OrderPushItem orderPushItem = new OrderPushItem();
			orderPushItem.setChannelSkuCode(item.getSku());
			orderPushItem.setOrderItemCode(item.getItemId().toString());
			orderPushItem.setCancelledQuantity(cancelledQty);
			
			cancelledSellerSkuQtyMap.put(item.getSku(), cancelledQty);
			orderItems.add(orderPushItem);
		}
		
		// Set request properties
		orderunfulfilmentRequest.setOrderItems(orderItems);
		orderunfulfilmentRequest.setCancelledBy(OrderConstants.CANCELLED_BY_SELLER);
		orderunfulfilmentRequest.setCancelledSellerSkuQtyMap(cancelledSellerSkuQtyMap);
		orderunfulfilmentRequest.setSplitSellerOrder(order);
		
		// Execute cancellation
		OmsUnfulfilmentResponse response = orderHelper.cancelSellerOrder(order, orderunfulfilmentRequest, new HashMap<>());
		
		if (response.getHasError()) {
			LOGGER.error("Error in canceling partial order for " + order.getIncrementId() 
				+ ": " + response.getErrorMessage());
			throw new RuntimeException("Failed to cancel partial order: " + response.getErrorMessage());
		} else {
			LOGGER.info("Order partially canceled successfully for packed quantity less than total quantity. "
				+ "Order code: " + order.getIncrementId() + ", Packed: " + packedQty);
		}
	}

    /**
     * Sets shipmentCode on back-order items tied to the given SplitSellerOrder using the provided shipment increment id.
     */
    private void updateBackOrderShipmentCode(SplitSellerOrder splitSellerOrder, String shipmentIncrementId) {
        try {
            if (splitSellerOrder == null || StringUtils.isBlank(shipmentIncrementId)) {
                return;
            }
            if (CollectionUtils.isEmpty(splitSellerOrder.getSplitSellerOrderItems())) {
                return;
            }
            for (SplitSellerOrderItem splitItem : splitSellerOrder.getSplitSellerOrderItems()) {
                if (splitItem == null || StringUtils.isBlank(splitItem.getSku())) {
                    continue;
                }
                List<SellerBackOrderItem> boItems = sellerBackOrderItemRepository
                        .findBySplitSellerOrderAndSku(splitSellerOrder, splitItem.getSku());
                if (CollectionUtils.isEmpty(boItems)) {
                    continue;
                }
                for (SellerBackOrderItem boItem : boItems) {
                    boItem.setShipmentCode(shipmentIncrementId);
                }
                sellerBackOrderItemRepository.saveAll(boItems);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to update back-order items shipmentCode for seller order: "
                    + (splitSellerOrder != null ? splitSellerOrder.getEntityId() : "null")
                    + " error: " + ex.getMessage(), ex);
        }
    }

	/**
	 * Update seller orders' status to 'received_at_warehouse' for back orders when last mile shipment is created.
	 * Use when shipment is created for the full (non-split) order.
	 *
	 * @param mainOrder The main order for which shipment was created
	 */
	public void updateSellerOrdersStatusForBackOrders(SalesOrder mainOrder) {
		updateSellerOrdersStatusForBackOrders(mainOrder, null);
	}

	/**
	 * Update seller orders' status to 'received_at_warehouse' for back orders when last mile shipment is created.
	 * Only runs for global (full) orders. Local splits do not have back orders, so this must not be called for local shipment.
	 * Only seller orders in status 'outward_midmile' are updated (not OPEN).
	 *
	 * @param mainOrder The main order for which shipment was created
	 * @param splitOrder When non-null, shipment was for a local split; no update is performed. When null, shipment was for full (global) order.
	 */
	public void updateSellerOrdersStatusForBackOrders(SalesOrder mainOrder, SplitSalesOrder splitOrder) {
		try {
			if (mainOrder == null || mainOrder.getEntityId() == null) {
				LOGGER.warn("Cannot update seller orders status: main order is null");
				return;
			}
			// Only update for global (full) orders. Local does not have back orders.
			if (splitOrder != null && splitOrder.getEntityId() != null) {
				LOGGER.info("Skipping received_at_warehouse update for local split: " + splitOrder.getIncrementId() + " (back orders only for global)");
				return;
			}

			// Shipment created for full (global) order: consider all back order items for main order
			List<SellerBackOrderItem> backOrderItems = sellerBackOrderItemRepository
					.findByMainOrderAndStatusNot(mainOrder, "CLOSED");
			if (backOrderItems == null) {
				backOrderItems = Collections.emptyList();
			}

			if (CollectionUtils.isEmpty(backOrderItems)) {
				LOGGER.info("No active back order items found for main order: " + mainOrder.getIncrementId());
				return;
			}

			LOGGER.info("Found " + backOrderItems.size() + " back order items for main order: "
					+ mainOrder.getIncrementId());

			// Only seller orders in outward_midmile status are updated (not OPEN).
			Map<Integer, SplitSellerOrder> uniqueSellerOrders = new HashMap<>();
			for (SellerBackOrderItem backOrderItem : backOrderItems) {
				if (backOrderItem.getSplitSellerOrder() == null
						|| backOrderItem.getSplitSellerOrder().getEntityId() == null) {
					continue;
				}
				SplitSellerOrder sellerOrder = backOrderItem.getSplitSellerOrder();
				if (sellerOrder.getStatus() == null
						|| !OrderConstants.OUTWARD_MIDMILE_ORDER_STATUS.equalsIgnoreCase(sellerOrder.getStatus())) {
					continue;
				}
				uniqueSellerOrders.put(sellerOrder.getEntityId(), sellerOrder);
			}

			if (uniqueSellerOrders.isEmpty()) {
				LOGGER.info("No seller orders in outward_midmile status found for main order: "
						+ mainOrder.getIncrementId() + " (received_at_warehouse only for outward_midmile back orders)");
				return;
			}

			// Update status to 'received_at_warehouse' for each unique seller order
			Timestamp updatedAt = new Timestamp(new Date().getTime());
			int updatedCount = 0;
			for (SplitSellerOrder sellerOrder : uniqueSellerOrders.values()) {
				try {
					sellerOrder.setStatus("received_at_warehouse");
					sellerOrder.setUpdatedAt(updatedAt);
					splitSellerOrderRepository.saveAndFlush(sellerOrder);
					updatedCount++;
					LOGGER.info("Updated seller order status to 'received_at_warehouse' for seller order: "
							+ sellerOrder.getIncrementId() + " (main order: " + mainOrder.getIncrementId() + ")");
				} catch (Exception e) {
					LOGGER.error("Error updating seller order status for seller order: "
							+ (sellerOrder != null ? sellerOrder.getIncrementId() : "null"), e);
				}
			}

			LOGGER.info("Successfully updated " + updatedCount + " seller orders to 'received_at_warehouse' "
					+ "for main order: " + mainOrder.getIncrementId());

		} catch (Exception e) {
			LOGGER.error("Error updating seller orders status for back orders of main order: "
					+ (mainOrder != null ? mainOrder.getIncrementId() : "null"), e);
			// Don't throw exception - log and continue to avoid breaking shipment creation
		}
	}
}

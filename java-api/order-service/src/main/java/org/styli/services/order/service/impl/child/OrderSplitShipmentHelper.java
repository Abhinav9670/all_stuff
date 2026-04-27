package org.styli.services.order.service.impl.child;

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
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.helper.*;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.OrdershipmentResponse;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.response.shipmentItem;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.impl.SplitOrderZatcaServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.db.product.exception.WmsException;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OrderSplitShipmentHelper {

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
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesShipmentRepository salesshipmentRepository;

	@Autowired
	SalesInvoiceRepository salesInvoiceRepository;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@Autowired
	OrderHelperV3 orderHelperV3;

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
	SplitOrderZatcaServiceImpl splitOrderZatcaServiceImpl;

	@Autowired
	OrderShipmentHelper orderShipmentHelper;

	@Autowired
	OrderpushHelper orderPushHelper;

	@Autowired
	@Lazy
	org.styli.services.order.service.SalesOrderServiceV3 salesOrderServiceV3;

	@Value("${region}")
	private String region;

	private static final Log LOGGER = LogFactory.getLog(OrderSplitShipmentHelper.class);

	@Transactional
	public OmsOrderoutboundresponse createSplitOrderShipment(@Valid OrderViewRequest request, SplitSalesOrder splitSalesOrder) {
		try{
			// Get the sales order from the split sales order
			SalesOrder order = splitSalesOrder.getSalesOrder();
			OmsOrderoutboundresponse omsOrdershipmentresponse = new OmsOrderoutboundresponse();
			OrdershipmentResponse ordershipmentResponse = new OrdershipmentResponse();
			//boolean bnplStatusFlag = false;
			//boolean captureFlag = false;

			if (null != splitSalesOrder && splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)
					&& null != splitSalesOrder.getWmsStatus() && splitSalesOrder.getWmsStatus().equals(1)
					&& !"1".equalsIgnoreCase(splitSalesOrder.getExtOrderId())) {

				SalesOrderAddress existingAddress = order.getSalesOrderAddress().stream()
						.filter(e -> e.getAddressType().equals(OrderConstants.ORDER_ADDRESS_TYPE_SHIPPING)).findFirst()
						.orElse(null);

				SalesShipment salesShipment = new SalesShipment();

				salesShipment.setCreatedAt(new Timestamp(new Date().getTime()));
				salesShipment.setUpdatedAt(new Timestamp(new Date().getTime()));
				salesShipment.setCustomerId(splitSalesOrder.getCustomerId());
				salesShipment.setOrderId(order.getEntityId());
				salesShipment.setSplitSalesOrder(splitSalesOrder);
				if (null != existingAddress) {
					salesShipment.setShippingAddressId(existingAddress.getEntityId());
				}
				salesShipment.setEmailSent(0);
				salesShipment.setStoreId(splitSalesOrder.getStoreId());
				salesShipment.setTotalQty(splitSalesOrder.getTotalQtyOrdered());

				List<shipmentItem> shipmentItems = new ArrayList<>();
				CopyOnWriteArrayList<SplitSalesOrderItem> orderListItems = new CopyOnWriteArrayList<>(splitSalesOrder.getSplitSalesOrderItems());
				for (SplitSalesOrderItem orderItem : orderListItems) {

					if (null == orderItem.getQtyCanceled()
							|| (null != orderItem.getQtyCanceled() && orderItem.getQtyCanceled().intValue() == 0)) {

						shipmentItem shipmentItem = new shipmentItem();
						SalesShipmentItem salesshipmentItem = new SalesShipmentItem();

						salesshipmentItem.setOrderItemId(orderItem.getSalesOrderItem().getItemId());
						salesshipmentItem.setSplitOrderItemId(orderItem.getItemId());
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
						salesshipmentItem.setOrderItemId(orderItem.getSalesOrderItem().getItemId());

						salesshipmentItem.setSplitOrderItemId(orderItem.getItemId());
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

				// Pessimistic lock on split order to prevent concurrent duplicate creation
				SplitSalesOrder lockedOrder = splitSalesOrderRepository.lockByEntityId(splitSalesOrder.getEntityId());
				splitSalesOrder = lockedOrder != null ? lockedOrder : splitSalesOrder;
				// Uniqueness guard for split orders: avoid duplicate shipment for (orderId, splitOrderId)
				long existingShipments = salesshipmentRepository.countByOrderIdAndSplit(splitSalesOrder.getSalesOrder().getEntityId(), splitSalesOrder.getEntityId());
				if (existingShipments > 0) {
					omsOrdershipmentresponse.setStatus(true);
					omsOrdershipmentresponse.setStatusCode("200");
					omsOrdershipmentresponse.setStatusMsg("Shipment already exists for split order");
					return omsOrdershipmentresponse;
				}

				// Check if packboxDetailsList has more than one item to set is_mps flag
				if (request.getPackboxDetailsList() != null && request.getPackboxDetailsList().size() > 1) {
					salesShipment.setIsMps(true);
				}

				SalesShipment newsalesShipment = salesshipmentRepository.saveAndFlush(salesShipment);

				salesShipment.setIncrementId(generateIncrementId(newsalesShipment.getEntityId(), splitSalesOrder.getStoreId()));

				// Process packboxDetailsList if present
				if (request.getPackboxDetailsList() != null && !request.getPackboxDetailsList().isEmpty()) {
					orderShipmentHelper.processPackboxDetails(request.getPackboxDetailsList(), salesShipment);
				}

				salesshipmentRepository.saveAndFlush(salesShipment);

				// Send Braze notification for dangerous goods shipments immediately after shipment creation
				try {
					if (salesOrderServiceV3 != null) {
						salesOrderServiceV3.sendDangerousGoodsBrazeNotification(order, salesShipment);
					}
				} catch (Exception e) {
					LOGGER.error("Error sending dangerous goods Braze notification after split shipment creation: " + e.getMessage(), e);
					// Don't fail shipment creation if Braze notification fails
				}

				if (CollectionUtils.isEmpty(splitSalesOrder.getSplitSalesInvoices())) {
					// Uniqueness guard for invoice by (orderId, splitOrderId)
					long existingInvoices = salesInvoiceRepository.countByOrderIdAndSplit(splitSalesOrder.getSalesOrder().getEntityId(), splitSalesOrder.getEntityId());
					if (existingInvoices > 0) {
						LOGGER.info("Split invoice already exists for orderId=" + splitSalesOrder.getSalesOrder().getEntityId() + ", splitOrderId=" + splitSalesOrder.getEntityId());
					} else {
					/** create invoice **/
					List<SalesCreditmemo> creditMemoList = salesCreditmemoRepository.findBySplitOrderId(splitSalesOrder.getEntityId());
					// EAS coins in invoice added inside createInvoiceObjectToPersist
					SalesInvoice salesInvoice = createInvoiceObjectToPersist(splitSalesOrder, creditMemoList);
					createInvoiceItems(salesInvoice, splitSalesOrder);
					splitSalesOrder.addSalesInvoice(salesInvoice);
					}
				}

				splitSalesOrder.setState(OrderConstants.MOVE_ORDER_STATE);
				splitSalesOrder.setStatus(OrderConstants.PACKED_ORDER_STATUS);
				splitSalesOrder.setWmsStatus(2);

				// If main order is having multiple split sales orders , loop all the split sales orders and find the order status
				// If all are PACKED_ORDER_STATUS make  salesorderGrid as PACKED_ORDER_STATUS
				List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findBySalesOrder(order);
				AtomicBoolean isAllPacked = new AtomicBoolean(true);
				SplitSalesOrder extOrder = splitSalesOrderRepository.findByEntityId(splitSalesOrder.getEntityId());
				splitSalesOrders.stream().filter(e -> e.getEntityId()!= extOrder.getEntityId())
						.forEach(e -> {
							if (!e.getStatus().equalsIgnoreCase(OrderConstants.PACKED_ORDER_STATUS)) {
								isAllPacked.set(false);
							}
						});
				if (isAllPacked.get()) {
					order.setState(OrderConstants.MOVE_ORDER_STATE);
					order.setStatus(OrderConstants.PACKED_ORDER_STATUS);
					order.setWmsStatus(2);
					SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());
					salesorderGrid.setStatus(OrderConstants.PACKED_ORDER_STATUS);

					salesOrderGridRepository.saveAndFlush(salesorderGrid);
					SalesOrder updatedOrder = salesOrderRepository.saveAndFlush(order);
					paymentDtfHelper.updateOrderStatusHistory(updatedOrder, OrderConstants.SHIPMENT_CREATE_MESSAGE,
							OrderConstants.SHIPMENT_STATUS_HISTORY_ENTITY, updatedOrder.getStatus());
					paymentDtfHelper.updateOrderStatusHistory(updatedOrder, OrderConstants.INVOICE_CREATE_MESSAGE,
							OrderConstants.INVOICE_STATUS_HISTORY_ENTITY, updatedOrder.getStatus());

				}
				// Split sales order and sales order both need to be updated with the new shipment details
				SplitSalesOrder updatedOrder2 = splitSalesOrderRepository.saveAndFlush(splitSalesOrder);

				// both main order and split order need to be updated with the new shipment details
				paymentDtfHelper.updateOrderStatusHistoryWithSplitSalesOrder(updatedOrder2, OrderConstants.SHIPMENT_CREATE_MESSAGE,
						OrderConstants.SHIPMENT_STATUS_HISTORY_ENTITY, updatedOrder2.getStatus());
				paymentDtfHelper.updateOrderStatusHistoryWithSplitSalesOrder(updatedOrder2, OrderConstants.INVOICE_CREATE_MESSAGE,
						OrderConstants.INVOICE_STATUS_HISTORY_ENTITY, updatedOrder2.getStatus());

				if (CollectionUtils.isNotEmpty(updatedOrder2.getSplitSalesInvoices())) {

					SalesInvoice invoice = splitSalesOrder.getSplitSalesInvoices().stream().findFirst().orElse(null);

					if (null != invoice) {

						invoice.setIncrementId(generateIncrementId(invoice.getEntityId(), splitSalesOrder.getStoreId()));
						ObjectMapper mapper= new ObjectMapper();
						LOGGER.info("Split order create shipment invoice data+"+ mapper.writeValueAsString(invoice));
						salesInvoiceRepository.saveAndFlush(invoice);

						Optional<String> paymentMethod = order.getSalesOrderPayment().stream()
								.map(SalesOrderPayment::getMethod).findFirst();

						if(paymentMethod.isPresent() && OrderConstants.checkBNPLPaymentMethods(paymentMethod.get())) {
							paymentUtility.processBNPLPaymentV2(paymentUtility, splitSalesOrder, paymentMethod, tabbyHelper, tamaraHelper);
						}else if(paymentMethod.isPresent()
								&& null != splitSalesOrder.getPayfortAuthorized()
								&& splitSalesOrder.getPayfortAuthorized() == 1) {
							PayfortReposne response = paymentUtility.processPayfortAuthorizedCapturePaymentV2(paymentUtility, splitSalesOrder, paymentMethod);
							if(null !=response && response.isStatus()) {
								splitSalesOrder.setAuthorizationCapture(1);
								updatedOrder2 = splitSalesOrderRepository.saveAndFlush(splitSalesOrder);
								//update in Main order also
								order.setAuthorizationCapture(1);
								salesOrderRepository.saveAndFlush(order);
							}
						}
						// ZATCA Start
						if(Constants.getZatcaFlag(splitSalesOrder.getStoreId())) {
							splitOrderZatcaServiceImpl.sendZatcaInvoiceSplitOrdder(splitSalesOrder,false,null);
						}
						// ZATCA Ends
						LOGGER.info("Split order invoice saved successfully");
					}

				}
                // Along with SKU , pass warehousid
				orderHelperV3.releaseInventoryQtyV3(splitSalesOrder, new HashMap<>(), false, OrderConstants.SHIPMENT_RELEASE_INVENTORY);

				orderHelperV3.updateStatusHistoryV3(splitSalesOrder, false, false, false, false, true);

				omsOrdershipmentresponse.setShipmentCode(salesShipment.getIncrementId());
				omsOrdershipmentresponse.setShipmentItems(shipmentItems);

				ordershipmentResponse.setShipmentIncid(salesShipment.getIncrementId());
				ordershipmentResponse.setShipmentId(salesShipment.getEntityId());
				omsOrdershipmentresponse.setStatus(true);
				omsOrdershipmentresponse.setStatusCode("200");
				omsOrdershipmentresponse.setStatusMsg("Created Successfully");

				orderPushHelper.pushReceivedOrderToSellerCentral(order);
				orderPushHelper.pushStyliOrderPackedToSellerCentral(order);

				// Do not update received_at_warehouse for local split: back orders exist only for global orders.
				if (splitSalesOrder.getIncrementId() != null && splitSalesOrder.getIncrementId().toUpperCase().contains("-G")) {
					orderShipmentHelper.updateSellerOrdersStatusForBackOrders(order, null);
				}
			} else if (null != splitSalesOrder && CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesShipments())) {

				SalesShipment shipment = splitSalesOrder.getSplitSalesShipments().stream().findFirst().orElse(null);

				if (null != shipment) {

					List<shipmentItem> shipmentItems = new ArrayList<>();

					for (SalesShipmentItem item : shipment.getSalesShipmentItem()) {
						shipmentItem shipItem = new shipmentItem();
						shipItem.setChannelSkuCode(item.getSku());
						if (null != item.getItemId()) {
							shipItem.setOrderItemCode(null!=item.getSplitOrderItemId()?item.getSplitOrderItemId().toString():item.getOrderItemId().toString());
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

	public String generateIncrementId(Integer newSequenceValue, Integer storeId) {

		Integer incrementStartValue = 1;
		int incrementStepValue = 1;

		String storeIdStr = storeId == 1 ? "" : String.valueOf(storeId);

		return storeIdStr + String.format(OrderConstants.INCREMENT_PADDING,
				((newSequenceValue - incrementStartValue) * incrementStepValue + incrementStartValue));
	}

	private SalesInvoice createInvoiceObjectToPersist(SplitSalesOrder splitSalesOrder, List<SalesCreditmemo> creditMemoList) {

		SalesInvoice salesInvoice = new SalesInvoice();
		String paymentMethod = null;
		boolean isCashOnDeliveryPartially = false;
		Map<String, BigDecimal> mapSkuList = new HashMap<>();
		if (CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesOrderPayments())) {
			for (SplitSalesOrderPayment payment : splitSalesOrder.getSplitSalesOrderPayments()) {
				paymentMethod = payment.getMethod();
			}
		}
		if (CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesOrderItems())) {
			for (SplitSalesOrderItem salesItem :splitSalesOrder.getSplitSalesOrderItems()) {
				if (null != salesItem.getQtyCanceled() && salesItem.getQtyCanceled().intValue() > 0
						&& null != salesItem.getProductType()
						&& !salesItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {

					mapSkuList.put(salesItem.getSku(), salesItem.getQtyCanceled());
				}
			}
		}
		salesInvoice.setSalesOrder(splitSalesOrder.getSalesOrder());
		salesInvoice.setSplitSalesOrder(splitSalesOrder);
		salesInvoice.setBaseToGlobalRate(new BigDecimal(1));
		salesInvoice.setBaseToOrderRate(splitSalesOrder.getBaseToOrderRate());

		salesInvoice.setStoreToBaseRate(splitSalesOrder.getStoreToBaseRate());
		salesInvoice.setStoreToOrderRate(new BigDecimal(1));

		salesInvoice.setBaseCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);
		salesInvoice.setGlobalCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);
		salesInvoice.setOrderCurrencyCode(splitSalesOrder.getOrderCurrencyCode());
		salesInvoice.setStoreCurrencyCode(splitSalesOrder.getStoreCurrencyCode());

		salesInvoice.setCustomerNoteNotify(null);
		salesInvoice.setBillingAddressId(0);
		salesInvoice.setShippingAddressId(0);

		salesInvoice.setCreatedAt(new Timestamp(new Date().getTime()));
		salesInvoice.setUpdatedAt(new Timestamp(new Date().getTime()));
		if(splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
			salesInvoice.setShukranBurnedPoints(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
			salesInvoice.setShukranBurnedValueInBaseCurrency(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
			salesInvoice.setShukranBurnedValueInCurrency(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency());
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

			BigDecimal invoiceSubTotal = splitSalesOrder.getSubtotal().subtract(subTotal);
			BigDecimal invoiceBaseSubTotal = splitSalesOrder.getBaseSubtotal().subtract(baseSubTotal);

			BigDecimal invoicegrandTotal = splitSalesOrder.getGrandTotal().subtract(grandTotal);
			BigDecimal invoiceBaseGrandTotal = splitSalesOrder.getBaseGrandTotal().subtract(baseGrandTotal);

			if (null != splitSalesOrder.getAmstorecreditAmount()) {

				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(splitSalesOrder.getIncrementId()).concat("\"]");
				LOGGER.info("Split order OrderActionData: " + orderActionData);

				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData, 0);

				if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

				}

				BigDecimal invoiceAmastStoreCreditAMount = splitSalesOrder.getAmstorecreditAmount()
						.subtract(cancelledAmastyAmount);
				BigDecimal invoiceBaseAmastStoreCreditAMount = invoiceAmastStoreCreditAMount
						.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
				salesInvoice.setAmstorecreditAmount(invoiceAmastStoreCreditAMount);
				salesInvoice.setAmstorecreditBaseAmount(invoiceBaseAmastStoreCreditAMount);

			} else {

				salesInvoice.setAmstorecreditAmount(BigDecimal.ZERO);
				salesInvoice.setAmstorecreditBaseAmount(BigDecimal.ZERO);

			}
			BigDecimal invoiceSubtotalIncludingTax = splitSalesOrder.getSubtotalInclTax().subtract(subTotalIncludingTax);
			BigDecimal invoiceBaseSubtotalIncludingTax = splitSalesOrder.getBaseSubtotalInclTax()
					.subtract(baseSubTotalIncludingTax);

			BigDecimal invoiceTaxAmount = splitSalesOrder.getTaxAmount().subtract(taxAmount);
			BigDecimal invoiceBaseTaxAmount = splitSalesOrder.getBaseTaxAmount().subtract(baseTaxAmount);

			BigDecimal invoiceDiscountAmount = splitSalesOrder.getDiscountAmount().subtract(discountAmount);
			BigDecimal invoiceBaseDiscountAmount = splitSalesOrder.getBaseDiscountAmount().subtract(baseDiscountAmount);

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

			BigDecimal subTotal = refundHelper.getCanceledItemSubTotal(splitSalesOrder.getSalesOrder(), mapSkuList, null);
			BigDecimal baseSubTotal = subTotal.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
			BigDecimal grandTotal = refundHelper.getGrandTotalAmount(splitSalesOrder.getSalesOrder(), mapSkuList);
			BigDecimal baseGrandTotal = grandTotal.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
			BigDecimal subTotalIncludingTax = refundHelper.getCanceledItemQty(splitSalesOrder.getSalesOrder(), mapSkuList, null);
			BigDecimal baseSubTotalIncludingTax = subTotalIncludingTax.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
			BigDecimal taxAmount = refundHelper.getCanceledTaxItemQty(splitSalesOrder.getSalesOrder(), mapSkuList);
			BigDecimal baseTaxAmount = taxAmount.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);

			BigDecimal discountAmount = refundHelper.getDiscountAmount(splitSalesOrder.getSalesOrder(), mapSkuList);
			BigDecimal baseDiscountAmount = discountAmount.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);

			BigDecimal invoiceSubTotal = splitSalesOrder.getSubtotal().subtract(subTotal);
			BigDecimal invoiceBaseSubTotal = splitSalesOrder.getBaseSubtotal().subtract(baseSubTotal);

			BigDecimal invoicegrandTotal = splitSalesOrder.getGrandTotal().subtract(grandTotal);
			BigDecimal invoiceBaseGrandTotal = splitSalesOrder.getBaseGrandTotal().subtract(baseGrandTotal);

			if (null != splitSalesOrder.getAmstorecreditAmount()) {
				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(splitSalesOrder.getIncrementId()).concat("\"]");
				LOGGER.info("Split order OrderActionData : " + orderActionData);
				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData, 0);

				if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

				}

				BigDecimal invoiceAmastStoreCreditAMount = splitSalesOrder.getAmstorecreditAmount()
						.subtract(cancelledAmastyAmount);
				BigDecimal invoiceBaseAmastStoreCreditAMount = invoiceAmastStoreCreditAMount
						.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
				salesInvoice.setAmstorecreditAmount(invoiceAmastStoreCreditAMount);
				salesInvoice.setAmstorecreditBaseAmount(invoiceBaseAmastStoreCreditAMount);
			} else {

				salesInvoice.setAmstorecreditAmount(BigDecimal.ZERO);
				salesInvoice.setAmstorecreditBaseAmount(BigDecimal.ZERO);

			}
			BigDecimal invoiceSubtotalIncludingTax = splitSalesOrder.getSubtotalInclTax().subtract(subTotalIncludingTax);
			BigDecimal invoiceBaseSubtotalIncludingTax = splitSalesOrder.getBaseSubtotalInclTax()
					.subtract(baseSubTotalIncludingTax);

			BigDecimal invoiceTaxAmount = splitSalesOrder.getTaxAmount().subtract(taxAmount);
			BigDecimal invoiceBaseTaxAmount = splitSalesOrder.getBaseTaxAmount().subtract(baseTaxAmount);

			BigDecimal invoiceDiscountAmount = splitSalesOrder.getDiscountAmount().subtract(discountAmount);
			BigDecimal invoiceBaseDiscountAmount = splitSalesOrder.getBaseDiscountAmount().subtract(baseDiscountAmount);

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

			BigDecimal sumOrderedQty = splitSalesOrder.getSplitSalesOrderItems().stream()
					.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.map(SplitSalesOrderItem::getQtyOrdered).reduce(BigDecimal.ZERO, BigDecimal::add);

			BigDecimal sumOrderedCancelled = splitSalesOrder.getSplitSalesOrderItems().stream()
					.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.map(SplitSalesOrderItem::getQtyCanceled).reduce(BigDecimal.ZERO, BigDecimal::add);

			if (sumOrderedQty.intValue() != sumOrderedCancelled.intValue()
					&& null != splitSalesOrder.getAmstorecreditAmount()) {

				BigDecimal cancelledAmastyAmount = new BigDecimal("0");
				String orderActionData = "[\"".concat(splitSalesOrder.getIncrementId()).concat("\"]");
				LOGGER.info("Split order  OrderActionData:" + orderActionData);

				List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
						.findByActionDataAndAction(orderActionData, 0);

				if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

					for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

						cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
					}

				}

				BigDecimal invoiceAmastStoreCreditAMount = splitSalesOrder.getAmstorecreditAmount()
						.subtract(cancelledAmastyAmount);
				BigDecimal invoiceBaseAmastStoreCreditAMount = invoiceAmastStoreCreditAMount
						.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
				salesInvoice.setAmstorecreditAmount(invoiceAmastStoreCreditAMount);
				salesInvoice.setAmstorecreditBaseAmount(invoiceBaseAmastStoreCreditAMount);

			} else {

				salesInvoice.setAmstorecreditAmount(BigDecimal.ZERO);
				salesInvoice.setAmstorecreditBaseAmount(BigDecimal.ZERO);
			}

			salesInvoice.setSubtotal(splitSalesOrder.getSubtotal());
			salesInvoice.setBaseSubtotal(splitSalesOrder.getBaseSubtotal());
			salesInvoice.setTaxAmount(splitSalesOrder.getTaxAmount());
			salesInvoice.setBaseTaxAmount(splitSalesOrder.getTaxAmount());
			salesInvoice.setSubtotalInclTax(splitSalesOrder.getSubtotalInclTax());
			salesInvoice.setBaseSubtotalInclTax(splitSalesOrder.getBaseSubtotalInclTax());

			salesInvoice.setGrandTotal(splitSalesOrder.getGrandTotal());
			salesInvoice.setBaseGrandTotal(splitSalesOrder.getBaseGrandTotal());
			salesInvoice.setDiscountAmount(splitSalesOrder.getDiscountAmount());
			salesInvoice.setBaseDiscountAmount(splitSalesOrder.getBaseDiscountAmount());
		}
		salesInvoice.setShippingAmount(splitSalesOrder.getShippingAmount());
		salesInvoice.setBaseShippingAmount(splitSalesOrder.getBaseShippingAmount());
		salesInvoice.setShippingTaxAmount(new BigDecimal(0));

		salesInvoice.setShippingInclTax(splitSalesOrder.getShippingInclTax());
		salesInvoice.setBaseShippingInclTax(splitSalesOrder.getBaseShippingInclTax());
		salesInvoice.setCashOnDeliveryFee(splitSalesOrder.getCashOnDeliveryFee());
		salesInvoice.setBaseCashOnDeliveryFee(splitSalesOrder.getBaseCashOnDeliveryFee());

		salesInvoice.setBaseShippingTaxAmount(new BigDecimal(0));

		// EAS coins update in invoice
		if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getEasCoins()) {
			salesInvoice.setEasCoins(splitSalesOrder.getSplitSubSalesOrder().getEasCoins());
			salesInvoice.setEasValueInCurrency(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency());
			salesInvoice.setEasValueInBaseCurrency(splitSalesOrder.getSplitSubSalesOrder().getEasValueInBaseCurrency());
		}
		// EAS coins update in invoice

		return salesInvoice;
	}

	/**
	 * @param splitSalesOrder
	 * @param salesInvoice
	 */
	private void createInvoiceItems(SalesInvoice salesInvoice, SplitSalesOrder splitSalesOrder) {

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



		for (SplitSalesOrderItem orderItem : splitSalesOrder.getSplitSalesOrderItems()) {
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
				BigDecimal baseTaxAmount = taxAmount.multiply(splitSalesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal indivisualRowTotal = orderItem.getRowTotal()
						.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal rowTotal = indivisualRowTotal.multiply(orderedQty).setScale(4, RoundingMode.HALF_UP);
				BigDecimal baseRowTotal = rowTotal.multiply(splitSalesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal priceIncludingTax = orderItem.getPriceInclTax();
				BigDecimal basepriceIncludingTax = priceIncludingTax.multiply(splitSalesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal indivisualRowTotalIncludeTAx = orderItem.getRowTotalInclTax()
						.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);

				BigDecimal rowRotalIncludingTax = indivisualRowTotalIncludeTAx.multiply(orderedQty).setScale(4,
						RoundingMode.HALF_UP);
				BigDecimal baseRowpriceIncludingTax = rowRotalIncludingTax.multiply(splitSalesOrder.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
				subTotal = rowRotalIncludingTax;
				baseSubTotal = baseRowpriceIncludingTax;
				if (null != orderItem.getDiscountAmount()) {

					BigDecimal indivisualDiscount = orderItem.getDiscountAmount()
							.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					discount = indivisualDiscount.multiply(orderedQty).setScale(4, RoundingMode.HALF_UP);

					baseDiscount = discount.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
					rowTotal = rowTotal.subtract(discount);
					baseRowTotal = baseRowTotal.subtract(baseDiscount);
					rowRotalIncludingTax = rowRotalIncludingTax.subtract(discount);
					baseRowpriceIncludingTax = baseRowpriceIncludingTax.subtract(baseDiscount);

				}
				if (null != orderItem.getDiscountTaxCompensationAmount()) {

					taxcompAmount = orderItem.getDiscountTaxCompensationAmount()
							.divide(orderedQty, 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
					baseTaxCompAmount = taxcompAmount.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4,
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

				salesInvoiceItem.setOrderItemId(orderItem.getSalesOrderItem().getItemId());
				salesInvoiceItem.setSplitOrderItemId(orderItem.getItemId());
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
					SalesOrderItem salesOrderItem = orderItem.getSalesOrderItem();
					for (SalesOrderItemTax salesOrderItemTax : salesOrderItem.getSalesOrderItemTax()) {
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
		if (null != splitSalesOrder.getShippingAmount()) {
			grandTotal = grandTotal.add(splitSalesOrder.getShippingAmount());
		}
		if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getDonationAmount()) {

			grandTotal = grandTotal.add(splitSalesOrder.getSplitSubSalesOrder().getDonationAmount());

		}
		if (null != splitSalesOrder.getCashOnDeliveryFee()) {

			grandTotal = grandTotal.add(splitSalesOrder.getCashOnDeliveryFee());

		}
		if (null != splitSalesOrder.getImportFee()) {

			grandTotal = grandTotal.add(splitSalesOrder.getImportFee());

		}
		if (null != splitSalesOrder.getAmstorecreditAmount() && null != salesInvoice.getAmstorecreditAmount()) {

			grandTotal = grandTotal.subtract(salesInvoice.getAmstorecreditAmount());
		}

		if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getEasCoins()) {

			grandTotal = grandTotal.subtract(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency());
		}

		salesInvoice.setGrandTotal(grandTotal);
		salesInvoice.setBaseGrandTotal(
				grandTotal.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));

		BigDecimal totalInvoiced = new BigDecimal(0);
		if (null != salesInvoice.getGrandTotal()) {

			totalInvoiced = salesInvoice.getGrandTotal();

		}
		splitSalesOrder.setTotalInvoiced(totalInvoiced);
		splitSalesOrder.setBaseTotalInvoiced(
				totalInvoiced.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));

		splitSalesOrder.setAmstorecreditInvoicedAmount(salesInvoice.getAmstorecreditAmount());
		splitSalesOrder.setAmstorecreditInvoicedBaseAmount(salesInvoice.getAmstorecreditBaseAmount());
		splitSalesOrder.setDiscountInvoiced(salesInvoice.getDiscountAmount());
		splitSalesOrder.setBaseDiscountInvoiced(salesInvoice.getBaseDiscountAmount());
		splitSalesOrder.setBaseDiscountInvoiced(salesInvoice.getBaseDiscountAmount());
		splitSalesOrder.setDiscountTaxCompensationInvoiced(salesInvoice.getDiscountTaxCompensationAmount());
		splitSalesOrder.setBaseDiscountTaxCompensationInvoiced(salesInvoice.getBaseDiscountTaxCompensationAmount());
		splitSalesOrder.setTaxInvoiced(salesInvoice.getTaxAmount());
		splitSalesOrder.setSubtotalInvoiced(salesInvoice.getSubtotal());
		splitSalesOrder.setBaseSubtotalInvoiced(salesInvoice.getBaseSubtotal());
		splitSalesOrder.setShippingInvoiced(salesInvoice.getShippingAmount());

	}

}
package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

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
import java.sql.Timestamp;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.CancelDetails;
import org.styli.services.order.pojo.RefundAmountObject;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.request.RequestBody;
import org.styli.services.order.pojo.zatca.InvoiceLine;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.pojo.zatca.ZatcaInvoice;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSubSalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SplitSubSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.AutoRefundService;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.PaymentUtility;

import com.fasterxml.jackson.core.JsonProcessingException;

@Component
public class PrepaidRefundHelper {

	private static final Log LOGGER = LogFactory.getLog(PrepaidRefundHelper.class);

	@Autowired
	private PaymentRefundHelper paymentRefundHelper;

	@Autowired
	RtoZatcaHelper rtoZatcaHelper;

	@Autowired
	private PaymentUtility paymentUtility;

	@Autowired
	private RefundHelper refundHelper;

	@Autowired
	private CommonServiceImpl commonService;

	@Autowired
	private SalesCreditmemoRepository creditmemoRepository;

	@Autowired
	@Lazy
	private AutoRefundService autoRefundService;

	@Autowired
	private OrderHelper orderHelper;
	
	@Autowired
	@Lazy
	private EASServiceImpl eASServiceImpl;
	
	@Autowired
	@Lazy
	ZatcaServiceImpl zatcaServiceImpl;

	@Autowired
	SubSalesOrderItemRepository subSalesOrderItemRepository;

	@Autowired
	SplitSubSalesOrderItemRepository splitSubSalesOrderItemRepository;

	@Autowired
	SalesOrderItemRepository salesOrderItemRepository;

	@Autowired
	MulinHelper mulinHelper;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	SplitSubSalesOrderRepository splitSubSalesOrderRepository;
	
	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;
	
	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	/**
	 * Initiate the refund process for RTO orders
	 * 
	 * @param salesOrder
	 * @return
	 */
	public void prepaidRefundCall(SalesOrder salesOrder) {
		String incrementId = salesOrder.getIncrementId();
		Integer storeId = salesOrder.getStoreId();
		Integer entityId = salesOrder.getEntityId();

		LOGGER.info("Order to initiate refund : " + incrementId);
		String msgString = null;
		String fortId = null;
		RefundPaymentRespone response = new RefundPaymentRespone();
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
				.orElse(null);
		if (store == null) {
			response.setStatus(false);
			response.setStatusCode("400");
			response.setStatusMsg("Store Not Found !!");
			return;
		}

		List<SalesCreditmemo> creditMemos = creditmemoRepository.findByRmaNumber(entityId.toString());
		if (!creditMemos.isEmpty()) {
			LOGGER.info("Order already refunded " + incrementId);
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded already done !!");
			response.setSendSms(false);
			return;
		}

		SalesOrderPayment orderPayment = salesOrder.getSalesOrderPayment().stream().findFirst().orElse(null); 


		String paymentMethod = null;

		if (null != orderPayment) {
			paymentMethod = orderPayment.getMethod();
			fortId = orderPayment.getCcTransId();
		}
		BigDecimal totalAmountToRefund = BigDecimal.ZERO;
		BigDecimal totalAmountToRefundWithoutTax = BigDecimal.ZERO;

		BigDecimal totalDiscountAmountForOrder = BigDecimal.ZERO;
		BigDecimal totalTaxAmount = BigDecimal.ZERO;
		BigDecimal totalItemsOrdered = BigDecimal.ZERO;
		BigDecimal totalItemsShipped = BigDecimal.ZERO;
		BigDecimal totalSubTotalIncTax = BigDecimal.ZERO;
		BigDecimal totalSubTotalExclTax = BigDecimal.ZERO;
		BigDecimal totalTaxableAmount = BigDecimal.ZERO;
		List<SalesCreditmemoItem> salesCreditmemoItems = new ArrayList<>();
		BigDecimal taxFactor = new BigDecimal(1);
		if (store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
			taxFactor = taxFactor.add(store.getTaxPercentage().divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
		}
		if (salesOrder.getSalesOrderItem() != null) {

			for (SalesOrderItem parentItem : salesOrder.getSalesOrderItem()) {

				if (parentItem != null && parentItem.getProductType().equalsIgnoreCase("configurable") && parentItem.getOriginalPrice() != null) {
					SalesCreditmemoItem memoItem = new SalesCreditmemoItem();
					memoItem.setBaseCost(parentItem.getBaseCost());
					memoItem.setPrice(parentItem.getPrice());
					memoItem.setBasePrice(parentItem.getBasePrice());
					memoItem.setProductId(parentItem.getProductId());
					memoItem.setOrderItemId(parentItem.getItemId());
					memoItem.setSku(parentItem.getSku());
					memoItem.setName(parentItem.getName());
					memoItem.setBasePrice(parentItem.getBasePrice());
					memoItem.setWeeeTaxAppliedRowAmount(parentItem.getWeeeTaxAppliedRowAmount());
					memoItem.setWeeeTaxRowDisposition(parentItem.getWeeeTaxRowDisposition());
					totalItemsOrdered = totalItemsOrdered.add(parentItem.getQtyOrdered());
					BigDecimal itemQtyCancelled = parentItem.getQtyCanceled() != null ? parentItem.getQtyCanceled() : new BigDecimal(0);
					totalItemsShipped = totalItemsShipped.add(parentItem.getQtyOrdered().subtract(itemQtyCancelled));
					memoItem.setQty(parentItem.getQtyOrdered().subtract(itemQtyCancelled));

					totalSubTotalIncTax = totalSubTotalIncTax.add(parentItem.getOriginalPrice().multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP));

					BigDecimal itemSubTotal = parentItem.getOriginalPrice()
							.divide(taxFactor, 6, RoundingMode.HALF_UP)
							.multiply(parentItem.getQtyOrdered());
					totalSubTotalExclTax = totalSubTotalExclTax.add(parentItem.getOriginalPrice().divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP));
					BigDecimal itemDiscount1 = (parentItem.getOriginalPrice()
							.subtract(parentItem.getPriceInclTax()))
							.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered());

					BigDecimal discountAmount = BigDecimal.ZERO;

					if (parentItem.getParentOrderItem() != null) {
						BigDecimal subSalesOrderDiscountAmount = subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(salesOrder.getEntityId(), parentItem.getParentOrderItem().getItemId());
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

					BigDecimal totalDiscountAmount = itemDiscount1.add(itemDiscount2);
					BigDecimal itemDiscountAmount = totalDiscountAmount.multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP);
					memoItem.setDiscountAmount(itemDiscountAmount);
					memoItem.setBasePriceInclTax(parentItem.getBasePriceInclTax().divide(parentItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(2, RoundingMode.HALF_UP));
					memoItem.setPriceInclTax(parentItem.getPriceInclTax().divide(parentItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(2, RoundingMode.HALF_UP));
					memoItem.setBaseDiscountAmount(itemDiscountAmount.multiply(salesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					BigDecimal itemTaxablePrice = itemSubTotal.subtract(totalDiscountAmount).multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP);
					totalTaxableAmount = totalTaxableAmount.add(itemTaxablePrice);
					BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor);
					memoItem.setRowTotal(itemTaxablePrice);
					memoItem.setBaseRowTotal(itemTaxablePrice.multiply(salesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					memoItem.setRowTotalInclTax(itemFinalPrice);
					memoItem.setBaseRowTotalInclTax(itemFinalPrice.multiply(salesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					totalAmountToRefund = totalAmountToRefund.add(itemFinalPrice);
					totalAmountToRefundWithoutTax = totalAmountToRefundWithoutTax.add(itemTaxablePrice);
					totalDiscountAmountForOrder = totalDiscountAmountForOrder.add(itemDiscountAmount);
					BigDecimal taxAmount = itemFinalPrice.subtract(itemTaxablePrice);
					memoItem.setTaxAmount(taxAmount);
					memoItem.setBaseTaxAmount(taxAmount.multiply(salesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					totalTaxAmount = totalTaxAmount.add(taxAmount);
					salesCreditmemoItems.add(memoItem);
				}
			}
		}

		BigDecimal totalAmountToRefundOnline = totalAmountToRefund;
		BigDecimal totalAmountToRefundAsCredit = BigDecimal.ZERO;
		if (salesOrder.getAmstorecreditAmount() != null && salesOrder.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) > 0 && totalItemsOrdered.compareTo(BigDecimal.ZERO)>0) {
			totalAmountToRefundAsCredit = salesOrder.getAmstorecreditAmount().multiply(totalItemsShipped).divide(totalItemsOrdered, 2, RoundingMode.HALF_UP);
			totalAmountToRefundOnline = totalAmountToRefundOnline.subtract(totalAmountToRefundAsCredit);
		}
		if (salesOrder.getSubSalesOrder() != null) {
			if (salesOrder.getSubSalesOrder().getEasValueInCurrency() != null && salesOrder.getSubSalesOrder().getEasValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
				totalAmountToRefundOnline = totalAmountToRefundOnline.subtract(salesOrder.getSubSalesOrder().getEasValueInCurrency());
			}
			if (salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned() != null && salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && store != null && store.getIsShukranEnable() && salesOrder.getSubSalesOrder().getShukranLocked().equals(0)) {
				// Round shukran points to integer (remove decimals) before unlocking
				BigDecimal shukranPointsToUnlock = salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned();
				String roundedPoints = shukranPointsToUnlock.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
				LOGGER.info("prepaidRefundCall: Unlocking shukran points - Original: " + shukranPointsToUnlock + ", Rounded: " + roundedPoints);
				commonService.lockUnlockShukranData(salesOrder.getSubSalesOrder().getCustomerProfileId(), roundedPoints, salesOrder.getSubSalesOrder().getQuoteId(), false, salesOrder, store, "Refund Shukran Burned Points", "");
				SubSalesOrder subSalesOrder = salesOrder.getSubSalesOrder();
				subSalesOrder.setShukranLocked(1);
				subSalesOrderRepository.saveAndFlush(subSalesOrder);
				totalAmountToRefundOnline = totalAmountToRefundOnline.subtract(salesOrder.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
			}
		}

		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafka(salesOrder, 0.0);
		}


		if (totalAmountToRefundOnline.compareTo(BigDecimal.ZERO) > 0 && paymentMethod != null) {
			if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)
					|| paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE)) {
				paymentUtility.initiateRefund(salesOrder, totalAmountToRefundOnline.toString(), paymentMethod);
			} else {
				paymentRefundHelper.payfortRefundcall(salesOrder, totalAmountToRefundOnline, fortId,
						paymentMethod);
			}

			if (totalAmountToRefundAsCredit.compareTo(BigDecimal.ZERO) > 0) {
				refundHelper.releaseStoreCredit(salesOrder, totalAmountToRefundAsCredit.multiply(salesOrder.getStoreToBaseRate()));
			}
			triggerSMS(salesOrder, null, totalAmountToRefund.toString());
			autoRefundService.updateRtoAutoRefund(salesOrder, null, PaymentConstants.REFUND_STATUS_REFUNDED);
			rtoZatcaHelper.generateRtoCreditMemo(salesOrder, null, store, salesCreditmemoItems, totalAmountToRefundAsCredit, totalAmountToRefundOnline, totalSubTotalIncTax, totalSubTotalExclTax, totalTaxAmount, totalDiscountAmountForOrder, totalAmountToRefund, taxFactor, totalTaxableAmount);
			response.setStatusMsg(StringUtils.isNotBlank(msgString) ? msgString : "Refunded Successfully!");
		}
	}

	public void prepaidRefundCallForSplitOrder(SplitSalesOrder splitSalesOrder) {
		LOGGER.info("Order to initiate refund : " + splitSalesOrder.getIncrementId());
		String msgString = null;
		String fortId = null;
		RefundPaymentRespone response = new RefundPaymentRespone();
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(splitSalesOrder.getStoreId())).findAny()
				.orElse(null);
		if (store == null) {
			response.setStatus(false);
			response.setStatusCode("400");
			response.setStatusMsg("Store Not Found !!");
			return;
		}

		List<SalesCreditmemo> creditMemos = creditmemoRepository.findByRmaNumber(splitSalesOrder.getEntityId().toString());
		if (!creditMemos.isEmpty()) {
			LOGGER.info("Order already refunded " + splitSalesOrder.getIncrementId());
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded already done !!");
			response.setSendSms(false);
			return;
		}

		SalesOrderPayment orderPayment = null;
		if (null!=splitSalesOrder.getSalesOrder()  && CollectionUtils.isNotEmpty(splitSalesOrder.getSalesOrder().getSalesOrderPayment())) {
			orderPayment = splitSalesOrder.getSalesOrder().getSalesOrderPayment().stream().findFirst().orElse(null);
		}

		String paymentMethod = null;

		if (null != orderPayment) {
			paymentMethod = orderPayment.getMethod();
			fortId = orderPayment.getCcTransId();
		}
		BigDecimal totalAmountToRefund = BigDecimal.ZERO;
		BigDecimal totalAmountToRefundWithoutTax = BigDecimal.ZERO;

		BigDecimal totalDiscountAmountForOrder = BigDecimal.ZERO;
		BigDecimal totalTaxAmount = BigDecimal.ZERO;
		BigDecimal totalItemsOrdered = BigDecimal.ZERO;
		BigDecimal totalItemsShipped = BigDecimal.ZERO;
		BigDecimal totalSubTotalIncTax = BigDecimal.ZERO;
		BigDecimal totalSubTotalExclTax = BigDecimal.ZERO;
		BigDecimal totalTaxableAmount = BigDecimal.ZERO;
		List<SalesCreditmemoItem> salesCreditmemoItems = new ArrayList<>();
		BigDecimal taxFactor = new BigDecimal(1);
		if (store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
			taxFactor = taxFactor.add(store.getTaxPercentage().divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
		}
		if (splitSalesOrder.getSplitSalesOrderItems() != null) {

			for (SplitSalesOrderItem parentItem : splitSalesOrder.getSplitSalesOrderItems()) {

				if (parentItem != null && parentItem.getProductType().equalsIgnoreCase("configurable") && parentItem.getOriginalPrice() != null) {
					SalesCreditmemoItem memoItem = new SalesCreditmemoItem();
					memoItem.setBaseCost(parentItem.getBaseCost());
					memoItem.setPrice(parentItem.getPrice());
					memoItem.setBasePrice(parentItem.getBasePrice());
					memoItem.setProductId(parentItem.getProductId());
					memoItem.setOrderItemId(parentItem.getItemId());
					memoItem.setSku(parentItem.getSku());
					memoItem.setName(parentItem.getName());
					memoItem.setBasePrice(parentItem.getBasePrice());
					memoItem.setWeeeTaxAppliedRowAmount(parentItem.getWeeeTaxAppliedRowAmount());
					memoItem.setWeeeTaxRowDisposition(parentItem.getWeeeTaxRowDisposition());
					totalItemsOrdered = totalItemsOrdered.add(parentItem.getQtyOrdered());
					BigDecimal itemQtyCancelled = parentItem.getQtyCanceled() != null ? parentItem.getQtyCanceled() : new BigDecimal(0);
					totalItemsShipped = totalItemsShipped.add(parentItem.getQtyOrdered().subtract(itemQtyCancelled));
					memoItem.setQty(parentItem.getQtyOrdered().subtract(itemQtyCancelled));

					totalSubTotalIncTax = totalSubTotalIncTax.add(parentItem.getOriginalPrice().multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP));

					BigDecimal itemSubTotal = parentItem.getOriginalPrice()
							.divide(taxFactor, 6, RoundingMode.HALF_UP)
							.multiply(parentItem.getQtyOrdered());
					totalSubTotalExclTax = totalSubTotalExclTax.add(parentItem.getOriginalPrice().divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP));
					BigDecimal itemDiscount1 = (parentItem.getOriginalPrice()
							.subtract(parentItem.getPriceInclTax()))
							.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(parentItem.getQtyOrdered());

					BigDecimal discountAmount = BigDecimal.ZERO;

					if (parentItem.getSplitSalesOrderItem() != null) {
						BigDecimal subSalesOrderDiscountAmount = splitSubSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(splitSalesOrder.getEntityId(), parentItem.getSplitSalesOrderItem().getItemId());
						if (subSalesOrderDiscountAmount != null) {
							discountAmount = subSalesOrderDiscountAmount;
						}
					} else if (parentItem.getSplitSubSalesOrderItem() != null) {

						for (SplitSubSalesOrderItem i : parentItem.getSplitSubSalesOrderItem()) {
							if (i.isGiftVoucher()) {
								discountAmount = i.getDiscount();
							}
						}

					}

					BigDecimal itemDiscount2 = (parentItem.getDiscountAmount()
							.subtract(discountAmount))
							.divide(taxFactor, 6, RoundingMode.HALF_UP);

					BigDecimal totalDiscountAmount = itemDiscount1.add(itemDiscount2);
					BigDecimal itemDiscountAmount = totalDiscountAmount.multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP);
					memoItem.setDiscountAmount(itemDiscountAmount);
					memoItem.setBasePriceInclTax(parentItem.getBasePriceInclTax().divide(parentItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(2, RoundingMode.HALF_UP));
					memoItem.setPriceInclTax(parentItem.getPriceInclTax().divide(parentItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(2, RoundingMode.HALF_UP));
					memoItem.setBaseDiscountAmount(itemDiscountAmount.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					BigDecimal itemTaxablePrice = itemSubTotal.subtract(totalDiscountAmount).multiply(parentItem.getQtyOrdered().subtract(itemQtyCancelled)).divide(parentItem.getQtyOrdered(), 2, RoundingMode.HALF_UP);
					totalTaxableAmount = totalTaxableAmount.add(itemTaxablePrice);
					BigDecimal itemFinalPrice = itemTaxablePrice.multiply(taxFactor);
					memoItem.setRowTotal(itemTaxablePrice);
					memoItem.setBaseRowTotal(itemTaxablePrice.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					memoItem.setRowTotalInclTax(itemFinalPrice);
					memoItem.setBaseRowTotalInclTax(itemFinalPrice.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					totalAmountToRefund = totalAmountToRefund.add(itemFinalPrice);
					totalAmountToRefundWithoutTax = totalAmountToRefundWithoutTax.add(itemTaxablePrice);
					totalDiscountAmountForOrder = totalDiscountAmountForOrder.add(itemDiscountAmount);
					BigDecimal taxAmount = itemFinalPrice.subtract(itemTaxablePrice);
					memoItem.setTaxAmount(taxAmount);
					memoItem.setBaseTaxAmount(taxAmount.multiply(splitSalesOrder.getStoreToBaseRate()).setScale(2, RoundingMode.HALF_UP));
					totalTaxAmount = totalTaxAmount.add(taxAmount);
					salesCreditmemoItems.add(memoItem);
				}
			}
		}

		BigDecimal totalAmountToRefundOnline = totalAmountToRefund;
		BigDecimal totalAmountToRefundAsCredit = BigDecimal.ZERO;
		if (splitSalesOrder.getAmstorecreditAmount() != null && splitSalesOrder.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) > 0 && totalItemsOrdered.compareTo(BigDecimal.ZERO)>0) {
			totalAmountToRefundAsCredit = splitSalesOrder.getAmstorecreditAmount().multiply(totalItemsShipped).divide(totalItemsOrdered, 2, RoundingMode.HALF_UP);
			totalAmountToRefundOnline = totalAmountToRefundOnline.subtract(totalAmountToRefundAsCredit);
		}
		if (splitSalesOrder.getSplitSubSalesOrder() != null) {
			if (splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency() != null && splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
				totalAmountToRefundOnline = totalAmountToRefundOnline.subtract(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency());
			}
			if (splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && store != null && store.getIsShukranEnable() && splitSalesOrder.getSplitSubSalesOrder().getShukranLocked().equals(0)) {
				// Step 1: Calculate total points from all shipments in the parent order
				BigDecimal rtoOrderPoints = splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned();
				BigDecimal totalOrderPoints = BigDecimal.ZERO;
				if (splitSalesOrder.getSalesOrder() != null) {
					List<SplitSalesOrder> allSplitOrders = splitSalesOrderRepository.findBySalesOrder(splitSalesOrder.getSalesOrder());
					if (allSplitOrders != null && !allSplitOrders.isEmpty()) {
						for (SplitSalesOrder splitOrder : allSplitOrders) {
							if (splitOrder.getSplitSubSalesOrder() != null 
									&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null
									&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
								totalOrderPoints = totalOrderPoints.add(splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
							}
						}
						LOGGER.info("prepaidRefundCallForSplitOrder: Total points from all shipments in parent order: " + totalOrderPoints + ", RTO order points: " + rtoOrderPoints);
					} else {
						totalOrderPoints = rtoOrderPoints; // Fallback if no split orders found
					}
				} else {
					totalOrderPoints = rtoOrderPoints; // Fallback if no parent order
				}
				
				// Step 2: Unlock ALL points from the parent order (all shipments)
				if (totalOrderPoints.compareTo(BigDecimal.ZERO) > 0) {
					String roundedPointsToUnlock = totalOrderPoints.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
					LOGGER.info("prepaidRefundCallForSplitOrder: Step 2 - Unlocking ALL shukran points from parent order (all shipments) - Points: " + roundedPointsToUnlock);
					commonService.lockUnlockShukranDataForSplit(splitSalesOrder.getSplitSubSalesOrder().getCustomerProfileId(), roundedPointsToUnlock, splitSalesOrder.getSplitSubSalesOrder().getQuoteId(), false, splitSalesOrder, store, "Refund Shukran Burned Points - Unlock All Parent Order Points", "");
				}
				
				// Step 3: Calculate delivered order points = total - RTO order points
				BigDecimal deliveredOrderPoints = totalOrderPoints.subtract(rtoOrderPoints);
				
				// Step 4: Lock back points for delivered order (not RTO order)
				if (deliveredOrderPoints.compareTo(BigDecimal.ZERO) > 0) {
					String roundedPointsToLock = deliveredOrderPoints.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
					LOGGER.info("prepaidRefundCallForSplitOrder: Step 3 - Locking back shukran points for delivered order (Total: " + totalOrderPoints + " - RTO: " + rtoOrderPoints + " = " + deliveredOrderPoints + ") - Points: " + roundedPointsToLock);
					commonService.lockUnlockShukranDataForSplit(splitSalesOrder.getSplitSubSalesOrder().getCustomerProfileId(), roundedPointsToLock, splitSalesOrder.getSplitSubSalesOrder().getQuoteId(), true, splitSalesOrder, store, "Refund Shukran Burned Points - Lock Back Delivered Order", "Prepaid Refund Api");
				} else {
					LOGGER.info("prepaidRefundCallForSplitOrder: No points to lock back for delivered order (all points are from RTO order)");
				}
				
				SplitSubSalesOrder subSalesOrder = splitSalesOrder.getSplitSubSalesOrder();
				subSalesOrder.setShukranLocked(1); // Set to 1 (unlocked) since RTO points are unlocked
				splitSubSalesOrderRepository.saveAndFlush(subSalesOrder);
				totalAmountToRefundOnline = totalAmountToRefundOnline.subtract(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency());
			}
		}

		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafkaForSplitOrder(splitSalesOrder, 0.0);
		}


		if (totalAmountToRefundOnline.compareTo(BigDecimal.ZERO) > 0 && paymentMethod != null) {
			if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)
					|| paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE)) {
				paymentUtility.initiateRefund(splitSalesOrder.getSalesOrder(), totalAmountToRefundOnline.toString(), paymentMethod);
			} else {
				paymentRefundHelper.payfortRefundcall(splitSalesOrder.getSalesOrder(), totalAmountToRefundOnline, fortId,
						paymentMethod);
			}

			if (totalAmountToRefundAsCredit.compareTo(BigDecimal.ZERO) > 0) {
				refundHelper.releaseStoreCreditForSplitOrder(splitSalesOrder, totalAmountToRefundAsCredit.multiply(splitSalesOrder.getStoreToBaseRate()));
			}
			triggerSMS(null, splitSalesOrder, totalAmountToRefund.toString());
			autoRefundService.updateRtoAutoRefund(null, splitSalesOrder, PaymentConstants.REFUND_STATUS_REFUNDED);
			rtoZatcaHelper.generateRtoCreditMemo(null, splitSalesOrder, store, salesCreditmemoItems, totalAmountToRefundAsCredit, totalAmountToRefundOnline, totalSubTotalIncTax, totalSubTotalExclTax, totalTaxAmount, totalDiscountAmountForOrder, totalAmountToRefundAsCredit, taxFactor, totalTaxableAmount);
            response.setStatusMsg("Refunded Successfully!");
		}
	}

	/**
	 * Generate Credit memo for refunded Orders
	 * 
	 * @param salesOrder
	 * @param totalRefundOnlineAmount
	 * @param details
	 * @param salesItemList
	 * @param response
	 * @param paymentMethod
	 * @param mapSkuList
	 */
	private void generateCreditMemo(SalesOrder salesOrder, BigDecimal totalRefundOnlineAmount, CancelDetails details,
			List<SalesOrderItem> salesItemList, RefundPaymentRespone response, String paymentMethod,
			Map<String, BigDecimal> mapSkuList, BigDecimal totalShukranBurnedValueInCurrency, BigDecimal totalShukranBurnedValueInBaseCurrency, BigDecimal totalShukranCoinsBurned) {
		LOGGER.info("Inside generateCreditMemo: " + salesOrder.getIncrementId());
		String memoIncrementId = refundHelper.getIncrementId(salesOrder.getStoreId());
		SalesCreditmemo memo = refundHelper.createCreditMemo(salesOrder, memoIncrementId,
				totalRefundOnlineAmount, details.getAmasyStoreCredit(), paymentMethod, mapSkuList, true, null, response, totalShukranBurnedValueInCurrency, totalShukranBurnedValueInBaseCurrency, totalShukranCoinsBurned);
		refundHelper.createCancelCreditmemoItems(salesOrder, memo, mapSkuList, salesItemList);
		if (StringUtils.isNotBlank(response.getStatusMsg())) {
			refundHelper.createCreditmemoFailComment(memo, details.getAmasyStoreCredit(), response.getStatusMsg());

		} else {
			refundHelper.createCreditmemoComment(memo, details.getAmasyStoreCredit());
		}
		// ZATCA start creditMemo
		if(Constants.getZatcaFlag(salesOrder.getStoreId())) {
			LOGGER.info("Inside zatca generateCreditMemo call : " + salesOrder.getIncrementId());
			SalesInvoice invoice = salesOrder.getSalesInvoices().stream().findFirst().orElse(null);
			zatcaServiceImpl.sendZatcaCreditMemo(memo, invoice, salesOrder, null, false, null, null, null, null);
		}
		// ZATCA Ends creditMemo

	}

	private void addStoreCredit(SalesOrder salesOrder, RefundAmountObject refundAmountDetails) {
		if (null != refundAmountDetails.getBaseAmastyStoreCreditAmount()
				&& refundAmountDetails.getBaseAmastyStoreCreditAmount().compareTo(BigDecimal.ZERO) != 0) {
			paymentRefundHelper.addStoreCredit(salesOrder, refundAmountDetails.getRefundStorecreditAmount(), refundAmountDetails.isGiftVoucher());
		}
	}

	private void triggerSMS(SalesOrder salesOrder, SplitSalesOrder splitSalesOrder, String totalRefundOnlineAmount) {
		String incrementId = salesOrder != null ? salesOrder.getIncrementId() : splitSalesOrder.getIncrementId();
		orderHelper.sendRefundSmsAndEMail(incrementId, "order",
				OrderConstants.SMS_TEMPLATE_RTO_REFUND_INITIATE, totalRefundOnlineAmount);
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

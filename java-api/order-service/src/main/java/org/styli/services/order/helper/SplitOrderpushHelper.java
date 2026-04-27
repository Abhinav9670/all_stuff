package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.eas.EASPartialCancelRefundResponse;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.EmailService;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.PaymentUtility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SplitOrderpushHelper {


	private static final Log LOGGER = LogFactory.getLog(SplitOrderpushHelper.class);
	private static final String SIMPLE_PRODUCT_TYPE = "simple";

	private static final ObjectMapper mapper = new ObjectMapper();


	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	private PaymentUtility paymentUtility;

	@Autowired
	SplitPaymentRefundHelper splitPaymentRefundHelper;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;


	@Autowired
	SplitSubSalesOrderItemRepository splitSubSalesOrderItemRepository;

	@Autowired
	SplitSubSalesOrderRepository splitSubSalesOrderRepository;
	
	@Autowired
	SplitSalesOrderItemRepository splitSalesOrderItemRepository;

	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	OrderHelper orderHelper;


	@Autowired
	StaticComponents staticComponents;

	@Autowired
	RefundHelper refundHelper;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	TabbyHelper tabbyHelper;

	@Autowired
	TamaraHelper tamaraHelper;

	@Autowired
	@Lazy
	EASServiceImpl eASServiceImpl;

	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Autowired
	EmailService emailService;

	@Value("${env}")
	private String env;


	/**
	 * @param splitSalesOrder
	 * @return order cancel response
	 */
	public OmsUnfulfilmentResponse cancelUnfulfilledSplitOrder(SplitSalesOrder splitSalesOrder, OrderunfulfilmentRequest request
			, Map<String, String> httpRequestHeadrs) {
		OmsUnfulfilmentResponse response = new OmsUnfulfilmentResponse();
		try {
			List<Stores> stores = Constants.getStoresList();
			Boolean isFullyCancellation = false;
			String orderStatus = null;
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(splitSalesOrder.getStoreId())).findAny()
					.orElse(null);

			String fortId = null;
			String paymentMethod = null;
			if (null!=splitSalesOrder.getSalesOrder()  && CollectionUtils.isNotEmpty(splitSalesOrder.getSalesOrder().getSalesOrderPayment())) {
				for (SalesOrderPayment payment : splitSalesOrder.getSalesOrder().getSalesOrderPayment()) {
					fortId = payment.getCcTransId();
					paymentMethod = payment.getMethod();
				}
			}

			boolean isValidate = validateRequest(splitSalesOrder, request);
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
			//Find import fee of order based on available items of order
			BigDecimal currentOrderValue = splitPaymentRefundHelper.findSplitCurrentOrderValue(splitSalesOrder);
			boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();
			LOGGER.info("In cancelUnfulfilOrder Non KSA Seller Cancellation Flag: " + nonKsaSellerCancellation);
			//If non KSA seller cancellation is enabled, allow partial cancellation for all countries except COD with Styli credit or Gift Voucher
			if (nonKsaSellerCancellation) {
				if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& null != splitSalesOrder.getAmstorecreditAmount()
						&& splitSalesOrder.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with styli credit.");
					return response;
				} else if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& null != splitSalesOrder.getGiftVoucherDiscount()
						&& splitSalesOrder.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with Gift Voucher.");
					return response;
				}
			}
			// If non KSA seller cancellation is disabled, apply old KSA rules
			if (!nonKsaSellerCancellation) {
				if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& !(splitSalesOrder.getStoreId().equals(1) || splitSalesOrder.getStoreId().equals(3) || splitSalesOrder.getStoreId().equals(51))) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled because of its payment method.");
					return response;
				} else if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& (splitSalesOrder.getStoreId().equals(1) || splitSalesOrder.getStoreId().equals(3) || splitSalesOrder.getStoreId().equals(51))
						&& null != splitSalesOrder.getAmstorecreditAmount()
						&& splitSalesOrder.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) != 0) {

					response.setHasError(true);
					response.setErrorMessage("This order is not available to be partially cancelled"
							+ " because of COD  with styli credit.");
					return response;
				} else if (PaymentCodeENUM.CASH_ON_DELIVERY.getValue().equalsIgnoreCase(paymentMethod)
						&& (splitSalesOrder.getStoreId().equals(1) || splitSalesOrder.getStoreId().equals(3) || splitSalesOrder.getStoreId().equals(51))
						&& null != splitSalesOrder.getGiftVoucherDiscount()
						&& splitSalesOrder.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {

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
			if (!nonKsaSellerCancellation && !(splitSalesOrder.getStoreId().equals(1) || splitSalesOrder.getStoreId().equals(3) || splitSalesOrder.getStoreId().equals(51))) {
				response.setHasError(true);
				response.setErrorMessage("This store order is order not availble for seller cancellation!");
				return response;
			}

			BigDecimal customerTotalPoints = null;
			if (splitSalesOrder != null && splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && splitSalesOrder.getSplitSubSalesOrder().getShukranLocked() != null && splitSalesOrder.getSplitSubSalesOrder().getShukranLocked().equals(0)) {
				customerTotalPoints = commonService.customerShukranBalance(splitSalesOrder.getSplitSubSalesOrder().getCustomerProfileId());
				if (customerTotalPoints == null || customerTotalPoints.compareTo(BigDecimal.ZERO) < 0) {
					response.setHasError(true);
					response.setErrorMessage("Customer Does Not Have Enough Shukran Points");
					return response;
				}
			}
			if (null != splitSalesOrder && CollectionUtils.isNotEmpty(request.getOrderItems())) {

				List<SplitSalesOrderItem> itemList = new ArrayList<>();
				BigDecimal totalAmountToRefund = new BigDecimal(0);

				BigDecimal beforeCancelledAmount = splitPaymentRefundHelper.getCanceledItemQty(splitSalesOrder);
				Map<String, BigDecimal> skumApList = new HashMap<>();
				Map<String, BigDecimal> actualskumApList = new HashMap<>();
				for (OrderPushItem canceledItem : request.getOrderItems()) {

					if (null != canceledItem.getCancelledQuantity() && null != canceledItem.getOrderItemCode()
							&& canceledItem.getCancelledQuantity().intValue() != 0) {

						calculateCancelledValue(splitSalesOrder, itemList, skumApList, canceledItem, actualskumApList);

					}

				}

				itemList.removeAll(Collections.singleton(null));

				if (CollectionUtils.isEmpty(itemList)) {

					response.setHasError(false);
					response.setErrorMessage("Already Cancelled.");

					return response;
				}


				BigDecimal sumOrderedQty = splitSalesOrder.getSplitSalesOrderItems().stream()
						.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.map(x -> x.getQtyOrdered())
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				BigDecimal sumOrderedCancelled = splitSalesOrder.getSplitSalesOrderItems().stream()
						.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.map(x -> x.getQtyCanceled())
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				Integer cancelledCount = 0;
				boolean isCurrentShipmentFullyCancelled = (sumOrderedQty.intValue() == sumOrderedCancelled.intValue());
				boolean isAllShipmentsCancelled = false;
				
				// Check if all shipments are cancelled (including current one)
				// This check is needed to determine if we should leave all points unlocked
				List<SplitSalesOrder> allSplitSalesOrders = splitSalesOrderRepository.findBySalesOrder(splitSalesOrder.getSalesOrder());
				if (CollectionUtils.isNotEmpty(allSplitSalesOrders)) {
					// Check if current shipment is fully cancelled
					boolean currentIsFullyCancelled = isCurrentShipmentFullyCancelled;
					// Check if all other shipments are cancelled
					boolean allOthersCancelled = allSplitSalesOrders.stream()
							.filter(e -> !e.getEntityId().equals(splitSalesOrder.getEntityId()))
							.allMatch(e -> OrderConstants.CLOSED_ORDER_STATUS.equalsIgnoreCase(e.getStatus()));
					// All shipments are cancelled only if current is fully cancelled AND all others are cancelled
					isAllShipmentsCancelled = currentIsFullyCancelled && allOthersCancelled;
					LOGGER.info("cancelUnfulfilledSplitOrder: Current shipment fully cancelled: " + currentIsFullyCancelled + 
							", All other shipments cancelled: " + allOthersCancelled + 
							", All shipments cancelled: " + isAllShipmentsCancelled);
				} else {
					// If no other shipments exist, then all shipments cancelled = current shipment fully cancelled
					isAllShipmentsCancelled = isCurrentShipmentFullyCancelled;
					LOGGER.info("cancelUnfulfilledSplitOrder: No other shipments found. All shipments cancelled = current shipment fully cancelled: " + isAllShipmentsCancelled);
				}
				
				if (isCurrentShipmentFullyCancelled) {
					cancelledCount = 2;
					orderStatus = OrderConstants.CLOSED_ORDER_STATUS;
					splitSalesOrder.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
					// If all shipments are cancelled, mark parent order as closed
					if (isAllShipmentsCancelled) {
						splitSalesOrder.getSalesOrder().setStatus(OrderConstants.CLOSED_ORDER_STATUS);
					}
					splitSalesOrder.setWmsPullStatus(7);
					isFullyCancellation = true;
					LOGGER.info("cancelUnfulfilledSplitOrder: Current shipment is fully cancelled. All shipments cancelled: " + isAllShipmentsCancelled);
				} else {
					orderStatus = splitSalesOrder.getStatus();
					cancelledCount = 1;
					splitSalesOrder.setWmsPullStatus(7);
					LOGGER.info("cancelUnfulfilledSplitOrder: Current shipment is partially cancelled. All shipments cancelled: " + isAllShipmentsCancelled);
				}

				splitSalesOrder.getSplitSubSalesOrder().setIsUnfulfilmentOrder(cancelledCount);


				totalAmountToRefund = BigDecimal.ZERO;
				DecimalFormat df = new DecimalFormat(".##");
				BigDecimal totalVoucherToRefund = BigDecimal.ZERO;

				BigDecimal totalShukranCoinsNeedsToBeLockedAgain = BigDecimal.ZERO;
				BigDecimal totalOrderBurnedPoints = BigDecimal.ZERO; // Total burned points from ALL shipments in the order
				
				// Calculate total burned points from ALL split orders (shipments) in the parent order
				if (splitSalesOrder != null && splitSalesOrder.getSalesOrder() != null) {
					List<SplitSalesOrder> allSplitOrders = splitSalesOrderRepository.findBySalesOrder(splitSalesOrder.getSalesOrder());
					if (CollectionUtils.isNotEmpty(allSplitOrders)) {
						for (SplitSalesOrder splitOrder : allSplitOrders) {
							if (splitOrder.getSplitSubSalesOrder() != null 
									&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null
									&& splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
								totalOrderBurnedPoints = totalOrderBurnedPoints.add(splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
								LOGGER.info("cancelUnfulfilledSplitOrder: Found burned points in shipment - splitOrderId: " + splitOrder.getEntityId() + 
										", incrementId: " + splitOrder.getIncrementId() + 
										", burnedPoints: " + splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
							}
						}
					}
					LOGGER.info("cancelUnfulfilledSplitOrder: Total burned points from ALL shipments in order: " + totalOrderBurnedPoints);
				}
				
				// Initialize with all burned points from the CURRENT split order (for calculating remaining points after cancellation)
				if (splitSalesOrder != null && splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal currentSplitOrderBurnedPoints = splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned();
					totalShukranCoinsNeedsToBeLockedAgain = currentSplitOrderBurnedPoints;
					LOGGER.info("cancelUnfulfilledSplitOrder: Initialized totalShukranCoinsNeedsToBeLockedAgain with current split order's burned points: " + currentSplitOrderBurnedPoints + " for splitOrderId: " + splitSalesOrder.getEntityId());
					
					// Unlock ALL points from the entire order (all shipments), not just current split order
					if (customerTotalPoints != null && customerTotalPoints.compareTo(BigDecimal.ZERO) >= 0 && totalOrderBurnedPoints.compareTo(BigDecimal.ZERO) > 0) {
						LOGGER.info("cancelUnfulfilledSplitOrder: Unlocking ALL burned points from entire order - Total: " + totalOrderBurnedPoints);
						commonService.lockUnlockShukranDataForSplit(splitSalesOrder.getSplitSubSalesOrder().getCustomerProfileId(), totalOrderBurnedPoints.toString(), splitSalesOrder.getSplitSubSalesOrder().getQuoteId(), false, splitSalesOrder, store, "Cancel Shukran Burned Points On Unfulfilled Order", "");
						SplitSubSalesOrder splitSubSalesOrder = splitSalesOrder.getSplitSubSalesOrder();
						splitSubSalesOrder.setShukranLocked(1);
						splitSubSalesOrderRepository.saveAndFlush(splitSubSalesOrder);
						LOGGER.info("cancelUnfulfilledSplitOrder: Successfully unlocked all " + totalOrderBurnedPoints + " points from entire order and set ShukranLocked=1");
					} else {
						LOGGER.info("cancelUnfulfilledSplitOrder: Customer balance check failed or customerTotalPoints is null or no burned points - Skipping unlock, but will still calculate points to lock back for non-cancelled items");
					}
				} else {
					LOGGER.info("cancelUnfulfilledSplitOrder: No burned points found in current split order - totalShukranCoinsNeedsToBeLockedAgain remains 0");
				}
				for (SplitSalesOrderItem item : itemList) {

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

						//**for match **//
						totalProductValue = originpriceIncludeTax.multiply(qtyOrdered).subtract(item.getDiscountAmount());
						BigDecimal difference = priceIncludeTax.multiply(qtyOrdered).subtract(totalProductValue).setScale(2, RoundingMode.HALF_UP);
						BigDecimal diffentConstantAmount = new BigDecimal("0.01");
						if (difference.compareTo(diffentConstantAmount) == 0) {

							priceIncludeTax = priceIncludeTax.subtract(diffentConstantAmount);

						}


						totalAmountToRefund = totalAmountToRefund.add(priceIncludeTax);
						SplitSubSalesOrderItem splitSubSalesOrderItem = null;
						if (null != item.getSplitSalesOrderItem()) {
							splitSubSalesOrderItem = item.getSplitSalesOrderItem().getSplitSubSalesOrderItem().stream()
									.filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
						}

						if (Objects.nonNull(splitSubSalesOrderItem)) {
							if (!splitSubSalesOrderItem.getDiscount().equals(BigDecimal.ZERO)) {
								BigDecimal indivisualVoucherAmount = splitSubSalesOrderItem.getDiscount()
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
						
						LOGGER.info("cancelUnfulfilledSplitOrder: Item Shukran calculation - SKU: " + item.getSku() + 
								", TotalBurned: " + itemTotalBurned + 
								", QtyOrdered: " + itemQtyOrdered + 
								", QtyCancelled: " + qtyCancelled + 
								", PointsToRefund: " + itemPointsToRefund + 
								", ValueToRefund: " + itemValueToRefund);
						
						BigDecimal beforeSubtract = totalShukranCoinsNeedsToBeLockedAgain;
						totalAmountToRefund = totalAmountToRefund.subtract(itemValueToRefund);
						totalShukranCoinsNeedsToBeLockedAgain = totalShukranCoinsNeedsToBeLockedAgain.subtract(itemPointsToRefund);
						
						LOGGER.info("cancelUnfulfilledSplitOrder: After item calculation - Before subtract: " + beforeSubtract + 
								", Points subtracted: " + itemPointsToRefund + 
								", Remaining to lock: " + totalShukranCoinsNeedsToBeLockedAgain);
					}

				}

				EASPartialCancelRefundResponse eASPartialCancelRefundResponse = new EASPartialCancelRefundResponse();
				if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
					// EAS coins subtract for partial cancellation
					eASPartialCancelRefundResponse = eASServiceImpl.easSplitPartialCancelRefund(splitSalesOrder, request.getOrderItems(), httpRequestHeadrs.get(Constants.deviceId));
				}


				response.setTotalCodCancelledAmount(df.format(totalAmountToRefund).toString());

				BigDecimal storeCreditAmount = splitSalesOrder.getAmstorecreditAmount();

				if (OrderConstants.checkPaymentMethod(paymentMethod)) {
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund, eASPartialCancelRefundResponse);
					//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
					BigDecimal calcultedcancelAmount = onlineForward(splitSalesOrder, store, fortId, paymentMethod, storeCreditAmount, totalAmountToRefund,
							beforeCancelledAmount, skumApList, isFullyCancellation, itemList, actualskumApList, totalVoucherToRefund, currentOrderValue);
					LOGGER.info(paymentMethod + " and cancel amount:" + calcultedcancelAmount);
					if (nonKsaSellerCancellation) response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (OrderConstants.checkTamaraPaymentMethod(paymentMethod)) {

					CancelDetails details = new CancelDetails();
					details.setCurrentOrderValue(currentOrderValue);


					//** EAS Coin amount to be subtracted **//*

					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund, eASPartialCancelRefundResponse);

					BigDecimal calcultedcancelAmount = splitPaymentRefundHelper.cancelPercentageCalculation(splitSalesOrder, totalAmountToRefund,
							storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);

					LOGGER.info("tamrara cancel amount:" + calcultedcancelAmount);

					tamaraHelper.cancelPayment(splitSalesOrder.getSalesOrder(), calcultedcancelAmount.toString());

					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addSplitStoreCredit(splitSalesOrder, storeCreditAmount, details);

					}
					setOrderGrid(splitSalesOrder, isFullyCancellation, orderStatus);
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (OrderConstants.checkTabbyPaymentMethod(paymentMethod) && isFullyCancellation) {
					totalVoucherToRefund = BigDecimal.ZERO;// Voucher not refunded for Full cancellation
					LOGGER.info("tabyy fullycancellation");
					tabbyHelper.closePayment(splitSalesOrder.getSplitSubSalesOrder().getPaymentId());
					CancelDetails details = new CancelDetails();
					// EAS Coin amount to be subtracted
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund, eASPartialCancelRefundResponse);
					BigDecimal calcultedcancelAmount =	splitPaymentRefundHelper.cancelPercentageCalculation(splitSalesOrder, totalAmountToRefund,
							storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);


					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addSplitStoreCredit(splitSalesOrder, storeCreditAmount, details);

					}

					setOrderGrid(splitSalesOrder, isFullyCancellation, orderStatus);
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (OrderConstants.checkTabbyPaymentMethod(paymentMethod) &&
						null != storeCreditAmount && !(storeCreditAmount.compareTo(BigDecimal.ZERO) == 0)
						&& !isFullyCancellation) {

					LOGGER.info("tabyy partially fullycancellation");
					CancelDetails details = new CancelDetails();
					// EAS Coin amount to be subtracted
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund, eASPartialCancelRefundResponse);
					BigDecimal calcultedcancelAmount =	splitPaymentRefundHelper.cancelPercentageCalculation(splitSalesOrder, totalAmountToRefund, storeCreditAmount,
							details, isFullyCancellation, paymentMethod, totalVoucherToRefund);


					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addSplitStoreCredit(splitSalesOrder, storeCreditAmount, details);
					}
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
					CancelDetails details = new CancelDetails();
					totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund,
							eASPartialCancelRefundResponse);
					BigDecimal calcultedcancelAmount = splitPaymentRefundHelper.cancelPercentageCalculation(splitSalesOrder, totalAmountToRefund,
							storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);

					LOGGER.info("cashfree cancel amount:" + calcultedcancelAmount);

					paymentUtility.initiateSplitRefund(splitSalesOrder, calcultedcancelAmount.toString(), paymentMethod);
					if (null != storeCreditAmount && details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) != 0) {
						addSplitStoreCredit(splitSalesOrder, storeCreditAmount, details);
					}
					if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
				} else {
					if (null != storeCreditAmount && !(storeCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
						totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund,
								eASPartialCancelRefundResponse);
						BigDecimal calculatedStoreCredit = splitPaymentRefundHelper.getCancelledStoreCreditWithSplitCurrentOrderValue(splitSalesOrder,
								store, totalAmountToRefund, beforeCancelledAmount, isFullyCancellation, paymentMethod,currentOrderValue);
						if (null != calculatedStoreCredit && !(calculatedStoreCredit.compareTo(BigDecimal.ZERO) == 0)) {

							response.setTotalCodCancelledAmount(calculatedStoreCredit.toString());

							SalesOrderGrid grid = refundHelper.cancelSplitOrderGrid(splitSalesOrder, true, paymentMethod);

							grid.setStatus(orderStatus);
							BigDecimal calculatedBaseStoreCredit = calculatedStoreCredit.multiply(splitSalesOrder.getStoreToBaseRate())
									.setScale(4, RoundingMode.HALF_UP);
							Boolean isVoucherApplied = false;
							if (Boolean.TRUE.equals(!isFullyCancellation) && !totalVoucherToRefund.equals(BigDecimal.ZERO)) {
								calculatedBaseStoreCredit = calculatedBaseStoreCredit.add(totalVoucherToRefund);
								isVoucherApplied = true;
							}
							//refundHelper.releaseStoreCredit(splitSalesOrder, calculatedBaseStoreCredit);
							splitPaymentRefundHelper.addSplitOrderStoreCredit(splitSalesOrder, calculatedBaseStoreCredit, isVoucherApplied);
							salesOrderGridRepository.saveAndFlush(grid);

						} else {

							if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(totalAmountToRefund));
							SalesOrderGrid grid = refundHelper.cancelSplitOrderGrid(splitSalesOrder, true, paymentMethod);
							grid.setStatus(orderStatus);
							salesOrderGridRepository.saveAndFlush(grid);
						}


					} else {
						CancelDetails details = new CancelDetails();
						details.setCurrentOrderValue(currentOrderValue);
						totalAmountToRefund = coinSubtractOncancel(isFullyCancellation, splitSalesOrder, totalAmountToRefund, eASPartialCancelRefundResponse);
						BigDecimal calcultedcancelAmount = splitPaymentRefundHelper.cancelPercentageCalculation(splitSalesOrder, totalAmountToRefund,
								storeCreditAmount, details, isFullyCancellation, paymentMethod, totalVoucherToRefund);
						if(nonKsaSellerCancellation)response.setTotalCodCancelledAmount(df.format(calcultedcancelAmount));
						setOrderGrid(splitSalesOrder, isFullyCancellation, orderStatus);

					}

					if (splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
						splitSalesOrder.setWmsPullStatus(2);
						orderHelper.updateSplitStatusHistory(splitSalesOrder, false, false, false, true, false);
					}


					splitSalesOrderRepository.saveAndFlush(splitSalesOrder);
				}
				String skuListString;
				try {
					skuListString = mapper.writeValueAsString(skumApList);
					skuListString = OrderConstants.SELLER_CANCELLED_MSG + skuListString;
					updateOrderStatusHistory(splitSalesOrder, skuListString, "order", splitSalesOrder.getStatus());

				} catch (JsonProcessingException e1) {

					LOGGER.error("error during sku string parse");
				}
				LOGGER.info("cancelUnfulfilledSplitOrder: After processing all items - isFullyCancellation: " + isFullyCancellation + 
						", isAllShipmentsCancelled: " + isAllShipmentsCancelled +
						", totalShukranCoinsNeedsToBeLockedAgain: " + totalShukranCoinsNeedsToBeLockedAgain);
				
				// Calculate non-cancelled items' points from CURRENT shipment by checking item-level cancellation
				// Always calculate this, even if current shipment is fully cancelled (will be 0 in that case)
				// This is needed to combine with other shipments for the final lock-back
				BigDecimal currentShipmentNonCancelledPoints = BigDecimal.ZERO;
				if (store != null) {
					// Fetch all items for current shipment
					List<SplitSalesOrderItem> currentShipmentItems = splitSalesOrderItemRepository.findBySplitSalesOrderEntityId(splitSalesOrder.getEntityId());
					
					if (CollectionUtils.isNotEmpty(currentShipmentItems)) {
						for (SplitSalesOrderItem item : currentShipmentItems) {
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
								
								currentShipmentNonCancelledPoints = currentShipmentNonCancelledPoints.add(itemPointsForNonCancelled);
								
								LOGGER.info("cancelUnfulfilledSplitOrder: Found non-cancelled item in CURRENT shipment - SKU: " + item.getSku() + 
										", ItemId: " + item.getItemId() +
										", QtyOrdered: " + qtyOrdered + 
										", QtyCancelled: " + qtyCancelled + 
										", QtyNotCancelled: " + qtyNotCancelled + 
										", ShukranCoinsBurned (from DB): " + item.getShukranCoinsBurned() + 
										", PointsForNonCancelledQty: " + itemPointsForNonCancelled);
							} else {
								LOGGER.info("cancelUnfulfilledSplitOrder: Skipping item in CURRENT shipment (fully cancelled or no burned points) - SKU: " + item.getSku() + 
										", ItemId: " + item.getItemId() +
										", QtyOrdered: " + qtyOrdered + 
										", QtyCancelled: " + qtyCancelled + 
										", ShukranCoinsBurned: " + item.getShukranCoinsBurned());
							}
						}
					}
					LOGGER.info("cancelUnfulfilledSplitOrder: Total non-cancelled points from CURRENT shipment: " + currentShipmentNonCancelledPoints + 
							" (isFullyCancellation: " + isFullyCancellation + ")");
				}
				
				// Update SplitSubSalesOrder with remaining burned points for current shipment (if not fully cancelled)
				if (!isFullyCancellation && store != null && totalShukranCoinsNeedsToBeLockedAgain.compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal pointsToLock = totalShukranCoinsNeedsToBeLockedAgain.setScale(0, RoundingMode.HALF_UP);
					SplitSubSalesOrder splitSubSalesOrder = splitSalesOrder.getSplitSubSalesOrder();
					splitSubSalesOrder.setTotalShukranCoinsBurned(new BigDecimal(pointsToLock.toBigInteger()));
					splitSubSalesOrder.setTotalShukranBurnedValueInBaseCurrency(totalShukranCoinsNeedsToBeLockedAgain.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP));
					splitSubSalesOrder.setTotalShukranBurnedValueInCurrency(totalShukranCoinsNeedsToBeLockedAgain.multiply(store.getShukranPointConversion() != null ? store.getShukranPointConversion() : BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP));
					splitSubSalesOrder.setShukranLocked(0);
					splitSubSalesOrderRepository.saveAndFlush(splitSubSalesOrder);
					LOGGER.info("cancelUnfulfilledSplitOrder: Updated SplitSubSalesOrder with remaining burned points: " + pointsToLock);
				} else if (isFullyCancellation && isAllShipmentsCancelled) {
					LOGGER.info("cancelUnfulfilledSplitOrder: Full cancellation of ALL shipments - All Shukran points remain unlocked (not locking back)");
				} else if (isFullyCancellation && !isAllShipmentsCancelled) {
					LOGGER.info("cancelUnfulfilledSplitOrder: Current shipment is fully cancelled but other shipments exist - Will lock back points from other shipments");
				}

				// For split orders: Lock back burned points from CURRENT shipment + OTHER shipments for non-cancelled items only
				// This should run even if current shipment is fully cancelled, as long as other shipments exist
				if (splitSalesOrder.getSalesOrder() != null && store != null && !isAllShipmentsCancelled) {
					try {
						LOGGER.info("cancelUnfulfilledSplitOrder: Checking for other shipments in split order. Current splitOrderId: " + splitSalesOrder.getEntityId() + ", incrementId: " + splitSalesOrder.getIncrementId() + ", Parent orderId: " + splitSalesOrder.getSalesOrder().getEntityId());
						List<SplitSalesOrder> allSplitOrders = splitSalesOrderRepository.findBySalesOrder(splitSalesOrder.getSalesOrder());
						
						if (CollectionUtils.isNotEmpty(allSplitOrders)) {
							BigDecimal otherShipmentsBurnedPoints = BigDecimal.ZERO;
							
							for (SplitSalesOrder otherSplitOrder : allSplitOrders) {
								// Skip the current split order being cancelled (we already calculated it above)
								if (otherSplitOrder.getEntityId().equals(splitSalesOrder.getEntityId())) {
									LOGGER.info("cancelUnfulfilledSplitOrder: Skipping current shipment (already calculated above) - splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ")");
									continue;
								}
								
								LOGGER.info("cancelUnfulfilledSplitOrder: Checking other shipment - splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ", status: " + otherSplitOrder.getStatus());
								
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
											
											LOGGER.info("cancelUnfulfilledSplitOrder: Found non-cancelled item in other shipment - SKU: " + item.getSku() + 
													", ItemId: " + item.getItemId() +
													", QtyOrdered: " + qtyOrdered + 
													", QtyCancelled: " + qtyCancelled + 
													", QtyNotCancelled: " + qtyNotCancelled + 
													", ShukranCoinsBurned (from DB): " + item.getShukranCoinsBurned() + 
													", PointsForNonCancelledQty: " + itemPointsForNonCancelled);
										} else {
											LOGGER.info("cancelUnfulfilledSplitOrder: Skipping item (fully cancelled or no burned points) - SKU: " + item.getSku() + 
													", ItemId: " + item.getItemId() +
													", QtyOrdered: " + qtyOrdered + 
													", QtyCancelled: " + qtyCancelled + 
													", ShukranCoinsBurned: " + item.getShukranCoinsBurned());
										}
									}
									
									if (shipmentBurnedPoints.compareTo(BigDecimal.ZERO) > 0) {
										otherShipmentsBurnedPoints = otherShipmentsBurnedPoints.add(shipmentBurnedPoints);
										LOGGER.info("cancelUnfulfilledSplitOrder: Accumulated points from shipment (splitOrderId: " + otherSplitOrder.getEntityId() + 
												", incrementId: " + otherSplitOrder.getIncrementId() + "): " + shipmentBurnedPoints);
									} else {
										LOGGER.info("cancelUnfulfilledSplitOrder: No non-cancelled items with burned points in shipment (splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ")");
									}
								} else {
									LOGGER.info("cancelUnfulfilledSplitOrder: No items found for other shipment (splitOrderId: " + otherSplitOrder.getEntityId() + ", incrementId: " + otherSplitOrder.getIncrementId() + ")");
								}
							}
							
							// Combine current shipment's non-cancelled points + other shipments' non-cancelled points
							BigDecimal totalPointsToLockBack = currentShipmentNonCancelledPoints.add(otherShipmentsBurnedPoints);
							
							// Lock back points from current shipment + other shipments if any
							if (totalPointsToLockBack.compareTo(BigDecimal.ZERO) > 0) {
								LOGGER.info("cancelUnfulfilledSplitOrder: Locking back burned points from non-cancelled items - Current shipment: " + currentShipmentNonCancelledPoints + 
										", Other shipments: " + otherShipmentsBurnedPoints + 
										", Total: " + totalPointsToLockBack);
								
								// Only lock if we previously unlocked (i.e., customerTotalPoints check passed)
								if (customerTotalPoints != null && customerTotalPoints.compareTo(BigDecimal.ZERO) >= 0) {
									commonService.lockUnlockShukranDataForSplit(
											splitSalesOrder.getSplitSubSalesOrder().getCustomerProfileId(), 
											totalPointsToLockBack.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString(), 
											splitSalesOrder.getSplitSubSalesOrder().getQuoteId(), 
											true, // true = LOCK
											splitSalesOrder, 
											store, 
											"Shukran Burned Points On Current & Other Shipments (Not Cancelled)", 
											"Seller Cancelation Api - Split Order"
									);
									LOGGER.info("cancelUnfulfilledSplitOrder: Successfully locked back " + totalPointsToLockBack + " points from non-cancelled items (current + other shipments)");
								} else {
									LOGGER.info("cancelUnfulfilledSplitOrder: Skipping lockUnlockShukranDataForSplit call (customerTotalPoints check failed), but points calculated: " + totalPointsToLockBack);
								}
							} else {
								LOGGER.info("cancelUnfulfilledSplitOrder: No burned points found in non-cancelled items from current or other shipments to lock back");
							}
						} else {
							LOGGER.info("cancelUnfulfilledSplitOrder: No split orders found for parent orderId: " + splitSalesOrder.getSalesOrder().getEntityId());
						}
					} catch (Exception e) {
						LOGGER.error("cancelUnfulfilledSplitOrder: Error while locking back points from other shipments in split order. SplitOrderId: " + splitSalesOrder.getEntityId() + ". Error: " + e.getMessage(), e);
						// Don't throw exception - continue with cancellation even if this fails
					}
				}

				orderHelper.releaseInventoryQtyForSplitOrder(splitSalesOrder, skumApList, false, OrderConstants.RELEASE_SELLER_CANCELLATION);
				if (store != null && store.getIsShukranEnable() && splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getEntityId() != null && StringUtils.isNotEmpty(splitSalesOrder.getSplitSubSalesOrder().getTenders()) && StringUtils.isNotBlank(splitSalesOrder.getSplitSubSalesOrder().getTenders())) {
					resetShukranTendersForSplitOrder(splitSalesOrder.getSplitSubSalesOrder().getTenders(), splitSalesOrder.getEntityId(), store);
				}

			} else {

				response.setHasError(true);
				response.setErrorMessage("bad request");
				return response;
			}

			orderHelper.cancelSellerOrders(null, splitSalesOrder, request);
			// EAS Service Call for Styli Coins Refund on Seller Cancellation
			if (splitSalesOrder != null) {
				eASServiceImpl.handleSellerCancellationStyliCoinsRefund(
					splitSalesOrder.getIncrementId(),
					splitSalesOrder.getCustomerId(),
					splitSalesOrder.getCustomerIsGuest(),
					store,
					request.getOrderItems(),
					httpRequestHeadrs.get(Constants.deviceId),
					true,  // isSplitOrder
					splitSalesOrder, // splitSalesOrder
					null   // salesOrder
				);
			}

			response.setHasError(false);
			return response;
		} catch (Exception e) {
			LOGGER.info("Cancel Unfulfilment Order " + e.getMessage());
			throw new RuntimeException(e);
		}
	}


	private void setOrderGrid(SplitSalesOrder splitSalesOrder, Boolean isFullyCancellation, String orderStatus) {
		SalesOrderGrid grid = salesOrderGridRepository.findByEntityId(splitSalesOrder.getSalesOrder().getEntityId());

		if (grid != null && isFullyCancellation) {
			splitSalesOrder.getSplitSubSalesOrder().setIsUnfulfilmentOrder(2);
			splitSalesOrder.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			SalesOrder salesOrder = splitSalesOrder.getSalesOrder();
			// If main order is having multiple split sales orders , loop all the split sales orders and find the order status
			// If all are CLOSED_ORDER_STATUS make sales order and grid as CLOSED_ORDER_STATUS
			List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findBySalesOrder(salesOrder);
			AtomicBoolean isAllClosed = new AtomicBoolean(true);
			splitSalesOrders.stream().filter(e -> e.getEntityId()!= splitSalesOrder.getEntityId())
					.forEach(e -> {
						if (!e.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
							isAllClosed.set(false);
						}
					});
			if (isAllClosed.get()) {
				splitSalesOrder.getSalesOrder().setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				salesOrderGridRepository.saveAndFlush(grid);
				paymentUtility.publishToSplitPubSubOTSForSalesOrder(splitSalesOrder.getSalesOrder(),"4.0","Closed");
			}
			// When only current shipment is fully cancelled but other shipments exist, do not close main order (grid)
		} else if (null != grid) {
			grid.setStatus(orderStatus);
			splitSalesOrder.setStatus(orderStatus);
			salesOrderGridRepository.saveAndFlush(grid);
		}
	}


	private void addSplitStoreCredit(SplitSalesOrder splitSalesOrder, BigDecimal storeCreditAmount, CancelDetails details) {
		BigDecimal amastyBaseAmount = details.getAmastyBaseStoreCredit();

		BigDecimal cancelledAmastyAmount = new BigDecimal("0");
		String OrderActionData = "[\"".concat(splitSalesOrder.getIncrementId()).concat("\"]");
		LOGGER.info("OrderActionData:" + OrderActionData);

		List<AmastyStoreCreditHistory> amastyHistoryList = amastyStoreCreditHistoryRepository
				.findByActionDataAndAction(OrderActionData, 0);

		if (CollectionUtils.isNotEmpty(amastyHistoryList)) {

			for (AmastyStoreCreditHistory storeCreditHistory : amastyHistoryList) {

				cancelledAmastyAmount = cancelledAmastyAmount.add(storeCreditHistory.getDifference());
			}

			BigDecimal newAmastyAount = new BigDecimal("0");

			newAmastyAount = newAmastyAount.add(cancelledAmastyAmount).add(amastyBaseAmount);

			BigDecimal difference = splitSalesOrder.getAmstorecreditBaseAmount().subtract(newAmastyAount);
			BigDecimal constantDifference = new BigDecimal("0.3");
			if (difference.compareTo(constantDifference) == 1) {

				amastyBaseAmount = amastyBaseAmount.add(difference);
			}

		}

		if (null != storeCreditAmount && amastyBaseAmount.compareTo(BigDecimal.ZERO) != 0) {
			splitPaymentRefundHelper.addSplitOrderStoreCredit(splitSalesOrder, amastyBaseAmount, details.isGiftVoucher());

		}
	}


	/**
	 * @param splitSalesOrder
	 * @param itemList
	 * @param skumApList
	 * @param canceledItem
	 * @param actualskumApList
	 */
	private void calculateCancelledValue(SplitSalesOrder splitSalesOrder, List<SplitSalesOrderItem> itemList,
										 Map<String, BigDecimal> skumApList, OrderPushItem canceledItem, Map<String, BigDecimal> actualskumApList) {
		for (SplitSalesOrderItem item : splitSalesOrder.getSplitSalesOrderItems()) {

			LOGGER.info("item:" + item.getItemId());
			LOGGER.info("canceledItem:" + canceledItem.getOrderItemCode());

			if (item.getItemId().equals(Integer.parseInt(canceledItem.getOrderItemCode()))) {

				BigDecimal qtyOrdered = item.getQtyOrdered();
				BigDecimal qtyCanclled = item.getQtyCanceled();

				if (null != qtyOrdered && null != qtyCanclled
						&& qtyOrdered.intValue() > qtyCanclled.intValue()
						&& null != canceledItem.getCancelledQuantity()
						&& null != item.getSellerQtyCancelled()
						&& item.getSellerQtyCancelled().compareTo(canceledItem.getCancelledQuantity()) != 0) {

					final BigDecimal previousCancelVal = item.getSellerQtyCancelled();

					splitSalesOrder.getSplitSalesOrderItems().stream().forEach(i -> {
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

				} else if (null != qtyOrdered
						&& (null == item.getSellerQtyCancelled() || item.getSellerQtyCancelled().intValue() == 0)) {

					final BigDecimal previousCancelVal = new BigDecimal(0);

					splitSalesOrder.getSplitSalesOrderItems().stream().forEach(i -> {
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
	 * @param splitSalesOrder
	 * @param message
	 * @param entity
	 * @param status
	 */
	public void updateOrderStatusHistory(SplitSalesOrder splitSalesOrder, String message
			, String entity, String status) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();

		LOGGER.info("History set");

		sh.setParentId(splitSalesOrder.getSalesOrder().getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(1);
		sh.setComment("Split Order updated with message: " + message);
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setStatus(status);
		sh.setSplitSalesOrder(splitSalesOrder);
		sh.setEntityName(entity);

		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}


	private boolean validateRequest(SplitSalesOrder splitSalesOrder, OrderunfulfilmentRequest request) {

		for (OrderPushItem canceledItem : request.getOrderItems()) {

			if (null != canceledItem.getCancelledQuantity() && null != canceledItem.getOrderItemCode()) {

				for (SplitSalesOrderItem item : splitSalesOrder.getSplitSalesOrderItems()) {

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


	private BigDecimal onlineForward(SplitSalesOrder splitSalesOrder, Stores store, String fortId, String paymentMethod,
									 BigDecimal storeCreditAmount, BigDecimal calcultedcancelAmount, BigDecimal beforeCancelledAmount
			, Map<String, BigDecimal> skuMapList, Boolean isFullyCancellation, List<SplitSalesOrderItem> itemList
			, Map<String, BigDecimal> actualSkuMapList, BigDecimal totalVoucherToRefund, BigDecimal currentOrderValue) {


		boolean payfortRefundOnSellerCancellation = Constants.orderCredentials.getPayfort().isPayfortRefundOnSellerCancellation();
		boolean isMadaTransaction = false;

		// Check if the transaction is a Mada transaction by querying the sales order repository with the parent sales order ID
		if (null!=splitSalesOrder.getSalesOrder() && null!=splitSalesOrder.getSalesOrder().getEntityId()) {
			int count = salesOrderRepository.checkIfMadaTransaction(splitSalesOrder.getSalesOrder().getEntityId());
			isMadaTransaction = count > 0;
		}
		LOGGER.info("Seller cancellation check — Order " + splitSalesOrder.getIncrementId() + " consul flag is : " + payfortRefundOnSellerCancellation);
		LOGGER.info("Seller cancellation check — Order " + splitSalesOrder.getIncrementId() + " is MADA transaction: " + isMadaTransaction);

		boolean isSellerCancelDoneBefore = false;
		if (null!=splitSalesOrder.getSalesOrder() && null!=splitSalesOrder.getSalesOrder().getEntityId()) {
			int sellerCancelCount = salesOrderRepository.checkIfSellerCancelExists(splitSalesOrder.getSalesOrder().getEntityId());
			isSellerCancelDoneBefore = sellerCancelCount > 0;
		}
		LOGGER.info("Seller cancellation check — Order " + splitSalesOrder.getIncrementId() + " has previous seller cancellations: " + isSellerCancelDoneBefore);

		CancelDetails details = new CancelDetails();

		calcultedcancelAmount = splitPaymentRefundHelper.cancelPercentageCalculation(splitSalesOrder, calcultedcancelAmount, storeCreditAmount, details
				, isFullyCancellation, paymentMethod, totalVoucherToRefund);
		LOGGER.info("Calculated cancel amount after percentage calculation: " + calcultedcancelAmount);
		PayfortConfiguration configuration = new PayfortConfiguration();
		splitPaymentRefundHelper.getPayfortConfDetails(splitSalesOrder.getStoreId().toString(), paymentMethod, configuration);
		try {
			LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
		} catch (JsonProcessingException e) {
			LOGGER.error("error during write configuration:" + e.getMessage());
		}

		RefundPaymentRespone paymentresponse = new RefundPaymentRespone();
		LOGGER.info("In onlineForward — Order " + splitSalesOrder.getIncrementId() +
				" has payfort authorised flag: " + splitSalesOrder.getPayfortAuthorized() +
				" for payment method: " + paymentMethod);
		if (splitSalesOrder.getPayfortAuthorized() != null && splitSalesOrder.getPayfortAuthorized() == 1 && isFullyCancellation && !isSellerCancelDoneBefore) {
			// Case 1: Fully cancelled, not seller-cancelled earlier — do a void authorization
			LOGGER.info("In Seller cancellation check — Order: "+splitSalesOrder.getPayfortAuthorized()+
			"isFullyCancellation: "+isFullyCancellation+"isSellerCancelDoneBefore: "+isSellerCancelDoneBefore);
			paymentresponse = splitPaymentRefundHelper.payfortVoidAuthorizationcall(splitSalesOrder, fortId, paymentMethod);

		} else if (splitSalesOrder.getPayfortAuthorized() != null && splitSalesOrder.getPayfortAuthorized() == 1 && isMadaTransaction && payfortRefundOnSellerCancellation) {
			// Case 2: Authorized Mada transaction — capture first, then refund
			LOGGER.info("Seller cancellation check — Order " + splitSalesOrder.getIncrementId() +
					" has payfort authorised flag: " + splitSalesOrder.getPayfortAuthorized() +
					" for payment method: " + paymentMethod +
					" where refund amount is: " + calcultedcancelAmount);

			// Prepare and send capture request
			PayfortPaymentCaptureRequest captureRequest = paymentUtility.preparePayfortCaptureRequestV2(configuration, splitSalesOrder, calcultedcancelAmount, fortId);
			PayfortReposne captureResponse = paymentUtility.triggerPayfortPaymentCaptureRestApiCallV2(captureRequest, splitSalesOrder, configuration);

			if (captureResponse != null && captureResponse.isStatus()) {
				LOGGER.info("Payfort capture successful for order " + splitSalesOrder.getIncrementId() + ", proceeding to refund");
				paymentresponse = splitPaymentRefundHelper.payfortSplitOrderRefundcall(splitSalesOrder, calcultedcancelAmount, fortId, paymentMethod);
			} else {
				LOGGER.error("Payfort capture failed for order " + splitSalesOrder.getIncrementId() + ", skipping refund.");
			}

		} else if (splitSalesOrder.getPayfortAuthorized() != null && splitSalesOrder.getPayfortAuthorized() != 1) {
			LOGGER.info("In onlineForward — Order " + splitSalesOrder.getIncrementId() + "  Payment method: " + paymentMethod+", splitSalesOrder.getPayfortAuthorized() "+splitSalesOrder.getPayfortAuthorized());
			paymentresponse = splitPaymentRefundHelper.payfortSplitOrderRefundcall(splitSalesOrder, calcultedcancelAmount, fortId, paymentMethod);
		}


		if (null != paymentresponse.getStatusCode() && paymentresponse.getStatusCode().equals("200")) {

			//String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
			SalesOrderGrid grid = refundHelper.cancelSplitOrderGrid(splitSalesOrder, true, paymentMethod);
			grid.setStatus(splitSalesOrder.getStatus());
			if (null != details.getAmastyBaseStoreCredit()
					&& !(details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) == 0)) {

				if (!isFullyCancellation) {

				}
				splitPaymentRefundHelper.addSplitOrderStoreCredit(splitSalesOrder, details.getAmastyBaseStoreCredit(), details.isGiftVoucher());

			}

			if (!splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				splitSalesOrder.setWmsPullStatus(2);

			}

			splitSalesOrderRepository.saveAndFlush(splitSalesOrder);
		} else if (null != splitSalesOrder.getPayfortAuthorized() && splitSalesOrder.getPayfortAuthorized() == 1 && !isFullyCancellation
				&& null != details.getAmastyBaseStoreCredit()
				&& !(details.getAmastyBaseStoreCredit().compareTo(BigDecimal.ZERO) == 0)) {
			splitPaymentRefundHelper.addSplitOrderStoreCredit(splitSalesOrder, details.getAmastyBaseStoreCredit(), details.isGiftVoucher());

			if (!splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				splitSalesOrder.setWmsPullStatus(2);

			}
			splitSalesOrderRepository.saveAndFlush(splitSalesOrder);
		}

		if (isFullyCancellation) {
			SalesOrderGrid grid = refundHelper.cancelSplitOrderGrid(splitSalesOrder, true, paymentMethod);
			salesOrderGridRepository.saveAndFlush(grid);
		}

		return calcultedcancelAmount;
	}
	
	private BigDecimal coinSubtractOncancel(Boolean isFullyCancellation, SplitSalesOrder splitSalesOrder, BigDecimal totalAmountToRefund, EASPartialCancelRefundResponse eASPartialCancelRefundResponse) {
		if(Objects.isNull(eASPartialCancelRefundResponse.getCoinAmountRefunded())) {
			return totalAmountToRefund;
		}
		BigDecimal newTotalAmountToRefund = totalAmountToRefund;
		if(isFullyCancellation) {
			LOGGER.info("In coinSubtractOncancel isFullyCancellation");
			if(null != splitSalesOrder.getSplitSubSalesOrder() && null != eASPartialCancelRefundResponse.getEasValueInBaseCurrency()) {
				LOGGER.info("In coinSubtractOncancel coinAmountRefunded: " + eASPartialCancelRefundResponse.getEasValueInBaseCurrency() + ", totalAmountToRefund: " + totalAmountToRefund);
				newTotalAmountToRefund = totalAmountToRefund.subtract(eASPartialCancelRefundResponse.getEasValueInBaseCurrency());
			}
		}else {
			LOGGER.info("In coinSubtractOncancel coinAmountRefunded: " + eASPartialCancelRefundResponse.getCoinAmountRefunded() + ", totalAmountToRefund: " + totalAmountToRefund);
			newTotalAmountToRefund = totalAmountToRefund.subtract(eASPartialCancelRefundResponse.getCoinAmountRefunded());
		}
		return newTotalAmountToRefund;
	}

	public void resetShukranTendersForSplitOrder(String tenders, Integer orderId, Stores store){
		String response= tenders;
		ObjectMapper objectMapper= new ObjectMapper();
		SplitSalesOrder splitSalesOrder= splitSalesOrderRepository.findByEntityId(orderId);
		if(splitSalesOrder != null && splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSalesOrderItems() != null) {
			try {
				BigDecimal taxFactor = new BigDecimal(1);
				if (store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
					taxFactor = taxFactor.add(store.getTaxPercentage().divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
				}
				BigDecimal totalTenderValue = BigDecimal.ZERO;
				for (SplitSalesOrderItem item : splitSalesOrder.getSplitSalesOrderItems()) {
					if (item.getProductType().equalsIgnoreCase(SIMPLE_PRODUCT_TYPE)) {
						BigDecimal itemSubTotal = item.getOriginalPrice()
								.divide(taxFactor, 6, RoundingMode.HALF_UP)
								.multiply(item.getQtyOrdered());

						BigDecimal itemDiscount1 = (item.getOriginalPrice()
								.subtract(item.getPriceInclTax()))
								.divide(taxFactor, 6, RoundingMode.HALF_UP).multiply(item.getQtyOrdered());

						BigDecimal discountAmount = BigDecimal.ZERO;

						if (item.getSplitSalesOrderItem() != null) {
							BigDecimal subSalesOrderDiscountAmount = splitSubSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(splitSalesOrder.getEntityId(), item.getSalesOrderItem().getItemId());
							if (subSalesOrderDiscountAmount != null) {
								discountAmount = subSalesOrderDiscountAmount;
							}
						} else if (item.getSalesOrderItem() != null) {

							for (SplitSubSalesOrderItem i : item.getSplitSubSalesOrderItem()) {
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

				List<ShukranTenders> shukranTenders = objectMapper.readValue(splitSalesOrder.getSplitSubSalesOrder().getTenders(), new TypeReference<List<ShukranTenders>>() {});
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
					BigDecimal tenderAmount = e.getTenderCode().equals(Constants.orderCredentials.getShukranTenderMappings().getCashOnDelivery()) ? e.getTenderAmount().subtract(splitSalesOrder.getCashOnDeliveryFee()) : e.getTenderAmount();
					if (finalOriginalTotalTenderValue.compareTo(BigDecimal.ZERO) > 0) {
						BigDecimal newTenderAmount = tenderAmount.divide(finalOriginalTotalTenderValue, 6, RoundingMode.HALF_UP).multiply(finalTotalTenderValue).setScale(2, RoundingMode.HALF_UP);
						LOGGER.info("new tender amount " + newTenderAmount);
						newTenderAmount = e.getTenderCode().equals(Constants.orderCredentials.getShukranTenderMappings().getCashOnDelivery()) ? newTenderAmount.add(splitSalesOrder.getCashOnDeliveryFee()) : newTenderAmount;
						e.setTenderAmount(newTenderAmount);
					}
					shukranTenders1.add(e);
				});
				response = objectMapper.writeValueAsString(shukranTenders1);

			} catch (JsonProcessingException e) {
				LOGGER.info("Error In Resetting Tenders" + e.getMessage());
			}
			SplitSubSalesOrder splitSubSalesOrder = splitSalesOrder.getSplitSubSalesOrder();
			splitSubSalesOrder.setTenders(response);
			splitSubSalesOrderRepository.saveAndFlush(splitSubSalesOrder);
		}
	}
}

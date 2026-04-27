package org.styli.services.order.converter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SubSalesOrderItem;
import org.styli.services.order.pojo.ClickpostMessageJSON;
import org.styli.services.order.pojo.DiscountData;
import org.styli.services.order.pojo.QuantityReturned;
import org.styli.services.order.pojo.SalesOrderPaymentInformation;
import org.styli.services.order.pojo.mulin.GalleryItem;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.response.Order.OrderAddress;
import org.styli.services.order.pojo.response.Order.OrderItem;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.pojo.response.Order.OrderTracking;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OrderEntityConverterV1 {

	private static final Log LOGGER = LogFactory.getLog(OrderEntityConverterV1.class);

	@Autowired
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Value("${magento.base.url}")
	private String magentoBaseUrl;

	@Value("${shipping.navik.base.url}")
	private String shippingNavikBaseUrl;

	@Value("${shipping.clickpost.base.url}")
	private String shippingClickpostBaseUrl;

	public OrderResponse convertOrder(SalesOrder order, boolean orderDetails, ObjectMapper mapper,
			Map<String, ProductResponseBody> productsFromMulin, String paymentId) {

		OrderResponse resp = new OrderResponse();

		resp.setTabbyPaymentId(paymentId);
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
		resp.setCreatedAt(convertTimezone1(order.getCreatedAt()));
		resp.setUpdatedAt(convertTimezone1(order.getUpdatedAt()));
		resp.setEmail(parseNullStr(order.getCustomerEmail()));
		resp.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
		resp.setStoreCreditApplied(parseNullStr(order.getAmstorecreditAmount()));

		resp.setDeliveredAt(convertTimezone1(order.getDeliveredAt()));
		resp.setEstimatedDeliveryTime(convertTimezone1(order.getEstimatedDeliveryTime()));

		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {
			resp.setDonationAmount(parseNullStr(order.getSubSalesOrder().getDonationAmount()));
		}

		addClickpostmessage(order.getClickpostMessage(), resp, mapper);

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
		SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
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

		resp.setCodCharges(parseNullStr(order.getCashOnDeliveryFee()));
		resp.setImportFeesAmount(parseNullStr(order.getImportFee()));

		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

		addCoupondetails(order, resp, mapper);
		addPaymentinfo(mapper, resp, salesOrderPayment);

		addOrderAddress(order, resp, orderDetails);
		addCanceldates(order, resp);

		return resp;
	}

	public String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}

	private void setOrderStatusCallToActionFlag(OrderResponse resp, SalesOrder order) {

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
		case OrderConstants.CANCELED_ORDER_STATE:
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
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.DELIVERED_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.INWARD_MIDMILE_ORDER_STATUS) ||
									sellerOrder.getStatus().equalsIgnoreCase(OrderConstants.OUTWARD_MIDMILE_ORDER_STATUS)));
			if (hasRestrictedStatus) {
				return false;
			}
		}
		return true;
	}

	private void setFullyOrPartialCancelled(SalesOrder order, OrderResponse resp) {
		/** Set fully cancelled and partial cancelled flag */
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsUnfulfilmentOrder()) {

			if (order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(1)) {
				resp.setPartialCancelled(true);

			} else if (order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(2)) {
				resp.setFullyCancelled(true);
			}
		}
	}

	private void addOtherinformation(SalesOrder order, OrderResponse resp, Boolean orderDetails,
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
				if (orderDetails.booleanValue()) {
					productDTO.setSku(salesOrderItem.getSku());
					productDTO.setName(salesOrderItem.getName());
					productDTO.setParentOrderItemId(parseNullStr(salesOrderItem.getItemId()));
					addQtydetails(salesOrderItem, productDTO);

					order.getSalesOrderItem().stream()
							.filter(e -> e.getParentOrderItem() != null && e.getParentOrderItem().getItemId() != null
									&& e.getParentOrderItem().getItemId().equals(salesOrderItem.getItemId()))
							.findFirst().ifPresent(childOrderItem -> {
								QuantityReturned quantityReturned = omsorderentityConverter
										.getQtyReturned(childOrderItem.getItemId(), true, false);
								productDTO.setQtyReturned(parseNullStr(quantityReturned.getQtyReturned()));
								productDTO.setQtyReturnedInProcess(
										parseNullStr(quantityReturned.getQtyReturnedInProcess()));
							});
					BigDecimal totalSellableprice = null;

					if (salesOrderItem.getPriceInclTax() != null && salesOrderItem.getOriginalPrice() != null) {

						totalSellableprice = salesOrderItem.getPriceInclTax();

						BigDecimal productDiscountPrice = new BigDecimal(0);
						BigDecimal bagDiscountPrice = new BigDecimal(0);
						BigDecimal indivisualProductDiscountPrice = new BigDecimal(0);

						if (CollectionUtils.isNotEmpty(salesOrderItem.getSubSalesOrderItem())) {
							productDiscountPrice = salesOrderItem.getSubSalesOrderItem().stream()
									.map(SubSalesOrderItem::getDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);

							indivisualProductDiscountPrice = salesOrderItem.getSubSalesOrderItem().stream()
									.map(SubSalesOrderItem::getDiscount).reduce(BigDecimal.ZERO, BigDecimal::add)
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

						configureDiscountamount(productDTO, salesOrderItem, discountAmount, totalSellableprice,
								discountedSellablePrice);
						subTotal = subTotal
								.add(salesOrderItem.getOriginalPrice().multiply(salesOrderItem.getQtyOrdered()));
						totalBagDiscount = totalBagDiscount.add(bagDiscountPrice);

					}

				}

				boolean returnCategoryRestriction = false;
				for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
					ProductResponseBody productDetailsFromMulin = entry.getValue();
					Variant variant = productDetailsFromMulin.getVariants().stream()
							.filter(e -> e.getSku().equals(salesOrderItem.getSku())).findAny().orElse(null);
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
					}
				}
				productDTO.setAvailableNow(true);
				productDTO.setParentProductId(parseNullStr(salesOrderItem.getProductId()));

				if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsUnfulfilmentOrder()) {

					// Check if the order is partially unfulfilled
					if (order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(2)) {

					}
					if (productDTO.getQty() != null && Double.parseDouble(productDTO.getQty()) > 0
							&& productDTO.getQtyCanceled() != null
							&& Double.parseDouble(productDTO.getQtyCanceled()) > 0) {

						setAmountRefundAndInitiatedDate(order, resp, productDTO, salesOrderItem);
					}
				}

				resp.getProducts().add(productDTO);

				addCancelProducts(order, resp, productDTO, salesOrderItem);
			}
		}

		addtotaldisAmount(totalBagDiscount, resp, subTotal);

	}

	private void addCancelProducts(SalesOrder order, OrderResponse resp, OrderItem productDTO,
			SalesOrderItem salesOrderItem) {
		/** Set amount and date for unfulfilled products to show on UI */
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsUnfulfilmentOrder()
				&& order.getSubSalesOrder().getIsUnfulfilmentOrder().equals(1) && productDTO.getQty() != null
				&& Double.parseDouble(productDTO.getQty()) > 0 && productDTO.getQtyCanceled() != null
				&& Double.parseDouble(productDTO.getQtyCanceled()) > 0) {
			// Check if the order is partially unfulfilled
			setAmountRefundAndInitiatedDate(order, resp, productDTO, salesOrderItem);
			resp.getCancelProducts().add(productDTO);
		}
	}

	private void setAmountRefundAndInitiatedDate(SalesOrder order, OrderResponse resp, OrderItem productDTO,
			SalesOrderItem salesOrderItem) {
		if (null != salesOrderItem.getDiscountAmount() && null != salesOrderItem.getPriceInclTax()) {

			BigDecimal discount = salesOrderItem.getDiscountAmount().divide(salesOrderItem.getQtyOrdered(), 4,
					RoundingMode.HALF_UP);

			BigDecimal productAmtRefunded = salesOrderItem.getPriceInclTax().subtract(discount)
					.multiply(salesOrderItem.getQtyCanceled()).setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP);

			productDTO.setAmountRefund(String.valueOf(productAmtRefunded));
		}

		// List<SalesCreditmemo> salesCreditMemoList = salesCreditmemoRepository.findByOrderId(order.getEntityId());
		List<SalesCreditmemo> salesCreditMemoList = orderHelper.getSalesCreditMemoList(order.getEntityId());
		if (salesCreditMemoList != null && !salesCreditMemoList.isEmpty() && salesCreditMemoList.get(0) != null) {

			BigDecimal styliCredit = salesCreditMemoList.stream().map(SalesCreditmemo::getAmstorecreditAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP);

			resp.setStyliCreditRefund(styliCredit.toString());

			BigDecimal totalAmtRefund = salesCreditMemoList.stream().map(SalesCreditmemo::getGrandTotal)
					.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP);

			resp.setTotalAmountRefund(totalAmtRefund.toString());

			productDTO.setRefundInitiatedOn(
					convertTimezone(salesCreditMemoList.get(0).getCreatedAt(), order.getStoreId()));
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

	private void addtotaldisAmount(BigDecimal totalBagDiscount, OrderResponse resp, BigDecimal subTotal) {

		if (totalBagDiscount.doubleValue() < 0) {
			resp.setDiscountAmount("0.0");
		} else {
			resp.setDiscountAmount(parseNullStr(totalBagDiscount.doubleValue()));
		}

		resp.setSubtotal(parseNullStr(subTotal.setScale(2, RoundingMode.HALF_UP)));

	}

	private void configureDiscountamount(OrderItem productDTO, SalesOrderItem salesOrderItem, double discountAmount,
			BigDecimal totalSellableprice, BigDecimal discountedSellablePrice) {

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

			BigDecimal totalDiscount = salesOrderItem.getOriginalPrice().multiply(salesOrderItem.getQtyOrdered())
					.subtract(discountedSellablePrice.multiply(salesOrderItem.getQtyOrdered()));

			double discount = Double.parseDouble(df.format(totalDiscount.doubleValue()
					/ Double.parseDouble(
							salesOrderItem.getOriginalPrice().multiply(salesOrderItem.getQtyOrdered()).toString())
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
				} catch (JsonProcessingException e) {
					LOGGER.error("exception occoured during convert offer string to object" + e.getMessage());
				}
				resp.setDiscountData(discountDataList);
			}
		}

		BigDecimal couponDiscount = order.getDiscountAmount();
		if (null != couponDiscount && null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getExternalAutoCouponAmount()) {
			couponDiscount = couponDiscount.abs().subtract(order.getSubSalesOrder().getExternalAutoCouponAmount());
			resp.setCouponDiscountAmount(parseNullStr(couponDiscount));
		}
	}

	private void addPaymentinfo(ObjectMapper mapper, OrderResponse resp, SalesOrderPayment salesOrderPayment) {
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

	private void addOrderAddress(SalesOrder order, OrderResponse resp, boolean orderDetails) {

		if (orderDetails) {

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
                    orderAddress.setShortAddress(shippingAddress.getShortAddress());
                    orderAddress.setKsaAddressComplaint(shippingAddress.getKsaAddressComplaint());
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
						if (null != Constants.orderCredentials
								&& Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
							encodeValue = order.getEntityId().toString().concat("#").concat(order.getCustomerEmail());
						} else {
							encodeValue = order.getEntityId().toString();
						}
						String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
						String invoiceUrl = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()
								+ "/v1/orders/generatePDF/" + encoded;
						invoices.add(invoiceUrl);
					}
				}
			}
			resp.setInvoices(invoices);
		}

	}

	private void configureOrderTracking(SalesOrder order, OrderResponse resp) {

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

			String tarckingNumber = orderTrackings.get(0).getTrack_number();

			if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getIsStyliPost()
					&& order.getSubSalesOrder().getIsStyliPost().equals(1)) {

				if (null != tarckingNumber) {
					String joinTrackingUrl = shippingNavikBaseUrl + "/?waybill=" + tarckingNumber;
					resp.setShippingUrl(joinTrackingUrl);
				}
			} else {

				if (null != tarckingNumber) {
					String joinTrackingUrl = shippingClickpostBaseUrl + "/?waybill=" + tarckingNumber;
					resp.setShippingUrl(joinTrackingUrl);
				}
			}

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
				LOGGER.error("exception occoured:" + e.getMessage());
			}
		}

	}

	private String convertTimezone(Timestamp datetime, Integer storeId) {

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

	/**
	 * @param storeId Integer
	 * @return
	 */
	public String getStoreName(Integer storeId) {

		String storeName = null;

		if (storeId.equals(1)) {

			storeName = "KSA English";

		} else if (storeId.equals(3)) {

			storeName = "KSA Arabic";

		} else if (storeId.equals(7)) {

			storeName = "UAE English";
		} else if (storeId.equals(11)) {

			storeName = "UAE Arabic";
		} else if (storeId.equals(12)) {

			storeName = "KWT English";
		} else if (storeId.equals(13)) {

			storeName = "KWT Arabic";

		} else if (storeId.equals(15)) {

			storeName = "QAT English";
		} else if (storeId.equals(17)) {

			storeName = "KWT Arabic";

		} else if (storeId.equals(19)) {

			storeName = "BAH English";
		} else if (storeId.equals(21)) {

			storeName = "BAH Arabic";
		} else if(storeId.equals(23)) {

			storeName = "OMAN English";
		}else if(storeId.equals(25)) {

			storeName = "OMAN Arabic";
		}
		return storeName;
	}

	private String convertTimezone1(Timestamp datetime) {
		if (null != datetime) {
			Calendar calendar = Calendar.getInstance();
			Date dateTime = new Date(datetime.getTime());
			calendar.setTime(dateTime);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			return sdf.format(calendar.getTime());
		} else {
			return null;
		}
	}

}

package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.order.ShukranEarnItem;
import org.styli.services.order.pojo.order.ShukranEarnItemDetails;
import org.styli.services.order.pojo.order.TotalRefundAmountResponse;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.impl.*;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.UtilityConstant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SplitPaymentRefundHelper {

	private static final String ORDERCONSTANT = "order	";
	private static final Log LOGGER = LogFactory.getLog(SplitPaymentRefundHelper.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	@Value("${eas.base.url}")
	private String earnUrl;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	MulinHelper mulinHelper;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	@Lazy
	private EASServiceImpl eASServiceImpl;

	@Autowired
	private SubSalesOrderItemRepository subSalesOrderItemRepository;

	@Autowired
	private AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	private AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	SplitSubSalesOrderRepository splitSubSalesOrderRepository;

	public RefundPaymentRespone payfortSplitOrderRefundcall(SplitSalesOrder splitSalesOrder, BigDecimal amount, String fortId,
												  String paymentMethod) {
		LOGGER.info("inside payfortRefundcall : ");

		String refundAmount = amount.toString();
		RefundPaymentRespone response = new RefundPaymentRespone();
		PayfortReposne payfortResponse = null;
		if (OrderConstants.checkPaymentMethod(paymentMethod)) {

			PayfortConfiguration configuration = new PayfortConfiguration();
			getPayfortConfDetails(splitSalesOrder.getStoreId().toString(), paymentMethod, configuration);
			try {
				LOGGER.info("inside payfortRefundcall : configuration:" + mapper.writeValueAsString(configuration));
			} catch (JsonProcessingException e) {
				LOGGER.error(" inside payfortRefundcall : error during write configuration:" + e.getMessage());
			}

			payfortResponse = triggerPayfortRefundRestApiCall(
					preparePayfortRefundRequest(configuration, splitSalesOrder, refundAmount, fortId), splitSalesOrder);

		}

		if (null != payfortResponse && !payfortResponse.isStatus()) {
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(payfortResponse.getMessage());
			return response;
		}

		response.setStatus(true);
		response.setStatusCode("200");
		if (Objects.nonNull(payfortResponse)) {
			response.setPaymentRRN(payfortResponse.getPaymentRRN());
		}
		return response;
	}


	// EAS to be implement for payment fail.If Earn Service flag ON!.
	private void releaseCoins(SplitSalesOrder splitSalesOrder) {

		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafkaForSplitOrder(splitSalesOrder, 0.0);
		}
	}

	/**
	 * @param amount
	 * @param multiplier
	 * @return
	 */
	private String getConvertedAmount(String amount, Integer multiplier) {

		LOGGER.info("amount:" + amount);
		LOGGER.info("multiplier:" + multiplier);
		if (null != amount && null != multiplier) {

			Integer payfortValue = new BigDecimal(amount).multiply(new BigDecimal(multiplier)).intValue();

			return payfortValue.toString();
		} else {

			return null;
		}

	}




	private PayfortReposne triggerPayfortRefundRestApiCall(PayfortOrderRefundPayLoad payfortRedundRequest, SplitSalesOrder splitSalesOrder) {
		LOGGER.info("inside payfortRefundcall : triggerPayfortRefundRestApiCall : ");

		PayfortReposne  payfortResponse= new PayfortReposne();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));



		HttpEntity<PayfortOrderRefundPayLoad> requestBody = new HttpEntity<>(payfortRedundRequest, requestHeaders);
		String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi"; //temp for test
		try {

			LOGGER.info("inside payfortRefundcall : payfort url for refund: " + url);
			LOGGER.info("inside payfortRefundcall : Request body: " + mapper.writeValueAsString(requestBody));


			ResponseEntity<PayfortOrderRefundResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					PayfortOrderRefundResponse.class);

			if (response.getStatusCode() == HttpStatus.OK) {
				PayfortOrderRefundResponse responseBody = response.getBody();

				LOGGER.info("inside payfortRefundcall : Payfort refund response Body:"+mapper.writeValueAsString(responseBody));
				if(null != responseBody &&
						null != responseBody.getStatus()
						&& responseBody.getStatus().equals(OrderConstants.PAYFORT_REFUND_CONSTANT_SUCCESS_STATUS)) {

					payfortResponse.setStatus(true);
					payfortResponse.setMessage(responseBody.getResponseMessage());
					payfortResponse.setPaymentRRN(responseBody.getReconciliationReference());
				}else {

					payfortResponse.setStatus(false);
					payfortResponse.setMessage(responseBody.getResponseMessage())	;
				}
			}
		} catch (RestClientException  | JsonProcessingException e2) {
			LOGGER.error("inside payfortRefundcall : exception occoured during refund process:"+splitSalesOrder.getIncrementId()+" "+e2.getMessage());

			payfortResponse.setStatus(false);
			payfortResponse.setMessage(e2.getMessage());
		}

		return payfortResponse;
	}

	/**
	 * @param payfortRedundRequest
	 * @return signature
	 */
	private String getSignature(PayfortOrderRefundPayLoad payfortRedundRequest, String signatureHash) {

		String signature = null;
		String signatureRaw = new StringBuilder()
				.append("access_code=").append(payfortRedundRequest.getAccessCode())
				.append("amount=").append(payfortRedundRequest.getAmount())
				.append("command=").append(OrderConstants.REFUND_STRING)
				.append("currency=").append(payfortRedundRequest.getCurrency())
				.append("fort_id=").append(payfortRedundRequest.getFortId())
				.append("language=").append(payfortRedundRequest.getLanguage())
				.append("merchant_identifier=").append(payfortRedundRequest.getMerchantIdentifier())
				.append("merchant_reference=").append(payfortRedundRequest.getMerchantReference())
				.append("order_description=").append(payfortRedundRequest.getOrderDescription()).toString();

		signature = new StringBuilder().append(signatureHash).append(signatureRaw).append(signatureHash).toString();
		LOGGER.info("signature sequence:"+signature);

		String sha256hex =  org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
		LOGGER.info("sha256hex :"+sha256hex);

		return sha256hex;
	}


	/**
	 * @param storeId
	 * @param paymentMethod
	 */
	public PayfortConfiguration getPayfortConfDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if(storeId.equals("1")|| storeId.equals("3")) {
			setKsaDetails(storeId, paymentMethod, configuration);

		}else if(storeId.equals("7")|| storeId.equals("11")) {
			setUaeDetails(storeId, paymentMethod, configuration);

		}else if(storeId.equals("12")|| storeId.equals("13")) {
			setKwtDetails(storeId, paymentMethod, configuration);

		}else if(storeId.equals("15")|| storeId.equals("17")) {
			setqarDetails(storeId, paymentMethod, configuration);

		}else if(storeId.equals("19")|| storeId.equals("21")) {

			setBahDetails(storeId, paymentMethod, configuration);
		}else if(storeId.equals("23")|| storeId.equals("25")) {

			setOmnDetails(storeId, paymentMethod, configuration);
		}

		return configuration;
	}


	private void setKsaDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("1")  || storeId.equals("3"))&& paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {

			configuration.setAccessCode(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaAppleAccessCode());
			configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaHashAppleReqTokenphrase());
			configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaAppleMerchantIdentifier());
			configuration.setLanguage("en");
			configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortksaAmountMultiplier()));

		} else if ((storeId.equals("3")|| storeId.equals("1")) && (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {


			configuration.setAccessCode(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaCardAccessCode());
			configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortKsaCardMerchantIdentifier());
			if(storeId.equals("1")) {
				configuration.setLanguage("en");
			}else {
				configuration.setLanguage("ar");
			}

			configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getKsaCredentials().getPayfortksaAmountMultiplier()));

		}
	}


	private void setUaeDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("7") || storeId.equals("11")) && paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {

			configuration.setAccessCode(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeAppleAccessCode());
			configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeHashAppleReqTokenphrase());
			configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeAppleMerchantIdentifier());
			configuration.setLanguage("en");
			configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortuaeAmountMultiplier()));

		}
		if ((storeId.equals("7")|| storeId.equals("11")) && (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeCardAccessCode());
			configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortUaeCardMerchantIdentifier());
			if(storeId.equals("7")) {
				configuration.setLanguage("en");
			}else {
				configuration.setLanguage("ar");
			}
			configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getUaeCredentials().getPayfortuaeAmountMultiplier()));

		}
	}


	private void setKwtDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("12") || storeId.equals("13"))) {
			if(paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
					|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
					|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())) {
				configuration.setAccessCode(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtCardAccessCode());
				configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtHashCardReqTokenphrase());
				configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtCardMerchantIdentifier());
				configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtAmountMultiplier()));
			}
			else if (paymentMethod.equals(PaymentCodeENUM.APPLE_PAY.getValue())) {
				configuration.setAccessCode(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtApplePayAccessCode());
				configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtHashApplePayReqTokenPhrase());
				configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtCardMerchantIdentifier());
				configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getKwtCredentials().getPayfortKwtAmountMultiplier()));
			}
			if (storeId.equals("12")) {
				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}

		}
	}


	private void setqarDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("15") || storeId.equals("17"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatCardAccessCode());
			configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatCardMerchantIdentifier());
			configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getQatCredentials().getPayfortQatAmountMultiplier()));

			if (storeId.equals("15")) {
				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}

		}
	}


	private void setBahDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("19") || storeId.equals("21"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahCardAccessCode());
			configuration.setSignatureHash(Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahCardMerchantIdentifier());
			configuration.setMultiplier(Integer.parseInt(Constants.orderCredentials.getPayfort().getBahCredentials().getPayfortBahAmountMultiplier()));

			if (storeId.equals("19")) {

				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}
		}
	}

	private void setOmnDetails(String storeId, String paymentMethod, PayfortConfiguration configuration) {
		if ((storeId.equals("23") || storeId.equals("25"))
				&& (paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equals(PaymentCodeENUM.PAYFORT_FORT_CC.getValue()))) {

			configuration.setAccessCode(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnCardAccessCode());
			configuration.setSignatureHash(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnHashCardReqTokenphrase());
			configuration.setMerchantIdentifier(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnCardMerchantIdentifier());
			configuration.setMultiplier(Integer.parseInt(
					Constants.orderCredentials.getPayfort().getOmnCredentials().getPayfortOmnAmountMultiplier()));

			if (storeId.equals("23")) {

				configuration.setLanguage("en");

			} else {

				configuration.setLanguage("ar");
			}
		}
	}


	/**
	 * @param splitSalesOrder
	 * @param styliCreditAmount
	 */
	public void addSplitOrderStoreCredit(SplitSalesOrder splitSalesOrder, BigDecimal styliCreditAmount, boolean isGiftVoucher) {

		LOGGER.info("Styli Credit Amount: "+ styliCreditAmount);
		long currTime  = new Date().getTime() / 1000;
		BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
		bulkWalletUpdate.setEmail(splitSalesOrder.getCustomerEmail());
		bulkWalletUpdate.setCustomerId(splitSalesOrder.getCustomerId());
		bulkWalletUpdate.setStore_id(splitSalesOrder.getStoreId());
		bulkWalletUpdate.setAmount_to_be_refunded(styliCreditAmount);
		bulkWalletUpdate.setOrder_no(splitSalesOrder.getIncrementId());
//		bulkWalletUpdate.setComment("Pushed from java-service");
//		IMP:: do not change initiatedBy value
		bulkWalletUpdate.setInitiatedBy("java-api");
		bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
		bulkWalletUpdate.setJobId("JAVA Service");
		if(isGiftVoucher) {
			bulkWalletUpdate.setReturnableToBank(false);
		} else {
			bulkWalletUpdate.setReturnableToBank(true);
		}

		LOGGER.info("addStoreCredit => " + bulkWalletUpdate.toString());
		paymentDtfHelper.publishSCToKafka(bulkWalletUpdate);

	}




	public BigDecimal getCanceledItemQty(SplitSalesOrder splitSalesOrder) {

		BigDecimal totalCancelVal = new BigDecimal(0);

		List<SplitSalesOrderItem> cancelledItemList = splitSalesOrder.getSplitSalesOrderItems().stream()
				.filter(e->  null !=e.getProductType()
						&& !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
						&& null != e.getQtyCanceled() && e.getQtyCanceled().intValue() != 0)
				.collect(Collectors.toList());

		if(CollectionUtils.isNotEmpty(cancelledItemList)) {

			for(SplitSalesOrderItem item : cancelledItemList) {

				BigDecimal priceIcludingTax = item.getPriceInclTax();

				if(null != item.getDiscountAmount()) {


					BigDecimal qtyCancelled = item.getQtyCanceled();
					BigDecimal qtyOrdered = item.getQtyOrdered();
					BigDecimal Indivisualdiscount = item.getDiscountAmount().divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(4,
							RoundingMode.HALF_UP);
					BigDecimal cancelDiscountVal = BigDecimal.ZERO;
					if (null != qtyCancelled) {
						cancelDiscountVal = Indivisualdiscount.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP);

					}
					priceIcludingTax = priceIcludingTax.subtract(cancelDiscountVal);

				}

				priceIcludingTax = priceIcludingTax.multiply(item.getQtyCanceled());

				totalCancelVal = totalCancelVal.add(priceIcludingTax);

			}
		}

		return totalCancelVal;
	}

	/**
	 * @param splitSalesOrder
	 * @param totalAmountToRefund
	 * @param store
	 * @return
	 */
	public BigDecimal getCancelledStoreCredit(SplitSalesOrder splitSalesOrder,
											  Stores store, BigDecimal totalAmountToRefund,
											  BigDecimal beforeCancelledAmount, boolean isFullyCancellation
			, String paymentMethod) {

		BigDecimal refundStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal amastyStoreCredit = splitSalesOrder.getAmstorecreditAmount();

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 0) {
			refundStoreCreditAmount = totalAmountToRefund;

			return refundStoreCreditAmount;
		}
		if (null != beforeCancelledAmount && null != amastyStoreCredit
				&& (amastyStoreCredit.compareTo(beforeCancelledAmount) == 1)) {

			amastyStoreCredit = amastyStoreCredit.subtract(beforeCancelledAmount);
		}

		if (null != splitSalesOrder.getShippingAmount() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {

			totalAmountToRefund = totalAmountToRefund.add(splitSalesOrder.getShippingAmount());

		}
//	if (null != order.getCashOnDeliveryFee() && isFullyCancellation) {
//
//		totalAmountToRefund = totalAmountToRefund.add(order.getCashOnDeliveryFee());
//
//	}
		if (null != splitSalesOrder.getImportFee() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {

			totalAmountToRefund = totalAmountToRefund.add(splitSalesOrder.getImportFee());
		}

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 1) {

			totalAmountToRefund = amastyStoreCredit;
		}

		/** this condition sign is correct **/
		return totalAmountToRefund;
	}

	public BigDecimal getCancelledStoreCreditWithSplitCurrentOrderValue(SplitSalesOrder splitSalesOrder,
																   Stores store, BigDecimal totalAmountToRefund,
																   BigDecimal beforeCancelledAmount, boolean isFullyCancellation
			, String paymentMethod,BigDecimal currentOrderValue) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();

		BigDecimal refundStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal amastyStoreCredit = splitSalesOrder.getAmstorecreditAmount();

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 0) {
			refundStoreCreditAmount = totalAmountToRefund;

			return refundStoreCreditAmount;
		}
		if (null != beforeCancelledAmount && null != amastyStoreCredit
				&& (amastyStoreCredit.compareTo(beforeCancelledAmount) == 1)) {

			amastyStoreCredit = amastyStoreCredit.subtract(beforeCancelledAmount);
		}

		if (null != splitSalesOrder.getShippingAmount() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {

			totalAmountToRefund = totalAmountToRefund.add(splitSalesOrder.getShippingAmount());

		}
//	if (null != order.getCashOnDeliveryFee() && isFullyCancellation) {
//
//		totalAmountToRefund = totalAmountToRefund.add(order.getCashOnDeliveryFee());
//
//	}
		if (null != splitSalesOrder.getImportFee() && isFullyCancellation
				&& paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue())) {
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
			BigDecimal importFee = splitSalesOrder.getImportFee();
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
			LOGGER.info("In isFullyCancellation getCancelledStoreCreditWithCurrentOrderValue ::importFee " + importFee+ " :: currentOrderValue :: "+currentOrderValue
					+" nonKsaSellerCancellation:: "+nonKsaSellerCancellation +" isFullyCancellation:: "+isFullyCancellation);
			if(nonKsaSellerCancellation && null != currentOrderValue && currentOrderValue.compareTo(BigDecimal.ZERO) > 0) {
				importFee = calculateSplitImportFee(currentOrderValue, store);
				//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
				LOGGER.info("In isFullyCancellation After calculation ::importFee " + importFee);
				splitSalesOrder.setRefundedImportFee(splitSalesOrder.getRefundedImportFee().add(importFee));
				LOGGER.info("In isFullyCancellation after calculation ::order.getRefundedImportFee() " + splitSalesOrder.getRefundedImportFee());
			}

			totalAmountToRefund = totalAmountToRefund.add(importFee);
		}

		if (totalAmountToRefund.compareTo(amastyStoreCredit) == 1) {

			totalAmountToRefund = amastyStoreCredit;
		}
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		// If not fully cancellation, calculate import fee for non KSA countries
		LOGGER.info("In getCancelledStoreCreditWithCurrentOrderValue :: isFullyCancellation:: " +isFullyCancellation+
				" nonKsaSellerCancellation:: "+nonKsaSellerCancellation);
		if (nonKsaSellerCancellation && !isFullyCancellation && null!=currentOrderValue && currentOrderValue.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal currentImportFee = calculateSplitImportFee(currentOrderValue, store);
			BigDecimal newImportFee = findSplitNewImportFeeOfOrder(currentOrderValue, store, totalAmountToRefund);
			BigDecimal refundImportFee = currentImportFee.subtract(newImportFee).max(BigDecimal.ZERO);
			splitSalesOrder.setRefundedImportFee(splitSalesOrder.getRefundedImportFee().add(refundImportFee));
			LOGGER.info("In !isFullyCancellation getCancelledStoreCreditWithCurrentOrderValue :: Before totalAmountToRefund " +totalAmountToRefund);
			totalAmountToRefund = totalAmountToRefund.add(refundImportFee);
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
			LOGGER.info("In !isFullyCancellation After calculation ::currentImportFee " + currentImportFee+ " :: newImportFee :: "+newImportFee + " :: refundImportFee :: "+refundImportFee+" order.getRefundedImportFee():: " + splitSalesOrder.getRefundedImportFee()+" After totalAmountToRefund:: "+totalAmountToRefund);
		}
		/** this condition sign is correct **/
		return totalAmountToRefund;
	}


	public BigDecimal cancelPercentageCalculation(SplitSalesOrder splitSalesOrder, BigDecimal calcultedcancelAmount,
												  BigDecimal storeCreditAmount, CancelDetails details,boolean isFullyCancellation
			, String paymentMethod, BigDecimal totalVoucherToRefund) {
		// SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		boolean nonKsaSellerCancellation = Constants.orderCredentials.getNonKsaSellerCancellation();
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(splitSalesOrder.getStoreId())).findAny()
				.orElse(null);

		BigDecimal paidStoreCreditAmount = storeCreditAmount;
		BigDecimal divideVal = new BigDecimal(100);
		BigDecimal totalAmount =BigDecimal.ZERO;
		BigDecimal cancelDonationStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal refundShippingStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal refundImportStoreCreditAmount = BigDecimal.ZERO;
		//BigDecimal totalUsedStyliCoinsValue = BigDecimal.ZERO;
		details.setAmasyStoreCredit(BigDecimal.ZERO);

		BigDecimal sumOrderedCancelled = splitSalesOrder.getSplitSalesOrderItems().stream()
				.filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.map(x -> x.getQtyCanceled())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

//	 if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getInitialEasValueInCurrency()) {
//		totalUsedStyliCoinsValue = order.getSubSalesOrder().getInitialEasValueInCurrency();
//	 }

		if (sumOrderedCancelled.compareTo(BigDecimal.ZERO) == 0) {

			totalAmount = splitSalesOrder.getGrandTotal();

			if(null != paidStoreCreditAmount) {

				totalAmount = totalAmount.add(paidStoreCreditAmount);

			}
//			if(null != totalUsedStyliCoinsValue) {
//				totalAmount = totalAmount.add(totalUsedStyliCoinsValue);
//			}

			if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getDonationAmount()
					&& !(splitSalesOrder.getSplitSubSalesOrder().getDonationAmount().compareTo(BigDecimal.ZERO) == 0)
					&& isFullyCancellation && !(splitSalesOrder.getGrandTotal().compareTo(BigDecimal.ZERO) == 0)
					&& !paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())){

				if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {

					BigDecimal donationPercenatgeShare = splitSalesOrder.getSplitSubSalesOrder().getDonationAmount()
							.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
							.setScale(4, RoundingMode.HALF_UP);


					BigDecimal refundDonation = splitSalesOrder.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
							.multiply(donationPercenatgeShare)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					totalAmount = splitSalesOrder.getGrandTotal().subtract(refundDonation);

					BigDecimal cancelDonation = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
							.multiply(donationPercenatgeShare)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);

					cancelDonationStoreCreditAmount = cancelDonation;

					paidStoreCreditAmount = paidStoreCreditAmount.subtract(cancelDonationStoreCreditAmount);

					details.setTotalOnliineCancelAMount(totalAmount);
					details.setAmasyStoreCredit(paidStoreCreditAmount);

					return totalAmount;

				}
			}else if(null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)){
				if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getDonationAmount()) {

					paidStoreCreditAmount = paidStoreCreditAmount
							.subtract(splitSalesOrder.getSplitSubSalesOrder().getDonationAmount());
					details.setAmasyStoreCredit(paidStoreCreditAmount);
				}else {

					details.setAmasyStoreCredit(paidStoreCreditAmount);
				}
				details.setTotalOnliineCancelAMount(splitSalesOrder.getGrandTotal());

				return splitSalesOrder.getGrandTotal();

			}
			if(null !=splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getDonationAmount()) {

				totalAmount = totalAmount.subtract(splitSalesOrder.getSplitSubSalesOrder().getDonationAmount());
				details.setTotalOnliineCancelAMount(totalAmount);
			}else {

				details.setTotalOnliineCancelAMount(splitSalesOrder.getGrandTotal());
			}
			if(!totalVoucherToRefund.equals(BigDecimal.ZERO) && !isFullyCancellation) {
				BigDecimal amasyStoreCredit = details.getAmasyStoreCredit();
				amasyStoreCredit = amasyStoreCredit.add(totalVoucherToRefund);
				details.setAmasyStoreCredit(amasyStoreCredit);
				details.setGiftVoucher(true);
			}
			return totalAmount;
		}

		totalAmount = splitSalesOrder.getGrandTotal();
		if (null != paidStoreCreditAmount) {

			totalAmount = splitSalesOrder.getGrandTotal().add(paidStoreCreditAmount);

		}
//	if(null != totalUsedStyliCoinsValue) {
//		totalAmount = totalAmount.add(totalUsedStyliCoinsValue);
//	}


		BigDecimal cancelPercenatgeShare = calcultedcancelAmount
				.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
				.setScale(4, RoundingMode.HALF_UP);


		BigDecimal refundGrandTotal = splitSalesOrder.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
				.multiply(cancelPercenatgeShare)
				.setScale(2, RoundingMode.HALF_UP)
				.setScale(4,RoundingMode.HALF_UP);


		if(null !=splitSalesOrder.getImportFee() && ! (splitSalesOrder.getImportFee().compareTo(BigDecimal.ZERO) ==0)
				&& isFullyCancellation) {

			if (null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0)) {
				//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
				BigDecimal importFee = splitSalesOrder.getImportFee();
				LOGGER.info("In IF cancelPercentageCalculation ::importFee " + importFee);
				LOGGER.info("In IF cancelPercentageCalculation ::details.getCurrentOrderValue() " + details.getCurrentOrderValue()+"nonKsaSellerCancellation :: "+nonKsaSellerCancellation);
				if(nonKsaSellerCancellation && null!=details.getCurrentOrderValue() && details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
					importFee = calculateSplitImportFee(details.getCurrentOrderValue(), store);
					//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
					LOGGER.info("In IF cancelPercentageCalculation ::importFee " + importFee);
					splitSalesOrder.setRefundedImportFee(splitSalesOrder.getRefundedImportFee().add(importFee));
					LOGGER.info("In IF cancelPercentageCalculation ::order.getRefundedImportFee() " + splitSalesOrder.getRefundedImportFee());
				}
				BigDecimal cancelImportPercenatgeShare = splitSalesOrder.getImportFee()
						.divide(totalAmount, 6, RoundingMode.HALF_UP)
						.multiply(divideVal)
						.setScale(4, RoundingMode.HALF_UP);

				BigDecimal onlileImportReturnAmount = splitSalesOrder.getGrandTotal()
						.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelImportPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundGrandTotal = refundGrandTotal.add(onlileImportReturnAmount);

				BigDecimal returnImportCredit = paidStoreCreditAmount.
						divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelImportPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);

				refundImportStoreCreditAmount = returnImportCredit;
			} else {
				//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
				BigDecimal importFee = splitSalesOrder.getImportFee();
				LOGGER.info("In ELSE cancelPercentageCalculation ::importFee " + importFee+" details.getCurrentOrderValue() :: "+ details.getCurrentOrderValue()+" nonKsaSellerCancellation :: "+nonKsaSellerCancellation);
				if(nonKsaSellerCancellation && null != details.getCurrentOrderValue() && details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
					importFee = calculateSplitImportFee(details.getCurrentOrderValue(), store);
					//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
					LOGGER.info("In ELSE cancelPercentageCalculation ::after calculation importFee " + importFee);
					splitSalesOrder.setRefundedImportFee(splitSalesOrder.getRefundedImportFee().add(importFee));
					LOGGER.info("In ELSE cancelPercentageCalculation ::order.getRefundedImportFee() " + splitSalesOrder.getRefundedImportFee());
				}
				refundGrandTotal = refundGrandTotal.add(splitSalesOrder.getImportFee());

			}

		}if(null !=splitSalesOrder.getShippingAmount() && ! (splitSalesOrder.getShippingAmount().compareTo(BigDecimal.ZERO) ==0)
				&& isFullyCancellation) {



			if(null != paidStoreCreditAmount && !(paidStoreCreditAmount.compareTo(BigDecimal.ZERO) ==0)) {

				BigDecimal cancelShippingPercenatgeShare = splitSalesOrder.getShippingAmount()
						.divide(totalAmount, 6, RoundingMode.HALF_UP).multiply(divideVal)
						.setScale(4, RoundingMode.HALF_UP);

				BigDecimal  refundShippingAmount = splitSalesOrder.getGrandTotal().divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelShippingPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4,RoundingMode.HALF_UP);

				refundGrandTotal = refundGrandTotal.add(refundShippingAmount);

				BigDecimal returnShippingCredit= paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
						.multiply(cancelShippingPercenatgeShare)
						.setScale(2, RoundingMode.HALF_UP)
						.setScale(4,RoundingMode.HALF_UP);

				refundShippingStoreCreditAmount = returnShippingCredit	;
			}else {

				refundGrandTotal = refundGrandTotal.add(splitSalesOrder.getShippingAmount());
			}

		}


		if (null != paidStoreCreditAmount) {

			paidStoreCreditAmount = paidStoreCreditAmount.divide(divideVal, 4, RoundingMode.HALF_UP)
					.multiply(cancelPercenatgeShare)
					.setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP);

			paidStoreCreditAmount = paidStoreCreditAmount.add(refundShippingStoreCreditAmount)
					.add(refundImportStoreCreditAmount).subtract(cancelDonationStoreCreditAmount);

		}
		calcultedcancelAmount = refundGrandTotal;

		if(!totalVoucherToRefund.equals(BigDecimal.ZERO) && !isFullyCancellation) {
			if(Objects.isNull(paidStoreCreditAmount)) {
				paidStoreCreditAmount=BigDecimal.ZERO;
			}
			paidStoreCreditAmount = paidStoreCreditAmount.add(totalVoucherToRefund);
			details.setGiftVoucher(true);
		}

		details.setAmasyStoreCredit(paidStoreCreditAmount);

		if(null != paidStoreCreditAmount) {

			details.setAmastyBaseStoreCredit(paidStoreCreditAmount.multiply(splitSalesOrder.getStoreToBaseRate())
					.setScale(2, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP));
		}
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
		//If not fully cancellation, calculate import fee for non KSA countries
		LOGGER.info("In cancelPercentageCalculation ::nonKsaSellerCancellation " + nonKsaSellerCancellation+
				" :: isFullyCancellation ::"+isFullyCancellation);
		if (nonKsaSellerCancellation && !isFullyCancellation && null != details.getCurrentOrderValue() && details.getCurrentOrderValue().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal currentImportFee = calculateSplitImportFee(details.getCurrentOrderValue(), store);
			BigDecimal newImportFee = findSplitNewImportFeeOfOrder(details.getCurrentOrderValue(), store, calcultedcancelAmount);
			BigDecimal refundImportFee = currentImportFee.subtract(newImportFee).max(BigDecimal.ZERO);
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
			LOGGER.info("In !isFullyCancellation cancelPercentageCalculation ::currentImportFee " + currentImportFee+
					" :: newImportFee ::"+newImportFee+" :: refundImportFee ::"+refundImportFee);
			splitSalesOrder.setRefundedImportFee(splitSalesOrder.getRefundedImportFee().add(refundImportFee));
			LOGGER.info("In !isFullyCancellation cancelPercentageCalculation ::order.getRefundedImportFee() " + splitSalesOrder.getRefundedImportFee());

			calcultedcancelAmount = calcultedcancelAmount.add(refundImportFee);
		}



		details.setTotalOnliineCancelAMount(calcultedcancelAmount);

		// Subtract EAS Coin amount from refund for cancel by OMS or payment failed
// 	if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getEasCoins()) {
// 		calcultedcancelAmount = calcultedcancelAmount.subtract(order.getSubSalesOrder().getEasValueInCurrency());
//
//	}
		// Subtract EAS Coin amount from refund for cancel by OMS or payment failed

		return calcultedcancelAmount;
	}



	public static void main(String arg[]) {
	}

	/**
	 * responsible to prepare payfort refund payload
	 * @param configuration
	 * @param splitSalesOrder
	 * @param refundAmount
	 * @param fortId
	 * @return
	 */
	private PayfortOrderRefundPayLoad preparePayfortRefundRequest(PayfortConfiguration configuration, SplitSalesOrder splitSalesOrder,
																  String refundAmount, String fortId) {
		LOGGER.info("inside payfortRefundcall : preparePayfortRefundRequest ");
		PayfortOrderRefundPayLoad payfortRedundRequest = new PayfortOrderRefundPayLoad();
		payfortRedundRequest.setAccessCode(configuration.getAccessCode());
		payfortRedundRequest.setAmount(getConvertedAmount(refundAmount, configuration.getMultiplier()));
		payfortRedundRequest.setCommand(OrderConstants.REFUND_STRING);
		payfortRedundRequest.setCurrency(splitSalesOrder.getStoreCurrencyCode());
		payfortRedundRequest.setFortId(fortId);
		payfortRedundRequest.setLanguage(configuration.getLanguage());
		payfortRedundRequest.setMerchantIdentifier(configuration.getMerchantIdentifier());
		String incrementId = getSplitIncrementIdForApplePay(splitSalesOrder);
		payfortRedundRequest.setMerchantReference(incrementId);
		payfortRedundRequest.setOrderDescription(incrementId);
		payfortRedundRequest.setSignature(getSignature(payfortRedundRequest, configuration.getSignatureHash()));

		return payfortRedundRequest;
	}

	public RefundPaymentRespone payfortVoidAuthorizationcall(SplitSalesOrder splitSalesOrder, String fortId,
															 String paymentMethod) {

		RefundPaymentRespone response = new RefundPaymentRespone();
		PayfortReposne payfortResponse = null;
		if (OrderConstants.checkPaymentMethod(paymentMethod)) {

			PayfortConfiguration configuration = new PayfortConfiguration();
			getPayfortConfDetails(splitSalesOrder.getStoreId().toString(), paymentMethod, configuration);
			try {
				LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
			} catch (JsonProcessingException e) {
				LOGGER.error("error during write configuration:" + e.getMessage());
			}

			payfortResponse = triggerPayfortVoidAuthorizationRestApiCall(
					preparePayfortVoidAuthorizationRequest(configuration, splitSalesOrder, fortId), splitSalesOrder);

		}
		if (null != payfortResponse && !payfortResponse.isStatus()) {
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(payfortResponse.getMessage());
			return response;
		}

		response.setStatus(true);
		response.setStatusCode("200");
		return response;
	}

	/**
	 * responsible to prepare payfort void authorization request/pyaload
	 * @param configuration
	 * @param splitSalesOrder
	 * @param fortId
	 * @return
	 */
	private PayfortVoidAuthorizationRequest preparePayfortVoidAuthorizationRequest(PayfortConfiguration configuration,
																				   SplitSalesOrder splitSalesOrder, String fortId) {
		PayfortVoidAuthorizationRequest payfortVoidAuthorizationRequest = new PayfortVoidAuthorizationRequest();
		payfortVoidAuthorizationRequest.setAccessCode(configuration.getAccessCode());
		payfortVoidAuthorizationRequest.setCommand(OrderConstants.VOID_AUTHORIZATION);
		payfortVoidAuthorizationRequest.setFortId(fortId);
		payfortVoidAuthorizationRequest.setLanguage(configuration.getLanguage());
		payfortVoidAuthorizationRequest.setMerchantIdentifier(configuration.getMerchantIdentifier());
		String incrementId = getSplitIncrementIdForApplePay(splitSalesOrder);
		payfortVoidAuthorizationRequest.setMerchantReference(incrementId);
		payfortVoidAuthorizationRequest.setOrderDescription(incrementId);
		payfortVoidAuthorizationRequest.setSignature(
				getVoidAuthorizationSignature(payfortVoidAuthorizationRequest, configuration.getSignatureHash()));

		return payfortVoidAuthorizationRequest;
	}

	/**
	 * @param payfortRedundRequest
	 * @param signatureHash
	 * @return
	 */
	private String getVoidAuthorizationSignature(PayfortVoidAuthorizationRequest payfortRedundRequest,
												 String signatureHash) {

		String signature = null;
		String signatureRaw = new StringBuilder().append("access_code=").append(payfortRedundRequest.getAccessCode())
				.append("command=").append(OrderConstants.VOID_AUTHORIZATION)
				.append("fort_id=").append(payfortRedundRequest.getFortId())
				.append("language=").append(payfortRedundRequest.getLanguage())
				.append("merchant_identifier=").append(payfortRedundRequest.getMerchantIdentifier())
				.append("merchant_reference=").append(payfortRedundRequest.getMerchantReference())
				.append("order_description=").append(payfortRedundRequest.getOrderDescription()).toString();

		signature = new StringBuilder().append(signatureHash).append(signatureRaw).append(signatureHash).toString();
		LOGGER.info("Void authorization signature sequence:" + signature);
		String sha256hex = org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
		LOGGER.info("Void Authorization sha256hex :" + sha256hex);

		return sha256hex;
	}

	/**
	 * responsible to call payfort payment api by passing command as VOID_AUTHORIZATION
	 * @param payfortVoidAuthorizationRequest
	 * @param splitSalesOrder
	 * @return
	 */
	private PayfortReposne triggerPayfortVoidAuthorizationRestApiCall(
			PayfortVoidAuthorizationRequest payfortVoidAuthorizationRequest, SplitSalesOrder splitSalesOrder) {

		PayfortReposne payfortResponse = new PayfortReposne();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity<PayfortVoidAuthorizationRequest> requestBody = new HttpEntity<>(payfortVoidAuthorizationRequest,
				requestHeaders);
		String url = Constants.orderCredentials.getPayfort().getPayfortRefundBaseUrl() + "/FortAPI/paymentApi";

		try {
			LOGGER.info("payfort url for void authorization: " + url);
			LOGGER.info("Request body: " + mapper.writeValueAsString(requestBody));

			ResponseEntity<PayfortVoidAuthorizationResponse> response = restTemplate.exchange(url, HttpMethod.POST,
					requestBody, PayfortVoidAuthorizationResponse.class);

			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				PayfortVoidAuthorizationResponse responseBody = response.getBody();

				LOGGER.info("Payfort VOID_AUTHORIZATION response Body:" + mapper.writeValueAsString(responseBody));
				if (null != responseBody) {
					if (null != responseBody.getStatus() && responseBody.getStatus()
							.equals(OrderConstants.PAYFORT_VOID_AUTHORIZATION_SUCCESS_STATUS)) {
						payfortResponse.setStatus(true);
						payfortResponse.setMessage(responseBody.getResponseMessage());
					} else {
						payfortResponse.setStatus(false);
						payfortResponse.setMessage(responseBody.getResponseMessage());
					}
				} else {
					payfortResponse.setStatus(false);
					payfortResponse.setMessage("Response body is null");
				}
			}
		} catch (RestClientException | JsonProcessingException e2) {
			LOGGER.error("exception occoured during void authorization process:" + splitSalesOrder.getIncrementId() + " "
					+ e2.getMessage());
			payfortResponse.setStatus(false);
			payfortResponse.setMessage(e2.getMessage());
		}

		return payfortResponse;
	}

	public String getSplitIncrementIdForApplePay(SplitSalesOrder splitSalesOrder) {
		// Get Main order increment id for apple pay order if order is splitted for apple pay and edit increment id is present in split order
		if (null == splitSalesOrder.getSalesOrder()) {
			return null;
		}
		SalesOrder order = splitSalesOrder.getSalesOrder();
		String incrementId = order.getIncrementId();
		List<Stores> stores = Constants.getStoresList();
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst()
				.orElse(null);
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);
		if (Objects.nonNull(store) && store.isEnableApplepayholdOrder() && Objects.nonNull(order.getEditIncrement())
				&& null != salesOrderPayment && null != salesOrderPayment.getMethod()
				&& salesOrderPayment.getMethod().equalsIgnoreCase("apple_pay")) {
			incrementId = order.getEditIncrement();
		}
		return incrementId;
	}
	public BigDecimal findSplitCurrentOrderValue(SplitSalesOrder splitSalesOrder) {
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
		LOGGER.info("In findCurrentOrderValue order increment Id: " + splitSalesOrder.getIncrementId());
		BigDecimal currentOrderValue = splitSalesOrder.getSubtotal().add(splitSalesOrder.getShippingAmount())
				.add(splitSalesOrder.getCashOnDeliveryFee()).add(splitSalesOrder.getDiscountAmount()).setScale(2, RoundingMode.HALF_UP);
		for (SplitSalesOrderItem item : splitSalesOrder.getSplitSalesOrderItems()) {
			if (!item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE) && item.getQtyCanceled().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal itemPrice = item.getPriceInclTax().multiply(item.getQtyCanceled());
				currentOrderValue = currentOrderValue.subtract(itemPrice);
			}
		}

		LOGGER.info("In findCurrentOrderValue : currentOrderValue :: " + currentOrderValue);
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- END

		return currentOrderValue;
	}

	public BigDecimal calculateSplitImportFee(BigDecimal orderValue, Stores store) {
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
		LOGGER.info("In calculateImportFee order value: " + orderValue);
		BigDecimal minValue = store.getMinimumDutiesAmount();
		BigDecimal importLow = store.getImportFeePercentage();
		BigDecimal importHigh = store.getImportMaxFeePercentage();
		BigDecimal dutyPercentage = store.getCustomDutiesPercentage();
		BigDecimal dutyAmount = new BigDecimal(0);
		BigDecimal feePercentage = importLow;
		if(orderValue.compareTo(minValue) > 0 ) {
			feePercentage = importHigh;
			dutyAmount = orderValue.multiply(dutyPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		}
		LOGGER.info("In calculateImportFee : dutyAmount :: " + dutyAmount);
		BigDecimal importFeeAmount = (orderValue.add(dutyAmount)).multiply(feePercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		LOGGER.info("In calculateImportFee : importFeeAmount :: " + importFeeAmount);
		BigDecimal totalFee = importFeeAmount.add(dutyAmount);
		LOGGER.info("In calculateImportFee : importFeeAmount+dutyAmount :: final import fee" + totalFee);

		LOGGER.info("In calculateImportFee : import fee :: " + totalFee);
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- END
		return totalFee.setScale(2, RoundingMode.HALF_UP);
	}
	private BigDecimal findSplitNewImportFeeOfOrder(BigDecimal currentOrderValue, Stores store, BigDecimal totalAmountToRefund) {
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- START
		LOGGER.info("In findNewImportFeeOfOrder ::currentOrderValue " + currentOrderValue);
		LOGGER.info("In findNewImportFeeOfOrder ::totalAmountToRefund " + totalAmountToRefund);
		BigDecimal newOrderValue = currentOrderValue.subtract(totalAmountToRefund);
		BigDecimal newImportFee = calculateSplitImportFee(newOrderValue, store);
		LOGGER.info("In findNewImportFeeOfOrder ::newImportFee " + newImportFee);
		//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries  ---- END

		return newImportFee;
	}

}


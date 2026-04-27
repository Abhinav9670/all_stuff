package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.db.product.pojo.PayfortSecondReturnStatus;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.model.sales.VaultPaymentToken;
import org.styli.services.order.pojo.CustomerCardDetails;
import org.styli.services.order.pojo.OrderSms;
import org.styli.services.order.pojo.PayforDtfRequest;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.request.CitySearchAddressMapperRequest;
import org.styli.services.order.pojo.response.AddressMapperResponse;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.VaultPaymentTokenRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.KafkaServiceImpl;
import org.styli.services.order.service.impl.SalesOrderCancelServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.UtilityConstant;

/**
 * @author
 *
 */
@Component
public class PaymentDtfHelper {

	private static final String TRANSACTION_ID = " Transaction ID:";

	private static final String PAY_FORT_RESPONSE_CODE_IS = "PayFort Response code is:";

	private static final String THE_AUTHORIZED_AMOUNT_IS = "The Authorized Amount is ";
	private static final String AND_MESSAGE = "& Message ";

	private static final Log LOGGER = LogFactory.getLog(PaymentDtfHelper.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;


	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	VaultPaymentTokenRepository vaultPaymentTokenRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;


	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Autowired
	ExternalQuoteHelper externalQuoteHelper;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	@Lazy
	KafkaServiceImpl kafkaService;

	@Autowired
	AmastyStoreCreditRepository amastyStoreCreditRepository;

	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${auth.internal.jwt.token}")
	private String authInternalJwtToken;
	
	@Value("${address.mapper.url}")
	private String addressMapperUrl;

	@Autowired
	@Lazy
	private EASServiceImpl eASServiceImpl;
	
	@Autowired
	OrderHelperV2 orderHelperV2;

	public ResponseEntity<String> payfortDtfcall(SalesOrder order, @Valid PayforDtfRequest request,
			Map<String, String> httpRequestHeadrs) {
		
		
		String orderId = order.getIncrementId();

		LOGGER.info("PaymentDtfHelper -> payfortDtfcall enter into if...Order details: " + orderId);
		LOGGER.info("PaymentDtfHelper -> payfortDtfcall getCommand: " + request.getCommand());
		LOGGER.info("PaymentDtfHelper -> payfortDtfcall getStatus: " + request.getStatus());
		LOGGER.info("PaymentDtfHelper -> payfortDtfcall getResponseCode: " + request.getResponseCode());
		if (null != order && StringUtils.isNotBlank(request.getStatus())
				&& request.getStatus().equalsIgnoreCase(OrderConstants.PAYFORT_SUCCESS_ORDER_STATUS)
				&& StringUtils.isNotBlank(request.getResponseCode())
				&& request.getResponseCode().equalsIgnoreCase(OrderConstants.PAYFORT_SUCCESS_RESPONSE_CODE)
				&& null != request.getCommand()
				&& request.getCommand().equalsIgnoreCase(OrderConstants.PAYFORT_DTF_COMMMAND_NAME)) {
			dtfSuccess(order, request);

		} else if (null != order && StringUtils.isNotBlank(request.getStatus())
				&& (request.getCommand().equalsIgnoreCase(OrderConstants.AUTHORIZATION)
						&& request.getStatus()
								.equalsIgnoreCase(OrderConstants.PAYFORT_AUTHORIZATION_DTF_SUCCESS_ORDER_STATUS)
						&& request.getResponseCode()
								.equalsIgnoreCase(OrderConstants.PAYFORT_AUTHORIZATION_DTF_RESPONSE_CODE))) {
			dtfSuccess(order, request);

		} else if (null != order && StringUtils.isNotBlank(request.getResponseCode())
				&& request.getResponseCode().equalsIgnoreCase(OrderConstants.PAYFORT_HOLD_RESPONSE_CODE)
				&& request.getStatus().equalsIgnoreCase(OrderConstants.PAYFORT_HOLD_ORDER_STATUS)) {
			LOGGER.info("PaymentDtfHelper -> payfortDtfcall enter into else if  for Order :" + orderId);
			dtfOnhold(order, request);
		} else {

			LOGGER.info("dtf fail condition: Order details: " + orderId);
			if (null != request && null != order
					&& order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {

				String command = request.getCommand();

				if (StringUtils.isNotBlank(command) && command.equals("REFUND")) {

					return ResponseEntity.badRequest().build();
				}
				String message = THE_AUTHORIZED_AMOUNT_IS + request.getCurrency() + " " + request.getAmount() + ""
						+ TRANSACTION_ID + request.getFortId();

				updateOrderStatusHistory(order, message, OrderConstants.ORDER2, order.getStatus());

				String updateMessage = PAY_FORT_RESPONSE_CODE_IS + request.getResponseCode() + "" + AND_MESSAGE
						+ request.getResponseMessage();

				updateOrderStatusHistory(order, updateMessage, OrderConstants.ORDER2, order.getStatus());

			}

			if (null != order && order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {

				SubSalesOrder subSalesOrder = order.getSubSalesOrder();
				SalesOrderPayment salesOrderPayment = null;

				subSalesOrder.setDtfLock(1);

				salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

				setPaymentDetails(salesOrderPayment, request);

				/**
				 * To allow older clients to be able to place order without otp verification.
				 * This part can be removed after setting minimum app version to the threshold
				 * version.
				 */
			
				if(order != null && order.getSubSalesOrder() != null && order.getSubSalesOrder().getRetryPayment()!=null){
					
					LOGGER.info("order retry payment:" + order.getSubSalesOrder().getRetryPayment() + "Order details: "
						+ orderId);
				}
				Long clientVersion = Constants.decodeAppVersion(order.getAppVersion());
				Long thresholdVersion = Constants.decodeAppVersion(Constants.getPaymentFailedThresholdVersion());
				String source = order.getSubSalesOrder().getClientSource();
				LOGGER.info("thresholdVersion" + thresholdVersion + " for Order " + orderId);
				LOGGER.info("clientVersion" + clientVersion + " for Order " + orderId);
				LOGGER.info("source" + source + " for Order " + orderId);
				List<Stores> stores = Constants.getStoresList();
				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
						.findAny().orElse(null);

				if (clientVersion != null && thresholdVersion != null && clientVersion < thresholdVersion && source != null && !source.equals("oldmsite")) {
					setFailedOrder(order, request, httpRequestHeadrs.get(Constants.deviceId));
				} else if (StringUtils.isNotBlank(source) && !UtilityConstant.APPSOURCELIST.contains(source)) {

					LOGGER.info("confirmed fail because order is not placed by APP for Order " + orderId);
					setFailedOrder(order, request, httpRequestHeadrs.get(Constants.deviceId));
				} else if (clientVersion != null && thresholdVersion != null && clientVersion >= thresholdVersion
						&& null != store && !store.isHoldOrder() && !source.equals("oldmsite")) {

					LOGGER.info("order hold is false so going to fail for Order " + orderId);
					setFailedOrder(order, request, httpRequestHeadrs.get(Constants.deviceId));
				} else if ("APPLE_PAY".equalsIgnoreCase(request.getDigitalWallet())
						&& !store.isEnableApplepayholdOrder()) {
					LOGGER.info("Order payment done by : ApplePay and not hold. Marking order as failed for Order "
							+ orderId);
					setFailedOrder(order, request, httpRequestHeadrs.get(Constants.deviceId));
				} else if (OrderConstants.ORDER_STATUS_PAYMENT_HOLD.equalsIgnoreCase(order.getStatus())) {
					LOGGER.info("Order is still on hold : Order details: " + orderId);
					setPayfortPaymentFailedOrder(order, request);
				}
			} else {
				return ResponseEntity.badRequest().build();
			}
		}
		return ResponseEntity.ok().build();
	}
	
	/**
	 *  Move payfort payment failed order to failed state
	 * @param order
	 * @param request
	 */
	private void setPayfortPaymentFailedOrder(SalesOrder order, PayforDtfRequest request) {
		updateFailedOrder(order, request);
		releaseStoreCredit(order);
		orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_DTF_FAILED_CALL);
		releaseCoupon(order);
		releaseCoins(order);
	}

	/**
	 *  Move payfort payment failed order to failed state and enable Quote to reorder.
	 * @param order
	 * @param request
	 */
	private void setFailedOrder(SalesOrder order, PayforDtfRequest request, String deviceId) {
		String orderId = order.getIncrementId();
		LOGGER.info("order is going to failed : Order details: " + orderId);
		updateFailedOrder(order, request);
		releaseStoreCredit(order);
		orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_DTF_FAILED_CALL);
		releaseCoupon(order);
		enableQuote(order, deviceId);
		releaseCoins(order);
	}

	public void releaseStoreCredit(SalesOrder order) {
		if (null != order.getAmstorecreditBaseAmount()) {
			releaseStoreCredit(order, order.getAmstorecreditAmount());
			String stylicreditMsg = OrderConstants.STYLI_CREDIT_FAILED_MSG + order.getBaseCurrencyCode() + ""
					+ order.getAmstorecreditBaseAmount();
			updateOrderStatusHistory(order, stylicreditMsg, OrderConstants.ORDER2, order.getStatus());
		}
		order.getSubSalesOrder().setRetryPayment(0);
		order.setRetryPayment(0);
		salesOrderRepository.saveAndFlush(order);
	}

	public void releaseCoins(SalesOrder order) {
	// EAS to be implement for payment fail.If Earn Service flag ON!.
		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafka(order, 0.0);
		}
	}
	
	private void updateFailedOrder(SalesOrder order, PayforDtfRequest request) {
		order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
		order.setState(OrderConstants.FAILED_ORDER_STATUS);


		updateOrderStatusHistory(order, OrderConstants.PAYFORT_CANCEL_MESSAGE,
				OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());

		String updateMessage = PAY_FORT_RESPONSE_CODE_IS + request.getResponseCode() + "" + AND_MESSAGE
				+ request.getResponseMessage();

		if(order.getSubSalesOrder()!= null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSubSalesOrder().getShukranLocked().equals(0)){
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
					.orElse(null);
			commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(), order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), order.getSubSalesOrder().getQuoteId(), false, order, store, "Refund Shukran Coins Burned On Payment Failure", "");
		}
		updateOrderStatusHistory(order, updateMessage, OrderConstants.ORDER2, order.getStatus());

		saveOrderGrid(order, OrderConstants.FAILED_ORDER_STATUS);
	}

	public void failStatusOnwards(SalesOrder order, String deviceId) {
		releaseCoupon(order);
		enableQuote(order, deviceId);
	}

	public void failStatusOnwardsSplit(SplitSalesOrder splitSalesOrder, String deviceId) {
		releaseCouponSplit(splitSalesOrder);
		enableQuoteSplit(splitSalesOrder, deviceId);
	}

	public void releaseCoupon(SalesOrder order) {
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())) {

			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
					.findAny().orElse(null);
			salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, order, false, false);
		}
	}

	public void releaseCouponSplit(SplitSalesOrder splitSalesOrder) {
		if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(splitSalesOrder.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(splitSalesOrder.getSplitSubSalesOrder().getExternalCouponRedemptionTrackingId())) {

			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(splitSalesOrder.getStoreId()))
					.findAny().orElse(null);
			salesOrderCancelServiceImpl.cancelReedmeExternalCouponForSplitOrder(store, splitSalesOrder, false, false);
		}
	}
	public void enableQuote(SalesOrder order, String deviceId) {
		String tokenHeader = authInternalJwtToken;
		String quoteId = order.getSubSalesOrder().getExternalQuoteId().toString();
		externalQuoteHelper.enableExternalQuote(quoteId, order.getStoreId(), tokenHeader, deviceId);
	}

	public void enableQuoteSplit(SplitSalesOrder splitSalesOrder, String deviceId) {
		String tokenHeader = authInternalJwtToken;
		String quoteId = splitSalesOrder.getSplitSubSalesOrder().getExternalQuoteId().toString();
		externalQuoteHelper.enableExternalQuote(quoteId, splitSalesOrder.getStoreId(), tokenHeader, deviceId);
	}

	private void dtfOnhold(SalesOrder order, PayforDtfRequest request) {

		if (null != order.getStatus()
				&& (order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
						|| order.getStatus().equalsIgnoreCase(OrderConstants.ORDER_STATUS_PAYMENT_HOLD))) {

			SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

			setPaymentDetails(salesOrderPayment, request);

			order.setStatus(OrderConstants.ORDER_STATUS_PAYMENT_HOLD);
			order.setState(OrderConstants.ORDER_STATE_PAYMENT_HOLD);
//			if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned()!=null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSubSalesOrder().getShukranLocked() == 0){
//				SubSalesOrder subSalesOrder = order.getSubSalesOrder();
//				List<Stores> stores = Constants.getStoresList();
//				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
//						.orElse(null);
//				commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(),order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), order.getSubSalesOrder().getQuoteId(), false, order, store);
//				subSalesOrder.setShukranLocked(1);
//				subSalesOrderRepository.saveAndFlush(subSalesOrder);
//			}

			updateOrderStatusHistory(order, OrderConstants.PAYFORT_HOLD_MESSAGE,
					OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
			saveOrderGrid(order, OrderConstants.ORDER_STATUS_PAYMENT_HOLD);
			salesOrderRepository.saveAndFlush(order);

			BigDecimal amount = Objects.nonNull(salesOrderPayment) ? salesOrderPayment.getAmountOrdered()
					: BigDecimal.ZERO;
			String message = THE_AUTHORIZED_AMOUNT_IS + request.getCurrency() + " " + amount + TRANSACTION_ID
					+ request.getFortId();

			updateOrderStatusHistory(order, message, OrderConstants.ORDER2, order.getStatus());

			String updateMessage = PAY_FORT_RESPONSE_CODE_IS + request.getResponseCode() + "" + AND_MESSAGE
					+ request.getResponseMessage();

			updateOrderStatusHistory(order, updateMessage, OrderConstants.ORDER2, order.getStatus());
		}
	}

	private void dtfSuccess(SalesOrder order, PayforDtfRequest request) {
		SubSalesOrder subSalesOrder = order.getSubSalesOrder();
		SalesOrderPayment salesOrderPayment = null;
		String orderId = order.getIncrementId();
		
		LOGGER.info("inside DTF success : Order details: " + orderId);

		if (null != order.getStatus()
				&& (order.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)
						|| order.getStatus().equalsIgnoreCase(OrderConstants.ORDER_STATUS_PAYMENT_HOLD))) {
			LOGGER.info("validation match : Order details: " + orderId);
			validateResponseSignature(request);

			subSalesOrder.setDtfLock(1);
			if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {

				salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
			}
			
			if(Objects.isNull(salesOrderPayment)) {
				LOGGER.info("Order payment not found! : Order details: " + orderId);
				return;
			}
				

			setPaymentDetails(salesOrderPayment, request);

			if (StringUtils.isNotBlank(request.getRememberMe()) && request.getRememberMe().equals("YES")
					&& null != order.getCustomerId()) {

				createCardToken(request, order);

			}
			LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in dtfSuccess" + orderId);
			order.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
			order.setState(OrderConstants.PROCESSING_ORDER_STATUS);
			order.setExtOrderId("0");
			order.getSubSalesOrder().setRetryPayment(0);
			if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0) {
				try {
					List<Stores> stores = Constants.getStoresList();
					Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
							.findAny().orElse(null);
					String lockResponse = commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(), String.valueOf(order.getSubSalesOrder().getTotalShukranCoinsBurned().intValue()), order.getSubSalesOrder().getQuoteId(), true, order, store, "Locking Points On Retry Payment Success", "On Payment Success DTF");

					if (StringUtils.isNotBlank(lockResponse) && StringUtils.isNotEmpty(lockResponse) && lockResponse.equalsIgnoreCase("api passed")) {
						LOGGER.info("in retry payment locking 1 ");
						order.getSubSalesOrder().setShukranLocked(0);
					}
				}catch(Exception e){
					LOGGER.info("error on locking points"+ e.getMessage());
				}
			}
			order.setRetryPayment(0);
			
			updateOrderStatusHistory(order, OrderConstants.PAYFORT_SUCCESS_MESSAGE,
					OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
			saveOrderGrid(order, OrderConstants.PROCESSING_ORDER_STATUS, salesOrderPayment.getMethod());
			
			if(null != subSalesOrder.getRetryPayment() && subSalesOrder.getRetryPayment() == 1) {
				setEstimatedDeliveryTimeForRetryPayment(order);
				subSalesOrder.setRetryPayment(0);
				order.setRetryPayment(0);
			}

			salesOrderRepository.saveAndFlush(order);
			
			LOGGER.info("dtf successfully saved! : Order details: " + orderId);

			if (null != salesOrderPayment) {

				String message = THE_AUTHORIZED_AMOUNT_IS + request.getCurrency() + " "
						+ salesOrderPayment.getAmountOrdered() + TRANSACTION_ID + request.getFortId();

				updateOrderStatusHistory(order, message, OrderConstants.ORDER2, order.getStatus());
			}

			String updateMessage = PAY_FORT_RESPONSE_CODE_IS + request.getResponseCode() + "" + AND_MESSAGE
					+ request.getResponseMessage();

			updateOrderStatusHistory(order, updateMessage, OrderConstants.ORDER2, order.getStatus());

			OrderSms ordersms = new OrderSms();
			ordersms.setOrderid(order.getEntityId().toString());

			publishToKafka(ordersms);
			orderHelper.updateStatusHistory(order, false, true, false, false, false);
			LOGGER.info(" dtf the end! ");
			
			String modeOfPayment = salesOrderPayment.getMethod();
			if (modeOfPayment != null) {
						LOGGER.info("Order " +order.getIncrementId() + "modeOfPayment is : " +modeOfPayment);
						orderHelperV2.publishPreferredPaymentIfValid(modeOfPayment, order);
			}

		}
	}

	public void setEstimatedDeliveryTimeForRetryPayment(SalesOrder order) {
		
		for (SalesOrderAddress shippingAddress : order.getSalesOrderAddress()) {
			if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {
				

			    LOGGER.info("inside setEstimatedDeliveryTimeForRetryPayment");

			    HttpHeaders requestHeaders = new HttpHeaders();
			    requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			    requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			    requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

			    CitySearchAddressMapperRequest payload = new CitySearchAddressMapperRequest();

			    payload.setCountry(shippingAddress.getCountryId());
			    payload.setCitySearchKey(shippingAddress.getCity());
			    payload.setRegionId(shippingAddress.getRegionId());
			    
			    HttpEntity<CitySearchAddressMapperRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

			    String url = "{url}/api/address/city/etd";
			    Map<String, Object> parameters = new HashMap<>();
			    parameters.put("url", addressMapperUrl);

			    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

			    LOGGER.info("Address Mapper Coupon URL:" + builder.buildAndExpand(parameters).toUri());
			    LOGGER.info(" Request Body " + requestBody);
			    try {

			      ResponseEntity<AddressMapperResponse> response = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
			          HttpMethod.POST, requestBody, AddressMapperResponse.class);

			      if (response.getStatusCode() == HttpStatus.OK) {

			        AddressMapperResponse body = response.getBody();
			        if (body != null && body.getStatusCode().equals("200")) {
			          LOGGER.info("Address Mapper respose: " + response.getBody());

			          String estimatedDate = body.getResponse().getEstimatedDate();

			          if (StringUtils.isNotBlank(estimatedDate)) {

			            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
			            Date date = formatter.parse(estimatedDate);
			            Timestamp timeStampDate = new Timestamp(date.getTime());

			            order.setEstimatedDeliveryTime(timeStampDate);
			            LOGGER.info("estimated date set done");
			          }
			        }
			      }

			    } catch (RestClientException e) {

			      LOGGER.error("Exception occurred  during REST call: " + e.getMessage());

			    } catch (ParseException e) {
			      LOGGER.error("Exception :" + e);
			    }
			  
			}
		}
		
	}



	private void validateResponseSignature(@Valid PayforDtfRequest request) {

		String signature = null;
		String signatureRaw = new StringBuilder().append("access_code=").append(request.getMerchantIdentifier())
				.append("acquirer_response_code=").append(request.getRememberMe()).append("authorization_code=")
				.append(request.getAuthorizationCode()).append("currency=").append(request.getCurrency())
				.append("customer_ip=").append(request.getCustomerIp()).append("eci=").append(request.getEci())
				.append("language=").append(request.getLanguage()).append("merchant_identifier=")
				.append(request.getMerchantIdentifier()).append("merchant_reference=")
				.append(request.getMerchantReference()).append("order_description=")
				.append(request.getOrderDescription()).append("remember_me=").append(request.getRememberMe())
				.append("response_code=").append(request.getResponseCode()).append("token_name=")
				.append(request.getTokenName()).toString();

		signature = new StringBuilder()
				.append(Constants.orderCredentials.getPayfort().getKsaCredentials()
						.getPayfortKsaHashCardReqTokenphrase())
				.append(signatureRaw).append(Constants.orderCredentials.getPayfort().getKsaCredentials()
						.getPayfortKsaHashCardResTokenphrase())
				.toString();
		LOGGER.info("dtf signature sequence: " + signature);

		String sha256hex = org.apache.commons.codec.digest.DigestUtils.sha256Hex(signature);
		LOGGER.info("created : " + sha256hex);

		LOGGER.info("dtf signare response : " + request.getSignature());

	}

	/**
	 * @param salesOrderPayment
	 * @param order
	 */
	private void setPaymentDetails(SalesOrderPayment salesOrderPayment, PayforDtfRequest request) {

		LOGGER.info("payment set");
		if (null != salesOrderPayment) {

			try {
				String jsonInString = mapper.writeValueAsString(request);
				salesOrderPayment.setAdditionalInformation(jsonInString);
				salesOrderPayment.setLastTransId(request.getFortId());
				salesOrderPayment.setCcTransId(request.getFortId());
				if (StringUtils.isNotBlank(request.getDigitalWallet())
						&& !request.getDigitalWallet().equalsIgnoreCase(salesOrderPayment.getMethod())) {
					LOGGER.info("Manual payment method updated in dtf call. Method : " + request.getDigitalWallet()
							+ " Order Id " + salesOrderPayment.getSalesOrder().getIncrementId());
					salesOrderPayment.setMethod(request.getDigitalWallet().toLowerCase());
				}
			} catch (JsonProcessingException e) {

				LOGGER.error("json parse exception during order payment");
			}
		}
	}

	private void createCardToken(PayforDtfRequest request, SalesOrder order) {

		VaultPaymentToken vaultPaymentToken = new VaultPaymentToken();

		Timestamp expireAt = null;

		String expireDate = request.getExpireDate();

		if (StringUtils.isNotBlank(expireDate) && expireDate.length() == 4) {

			String year = expireDate.substring(0, 2);

			year = "20" + year;

			Integer currentyear = Integer.valueOf(Calendar.getInstance().get(Calendar.YEAR));
			Integer cardExpireyear = Integer.parseInt(year);

			Integer yrDifference = cardExpireyear.intValue() - currentyear.intValue();

			String month = expireDate.substring(2, 4);

			Date currentDate = new Date();

			Calendar c = Calendar.getInstance();
			c.setTime(currentDate);

			c.add(Calendar.MONTH, Integer.parseInt(month));

			c.add(Calendar.YEAR, yrDifference);

			Date estimatedDate = c.getTime();

			expireAt = new Timestamp(estimatedDate.getTime());
			vaultPaymentToken.setExpiresAt(expireAt);

		}

		vaultPaymentToken.setGatewayToken(request.getTokenName());
		vaultPaymentToken.setActive(1);
		vaultPaymentToken.setVisible(1);
		vaultPaymentToken.setPaymentMethodCode(request.getPaymentOption());
		vaultPaymentToken.setType(OrderConstants.PAYMENT_TYPE_CARD);
		vaultPaymentToken.setPaymentMethodCode(OrderConstants.PAYMENT_MODE_CODE);
		vaultPaymentToken.setCreatedAt(new Timestamp(new Date().getTime()));
		vaultPaymentToken.setCustomerId(order.getCustomerId());
		vaultPaymentToken.setPublicHash(request.getSignature());

		CustomerCardDetails cardDetails = new CustomerCardDetails();
		cardDetails.setType(OrderConstants.checkPaymentCard(request.getPaymentOption()));
		if (null != expireAt) {
			cardDetails.setExpirationDate(expireAt.toString());

		}
		cardDetails.setStoreId(order.getStoreId().toString());
		cardDetails.setCardBin(StringUtils.left(request.getCardNumber(), 6));

		if (StringUtils.isNotBlank(request.getCardNumber())) {
			String lastFourDigits = request.getCardNumber().substring(request.getCardNumber().length() - 4);

			cardDetails.setMaskedCC(lastFourDigits);

		}

		/** Generate details JSON string from java object **/
		try {
			String details = mapper.writeValueAsString(cardDetails);
			vaultPaymentToken.setDetails(details);
		} catch (JsonProcessingException e) {

			LOGGER.error("exception ocoured due to save card");
		}

		LOGGER.info("vaultPaymentToken " + vaultPaymentToken);

		vaultPaymentTokenRepository.saveAndFlush(vaultPaymentToken);

	}

	/**
	 * @param request
	 * @param order
	 */
	public void updateOrderStatusHistory(SalesOrder order, String message, String entity, String status) {
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


	public void updateOrderStatusHistoryWithSplitSalesOrder(SplitSalesOrder splitSalesOrder, String message, String entity, String status) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();

		LOGGER.info("History set");

		sh.setParentId(splitSalesOrder.getSalesOrder().getEntityId());
		sh.setSplitSalesOrder(splitSalesOrder);
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(1);
		sh.setComment("Order updated with message: " + message);
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setStatus(status);
		sh.setEntityName(entity);

		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}



	/**
	 * @param order
	 * @param message
	 */
	public void saveOrderGrid(SalesOrder order, String message) {

		SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());

		salesorderGrid.setStatus(message);
		LOGGER.info(" payment dtf Save to GRID : "+message +" for increment Id "+ order.getIncrementId());
		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}
	
	public void saveOrderGrid(SalesOrder order, String message, String paymentMethod) {
		SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());
		salesorderGrid.setStatus(message);
		if (StringUtils.isNotEmpty(paymentMethod))
			salesorderGrid.setPaymentMethod(paymentMethod);
		LOGGER.info("in Dtf helper Save to GRID : "+message +" for increment Id "+ order.getIncrementId());
		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}

	/**
	 * Publish orderId to Kafka
	 * 
	 * @param orderId OrderSms
	 */
	public void publishToKafka(OrderSms orderId) {
		kafkaService.publishToKafka(orderId);
	}

	/**
	 * Publish wallet update to Kafka
	 * 
	 * @param update BulkWalletUpdate
	 */
	public void publishSCToKafka(BulkWalletUpdate update) {
		kafkaService.publishSCToKafka(update);
	}

	/**
	 * Publish wallet update to Kafka for Braze
	 * 
	 * @param update BulkWalletUpdate
	 */
	public void publishSCToKafkaForBraze(BulkWalletUpdate update) {
		kafkaService.publishSCToKafkaForBraze(update);
	}

	public void releaseStoreCredit(SalesOrder order, BigDecimal storeCreditAmount) {

		List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
				.findByCustomerId(order.getCustomerId());
		AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
		if (amastyStoreCredit != null) {
			long currTime = new Date().getTime() / 1000;
			BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
			bulkWalletUpdate.setEmail(order.getCustomerEmail());
			bulkWalletUpdate.setCustomerId(order.getCustomerId());
			bulkWalletUpdate.setStore_id(order.getStoreId());
			bulkWalletUpdate.setAmount_to_be_refunded(storeCreditAmount);
			bulkWalletUpdate.setOrder_no(order.getIncrementId());
			bulkWalletUpdate.setInitiatedBy("java-api");
			bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
			bulkWalletUpdate.setJobId("JAVA Service");

			LOGGER.info("releaseStoreCredit => " + bulkWalletUpdate.toString());
			kafkaService.publishSCToKafka(bulkWalletUpdate);
		}
	}

	public void releaseStoreCreditSplit(SplitSalesOrder splitSalesOrder, BigDecimal storeCreditAmount) {

		List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
				.findByCustomerId(splitSalesOrder.getCustomerId());
		AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
		if (amastyStoreCredit != null) {
			long currTime = new Date().getTime() / 1000;
			BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
			bulkWalletUpdate.setEmail(splitSalesOrder.getCustomerEmail());
			bulkWalletUpdate.setCustomerId(splitSalesOrder.getCustomerId());
			bulkWalletUpdate.setStore_id(splitSalesOrder.getStoreId());
			bulkWalletUpdate.setAmount_to_be_refunded(storeCreditAmount);
			bulkWalletUpdate.setOrder_no(splitSalesOrder.getIncrementId());
			bulkWalletUpdate.setInitiatedBy("java-api");
			bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
			bulkWalletUpdate.setJobId("JAVA Service");

			LOGGER.info("releaseStoreCredit => " + bulkWalletUpdate.toString());
			kafkaService.publishSCToKafka(bulkWalletUpdate);
		}
	}


	public void updateStatusForSecondReturn(@Valid PayforDtfRequest request) {

		LOGGER.info("inside updateStatusForSecondReturn" + request.getMerchantReference());

		String Splitarray[] = request.getMerchantReference().split("R");

		if (ArrayUtils.isNotEmpty(Splitarray)) {

			LOGGER.info("arrayis not empty and increment id is:" + Splitarray[0]);

			String orderIncremengtId = Splitarray[0];
			PayfortSecondReturnStatus statusObj = new PayfortSecondReturnStatus();
			List<PayfortSecondReturnStatus> statusList = new ArrayList<>();
			LOGGER.info("orderIncremengtId:" + orderIncremengtId);
			if (org.apache.commons.lang.StringUtils.isNotEmpty(orderIncremengtId)) {

				SalesOrder order = salesOrderRepository.findByIncrementId(orderIncremengtId);
				String statusValue = null;

				if (order != null && null == order.getDiscountDescription()) {

					LOGGER.info("order not having  list data:" + order.getDiscountDescription());

					statusValue = getStatusValue(request);

					statusObj.setId(request.getMerchantReference());
					statusObj.setStatus(statusValue);

					statusList.add(statusObj);
					try {
						LOGGER.info("order save data"+mapper.writeValueAsString(statusList));
						order.setProtectCode(mapper.writeValueAsString(statusList));

					} catch (Exception e) {

						LOGGER.error("exception during convert second return to string");
					}

					salesOrderRepository.saveAndFlush(order);
					LOGGER.info("order not having  list data:");
				} else if (order != null) {

					LOGGER.info("order already having  list data:" + order.getDiscountDescription());

					try {
						PayfortSecondReturnStatus[] statusData = mapper.readValue(order.getDiscountDescription(),
								PayfortSecondReturnStatus[].class);

						statusList = Arrays.asList(statusData);

						statusObj.setId(request.getMerchantReference());
						statusObj.setStatus(getStatusValue(request));

						statusList.add(statusObj);
						
						order.setProtectCode(mapper.writeValueAsString(statusList));

						salesOrderRepository.saveAndFlush(order);
						

					} catch (Exception e) {
						
						LOGGER.error("exception during convert  duringget second return status to string"+e.getMessage());
					}
				}

			}
		}
	}

	private String getStatusValue(PayforDtfRequest request) {
		String statusValue;
		if (StringUtils.isNotBlank(request.getStatus())
				&& request.getStatus().equalsIgnoreCase(OrderConstants.PAYFORT_SUCCESS_ORDER_STATUS)
				&& StringUtils.isNotBlank(request.getResponseCode())
				&& request.getResponseCode().equalsIgnoreCase(OrderConstants.PAYFORT_SUCCESS_RESPONSE_CODE)
				&& null != request.getCommand()
				&& request.getCommand().equalsIgnoreCase(OrderConstants.PAYFORT_DTF_COMMMAND_NAME)) {

			// status.setReturnIncPayfortId(merchantReference);
			statusValue = "success";
		} else if (StringUtils.isNotBlank(request.getResponseCode())
				&& request.getResponseCode().equalsIgnoreCase(OrderConstants.PAYFORT_HOLD_RESPONSE_CODE)
				&& request.getStatus().equalsIgnoreCase(OrderConstants.PAYFORT_HOLD_ORDER_STATUS)) {

			statusValue = "hold";

		} else {
			statusValue = "failed";
		}
		
		return statusValue;
	}
	
}

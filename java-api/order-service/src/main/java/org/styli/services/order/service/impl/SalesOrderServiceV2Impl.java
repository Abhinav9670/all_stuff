

package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.exception.BadRequestException;
import org.styli.services.order.helper.*;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.redis.OtpBucketObject;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.braze.BrazePushAttribute;
import org.styli.services.order.pojo.eas.EASQuoteSpend;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.CreateReplicaQuoteV4Request;
import org.styli.services.order.pojo.order.OTSOrderRequest;
import org.styli.services.order.pojo.order.StoreCredit;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;
import org.styli.services.order.pojo.quote.response.QuoteUpdateDTOV2;
import org.styli.services.order.pojo.quote.response.QuoteV7Response;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.ProductStatusRequest;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.ProductStatusRequestV2;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.pojo.response.Order.CreateOrderResponse;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.CustomerService;
import org.styli.services.order.service.PaymentService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.gson.Gson;

/**
 * @author Umesh, 24/09/2020
 * @project product-service
 */

@Component
public class SalesOrderServiceV2Impl implements SalesOrderServiceV2 {

	private static final String EXCEPTION = "Exception";
	private static final String LOG_ORDER_CREATION_PREFIX = "Order creation ";
	private static final Log LOGGER = LogFactory.getLog(SalesOrderServiceV2Impl.class);
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String LOG_STORE_ID_SUFFIX = ", StoreId: ";

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	ExternalQuoteHelper externalQuoteHelper;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	OrderHelperV2 orderHelperV2;

	@Autowired
	OrderHelperV3 orderHelperV3;

	@Autowired
	CustomerService customerService;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	AmastyStoreCreditRepository amastyStoreCreditRepository;

	@Autowired
	SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	private JwtValidator validator;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@Autowired
	KafkaBrazeHelper kafkaBrazeHelper;

	@Autowired
	ConfigService configService;

	@Autowired
	@Lazy
	EASServiceImpl easService;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	SubSalesOrderRepository subSalesOrderRepository;

	@Autowired
	private PaymentUtility paymentUtility;

	@Autowired
	private ProxyOrderRepository proxyOrderRepository;

	@Autowired
	@Qualifier("tabbyPaymentServiceImpl")
	@Lazy
	private PaymentService paymentService;

	@Autowired
	@Lazy
	EASServiceImpl eASServiceImpl;

	@Autowired
	RedisHelper redisHelper;

	@Autowired
	@Lazy
	KafkaServiceImpl kafkaService;

	@Autowired
	PubSubServiceImpl pubSubServiceImpl;

	@Value("${pubsub.topic.split.order}")
	private String splitOrderTopic;

	@Value("${pubsub.topic.split.order.tracking}")
	private String splitOrderTrackingTopic;

	@Autowired
	private SplitSubSalesOrderRepository splitSubSalesOrderRepository;

	@Autowired
	private LmdCommissionConfigRepository lmdCommissionConfigRepository;


  @Override
  @Transactional
  public CreateOrderResponseDTO convertQuoteToOrderV2(CreateOrderRequestV2 request,
													  String tokenHeader,
													  String incrementId,
													  String xSource,
													  Map<String, String> requestHeader,
													  String xHeaderToken,
													  String xClientVersion,
													  String customerIp,
                                                      String deviceId) throws NotFoundException {
	      final String xHeaderCloudMethod = (StringUtils.isNotEmpty(requestHeader.get("X-Header-Cloud-Method")))
				  ? requestHeader.get("X-Header-Cloud-Method").toLowerCase() : "";
		  int source = request.getSource() == null ? 1 : request.getSource();

		  CreateOrderResponseDTO resp = new CreateOrderResponseDTO();

		  SalesOrder order = null;

		  if (StringUtils.isBlank(incrementId)) {
			  resp.setStatus(false);
			  resp.setStatusCode("206");
			  resp.setStatusMsg("Increment ID not found!");
			  return resp;
		  }

		  if (request.getQuoteId() == null || request.getStoreId() == null) {
			  resp.setStatus(true);
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
			  resp.setStatusMsg(Constants.STORE_NOT_FOUND_MSG);
			  return resp;
		  }

    GetOrderConsulValues orderCredentials = Constants.orderCredentials;

    boolean isProxyOrderCreated = false;
    QuoteDTO quote = null;
    ProxyOrder proxyOrder = null;
	if (Objects.nonNull(request.getPaymentId()) && !request.isProxy) {
		
		LOGGER.info("payment id and prox is false");
		
		proxyOrder = orderHelperV2.findProxyOrderByPaymentId(request.getPaymentId());
		if (Objects.nonNull(proxyOrder)) {
			LOGGER.info("proxyOrder is not null");
			incrementId = proxyOrder.getIncrementId();
			isProxyOrderCreated = true;
			try {
				quote = mapper.readValue(proxyOrder.getQuote(), QuoteDTO.class);
			} catch (JsonProcessingException e) {
				resp.setStatus(false);
				resp.setStatusCode("203");
				resp.setStatusMsg("Error: There is an issue found in Quote!");
				LOGGER.error("Error in parsing quote object for Proxy Order. Error : " + e);
				return resp;
			}
		}
	} else {
		CreateOrderResponseDTO quoteResponse = getQuote(request, tokenHeader, xSource, requestHeader, xHeaderToken,
				xClientVersion, xHeaderCloudMethod, deviceId);
		if (quoteResponse.getStatus().booleanValue()) {
			quote = quoteResponse.getQuoteResponse().getResponse();
		} else {
			return quoteResponse;
		}
	}
	if (null == quote) {

			  resp.setStatus(false);
			  resp.setStatusCode("212");
			  resp.setStatusMsg("Quote object not found!");

			  return resp;
	}


      List<CatalogProductEntityForQuoteDTO> products = quote.getProducts();
      for (CatalogProductEntityForQuoteDTO product : products) {
		  BigDecimal poPrice = BigDecimal.ZERO;
		  try {
          String soldBy = product.getSoldBy();
		  	String shortDesc = product.getShortDescription();
			LmdCommissionConfig lmdCommissionConfig = resolveCommissionPercentFromConfig(soldBy, shortDesc);

			LOGGER.info("Calculating PO Price for product sku=" + product.getSku()
					  + ", soldBy=" + soldBy + ", short_description=" + shortDesc
					  + ", lmdCommissionConfig=" + lmdCommissionConfig);
		      if (null!=soldBy && null!=lmdCommissionConfig) {
				   BigDecimal commissionPercent = resolveStyliCommission(lmdCommissionConfig);
          			// default poPrice
					BigDecimal rrPrice = BigDecimal.ZERO;
					BigDecimal rowTotalInclTax = BigDecimal.ZERO;
					BigDecimal taxAmount = BigDecimal.ZERO;
					// row total inclTax
					String rowTotalInclTaxStr = product.getRowTotalInclTax();
					LOGGER.info("rowTotalInclTax: " + rowTotalInclTaxStr);
					if (StringUtils.isNotBlank(rowTotalInclTaxStr)) {
						try {
							rowTotalInclTax = new BigDecimal(rowTotalInclTaxStr);
							rrPrice = rowTotalInclTax;
							//Apply ntd discount if any
							if (null != lmdCommissionConfig.getNtdDiscount() && lmdCommissionConfig.getNtdDiscount().compareTo(BigDecimal.ZERO) >= 0) {
								BigDecimal discountAmount = null!=product.getDiscountAmount() ? new BigDecimal(product.getDiscountAmount()) : BigDecimal.ZERO;
								LOGGER.info("Calculating NTD discount. Discount Amount: " + discountAmount + ", NTD Discount Percent: " + lmdCommissionConfig.getNtdDiscount() + "%, rrPrice"+ rrPrice);
								BigDecimal ntdDiscount = discountAmount.multiply(lmdCommissionConfig.getNtdDiscount().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
								rrPrice = rrPrice.subtract(ntdDiscount);
								LOGGER.info("Applied NTD discount of " + ntdDiscount + " on rrPrice. New rrPrice: " + rrPrice);
							}
							// Remove tax: net = gross / (1 + taxPercent/100)
							BigDecimal taxPercent = product.getTaxPercent() != null ? new BigDecimal(product.getTaxPercent()) : BigDecimal.ZERO;
							LOGGER.info("In convertQuoteTOOrderV2 rrPrice with tax: " + rrPrice+", taxPercent: "+taxPercent);
							if (taxPercent.compareTo(BigDecimal.ZERO) > 0) {
								BigDecimal taxFactor = BigDecimal.ONE.add(
										taxPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
								);
								rrPrice = rrPrice.divide(taxFactor, 2, RoundingMode.HALF_UP);
							} else {
								LOGGER.info("No tax percent , falling to default tax percent from config: " + lmdCommissionConfig.getDefaultTaxPercentage());
								taxPercent = null!=lmdCommissionConfig.getDefaultTaxPercentage() ? lmdCommissionConfig.getDefaultTaxPercentage() : BigDecimal.ZERO;
								LOGGER.info("Tax percent  from config: " + taxPercent);
								BigDecimal taxFactor = BigDecimal.ONE.add(
										taxPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
								);
								rrPrice = rrPrice.divide(taxFactor, 2, RoundingMode.HALF_UP);
							}
							LOGGER.info("In convertQuoteTOOrderV2 rrPrice pre tax: " + rrPrice);
							// Find RR price per quantity
							int qty = safeInt(product.getQuantity(), 1);
							if (qty > 1) {
								rrPrice = rrPrice.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
							}
						} catch (NumberFormatException ignore) {
							// log and continue with rrPrice as 0
							LOGGER.error("NumberFormatException in finding rrPrice for product sku: " + product.getSku() + ", rowTotalInclTax: " + rowTotalInclTaxStr + ", taxAmount: " + product.getTaxAmount() + ", discountAmount: " + product.getDiscountAmount(), ignore);
						}
					}
					LOGGER.info("rrPrice (price after discount, with out tax): " + rrPrice);
					LOGGER.info("styli commissionPercent : " + commissionPercent);
					BigDecimal styliCommission = rrPrice.multiply(commissionPercent != null ? commissionPercent.divide(BigDecimal.valueOf(100)) : BigDecimal.ZERO);
					LOGGER.info("styli commission : " + styliCommission);
					poPrice = rrPrice.subtract(styliCommission);
                    // FIX: Final safety check - ensure poPrice is never negative
                    if (poPrice.compareTo(BigDecimal.ZERO) < 0) {
                        LOGGER.warn("Calculated poPrice is negative (" + poPrice + "). Setting to 0.");
                        poPrice = BigDecimal.ZERO;
                    }
                    LOGGER.info("po price: " + poPrice);
					// append poPrice back to product (quote) so it can be used downstream
          	}
			  product.setPoPrice(poPrice.toPlainString());
		  } catch (Exception ex) {
			  LOGGER.error("Exception in finding po price" + ex.getMessage(),ex);
			  product.setPoPrice("0");
		  }
      }


		// Check mobile verification status in Redis before allowing order creation (similar to registration)
		// This check ensures mobile number is verified in Redis before order creation
		// Uses same flags as registration: isSignUpOtpEnabled, version check, source check, and store validation
		// Only perform this check for guest users
	  String verifiedPhoneNumber = null;
	  boolean shouldCleanupVerification = false;
	  // Only perform guest mobile verification if NOT proxy
	  LOGGER.info("V2 createOrder: Input : incrementId=" + incrementId + ", isProxyOrderCreated=" + isProxyOrderCreated);
	  // Check only if proxy order created or not
	  boolean isProxyFlow = isProxyOrderCreated;
	  if (!isProxyFlow) {
		  GuestVerificationResult verificationResult = checkGuestPhoneVerification(
				  quote.getCustomerId(),
				  quote.getShippingAddress(),
				  quote.getCustomerPhoneNumber(),
				  request,
				  xClientVersion,
				  xSource,
				  "V2"
		  );
		  if (!verificationResult.success) {
			  return verificationResult.errorResponse;
		  }
		  verifiedPhoneNumber = verificationResult.verifiedPhoneNumber;
		  shouldCleanupVerification = verificationResult.shouldCleanupVerification;
	  }

		  String paymentMethod = quote.getSelectedPaymentMethod();
	      if((StringUtils.isBlank(paymentMethod) || StringUtils.isEmpty(paymentMethod)) && request.getIsApplePay() != null && request.getIsApplePay().equals(1)){
			  LOGGER.info("Inside Apple Pay Method");
			  paymentMethod= OrderConstants.APPLE_PAY;
		  }
          LOGGER.info("payment method to set "+ paymentMethod);
		  BigDecimal amastyBaseStoreBalance = new BigDecimal(0);

		  BigDecimal restrictAMount = OrderConstants.checkPaymentMethodAmountRestn(paymentMethod);
		  BigDecimal grandTotal = new BigDecimal(quote.getGrandTotal());
		  LOGGER.info("grand total amount:" + grandTotal + " threshold amount:" + restrictAMount);
	  if (quote.getIsAvailableShukranChanged()) {

		  resp.setStatus(true);
		  resp.setStatusCode("226");
		  resp.setStatusMsg("Shukran Available Points Changed");
		  return resp;

	  }

		  if (quote.getTotalShukranBurn() != null && quote.getTotalShukranBurn().compareTo(BigDecimal.ZERO) > 0) {

			  boolean isAvailablePointsInvalid = quote.getShukranAvailablePoint() == null
					  || quote.getShukranAvailablePoint().compareTo(BigDecimal.ZERO) <= 0
					  || quote.getShukranAvailablePoint().compareTo(quote.getTotalShukranBurn()) < 0;
			  if (isAvailablePointsInvalid) {
				  resp.setStatus(true);
				  resp.setStatusCode("226");
				  resp.setStatusMsg("Shukran Available Points Are Less Than Burned Points");
				  return resp;
			  }
		  }



		  if (null != restrictAMount && grandTotal.intValue() > restrictAMount.intValue()) {

			  resp.setStatus(false);
			  resp.setStatusCode("222");
			  resp.setStatusMsg("Grand total amount is more than threshold value!");
			  return resp;
		  }

	  	LOGGER.info("V2 createOrder: OTP validation gate. incrementId=" + incrementId + ", isProxyOrderCreated=" + isProxyOrderCreated);
	 	 //	API-1941 starts
		  if (!isProxyFlow &&  isOtpValidationRequired(xClientVersion, quote, requestHeader) && (!validateOtp(request, quote))) {
			  resp.setStatus(false);
			  resp.setStatusCode("217");
			  resp.setStatusMsg("Otp validation failed!");
			  return resp;

		  }

		  //	API-1941 ends

		  /*
		   * Store credit check If applied store credit for quote is less than or equal to
		   * the available store credit of customer return wilt failure response, else
		   * continue
		   */
		  if (quote.getStoreCreditApplied() != null) {
			  BigDecimal appliedStoreCredit = new BigDecimal(quote.getStoreCreditApplied());
			  if (appliedStoreCredit != null && appliedStoreCredit.compareTo(BigDecimal.ZERO) > 0) {

				  List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
						  .findByCustomerId(Integer.parseInt(quote.getCustomerId()));
				  AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;

				  if (amastyStoreCredit != null && null != amastyStoreCredit.getStoreCredit()) {

					  LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
					  amastyBaseStoreBalance = amastyStoreCredit.getStoreCredit();
					  BigDecimal convertedStoreCredit = amastyStoreCredit.getStoreCredit().divide(store.getCurrencyConversionRate(),
									  4, RoundingMode.HALF_UP)
							  .setScale(2, RoundingMode.HALF_UP)
							  .setScale(4, RoundingMode.HALF_UP);
					  int result = appliedStoreCredit.compareTo(convertedStoreCredit);
					  if (result > 0) {

						  resp.setStatus(false);
						  resp.setStatusCode("205");
						  resp.setStatusMsg("Store Credit not applied! for quote: " + request.getQuoteId());
						  return resp;

					  }
				  }
			  }
		  }

		  if (CollectionUtils.isEmpty(quote.getProducts()) && !request.isRetryPaymentReplica()) {
			  resp.setStatus(false);
			  resp.setStatusCode("204");
			  resp.setStatusMsg("Error: Quote did not have products! for quote: " + request.getQuoteId());
			  return resp;
		  }

		  if (OrderConstants.checkBNPLPaymentMethods(paymentMethod) && request.isProxy && (Objects
				  .isNull(quote.getBnplSessionAmount()) || Objects.isNull(quote.getGrandTotal())
				  || Double.valueOf(quote.getBnplSessionAmount()).compareTo(Double.valueOf(quote.getGrandTotal())) != 0)) {
			  LOGGER.error("Order cration failed as the bnpl getting mismatch. Quote ID:" + quote.getQuoteId()
					  + " BNPL Amount : " + quote.getBnplSessionAmount() + " Order Amount : " + quote.getGrandTotal());
			  resp.setStatus(false);
			  resp.setStatusCode("203");
			  resp.setStatusMsg("Order amount and bnpl session amount mismatch. Quote ID : " + request.getQuoteId());
			  return resp;
		  }


		  if (paymentMethod.equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_TYPE_FREE)) {
			  if (!quote.isFreeOrder() && (quote.getStoreCreditApplied() == null || quote.getStoreCreditApplied().equals("")
					  || quote.getStoreCreditApplied().equals("0"))) {
				  resp.setStatus(false);
				  resp.setStatusCode("215");
				  resp.setStatusMsg("Store Credit not applied! for quote: " + request.getQuoteId());
				  return resp;
			  }

		  } else if (paymentMethod.equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_TYPE_FREE)
				  && null != quote.getGrandTotal() && !quote.getGrandTotal().equals("0") && quote.isFreeOrder()) {

			  resp.setStatus(false);
			  resp.setStatusCode("203");
			  resp.setStatusMsg("free coupon is not applied! for quote: " + request.getQuoteId());
			  return resp;
		  }


		  AddressObject shippingAddress = quote.getShippingAddress();
		  if (shippingAddress == null) {
			  resp.setStatus(false);
			  resp.setStatusCode("206");
			  resp.setStatusMsg("Error: Address Not Found For Quote: " + request.getQuoteId());
			  return resp;
		  }

		  if (StringUtils.isBlank(shippingAddress.getArea())) {
			  resp.setStatus(false);
			  resp.setStatusCode("207");
			  resp.setStatusMsg(Constants.ERROR_MSG);
			  ErrorType error = new ErrorType();
			  error.setErrorCode("207");
			  error.setErrorMessage("Error:Shipping Address Area Missed For Quote: " + request.getQuoteId());
			  resp.setError(error);
			  return resp;
		  }

		  if (StringUtils.isBlank(shippingAddress.getCity())) {
			  resp.setStatus(false);
			  resp.setStatusCode("208");
			  resp.setStatusMsg(Constants.ERROR_MSG);
			  ErrorType error = new ErrorType();
			  error.setErrorCode("208");
			  error.setErrorMessage("Error:Shipping Address City Missed For Quote: " + request.getQuoteId());
			  resp.setError(error);
			  return resp;
		  }

	  if (proxyOrder == null && !quote.isRetryPayment() && Constants.orderCredentials != null && quote.getTotalShukranBurn() != null && quote.getTotalShukranBurn().compareTo(BigDecimal.ZERO) > 0 && StringUtils.isNotBlank(quote.getProfileId()) && StringUtils.isNotEmpty(quote.getProfileId()) && store.getIsShukranEnable() && quote.getShukranLinkFlag() && StringUtils.isNotBlank(quote.getShukranCardNumber()) && StringUtils.isNotEmpty(quote.getShukranCardNumber())) {

		  String cartId = StringUtils.isNotBlank(Constants.orderCredentials.getShukranCartIdPrefix()) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranCartIdPrefix()) ? Constants.orderCredentials.getShukranCartIdPrefix() + quote.getQuoteId() : quote.getQuoteId();
		  String lockUnlockResponse = commonService.lockUnlockShukranData(quote.getProfileId(), quote.getTotalShukranBurn().toString(), cartId, true, null, store, "", "Create Order Api");
		  if (StringUtils.isEmpty(lockUnlockResponse) || StringUtils.isBlank(lockUnlockResponse) || !lockUnlockResponse.equals("api passed")) {
			  resp.setStatus(false);
			  resp.setStatusCode("243");
			  resp.setStatusMsg("Shukran Locking Failed!");
			  return resp;
		  }
	  }
	ProductInventoryRes invResponse = null;
	if (!isProxyOrderCreated && !request.isRetryPaymentReplica()) {
		List<String> skus = new ArrayList<>();
		ProductStatusRequest productStatusReq = new ProductStatusRequest();
		productStatusReq.setStoreId(request.getStoreId());
		productStatusReq.setSkus(skus);
		for (CatalogProductEntityForQuoteDTO product : quote.getProducts()) {
			skus.add(product.getSku());
		}
		invResponse = getInventoryQty(productStatusReq);
		CreateOrderResponseDTO checkInventoryRes = checkInventory(request, quote, invResponse);
		if (Objects.nonNull(checkInventoryRes) && !checkInventoryRes.getStatus().booleanValue())
			return checkInventoryRes;
	}
	  if (request.isProxy()) {
		SalesOrder salesOrd = orderHelperV2.createOrderObjectToPersist(quote, paymentMethod, store, incrementId,
				request.getIpAddress(), source, request.getMerchantReference(), request.getAppVersion(), xSource,
				customerIp, deviceId, request.isRetryPaymentReplica(), request.isPayfortAuthorized());
		orderHelperV2.extractOrderItemsFromQuote(quote, salesOrd, store,invResponse);
		request.setXSource(xSource);

		if(StringUtils.isNotEmpty(customerIp) && StringUtils.isNotBlank(customerIp) && customerIp.length()<45) {
			request.setCustomerIp(customerIp);
		}

		CreateOrderResponseDTO proxyOrderRes = createOrderProxy(request, salesOrd, quote, paymentMethod, store);
		if (proxyOrderRes.getStatus().booleanValue())
			externalQuoteHelper.disableExternalQuote(quote, tokenHeader, xHeaderToken, requestHeader.get(Constants.deviceId));
		return proxyOrderRes;
	}

		  boolean firstOrderByCustomer = orderHelper.checkFirstCreateOrder(quote.getCustomerEmail());
		  boolean firstTimeOrderCall = false;
		  try {

			  LOGGER.info("insertion started");
			  // EAS_CHANGES added in below functions
			  order = orderHelperV2.createOrderObjectToPersist(quote,
					  paymentMethod,
					  store,
					  incrementId,
					  request.getIpAddress(),
					  source,
					  request.getMerchantReference(),
					  request.getAppVersion(),
					  xSource,
					  customerIp,
					  deviceId,
					  request.isRetryPaymentReplica(),
					  request.isPayfortAuthorized());

			  orderHelperV2.saveUuidOfUserInOrder(order, tokenHeader);

			  LOGGER.info("sales_order  table insertion done!");

			  orderHelperV2.createOrderAddresses(quote, order);
			  LOGGER.info("sales_order_address table insertion done!");

			  orderHelperV2.createOrderPayment(quote, order, paymentMethod, store);
			  LOGGER.info("sales_order_payment table insertion done!");

			  if (null != order.getSubSalesOrder()
					  && null == order.getSubSalesOrder().getRetryPayment()) {
				  customerService.deductStoreCreditV2(quote, order, store, amastyBaseStoreBalance);
				  firstTimeOrderCall = true;
				  LOGGER.info("deduct store credit done!");
			  }

			  orderHelperV2.createOrderItems(quote, order, store, request.isRetryPaymentReplica(),invResponse);
			  LOGGER.info("sales_order child table insertion done!");

			  orderHelperV2.createOrderGrid(quote, order, paymentMethod, shippingAddress, store, source,
					  request.getAppVersion());
			  LOGGER.info("sales_order_grid table insertion done!");

			  if (!request.isRetryPaymentReplica()) {
				  orderHelper.createOrderStatusHistory(order, null);
				  LOGGER.info("order_status_history table insertion done!");
			  }

			  LOGGER.info("isProxyOrderCreated fore block:" + isProxyOrderCreated);
			  if (!isProxyOrderCreated && firstTimeOrderCall) {
				  orderHelper.blockInventory(order);
				  LOGGER.info("block inventory call done!");
			  }


			  CreateOrderResponse orderResponse = new CreateOrderResponse();
			  orderResponse.setIncrementId(order.getIncrementId());

			  orderResponse.setFirstOrderByCustomer(firstOrderByCustomer);

			  if (null != order.getEntityId()) {
				  orderResponse.setOrderId(order.getEntityId().toString());
			  }
			  if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalQuoteId()) {
				  orderResponse.setQuoteId(order.getSubSalesOrder().getExternalQuoteId().toString());
			  }
			  if (null != order.getGrandTotal()) {
				  orderResponse.setGrandTotal(order.getGrandTotal().toString());
			  }
			  if (StringUtils.isNotBlank(quote.getTabbyPaymentId())) {
				  orderResponse.setTabbyPaymentId(quote.getTabbyPaymentId());
			  }
			  if (null != order.getPayfortAuthorized()) {
				  LOGGER.info("SalesOrderServiceV2Impl - convertQuoteToOrderV2 - order.getPayfortAuthorized:" + order.getPayfortAuthorized());
				  orderResponse.setPayfortAuthorized(String.valueOf(order.getPayfortAuthorized()));
				  LOGGER.info("SalesOrderServiceV2Impl - convertQuoteToOrderV2 - orderResponse: " + orderResponse);
			  }

			  if (StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData()) && !isProxyOrderCreated
					  && firstTimeOrderCall) {
				  orderHelperV2.reedmeExternalCoupon(quote, store, order, false);
			  } else {
				  updateCouponReedme(order, proxyOrder);
			  }
			  if (null != paymentMethod && (paymentMethod.equals(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
					  || paymentMethod.equals(PaymentCodeENUM.FREE.getValue()))) {

				  orderHelper.updateStatusHistory(order, true, true, false, false, false);

				  resp.setCodOrder(true);
			  } else {

				  orderHelper.updateStatusHistory(order, true, false, true, false, false);
				  LOGGER.info("update status  done!");
			  }
			  // Kafka push to EAS service for order created. If Earn service flag ON!.
			  if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
				  eASServiceImpl.publishSaleOrderToKafka(order);
			  }
			  if (!isProxyOrderCreated) {
				  externalQuoteHelper.disableExternalQuote(quote, tokenHeader, xHeaderToken, requestHeader.get(Constants.deviceId));
				  LOGGER.info("quote disable done!");
			  }

			  updateIncrementId(request, order, quote, orderResponse, store);
			  if(proxyOrder == null && !quote.isRetryPayment() && order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0){
				  ShukranLedgerData shukranLedgerData= orderHelperV2.createShukranLedgerData(order, quote.getTotalShukranBurn(), quote.getTotalShukranBurnValueInCurrency(), quote.getTotalShukranBurnValueInBaseCurrency(), store, false, "Shukran Locked On Order Creation");
				  LOGGER.info("inside shukran ledger data "+ mapper.writeValueAsString(shukranLedgerData));
				  easService.updateShukranLedger(shukranLedgerData);
			  }

			  resp.setStatus(true);
			  resp.setStatusCode("200");
			  resp.setStatusMsg("Order created successfully!");
			  resp.setResponse(orderResponse);

			  // Clean up phone verification status from Redis after successful order creation
			  // This is only needed for guest users who verified their phone
			  if (shouldCleanupVerification && StringUtils.isNotEmpty(verifiedPhoneNumber)) {
				  removeVerificationStatusFromRedis(verifiedPhoneNumber);
			  }

		  } catch (Exception e) {
			  LOGGER.error("Error crateOrder: " + e);
			  LOGGER.error("for quote: " + request.getQuoteId());
			  resp.setStatus(false);
			  resp.setStatusCode("204");
			  resp.setStatusMsg("Error: " + e.getMessage());
			  return resp;
		  }
	  return resp;

  }

	public CreateOrderResponseDTO convertQuoteToOrderV3(CreateOrderRequestV2 request,
														String tokenHeader,
														String incrementId,
														String xSource,
														Map<String, String> requestHeader,
														String xHeaderToken,
														String xClientVersion,
														String customerIp,
														String deviceId)  {
		final String xHeaderCloudMethod = (StringUtils.isNotEmpty(requestHeader.get("X-Header-Cloud-Method")))
				? requestHeader.get("X-Header-Cloud-Method").toLowerCase() : "";
		int source = request.getSource() == null ? 1 : request.getSource();

		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();

		SalesOrder order = null;

		if (StringUtils.isBlank(incrementId)) {
			resp.setStatus(false);
			resp.setStatusCode("206");
			resp.setStatusMsg("Increment ID not found!");
			return resp;
		}

		if (request.getQuoteId() == null || request.getStoreId() == null) {
			resp.setStatus(true);
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
			resp.setStatusMsg(Constants.STORE_NOT_FOUND_MSG);
			return resp;
		}

		boolean isProxyOrderCreated = false;
		QuoteV7DTO quote = null;
		QuoteDTO quoteDTO = null;

		ProxyOrder proxyOrder = null;
		if (Objects.nonNull(request.getPaymentId()) && !request.isProxy) {

			LOGGER.info("payment id and prox is false");

			proxyOrder = orderHelperV2.findProxyOrderByPaymentId(request.getPaymentId());
			if (Objects.nonNull(proxyOrder)) {
				LOGGER.info("proxyOrder is not null");
				incrementId = proxyOrder.getIncrementId();
				isProxyOrderCreated = true;
				try {
					quote = mapper.readValue(proxyOrder.getQuote(), QuoteV7DTO.class);
					// convert QuoteV7DTO to QuoteDTO
					quoteDTO = getQuoteFromQuoteV7(quote);
				} catch (JsonProcessingException e) {
					resp.setStatus(false);
					resp.setStatusCode("203");
					resp.setStatusMsg("Error: There is an issue found in Quote!");
					LOGGER.error("Error in parsing quote object for Proxy Order. Error : " + e);
					return resp;
				}
			}
		} else {
			CreateOrderResponseDTO quoteResponse = getQuotev7(request, tokenHeader, xSource, requestHeader, xHeaderToken,
					xClientVersion, xHeaderCloudMethod, deviceId);
			if (quoteResponse.getStatus().booleanValue()) {
				quote = quoteResponse.getQuoteV7Response().getResponse();
				// convert QuoteV7DTO to QuoteDTO
				quoteDTO = getQuoteFromQuoteV7(quote);
			} else {
				return quoteResponse;
			}
		}
		if (null == quote) {

			resp.setStatus(false);
			resp.setStatusCode("212");
			resp.setStatusMsg("Quote object not found!");

			return resp;
		}
        GetOrderConsulValues orderCredentials = Constants.orderCredentials;
        List<LmdCommission> lmdCommissions = orderCredentials.getLmdCommission();
        calculatePoPriceForProductsV7(quote.getProducts());

		// Check mobile verification status in Redis before allowing order creation (similar to registration)
		// This check ensures mobile number is verified in Redis before order creation
		// Uses same flags as registration: isSignUpOtpEnabled, version check, source check, and store validation
		// Only perform this check for guest users
		// Check only if proxy order created or not
		boolean isProxyFlow = isProxyOrderCreated;
		LOGGER.info("V3 convertQuoteToOrderV3: INPUT: Guest phone verification gate. incrementId=" + incrementId + ", isProxyOrderCreated=" + isProxyOrderCreated);
		// guest verification
		String verifiedPhoneNumberV3 = null;
		boolean shouldCleanupVerificationV3 = false;
		if (!isProxyFlow) {
			GuestVerificationResult verificationResultV3 = checkGuestPhoneVerification(
					quote.getCustomerId(),
					quote.getShippingAddress(),
					quote.getCustomerPhoneNumber(),
					request,
					xClientVersion,
					xSource,
					"V3"
			);
			if (!verificationResultV3.success) {
				return verificationResultV3.errorResponse;
			}
			verifiedPhoneNumberV3 = verificationResultV3.verifiedPhoneNumber;
			shouldCleanupVerificationV3 = verificationResultV3.shouldCleanupVerification;
		}
		String paymentMethod = quote.getSelectedPaymentMethod();
		if((StringUtils.isBlank(paymentMethod) || StringUtils.isEmpty(paymentMethod)) && request.getIsApplePay() != null && request.getIsApplePay().equals(1)){
			LOGGER.info("Inside Apple Pay Method");
			paymentMethod= OrderConstants.APPLE_PAY;
		}
		LOGGER.info("payment method to set "+ paymentMethod);
		BigDecimal amastyBaseStoreBalance = new BigDecimal(0);

		BigDecimal restrictAMount = OrderConstants.checkPaymentMethodAmountRestn(paymentMethod);
		BigDecimal grandTotal = new BigDecimal(quote.getGrandTotal());
		LOGGER.info("grand total amount:" + grandTotal + " threshold amount:" + restrictAMount);
		if (quote.getIsAvailableShukranChanged()) {

			resp.setStatus(true);
			resp.setStatusCode("226");
			resp.setStatusMsg("Shukran Available Points Changed");
			return resp;

		}

		if (quote.getTotalShukranBurn() != null && quote.getTotalShukranBurn().compareTo(BigDecimal.ZERO) > 0) {

			boolean isAvailablePointsInvalid = quote.getShukranAvailablePoint() == null
					|| quote.getShukranAvailablePoint().compareTo(BigDecimal.ZERO) <= 0
					|| quote.getShukranAvailablePoint().compareTo(quote.getTotalShukranBurn()) < 0;
			if (isAvailablePointsInvalid) {
				resp.setStatus(true);
				resp.setStatusCode("226");
				resp.setStatusMsg("Shukran Available Points Are Less Than Burned Points");
				return resp;
			}
		}



		if (null != restrictAMount && grandTotal.intValue() > restrictAMount.intValue()) {

			resp.setStatus(false);
			resp.setStatusCode("222");
			resp.setStatusMsg("Grand total amount is more than threshold value!");
			return resp;
		}

		LOGGER.info("V3 convertQuoteToOrderV3: OTP validation gate. incrementId=" + incrementId + ", isProxyOrderCreated=" + isProxyOrderCreated);
		//	API-1941 starts
		if (!isProxyFlow && isOtpValidationRequired(xClientVersion, quoteDTO, requestHeader) && (!validateOtp(request, quoteDTO))) {
			resp.setStatus(false);
			resp.setStatusCode("217");
			resp.setStatusMsg("Otp validation failed!");
			return resp;

		}

		//	API-1941 ends

		/*
		 * Store credit check If applied store credit for quote is less than or equal to
		 * the available store credit of customer return wilt failure response, else
		 * continue
		 */
		if (quote.getStoreCreditApplied() != null) {
			BigDecimal appliedStoreCredit = new BigDecimal(quote.getStoreCreditApplied());
			if (appliedStoreCredit != null && appliedStoreCredit.compareTo(BigDecimal.ZERO) > 0) {

				List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
						.findByCustomerId(Integer.parseInt(quote.getCustomerId()));
				AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;

				if (amastyStoreCredit != null && null != amastyStoreCredit.getStoreCredit()) {

					LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
					amastyBaseStoreBalance = amastyStoreCredit.getStoreCredit();
					BigDecimal convertedStoreCredit = amastyStoreCredit.getStoreCredit().divide(store.getCurrencyConversionRate(),
									4, RoundingMode.HALF_UP)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					int result = appliedStoreCredit.compareTo(convertedStoreCredit);
					if (result > 0) {

						resp.setStatus(false);
						resp.setStatusCode("205");
						resp.setStatusMsg("Store Credit not applied! for quote: " + request.getQuoteId());
						return resp;

					}
				}
			}
		}

		if (CollectionUtils.isEmpty(quote.getProducts()) && !request.isRetryPaymentReplica()) {
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error: Quote did not have products! for quote: " + request.getQuoteId());
			return resp;
		}

		if (OrderConstants.checkBNPLPaymentMethods(paymentMethod) && request.isProxy && (Objects
				.isNull(quote.getBnplSessionAmount()) || Objects.isNull(quote.getGrandTotal())
				|| Double.valueOf(quote.getBnplSessionAmount()).compareTo(Double.valueOf(quote.getGrandTotal())) != 0)) {
			LOGGER.error("Order cration failed as the bnpl getting mismatch. Quote ID:" + quote.getQuoteId()
					+ " BNPL Amount : " + quote.getBnplSessionAmount() + " Order Amount : " + quote.getGrandTotal());
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Order amount and bnpl session amount mismatch. Quote ID : " + request.getQuoteId());
			return resp;
		}


		if (paymentMethod.equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_TYPE_FREE)) {
			if (!quote.isFreeOrder() && (quote.getStoreCreditApplied() == null || quote.getStoreCreditApplied().equals("")
					|| quote.getStoreCreditApplied().equals("0"))) {
				resp.setStatus(false);
				resp.setStatusCode("215");
				resp.setStatusMsg("Store Credit not applied! for quote: " + request.getQuoteId());
				return resp;
			}

		} else if (paymentMethod.equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_TYPE_FREE)
				&& null != quote.getGrandTotal() && !quote.getGrandTotal().equals("0") && quote.isFreeOrder()) {

			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("free coupon is not applied! for quote: " + request.getQuoteId());
			return resp;
		}


		AddressObject shippingAddress = quote.getShippingAddress();
		if (shippingAddress == null) {
			resp.setStatus(false);
			resp.setStatusCode("206");
			resp.setStatusMsg("Error: Address Not Found For Quote: " + request.getQuoteId());
			return resp;
		}

		if (StringUtils.isBlank(shippingAddress.getArea())) {
			resp.setStatus(false);
			resp.setStatusCode("207");
			resp.setStatusMsg(Constants.ERROR_MSG);
			ErrorType error = new ErrorType();
			error.setErrorCode("207");
			error.setErrorMessage("Error:Shipping Address Area Missed For Quote: " + request.getQuoteId());
			resp.setError(error);
			return resp;
		}

		if (StringUtils.isBlank(shippingAddress.getCity())) {
			resp.setStatus(false);
			resp.setStatusCode("208");
			resp.setStatusMsg(Constants.ERROR_MSG);
			ErrorType error = new ErrorType();
			error.setErrorCode("208");
			error.setErrorMessage("Error:Shipping Address City Missed For Quote: " + request.getQuoteId());
			resp.setError(error);
			return resp;
		}

		if (proxyOrder == null && !quote.isRetryPayment() && Constants.orderCredentials != null && quote.getTotalShukranBurn() != null && quote.getTotalShukranBurn().compareTo(BigDecimal.ZERO) > 0 && StringUtils.isNotBlank(quote.getProfileId()) && StringUtils.isNotEmpty(quote.getProfileId()) && store.getIsShukranEnable() && quote.getShukranLinkFlag() && StringUtils.isNotBlank(quote.getShukranCardNumber()) && StringUtils.isNotEmpty(quote.getShukranCardNumber())) {

			String cartId = StringUtils.isNotBlank(Constants.orderCredentials.getShukranCartIdPrefix()) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranCartIdPrefix()) ? Constants.orderCredentials.getShukranCartIdPrefix() + quote.getQuoteId() : quote.getQuoteId();
			String lockUnlockResponse = commonService.lockUnlockShukranData(quote.getProfileId(), quote.getTotalShukranBurn().toString(), cartId, true, null, store, "", "Create Order Api");
			if (StringUtils.isEmpty(lockUnlockResponse) || StringUtils.isBlank(lockUnlockResponse) || !lockUnlockResponse.equals("api passed")) {
				resp.setStatus(false);
				resp.setStatusCode("243");
				resp.setStatusMsg("Shukran Locking Failed!");
				return resp;
			}
		}
		if (!isProxyOrderCreated && !request.isRetryPaymentReplica()) {
			List<String> skus = new ArrayList<>();
			ProductStatusRequestV2 productStatusReqV2 = new ProductStatusRequestV2();
			productStatusReqV2.setStoreId(request.getStoreId());
			for (ProductEntityForQuoteV7DTO product : quote.getProducts()) {
				skus.add(product.getSku());
			}
			productStatusReqV2.setSkus(skus);
			productStatusReqV2.setCity_id(shippingAddress.getCityMapper().getId());
			productStatusReqV2.setCountry_id(shippingAddress.getCountryId());
			CreateOrderResponseDTO checkInventoryRes = checkInventoryV2(request, quote, productStatusReqV2);
			if (Objects.nonNull(checkInventoryRes) && !checkInventoryRes.getStatus().booleanValue())
				return checkInventoryRes;
		}

		//Set split config value to sales order
		List<String> shipmentModes = store.getShipmentMode();
		String shipmentModesStr = (null!=shipmentModes && !shipmentModes.isEmpty())? shipmentModes.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.joining(",")):"";
		LOGGER.info("Shipment modes from store config : "+shipmentModesStr+" for store id : "+store.getStoreId());
		if (request.isProxy()) {
			SalesOrder salesOrd = orderHelperV3.createOrderObjectToPersistV3(quote,quoteDTO, paymentMethod, store, incrementId,
					request.getIpAddress(), source, request.getMerchantReference(), request.getAppVersion(), xSource,
					customerIp, deviceId, request.isRetryPaymentReplica(), request.isPayfortAuthorized());
			//Is club shipment
			salesOrd.setIsClubShipment(quote.getIsClubShipment() != null && quote.getIsClubShipment() ? 1 : 0);
			salesOrd.setSplitConfigValue(shipmentModesStr);
			salesOrd.setIsSplitOrder(1);
			orderHelperV3.extractOrderItemsFromQuotev3(quote, salesOrd, store);
			request.setXSource(xSource);

			if(StringUtils.isNotEmpty(customerIp) && StringUtils.isNotBlank(customerIp) && customerIp.length()<45) {
				request.setCustomerIp(customerIp);
			}

			CreateOrderResponseDTO proxyOrderRes = createV3OrderProxy(request, salesOrd,quoteDTO, quote, paymentMethod, store);
			if (proxyOrderRes.getStatus().booleanValue())
				externalQuoteHelper.disableExternalQuote(quoteDTO, tokenHeader, xHeaderToken, requestHeader.get(Constants.deviceId));
			return proxyOrderRes;
		}

		boolean firstOrderByCustomer = orderHelper.checkFirstCreateOrder(quote.getCustomerEmail());
		boolean firstTimeOrderCall = false;
		try {

			LOGGER.info("insertion started");
			// EAS_CHANGES added in below functions
			order = orderHelperV3.createOrderObjectToPersistV3(quote,
					quoteDTO,
					paymentMethod,
					store,
					incrementId,
					request.getIpAddress(),
					source,
					request.getMerchantReference(),
					request.getAppVersion(),
					xSource,
					customerIp,
					deviceId,
					request.isRetryPaymentReplica(),
					request.isPayfortAuthorized());
			//Is club shipment
			order.setIsClubShipment(quote.getIsClubShipment() != null && quote.getIsClubShipment() ? 1 : 0);
			order.setSplitConfigValue(shipmentModesStr);
			order.setIsSplitOrder(1);

			orderHelperV2.saveUuidOfUserInOrder(order, tokenHeader);

			LOGGER.info("sales_order  table insertion done!");
			orderHelperV2.createOrderAddresses(quoteDTO, order);
			LOGGER.info("sales_order_address table insertion done!");

			orderHelperV2.createOrderPayment(quoteDTO, order, paymentMethod, store);
			LOGGER.info("sales_order_payment table insertion done!");

			if (null != order.getSubSalesOrder()
					&& null == order.getSubSalesOrder().getRetryPayment()) {
				customerService.deductStoreCreditV2(quoteDTO, order, store, amastyBaseStoreBalance);
				firstTimeOrderCall = true;
				LOGGER.info("deduct store credit done!");
			}

			orderHelperV3.createOrderItemsV3(quote, order, store, request.isRetryPaymentReplica());
			LOGGER.info("sales_order child table insertion done!");

			orderHelperV3.createOrderGridV3(quote, quoteDTO,order, paymentMethod, shippingAddress, store, source,
					request.getAppVersion());
			LOGGER.info("sales_order_grid table insertion done!");

			if (!request.isRetryPaymentReplica()) {
				orderHelper.createOrderStatusHistory(order, null);
				LOGGER.info("order_status_history table insertion done!");
			}

			LOGGER.info("isProxyOrderCreated fore block:" + isProxyOrderCreated);
			if (!isProxyOrderCreated && firstTimeOrderCall) {
				orderHelperV3.blockInventoryV3(order);
				LOGGER.info("block inventory call done!");
			}


			CreateOrderResponse orderResponse = new CreateOrderResponse();
			orderResponse.setIncrementId(order.getIncrementId());

			orderResponse.setFirstOrderByCustomer(firstOrderByCustomer);

			if (null != order.getEntityId()) {
				orderResponse.setOrderId(order.getEntityId().toString());
			}
			if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalQuoteId()) {
				orderResponse.setQuoteId(order.getSubSalesOrder().getExternalQuoteId().toString());
			}
			if (null != order.getGrandTotal()) {
				orderResponse.setGrandTotal(order.getGrandTotal().toString());
			}
			if (StringUtils.isNotBlank(quote.getTabbyPaymentId())) {
				orderResponse.setTabbyPaymentId(quote.getTabbyPaymentId());
			}
			if (null != order.getPayfortAuthorized()) {
				LOGGER.info("SalesOrderServiceV2Impl - convertQuoteToOrderV2 - order.getPayfortAuthorized:" + order.getPayfortAuthorized());
				orderResponse.setPayfortAuthorized(String.valueOf(order.getPayfortAuthorized()));
				LOGGER.info("SalesOrderServiceV2Impl - convertQuoteToOrderV2 - orderResponse: " + orderResponse);
			}

			if (StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData()) && !isProxyOrderCreated
					&& firstTimeOrderCall) {
				orderHelperV2.reedmeExternalCoupon(quoteDTO, store, order, false);
			} else {
				updateCouponReedmeV3(order, proxyOrder);
			}
			if (null != paymentMethod && (paymentMethod.equals(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
					|| paymentMethod.equals(PaymentCodeENUM.FREE.getValue()))) {

				orderHelper.updateStatusHistory(order, true, true, false, false, false);

				resp.setCodOrder(true);
			} else {

				orderHelper.updateStatusHistory(order, true, false, true, false, false);
				LOGGER.info("update status  done!");
			}
			// Kafka push to EAS service for order created. If Earn service flag ON!.
			if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
				eASServiceImpl.publishSaleOrderToKafka(order);
			}
			if (!isProxyOrderCreated) {
				externalQuoteHelper.disableExternalQuote(quoteDTO, tokenHeader, xHeaderToken, requestHeader.get(Constants.deviceId));
				LOGGER.info("quote disable done!");
			}

			updateIncrementId(request, order, quoteDTO, orderResponse, store);
			if(proxyOrder == null && !quote.isRetryPayment() && order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0){
				ShukranLedgerData shukranLedgerData= orderHelperV2.createShukranLedgerData(order, quote.getTotalShukranBurn(), quote.getTotalShukranBurnValueInCurrency(), quote.getTotalShukranBurnValueInBaseCurrency(), store, false, "Shukran Locked On Order Creation");
				LOGGER.info("inside shukran ledger data "+ mapper.writeValueAsString(shukranLedgerData));
				easService.updateShukranLedger(shukranLedgerData);
			}
			//Build OTS payload and publish to PubSub topic
			buildOTSPayloadAndPublishToPubSub(order, quote);
			Map<String,String> pubsubPayload = new HashMap<>();
			pubsubPayload.put("orderId", order.getEntityId() != null ? order.getEntityId().toString() : null);
			LOGGER.info("Publishing split order pubsub for orderId: " + order.getEntityId()+" and pubsubPayload: " + pubsubPayload);
			pubSubServiceImpl.publishSplitOrderPubSub(splitOrderTopic,pubsubPayload);
			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Order created successfully!");
			resp.setResponse(orderResponse);

			// Clean up phone verification status from Redis after successful order creation
			// This is only needed for guest users who verified their phone
			if (shouldCleanupVerificationV3 && StringUtils.isNotEmpty(verifiedPhoneNumberV3)) {
				removeVerificationStatusFromRedis(verifiedPhoneNumberV3);
			}

		} catch (Exception e) {
			LOGGER.error("Error crateOrder: " + e);
			LOGGER.error("for quote: " + request.getQuoteId());
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error: " + e.getMessage());
			return resp;
		}
		return resp;

	}

	private void buildOTSPayloadAndPublishToPubSub(SalesOrder order, QuoteV7DTO quoteV7DTO) {
		try {
			OTSOrderRequest otsOrderRequest = orderHelperV3.buildOTSPayload(order, quoteV7DTO);
			List<OTSOrderRequest> otsOrderRequestList = new ArrayList<>();
			otsOrderRequestList.add(otsOrderRequest);
			LOGGER.info("Pushing payload to Order tracking service PUBSUB for orderId: " + order.getEntityId()+" and payload: " + mapper.writeValueAsString(otsOrderRequestList));
			pubSubServiceImpl.publishOrderTrackingPubSub(splitOrderTrackingTopic,otsOrderRequestList);
		} catch(Exception e) {
			LOGGER.error("Error in building OTS payload and publishing to pubsub for orderId: " + order.getEntityId(), e);
		}
	}

	private QuoteDTO getQuoteFromQuoteV7 (QuoteV7DTO quote) {
		Map<String,Object> tmp = mapper.convertValue(quote, new TypeReference<>(){});
		tmp.remove("shippingAmount");
		QuoteDTO dto = mapper.convertValue(tmp, QuoteDTO.class);
		BigDecimal shippingAmount = Optional.ofNullable(quote.getShippingAmount()).orElse(java.util.Collections.emptyList()).stream()
				.filter(entry ->  null != entry.getShipmentMode() && "Local".equalsIgnoreCase(entry.getShipmentMode()))
				.map(ShippingAmountEntry::getRemainshippingAmount)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(BigDecimal.ZERO);
		dto.setShippingAmount(shippingAmount+"");
		return dto;
	}

	/**
	 * If previous payment failed with apple pay and customer trying again with
	 * apply pay re-generate increment ID and save in sales_order.edit_increment
	 * 
	 * @param request
	 * @param order
	 * @param quote
	 * @param orderResponse 
	 * @param paymentMethod
	 */
	private void updateIncrementId(CreateOrderRequestV2 request, SalesOrder order, QuoteDTO quote,
			CreateOrderResponse orderResponse, Stores store) {

		if (store.isEnableApplepayholdOrder() && request.isRetryPaymentReplica()
				&& orderHelperV2.checkApplePayRetryRequest(quote)) {
			String newIncrementId = salesOrderService.getOrderIncrementId(request.getStoreId());
			LOGGER.info("Generating new increment for applePay order : " + order.getIncrementId()
					+ " new Increment ID: " + newIncrementId);
			order.setEditIncrement(newIncrementId);
			try {
				//save into sales order table to get the edit increment id in order edit flow
				salesOrderRepository.saveAndFlush(order);
			} catch (Exception e) {
				LOGGER.error("Error while saving order with new increment id for apple pay retry flow for order id: " + order.getEntityId(), e);
			}
			orderResponse.setIncrementId(newIncrementId);
		}
	}

  boolean isOtpValidationRequired(String xClientVersion, QuoteDTO quote, Map<String, String> requestHeader) {
  	boolean result = false;
  	try {
		if(!Constants.getOtpVerificationInCreateOrderFlag()) return false;

		String authorizationToken = (StringUtils.isNotEmpty(requestHeader.get("authorization-token")))
				? requestHeader.get("authorization-token").toLowerCase() : "";
		if(configService.checkAuthorization(authorizationToken,null)) return false;

		String paymentMethod = ((quote!=null) ? quote.getSelectedPaymentMethod() : null);
		if(StringUtils.isNotEmpty(paymentMethod)
				&& paymentMethod.equals(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
				&& quote.getPaymentRestriction()!=null
				&& quote.getPaymentRestriction().getOtpValidation() != null
				&& quote.getPaymentRestriction().getOtpValidation().booleanValue()) {
			result = true;


			/**
			 * To allow older clients to be able to place order without otp verification.
			 * This part can be removed after setting minimum app version to the threshold version.
			 */
			Long clientVersion = Constants.decodeAppVersion(xClientVersion);
			Long thresholdVersion = Constants.decodeAppVersion(Constants.getOtpVerificationThresholdVersion());
			if (clientVersion != null && thresholdVersion != null && clientVersion < thresholdVersion) {
				result = false;
			}
		}

	} catch (Exception e) {
		LOGGER.error("Error while validating otp " + e);
	}
  	return result;
  }

	boolean validateOtp(CreateOrderRequestV2 request, QuoteDTO quote) {
		boolean result = false;
		try {
			if(request==null
					|| quote==null
					|| quote.getShippingAddress() == null
					|| StringUtils.isBlank(quote.getShippingAddress().getMobileNumber())) return result;
			String plainPhoneNo = Constants.getPlainPhoneNo(quote.getShippingAddress().getMobileNumber());
			OtpBucketObject otpBucketObject = getBucketObject(plainPhoneNo);
			if (otpBucketObject != null
					&& otpBucketObject.getExpiresAt() != null
					&& StringUtils.isNotBlank(otpBucketObject.getOtp())
					&& StringUtils.isNotBlank(request.getOtpvalue())) {
				otpBucketObject.setMobileNo(plainPhoneNo);
				long now = Instant.now().toEpochMilli();
				if(now <= otpBucketObject.getExpiresAt() && request.getOtpvalue().equals(otpBucketObject.getOtp())) {
					result = true;
					redisHelper.remove(Constants.OTP_CACHE_NAME, otpBucketObject.getMobileNo());
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while validating Otp " + e);
		}
		return result;
	}


	private OtpBucketObject getBucketObject(String phoneNo) {
		OtpBucketObject result = null;
		if (StringUtils.isEmpty(phoneNo)) return result;
		try {
			result = (OtpBucketObject) redisHelper.get(Constants.OTP_CACHE_NAME, phoneNo, OtpBucketObject.class);
			if (result != null) {
				result.setMobileNo(phoneNo);
			}
		} catch (Exception e) {
			result = null;
		}
		return result;
	}

	/**
	 * Get mobile verification status from Redis for guest users only.
	 * Checks the separate verification status key (phone_verified:{phone}) which is created
	 * when OTP validates successfully for guest users. This key persists even after OTP bucket is removed.
	 * This method is ONLY used for guest users at order placement.
	 * Returns true if mobile number is verified, false otherwise
	 */
	private Boolean getGuestPhoneVerificationStatusFromRedis(String phoneNumber) {
		// Check the separate verification status key for guest users (phone_verified:{phone})
		// This key is created when guest user OTP validates successfully
		String key = "phone_verified:" + phoneNumber;
		try {
			// Try to get as String first (as stored by saveGuestPhoneVerificationStatus)
			Object result = redisHelper.get(Constants.OTP_CACHE_NAME, key, String.class);
			if (result instanceof String stringResult) {
				return Boolean.parseBoolean(stringResult);
			}
			// If not String, try as Boolean
			result = redisHelper.get(Constants.OTP_CACHE_NAME, key, Boolean.class);
			if (result instanceof Boolean booleanResult) {
				return booleanResult;
			}
			return false;
		} catch (Exception e) {
			LOGGER.error("Error getting guest phone verification status from Redis for " + key + ": " + e);
			return false;
		}
	}

	/**
	 * Remove phone verification status from Redis after successful order placement.
	 * This cleans up the verification status key that was created during OTP validation.
	 */
	private void removeVerificationStatusFromRedis(String phoneNumber) {
		if (StringUtils.isEmpty(phoneNumber)) {
			return;
		}
		try {
			String key = "phone_verified:" + phoneNumber;
			redisHelper.remove(Constants.OTP_CACHE_NAME, key);
			LOGGER.info("Removed phone verification status from Redis for " + phoneNumber + " after successful order creation.");
		} catch (Exception e) {
			LOGGER.error("Error removing verification status from Redis for " + phoneNumber + ": " + e);
			// Don't throw exception - cleanup failure shouldn't affect order creation
		}
	}

	/**
	 * Result class for guest phone verification check
	 */
	private static class GuestVerificationResult {
		boolean success;
		CreateOrderResponseDTO errorResponse;
		String verifiedPhoneNumber;
		boolean shouldCleanupVerification;

		GuestVerificationResult(boolean success, CreateOrderResponseDTO errorResponse, String verifiedPhoneNumber, boolean shouldCleanupVerification) {
			this.success = success;
			this.errorResponse = errorResponse;
			this.verifiedPhoneNumber = verifiedPhoneNumber;
			this.shouldCleanupVerification = shouldCleanupVerification;
		}
	}

	/**
	 * Helper method to check guest user phone verification status.
	 * This method extracts the duplicated logic from convertQuoteToOrderV2 and convertQuoteToOrderV3.
	 * 
	 * @param customerId The customer ID from quote (null for guest users)
	 * @param shippingAddress The shipping address from quote
	 * @param customerPhoneNumber The customer phone number from quote
	 * @param request The create order request
	 * @param xClientVersion The client version header
	 * @param xSource The source header
	 * @param versionPrefix The version prefix for logging ("V2" or "V3")
	 * @return GuestVerificationResult containing success status, error response (if failed), verified phone number, and cleanup flag
	 */
	private GuestVerificationResult checkGuestPhoneVerification(String customerId, AddressObject shippingAddress, 
			String customerPhoneNumber, CreateOrderRequestV2 request, String xClientVersion, String xSource, String versionPrefix) {
		
		// Only perform this check for guest users
		boolean isGuestUser = StringUtils.isEmpty(customerId);
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Checking if guest user. QuoteId: " + request.getQuoteId() + ", StoreId: " + request.getStoreId() + ", CustomerId: " + customerId + ", isGuestUser: " + isGuestUser);
		
		if (!isGuestUser) {
			LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile verification check skipped - user is not a guest user.");
			return new GuestVerificationResult(true, null, null, false);
		}
		
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Starting mobile verification check for guest user. QuoteId: " + request.getQuoteId() + ", StoreId: " + request.getStoreId() + ", CustomerId: " + customerId);
		String isSignUpOtpEnabled = Constants.orderCredentials != null ? Constants.orderCredentials.getIsSignUpOtpEnabled() : null;
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": isSignUpOtpEnabled flag: " + isSignUpOtpEnabled);
		
		if (!"true".equalsIgnoreCase(isSignUpOtpEnabled)) {
			LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile verification check skipped - isSignUpOtpEnabled is not 'true'.");
			return new GuestVerificationResult(true, null, null, false);
		}
		
		boolean shouldCheckVerification = shouldPerformVerificationCheck(request, xClientVersion, xSource, versionPrefix);
		if (!shouldCheckVerification) {
			LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile verification check skipped - shouldCheckVerification is false.");
			return new GuestVerificationResult(true, null, null, false);
		}
		
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile verification check is required. Extracting mobile number from quote.");
		String mobileNumber = extractMobileNumberFromQuote(shippingAddress, customerPhoneNumber, versionPrefix);
		
		if (StringUtils.isEmpty(mobileNumber)) {
			LOGGER.warn(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile number is missing but verification is required. Rejecting order creation. QuoteId: " + request.getQuoteId() + LOG_STORE_ID_SUFFIX + request.getStoreId());
			return createErrorResponse("OTP validation failed for mobile");
		}
		
		return validateMobileVerification(mobileNumber, request, versionPrefix);
	}

	/**
	 * Checks if verification should be performed based on store, version, and source validation.
	 */
	private boolean shouldPerformVerificationCheck(CreateOrderRequestV2 request, String xClientVersion, String xSource, String versionPrefix) {
		String minAppVersion = Constants.orderCredentials != null ? Constants.orderCredentials.getMinAppVersionReqdForOtpFeature() : null;
		List<Integer> storeIds = Constants.orderCredentials != null ? Constants.orderCredentials.getStoreIdsForOtpFeature() : null;
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": minAppVersion: " + minAppVersion + ", storeIds for OTP: " + storeIds);
		
		boolean isStoreValid = storeIds != null && storeIds.contains(request.getStoreId());
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Store validation result - StoreId: " + request.getStoreId() + ", isStoreValid: " + isStoreValid);
		
		boolean isVersionValid = checkVersionValidity(xClientVersion, minAppVersion, versionPrefix);
		boolean isSourceMsite = "msite".equalsIgnoreCase(xSource);
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Source check - xSource: " + xSource + ", isSourceMsite: " + isSourceMsite);
		
		boolean shouldCheckVerification = isStoreValid && (isVersionValid || isSourceMsite);
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": shouldCheckVerification: " + shouldCheckVerification + " (isStoreValid: " + isStoreValid + ", isVersionValid: " + isVersionValid + ", isSourceMsite: " + isSourceMsite + ")");
		
		return shouldCheckVerification;
	}

	/**
	 * Checks if the client version is valid compared to the minimum required version.
	 */
	private boolean checkVersionValidity(String xClientVersion, String minAppVersion, String versionPrefix) {
		if (StringUtils.isEmpty(xClientVersion) || StringUtils.isEmpty(minAppVersion)) {
			LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Version check skipped - xClientVersion: " + xClientVersion + ", minAppVersion: " + minAppVersion);
			return false;
		}
		
		Long clientVersion = Constants.decodeAppVersion(xClientVersion);
		Long minVersion = Constants.decodeAppVersion(minAppVersion);
		boolean isVersionValid = (clientVersion != null && minVersion != null && clientVersion >= minVersion);
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Version check - xClientVersion: " + xClientVersion + " (decoded: " + clientVersion + "), minAppVersion: " + minAppVersion + " (decoded: " + minVersion + "), isVersionValid: " + isVersionValid);
		return isVersionValid;
	}

	/**
	 * Extracts mobile number from shipping address or customer phone number.
	 */
	private String extractMobileNumberFromQuote(AddressObject shippingAddress, String customerPhoneNumber, String versionPrefix) {
		String mobileNumber = null;
		String mobileNumberSource = null;
		
		if (shippingAddress != null) {
			if (StringUtils.isNotEmpty(shippingAddress.getTelephone())) {
				mobileNumber = shippingAddress.getTelephone();
				mobileNumberSource = "shippingAddress.telephone";
			} else if (StringUtils.isNotEmpty(shippingAddress.getMobileNumber())) {
				mobileNumber = shippingAddress.getMobileNumber();
				mobileNumberSource = "shippingAddress.mobileNumber";
			}
		}
		
		if (StringUtils.isEmpty(mobileNumber) && StringUtils.isNotEmpty(customerPhoneNumber)) {
			mobileNumber = customerPhoneNumber;
			mobileNumberSource = "customerPhoneNumber";
		}
		
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile number extraction - mobileNumber: " + (StringUtils.isNotEmpty(mobileNumber) ? "***" : "null") + ", source: " + mobileNumberSource);
		return mobileNumber;
	}

	/**
	 * Validates mobile number and checks Redis verification status.
	 */
	private GuestVerificationResult validateMobileVerification(String mobileNumber, CreateOrderRequestV2 request, String versionPrefix) {
		// Normalize phone number (remove spaces, dashes, plus signs)
		String plainPhoneNo = Constants.getPlainPhoneNo(mobileNumber);
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Normalized phone number - original: " + mobileNumber + ", normalized: " + plainPhoneNo);
		
		// For guest users, check the special phone_verified key
		Boolean mobileVerificationStatus = getGuestPhoneVerificationStatusFromRedis(plainPhoneNo);
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Guest phone verification status from Redis for " + plainPhoneNo + " is: " + mobileVerificationStatus);

		// Check if mobile is verified - if not verified, reject order creation
		if (mobileVerificationStatus == null || Boolean.FALSE.equals(mobileVerificationStatus)) {
			LOGGER.warn(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile number " + plainPhoneNo + " is not verified in Redis. Rejecting order creation. QuoteId: " + request.getQuoteId() + LOG_STORE_ID_SUFFIX + request.getStoreId() + ", verificationStatus: " + mobileVerificationStatus);
			return createErrorResponse("OTP validation failed for mobile");
		}
		
		LOGGER.info(LOG_ORDER_CREATION_PREFIX + versionPrefix + ": Mobile number " + plainPhoneNo + " is verified. Proceeding with order creation.");
		return new GuestVerificationResult(true, null, plainPhoneNo, true);
	}

	/**
	 * Creates an error response for mobile verification failure.
	 */
	private GuestVerificationResult createErrorResponse(String errorMessage) {
		CreateOrderResponseDTO errorResp = new CreateOrderResponseDTO();
		errorResp.setStatus(false);
		errorResp.setStatusCode("217");
		errorResp.setStatusMsg(errorMessage);
		return new GuestVerificationResult(false, errorResp, null, false);
	}

	/**
	 * Calculates poPrice for all products in the quote that match valid sellers.
	 * This method reduces cyclomatic complexity by extracting the poPrice calculation logic.
	 */
	private void calculatePoPriceForProductsV7(List<ProductEntityForQuoteV7DTO> products) {
		if (products == null || products.isEmpty()) {
			return;
		}

		for (ProductEntityForQuoteV7DTO product : products) {
			String soldBy = product.getSoldBy();
			if (soldBy != null) {
				BigDecimal poPrice = calculatePoPriceForProductV7(product, soldBy);
				product.setPoPrice(poPrice != null ? poPrice.toPlainString() : "0");
			}
		}
	}

	/**
	 * Calculates poPrice for a single product based on seller, prices, discounts, and commissions.
	 * Returns null if calculation fails.
	 */
	private BigDecimal calculatePoPriceForProductV7(ProductEntityForQuoteV7DTO product, String soldBy) {
		//LmdCommission lmdCommission = getSellerCommissionPercent(soldBy);
		LOGGER.info("calculatePoPriceForProductV7 for product SKU: " + product.getSku() + ", soldBy: " + soldBy );
		// 1) Extract short_description from product_attributes
		String shortDesc = product.getShortDescription();
		LmdCommissionConfig lmdCommissionConfig = resolveCommissionPercentFromConfig(soldBy, shortDesc);

		LOGGER.info("calculatePoPriceForProductV7 PO price commission% resolved from LmdCommissionConfig. sku=" + product.getSku()
				+ ", soldBy=" + soldBy + ", short_description=" + shortDesc
				+ ", lmdCommissionConfig=" + lmdCommissionConfig);

		if (null == lmdCommissionConfig) {
			return BigDecimal.ZERO;
		}
				try {
					BigDecimal commissionPercent = resolveStyliCommission(lmdCommissionConfig);
					if (commissionPercent.compareTo(BigDecimal.ZERO) <= 0) {
						LOGGER.info("No valid commission found. sku= "+ product.getSku()+"  soldBy= "+soldBy);
						return BigDecimal.ZERO;
					}
					// default poPrice
					BigDecimal rrPrice = BigDecimal.ZERO;
					BigDecimal poPrice = BigDecimal.ZERO;
					BigDecimal rowTotalInclTax = BigDecimal.ZERO;
					BigDecimal taxAmount = BigDecimal.ZERO;
					// row total inclTax
					String rowTotalInclTaxStr = product.getRowTotalInclTax();
					LOGGER.info("calculatePoPriceForProductV7 rowTotalInclTax: " + rowTotalInclTaxStr);
					if (StringUtils.isNotBlank(rowTotalInclTaxStr)) {
						try {
							rowTotalInclTax = new BigDecimal(rowTotalInclTaxStr);
							rrPrice = rowTotalInclTax;
							//Apply ntd discount if any
							if (null != lmdCommissionConfig.getNtdDiscount() && lmdCommissionConfig.getNtdDiscount().compareTo(BigDecimal.ZERO) > 0) {
								BigDecimal discountAmount = null!=product.getDiscountAmount() ? new BigDecimal(product.getDiscountAmount()) : BigDecimal.ZERO;
								LOGGER.info("In calculatePoPriceForProductV7 Calculating NTD discount. Discount Amount: " + discountAmount + ", NTD Discount Percent: " + lmdCommissionConfig.getNtdDiscount() + "%, rrPrice"+ rrPrice);
								BigDecimal ntdDiscount = discountAmount.multiply(lmdCommissionConfig.getNtdDiscount().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
								rrPrice = rrPrice.subtract(ntdDiscount);
								LOGGER.info("In calculatePoPriceForProductV7 Applied NTD discount of " + ntdDiscount + " on rrPrice. New rrPrice: " + rrPrice);
							}
							// Remove tax: net = gross / (1 + taxPercent/100)
							BigDecimal taxPercent = product.getTaxPercent() != null ? new BigDecimal(product.getTaxPercent()) : BigDecimal.ZERO;
							LOGGER.info("calculatePoPriceForProductV7 rrPrice with tax: " + rrPrice+", taxPercent"+taxPercent);
							if (taxPercent.compareTo(BigDecimal.ZERO) > 0) {
								BigDecimal taxFactor = BigDecimal.ONE.add(
										taxPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
								);
								rrPrice = rrPrice.divide(taxFactor, 2, RoundingMode.HALF_UP);
							} else {
								LOGGER.info("calculatePoPriceForProductV7 No tax percent , falling to default tax percent from config: " + lmdCommissionConfig.getDefaultTaxPercentage());
								taxPercent = null!=lmdCommissionConfig.getDefaultTaxPercentage() ? lmdCommissionConfig.getDefaultTaxPercentage() : BigDecimal.ZERO;
								LOGGER.info("calculatePoPriceForProductV7 Tax percent  from config: " + taxPercent);
								BigDecimal taxFactor = BigDecimal.ONE.add(
										taxPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
								);
								rrPrice = rrPrice.divide(taxFactor, 2, RoundingMode.HALF_UP);
							}
							LOGGER.info("calculatePoPriceForProductV7 rrPrice pre tax: " + rrPrice);
							// Find RR price per quantity
							int qty = safeInt(product.getQuantity(), 1);
							if (qty > 1) {
								rrPrice = rrPrice.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
							}
						} catch (NumberFormatException ignore) {
							// log and continue with rrPrice as 0
							LOGGER.error("NumberFormatException in finding rrPrice for product sku: " + product.getSku() + ", rowTotalInclTax: " + rowTotalInclTaxStr + ", taxAmount: " + product.getTaxAmount() + ", discountAmount: " + product.getDiscountAmount(), ignore);
						}
					}
					LOGGER.info("calculatePoPriceForProductV7 rrPrice (price after discount, with out tax): " + rrPrice);
					LOGGER.info("calculatePoPriceForProductV7 styli commissionPercent : " + commissionPercent);
					BigDecimal styliCommission = rrPrice.multiply(commissionPercent.divide(BigDecimal.valueOf(100)));
					LOGGER.info("calculatePoPriceForProductV7 styli commission : " + styliCommission);
					poPrice = rrPrice.subtract(styliCommission);
					// FIX: Final safety check - ensure poPrice is never negative
					if (poPrice.compareTo(BigDecimal.ZERO) < 0) {
						LOGGER.warn("calculatePoPriceForProductV7 Calculated poPrice is negative (" + poPrice + "). Setting to 0.");
						poPrice = BigDecimal.ZERO;
					}
					LOGGER.info("calculatePoPriceForProductV7 po price: " + poPrice);
					return poPrice;
				} catch (Exception ex) {
					LOGGER.error("Exception in calculatePoPriceForProductV7: " + ex.getMessage(), ex);
					return BigDecimal.ZERO;
				}
	}

	/**
	 * Selects the price to use (specialPrice if valid and > 0, otherwise basePrice).
	 */
	private String selectPriceToUse(String basePrice, String specialPrice) {
		if (StringUtils.isBlank(specialPrice)) {
			return basePrice;
		}
		try {
			BigDecimal specialVal = new BigDecimal(specialPrice);
			return specialVal.compareTo(BigDecimal.ZERO) > 0 ? specialPrice : basePrice;
		} catch (NumberFormatException ignore) {
			return basePrice;
		}
	}

	/**
	 * Applies coupon discount with seller-specific commission adjustment if applicable.
	 */
	private BigDecimal applyCouponDiscountWithCommission(ProductEntityForQuoteV7DTO product, String soldBy, BigDecimal rrPrice) {
		BigDecimal appliedCouponDiscount = getAppliedCouponDiscount(product);
		if (appliedCouponDiscount.compareTo(BigDecimal.ZERO) <= 0  || soldBy == null) {
			return rrPrice;
		}

	LmdCommission lmdCommission = getSellerCommissionPercent(soldBy);
		LOGGER.info("applyCouponDiscountWithCommission for product SKU: " + product.getSku() + ", soldBy: " + soldBy + ", lmdCommission: " + lmdCommission);
	if (lmdCommission == null) {
		BigDecimal result = rrPrice.subtract(appliedCouponDiscount);
		// FIX: Ensure result doesn't go below zero
		if (result.compareTo(BigDecimal.ZERO) < 0) {
			LOGGER.warn("Coupon discount (" + appliedCouponDiscount + ") exceeds remaining price. Setting to 0.");
			return BigDecimal.ZERO;
		}
		return result;
	}

	BigDecimal commissionRate = BigDecimal.valueOf(lmdCommission.getValue())
			.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
	BigDecimal commissionOnDiscount = appliedCouponDiscount
			.multiply(commissionRate)
			.setScale(2, RoundingMode.HALF_UP);
	BigDecimal adjustedCouponDiscount = appliedCouponDiscount.subtract(commissionOnDiscount);
	
	LOGGER.info("commission on coupon discount: " + commissionOnDiscount);
	LOGGER.info("adjusted coupon discount after commission: " + adjustedCouponDiscount);
	
	BigDecimal adjustedRrPrice = rrPrice.subtract(adjustedCouponDiscount);
	// FIX: Ensure adjusted price doesn't go below zero
	if (adjustedRrPrice.compareTo(BigDecimal.ZERO) < 0) {
		LOGGER.warn("Adjusted coupon discount (" + adjustedCouponDiscount + ") exceeds remaining price. Setting to 0.");
		adjustedRrPrice = BigDecimal.ZERO;
	}
	LOGGER.info("rrPrice after adjusted coupon discount: " + adjustedRrPrice);
	return adjustedRrPrice;
	}

	/**
	 * Gets the applied coupon discount from product, returns ZERO if none.
	 */
	private BigDecimal getAppliedCouponDiscount(ProductEntityForQuoteV7DTO product) {
		if (product.getAppliedCouponValue() == null) {
			return BigDecimal.ZERO;
		}
		for (AppliedCouponValue couponValue : product.getAppliedCouponValue()) {
			if (couponValue != null && couponValue.getDiscount() != null
					&& couponValue.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
				LOGGER.info("coupon value applied");
				return couponValue.getDiscount();
			}
		}
		return BigDecimal.ZERO;
	}

	/**
	 * Gets the commission percent for a seller from LmdCommission.
	 * Uses switch-case for better readability and performance.
	 */
	private LmdCommission getSellerCommissionPercent(String soldBy) {
		try {
			List<LmdCommission> lmdCommissions = Constants.orderCredentials.getLmdCommission();
			if (soldBy == null || lmdCommissions == null || lmdCommissions.isEmpty()) {
				return null;
			}

			return lmdCommissions.stream()
					.filter(c -> c.getValue() != null)
					.filter(c -> {
						String en = c.getEn() != null
								? c.getEn().toLowerCase()
								: null;

						String ar = c.getAr() != null
								? c.getAr().toLowerCase()
								: null;

						return soldBy.equalsIgnoreCase(en) || soldBy.equalsIgnoreCase(ar);
					})
					.findFirst()
					.orElse(null);
		} catch (Exception ex) {
			LOGGER.error("Exception in getSellerCommissionPercent: " + ex.getMessage(), ex);
			return null;
		}
	}



	private CreateOrderResponseDTO getQuote(CreateOrderRequestV2 request, String tokenHeader, String xSource,
			Map<String, String> requestHeader, String xHeaderToken, String xClientVersion,
			final String xHeaderCloudMethod, String deviceId) {
		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();

		GetQuoteResponse quoteResponse = externalQuoteHelper.fetchQuote(request.getQuoteId(), request.getCustomerId(),
				request.getStoreId(), tokenHeader, false, xHeaderToken, xSource, xClientVersion,request.isRetryPaymentReplica(), requestHeader.get(Constants.deviceId) );

		/*
		 * Validations: quote not available - checked payment method available - checked
		 * products available - checked store credit applied in case of FREE payment
		 * method
		 *
		 */

		if (null == quoteResponse || !quoteResponse.getStatus() || quoteResponse.getStatusCode().equals("203")) {
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Error: There is an issue found in Quote!");
			return resp;
		} else if (!quoteResponse.getStatus().booleanValue()
				|| !quoteResponse.getStatusCode().equals("200")) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
			return resp;
		} else if (null != quoteResponse.getResponse()
				&& (quoteResponse.getResponse().getSelectedPaymentMethod() == null
						|| quoteResponse.getResponse().getSelectedPaymentMethod().equals("")) && (request.getIsApplePay() == null || !request.getIsApplePay().equals(1))) {

			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error: Quote did not have payment method! for quote: " + request.getQuoteId());
			return resp;
		} else if (null != quoteResponse.getResponse()
				&& quoteResponse.getStatusCode().equals("200")
				&& StringUtils.isNotBlank(quoteResponse.getResponse().getCustomerId())) {

			authenticateCheck(requestHeader, Integer.valueOf(quoteResponse.getResponse().getCustomerId()));
		} else if (quoteResponse.getResponse() != null && "true".equals(xHeaderCloudMethod)
				&& PaymentCodeENUM.CASH_ON_DELIVERY.getValue()
						.equals(quoteResponse.getResponse().getSelectedPaymentMethod())) {
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error: Quote have invalid payment method for X-Header-Cloud-Method, expected: ("
					+ PaymentCodeENUM.MD_PAYFORT.getValue() + " / " + PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue() + " / "
					+ PaymentCodeENUM.APPLE_PAY.getValue() + ") but found: ("
					+ quoteResponse.getResponse().getSelectedPaymentMethod() + ")");
			return resp;
		}
		resp.setStatus(true);
		resp.setQuoteResponse(quoteResponse);
		return resp;
	
	}

	private CreateOrderResponseDTO getQuotev7(CreateOrderRequestV2 request, String tokenHeader, String xSource,
											Map<String, String> requestHeader, String xHeaderToken, String xClientVersion,
											final String xHeaderCloudMethod, String deviceId) {
		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();

		QuoteV7Response quoteResponse = externalQuoteHelper.fetchQuotev7(request.getQuoteId(), request.getCustomerId(),
				request.getStoreId(), tokenHeader, false, xHeaderToken, xSource, xClientVersion,request.isRetryPaymentReplica(), requestHeader.get(Constants.deviceId) );

		/*
		 * Validations: quote not available - checked payment method available - checked
		 * products available - checked store credit applied in case of FREE payment
		 * method
		 *
		 */

		if (null == quoteResponse || !quoteResponse.getStatus() || quoteResponse.getStatusCode().equals("203")) {
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Error: There is an issue found in Quote!");
			return resp;
		} else if (!quoteResponse.getStatus().booleanValue()
				|| !quoteResponse.getStatusCode().equals("200")) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
			return resp;
		} else if (null != quoteResponse.getResponse()
				&& (quoteResponse.getResponse().getSelectedPaymentMethod() == null
				|| quoteResponse.getResponse().getSelectedPaymentMethod().equals("")) && (request.getIsApplePay() == null || !request.getIsApplePay().equals(1))) {

			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error: Quote did not have payment method! for quote: " + request.getQuoteId());
			return resp;
		} else if (null != quoteResponse.getResponse()
				&& quoteResponse.getStatusCode().equals("200")
				&& StringUtils.isNotBlank(quoteResponse.getResponse().getCustomerId())) {

			authenticateCheck(requestHeader, Integer.valueOf(quoteResponse.getResponse().getCustomerId()));
		} else if (quoteResponse.getResponse() != null && "true".equals(xHeaderCloudMethod)
				&& PaymentCodeENUM.CASH_ON_DELIVERY.getValue()
				.equals(quoteResponse.getResponse().getSelectedPaymentMethod())) {
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Error: Quote have invalid payment method for X-Header-Cloud-Method, expected: ("
					+ PaymentCodeENUM.MD_PAYFORT.getValue() + " / " + PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue() + " / "
					+ PaymentCodeENUM.APPLE_PAY.getValue() + ") but found: ("
					+ quoteResponse.getResponse().getSelectedPaymentMethod() + ")");
			return resp;
		}
		resp.setStatus(true);
		resp.setQuoteV7Response(quoteResponse);
		return resp;

	}

  	/**
  	 * For PROXY order get the coupon detail from sales order JSON.
  	 * @param order
  	 * @param quote
  	 */
	private void updateCouponReedme(SalesOrder order, ProxyOrder proxyOrder) {
		try {
			if(null != proxyOrder && null != proxyOrder.getSalesOrder()) {
				
				SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
				String trackingId = salesOrder.getSubSalesOrder().getExternalCouponRedemptionTrackingId();
				Integer trackingStatus = salesOrder.getSubSalesOrder().getExternalCouponRedemptionStatus();
				order.getSubSalesOrder().setExternalCouponRedemptionTrackingId(trackingId);
				order.getSubSalesOrder().setExternalCouponRedemptionStatus(trackingStatus);
				// update the warehouse id from the proxy order sales order json to sales order items
				order.getSalesOrderItem().stream().forEach(orderItem -> {
							salesOrder.getSalesOrderItem().stream().forEach(proxyOrderItem -> {
								if(orderItem.getSku().equals(proxyOrderItem.getSku())) {
									orderItem.setWarehouseLocationId(proxyOrderItem.getWarehouseLocationId());
								}
							});
						}
				);
				salesOrderRepository.saveAndFlush(order);
			}
			
		} catch (Exception e) {
			LOGGER.error("Error In Coupon Reedmention. Error : " + e);
		}
	}

	/**
	 * For PROXY order get the coupon detail from sales order JSON.
	 * @param order
	 * @param quote
	 */
	private void updateCouponReedmeV3(SalesOrder order, ProxyOrder proxyOrder) {
		try {
			if(null != proxyOrder && null != proxyOrder.getSalesOrder()) {

				SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
				String trackingId = salesOrder.getSubSalesOrder().getExternalCouponRedemptionTrackingId();
				Integer trackingStatus = salesOrder.getSubSalesOrder().getExternalCouponRedemptionStatus();
				order.getSubSalesOrder().setExternalCouponRedemptionTrackingId(trackingId);
				order.getSubSalesOrder().setExternalCouponRedemptionStatus(trackingStatus);
				salesOrderRepository.saveAndFlush(order);
			}

		} catch (Exception e) {
			LOGGER.error("Error In Coupon Reedmention. Error : " + e);
		}
	}


	private CreateOrderResponseDTO checkInventory(CreateOrderRequestV2 request, QuoteDTO quote,
												  ProductInventoryRes invResponse) {
		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();
		if (invResponse == null || invResponse.getResponse() == null) {
			resp.setStatus(false);
			resp.setStatusCode("210");
			resp.setStatusMsg(Constants.ERROR_MSG);
			ErrorType error = new ErrorType();
			error.setErrorCode("210");
			error.setErrorMessage("Error: Inventory data not received! for quote: " + request.getQuoteId());
			resp.setError(error);
			return resp;
		}
		Map<String, Integer> skuInventoryMap = invResponse.getResponse().stream().collect(
				Collectors.toMap(ProductValue::getSku, e -> (int) Double.parseDouble(e.getValue()), (a1, a2) -> a1));
		LOGGER.info("inventory for requested products" + skuInventoryMap);
		for (CatalogProductEntityForQuoteDTO quoteProduct : quote.getProducts()) {
			if (Integer.parseInt(quoteProduct.getQuantity()) > skuInventoryMap.get(quoteProduct.getSku())) {
				resp.setStatus(false);
				resp.setStatusCode("203");
				resp.setStatusMsg("Error: Some items have insufficient inventory!");
				ErrorType error = new ErrorType();
				error.setErrorCode("203");
				error.setErrorMessage("Some items have insufficient inventory!");
				resp.setError(error);
				return resp;
			}
		}
		return null;
	}

	private CreateOrderResponseDTO checkInventoryV2(CreateOrderRequestV2 request, QuoteV7DTO quote,
													ProductStatusRequestV2 productStatusReq) {
		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();
		ProductInventoryResV2 invResponse = getInventoryV2(productStatusReq);
		if (invResponse == null || invResponse.getResponse() == null) {
			resp.setStatus(false);
			resp.setStatusCode("210");
			resp.setStatusMsg(Constants.ERROR_MSG);
			ErrorType error = new ErrorType();
			error.setErrorCode("210");
			error.setErrorMessage("Error: Inventory data not received! for quote: " + request.getQuoteId());
			resp.setError(error);
			return resp;
		}
		Map<String, Integer> skuInventoryMap = invResponse.getResponse().stream().collect(
				Collectors.toMap(ResponseItem::getSku, e -> (int) Double.parseDouble(e.getValue()), (a1, a2) -> a1));
		LOGGER.info("inventory for requested products" + skuInventoryMap);
		for (ProductEntityForQuoteV7DTO quoteProduct : quote.getProducts()) {
			if (Integer.parseInt(quoteProduct.getQuantity()) > skuInventoryMap.get(quoteProduct.getSku())) {
				resp.setStatus(false);
				resp.setStatusCode("203");
				resp.setStatusMsg("Error: Some items have insufficient inventory!");
				ErrorType error = new ErrorType();
				error.setErrorCode("203");
				error.setErrorMessage("Some items have insufficient inventory!");
				resp.setError(error);
				return resp;
			}
		}
		return null;
	}


  	/**
  	 * Create Proxy order for BNPL Payment methods
  	 * @param request
  	 * @param incrementId
  	 * @param resp
  	 * @param quote
  	 * @param paymentMethod
  	 * @return
  	 */
	private CreateOrderResponseDTO createOrderProxy(CreateOrderRequestV2 request, SalesOrder order, QuoteDTO quote,
			String paymentMethod, Stores store) {
		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();
		if (quote.getTabbyPaymentId() == null || quote.getTabbyPaymentId().equals("")) {
			resp.setStatus(false);
			resp.setStatusCode("216");
			resp.setStatusMsg("Payment Id is missing" + request.getQuoteId());
			return resp;
		} else {
			if(!request.isRetryPaymentReplica()) {
				
				orderHelper.blockInventory(order);
			}
			if(StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData()))
	    		orderHelperV2.reedmeExternalCoupon(quote, store, order, true);
			ProxyOrder proxyOrder = orderHelperV2.createProxyOrder(quote, paymentMethod, order, request);
			LOGGER.info("Proxy order created! " + new Gson().toJson(proxyOrder));
			CreateOrderResponse orderResponse = new CreateOrderResponse();
			orderResponse.setOrderId(proxyOrder.getId().toString());
			orderResponse.setIncrementId(proxyOrder.getIncrementId());
			orderResponse.setQuoteId(proxyOrder.getQuoteId());
			orderResponse.setTabbyPaymentId(proxyOrder.getPaymentId());
			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Proxy Order created successfully!");
			resp.setResponse(orderResponse);
			return resp;
		}
	}

	/**
	 * Create Proxy order for BNPL Payment methods
	 * @param request
	 * @param incrementId
	 * @param resp
	 * @param quote
	 * @param paymentMethod
	 * @return
	 */
	private CreateOrderResponseDTO createV3OrderProxy(CreateOrderRequestV2 request, SalesOrder order,QuoteDTO quoteDTO, QuoteV7DTO quoteV7DTO,
													String paymentMethod, Stores store) {
		CreateOrderResponseDTO resp = new CreateOrderResponseDTO();
		if (quoteV7DTO.getTabbyPaymentId() == null || quoteV7DTO.getTabbyPaymentId().equals("")) {
			resp.setStatus(false);
			resp.setStatusCode("216");
			resp.setStatusMsg("Payment Id is missing" + request.getQuoteId());
			return resp;
		} else {
			if(!request.isRetryPaymentReplica()) {

				orderHelper.blockInventory(order);
			}
			if(StringUtils.isNotBlank(order.getSubSalesOrder().getDiscountData()))
				orderHelperV2.reedmeExternalCoupon(quoteDTO, store, order, true);
			ProxyOrder proxyOrder = orderHelperV2.createV3ProxyOrder(quoteV7DTO, paymentMethod, order, request);
			LOGGER.info("Proxy order created! " + new Gson().toJson(proxyOrder));
			CreateOrderResponse orderResponse = new CreateOrderResponse();
			orderResponse.setOrderId(proxyOrder.getId().toString());
			orderResponse.setIncrementId(proxyOrder.getIncrementId());
			orderResponse.setQuoteId(proxyOrder.getQuoteId());
			orderResponse.setTabbyPaymentId(proxyOrder.getPaymentId());
			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Proxy Order created successfully!");
			resp.setResponse(orderResponse);
			return resp;
		}
	}


  @Override
	public Boolean authenticateCheck(@RequestHeader Map<String, String> requestHeader, Integer customerId) {


		Boolean authenticate = false;
		LOGGER.info("customer id:" + customerId);
		if(null != customerId) {

			String jwtToken = null;
			String headerEmail = null;

			for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
	            String k = entry.getKey();
	            String v = entry.getValue();

	            if ("Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {

	            	 jwtToken = v.substring(4);

	            }

	            if ("X-Header-Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {

	            	String trimEmail  = v;

	            	 headerEmail = getEmailFromHeader(trimEmail);

	            }
	        }
			JwtUser jwtUser = validator.validate(jwtToken);
			
			Boolean jwtRefreshTokenFlag = false ;
			if(null != jwtUser) {
				
				jwtRefreshTokenFlag = jwtUser.getRefreshToken();
			}
			boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader,jwtRefreshTokenFlag);
			if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
					|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
				headerEmail = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
			}

			if (jwtUser == null) {

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
			} else if(null != jwtUser.getCustomerId() && ( !customerId.equals(jwtUser.getCustomerId()))){

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

			}else if(null != headerEmail  && null !=jwtUser.getUserId()  && ! headerEmail.equalsIgnoreCase(jwtUser.getUserId())){

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
				
			} else if(null != customerId && null == jwtUser.getCustomerId()&& Constants.IS_JWT_TOKEN_ENABLE){

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

			}


		}
		return authenticate;
	}

	   private  String getEmailFromHeader(String inputEmail) {
	        String result = inputEmail;
	        try {
	        	 String[] chunks = inputEmail.split("_");
	            if(chunks!=null && chunks.length>1) {
	                ArrayList<String> chunksList = new ArrayList<>(Arrays.asList(chunks));
	                for (int i = (chunksList.size()-1); i > (-1); i--) {
	                    final String item = chunksList.get(i);
	                    if(StringUtils.isNumericSpace(item)) {
	                        chunksList.remove(i);
	                    }else {
	                        break;
	                    }
	                }
	                String value = String.join("_", chunksList);
	                if(value!=null) {
	                    result = value.trim();
	                } else {
	                    result = "";
	                }
	            }
	        } catch (Exception e)  {
	        	  LOGGER.error("Error while getting Email from Hearder"+e.getMessage());
	        }
	        return result;
	    }

	@Override
	@Transactional
	public AddStoreCreditResponse addStoreCredit(@Valid AddStoreCreditRequest request) {
		
		 List<Stores> stores = Constants.getStoresList();
		 AddStoreCreditResponse response = new AddStoreCreditResponse();
		 if(request==null || CollectionUtils.isEmpty(request.getStoreCredits())) {
             response.setStatus(false);
             response.setStatusCode("400");
             response.setStatusMsg("Invalid Request!");

             return response;
         }
		 Boolean status = true;
		 String statusCode = "200";
		 String statusMsg = "All store credits updated successfully!";
		 ArrayList<StoreCreditResponse> responses = new ArrayList<>();
		 int rowCount = 2;

         Integer amountRestriction = null;
        GetOrderConsulValues orderCredentials = Constants.orderCredentials;
        OrderKeyDetails orderKeyDetails = orderCredentials.getOrderDetails();
        if (orderKeyDetails != null) {
            if (Objects.equals(request.getUpdateRequestType(), "bulk") && orderKeyDetails.getStyliCreditBulkUpdateAmount() != null) {
                amountRestriction = Integer.parseInt(orderKeyDetails.getStyliCreditBulkUpdateAmount());
            }
            if (Objects.equals(request.getUpdateRequestType(), "oms") && orderKeyDetails.getStyliCreditOmsUpdateAmount() != null) {
                amountRestriction = Integer.parseInt(orderKeyDetails.getStyliCreditOmsUpdateAmount());
            }
        }

        for (StoreCredit storeCredit : request.getStoreCredits()) {


            StoreCreditResponse storeCreditResponse = new StoreCreditResponse();
            CustomerEntity customer = null;
            if(storeCredit == null) {
                storeCreditResponse.setStatus(false);
                storeCreditResponse.setStatusCode("201");
                storeCreditResponse.setStatusMsg("null storeCredit object found!");
                storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
                responses.add(storeCreditResponse);
                status = false;
                statusCode = "202";
                statusMsg = Constants.CREDIT_FAILED_REQ;
                rowCount++;
                continue;
            }
            if(null != storeCredit.getCustomerId()) {

        		customer  = orderHelper.getCustomerDetails(storeCredit.getCustomerId(),null);

            }else if(StringUtils.isNotBlank(storeCredit.getEmailId())) {

            	customer = orderHelper.getCustomerDetails(null,storeCredit.getEmailId());

            }


			if (null !=customer && null == customer.getEntityId() ) {

				storeCreditResponse.setStatus(false);
				storeCreditResponse.setStatusCode("201");
				storeCreditResponse.setStatusMsg("Invalid customer");
				storeCreditResponse.setEmailId(storeCredit.getEmailId());
				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
				responses.add(storeCreditResponse);
				status = false;
				statusCode = "207";
				statusMsg = Constants.CREDIT_FAILED_REQ;
				rowCount++;
				continue;

			}

            storeCreditResponse.setCustomerId(customer.getEntityId());
            storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
            storeCreditResponse.setEmailId(storeCredit.getEmailId());
            storeCreditResponse.setRowNumber(rowCount);
            storeCreditResponse.setActualRequest(storeCredit);
            Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeCredit.getStoreId()))
                    .findAny().orElse(null);



            if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
                storeCreditResponse.setStatus(false);
                storeCreditResponse.setStatusCode("202");
                storeCreditResponse.setStatusMsg("Invalid store name");
                storeCreditResponse.setEmailId(storeCredit.getEmailId());
				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
                responses.add(storeCreditResponse);
                status = false;
                statusCode = "202";
                statusMsg = Constants.CREDIT_FAILED_REQ;
                rowCount++;
                continue;
            }

            if (null == storeCredit.getStoreCredit()) {
                storeCreditResponse.setStatus(false);
                storeCreditResponse.setStatusCode("208");
                storeCreditResponse.setStatusMsg(Constants.INVALID_CURRENCY_MSG);
                storeCreditResponse.setEmailId(storeCredit.getEmailId());
				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
                responses.add(storeCreditResponse);
                status = false;
                statusCode = "208";
                statusMsg = Constants.CREDIT_FAILED_REQ;
                rowCount++;
                continue;
            }else if(null != storeCredit.getStoreCredit() && storeCredit.getStoreCredit().signum()==0 ) {

            	 storeCreditResponse.setStatus(false);
                 storeCreditResponse.setStatusCode("209");
                 storeCreditResponse.setStatusMsg(Constants.INVALID_CURRENCY_MSG);
                 storeCreditResponse.setEmailId(storeCredit.getEmailId());
 				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
 				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
                 responses.add(storeCreditResponse);
                 status = false;
                 statusCode = "209";
                 statusMsg = Constants.CREDIT_FAILED_REQ;
                 rowCount++;
                 continue;
            }

            if (null == storeCredit.getStoreCredit()) {
                storeCreditResponse.setStatus(false);
                storeCreditResponse.setStatusCode("208");
                storeCreditResponse.setStatusMsg(Constants.INVALID_CURRENCY_MSG);
                storeCreditResponse.setEmailId(storeCredit.getEmailId());
				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
                responses.add(storeCreditResponse);
                status = false;
                statusCode = "208";
                statusMsg = Constants.CREDIT_FAILED_REQ;
                rowCount++;
                continue;
            }else if(null != storeCredit.getStoreCredit() && storeCredit.getStoreCredit().signum()==0) {

            	 storeCreditResponse.setStatus(false);
                 storeCreditResponse.setStatusCode("209");
                 storeCreditResponse.setStatusMsg(Constants.INVALID_CURRENCY_MSG);
                 storeCreditResponse.setEmailId(storeCredit.getEmailId());
 				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
 				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);
                 responses.add(storeCreditResponse);
                 status = false;
                 statusCode = "209";
                 statusMsg = Constants.CREDIT_FAILED_REQ;
                 rowCount++;
                 continue;
            }

             if (amountRestriction != null && null !=storeCredit.getStoreCredit() && storeCredit.getStoreCredit().intValue() > amountRestriction) {
                 storeCreditResponse.setStatus(false);
                 storeCreditResponse.setStatusCode("203");
                 storeCreditResponse.setStatusMsg("Amount added cannot be greater than " + amountRestriction);
                 storeCreditResponse.setCustomerId(customer.getEntityId());
                 storeCreditResponse.setEmailId(storeCredit.getEmailId());
 				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
 				storeCreditResponse.setRowNumber(rowCount);
                 storeCreditResponse.setActualRequest(storeCredit);
                 responses.add(storeCreditResponse);
                 status = false;
                 statusCode = "202";
                 statusMsg = Constants.CREDIT_FAILED_REQ;
                 rowCount++;
                 continue;
             }


            try {

                List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
                        .findByCustomerId(customer.getEntityId());
                AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty()? amastyStoreCredits.get(0) : null;
                BigDecimal convertedStoreCredit ;

                /*
                 * Store Credit summation calculation code start.
                 */
                if (amastyStoreCredit == null) {
                    amastyStoreCredit = new AmastyStoreCredit();
                    amastyStoreCredit.setCustomerId(customer.getEntityId());
                }
                LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
                convertedStoreCredit = storeCredit.getStoreCredit()
                        .multiply(store.getCurrencyConversionRate());

                BigDecimal storeCreditValue =  amastyStoreCredit.getStoreCredit();
                if(storeCreditValue == null) {
                    storeCreditValue = new BigDecimal(0);
                }
                storeCreditValue = storeCreditValue.abs();
                BigDecimal returnableStoreCreditValue = BigDecimal.ZERO;
                if (ObjectUtils.isNotEmpty(amastyStoreCredit.getReturnableAmount()))
                    returnableStoreCreditValue = amastyStoreCredit.getReturnableAmount().abs();
                BigDecimal nonReturnableBuffer = storeCreditValue.subtract(returnableStoreCreditValue);
                boolean isNegative = (convertedStoreCredit.compareTo(new BigDecimal(0)) < 0);
                convertedStoreCredit = convertedStoreCredit.abs();
                if(isNegative) {
                    if(convertedStoreCredit.compareTo(storeCreditValue) > 0) {
                        storeCreditResponse.setStatus(false);
                        storeCreditResponse.setStatusCode("204");
                        storeCreditResponse.setStatusMsg("Amount entered to deduct is greater than current wallet balance");
                        storeCreditResponse.setCustomerId(customer.getEntityId());
                        storeCreditResponse.setEmailId(storeCredit.getEmailId());
        				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
        				storeCreditResponse.setRowNumber(rowCount);
                        storeCreditResponse.setActualRequest(storeCredit);
                        responses.add(storeCreditResponse);
                        status = false;
                        statusCode = "202";
                        statusMsg = Constants.CREDIT_FAILED_REQ;
                        rowCount++;
                        continue;
                    } else {
                        storeCreditValue = storeCreditValue.subtract(convertedStoreCredit);
                        deductReturnableAmount(storeCreditValue,
                                convertedStoreCredit,
                                returnableStoreCreditValue,
                                nonReturnableBuffer,
                                storeCredit.isReturnableToBank(),
                                amastyStoreCredit);
                    }
                } else {
                    storeCreditValue = storeCreditValue.add(convertedStoreCredit);
                    if(storeCredit.isReturnableToBank()) {
                        returnableStoreCreditValue = returnableStoreCreditValue.add(convertedStoreCredit);
                        amastyStoreCredit.setReturnableAmount(returnableStoreCreditValue);
                    }
                }
                amastyStoreCredit.setStoreCredit(storeCreditValue);
                amastyStoreCreditRepository.saveAndFlush(amastyStoreCredit);

                storeCreditResponse.setStatus(true);
                storeCreditResponse.setStatusCode("200");
                storeCreditResponse.setStatusMsg("Store credit updated successfully!");
                storeCreditResponse.setCustomerId(customer.getEntityId());
                storeCreditResponse.setEmailId(storeCredit.getEmailId());
				storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
				storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setActualRequest(storeCredit);

                /*
                 * Store Credit summation calculation code end.
                 */


                List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository
                        .findByCustomerId(customer.getEntityId());
                int newCustomerHistoryId = 1;
                if (CollectionUtils.isNotEmpty(histories)) {
                    AmastyStoreCreditHistory lastHistory = histories.get(histories.size() - 1);
                    newCustomerHistoryId = lastHistory.getCustomerHistoryId() + 1;
                }
                AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
                history.setCustomerHistoryId(newCustomerHistoryId);
                history.setMessage(storeCredit.getComment());
                history.setCustomerId(customer.getEntityId());
                history.setDeduct((isNegative)? 1 : 0);

                history.setDifference(convertedStoreCredit);
                history.setStoreCreditBalance(storeCreditValue);

                if(ObjectUtils.isNotEmpty(request.getStyliCreditType()))  {
                    switch (request.getStyliCreditType()) {
                        case CHANGED_BY_ADMIN: {
                            history.setAction(1);
                            break;
                        }
						case BANK_TRANSFER: {
							history.setAction(2);
							break;
						}
                        case ADMIN_REFUND: {
                            history.setAction(7);
                            break;
                        }
                        case FINANCE_BULK_CHANGES: {
                            history.setAction(8);
                            break;
                        }
                        default:
                        case REFERRAL: {
                            history.setAction(6);
                            break;
                        }
                        case BLANK_ACTION: {
                            history.setAction(0);
                            break;
                        }
						case BRAZE_UPDATE: {
							history.setAction(9);
							break;
						}
                    }
                }

                history.setActionData("[\"" + storeCredit.getReferenceNumber() + "\"]");
                history.setCreatedAt(new Timestamp(new Date().getTime()));
                history.setStoreId(storeCredit.getStoreId());
                amastyStoreCreditHistoryRepository.saveAndFlush(history);

            }catch(DataAccessException de) {

                LOGGER.error("exception occoured during update styli credit:"+de.getMessage());

                storeCreditResponse.setStatus(false);
                storeCreditResponse.setStatusCode("205");
                storeCreditResponse.setStatusMsg("Exception occured!");
                storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setEmailId(storeCredit.getEmailId());
                storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
                storeCreditResponse.setActualRequest(storeCredit);
                responses.add(storeCreditResponse);
                status = false;
                statusCode = "202";
                statusMsg = Constants.CREDIT_FAILED_REQ;
                rowCount++;
                continue;
            }catch(Exception de) {

                LOGGER.error("exception occoured during update styli exception:"+de.getMessage());

                storeCreditResponse.setStatus(false);
                storeCreditResponse.setStatusCode("207");
                storeCreditResponse.setStatusMsg("Exception occured!");
                storeCreditResponse.setRowNumber(rowCount);
                storeCreditResponse.setEmailId(storeCredit.getEmailId());
                storeCreditResponse.setReferenceNumber(storeCredit.getReferenceNumber());
                storeCreditResponse.setActualRequest(storeCredit);
                responses.add(storeCreditResponse);
                status = false;
                statusCode = "202";
                statusMsg = Constants.CREDIT_FAILED_REQ;
                rowCount++;
                continue;
            }
            responses.add(storeCreditResponse);
            rowCount =rowCount+1;
        }

        response.setStatus(status);
        response.setStatusCode(statusCode);
        response.setStatusMsg(statusMsg);
        response.setResponse(responses);

        LOGGER.info("Wallet updated processed. Details : " + response);
        return response;
	}

    /**
     * @param storeCreditValue
     * @param convertedStoreCredit       BigDecimal (amount which is supposed to be deducted)
     * @param returnableStoreCreditValue BigDecimal (current returnable balance in db)
     * @param nonReturnableBuffer        BigDecimal (difference of main balance and returnable balance)
     * @param returnableToBank           boolean (from request)
     * @param amastyStoreCredit          AmastyStoreCredit
     */
    private void deductReturnableAmount(BigDecimal storeCreditValue,
                                        BigDecimal convertedStoreCredit,
                                        BigDecimal returnableStoreCreditValue,
                                        BigDecimal nonReturnableBuffer,
                                        boolean returnableToBank,
                                        AmastyStoreCredit amastyStoreCredit) {

        /**
         * main balance = 50
         * returnable balance = 10
         * deduct = -15
         * abs = 15
         * nonReturnableBuffer = 40
         *
         * sets available balance = 50-15 = 35
         *
         * returnable checked true
         * max(10-15, 0) = 0
         *
         * returnable checked false
         * (40-10).signum() < 0 then set 35     // no exec
         *
         *
         * main balance = 50
         * returnable balance = 20
         * -40
         * abs = 40
         * nonReturnableBuffer = 50-20 = 30
         *
         * sets available balance = 50-40 = 10
         *
         * returnable checked true
         * max(20-40, 0) = 0
         *
         * returnable checked false
         * (30-40).signum() < 0 then set 10     // sets 10
         *
         * main balance = 15
         * returnable balance = 5
         * -12
         * nonReturnableBuffer = 15-5 = 10
         * sets available balance = 15-12 = 3
         * returnable checked true
         * max(5-12, 0) = 0
         * returnable checked false
         * (10-12).signum() < 0 then set 3		// sets 3
		 *
		 * main balance = 651
		 * returnable balance = 651
		 * -97
		 * nonReturnableBuffer = 651-651 = 0
		 * sets available balance = 651-97 = 554
		 * returnable checked true
		 * max(651-97, 0) = 554
		 * returnable checked false
		 * (0-97).signum() < 0 then set 554		// sets 554
         */

        if (returnableToBank) {
            returnableStoreCreditValue = returnableStoreCreditValue.subtract(convertedStoreCredit);
            amastyStoreCredit.setReturnableAmount(returnableStoreCreditValue.max(BigDecimal.ZERO));
        } else {
            BigDecimal returnableAmountOverflow = nonReturnableBuffer.subtract(convertedStoreCredit);
            if (ObjectUtils.isNotEmpty(returnableAmountOverflow) && returnableAmountOverflow.signum() < 0) {
//                amastyStoreCredit.setReturnableAmount(returnableAmountOverflow.abs());
//				if(storeCreditValue.compareTo(convertedStoreCredit) < 0) {
//					amastyStoreCredit.setReturnableAmount(storeCreditValue.min(convertedStoreCredit));
//				} else {
					amastyStoreCredit.setReturnableAmount(storeCreditValue);
//				}
            }
        }

    }

    @Override
	public Boolean authenticateOrderCheck(@RequestHeader Map<String, String> requestHeader, Integer customerId) {


		Boolean authenticate = false;
		LOGGER.info("customer id:" + customerId);
		if(null != customerId) {

			String jwtToken = null;
			String headerEmail = null;

			for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
	            String k = entry.getKey();
	            String v = entry.getValue();

	            if ("Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {

	            	 jwtToken = v.substring(4);

	            }

	            if ("X-Header-Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {

	            	String trimEmail  = v;

	            	 headerEmail = getEmailFromHeader(trimEmail);

	            }
	        }
			JwtUser jwtUser = validator.validate(jwtToken);
			Boolean jwtRefreshTokenFlag = false ;
			if(null != jwtUser) {

				jwtRefreshTokenFlag = jwtUser.getRefreshToken();
			}
			boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader,jwtRefreshTokenFlag);
			if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
					|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
				headerEmail = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
			}

			if (jwtUser == null) {

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
			} else if(null != jwtUser.getCustomerId() && ( !customerId.equals(jwtUser.getCustomerId()) && !jwtUser.getIsOldToken())){

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

			}else if(null != headerEmail  && null !=jwtUser.getUserId()  && ! headerEmail.equalsIgnoreCase(jwtUser.getUserId()) && !jwtUser.getIsOldToken()){

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

			} else if(null != customerId && null == jwtUser.getCustomerId()){

				throw new BadRequestException("403", EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

			}


		}
		LOGGER.info("authenticate flag value"+ authenticate);
		return authenticate;
	}


	 /**
		 * @param productStatusRequest
		 * @return
		 */
		public ProductInventoryRes getInventoryQty(ProductStatusRequest productStatusRequest) {


			ProductInventoryRes body = new ProductInventoryRes();

			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
			requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

			HttpEntity<ProductStatusRequest> requestBody = new HttpEntity<>(productStatusRequest,requestHeaders);
			String url = "";

			 if(null != Constants.orderCredentials && null !=
		        		Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
			         url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()+ "/api/inventory/storefront/atp";
		        }
			LOGGER.info(" GET Inventory URL:" + url);

			try {

				LOGGER.info("inventory request body:"+mapper.writeValueAsString(requestBody.getBody()));
				ResponseEntity<ProductInventoryRes> responseBody = restTemplate.exchange(
						url, HttpMethod.POST, requestBody,
						ProductInventoryRes.class);
				LOGGER.info("inventory response body:"+mapper.writeValueAsString(responseBody.getBody()));
				if (responseBody.getStatusCode() == HttpStatus.OK) {
					 body = responseBody.getBody();

				}else{

					LOGGER.error("Error  from InventoryFetch:" + body.getStatusMsg());
				}

			} catch (Exception e) {

				LOGGER.error("Exception occurred:" + e.getMessage());

			}

			return body;
		}

	/**
	 * @param productStatusRequest
	 * @return
	 */
	public ProductInventoryResV2 getInventoryV2(ProductStatusRequestV2 productStatusRequest) {


		ProductInventoryResV2 body = new ProductInventoryResV2();

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

		HttpEntity<ProductStatusRequestV2> requestBody = new HttpEntity<>(productStatusRequest,requestHeaders);
		String url = "";

		if(null != Constants.orderCredentials && null !=
				Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
			url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()+ "/api/inventory/storefront/v2/atp";
		}
		LOGGER.info(" GET Inventory URL:" + url);

		try {

			LOGGER.info("inventory request body:"+mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<ProductInventoryResV2> responseBody = restTemplate.exchange(
					url, HttpMethod.POST, requestBody,
					ProductInventoryResV2.class);
			LOGGER.info("inventory response body:"+mapper.writeValueAsString(responseBody.getBody()));
			if (responseBody.getStatusCode() == HttpStatus.OK) {
				body = responseBody.getBody();

			}else{

				LOGGER.error("Error  from InventoryFetch:" + body.getMeta().getMessage());
			}

		} catch (Exception e) {

			LOGGER.error("Exception occurred:" + e.getMessage());

		}

		return body;
	}

		public String getAuthorization(String authToken) {

			String token = null;

			if (StringUtils.isNotEmpty(authToken) &&  (authToken.contains(","))) {

					List<String> authTokenList = Arrays.asList(authToken.split(","));

					if (CollectionUtils.isNotEmpty(authTokenList)) {

						token = authTokenList.get(0);
					}
				
			}

			return token;
		}

		@Override
		@Transactional
		public int updateRatingStatus(String ratingStatus, Integer orderId) {
			SalesOrder salesOrder = salesOrderRepository.findByEntityId(orderId);
			if (null != salesOrder) {
				return salesOrderRepository.updateRatingStatus(ratingStatus, orderId);
			} else {
				SplitSalesOrder splitSalesOrder =splitSalesOrderRepository.findByEntityId(orderId);
				if (null!=splitSalesOrder)
					return splitSalesOrderRepository.updateRatingStatus(ratingStatus, orderId);
				else
					return 0;
			}
		}

		@Override
		public QuoteUpdateDTO createQuoteReplica(CreateReplicaQuoteV4Request request, String tokenHeader, String deviceId) {
			QuoteUpdateDTO response = new QuoteUpdateDTO();
			SalesOrder order = null;
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
					.findAny().orElse(null);
			if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg(Constants.STORE_NOT_FOUND_MSG);
				return response;
			}
			Long proxyOrderId = Objects.isNull(request.getOrderId()) ? 0 : Long.valueOf(request.getOrderId());
			Optional<ProxyOrder> proxyOrder = proxyOrderRepository.findByIdOrPaymentId(proxyOrderId, request.getTabbyPaymentId());
			String reasonToUnlockShukranPoints="Unlock Shukran Points At Create Replica";
			if(proxyOrder.isPresent()) {
				ProxyOrder proxyOrd = proxyOrder.get();
				try {
					SalesOrder salesOrd = salesOrderRepository.findByIncrementId(proxyOrd.getIncrementId());
					if(Objects.nonNull(salesOrd)){
						LOGGER.info("Replica alredy executed for this quote : " + proxyOrd.getQuoteId() + " Payment ID : "
								+ proxyOrd.getPaymentId());
						response.setStatus(true);
						response.setStatusMsg("Replica alredy executed for this quote : " + proxyOrd.getQuoteId());
						response.setStatusCode("200");
						return response;
					}
					paymentUtility.initiateReplica(order, proxyOrd.getPaymentId(), proxyOrd, deviceId);
					String shukranPointsToUnlock=null;
					String shukranCartId= null;
					String profileId= null;
					SalesOrder salesOrder = mapper.readValue(proxyOrd.getSalesOrder(), SalesOrder.class);
					if(salesOrder.getSubSalesOrder() != null && salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned() != null && salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0){
						shukranPointsToUnlock = salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned().toString();
						shukranCartId= salesOrder.getSubSalesOrder().getQuoteId();
						profileId = salesOrder.getSubSalesOrder().getCustomerProfileId();
					}
					if(StringUtils.isEmpty(shukranPointsToUnlock) && StringUtils.isBlank(shukranPointsToUnlock) && StringUtils.isNotEmpty(proxyOrd.getQuote()) && StringUtils.isNotBlank(proxyOrd.getQuote())){
						try {
							if (null != salesOrder.getIsSplitOrder() && Integer.valueOf(1).equals(salesOrder.getIsSplitOrder())) {
								//v3 create
								QuoteV7DTO quoteDTO = mapper.readValue(proxyOrd.getQuote(), QuoteV7DTO.class);
								if(quoteDTO != null && quoteDTO.getTotalShukranBurn() != null && quoteDTO.getTotalShukranBurn().compareTo(BigDecimal.ZERO)>0){
									shukranCartId = StringUtils.isNotBlank(Constants.orderCredentials.getShukranCartIdPrefix()) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranCartIdPrefix()) ? Constants.orderCredentials.getShukranCartIdPrefix() + quoteDTO.getQuoteId() : quoteDTO.getQuoteId();
									shukranPointsToUnlock=quoteDTO.getTotalShukranBurn().toString();
									profileId= quoteDTO.getProfileId();
								}
							} else {  // v2 create
								QuoteDTO quoteDTO = mapper.readValue(proxyOrd.getQuote(), QuoteDTO.class);
								if(quoteDTO != null && quoteDTO.getTotalShukranBurn() != null && quoteDTO.getTotalShukranBurn().compareTo(BigDecimal.ZERO)>0){
									shukranCartId = StringUtils.isNotBlank(Constants.orderCredentials.getShukranCartIdPrefix()) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranCartIdPrefix()) ? Constants.orderCredentials.getShukranCartIdPrefix() + quoteDTO.getQuoteId() : quoteDTO.getQuoteId();
									shukranPointsToUnlock=quoteDTO.getTotalShukranBurn().toString();
									profileId= quoteDTO.getProfileId();
								}
							}
						} catch (Exception e) {
							LOGGER.error("Error while parsing Proxy Order Quote JSON. " + e.getMessage(),e);
						}
					}
					if(StringUtils.isNotBlank(shukranPointsToUnlock) && StringUtils.isNotEmpty(shukranPointsToUnlock)){
						commonService.lockUnlockShukranData(profileId, shukranPointsToUnlock, shukranCartId, false, null, null, null, "");

					}
					QuoteUpdateDTOV2 quoteResponse = externalQuoteHelper.enableExternalQuote(proxyOrd.getQuoteId(), salesOrder.getStoreId(), tokenHeader, deviceId);
					if(Objects.nonNull(quoteResponse) && "200".equals(quoteResponse.getStatusCode())){
						response.setQuoteId(quoteResponse.getQuoteId());
						response.setCustomerId(quoteResponse.getCustomerId());
						response.setStatus(true);
						response.setStatusMsg("Proxy Order Quote enabled successfully!");
						response.setStatusCode("200");
					}else {
						response.setStatus(false);
						response.setStatusCode("202");
						response.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
						return response;
					}
					
				} catch (Exception e) {
					LOGGER.error("Error on Proxy Order Replica. " + e);
					response.setStatus(false);
					response.setStatusCode("202");
					response.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
					return response;
				}
			} else {
				
				LOGGER.info("in else part of not proxy order");
				order = salesOrderRepository.findByEntityId(request.getOrderId());
				if (Constants.IS_TABBY_REPLICA_FAILED_TRIGGER) {
					LOGGER.info("order type is Tabby failed replica");
					paymentUtility.initiateReplica(order, order.getSubSalesOrder().getPaymentId(), null, deviceId);
				}
			}
			if (null != order && null != order.getSubSalesOrder() && ! (null != order.getSubSalesOrder().getRetryPayment()
					&& order.getSubSalesOrder().getRetryPayment().equals(1))) {
				LOGGER.info("inside order is in hold order status!");
				String quoteId = null;
				if (null != order.getQuoteId()) {
					quoteId = order.getQuoteId().toString();
				} else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalQuoteId()) {
					quoteId = order.getSubSalesOrder().getExternalQuoteId().toString();
				}
				Integer storeId = order.getStoreId();
				if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSubSalesOrder().getShukranLocked() != null && order.getSubSalesOrder().getShukranLocked().equals(0)){
					commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(),order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(),order.getSubSalesOrder().getQuoteId(), false, order, store, reasonToUnlockShukranPoints, "");
					SubSalesOrder subSalesOrder= order.getSubSalesOrder();
					subSalesOrder.setShukranLocked(1);
					subSalesOrderRepository.saveAndFlush(subSalesOrder);
				}
				QuoteUpdateDTOV2 quoteResponse = externalQuoteHelper.enableExternalQuote(quoteId, storeId, tokenHeader, deviceId);
				if (null == quoteResponse || !quoteResponse.getStatus()
						|| !quoteResponse.getStatusCode().equals("200")) {
					response.setStatus(false);
					response.setStatusCode("202");
					response.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
					return response;
				}
				LOGGER.info("coupon flag: "+Constants.orderCredentials.getOrderDetails().isOrderReplicaReleaseCoupon());
				if (null != order.getSubSalesOrder()
						&& null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
						&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
						&& StringUtils.isNotBlank(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
						&& Constants.orderCredentials.getOrderDetails().isOrderReplicaReleaseCoupon()) {
					
					LOGGER.info("TRACKING ID:"+order.getSubSalesOrder().getExternalCouponRedemptionTrackingId());
					salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, order, true, false);
				}
				SubSalesOrder subSaleOrder = order.getSubSalesOrder();
				if (null != subSaleOrder) {
					subSaleOrder.setExternalQuoteStatus(1);
					subSaleOrder.setSalesOrder(order);
					order.setSubSalesOrder(subSaleOrder);
					salesOrderRepository.saveAndFlush(order);
				}
				response.setQuoteId(quoteResponse.getQuoteId());
				response.setCustomerId(quoteResponse.getCustomerId());
				response.setStatus(true);
				response.setStatusMsg(Constants.QOUTE_ENABLED_MSG);
				response.setStatusCode("200");
				return response;
			}else if (null != order && order.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
				LOGGER.info("inside order is in pending status!");
				String quoteId = null;
				if (null != order.getQuoteId()) {
					quoteId = order.getQuoteId().toString();
				} else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalQuoteId()) {
					quoteId = order.getSubSalesOrder().getExternalQuoteId().toString();
				}
				Integer storeId = order.getStoreId();
				if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSubSalesOrder().getShukranLocked() != null && order.getSubSalesOrder().getShukranLocked().equals(0)){
					commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(),order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(),order.getSubSalesOrder().getQuoteId(), false, order, store, reasonToUnlockShukranPoints, "");
					SubSalesOrder subSalesOrder= order.getSubSalesOrder();
					subSalesOrder.setShukranLocked(1);
					subSalesOrderRepository.saveAndFlush(subSalesOrder);
				}
				QuoteUpdateDTOV2 quoteResponse = externalQuoteHelper.enableExternalQuote(quoteId, storeId, tokenHeader, deviceId);
				if (null == quoteResponse || !quoteResponse.getStatus()
						|| !quoteResponse.getStatusCode().equals("200")) {
					response.setStatus(false);
					response.setStatusCode("202");
					response.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
					return response;
				}
				LOGGER.info("coupon flag: "+Constants.orderCredentials.getOrderDetails().isOrderReplicaReleaseCoupon());
				if (null != order.getSubSalesOrder()
						&& null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
						&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
						&& StringUtils.isNotBlank(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
						&& Constants.orderCredentials.getOrderDetails().isOrderReplicaReleaseCoupon()) {
					LOGGER.info("TRACKING ID:"+order.getSubSalesOrder().getExternalCouponRedemptionTrackingId());
					salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, order, true, false);
				}
				SubSalesOrder subSaleOrder = order.getSubSalesOrder();
				if (null != subSaleOrder) {
					subSaleOrder.setExternalQuoteStatus(1);
					subSaleOrder.setSalesOrder(order);
					order.setSubSalesOrder(subSaleOrder);
					salesOrderRepository.saveAndFlush(order);
				}
				response.setQuoteId(quoteResponse.getQuoteId());
				response.setCustomerId(quoteResponse.getCustomerId());
				response.setStatus(true);
				response.setStatusMsg(Constants.QOUTE_ENABLED_MSG);
				response.setStatusCode("200");
				return response;
			}  else if (proxyOrder.isPresent()){
				response.setStatus(true);
				response.setStatusMsg(Constants.QOUTE_ENABLED_MSG);
				response.setStatusCode("200");
			}else {
				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg("No Order Found for Given Quote ID");
			}

			return response;

		}
		
		@Override
		public QuoteUpdateDTO createRetryPaymentReplica(CreateReplicaQuoteV4Request request, String tokenHeader, String deviceId) {
			QuoteUpdateDTO response = new QuoteUpdateDTO();
			SalesOrder order = null;
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
					.findAny().orElse(null);
			if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg(Constants.STORE_NOT_FOUND_MSG);
				return response;
			}

			order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId() ,request.getCustomerId());

			if (null != order) {
				String quoteId = null;
				if (null != order.getQuoteId()) {
					quoteId = order.getQuoteId().toString();
				} else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalQuoteId()) {
					quoteId = order.getSubSalesOrder().getExternalQuoteId().toString();
				}
				Integer storeId = order.getStoreId();

				CreateRetryPaymentReplicaDTO quoteResponse = externalQuoteHelper.enableExternalQuoteForRetryPayment(
						quoteId, storeId, order.getMerchantReferance(), request.getFailedPaymentMethod(), tokenHeader , order.getAmstorecreditAmount(), deviceId);
				if (null == quoteResponse || !quoteResponse.getStatus()
						|| !quoteResponse.getStatusCode().equals("200")) {
					response.setStatus(false);
					response.setStatusCode("202");
					response.setStatusMsg(Constants.QOUTE_NOT_FOUND_MSG);
					return response;
				}

				if (null != quoteResponse.getStatusCode()
						&& quoteResponse.getStatusCode().equals("204")) {
					
					SubSalesOrder subSaleOrder = order.getSubSalesOrder();
					if (null != subSaleOrder) {
						subSaleOrder.setRetryPayment(0);
						order.setRetryPayment(0);
						subSaleOrder.setRetryPaymentCount(quoteResponse.getTriedPaymentCount());
						subSaleOrder.setSalesOrder(order);
						order.setSubSalesOrder(subSaleOrder);
						salesOrderRepository.saveAndFlush(order);

						response.setStatus(false);
						response.setStatusCode(quoteResponse.getStatusCode());
						response.setStatusMsg(quoteResponse.getStatusMsg());

						return response;
					}
				}

				if (null != quoteResponse.getStatusCode()
						&& quoteResponse.getStatusCode().equals("200")) {
					if (quoteResponse.getTriedPaymentCount() >= quoteResponse.getRetryPaymentThreshold()) {

						SubSalesOrder subSaleOrder = order.getSubSalesOrder();
						if (null != subSaleOrder && order.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
							subSaleOrder.setRetryPayment(0);
							order.setRetryPayment(0);
							subSaleOrder.setRetryPaymentCount(quoteResponse.getTriedPaymentCount());
							subSaleOrder.setSalesOrder(order);
							updateFailedOrder(order);
							order.setSubSalesOrder(subSaleOrder);
							order.setWmsStatus(2); //Mark order to be push to WMS for cancellation
							salesOrderRepository.saveAndFlush(order);
							response.setQuoteId(quoteResponse.getQuoteId());
							response.setCustomerId(quoteResponse.getCustomerId());
							response.setStatus(true);
							response.setStatusMsg("maximum retry threshold reached!");
							response.setStatusCode("201");
							paymentService.failProxyOrderByOrderId(order.getIncrementId());

							return response;
						}else {
							
							response.setStatus(true);
							response.setStatusMsg("Order payment status cannot update now!");
							response.setStatusCode("201");
						}
					} else {
						SubSalesOrder subSaleOrder = order.getSubSalesOrder();
						if (null != subSaleOrder && order.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
							
							/**set for retry **/
							
							Date createDate = new Date(order.getCreatedAt().getTime());
							Calendar calenderFailedOrder = Calendar.getInstance();
							calenderFailedOrder.setTime(createDate);
							
							if(null != Constants.orderCredentials.getWms()
									&& null != Constants.orderCredentials.getOrderDetails().getPendingOrderExpireTimeInMinute()) {
								calenderFailedOrder.add(Calendar.MINUTE, Constants.orderCredentials.getOrderDetails().getPendingOrderExpireTimeInMinute());
								Date expireDeliveryDate = calenderFailedOrder.getTime();
								if(null == subSaleOrder.getOrderExpiredAt() ) {
									subSaleOrder.setOrderExpiredAt(new Timestamp(expireDeliveryDate.getTime()));	
								}
							}if(null != Constants.orderCredentials.getWms()
									&& null != Constants.orderCredentials.getOrderDetails().getPendingOrderNotfcnDetails()
									&& null != Constants.orderCredentials.getOrderDetails()
									.getPendingOrderNotfcnDetails().getOrderPendingFirstNototificationPending()){
								
								Calendar calenderFirstNotificationOrder = Calendar.getInstance();
								calenderFirstNotificationOrder.setTime(createDate);
								
								calenderFirstNotificationOrder.add(Calendar.MINUTE, Constants.orderCredentials.getOrderDetails()
										.getPendingOrderNotfcnDetails().getOrderPendingFirstNototificationPending());
								Date firstNotificationDate = calenderFirstNotificationOrder.getTime();
								if(null == subSaleOrder.getFirstNotificationAt()) {
									subSaleOrder.setFirstNotificationAt(new Timestamp(firstNotificationDate.getTime()));
								}
								
							}if(null != Constants.orderCredentials.getWms()
									&& null != Constants.orderCredentials.getOrderDetails().getPendingOrderNotfcnDetails()
									&& null != Constants.orderCredentials.getOrderDetails()
									.getPendingOrderNotfcnDetails().getOrderPendingSecondNototificationPending()){
								
								Calendar calenderSEcondNotificationOrder = Calendar.getInstance();
								calenderSEcondNotificationOrder.setTime(createDate);
								
								calenderSEcondNotificationOrder.add(Calendar.MINUTE, Constants.orderCredentials.getOrderDetails()
										.getPendingOrderNotfcnDetails().getOrderPendingSecondNototificationPending());
								Date secondNotificationDate = calenderSEcondNotificationOrder.getTime();
								if(null == subSaleOrder.getSecondNotificationAt()) {
									subSaleOrder.setSecondNotificationAt(new Timestamp(secondNotificationDate.getTime()));
								}
								
							}
							subSaleOrder.setRetryPayment(1);
							order.setRetryPayment(1);
							subSaleOrder.setSalesOrder(order);
							subSaleOrder.setRetryPaymentCount(quoteResponse.getTriedPaymentCount());
							if(null == subSaleOrder.getRetryPaymentCountThreshold()) {
								subSaleOrder.setRetryPaymentCountThreshold(quoteResponse.getRetryPaymentThreshold());
							}								
							order.setSubSalesOrder(subSaleOrder);							
							salesOrderRepository.saveAndFlush(order);
							updateSplitOrdersOfPaymentReplica(order, quoteResponse);

							response.setQuoteId(quoteResponse.getQuoteId());
							response.setCustomerId(quoteResponse.getCustomerId());
							response.setStatus(true);
							response.setStatusMsg(Constants.QOUTE_ENABLED_MSG);
							response.setStatusCode("200");

							return response;
						}else {
							
							response.setStatus(true);
							response.setStatusMsg("Order payment status cannot update now!");
							response.setStatusCode("201");
						}
					}
				}

			} else {
				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg("No Order Found for Given Quote ID");
			}

			return response;
		}

	private void updateSplitOrdersOfPaymentReplica(SalesOrder order,CreateRetryPaymentReplicaDTO quoteResponse) {
		try{
			LOGGER.info("Updating split orders for payment replica retry payment flow for order id: "+order.getEntityId());
			// If order is split order , update retry payment flag in split order as well
			if (null != order.getIsSplitOrder() && Integer.valueOf(1).equals(order.getIsSplitOrder())) {
				List<SplitSalesOrder> splitOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId());
				if (CollectionUtils.isNotEmpty(splitOrders)) {
					LOGGER.info("Total split orders to update retry payment flag: "+splitOrders.size());
					splitOrders.forEach(s -> {
						s.setRetryPayment(1);
						splitSalesOrderRepository.saveAndFlush(s);
						SplitSubSalesOrder splitSubSalesOrder =s.getSplitSubSalesOrder();
						splitSubSalesOrder.setRetryPayment(1);
						splitSubSalesOrder.setRetryPaymentCount(quoteResponse.getTriedPaymentCount());
						splitSubSalesOrderRepository.saveAndFlush(splitSubSalesOrder);
					});
				}
			}
			LOGGER.info("Completed updating split orders for payment replica retry payment flow for order id: "+order.getEntityId());
		} catch (Exception e) {
			LOGGER.error("Error while updating split orders for payment replica retry payment flow. " + e.getMessage(),e);
		}
	}

	@Override
	public AddStoreCreditResponse brazeWalletUpdate(Map<String, String> httpRequestHeaders, AddStoreCreditRequest request) {

		AddStoreCreditResponse resp = new AddStoreCreditResponse();
		if (CollectionUtils.isEmpty(request.getStoreCredits())) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Empty request from braze!");
			return resp;
		}

		long brazeRequestLimit = 1000;
		if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
				&& ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushLimit())) {
			brazeRequestLimit = Constants.orderCredentials.getOrderDetails().getBrazeAttributePushLimit();
		}

		if (request.getStoreCredits().size() > brazeRequestLimit) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("braze requests exceeded limit: " + brazeRequestLimit);
			return resp;
		}

		try {
			for (StoreCredit sc : request.getStoreCredits()) {
				long currTime = new Date().getTime() / 1000;
				BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
				bulkWalletUpdate.setCustomerId(sc.getCustomerId());
				bulkWalletUpdate.setStore_id(sc.getStoreId());
				bulkWalletUpdate.setAmount_to_be_refunded(sc.getStoreCredit());
				bulkWalletUpdate.setOrder_no("");
				bulkWalletUpdate.setInitiatedBy("java-api");
				bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
				bulkWalletUpdate.setJobId("JAVA Braze Service");
				bulkWalletUpdate.setReturnableToBank(sc.isReturnableToBank());

				LOGGER.info("braze wallet push => " + bulkWalletUpdate);
				paymentDtfHelper.publishSCToKafkaForBraze(bulkWalletUpdate);
			}
		} catch (Exception e) {
			LOGGER.error("Braze update push to kafka failed => " + e);
		}

		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Wallet updates request acknowledged!");
		return resp;
	}

	@Override
	public AddStoreCreditResponse brazeAttributePush(Map<String, String> httpRequestHeaders) {
		AddStoreCreditResponse resp = new AddStoreCreditResponse();

		try {

//			2 hours = 7200 secs
			long startOffset = 7200;
			if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
					&& ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushStartOffset())) {
				startOffset = Constants.orderCredentials.getOrderDetails().getBrazeAttributePushStartOffset();
			}

			LocalDateTime dateToFilter = LocalDateTime.now().minus(startOffset, ChronoUnit.SECONDS);
			Timestamp timestamp = Timestamp.valueOf(dateToFilter);
			
			LOGGER.info("timestamp"+timestamp);
			List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository.findByCreatedAtGreaterThan(timestamp);

			List<Integer> customers = histories
					.stream()
					.map(AmastyStoreCreditHistory::getCustomerId)
					.collect(Collectors.toList());

			List<AmastyStoreCredit> credits = amastyStoreCreditRepository.findByCustomerIdIn(customers);
			Set<Integer> customersSetAfterProcessing = new HashSet<>();
			List<BrazePushAttribute> attributes = new ArrayList<>();

			for (AmastyStoreCredit sc : credits) {

				if (!customersSetAfterProcessing.contains(sc.getCustomerId())
						&& (ObjectUtils.isNotEmpty(sc.getCustomerId())
								&& ObjectUtils.isNotEmpty(sc.getStoreCredit()))) {
					BrazePushAttribute attribute = new BrazePushAttribute();
					attribute.setExternalId(String.valueOf(sc.getCustomerId()));
					attribute.setStyliCredit(sc.getStoreCredit());
					attributes.add(attribute);
					customersSetAfterProcessing.add(sc.getCustomerId());
					LOGGER.info("customer id for braze push" + sc.getCustomerId());

				}
			}
			LOGGER.info("attribute length:"+attributes.size());
			if (CollectionUtils.isNotEmpty(attributes)) {
				
    			Lists.partition(attributes, 50).stream().forEach(eventChunk -> kafkaBrazeHelper.sendAttributesToBraze(eventChunk));

			}


		} catch (Exception e) {
			LOGGER.error("Error sending wallet updates (attributes) to braze: "+ e.getMessage());
		}

		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Wallet attributes submit request acknowledged!");
		return resp;
	}

	private void updateFailedOrder(SalesOrder order) {
		order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
		order.setState(OrderConstants.FAILED_ORDER_STATUS);
	

		updateOrderStatusHistory(order, OrderConstants.HOLD_ORDER_CANCEL_MESSAGE,
				OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());

		String updateMessage = "Order retry count reached maximum threshold";

		updateOrderStatusHistory(order, updateMessage, OrderConstants.ORDER2, order.getStatus());

		saveOrderGrid(order, OrderConstants.FAILED_ORDER_STATUS);
		

		if (null != order.getAmstorecreditBaseAmount()) {

			releaseStoreCredit(order, order.getAmstorecreditAmount());
		}
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
				.findAny().orElse(null);
		
		orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_DTF_FAILED_CALL);
		// EAS to be implement for payment fail.If Earn Service flag ON!.
		if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
			eASServiceImpl.publishCancelOrderToKafka(order, 0.0);
		}
		if (null != order.getSubSalesOrder()
				&& null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())) {
					if(order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSubSalesOrder().getShukranLocked().equals(0)){
						commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(), order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), order.getSubSalesOrder().getQuoteId(), false, order, store, "Refund Shukran Coins Burned On Payment Failure", "");
						SubSalesOrder subSalesOrder = order.getSubSalesOrder();
						subSalesOrder.setShukranLocked(1);
						subSalesOrderRepository.saveAndFlush(subSalesOrder);
					}
			salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, order, false, false);
		}
		// Fail Proxy Order
		
	}
	
	 private void releaseStoreCredit(SalesOrder order, BigDecimal storeCreditAmount) {

			List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
					.findByCustomerId(order.getCustomerId());
			AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
			if (amastyStoreCredit != null) {
				long currTime = new Date().getTime() / 1000;
				BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
				bulkWalletUpdate.setEmail(order.getCustomerEmail());
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
	
	/**
	 * @param request
	 * @param order
	 */
	private void updateOrderStatusHistory(SalesOrder order, String message, String entity, String status) {
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
	

	private void saveOrderGrid(SalesOrder order, String message) {

		SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());

		salesorderGrid.setStatus(message);

		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}


	private int safeInt(String s, int defaultVal) {
		if (s == null || s.trim().isEmpty()) return defaultVal;
		// supports "2", "2.0", "2.000"
		return new BigDecimal(s.trim()).intValue();
	}

	private LmdCommissionConfig resolveCommissionPercentFromConfig(String soldBy, String shortDesc) {
		try {
			if (StringUtils.isBlank(soldBy)) return null;

			// 3) soldBy matches concept_en/ar AND shortDesc matches l4_category_en/ar
			if (StringUtils.isNotBlank(shortDesc)) {
				List<LmdCommissionConfig> categoryMatchList =
						lmdCommissionConfigRepository.findCategoryMatch(soldBy, shortDesc);

				if (null != categoryMatchList && !categoryMatchList.isEmpty()) {
					return categoryMatchList.get(0);
				}
			}

			// 4) Otherwise default_commission for soldBy
			List<LmdCommissionConfig> sellerDefaultList =
					lmdCommissionConfigRepository.findSellerDefault(soldBy);

			if (null != sellerDefaultList && !sellerDefaultList.isEmpty()) {
				return sellerDefaultList.get(0);
			}

		} catch (Exception ex) {
			LOGGER.error("Exception while resolving commission from LmdCommissionConfig. soldBy=" + soldBy
					+ ", shortDesc=" + shortDesc + ", err=" + ex.getMessage(), ex);
			return null;
		}

		return null;
	}

	private BigDecimal resolveStyliCommission(LmdCommissionConfig lmdCommissionConfig) {
		if (lmdCommissionConfig == null) {
			return BigDecimal.ZERO;
		}

		BigDecimal categoryCommission = lmdCommissionConfig.getCategoryCommission();
		if (categoryCommission != null && categoryCommission.compareTo(BigDecimal.ZERO) > 0) {
			return categoryCommission;
		}

		BigDecimal defaultCommission = lmdCommissionConfig.getDefaultCommission();
		if (defaultCommission != null && defaultCommission.compareTo(BigDecimal.ZERO) > 0) {
			return defaultCommission;
		}

		return BigDecimal.ZERO;
	}

}

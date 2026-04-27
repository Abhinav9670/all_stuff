package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.eas.EASQuoteSpend;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.request.*;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.AddressMapperHelper;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.KafkaServiceImpl;
import org.styli.services.order.service.impl.PubSubServiceImpl;
import org.styli.services.order.utility.*;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Umesh, 26/09/2020
 * @project product-service
 */

@Component
public class OrderHelperV2 {

  private static final Log LOGGER = LogFactory.getLog(OrderHelperV2.class);

  @Autowired
  SalesOrderAddressRepository salesOrderAddressRepository;

  @Autowired
  SalesOrderGridRepository salesOrderGridRepository;


  @Autowired
  SalesOrderRepository salesOrderRepository;

  @Autowired
  CommonServiceImpl commonService;

  @Autowired
  CustomerEntityRepository customerEntityRepository;

  @Autowired
  OrderHelper orderHelper;

  @Value("${promo.coupon.promo.url}")
  private String couponRedeemUrl;

  @Value("${address.mapper.url}")
  private String addressMapperUrl;

  @Value("${address.mapper.flag}")
  private String addressMapperFlag;
  
  @Value("${region.value}")
  private String regionValue;

  @Autowired
  @Qualifier("withoutEureka")
  private RestTemplate restTemplate;
  
  @Value("${jwt.salt.new.secret}")
  private String jwtsaltNewSecret;
  
  @Value("${jwt.salt.old.secret}")
  private String jwtsaltOldSecret;

  @Autowired
  private ProxyOrderRepository proxyOrderRepository;

  @Autowired
  private AddressMapperHelper addressMapperHelper;

  @Autowired
  private MulinHelper mulinHelper;
  
  private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private static final ObjectMapper mapper = new ObjectMapper();
  
   @Autowired
   @Lazy
   PubSubServiceImpl pubSubServiceImpl;

    @Autowired
    KafkaServiceImpl kafkaService;

	@Value("${pubsub.topic.preferred.payment.method}")
	private String preferredPaymentTopic;

    @Value("${auth.internal.header.bearer.token}")
    private String internalHeaderBearerToken;


	public boolean checkApplePayRetryRequest(QuoteDTO quote) {
		final String APPLEPAY = "applePay";
		String paymentMethod = quote.getSelectedPaymentMethod();
		String applePay = PaymentCodeENUM.APPLE_PAY.getValue();
		Optional<String> failedApplePay = quote.getFailedPaymentMethod().stream().filter(APPLEPAY::equals).findFirst();
		return applePay.equals(paymentMethod) && failedApplePay.isPresent();
	}

  public SalesOrder createOrderObjectToPersist(QuoteDTO quote,
                                               String paymentMethod,
                                               Stores store,
                                               String incrementId,
                                               String ipAddress,
                                               int source,
                                               String merchantReference,
                                               String appVersion,
                                               String xSource,
                                               String customerIp,
                                               String deviceId,
                                               boolean retryPaymentReplica,
                                               boolean isPayfortAuthorized
  ) {
	  
	    SalesOrder order = new SalesOrder();
	    BigDecimal storeCreditAmount = null;

try {
    
	
	if (retryPaymentReplica) {
		order = salesOrderRepository.findByEditIncrementOrIncrementId(incrementId, incrementId);
		if (Objects.nonNull(order)) {
			incrementId = order.getIncrementId();
		} else {
			order = new SalesOrder();
		}
	}
    
   

    AddressObject shippingAddress = quote.getShippingAddress();


    List<ShukranTenders> shukranTendersList= new ArrayList<>();
    Timestamp newTime= new Timestamp(new Date().getTime());
    SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    // Set the TimeZone to UTC to match the "Z" (Zulu time)
    sdf1.setTimeZone(TimeZone.getTimeZone("UTC"));

    // Convert the Timestamp to String using the defined format
    String timestampString = sdf1.format(newTime);
    if (quote.getStoreCreditApplied() != null) {
      storeCreditAmount = new BigDecimal(quote.getStoreCreditApplied());
    }
    if(null != order && null != order.getAmstorecreditAmount()) {
    	storeCreditAmount = order.getAmstorecreditAmount();    			
    	}

    if (orderShouldBePaymentPending(paymentMethod) && null == order.getStatus()) {
      order.setState(OrderConstants.PENDING_PAYMENT_ORDER_STATE);
      order.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
    } 
    if (orderShouldBePaymentPending(paymentMethod)&& null != order.getStatus() && !order.getStatus().equals(OrderConstants.PROCESSING_ORDER_STATUS)) {
        order.setState(OrderConstants.PENDING_PAYMENT_ORDER_STATE);
        order.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
      } else if (null != order.getStatus() && null!=paymentMethod && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())){
        order.setState(OrderConstants.NEW_ORDER_STATE);
        order.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
        LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in createOrderObjectToPersist");
     }  else {
      order.setState(OrderConstants.NEW_ORDER_STATE);
      order.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
      order.setExtOrderId("0");
      LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in createOrderObjectToPersist -- else");
    }

    order.setCouponCode(quote.getCouponCodeApplied());
    order.setShippingDescription(Constants.SHIPPING_DESCRIPTION);
    order.setVirtual(0);
    order.setStoreId(parseNullInteger(store.getStoreId()));
	if (StringUtils.isNotEmpty(quote.getCustomerId())) {
		order.setCustomerId(parseNullInteger(quote.getCustomerId()));
		CustomerEntity customerEntity = orderHelper
				.getCustomerDetails(parseNullInteger(quote.getCustomerId()) , null);
		order.setCustomerGroupId(1);
		if(null != customerEntity && null != customerEntity.getEntityId() && StringUtils.isNotEmpty(customerEntity.getEmail()) && StringUtils.isNotBlank(customerEntity.getEmail())) {
			order.setCustomerEmail(customerEntity.getEmail().toLowerCase());
		}
		order.setCustomerFirstname(shippingAddress.getFirstname());
		order.setCustomerLastname(shippingAddress.getLastname());
	} else {
      order.setCustomerGroupId(0);
      if(StringUtils.isNotBlank(shippingAddress.getEmail()) && StringUtils.isNotEmpty(shippingAddress.getEmail())) {
          order.setCustomerEmail(shippingAddress.getEmail().toLowerCase());
      }
      order.setCustomerFirstname(shippingAddress.getFirstname());
      order.setCustomerLastname(shippingAddress.getLastname());
    }

    order.setBaseToGlobalRate(new BigDecimal(1));
    order.setBaseToOrderRate(new BigDecimal(1).divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP));

    order.setStoreToBaseRate(store.getCurrencyConversionRate());
    order.setStoreToOrderRate(new BigDecimal(1));
    order.setCouponRuleName(null);
   
    order.setBaseCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);
    order.setGlobalCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);
    order.setOrderCurrencyCode(quote.getCurrency());
    order.setStoreCurrencyCode(quote.getCurrency());

    order.setTotalQtyOrdered(parseNullDecimal(quote.getItemsQty()));
    order.setCustomerIsGuest(parseNullInteger(quote.getCustomerIsGuest()));
    order.setCustomerNoteNotify(null);
    order.setBillingAddressId(0);
    order.setShippingAddressId(0);
    order.setWeight(new BigDecimal(1));
    order.setCustomerDob(null);
    order.setIncrementId(incrementId);
    order.setAppliedRuleIds(null);
    order.setTotalItemCount(parseNullInteger(quote.getItemsQty()));

    order.setDiscountDescription(quote.getCouponCodeApplied());

    if(StringUtils.isNotEmpty(customerIp) && StringUtils.isNotBlank(customerIp) && customerIp.length()<45) {
        order.setRemoteIp(customerIp);
    }

    order.setShippingMethod(Constants.SHIPPING_METHOD);
    order.setStoreName(GenericConstants.STORE_NAME);
    order.setCreatedAt(new Timestamp(new Date().getTime()));
    order.setUpdatedAt(new Timestamp(new Date().getTime()));
    order.setPaypalIpnCustomerNotified(0);

    order.setDiscountAmount(getCouponDiscount(quote));
    order.setBaseDiscountAmount(getBaseValueDecimal(getCouponDiscount(quote), store.getCurrencyConversionRate()));
    order.setGrandTotal(getBaseGrandTotal(quote));
    order.setBaseGrandTotal(getBaseValueDecimal(getBaseGrandTotal(quote), store.getCurrencyConversionRate()));
    order.setTotalDue(getBaseGrandTotal(quote));
    order.setBaseTotalDue(getBaseValueDecimal(getBaseGrandTotal(quote), store.getCurrencyConversionRate()));
    order.setShippingAmount(getShippingAmount(quote));
    order.setBaseShippingAmount(getBaseValueDecimal(getShippingAmount(quote), store.getCurrencyConversionRate()));
    order.setShippingTaxAmount(new BigDecimal(0));
    order.setSubtotal(getSubtotal(quote));
    order.setBaseSubtotal(getBaseValueDecimal(getSubtotal(quote), store.getCurrencyConversionRate()));
    order.setTaxAmount(getTaxAmount(quote));
    order.setBaseTaxAmount(getBaseValueDecimal(getTaxAmount(quote), store.getCurrencyConversionRate()));
    order.setBaseShippingDiscountAmount(new BigDecimal(0));
    order.setSubtotalInclTax(getSubtotalInclTax(quote));
    order.setBaseSubtotalInclTax(getBaseValueDecimal(getSubtotalInclTax(quote), store.getCurrencyConversionRate()));
    order.setShippingDiscountAmount(new BigDecimal(0));
    order.setShippingInclTax(getShippingInclTax(quote));
    order.setBaseShippingInclTax(getBaseValueDecimal(getShippingInclTax(quote), store.getCurrencyConversionRate()));
    order.setCashOnDeliveryFee(getCodCharges(quote));
    order.setBaseCashOnDeliveryFee(getBaseValueDecimal(getCodCharges(quote), store.getCurrencyConversionRate()));
    order.setAmstorecreditAmount(storeCreditAmount);
    order.setIsSplitOrder(quote.getIsSplitOrder());
    //Setting preferred payment to kafka/pubsub based on flag
    BigDecimal totalAmountToSubtractFromTenders= BigDecimal.ZERO;
    if (order.getShippingAmount() != null && order.getShippingAmount().compareTo(BigDecimal.ZERO) > 0) {
        totalAmountToSubtractFromTenders = totalAmountToSubtractFromTenders.add(order.getShippingAmount());
    }
    if (quote.getDonationAmount() != null && new BigDecimal(quote.getDonationAmount()).compareTo(BigDecimal.ZERO) > 0) {
        totalAmountToSubtractFromTenders = totalAmountToSubtractFromTenders.add(new BigDecimal(quote.getDonationAmount()));
    }
    if(quote.getImportFeesAmount() != null && new BigDecimal(quote.getImportFeesAmount()).compareTo(BigDecimal.ZERO)>0){
        totalAmountToSubtractFromTenders = totalAmountToSubtractFromTenders.add(new BigDecimal(quote.getImportFeesAmount()));
    }
    BigDecimal codFeeToSubtract= BigDecimal.ZERO;
    if(order.getTotalDue() != null && order.getTotalDue().compareTo(BigDecimal.ZERO)>0){
        if(order.getTotalDue().compareTo(totalAmountToSubtractFromTenders)>0 && StringUtils.isNotBlank(quote.getSelectedPaymentMethod()) && StringUtils.isNotEmpty(quote.getSelectedPaymentMethod()) && !quote.getSelectedPaymentMethod().equals("free") && !quote.getSelectedPaymentMethod().equals(PaymentCodeENUM.SHUKRAN_PAYMENT.getValue())) {
            BigDecimal tenderAmountToFill= order.getTotalDue().subtract(totalAmountToSubtractFromTenders);
            String tenderCode = null;
            switch (quote.getSelectedPaymentMethod()) {
                case OrderConstants.MD_PAYFORT_CC_VAULT, OrderConstants.MD_PAYFORT, OrderConstants.PAYFORT_FORT_CC:
                    tenderCode = Constants.orderCredentials.getShukranTenderMappings().getPayfortCc();
                    break;
                case PaymentConstants.TABBY_INSTALMENTS:
                    tenderCode = Constants.orderCredentials.getShukranTenderMappings().getTabby();
                    break;
                case PaymentConstants.TABBY_PAYLATER:
                    tenderCode = Constants.orderCredentials.getShukranTenderMappings().getTabbyPayLater();
                    break;
                case PaymentConstants.TAMARA_INSTALMENTS_3, PaymentConstants.TAMARA_INSTALMENTS_6:
                    tenderCode = Constants.orderCredentials.getShukranTenderMappings().getTamara();
                    break;
                case OrderConstants.APPLE_PAY:
                    tenderCode = Constants.orderCredentials.getShukranTenderMappings().getApplePay();
                    break;
                case OrderConstants.PAYMENT_METHOD_COD:
                    tenderCode = Constants.orderCredentials.getShukranTenderMappings().getCashOnDelivery();
                    if(order.getCashOnDeliveryFee() != null && order.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO)>0 && order.getCashOnDeliveryFee().compareTo(tenderAmountToFill)>0){
                        LOGGER.info("total amount to subtract from tenders 1" + totalAmountToSubtractFromTenders + tenderAmountToFill);
                        codFeeToSubtract = order.getCashOnDeliveryFee().subtract(tenderAmountToFill);;
                        tenderAmountToFill = order.getCashOnDeliveryFee();
                        LOGGER.info("total amount to subtract from tenders 2" + totalAmountToSubtractFromTenders + tenderAmountToFill);
                    }
                    break;
            }

            ShukranTenders shukranTenders = new ShukranTenders();

            shukranTenders.setTenderAmount(tenderAmountToFill.setScale(2, RoundingMode.HALF_UP));
            shukranTenders.setTenderCode(tenderCode);
            shukranTenders.setTransactionDateTime(timestampString);

            shukranTendersList.add(shukranTenders);
        }
        totalAmountToSubtractFromTenders = totalAmountToSubtractFromTenders.subtract(order.getTotalDue());

    }
    BigDecimal tenderAmountToSetForStyliCredits = BigDecimal.ZERO;
    if (null != storeCreditAmount) {
      order.setAmstorecreditBaseAmount(getBaseValueDecimal(storeCreditAmount, store.getCurrencyConversionRate()));
      tenderAmountToSetForStyliCredits= storeCreditAmount;
      if(totalAmountToSubtractFromTenders.compareTo(BigDecimal.ZERO)>0 && storeCreditAmount.compareTo(totalAmountToSubtractFromTenders)>0){
          tenderAmountToSetForStyliCredits= tenderAmountToSetForStyliCredits.subtract(totalAmountToSubtractFromTenders);
      }
      if(codFeeToSubtract.compareTo(BigDecimal.ZERO)>0){
          tenderAmountToSetForStyliCredits= tenderAmountToSetForStyliCredits.subtract(codFeeToSubtract);
          LOGGER.info("tender amount to be subtracted from wallet" + tenderAmountToSetForStyliCredits);
      }
    }
    order.setBaseShippingTaxAmount(new BigDecimal(0));
    order.setSource(source);
    order.setAppVersion(appVersion);
    order.setMerchantReferance(StringUtils.isNoneBlank(merchantReference) ? merchantReference : incrementId);

    order.setBaseImportFee(getBaseValueDecimal(getMinimumImportFee(quote), store.getCurrencyConversionRate()));
    order.setImportFee(getMinimumImportFee(quote));

    //call API-2647 Authorization capture for payments with Payfort
	if (isPayfortAuthorized && source == Integer.valueOf(Constants.ONE)) {
		Long clientVersion = Constants.decodeAppVersion(appVersion);
		Long thresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getAuthorizationThresholdVersion());
		LOGGER.info("clientVersion:: " + clientVersion + " | thresholdVersion:: "+thresholdVersion);
		if(null != clientVersion && null != thresholdVersion && clientVersion >= thresholdVersion) {
			order.setPayfortAuthorized(Integer.valueOf(Constants.ONE));
			order.setAuthorizationCapture(Integer.valueOf(Constants.ZERO));
		}else {
			order.setPayfortAuthorized(Integer.valueOf(Constants.ZERO));
			order.setAuthorizationCapture(Integer.valueOf(Constants.ZERO));
		}
	}else if(isPayfortAuthorized && (source == Integer.valueOf(Constants.ZERO) || source == Integer.valueOf(Constants.TWO))) {
		order.setPayfortAuthorized(Integer.valueOf(Constants.ONE));
		order.setAuthorizationCapture(Integer.valueOf(Constants.ZERO));
	}else {
		order.setPayfortAuthorized(Integer.valueOf(Constants.ZERO));
		order.setAuthorizationCapture(Integer.valueOf(Constants.ZERO));
	}

	if (CollectionUtils.isNotEmpty(quote.getDiscountData())) {
        DiscountData giftVoucherDiscount = quote.getDiscountData().stream().filter(DiscountData::getIsGiftVoucher)
				.findAny().orElse(null);
        if(Objects.nonNull(giftVoucherDiscount)) {
          order.setGiftVoucherDiscount(new BigDecimal(giftVoucherDiscount.getValue()));
          tenderAmountToSetForStyliCredits=  tenderAmountToSetForStyliCredits.add(new BigDecimal(giftVoucherDiscount.getValue()));
        }
	}

    if(tenderAmountToSetForStyliCredits.compareTo(BigDecimal.ZERO)>0) {
        ShukranTenders shukranTenders1 = new ShukranTenders();
        shukranTenders1.setTenderCode(Constants.orderCredentials.getShukranTenderMappings().getStyliWallet());
        shukranTenders1.setTransactionDateTime(timestampString);
        shukranTenders1.setTenderAmount(tenderAmountToSetForStyliCredits.setScale(2, RoundingMode.HALF_UP));
        shukranTendersList.add(shukranTenders1);
    }
    SubSalesOrder subSalesOrder = new SubSalesOrder();
    LOGGER.info("QuoteID:::" + quote.getQuoteId());
    
    if( null != order.getSubSalesOrder()) {
    	
    	subSalesOrder = order.getSubSalesOrder();
    }
    if (StringUtils.isNumeric(quote.getQuoteId())) {
        subSalesOrder.setExternalQuoteId(new BigInteger(quote.getQuoteId()));
    }

        subSalesOrder.setClientSource(xSource);
        subSalesOrder.setDeviceId(deviceId);
        subSalesOrder.setTotalShukranCoinsEarned(BigDecimal.ZERO);
        subSalesOrder.setTotalShukranCoinsBurned(BigDecimal.ZERO);
        subSalesOrder.setTotalShukranBurnedValueInBaseCurrency(BigDecimal.ZERO);
        subSalesOrder.setTotalShukranBurnedValueInCurrency(BigDecimal.ZERO);

    if(store.getIsShukranEnable() && quote.getShukranLinkFlag() && StringUtils.isNotEmpty(quote.getShukranCardNumber()) && StringUtils.isNotBlank(quote.getShukranCardNumber()) && StringUtils.isNotBlank(quote.getProfileId()) && StringUtils.isNotEmpty(quote.getProfileId())) {
        // shukran earn flow

        if (quote.getTotalShukranEarn() != null && quote.getTotalShukranEarn().compareTo(BigDecimal.ZERO) > 0) {
            subSalesOrder.setTotalShukranCoinsEarned(quote.getTotalShukranEarn());
        }
        if (quote.getTotalShukranEarnValueInBaseCurrency() != null && quote.getTotalShukranEarnValueInBaseCurrency().compareTo(BigDecimal.ZERO) > 0) {
            subSalesOrder.setTotalShukranEarnedValueInBaseCurrency(quote.getTotalShukranEarnValueInBaseCurrency());
        }
        if (quote.getTotalShukranEarnValueInCurrency() != null && quote.getTotalShukranEarnValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
            subSalesOrder.setTotalShukranEarnedValueInCurrency(quote.getTotalShukranEarnValueInCurrency());
        }

        // shukran burn flow
        if (quote.getTotalShukranBurn() != null && quote.getTotalShukranBurn().compareTo(BigDecimal.ZERO) > 0) {
            subSalesOrder.setTotalShukranCoinsBurned(quote.getTotalShukranBurn());
            ShukranTenders shukranTenders1= new ShukranTenders();
            shukranTenders1.setTenderAmount(quote.getTotalShukranBurnValueInCurrency().setScale(2, RoundingMode.HALF_UP));
            shukranTenders1.setTenderCode(Constants.orderCredentials.getShukranTenderMappings().getShukranPayment());
            shukranTenders1.setTransactionDateTime(timestampString);
            shukranTendersList.add(shukranTenders1);
            subSalesOrder.setShukranLocked(0);

        }
        if (quote.getTotalShukranBurnValueInBaseCurrency() != null && quote.getTotalShukranBurnValueInBaseCurrency().compareTo(BigDecimal.ZERO) > 0) {
            subSalesOrder.setTotalShukranBurnedValueInBaseCurrency(quote.getTotalShukranBurnValueInBaseCurrency());
        }
        ObjectMapper shukranMapper= new ObjectMapper();
        if(CollectionUtils.isNotEmpty(shukranTendersList)) {
            subSalesOrder.setTenders(shukranMapper.writeValueAsString(shukranTendersList));
        }
        if (quote.getTotalShukranBurnValueInCurrency() != null && quote.getTotalShukranBurnValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
            subSalesOrder.setTotalShukranBurnedValueInCurrency(quote.getTotalShukranBurnValueInCurrency());
        }

        if(StringUtils.isNotEmpty(quote.getQuoteId()) && StringUtils.isNotBlank(quote.getQuoteId())){
            subSalesOrder.setQuoteId(StringUtils.isNotEmpty(Constants.orderCredentials.getShukranCartIdPrefix()) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranCartIdPrefix())? Constants.orderCredentials.getShukranCartIdPrefix()+quote.getQuoteId():quote.getQuoteId());
        }

        if(StringUtils.isNotEmpty(quote.getProfileId()) && StringUtils.isNotBlank(quote.getProfileId())){
            subSalesOrder.setCustomerProfileId(quote.getProfileId());
        }
        if(StringUtils.isNotBlank(store.getShukranStoreCode()) && StringUtils.isNotEmpty(store.getShukranStoreCode()) && StringUtils.isNotEmpty(Constants.getShukarnEnrollmentStoreCode()) && StringUtils.isNotBlank(Constants.getShukarnEnrollmentStoreCode())){
            subSalesOrder.setShukranStoreCode(Constants.getShukranEnrollmentConceptCode()+Constants.getShukarnEnrollmentStoreCode()+store.getShukranStoreCode());
        }
        LOGGER.info("shukran link flag "+ quote.getShukranLinkFlag());
        subSalesOrder.setShukranLinked(quote.getShukranLinkFlag());
        subSalesOrder.setQualifiedPurchase(quote.getIsQualifyingPurchase());
        if(StringUtils.isNotBlank(quote.getShukranCardNumber()) && StringUtils.isNotEmpty(quote.getShukranCardNumber())){
            subSalesOrder.setShukranCardNumber(quote.getShukranCardNumber());
        }
        if(StringUtils.isNotBlank(quote.getTierName()) && StringUtils.isNotEmpty(quote.getTierName())){
            subSalesOrder.setTierName(quote.getTierName());
        }
        if(quote.getShukranBasicEarnPoint() != null && quote.getShukranBasicEarnPoint().compareTo(BigDecimal.ZERO)>0){
            subSalesOrder.setShukranBasicEarnPoint(quote.getShukranBasicEarnPoint());
        }
        if(quote.getShukranBonousEarnPoint() != null && quote.getShukranBonousEarnPoint().compareTo(BigDecimal.ZERO)>0){
            subSalesOrder.setShukranBonusEarnPoint(quote.getShukranBonousEarnPoint());
        }
        if(StringUtils.isNotEmpty(quote.getCustomerPhoneNumber()) && StringUtils.isNotBlank(quote.getCustomerPhoneNumber())){
            subSalesOrder.setShukranPhoneNumber(quote.getCustomerPhoneNumber());
        }

        subSalesOrder.setCrossBorder(StringUtils.isNotBlank(quote.getIsCrossBorderFlag()) && StringUtils.isNotEmpty(quote.getIsCrossBorderFlag()) && quote.getIsCrossBorderFlag().equalsIgnoreCase("Y"));
    }

    if (quote.getAutoCouponApplied() != null && quote.getAutoCouponDiscount() != null) {

      subSalesOrder.setExternalAutoCouponCode(quote.getAutoCouponApplied());
      if(ObjectUtils.isNotEmpty(quote.getAutoCouponDiscount())) {
          BigDecimal autoCouponDiscount = new BigDecimal(quote.getAutoCouponDiscount());
          subSalesOrder.setExternalAutoCouponAmount(autoCouponDiscount);
          subSalesOrder.setExternalAutoCouponBaseAmount(getBaseValueDecimal(autoCouponDiscount, store.getCurrencyConversionRate()));
      }

    }
    if(null != quote.getIsWhitelistedCustomer() && quote.getIsWhitelistedCustomer()) {
    	
    	subSalesOrder.setWhiteListedCustomer(1);
    }else {
    	
    	subSalesOrder.setWhiteListedCustomer(0);
    	
    }
    if(null != quote.getDonationAmount()) {
    	
    	subSalesOrder.setDonationAmount(new BigDecimal(quote.getDonationAmount()));
    	subSalesOrder.setBaseDonationAmount(getBaseValueDecimal(subSalesOrder.getDonationAmount(), store.getCurrencyConversionRate()));
    }

	SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
	sdf.setTimeZone(TimeZone.getTimeZone(OrderConstants.timeZoneMap.get(order.getStoreId())));

    subSalesOrder.setClientPlatform(xSource);
    subSalesOrder.setIsStyliPost(1);
    if (CollectionUtils.isNotEmpty(quote.getDiscountData())) {
      try {
        subSalesOrder.setDiscountData(mapper.writeValueAsString(quote.getDiscountData()));
      } catch (JsonProcessingException e) {

        LOGGER.error("exception during convert discount object to string");
      }
    }
    subSalesOrder.setExternalQuoteStatus(0);
    if(null!=store.getWarehouseId()) {
        subSalesOrder.setWarehouseLocationId(Integer.parseInt(store.getWarehouseId()));
    }
    
    subSalesOrder.setOtpVerified(quote.getOtpFlag());   
    subSalesOrder.setSalesOrder(order);
    subSalesOrder.setPaymentId(quote.getTabbyPaymentId());
    
    if(null != quote.getFirstFreeShipping() && quote.getFirstFreeShipping().isActive()) {	
    	subSalesOrder.setFreeShipmentTypeOrder(1);
    }

    // EAS_CHANGES quote data for spend
    EASQuoteSpend easQuoteSpendData = quote.getCoinDiscountData();


    // If blockEAS is true , we should not allow to set EAS data from Quote
    // Below condition will check if blockEAS is false and isCoinApplied is 1 then will set EAS data from quote
    if (null!=Constants.orderCredentials && !Constants.orderCredentials.getBlockEAS() && null != easQuoteSpendData && easQuoteSpendData.getIsCoinApplied() == 1) {
        subSalesOrder.setInitialEasCoins(easQuoteSpendData.getCoins());
        subSalesOrder.setInitialEasValueInBaseCurrency(new BigDecimal(easQuoteSpendData.getBaseCurrencyValue()));
        subSalesOrder.setInitialEasValueInCurrency(new BigDecimal(easQuoteSpendData.getStoreCoinValue()));

        subSalesOrder.setEasCoins(easQuoteSpendData.getCoins());
        subSalesOrder.setEasValueInBaseCurrency(new BigDecimal(easQuoteSpendData.getBaseCurrencyValue()));
        subSalesOrder.setEasValueInCurrency(new BigDecimal(easQuoteSpendData.getStoreCoinValue()));
    }
  
    order.setSubSalesOrder(subSalesOrder);
    

    
} catch (Exception e) {
    LOGGER.error("Order Error" + e.getMessage());
}

	LOGGER.info("done sub sales order setting. Order ID: " + incrementId);
	if (null != addressMapperFlag && addressMapperFlag.equals("1")) {
		if (null != quote.getShippingAddress() && null != quote.getShippingAddress().getCityMapper()
				&& null != quote.getShippingAddress().getCityMapper().getFastDeliver()
				&& quote.getShippingAddress().getCityMapper().getFastDeliver().booleanValue()) {
            // UIB-7056 Longest SLA is not shown in Cart Page if Apparel SKU and Styli SKU is present
            try{
                setEstmateDateAndFastDeliveryV2(quote, order,store.getStoreId());
                LOGGER.info("Estimated date and fast delivery flag set using address mapper response");
            } catch (Exception e) {
                LOGGER.error("Exception in setting estimated date and fast delivery flag:, So trying to use quote estimated date " + e.getMessage());
                setAddressCityEstimateDate(quote, order);
            }
		} else {
			setAddressCityEstimateDate(quote, order);
		}
	}
    return order;

  }
  private boolean orderShouldBePaymentPending(String paymentMethod) {
    return paymentMethod.equalsIgnoreCase(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())
        || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT.getValue())
        || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
        || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.APPLE_PAY.getValue())
        || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue())
        || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.TABBY_PAYLATER.getValue())
        || paymentMethod.equalsIgnoreCase(PaymentConstants.TAMARA_INSTALMENTS_3)
        || paymentMethod.equalsIgnoreCase(PaymentConstants.TAMARA_INSTALMENTS_6)
        || paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE);
  }

  private BigDecimal getSubtotalInclTax(QuoteDTO quoteObject) {
    return quoteObject.getSubtotalInclTax() == null ? new BigDecimal(0)
        : new BigDecimal(quoteObject.getSubtotalInclTax());
  }

  private BigDecimal getTaxAmount(QuoteDTO quoteObject) {
    return quoteObject.getTaxAmount() == null ? new BigDecimal(0) : new BigDecimal(quoteObject.getTaxAmount());
  }

  private BigDecimal getSubtotal(QuoteDTO quoteObject) {
    return quoteObject.getSubtotal() == null ? new BigDecimal(0) : new BigDecimal(quoteObject.getSubtotalExclTax());
  }

  private BigDecimal getCouponDiscount(QuoteDTO quoteObject) {

    BigDecimal totalCouponDiscount = new BigDecimal(0);

    if (StringUtils.isNotBlank(quoteObject.getCouponDiscount())) {

      totalCouponDiscount = totalCouponDiscount.add(new BigDecimal(quoteObject.getCouponDiscount()));
    }
    if (StringUtils.isNotBlank(quoteObject.getAutoCouponDiscount())) {

      totalCouponDiscount = totalCouponDiscount.add(new BigDecimal(quoteObject.getAutoCouponDiscount()));
    }
    // return quoteObject.getCouponDiscount() == null ? new BigDecimal(0)
    // : new BigDecimal(quoteObject.getCouponDiscount()).negate();

    return totalCouponDiscount.negate();
  }

  private BigDecimal getBaseGrandTotal(QuoteDTO quoteObject) {
    return quoteObject.getBaseGrandTotal() == null ? new BigDecimal(0)
        : new BigDecimal(quoteObject.getBaseGrandTotal());
  }
  
  private BigDecimal getBaseDonationAmount(QuoteDTO quoteObject) {
	    return quoteObject.getDonationAmount() == null ? new BigDecimal(0)
	        : new BigDecimal(quoteObject.getDonationAmount());
	  }

  private BigDecimal getShippingAmount(QuoteDTO quoteObject) {
    return quoteObject.getShippingAmount() == null ? new BigDecimal(0)
        : new BigDecimal(quoteObject.getShippingAmount());
  }

  private BigDecimal getCodCharges(QuoteDTO quoteObject) {
    return quoteObject.getCodCharges() == null ? new BigDecimal(0) : new BigDecimal(quoteObject.getCodCharges());
  }

  private BigDecimal getShippingInclTax(QuoteDTO quoteObject) {
    return quoteObject.getShippingInclTax() == null ? new BigDecimal(0)
        : new BigDecimal(quoteObject.getShippingInclTax());
  }

  private BigDecimal parseNullDecimal(String val) {
    return (val == null) ? null : new BigDecimal(val);
  }

  private Integer parseNullInteger(String val) {
    if (StringUtils.isNoneBlank(val)) {

      return StringUtils.isNoneBlank(val) ? Integer.parseInt(val) : null;

    } else {

      return 0;
    }
  }

  private BigDecimal getMinimumImportFee(QuoteDTO quoteObject) {
    return quoteObject.getImportFeesAmount() == null ? new BigDecimal(0)
        : new BigDecimal(quoteObject.getImportFeesAmount());
  }

  public void createOrderAddresses(QuoteDTO quote, SalesOrder order) {

    AddressObject addressObject = quote.getShippingAddress();

    SalesOrderAddress address =null;
    	  
	  if(CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
		  
		  address = order.getSalesOrderAddress().stream().findFirst().orElse(null);
	  }
	  
	  if(null == address) {
		  
		  address = new SalesOrderAddress();
	  }
	  
    // address.setParentId(order.getEntityId());
    address.setAddressType("shipping");
    copyAddressElements(address, addressObject);

    address.setCustomerId(parseNullInteger(quote.getCustomerId()));
    if(StringUtils.isNotBlank(order.getCustomerEmail()) && StringUtils.isNotEmpty(order.getCustomerEmail())) {
        address.setEmail(order.getCustomerEmail().toLowerCase());
    }
    order.addOrderAddress(address);
    // salesOrderAddressRepository.saveAndFlush(address);

    SalesOrderAddress billingAddress = new SalesOrderAddress();
    // billingAddress.setParentId(order.getEntityId());
    copyAddressElements(billingAddress, addressObject);

    billingAddress.setCustomerId(parseNullInteger(quote.getCustomerId()));
    if(StringUtils.isNotEmpty(order.getCustomerEmail()) && StringUtils.isNotBlank(order.getCustomerEmail())) {
        billingAddress.setEmail(order.getCustomerEmail().toLowerCase());
    }
    billingAddress.setAddressType("billing");

    order.addOrderAddress(billingAddress);
    // salesOrderAddressRepository.saveAndFlush(billingAddress);

  }

  private void copyAddressElements(SalesOrderAddress address, AddressObject addressObject) {

    if (addressObject != null && address != null) {
      address.setCustomerAddressId(parseNullInteger(addressObject.getCustomerAddressId()));
      // address.setQuoteAddressId(addressObject.getAddressId());

      if (null == addressObject.getRegionId() && null != addressObject.getRegion()) {
        String regionId = addressMapperHelper.getAddressMap(addressObject.getCountryId(), addressObject.getRegion()).get("id");
        address.setRegionId(regionId);
      } else {
        address.setRegionId(addressObject.getRegionId());
      }

      // address.setFax(addressObject.getFax());
      address.setRegion(addressObject.getRegion());
      address.setPostcode(addressObject.getPostcode());
      address.setLastname(addressObject.getLastname());
      address.setCity(addressObject.getCity());
      address.setTelephone(addressObject.getTelephone());
      address.setCountryId(addressObject.getCountryId());
      address.setFirstname(addressObject.getFirstname());

      address.setStreet(addressObject.getStreet());
      address.setStreetActual(addressObject.getStreet().replace(addressObject.getBuildingNumber(), ""));
      address.setArea(addressObject.getArea());
      address.setNearestLandmark(addressObject.getNearestLandmark());
      address.setLatitude(addressObject.getLatitude());
      address.setUnitNumber(addressObject.getUnitNumber());
      address.setShortAddress(addressObject.getShortAddress());
      address.setPostalCode(addressObject.getPostalCode());
      address.setKsaAddressComplaint(addressObject.getKsaAddressComplaint());
      address.setLongitude(addressObject.getLongitude());
      address.setFormattedAddress(addressObject.getFormattedAddress());
      address.setShortAddress(addressObject.getShortAddress());
      address.setPostalCode(addressObject.getPostalCode());
      address.setUnitNumber(addressObject.getUnitNumber());
      address.setKsaAddressComplaint(addressObject.getKsaAddressComplaint());
      address.setBuildingNumber(addressObject.getBuildingNumber());

    }

  }

  public void createOrderPayment(QuoteDTO quote, SalesOrder order, String paymentMethod, Stores store) {
	  SalesOrderPayment payment = null;
	  if(CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
		  payment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
	  }
	  if(null == payment) {
		  payment = new SalesOrderPayment();
	  }

    // payment.setParentId(order.getEntityId());
    payment.setShippingAmount(getShippingAmount(quote));
    payment.setBaseShippingAmount(getBaseValueDecimal(getShippingAmount(quote), store.getCurrencyConversionRate()));
    // payment.setShippingCaptured(getShippingAmount(quoteObject));
    // payment.setBaseShippingCaptured(getShippingAmount(quoteObject));
    // TODO
    payment.setBaseAmountPaid(null);
    payment.setAmountPaid(null);
    payment.setBaseAmountOrdered(getBaseValueDecimal(getBaseGrandTotal(quote), store.getCurrencyConversionRate()));
    payment.setAmountOrdered(getBaseGrandTotal(quote));
    payment.setMethod(paymentMethod);

    //Setting preferred payment to kafka/pubsub based on flag
      if ((PaymentConstants.CASHONDELIVERY.equals(paymentMethod) || PaymentConstants.SHUKRAN_PAYMENT.equals(paymentMethod)) && order.getCustomerId() != null) {
          publishPreferredPaymentIfValid(paymentMethod, order);
      }
      // TODO based on condition
    String additionalInformation = "";
	try {
		if (OrderConstants.checkTabbyPaymentMethod(paymentMethod)) {
			PaymentDTO tabbyPaymentInformation = new PaymentDTO();
			tabbyPaymentInformation.setId(quote.getTabbyPaymentId());
			tabbyPaymentInformation.setStatus(OrderConstants.TABBY_ORDER_CREATED);
			additionalInformation = mapper.writeValueAsString(tabbyPaymentInformation);
		}else{
			String methodTitle = orderHelper.getMethodTitle(order.getStoreId(), paymentMethod);
			String methodInstructions = orderHelper.getMethodInstructions(order.getStoreId(), paymentMethod);
			PaymentInformationCOD information = new PaymentInformationCOD();

            if(StringUtils.isNotBlank(order.getRemoteIp()) && StringUtils.isNotEmpty(order.getRemoteIp()) && order.getRemoteIp().length()<45) {
                information.setCustomerIp(order.getRemoteIp());
            }

			information.setMethodTitle(methodTitle);
			information.setInstructions(methodInstructions);
            if(paymentMethod.equalsIgnoreCase(OrderConstants.SHUKRAN_PAYMENT)){
                information.setMethodTitle(OrderConstants.SHUKRAN_PAYMENT);
                information.setInstructions("");
            }
			additionalInformation = mapper.writeValueAsString(information);
		}
	} catch (JsonProcessingException e) {
      LOGGER.error("createOrderPayment. Could not create payment information. IncrementId: " + order.getIncrementId());
    }
    payment.setAdditionalInformation(additionalInformation);
    order.addOrderPayment(payment);
    // salesOrderPaymentRepository.saveAndFlush(payment);
  }

	public void createOrderItems(QuoteDTO quote, SalesOrder order, Stores store , boolean isRetryPayment,ProductInventoryRes invResponse) {
		if (!isRetryPayment) {
			extractOrderItemsFromQuote(quote, order, store,invResponse);
		}
		if (null != order.getSubSalesOrder()  && null != order.getSubSalesOrder().getRetryPayment()) {	        
	     order.getSubSalesOrder().setRetryPayment(0);
	     order.setRetryPayment(0);  
		}
        boolean orderStatusFlag =  null != Constants.orderCredentials.getOrderStatusFlag() ? Constants.orderCredentials.getOrderStatusFlag() : Boolean.FALSE;
        if (orderStatusFlag) {
            determineOrderStatus(order);
        }

        SalesOrder resultOrder = salesOrderRepository.saveAndFlush(order);
		LOGGER.info("sales_order_item table insertion done!");
		order.setEntityId(resultOrder.getEntityId());
	}

    private void determineOrderStatus(SalesOrder order) {
        try{
            LOGGER.info("Order status before updating :"+order.getIncrementId()+" current status :"+order.getStatus());
            SalesOrder existingOrder = salesOrderRepository.findByIncrementId(order.getIncrementId());
            if(null!=existingOrder && OrderConstants.PROCESSING_ORDER_STATUS.equalsIgnoreCase(existingOrder.getStatus())){
                LOGGER.info("Order status after updating :"+order.getIncrementId()+" current status :"+existingOrder.getStatus());
                order.setStatus(existingOrder.getStatus());
                order.setState(existingOrder.getState());
            }
        } catch (Exception e) {
            LOGGER.error("Order status update failed: "+order.getIncrementId());
        }
    }

    /**
	 * This will extract order items from Quote and will prepare the sales order items.
	 * @param quote
	 * @param order
	 * @param store
	 */
	public void extractOrderItemsFromQuote(QuoteDTO quote, SalesOrder order, Stores store,ProductInventoryRes invResponse) {
		BigDecimal totalDiscountTaxCompensationAmount = new BigDecimal(0);


		if (null != order.getSubSalesOrder() 
				&& null == order.getSubSalesOrder().getRetryPayment()) {
		for (CatalogProductEntityForQuoteDTO product : quote.getProducts()) {
			BigDecimal itemOriginalPrice = new BigDecimal(0);
			BigDecimal price = new BigDecimal(0);
			BigDecimal actualPrice = null ;
			BigDecimal originalPrice = new BigDecimal(0);

			BigDecimal quantity = parseNullDecimal(product.getQuantity());

			if (product.getPrices().getSpecialPrice() != null && !product.getPrices().getSpecialPrice().equals("undefined")) {
				price = new BigDecimal(product.getPrices().getSpecialPrice());
			} else {
				
				price = new BigDecimal(product.getPrices().getPrice());
			}
			
			if (StringUtils.isNotBlank(product.getPrices().getDroppedPrice())) {

				BigDecimal dropedPrice = new BigDecimal(product.getPrices().getDroppedPrice());

				if (!(dropedPrice.compareTo(BigDecimal.ZERO) == 0)) {

					actualPrice = price; /** this product has dropped price **/

					price = new BigDecimal(product.getPrices().getDroppedPrice());
					
					LOGGER.info("dropped price :"+price + "SKU:"+product.getSku()+" quote id:"+quote.getQuoteId());		
					
				}

			}
			
			
			if (product.getPrices().getPrice() != null) {
				originalPrice = new BigDecimal(product.getPrices().getPrice());
				itemOriginalPrice = new BigDecimal(product.getPrices().getPrice());
			}

			BigDecimal priceWithoutTax = new BigDecimal(product.getPrice());
			BigDecimal discountPercent = new BigDecimal(product.getDiscountPercent());
			BigDecimal discountAmount = product.getDiscountAmount() == null ? new BigDecimal(0)
					: new BigDecimal(product.getDiscountAmount());

			// row total starts coming below line will remove
			BigDecimal rowTotal = new BigDecimal(0);
			if (null != product.getRowTotalInclTax() && null != rowTotal && rowTotal.intValue() == 0) {
				if (null != product.getTaxAmount() && null != product.getDiscountTaxCompensationAmount()) {

					rowTotal = new BigDecimal(product.getRowTotalInclTax())
							.subtract(new BigDecimal(product.getTaxAmount()))
							.subtract(new BigDecimal(product.getDiscountTaxCompensationAmount()));
				} else if (null != product.getTaxAmount()) {

					rowTotal = new BigDecimal(product.getRowTotalInclTax())
							.subtract(new BigDecimal(product.getTaxAmount()));
				}

			}

			BigDecimal taxAmount = new BigDecimal(product.getTaxAmount());
			BigDecimal taxPercent = new BigDecimal(product.getTaxPercent());
			BigDecimal priceIncltax = price;
			SalesOrderItem item = null;

			BigDecimal rowTotalInclTax = quantity.multiply(priceIncltax);
			BigDecimal discountTaxCompensationAmount = new BigDecimal(product.getDiscountTaxCompensationAmount());
			totalDiscountTaxCompensationAmount = totalDiscountTaxCompensationAmount.add(discountTaxCompensationAmount);

			String parentItemOptions = "{\"info_buyRequest\":{\"uenc\":\"<uenc>\",\"product\":\"<product_id>\",\"selected_configurable_option\":\"\",\"related_product\":\"\",\"item\":\"<product_id>\",\"super_attribute\":{\"<super_attr_id>\":\"<super_attr_value>\"},\"qty\":<qty>}}";
			parentItemOptions = parentItemOptions.replace("<uenc>",
					"aHR0cHM6Ly9zdHlsaWZhc2hpb24uY29tL2VuZ2xpc2gvdGVzdC1jb25maWd1cmFibGUtcHJvZHVjdC0zLmh0bWw,");
			parentItemOptions = parentItemOptions.replace("<product_id>", product.getParentProductId());
			if (product.getSuperAttributeId() != null)
				parentItemOptions = parentItemOptions.replace("<super_attr_id>", product.getSuperAttributeId());
			if (product.getSuperAttributeValue() != null)
				parentItemOptions = parentItemOptions.replace("<super_attr_value>", product.getSuperAttributeValue());
			parentItemOptions = parentItemOptions.replace("<qty>", product.getQuantity());
			
			 
//			  if(CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
//				  
//				  item = order.getSalesOrderItem().stream().filter(e -> e.getProductType().equals("configurable")).findFirst().orElse(null);
//			  }
//			  
			  if(null == item) {
				  
				  item = new SalesOrderItem();
			  }
            String warehouseLocationId =
                    Optional.ofNullable(invResponse)
                            .map(ProductInventoryRes::getResponse)
                            .orElseGet(Collections::emptyList)
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(e -> null!=e.getSku() && null!=product.getSku() && e.getSku().equalsIgnoreCase(product.getSku()))
                            .map(ProductValue::getWarehouseId)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(store.getWarehouseId());
            LOGGER.info("IN Extract Order Items - warehouseLocationId : " + warehouseLocationId + " for SKU : " + product.getSku() + " for Quote : " + quote.getQuoteId());
            item.setWarehouseLocationId(warehouseLocationId);
			item.setStoreId(Integer.parseInt(store.getStoreId()));
			item.setCreatedAt((new Timestamp(new Date().getTime())));
			item.setUpdatedAt((new Timestamp(new Date().getTime())));
			item.setProductId(product.getParentProductId());
			item.setProductType(UtilityConstant.PRODCUT_TYPE_ID_CONNF);
			item.setProductOptions(parentItemOptions);
            updateProductAttributesFromQuote(product, item);
            item.setWeight(BigDecimal.ONE);
			item.setVirtual(0);
			item.setSku(product.getSku());
            item.setVendorSku(product.getVariantSku());
			item.setName(product.getName());
            item.setDescription(null);
			item.setAppliedRuleIds(null);
			item.setAdditionalData(null);
			item.setQtyDecimal(0);
			item.setNoDiscount(0);
			item.setQtyOrdered(quantity);
			item.setPrice(priceWithoutTax);
            item.setPoPrice(StringUtils.isNotBlank(product.getPoPrice()) ? new BigDecimal(product.getPoPrice()) : BigDecimal.ZERO);
			item.setBasePrice(getBaseValueDecimal(priceWithoutTax, store.getCurrencyConversionRate()));
			item.setOriginalPrice(itemOriginalPrice);
			item.setBaseOriginalPrice(getBaseValueDecimal(itemOriginalPrice, store.getCurrencyConversionRate()));
			item.setTaxPercent(taxPercent);
			item.setTaxAmount(taxAmount);
			item.setBaseTaxAmount(getBaseValueDecimal(taxAmount, store.getCurrencyConversionRate()));
			item.setRowTotal(rowTotal);
			item.setBaseRowTotal(getBaseValueDecimal(rowTotal, store.getCurrencyConversionRate()));
			item.setPriceInclTax(priceIncltax);
			item.setBasePriceInclTax(getBaseValueDecimal(priceIncltax, store.getCurrencyConversionRate()));
			item.setRowTotalInclTax(rowTotalInclTax);
			item.setBaseRowTotalInclTax(getBaseValueDecimal(rowTotalInclTax, store.getCurrencyConversionRate()));

			item.setDiscountPercent(discountPercent);
			item.setDiscountAmount(discountAmount);
			item.setBaseDiscountAmount(getBaseValueDecimal(discountAmount, store.getCurrencyConversionRate()));
			item.setDiscountTaxCompensationAmount(discountTaxCompensationAmount);
			item.setBaseDiscountTaxCompensationAmount(
					getBaseValueDecimal(discountTaxCompensationAmount, store.getCurrencyConversionRate()));
			if(null != actualPrice) {
			item.setActualPrice(actualPrice );
			}
			if (StringUtils.isNoneBlank(product.getLandedCost())) {
				
				item.setOriginalBasePrice(new BigDecimal(product.getLandedCost()));
				
			}
			if(product.isGift()) {
				
				item.setGiftMessageAvailable(1);
				/** used for check is free git product **/
			}

			/** items add for sub_sales_order_item **/
			if (CollectionUtils.isNotEmpty(product.getAppliedCouponValue())) {

				for (AppliedCouponValue coupon : product.getAppliedCouponValue()) {

					SubSalesOrderItem subSalesOrderItem = new SubSalesOrderItem();

					subSalesOrderItem.setCouponName(coupon.getCoupon());
					subSalesOrderItem.setCouponType(coupon.getType());
					subSalesOrderItem.setDiscount(coupon.getDiscount());
					subSalesOrderItem.setSalesOrder(order);
          if(Objects.nonNull(coupon.getIsGiftVoucher())) {
              subSalesOrderItem.setGiftVoucher(coupon.getIsGiftVoucher());
			}
       
					item.addSubSalesOrderItem(subSalesOrderItem);
				}
			}
			if("IN".equalsIgnoreCase(regionValue)) {
				item.setHsnCode(product.getHsnCode());	
			}
			
			
			
			item.setParentSku(product.getParentSku());
            if(ObjectUtils.isNotEmpty(product.isReturnable())) {
                item.setReturnable(product.isReturnable() ? 1: 0);
            }
			order.addSalesOrderItem(item);

			SalesOrderItem childItem = null;
//			
//			 if(CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
//				  
//				 childItem = order.getSalesOrderItem().stream().filter(e -> e.getProductType().equals("simple")).findFirst().orElse(null);
//			  }
//			  
			  if(null == childItem) {
				  
				  childItem = new SalesOrderItem();
			  }
            childItem.setWarehouseLocationId(warehouseLocationId);
			childItem.setStoreId(Integer.parseInt(store.getStoreId()));
			childItem.setCreatedAt((new Timestamp(new Date().getTime())));
			childItem.setUpdatedAt((new Timestamp(new Date().getTime())));
			childItem.setProductId(product.getProductId());
			childItem.setProductType(UtilityConstant.PRODCUT_TYPE_ID_SIMEPLE);
			childItem.setProductOptions(parentItemOptions);
            updateProductAttributesFromQuote(product, childItem);
            childItem.setWeight(BigDecimal.ONE);
			childItem.setVirtual(0);
			childItem.setSku(product.getSku());
            childItem.setVendorSku(product.getVariantSku());
			childItem.setName(product.getName());
            childItem.setDescription(null);
			childItem.setAppliedRuleIds(null);
			childItem.setAdditionalData(null);
			childItem.setQtyDecimal(0);
			childItem.setNoDiscount(0);
			childItem.setQtyOrdered(quantity);

            if(ObjectUtils.isNotEmpty(product.getImages())) {
                if(product.getImages().getMediaGallery() != null && CollectionUtils.isNotEmpty(product.getImages().getMediaGallery())) {
                    childItem.setItemImgUrl(product.getImages().getMediaGallery().get(0));
                    item.setItemImgUrl(product.getImages().getMediaGallery().get(0));
                }else if(StringUtils.isNotEmpty(product.getImages().getImage()) && StringUtils.isNotBlank(product.getImages().getImage())){
                    childItem.setItemImgUrl(product.getImages().getImage());
                    item.setItemImgUrl(product.getImages().getImage());
                }
            }
            if(StringUtils.isNotEmpty(product.getBrandName()) && StringUtils.isNotBlank(product.getBrandName())){
                childItem.setItemBrandName(product.getBrandName());
                item.setItemBrandName(product.getBrandName());
            }
            if(StringUtils.isNotEmpty(product.getSize()) && StringUtils.isNotBlank(product.getSize())){
                childItem.setItemSize(product.getSize());
                item.setItemSize(product.getSize());
            }

			childItem.setPrice(priceWithoutTax);
            childItem.setPoPrice(StringUtils.isNotBlank(product.getPoPrice()) ? new BigDecimal(product.getPoPrice()) : BigDecimal.ZERO);

            item.setShukranCoinsEarned(BigDecimal.ZERO);
            childItem.setShukranCoinsEarned(BigDecimal.ZERO);
            item.setShukranCoinsBurned(BigDecimal.ZERO);
            childItem.setShukranCoinsBurned(BigDecimal.ZERO);
            item.setShukranCoinsBurnedValueInCurrency(BigDecimal.ZERO);
            childItem.setShukranCoinsBurnedValueInCurrency(BigDecimal.ZERO);
            item.setShukranCoinsBurnedValueInBaseCurrency(BigDecimal.ZERO);
            childItem.setShukranCoinsBurnedValueInBaseCurrency(BigDecimal.ZERO);
            item.setShukranCoinsEarnedValueInCurrency(BigDecimal.ZERO);
            childItem.setShukranCoinsEarnedValueInCurrency(BigDecimal.ZERO);
            item.setShukranCoinsEarnedValueInBaseCurrency(BigDecimal.ZERO);
            childItem.setShukranCoinsEarnedValueInBaseCurrency(BigDecimal.ZERO);
            if(store.getIsShukranEnable()) {
                // skukran earn
                if (quote.getTotalShukranEarn() != null && quote.getTotalShukranEarn().compareTo(BigDecimal.ZERO) > 0 && product.getShukranEarn() != null && product.getShukranEarn().compareTo(BigDecimal.ZERO) > 0) {
                    item.setShukranCoinsEarned(product.getShukranEarn());
                    childItem.setShukranCoinsEarned(product.getShukranEarn());
                }
                if (quote.getTotalShukranEarnValueInBaseCurrency() != null && quote.getTotalShukranEarnValueInBaseCurrency().compareTo(BigDecimal.ZERO) > 0 && product.getShukranEarnInBaseCurrency() != null && product.getShukranEarnInBaseCurrency().compareTo(BigDecimal.ZERO) > 0) {
                    item.setShukranCoinsEarnedValueInBaseCurrency(product.getShukranEarnInBaseCurrency());
                    childItem.setShukranCoinsEarnedValueInBaseCurrency(product.getShukranEarnInBaseCurrency());
                }
                if (quote.getTotalShukranEarnValueInCurrency() != null && quote.getTotalShukranEarnValueInCurrency().compareTo(BigDecimal.ZERO) > 0 && product.getShukranEarnInCurrency() != null && product.getShukranEarnInCurrency().compareTo(BigDecimal.ZERO) > 0) {
                    item.setShukranCoinsEarnedValueInCurrency(product.getShukranEarnInCurrency());
                    childItem.setShukranCoinsEarnedValueInCurrency(product.getShukranEarnInCurrency());
                }
                // shukran burned
                if (quote.getTotalShukranBurn() != null && quote.getTotalShukranBurn().compareTo(BigDecimal.ZERO) > 0 && product.getShukranBurn() != null && product.getShukranBurn().compareTo(BigDecimal.ZERO) > 0) {
                    item.setShukranCoinsBurned(product.getShukranBurn());
                    childItem.setShukranCoinsBurned(product.getShukranBurn());
                }
                if (quote.getTotalShukranBurnValueInBaseCurrency() != null && quote.getTotalShukranBurnValueInBaseCurrency().compareTo(BigDecimal.ZERO) > 0 && product.getShukranBurnInBaseCurrency() != null && product.getShukranBurnInBaseCurrency().compareTo(BigDecimal.ZERO) > 0) {
                    item.setShukranCoinsBurnedValueInBaseCurrency(product.getShukranBurnInBaseCurrency());
                    childItem.setShukranCoinsBurnedValueInBaseCurrency(product.getShukranBurnInBaseCurrency());
                }
                if (quote.getTotalShukranBurnValueInCurrency() != null && quote.getTotalShukranBurnValueInCurrency().compareTo(BigDecimal.ZERO) > 0 && product.getShukranBurnInCurrency() != null && product.getShukranBurnInCurrency().compareTo(BigDecimal.ZERO) > 0) {
                    item.setShukranCoinsBurnedValueInCurrency(product.getShukranBurnInCurrency());
                    childItem.setShukranCoinsBurnedValueInCurrency(product.getShukranBurnInCurrency());
                }
                if(StringUtils.isNotBlank(product.getL4Category()) && StringUtils.isNotEmpty(product.getL4Category())){
                    item.setShukranL4Category(product.getL4Category());
                    childItem.setShukranL4Category(product.getL4Category());
                }
                item.setOnSale(product.getIsSale() != null && product.getIsSale());
                childItem.setOnSale(product.getIsSale() != null && product.getIsSale());
            }

			childItem.setBasePrice(getBaseValueDecimal(priceWithoutTax, store.getCurrencyConversionRate()));
			childItem.setOriginalPrice(itemOriginalPrice);
			childItem.setBaseOriginalPrice(getBaseValueDecimal(itemOriginalPrice, store.getCurrencyConversionRate()));
			childItem.setTaxPercent(taxPercent);
			childItem.setTaxAmount(taxAmount);
			childItem.setBaseTaxAmount(getBaseValueDecimal(taxAmount, store.getCurrencyConversionRate()));
			childItem.setRowTotal(rowTotal);
			childItem.setBaseRowTotal(getBaseValueDecimal(rowTotal, store.getCurrencyConversionRate()));
			childItem.setPriceInclTax(priceIncltax);
			childItem.setBasePriceInclTax(getBaseValueDecimal(priceIncltax, store.getCurrencyConversionRate()));
			childItem.setRowTotalInclTax(rowTotalInclTax);
			childItem.setBaseRowTotalInclTax(getBaseValueDecimal(rowTotalInclTax, store.getCurrencyConversionRate()));

			childItem.setDiscountPercent(discountPercent);
			childItem.setDiscountAmount(discountAmount);
			childItem.setBaseDiscountAmount(getBaseValueDecimal(discountAmount, store.getCurrencyConversionRate()));
			childItem.setDiscountTaxCompensationAmount(discountTaxCompensationAmount);
			childItem.setBaseDiscountTaxCompensationAmount(
					getBaseValueDecimal(discountTaxCompensationAmount, store.getCurrencyConversionRate()));
			childItem.setParentOrderItem(item);
			childItem.setParentSku(product.getParentSku());
			if(null != actualPrice) {
			item.setActualPrice(actualPrice);
			}
			if(product.isGift()) {
				
				childItem.setGiftMessageAvailable(1);
				/** used for check is free git product **/
			}

			if (StringUtils.isNoneBlank(product.getLandedCost())) {
				childItem.setOriginalBasePrice(new BigDecimal(product.getLandedCost()));
			}
            if(ObjectUtils.isNotEmpty(product.isReturnable())) {
                childItem.setReturnable(product.isReturnable() ? 1: 0);
            }
            if("IN".equalsIgnoreCase(regionValue)) {
				if (product.getTaxObj() != null) {
					SalesOrderItemTax salesOrderItemtaxIGST = new SalesOrderItemTax();
					salesOrderItemtaxIGST.setTaxCountry(regionValue);
					salesOrderItemtaxIGST.setTaxType("IGST");
					salesOrderItemtaxIGST.setTaxPercentage(new BigDecimal(product.getTaxObj().getTaxIGST()));
					salesOrderItemtaxIGST.setTaxAmount(new BigDecimal(product.getTaxObj().getTaxIGSTAmount()));
					childItem.addSalesOrderItemTax(salesOrderItemtaxIGST);
					
					SalesOrderItemTax salesOrderItemtaxCGST = new SalesOrderItemTax();
					salesOrderItemtaxCGST.setTaxCountry(regionValue);
					salesOrderItemtaxCGST.setTaxType("CGST");
					salesOrderItemtaxCGST.setTaxPercentage(new BigDecimal(product.getTaxObj().getTaxCGST()));
					salesOrderItemtaxCGST.setTaxAmount(new BigDecimal(product.getTaxObj().getTaxCGSTAmount()));
					childItem.addSalesOrderItemTax(salesOrderItemtaxCGST);
					
					SalesOrderItemTax salesOrderItemtaxSGST = new SalesOrderItemTax();
					salesOrderItemtaxSGST.setTaxCountry(regionValue);
					salesOrderItemtaxSGST.setTaxType("SGST");
					salesOrderItemtaxSGST.setTaxPercentage(new BigDecimal(product.getTaxObj().getTaxSGST()));
					salesOrderItemtaxSGST.setTaxAmount(new BigDecimal(product.getTaxObj().getTaxSGSTAmount()));
					childItem.addSalesOrderItemTax(salesOrderItemtaxSGST);
				}
				childItem.setHsnCode(product.getHsnCode());	
			}		
			order.addSalesOrderItem(childItem);
		}
		
	}
		order.setDiscountTaxCompensationAmount(totalDiscountTaxCompensationAmount);
		order.setBaseDiscountTaxCompensationAmount(
				getBaseValueDecimal(totalDiscountTaxCompensationAmount, store.getCurrencyConversionRate()));
	}

    public void createOrderGrid(QuoteDTO quote, SalesOrder order, String paymentMethod, AddressObject shippingAddress,
      Stores store, int source, String appVersion) {
    SalesOrderGrid orderGrid = new SalesOrderGrid();

    if (StringUtils.isNoneBlank(quote.getCustomerId())) {
      CustomerEntity customerEntity = orderHelper.getCustomerDetails(parseNullInteger(quote.getCustomerId()) , null);
      if(StringUtils.isNotEmpty(customerEntity.getEmail()) && StringUtils.isNotBlank(customerEntity.getEmail())) {
          orderGrid.setCustomerEmail(customerEntity.getEmail().toLowerCase());
      }
      orderGrid.setCustomerName(quote.getShippingAddress().getFirstname() + " " + quote.getShippingAddress().getFirstname());
    } else if(ObjectUtils.isNotEmpty(quote.getShippingAddress())) {
            if(StringUtils.isNotBlank(quote.getShippingAddress().getEmail()) && StringUtils.isNotEmpty(quote.getShippingAddress().getEmail())) {
                orderGrid.setCustomerEmail(quote.getShippingAddress().getEmail().toLowerCase());
            }
            orderGrid
                    .setCustomerName(quote.getShippingAddress().getFirstname() + " " + quote.getShippingAddress().getLastname());

    }

    orderGrid.setEntityId(order.getEntityId());
    if (orderShouldBePaymentPending(paymentMethod) && !order.getStatus().equals(OrderConstants.PROCESSING_ORDER_STATUS)) {
      orderGrid.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
    } else {
      orderGrid.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
      LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in createOrderGrid");
    }
    orderGrid.setStoreId(Integer.parseInt(store.getStoreId()));
    orderGrid.setCustomerId(parseNullInteger(quote.getCustomerId()));
    orderGrid.setBaseGrandTotal(getBaseValueDecimal(getBaseGrandTotal(quote), store.getCurrencyConversionRate()));
    // orderGrid.setBaseTotalPaid(getBaseGrandTotal(quoteObject));
    // orderGrid.setTotalPaid(getBaseGrandTotal(quoteObject));
    orderGrid.setGrandTotal(getBaseGrandTotal(quote));
    orderGrid.setSubtotal(getSubtotal(quote));
    orderGrid.setTotalRefunded(null);
    orderGrid.setIncrementId(order.getIncrementId());
    orderGrid.setBaseCurrencyCode(Constants.QUOUTE_BASE_CURRENCY_CODE);

    orderGrid.setOrderCurrencyCode(quote.getCurrency());
    orderGrid.setStoreName(GenericConstants.STORE_NAME);
    orderGrid.setCreatedAt(new Timestamp(new Date().getTime()));
    orderGrid.setUpdatedAt(new Timestamp(new Date().getTime()));
    if (null != shippingAddress) {
      orderGrid.setShippingName(shippingAddress.getFirstname() + " " + shippingAddress.getLastname());
      orderGrid.setBillingName(shippingAddress.getFirstname() + " " + shippingAddress.getLastname());
      /** billing address is same as shipping address **/
    } else {
      orderGrid.setShippingName(quote.getCustomerFirstname() + " " + quote.getCustomerLastname());
      orderGrid.setBillingName(quote.getCustomerFirstname() + " " + quote.getCustomerLastname());
      /** billing address is same as shipping address **/
    }

    String addressObjectStr = "";
    try {
      if (shippingAddress != null) {
        addressObjectStr = shippingAddress.getStreet() + "," + shippingAddress.getCity() + ","
            + shippingAddress.getRegion();
      }
    } catch (Exception e) {
      LOGGER.error("Could not create address object! createOrderGrid");
    }
    orderGrid.setBillingAddress(addressObjectStr);
    orderGrid.setShippingAddress(addressObjectStr);
    orderGrid.setShippingInformation(Constants.SHIPPING_DESCRIPTION);

    if (quote.getCustomerId() != null) {
      orderGrid.setCustomerGroup("1");
    } else {
      orderGrid.setCustomerGroup("0");
    }

    orderGrid.setShippingAndHandling(getShippingAmount(quote));
    orderGrid.setPaymentMethod(paymentMethod);
    orderGrid.setSignifydGuaranteeStatus(null);

    orderGrid.setSource(source);
    orderGrid.setAppVersion(appVersion);

    salesOrderGridRepository.saveAndFlush(orderGrid);

  }

  private int setCodCharges(int codCharges, BigDecimal currencyConversionRate) {
    return new BigDecimal(codCharges).multiply(currencyConversionRate).setScale(2, RoundingMode.HALF_UP).intValue();
    // return new BigDecimal(codCharges).divide(currencyConversionRate, 2,
    // RoundingMode.HALF_UP).intValue();
  }

  private BigDecimal getBaseValueDecimal(BigDecimal value, BigDecimal currencyConversionRate) {
    return value.multiply(currencyConversionRate).setScale(4, RoundingMode.HALF_UP);
  }

  // private BigDecimal getBaseMiniumImportFee(BigDecimal value, BigDecimal
  // currencyConversionRate) {
  // return
  // value.multiply(currencyConversionRate).setScale(4,RoundingMode.HALF_UP);
  // }

  public void reedmeExternalCoupon(QuoteDTO quote, Stores store, SalesOrder createdOrder, boolean isProxy) {

    // CustomCouponValidationV4Response resp = new
    // CustomCouponValidationV4Response();

    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.setContentType(MediaType.APPLICATION_JSON);
    requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

    CustomCouponRedemptionV5Request payload = new CustomCouponRedemptionV5Request();
    if (null != createdOrder.getCustomerId()) {

      payload.setCustomerEmailId(createdOrder.getCustomerId().toString());

    } else {

      payload.setCustomerEmailId(createdOrder.getCustomerEmail());
    }
    payload.setOrderReferenceId(createdOrder.getIncrementId());
    payload.setQuoteId(quote.getQuoteId());
    payload.setStoreId(store.getStoreId());

    HttpEntity<CustomCouponRedemptionV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

    String url = getPromoRedemptionUrl(store.getStoreId(), createdOrder.getCustomerEmail());
    	
    Map<String, Object> parameters = new HashMap<>();

    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

    LOGGER.info("Redemption Coupon URl:" + builder.buildAndExpand(parameters).toUri());
    LOGGER.info("Request Body" + requestBody);
    SubSalesOrder subSalesOrder = null;
    try {

      LOGGER.info("request Body:" + mapper.writeValueAsString(requestBody.getBody()));
      ResponseEntity<CustomCouponRedemptionV5Response> response = restTemplate.exchange(
          builder.buildAndExpand(parameters).toUri(), HttpMethod.POST, requestBody,
          CustomCouponRedemptionV5Response.class);

      if (response.getStatusCode() == HttpStatus.OK) {

        LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));
        CustomCouponRedemptionV5Response body = response.getBody();
        if (body != null && body.getCode() == 200) {
          LOGGER.info("Coupon redemption response: " + response.getBody());

          String redmeptionTrackingId = body.getTrackingId();

          if (StringUtils.isNoneBlank(redmeptionTrackingId)) {
            subSalesOrder = createdOrder.getSubSalesOrder();

            if (null == subSalesOrder) {
              subSalesOrder = new SubSalesOrder();
            }
            subSalesOrder.setExternalCouponRedemptionTrackingId(redmeptionTrackingId);
            subSalesOrder.setExternalCouponRedemptionStatus(1);
            createdOrder.setSubSalesOrder(subSalesOrder);
            subSalesOrder.setSalesOrder(createdOrder);

            if(!isProxy)
            	salesOrderRepository.saveAndFlush(createdOrder);

          }
        }
      }

      /**
       * external coupon redemption coupon status =0(cancel redemption fail), 1(apply
       * redemption success) ,2(cancellation success) ,3(apply redemption fail)
       **/
    } catch (RestClientException | JsonProcessingException e) {

      LOGGER.error("Exception occurred  during REST call:" + e.getMessage());
      subSalesOrder = createdOrder.getSubSalesOrder();
      if (null == subSalesOrder) {

        subSalesOrder = new SubSalesOrder();
      }
      subSalesOrder.setExternalCouponRedemptionStatus(3);
      subSalesOrder.setSalesOrder(createdOrder);
      createdOrder.setSubSalesOrder(subSalesOrder);
      if(!isProxy)
    	  salesOrderRepository.saveAndFlush(createdOrder);
    }

  }


  private String getPromoRedemptionUrl(String storeId, String customerEmail) {
	  
	  LOGGER.info("Inside getPromoRedemptionUrl for customer " + customerEmail + " store id " + storeId);
	  PromoRedemptionValues values = Constants.getPromoRedemptionUrl();
	  String url = values.getDefaultRedemptionEndpoint();
	  
	  if(values.isEnabled() && !values.isAllowAllStores()) {
		  
		  if(values.isAllowInternalUsers() && customerEmail.contains(Constants.INTERNAL_USERS_EMAIL)
				  && !values.getExcludeEmailId().contains(customerEmail)) {
			  url = values.getRedemptionEndpoint();
			  
		  } else if(values.getAllowedStores().stream().anyMatch(store -> store.equals(Integer.parseInt(storeId)))) {
			  url = values.getRedemptionEndpoint();
		  }
	  }
	  LOGGER.info("Inside getPromoRedemptionUrl: url is " + url);
	return url;
}
  
  	/**
  	 * Update Estimated Date and Fast Delivery if eligible
  	 * @param quote
  	 * @param order
  	 */
	public void setEstmateDateAndFastDelivery(QuoteDTO quote, SalesOrder order) {
		AddressMapperCity cityDetail = setEstmateDate(quote, order);
		SubSalesOrder subSalesOrder = order.getSubSalesOrder();
		if (Objects.nonNull(cityDetail.getFastDeliver()) && cityDetail.getFastDeliver().booleanValue()
				&& cityDetail.isFastDeliveryEligible()) {
			subSalesOrder.setFasterDelivery(1);
		} else {
			subSalesOrder.setFasterDelivery(0);
		}
		order.setSubSalesOrder(subSalesOrder);
	}

    /**
     * Update Estimated Date and Fast Delivery if eligible
     * @param quote
     * @param order
     */
    public void setEstmateDateAndFastDeliveryV2(QuoteDTO quote, SalesOrder order,String storeId) {
        WarehouseResponseWrapper warehouseResponseWrapper = setEstmateDateV2(quote, order,storeId);
        SubSalesOrder subSalesOrder = order.getSubSalesOrder();
        int fasterDelivery = Optional.ofNullable(warehouseResponseWrapper)
                .map(WarehouseResponseWrapper::getResponse)
                .filter(list -> !list.isEmpty())
                .map(list -> list.size() == 1 ? list.get(0) : null)     // only accept exactly one response
                .map(WarehouseResponse::getData)
                .filter(Objects::nonNull)
                .filter(d -> d.isFast_delivery() && d.isFast_delivery_eligible())
                .map(d -> 1)
                .orElse(0);
        subSalesOrder.setFasterDelivery(fasterDelivery);
        order.setSubSalesOrder(subSalesOrder);
    }
	
	public AddressMapperCity setEstmateDate(QuoteDTO quote, SalesOrder order) {
		String incrementId = order.getIncrementId();
		try {
			AddressObject shippingAddress = quote.getShippingAddress();
			AddressMapperCity cityDetail = getAdrsmprCityDetails(shippingAddress.getCity(), shippingAddress.getRegionId(),
					shippingAddress.getCountryId(), incrementId);
			if (Objects.nonNull(cityDetail) && Objects.nonNull(cityDetail.getEstimatedDate())) {
				DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
				Date date = formatter.parse(cityDetail.getEstimatedDate());
				Timestamp timeStampDate = new Timestamp(date.getTime());
				order.setEstimatedDeliveryTime(timeStampDate);
				LOGGER.info("Evaluated estimated date set for Order : " + incrementId + " Date : " + timeStampDate
						+ " Fast delivery : " + cityDetail.isFastDeliveryEligible());
			} else {
				Date currentDate = new Date();
				LOGGER.info(dateFormat.format(currentDate));
				Calendar c = Calendar.getInstance();
				c.setTime(currentDate);
				c.add(Calendar.DATE, Integer.parseInt((quote.getShippingAddress().getCityMapper().getMaxSla())));
				Date estimatedDate = c.getTime();
				Timestamp estimatedDateTimeStamp = new Timestamp(estimatedDate.getTime());
				order.setEstimatedDeliveryTime(estimatedDateTimeStamp);
				LOGGER.info("Default estimated date set for Order : " + incrementId + " Date : " + estimatedDateTimeStamp);
			}
			return cityDetail;
		} catch (ParseException e) {
			LOGGER.error("Error in setting ETD for order : " + incrementId + " Error ", e);
		}
		return null;
	}

    public WarehouseResponseWrapper setEstmateDateV2(QuoteDTO quote, SalesOrder order,String storeId) {
        String incrementId = order.getIncrementId();
        try {
            AddressObject shippingAddress = quote.getShippingAddress();
            WarehouseResponseWrapper warehouseResponseWrapper = getAdrsmprCityDetailsV2(quote,shippingAddress.getCityMapper().getId(), shippingAddress.getRegionId(),
                    shippingAddress.getCountryId(), incrementId,storeId);
            if (Objects.nonNull(warehouseResponseWrapper) && Objects.nonNull(warehouseResponseWrapper.getEstimated_date())) {
                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                Date date = formatter.parse(warehouseResponseWrapper.getEstimated_date());
                Timestamp timeStampDate = new Timestamp(date.getTime());
                order.setEstimatedDeliveryTime(timeStampDate);
                LOGGER.info("[setEstmateDateV2] Evaluated estimated date set for Order : " + incrementId + " Date : " + timeStampDate
                       );
            } else {
                Date currentDate = new Date();
                LOGGER.info(dateFormat.format(currentDate));
                Calendar c = Calendar.getInstance();
                c.setTime(currentDate);
                c.add(Calendar.DATE, Integer.parseInt((quote.getShippingAddress().getCityMapper().getMaxSla())));
                Date estimatedDate = c.getTime();
                Timestamp estimatedDateTimeStamp = new Timestamp(estimatedDate.getTime());
                order.setEstimatedDeliveryTime(estimatedDateTimeStamp);
                LOGGER.info("Default estimated date set for Order : " + incrementId + " Date : " + estimatedDateTimeStamp);
            }
            return warehouseResponseWrapper;
        } catch (ParseException e) {
            LOGGER.error("Error in setting ETD for order : " + incrementId + " Error ", e);
        }
        return null;
    }


	public AddressMapperCity getAdrsmprCityDetails(String city, String regionId, String country, String incrementId) {
		LOGGER.info("Adrsmpr invoked for order : " + incrementId);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		CitySearchAddressMapperRequest payload = new CitySearchAddressMapperRequest();

		payload.setCountry(country);
		payload.setCitySearchKey(city);
		payload.setRegionId(regionId);

		HttpEntity<CitySearchAddressMapperRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = "{url}/api/address/city/etd";
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("url", addressMapperUrl);

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
		URI uri = builder.buildAndExpand(parameters).toUri();
		LOGGER.info(
				"Address Mapper Coupon URL:" + uri + " For Order : " + incrementId + " Request Body : " + requestBody);
		try {
			ResponseEntity<AddressMapperResponse> response = restTemplate.exchange(uri, HttpMethod.POST, requestBody,
					AddressMapperResponse.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				AddressMapperResponse body = response.getBody();
				if (body != null && body.getStatusCode().equals("200")) {
					LOGGER.info("AddressMapper. For Order " + incrementId + " Response: " + body);
					return body.getResponse();
				}
			}
		} catch (RestClientException e) {
			LOGGER.error("Exception during adrsmpr call for Order : " + incrementId + " . Error : " + e.getMessage());

		}
		return null;
	}

    public WarehouseResponseWrapper getAdrsmprCityDetailsV2(QuoteDTO quote,String city, String regionId, String country, String incrementId,String storeId) {
        LOGGER.info("[getAdrsmprCityDetailsV2] Adrsmpr invoked for order : " + incrementId);
        List<String> skus = quote.getProducts().stream().map(e -> e.getSku()).toList();
        ProductStatusRequest productStatusReq = new ProductStatusRequest();
        productStatusReq.setStoreId(Integer.parseInt(storeId));
        productStatusReq.setSkus(skus);
        ProductInventoryRes invResponse = getInventoryInfoOfQuoteProduct(productStatusReq);
        Set<String> warehouseIds = Optional.ofNullable(invResponse)
                .map(ProductInventoryRes::getResponse)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull)
                .map(ProductValue::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
        CitySearchAddressMapperRequestV2 payload = new CitySearchAddressMapperRequestV2();

        payload.setCity_id(city);
        payload.setWarehouse_ids(warehouseIds);

        HttpEntity<CitySearchAddressMapperRequestV2> requestBody = new HttpEntity<>(payload, requestHeaders);

        String url = "{url}/api/sla/search-city";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("url", addressMapperUrl);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        URI uri = builder.buildAndExpand(parameters).toUri();
        LOGGER.info(
                "[getAdrsmprCityDetailsV2] Address Mapper Coupon URL:" + uri + " For Order : " + incrementId + " Request Body : " + requestBody);
        try {
            ResponseEntity<WarehouseResponseWrapper> response = restTemplate.exchange(uri, HttpMethod.POST, requestBody,
                    WarehouseResponseWrapper.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                WarehouseResponseWrapper body = response.getBody();
                if (body != null && body.getStatusCode().equalsIgnoreCase("200") && body.getResponse() != null) {
                    LOGGER.info("[getAdrsmprCityDetailsV2]  AddressMapper. For Order " + incrementId + " Response: " + body);
                    return body;
                }
            }
        } catch (RestClientException e) {
            LOGGER.error("[getAdrsmprCityDetailsV2] Exception during adrsmpr call for Order : " + incrementId + " . Error : " + e.getMessage());

        }
        return null;
    }
  
private void setAddressCityEstimateDate(QuoteDTO quote, SalesOrder order) {

	if (null != quote.getShippingAddress() && null != quote.getShippingAddress().getCityMapper()
			&& null != quote.getShippingAddress().getCityMapper().getMaxSla()) {

		
		if(null != quote.getShippingAddress().getCityMapper().getFastDeliver() &&
				quote.getShippingAddress().getCityMapper().getFastDeliver()
				&& null != quote.getShippingAddress().getCityMapper().getFstdlvrythrsTime()
				&& quote.getShippingAddress().getCityMapper().getFstdlvrythrsTime().contains(":")) {
			
			if (null != quote.getShippingAddress().getCityMapper()
					&& null != quote.getShippingAddress().getCityMapper().getEsimatedDate()) {
				
				LOGGER.info("estimated date is not null");				
				Date date = new Date();
				try {
					date = dateFormat.parse(quote.getShippingAddress().getCityMapper().getEsimatedDate());
				} catch (ParseException e) {

					LOGGER.info("error during date parse for estimated date");

				}
				Timestamp timeStampDate = new Timestamp(date.getTime());

				order.setEstimatedDeliveryTime(timeStampDate);

			}else {
				
				LOGGER.info("else part of estimated date");	
		          Date currentDate = new Date();
		          LOGGER.info(dateFormat.format(currentDate));
		          Calendar c = Calendar.getInstance();
		          c.setTime(currentDate);
		          c.add(Calendar.DATE, Integer.parseInt((quote.getShippingAddress().getCityMapper().getMaxSla())));
		          Date estimatedDate = c.getTime();
		          Timestamp estimatedDateTimeStamp = new Timestamp(estimatedDate.getTime());
		          order.setEstimatedDeliveryTime(estimatedDateTimeStamp);
			}

		}else if (null != quote.getShippingAddress().getCityMapper()
				&& null != quote.getShippingAddress().getCityMapper().getEsimatedDate()) {
			
			LOGGER.info("estimated date is not null");				
			Date date = new Date();
			try {
				date = dateFormat.parse(quote.getShippingAddress().getCityMapper().getEsimatedDate());
			} catch (ParseException e) {

				LOGGER.info("error during date parse for estimated date");

			}
			Timestamp timeStampDate = new Timestamp(date.getTime());

			order.setEstimatedDeliveryTime(timeStampDate);

		} else {
			LOGGER.info("else part of estimated date");	
          Date currentDate = new Date();
          LOGGER.info(dateFormat.format(currentDate));
          Calendar c = Calendar.getInstance();
          c.setTime(currentDate);
          c.add(Calendar.DATE, Integer.parseInt((quote.getShippingAddress().getCityMapper().getMaxSla())));
          Date estimatedDate = c.getTime();
          Timestamp estimatedDateTimeStamp = new Timestamp(estimatedDate.getTime());
          order.setEstimatedDeliveryTime(estimatedDateTimeStamp);
        }
		

	}
}



  /**
   *
   * @param quote QuoteDTO
   * @param order SalesOrder
   * @param store Stores
   */	
  public void createOrderItemsProductDetails(QuoteDTO quote, SalesOrder order, Stores store , boolean isRetryPayment) {

    for (CatalogProductEntityForQuoteDTO product : quote.getProducts()) {

      SalesOrderProductDetails salesOrderProductDetails = new SalesOrderProductDetails();
      salesOrderProductDetails.setStoreId(Integer.valueOf(store.getStoreId()));
      salesOrderProductDetails.setProductId(Integer.valueOf(product.getParentProductId()));
      salesOrderProductDetails.setSku(product.getParentSku());
      salesOrderProductDetails.setName(product.getName());
      if(product.getImages() != null) {
        salesOrderProductDetails.setImageUrl(product.getImages().getImage());
      }
      int isReturnApplicable = product.isReturnApplicable() ? 1 : 0;
      salesOrderProductDetails.setIsReturnApplicable(isReturnApplicable);
      salesOrderProductDetails.setSize(product.getSize());

      order.addSalesOrderProductDetails(salesOrderProductDetails);

    }

  }

  
  	/**
  	 * 
  	 * @param quote
  	 * @param paymentMethod
  	 * @param salesOrde
  	 * @return
  	 */
	public ProxyOrder createProxyOrder(QuoteDTO quote, String paymentMethod, SalesOrder salesOrde, CreateOrderRequestV2 request) {
		ProxyOrder proxyorder = new ProxyOrder();
		proxyorder.setQuoteId(quote.getQuoteId());
		proxyorder.setPaymentId(quote.getTabbyPaymentId());
		try {
			proxyorder.setQuote(mapper.writeValueAsString(quote));
		} catch (JsonProcessingException e) {
			LOGGER.error("Error In Setting Quote Obj on saving proxy order. " + e);
		}
		proxyorder.setInventoryReleased(false);
		proxyorder.setIncrementId(salesOrde.getIncrementId());
		proxyorder.setPaymentMethod(paymentMethod);
		proxyorder.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);		
		try {
			proxyorder.setSalesOrder(mapper.writeValueAsString(salesOrde));
		} catch (JsonProcessingException e) {
			LOGGER.error("Error In Setting Order Obj on saving proxy order. " + e);
		}
		try {
			proxyorder.setOrderRequest(mapper.writeValueAsString(request));
		} catch (Exception e) {
			LOGGER.error("Error In Setting Create Order Request on saving proxy order. " + e);
		}
		proxyorder.setStoreId(salesOrde.getStoreId());
		proxyorder.setCustomerId(salesOrde.getCustomerId());
		proxyorder.setEmail(salesOrde.getCustomerEmail());
		return proxyOrderRepository.save(proxyorder);
	}

    /**
     *
     * @param quote
     * @param paymentMethod
     * @param salesOrde
     * @return
     */
    public ProxyOrder createV3ProxyOrder(QuoteV7DTO quote, String paymentMethod, SalesOrder salesOrde, CreateOrderRequestV2 request) {
        ProxyOrder proxyorder = new ProxyOrder();
        proxyorder.setQuoteId(quote.getQuoteId());
        proxyorder.setPaymentId(quote.getTabbyPaymentId());
        try {
            proxyorder.setQuote(mapper.writeValueAsString(quote));
        } catch (JsonProcessingException e) {
            LOGGER.error("Error In Setting Quote Obj on saving proxy order. " , e);
        }
        proxyorder.setInventoryReleased(false);
        proxyorder.setIncrementId(salesOrde.getIncrementId());
        proxyorder.setPaymentMethod(paymentMethod);
        proxyorder.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
        try {
            proxyorder.setSalesOrder(mapper.writeValueAsString(salesOrde));
        } catch (JsonProcessingException e) {
            LOGGER.error("Error In Setting Order Obj on saving proxy order. " , e);
        }
        try {
            proxyorder.setOrderRequest(mapper.writeValueAsString(request));
        } catch (Exception e) {
            LOGGER.error("Error In Setting Create Order Request on saving proxy order. " , e);
        }
        proxyorder.setStoreId(salesOrde.getStoreId());
        proxyorder.setCustomerId(salesOrde.getCustomerId());
        proxyorder.setEmail(salesOrde.getCustomerEmail());
        return proxyOrderRepository.save(proxyorder);
    }
	
	/**
	 * Find Proxy Order By PaymentID
	 * @param id
	 * @return
	 */
	public ProxyOrder findProxyOrderByPaymentId(String id) {
		return proxyOrderRepository.findByPaymentId(id);
	}
	
	/**
	 * Update InventoryRelease Status for Proxy Order
	 * @param orderId
	 * @param status
	 */
	public void updateProxyOrderInventoryRelease(Long orderId, boolean status) {
		Optional<ProxyOrder> order = proxyOrderRepository.findById(orderId);
		if(order.isPresent()) {
			ProxyOrder proxyOrder = order.get();
			proxyOrder.setInventoryReleased(status);
			proxyOrderRepository.saveAndFlush(proxyOrder);
		}
	}
	
	/**
	 * Update Proxy Order Status By Payment ID
	 * @param orderId
	 * @param status
	 */
	public void updateProxyOrderStatusByPaymentId(String paymentId, String status) {
		if(Objects.isNull(paymentId))
			return;
		ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentId);
		if(proxyOrder != null) {
			proxyOrder.setStatus(status);
			proxyOrderRepository.saveAndFlush(proxyOrder);
		}
	}

	/**
	 * API-2647 Authorization capture for payments with Payfort Update SalesOrder by
	 * Payfort Authorized
	 *
	 * @param order
	 * @param paymentMethod
	 *
	 * IF store level payfort authorized flag is enabled i.e. one(1) then SalesOrder new attribute(isPayfortAuthorized)
	 * will be set and update as per payment method to either 0 or 1. "if payment method not in "payfort_fort_cc",
	 * "md_payfort", "md_payfort_cc_vault", "apple_pay" it will be set to zero(0) else it will be set to one(1)".
     * ELSE it will execute as BAU.
	 */
	public void updateSalesOrderIsPayfortAuthorized(SalesOrder order, String paymentMethod) {
		if (!OrderConstants.checkPaymentMethod(paymentMethod)) {
			order.setPayfortAuthorized(Integer.valueOf(Constants.ZERO));
			order.setAuthorizationCapture(Integer.valueOf(Constants.ZERO));
		}else {
			order.setPayfortAuthorized(Integer.valueOf(Constants.ONE));
			order.setAuthorizationCapture(Integer.valueOf(Constants.ZERO));

		}
	}

	public void saveUuidOfUserInOrder(SalesOrder order, String tokenHeader) {
		LOGGER.info("Inside uuid save in create order " + order.getIncrementId() + " tokenHeader " + tokenHeader);
		if (Objects.isNull(tokenHeader))
			return;
		
		String jwtToken = tokenHeader.substring(4);

		try {
            Claims body = Jwts.parser().setSigningKey(jwtsaltOldSecret).parseClaimsJws(jwtToken).getBody();
            LOGGER.info("jwtsaltOldSecret " + jwtsaltOldSecret + "uuid " + body.get("uuid"));
			if(null != body && null != body.get("uuid"))
				order.setUuid((String) body.get("uuid"));
			LOGGER.info("order uuid " + order.getUuid());

        } catch (Exception e) {
        	try {
        	LOGGER.info("Error in getting uuid from jwt token of user from old secret key " + e.getMessage());
        		
        	Claims body = Jwts.parser().setSigningKey(jwtsaltNewSecret).parseClaimsJws(jwtToken).getBody();
        	LOGGER.info("jwtsaltNewSecret " + jwtsaltNewSecret + "uuid " + body.get("uuid"));
			if(null != body && null != body.get("uuid"))
				order.setUuid((String) body.get("uuid"));
			LOGGER.info("order uuid " + order.getUuid());

        	} catch (Exception secondEx) {
        		LOGGER.info("Error in getting uuid from jwt token of user from new secret key " + secondEx.getMessage());
        	}
        }
	}

    public ShukranLedgerData createShukranLedgerData(SalesOrder order, BigDecimal points, BigDecimal pointsValueInCurrency, BigDecimal pointsValueInBaseCurrency, Stores storeData, boolean isRefund, String reason){
        ShukranLedgerData shukranLedgerData = new ShukranLedgerData();
        shukranLedgerData.setShukranProfileId(StringUtils.isNotBlank(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotEmpty(order.getSubSalesOrder().getCustomerProfileId()) ? order.getSubSalesOrder().getCustomerProfileId(): "");
        shukranLedgerData.setShukranCardNumber(StringUtils.isNotBlank(order.getSubSalesOrder().getShukranCardNumber()) && StringUtils.isNotEmpty(order.getSubSalesOrder().getShukranCardNumber()) ? order.getSubSalesOrder().getShukranCardNumber(): "");
        shukranLedgerData.setCustomerId(order.getCustomerId());
        shukranLedgerData.setOrderId(order.getEntityId().toString());
        shukranLedgerData.setOrderIncrementId(order.getIncrementId());
        shukranLedgerData.setReason(reason);
        List<Integer> storeIds= new ArrayList<>();
        List<Stores> stores = Constants.getStoresList();

        for(Stores store : stores) {
            if(store.getStoreCurrency().equals(storeData.getStoreCurrency())){
                storeIds.add(Integer.valueOf(store.getStoreId()));
            }
        }
        shukranLedgerData.setStoreId(storeIds);
        shukranLedgerData.setPoints(points);
        shukranLedgerData.setCashValueInCurrency(pointsValueInCurrency);
        shukranLedgerData.setCashValueInBaseCurrency(pointsValueInBaseCurrency);
        shukranLedgerData.setType(1);
        shukranLedgerData.setTypeDetail(isRefund? 3 : 2);
        shukranLedgerData.setStatus("1");
        ShukranLedgerOtherData shukranLedgerOtherData = getShukranLedgerOtherData(order);
        shukranLedgerData.setOtherDetail(shukranLedgerOtherData);
        return shukranLedgerData;
    }

    public ShukranLedgerData createShukranLedgerDataForSplitOrder(SplitSalesOrder order, BigDecimal points, BigDecimal pointsValueInCurrency, BigDecimal pointsValueInBaseCurrency, Stores storeData, boolean isRefund, String reason){
      ShukranLedgerData shukranLedgerData = new ShukranLedgerData();
      shukranLedgerData.setShukranProfileId(StringUtils.isNotBlank(order.getSplitSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getCustomerProfileId()) ? order.getSplitSubSalesOrder().getCustomerProfileId(): "");
      shukranLedgerData.setShukranCardNumber(StringUtils.isNotBlank(order.getSplitSubSalesOrder().getShukranCardNumber()) && StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getShukranCardNumber()) ? order.getSplitSubSalesOrder().getShukranCardNumber(): "");
      shukranLedgerData.setCustomerId(order.getCustomerId());
      shukranLedgerData.setOrderId(order.getEntityId().toString());
      shukranLedgerData.setOrderIncrementId(order.getIncrementId());
      shukranLedgerData.setReason(reason);
      List<Integer> storeIds= new ArrayList<>();
      List<Stores> stores = Constants.getStoresList();

      for(Stores store : stores) {
          if(store.getStoreCurrency().equals(storeData.getStoreCurrency())){
              storeIds.add(Integer.valueOf(store.getStoreId()));
          }
      }
      shukranLedgerData.setStoreId(storeIds);
      shukranLedgerData.setPoints(points);
      shukranLedgerData.setCashValueInCurrency(pointsValueInCurrency);
      shukranLedgerData.setCashValueInBaseCurrency(pointsValueInBaseCurrency);
      shukranLedgerData.setType(1);
      shukranLedgerData.setTypeDetail(isRefund? 3 : 2);
      shukranLedgerData.setStatus("1");
      ShukranLedgerOtherData shukranLedgerOtherData = getShukranLedgerOtherDataForSplit(order);
      shukranLedgerData.setOtherDetail(shukranLedgerOtherData);
      return shukranLedgerData;
  }

    public ShukranLedgerData createShukranLedgerDataForSplit(SplitSalesOrder order, BigDecimal points, BigDecimal pointsValueInCurrency, BigDecimal pointsValueInBaseCurrency, Stores storeData, boolean isRefund, String reason){
      ShukranLedgerData shukranLedgerData = new ShukranLedgerData();
      shukranLedgerData.setShukranProfileId(StringUtils.isNotBlank(order.getSplitSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getCustomerProfileId()) ? order.getSplitSubSalesOrder().getCustomerProfileId(): "");
      shukranLedgerData.setShukranCardNumber(StringUtils.isNotBlank(order.getSplitSubSalesOrder().getShukranCardNumber()) && StringUtils.isNotEmpty(order.getSplitSubSalesOrder().getShukranCardNumber()) ? order.getSplitSubSalesOrder().getShukranCardNumber(): "");
      shukranLedgerData.setCustomerId(order.getCustomerId());
      shukranLedgerData.setOrderId(order.getEntityId().toString());
      shukranLedgerData.setOrderIncrementId(order.getIncrementId());
      shukranLedgerData.setReason(reason);
      List<Integer> storeIds= new ArrayList<>();
      List<Stores> stores = Constants.getStoresList();

      for(Stores store : stores) {
          if(store.getStoreCurrency().equals(storeData.getStoreCurrency())){
              storeIds.add(Integer.valueOf(store.getStoreId()));
          }
      }
      shukranLedgerData.setStoreId(storeIds);
      shukranLedgerData.setPoints(points);
      shukranLedgerData.setCashValueInCurrency(pointsValueInCurrency);
      shukranLedgerData.setCashValueInBaseCurrency(pointsValueInBaseCurrency);
      shukranLedgerData.setType(1);
      shukranLedgerData.setTypeDetail(isRefund? 3 : 2);
      shukranLedgerData.setStatus("1");
      ShukranLedgerOtherData shukranLedgerOtherData = getShukranLedgerOtherDataForSplit(order);
      shukranLedgerData.setOtherDetail(shukranLedgerOtherData);
      return shukranLedgerData;
  }

    private static ShukranLedgerOtherData getShukranLedgerOtherData(SalesOrder order) {
        ShukranLedgerOtherData shukranLedgerOtherData = new ShukranLedgerOtherData();
        ShukranLedgerOtherDataJson shukranLedgerOtherDataJson = new ShukranLedgerOtherDataJson();
        shukranLedgerOtherDataJson.setOrderId(order != null && order.getEntityId() != null ? order.getEntityId() : 0);
        shukranLedgerOtherDataJson.setOrderIncId(order != null && StringUtils.isNotEmpty(order.getIncrementId()) && StringUtils.isNotBlank(order.getIncrementId()) ? order.getIncrementId() : "");
        shukranLedgerOtherDataJson.setOrderCustomerId(order != null && order.getCustomerId() != null ? order.getCustomerId() : 0);
        shukranLedgerOtherDataJson.setOrderStoreId(order != null && order.getStoreId() != null ? order.getStoreId() : 0);
        shukranLedgerOtherData.setOtherData(shukranLedgerOtherDataJson);
        return shukranLedgerOtherData;
    }

    public void publishPreferredPaymentIfValid(String paymentMethod, SalesOrder order) {
        if (paymentMethod == null || order == null) {
            return;
        }
        boolean useKafka = Boolean.TRUE.equals(Constants.orderCredentials.getKafkaForPreferredPaymentFeature());
        if (order.getCustomerId()!= null && order.getStoreId() != null) {
            Integer customerNew = order.getCustomerId();
            Integer storeId = order.getStoreId();
            LOGGER.info("Pushing for customerId: " + customerNew + " for payment method: " + paymentMethod);
            try {
                if (useKafka) {
                    // Kafka publishing
                    PreferredPaymentData data = new PreferredPaymentData(paymentMethod, customerNew, storeId);
                    kafkaService.publishPreferredPaymentToKafka(data);
                } else {
                    // Pub/Sub publishing
                    pubSubServiceImpl.publishPreferredPaymentMethodToPubSub(
                            preferredPaymentTopic, paymentMethod, customerNew, storeId);
                }
            } catch (Exception e) {
                LOGGER.info("Error in publishing Preferred Payment Method to PubSub. " + e.getMessage());
            }
        }
    }

    public void publishPreferredPaymentIfValidSplit(String paymentMethod, SplitSalesOrder splitSalesOrder) {
        if (paymentMethod == null || splitSalesOrder == null) {
            return;
        }
        boolean useKafka = Boolean.TRUE.equals(Constants.orderCredentials.getKafkaForPreferredPaymentFeature());
        if (splitSalesOrder.getCustomerId()!= null && splitSalesOrder.getStoreId() != null) {
            Integer customerNew = splitSalesOrder.getCustomerId();
            Integer storeId = splitSalesOrder.getStoreId();
            LOGGER.info("Pushing for customerId: " + customerNew + " for payment method: " + paymentMethod);
            try {
                if (useKafka) {
                    // Kafka publishing
                    PreferredPaymentData data = new PreferredPaymentData(paymentMethod, customerNew, storeId);
                    kafkaService.publishPreferredPaymentToKafka(data);
                } else {
                    // Pub/Sub publishing
                    pubSubServiceImpl.publishPreferredPaymentMethodToPubSub(
                            preferredPaymentTopic, paymentMethod, customerNew, storeId);
                }
            } catch (Exception e) {
                LOGGER.info("Error in publishing Preferred Payment Method to PubSub. " + e.getMessage());
            }
        }
    }


    private static ShukranLedgerOtherData getShukranLedgerOtherDataForSplit(SplitSalesOrder order) {
      ShukranLedgerOtherData shukranLedgerOtherData = new ShukranLedgerOtherData();
      ShukranLedgerOtherDataJson shukranLedgerOtherDataJson = new ShukranLedgerOtherDataJson();
      shukranLedgerOtherDataJson.setOrderId(order != null && order.getEntityId() != null ? order.getEntityId() : 0);
      shukranLedgerOtherDataJson.setOrderIncId(order != null && StringUtils.isNotEmpty(order.getIncrementId()) && StringUtils.isNotBlank(order.getIncrementId()) ? order.getIncrementId() : "");
      shukranLedgerOtherDataJson.setOrderCustomerId(order != null && order.getCustomerId() != null ? order.getCustomerId() : 0);
      shukranLedgerOtherDataJson.setOrderStoreId(order != null && order.getStoreId() != null ? order.getStoreId() : 0);
      shukranLedgerOtherData.setOtherData(shukranLedgerOtherDataJson);
      return shukranLedgerOtherData;
  }

    /**
     * @param productStatusRequest
     * @return
     */
    public ProductInventoryRes getInventoryInfoOfQuoteProduct(ProductStatusRequest productStatusRequest) {
        ProductInventoryRes productInventoryRes = new ProductInventoryRes();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.add(Constants.AUTH_BEARER_HEADER, getInternalAuthorizationOfRestAPI(internalHeaderBearerToken));

        HttpEntity<ProductStatusRequest> apiRequestBody = new HttpEntity<>(productStatusRequest,requestHeaders);
        String url = "";
        if(null != Constants.orderCredentials && null !=
                Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
            url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()+ "/api/inventory/storefront/atp";
        }
        LOGGER.info("getInventoryInfoOfQuoteProduct GET Inventory URL:" + url);
        try {
            LOGGER.info("getInventoryInfoOfQuoteProduct inventory request body:"+mapper.writeValueAsString(apiRequestBody.getBody()));
            ResponseEntity<ProductInventoryRes> apiResponseBody = restTemplate.exchange(
                    url, HttpMethod.POST, apiRequestBody,
                    ProductInventoryRes.class);
            LOGGER.info("getInventoryInfoOfQuoteProduct inventory response body:"+mapper.writeValueAsString(apiResponseBody.getBody()));
            if (apiResponseBody.getStatusCode() == HttpStatus.OK) {
                productInventoryRes = apiResponseBody.getBody();
            }else{
                LOGGER.error("getInventoryInfoOfQuoteProduct Error  from InventoryFetch:" + productInventoryRes.getStatusMsg());
            }
        } catch (Exception e) {
            LOGGER.error("getInventoryInfoOfQuoteProduct Exception occurred:" + e.getMessage());
        }

        return productInventoryRes;
    }

    public String getInternalAuthorizationOfRestAPI(String authToken) {

        String token = null;

        if (org.apache.commons.lang.StringUtils.isNotEmpty(authToken) &&  (authToken.contains(","))) {

            List<String> authTokenList = Arrays.asList(authToken.split(","));

            if (CollectionUtils.isNotEmpty(authTokenList)) {

                token = authTokenList.get(0);
            }

        }

        return token;
    }

    public void updateProductDetails(SalesOrder order) {
        try{
            Map<String, ProductResponseBody> productsFromMulin =
                    mulinHelper.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
            for (Map.Entry<String,ProductResponseBody> entry : productsFromMulin.entrySet()) {
                LOGGER.info("Updating DGG (product attributes)  for sku "+ entry.getKey());
                for (SalesOrderItem orderItem: order.getSalesOrderItem()) {
                    if (orderItem.getParentOrderItem() == null && orderItem.getProductId().equalsIgnoreCase(entry.getKey())) {
                        // update product attributes
                        if (entry.getValue().getProductAttributes() != null) {
                            orderItem.setProductAttributes(mapper.writeValueAsString(entry.getValue().getProductAttributes()));
                        }
                    }
                }
            }
            order.getSalesOrderItem().stream().filter(item -> item.getParentOrderItem() != null).forEach(item -> {
                item.setProductAttributes(item.getParentOrderItem().getProductAttributes());
            });

            LOGGER.info("Product attributes updated successfully in order item for order: " + order.getIncrementId());
        } catch (Exception e) {
            LOGGER.error("Exception occurred while updating product attributes in order item: " + e.getMessage());
        }
    }

    private void updateProductAttributesFromQuote(CatalogProductEntityForQuoteDTO product, SalesOrderItem item){
       try{
            Map<String,Object> productAttributes = new HashMap<>();
            productAttributes.put("short_description", null!=product.getShortDescription()?product.getShortDescription():"");
            productAttributes.put("is_dangerous_product", product.getIsDangerousProduct());
            item.setProductAttributes(mapper.writeValueAsString(productAttributes));
        }catch (Exception e){
            LOGGER.error("[updateProductAttributesFromQuote] Exception occurred while updating product attributes in order item: " + e.getMessage());
        }
    }
}
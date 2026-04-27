package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import org.styli.services.order.pojo.order.OTSOrderRequest;
import org.styli.services.order.pojo.order.SkuItem;
import org.styli.services.order.pojo.order.StatusMessage;
import org.styli.services.order.pojo.request.*;
import org.styli.services.order.pojo.response.CustomerUpdateProfileResponse;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.WarehouseResponse;
import org.styli.services.order.pojo.response.WarehouseResponseWrapper;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.service.AddressMapperHelper;
import org.styli.services.order.utility.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Umesh, 26/09/2020
 * @project product-service
 */

@Component
public class OrderHelperV3 {

  private static final Log LOGGER = LogFactory.getLog(OrderHelperV3.class);

    private static final String V1_REST_ORDERSMS = "/v1/rest/ordersms";

  @Autowired
  SalesOrderGridRepository salesOrderGridRepository;


  @Autowired
  SalesOrderRepository salesOrderRepository;

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
  StatusChaneHistoryRepository statusChaneHistoryRepository;

  @Value("${auth.internal.header.bearer.token}")
  private String internalHeaderBearerToken;

  @Autowired
  private MulinHelper mulinHelper;
  
  private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private static final ObjectMapper mapper = new ObjectMapper();

  

  public SalesOrder createOrderObjectToPersistV3(QuoteV7DTO quoteV7,
                                               QuoteDTO quote,
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
      }  else if (null != order.getStatus() && null!=paymentMethod && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())){
        order.setState(OrderConstants.NEW_ORDER_STATE);
        order.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
        LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in createOrderObjectToPersistV3 - else-if");
    } else {
      order.setState(OrderConstants.NEW_ORDER_STATE);
      order.setStatus(OrderConstants.PROCESSING_ORDER_STATUS);
      LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in createOrderObjectToPersistV3 - else");
      order.setExtOrderId("0");
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
    order.setShippingAmount(getLocalShippingAmount(quoteV7));
    order.setBaseShippingAmount(getBaseValueDecimal(getLocalShippingAmount(quoteV7), store.getCurrencyConversionRate()));
    order.setGlobalShippingAmount(getGlobalShippingAmount(quoteV7));
    order.setBaseGlobalShippingAmount(getBaseValueDecimal(getGlobalShippingAmount(quoteV7), store.getCurrencyConversionRate()));
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
    //SFP-1104 COD fee changes for Order service
    // COD charges
    order.setCashOnDeliveryFee(getLocalCodCharges(quoteV7));
    order.setBaseCashOnDeliveryFee(getBaseValueDecimal(getLocalCodCharges(quoteV7), store.getCurrencyConversionRate()));
    order.setGlobalCashOnDeliveryFee(getGlobalCodCharges(quoteV7));
    order.setBaseGlobalCashOnDeliveryFee(getBaseValueDecimal(getGlobalCodCharges(quoteV7), store.getCurrencyConversionRate()));

    order.setAmstorecreditAmount(storeCreditAmount);
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
                setEstmateDateAndFastDeliveryV3(quoteV7, order);
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

  private BigDecimal getShippingAmount(QuoteDTO quoteObject) {
    return quoteObject.getShippingAmount() == null ? new BigDecimal(0)
        : new BigDecimal(quoteObject.getShippingAmount());
  }

  private BigDecimal getLocalShippingAmount(QuoteV7DTO quoteObject) {
      if (quoteObject.getShippingAmount() == null) {
          return BigDecimal.ZERO;
      }

      return Optional.ofNullable(quoteObject.getShippingAmount()).orElse(java.util.Collections.emptyList()).stream()
              .filter(entry ->  null != entry.getShipmentMode() && ("Local".equalsIgnoreCase(entry.getShipmentMode())
                      || "Express".equalsIgnoreCase(entry.getShipmentMode())))
              .map(entry -> entry.getRemainshippingAmount())
              .findFirst()
              .orElse(BigDecimal.ZERO);
  }

    private BigDecimal getGlobalShippingAmount(QuoteV7DTO quoteObject) {
        if (quoteObject.getShippingAmount() == null) {
            return BigDecimal.ZERO;
        }

        return Optional.ofNullable(quoteObject.getShippingAmount()).orElse(java.util.Collections.emptyList()).stream()
                .filter(entry ->  null != entry.getShipmentMode() && "Global".equalsIgnoreCase(entry.getShipmentMode()))
                .map(entry -> entry.getRemainshippingAmount())
                .findFirst()
                .orElse(BigDecimal.ZERO);
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




    /**
     * This will extract order items from Quote and will prepare the sales order items.
     * @param quote
     * @param order
     * @param store
     */
    public void extractOrderItemsFromQuotev3(QuoteV7DTO quote, SalesOrder order, Stores store) {
        BigDecimal totalDiscountTaxCompensationAmount = new BigDecimal(0);


        if (null != order.getSubSalesOrder()
                && null == order.getSubSalesOrder().getRetryPayment()) {
            for (ProductEntityForQuoteV7DTO product : quote.getProducts()) {
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
                // SPLIT SALES ORDER CHANGES
                item.setSellerId(product.getSellerId());
                item.setSellerName(product.getSellerName());
                item.setWarehouseLocationId(product.getWarehouseId());
                item.setShipmentType(product.getFulfillmentMode());
                item.setFulfillmentModuleType(product.getFulfillmentType());
                item.setSellerCountryLocation(product.getCountryCode());
                item.setDeliveryType(product.getDeliveryType());
                item.setFirstMileFc(product.getFirstMileLocationId() != null ? product.getFirstMileLocationId() : "");
                item.setMidMileFc(product.getMidMileLocationId() != null ? product.getMidMileLocationId() : "");
                item.setLastMileFc(product.getLastmileWarehouseId() != null ? product.getLastmileWarehouseId() : "");
                item.setFirstMileFcName(product.getFirstMileLocationName() != null ? product.getFirstMileLocationName() : "");
                item.setMidMileFcName(product.getMidMileLocationName() != null ? product.getMidMileLocationName() : "");
                item.setLastMileFcName(product.getLastmileWarehouseName() != null ? product.getLastmileWarehouseName() : "");
                try {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    if (product.getEstimatedDate() != null) {
                        Date estimated = formatter.parse(product.getEstimatedDate());
                        item.setEstimatedDeliveryDate(new Timestamp(estimated.getTime()));
                    }
                    if (product.getMinEstimatedDate() != null) {
                        Date min = formatter.parse(product.getMinEstimatedDate());
                        item.setMinEstimatedDate(new Timestamp(min.getTime()));
                    }
                    if (product.getMaxEstimatedDate() != null) {
                        Date max = formatter.parse(product.getMaxEstimatedDate());
                        item.setMaxEstimatedDate(new Timestamp(max.getTime()));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error parsing estimated delivery date for product: " + product.getSku(), e);
                }

                item.setStoreId(Integer.parseInt(store.getStoreId()));
                item.setCreatedAt((new Timestamp(new Date().getTime())));
                item.setUpdatedAt((new Timestamp(new Date().getTime())));
                item.setProductId(product.getParentProductId());
                item.setProductType(UtilityConstant.PRODCUT_TYPE_ID_CONNF);
                item.setProductOptions(parentItemOptions);
                updateProductAttributesFromQuoteV3(product, item);
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
                // SPLIT SALES ORDER CHANGES
                childItem.setSellerId(product.getSellerId());
                childItem.setSellerName(product.getSellerName());
                childItem.setWarehouseLocationId(product.getWarehouseId());
                childItem.setShipmentType(product.getFulfillmentMode());
                childItem.setFulfillmentModuleType(product.getFulfillmentType());
                childItem.setSellerCountryLocation(product.getCountryCode());
                childItem.setDeliveryType(product.getDeliveryType());
                childItem.setFirstMileFc(product.getFirstMileLocationId() != null ? product.getFirstMileLocationId() : "");
                childItem.setMidMileFc(product.getMidMileLocationId() != null ? product.getMidMileLocationId() : "");
                childItem.setLastMileFc(product.getLastmileWarehouseId() != null ? product.getLastmileWarehouseId() : "");
                childItem.setFirstMileFcName(product.getFirstMileLocationName() != null ? product.getFirstMileLocationName() : "");
                childItem.setMidMileFcName(product.getMidMileLocationName() != null ? product.getMidMileLocationName() : "");
                childItem.setLastMileFcName(product.getLastmileWarehouseName() != null ? product.getLastmileWarehouseName() : "");
                try {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    if (product.getEstimatedDate() != null) {
                        Date estimated = formatter.parse(product.getEstimatedDate());
                        childItem.setEstimatedDeliveryDate(new Timestamp(estimated.getTime()));
                    }
                    if (product.getMinEstimatedDate() != null) {
                        Date min = formatter.parse(product.getMinEstimatedDate());
                        childItem.setMinEstimatedDate(new Timestamp(min.getTime()));
                    }
                    if (product.getMaxEstimatedDate() != null) {
                        Date max = formatter.parse(product.getMaxEstimatedDate());
                        childItem.setMaxEstimatedDate(new Timestamp(max.getTime()));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error parsing estimated delivery date for product: " + product.getSku(), e);
                }
                childItem.setStoreId(Integer.parseInt(store.getStoreId()));
                childItem.setCreatedAt((new Timestamp(new Date().getTime())));
                childItem.setUpdatedAt((new Timestamp(new Date().getTime())));
                childItem.setProductId(product.getProductId());
                childItem.setProductType(UtilityConstant.PRODCUT_TYPE_ID_SIMEPLE);
                childItem.setProductOptions(parentItemOptions);
                updateProductAttributesFromQuoteV3(product, childItem);
                childItem.setWeight(BigDecimal.ONE);
                childItem.setVirtual(0);
                childItem.setSku(product.getSku());
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
                childItem.setPoPrice(StringUtils.isNotBlank(product.getPoPrice()) ? new BigDecimal(product.getPoPrice()) : BigDecimal.ZERO);
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




  private BigDecimal getBaseValueDecimal(BigDecimal value, BigDecimal currencyConversionRate) {
    return value.multiply(currencyConversionRate).setScale(4, RoundingMode.HALF_UP);
  }

  	/**
  	 * Update Estimated Date and Fast Delivery if eligible
  	 * @param quote
  	 * @param order
  	 */
	public void setEstmateDateAndFastDeliveryV3(QuoteV7DTO quote, SalesOrder order) {
        boolean isFasterDeliveryEligible = setEstmateDateV3(quote, order);
		SubSalesOrder subSalesOrder = order.getSubSalesOrder();
        if (isFasterDeliveryEligible) {
			subSalesOrder.setFasterDelivery(1);
		} else {
			subSalesOrder.setFasterDelivery(0);
		}
		order.setSubSalesOrder(subSalesOrder);
	}
	
	public boolean setEstmateDateV3(QuoteV7DTO quote, SalesOrder order) {
        boolean fastDeliveryEligible = false;
        String incrementId = order.getIncrementId();
		try {
			AddressObject shippingAddress = quote.getShippingAddress();
            WarehouseResponseWrapper warehouseResponseWrapper = getAdrsmprCityDetailsV3(quote,shippingAddress.getCityMapper().getId(), shippingAddress.getRegionId(),
					shippingAddress.getCountryId(), incrementId);
			if (Objects.nonNull(warehouseResponseWrapper) && Objects.nonNull(warehouseResponseWrapper.getEstimated_date())) {
                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                Date date = formatter.parse(warehouseResponseWrapper.getEstimated_date());
                Timestamp timeStampDate = new Timestamp(date.getTime());
                order.setEstimatedDeliveryTime(timeStampDate);
                fastDeliveryEligible = Optional.ofNullable(warehouseResponseWrapper)
                        .map(WarehouseResponseWrapper::getResponse)
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.size() == 1 ? list.get(0) : null)   // only accept exactly one response
                        .map(WarehouseResponse::getData)
                        .filter(Objects::nonNull)
                        .map(data -> data.isFast_delivery() && data.isFast_delivery_eligible())
                        .orElse(false);

                LOGGER.info("Evaluated estimated date set for Order : " + incrementId + " Date : " + timeStampDate);
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
			return fastDeliveryEligible;
		} catch (ParseException e) {
			LOGGER.error("Error in setting ETD for order : " + incrementId + " Error ", e);
		}
		return false;
	}


	public WarehouseResponseWrapper getAdrsmprCityDetailsV3(QuoteV7DTO quote,String city, String regionId, String country, String incrementId) {
		LOGGER.info("Adrsmpr invoked for order : " + incrementId);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
        CitySearchAddressMapperRequestV2 payload = new CitySearchAddressMapperRequestV2();
        Set<String> warehouse_id = new HashSet<>();
        for (ProductEntityForQuoteV7DTO product : quote.getProducts()) {
            warehouse_id.add(product.getWarehouseId());
        }
		payload.setCity_id(city);
		payload.setWarehouse_ids(warehouse_id);

		HttpEntity<CitySearchAddressMapperRequestV2> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = "{url}/api/sla/search-city";
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("url", addressMapperUrl);

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
		URI uri = builder.buildAndExpand(parameters).toUri();
		LOGGER.info(
				"Address Mapper Coupon URL:" + uri + " For Order : " + incrementId + " Request Body : " + requestBody);
		try {
			ResponseEntity<WarehouseResponseWrapper> response = restTemplate.exchange(uri, HttpMethod.POST, requestBody,
                    WarehouseResponseWrapper.class);
			if (response.getStatusCode() == HttpStatus.OK) {
                WarehouseResponseWrapper body = response.getBody();
				if (body != null && body.getStatusCode().equalsIgnoreCase("200") && body.getResponse() != null) {
					LOGGER.info("AddressMapper. For Order " + incrementId + " Response: " + body);
					return body;
				}
			}
		} catch (RestClientException e) {
			LOGGER.error("Exception during adrsmpr call for Order : " + incrementId + " . Error : " + e.getMessage());

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

    public void createOrderGridV3(QuoteV7DTO quoteV7,QuoteDTO quote, SalesOrder order, String paymentMethod, AddressObject shippingAddress,
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
            LOGGER.info("Order status :"+OrderConstants.PROCESSING_ORDER_STATUS + " in createOrderGridV3");
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

        orderGrid.setShippingAndHandling(getLocalShippingAmount(quoteV7).add(getGlobalShippingAmount(quoteV7)));
        orderGrid.setPaymentMethod(paymentMethod);
        orderGrid.setSignifydGuaranteeStatus(null);

        orderGrid.setSource(source);
        orderGrid.setAppVersion(appVersion);

        // is split order
        orderGrid.setIsSplitOrder(1);
        salesOrderGridRepository.saveAndFlush(orderGrid);

    }

    /**
     * @param order
     */
    public void blockInventoryV3(SalesOrder order) {

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
        String url = "";
        InventoryRequestV3 payload = setBlockInventoryRequest(order);

        payload.setIncrementId(order.getIncrementId());
        try {

            HttpEntity<InventoryRequestV3> requestBody = new HttpEntity<>(payload, requestHeaders);

            if (null != Constants.orderCredentials
                    && null != Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
                url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()
                        + "/api/inventory/storefront/block";

            }

            LOGGER.info("inventory block request url:" + url);
            LOGGER.info("inventory block request body:" + mapper.writeValueAsString(requestBody));

            ResponseEntity<InventoryBlockResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
                    InventoryBlockResponse.class);

            LOGGER.info("block inventory response body" + mapper.writeValueAsString(response.getBody()));
            if (response.getStatusCode() == HttpStatus.OK) {

                LOGGER.info("inventory block for:" + mapper.writeValueAsString(requestBody));

            }

        } catch (RestClientException | JsonProcessingException e) {

            LOGGER.error("exception occoured during block inventory:" + e.getMessage());
        }
    }

    /**
     * @param order
     * @return
     */
    private InventoryRequestV3 setBlockInventoryRequest(SalesOrder order) {

        InventoryRequestV3 request = new InventoryRequestV3();

        List<BlockInventoryV3> inventories = new ArrayList<>();
        for (SalesOrderItem salesItem : order.getSalesOrderItem()) {

            if (!salesItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {

                BlockInventoryV3 inventory = new BlockInventoryV3();

                inventory.setChannelSkuCode(salesItem.getSku());
                // Set warehouse ID
                if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
                    inventory.setWarehouseId(salesItem.getWarehouseLocationId());
                } else if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getWarehouseLocationId()) {
                    inventory.setWarehouseId(order.getSubSalesOrder().getWarehouseLocationId().toString());
                }
                if (null != salesItem.getQtyOrdered()) {
                    inventory.setQuantity(salesItem.getQtyOrdered().toString());

                    inventories.add(inventory);
                }

                request.setStoreId(order.getStoreId());

            }
        }
        request.setInventories(inventories);

        return request;

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

    /**
     * @param splitSalesOrder
     */
    public void releaseInventoryQtyV3(SplitSalesOrder splitSalesOrder, Map<String, BigDecimal> skuMapList, boolean updateQty,
                                    String releaseType) {

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
        String url = "";
        InventoryRequest payload = null;
        LOGGER.info("release before retry payment:" + splitSalesOrder.getRetryPayment() + "split order id:"+splitSalesOrder.getIncrementId());

        try {
            if (MapUtils.isNotEmpty(skuMapList)) {

                payload = setCancelledInventoryRequest(splitSalesOrder, skuMapList);
            } else {

                payload = setInventoryRequest(splitSalesOrder);

            }
            payload.setIncrementId(splitSalesOrder.getIncrementId());
            payload.setUpdateQty(updateQty);
            payload.setReleaseType(releaseType);
            // Don't send warehouseId, as inventory is blocked based on sku , sku level warehouseid and storeId
           /* if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId()) {
                payload.setWarehouseId(splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId().toString());
            }*/
            HttpEntity<InventoryRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
            if (null != Constants.orderCredentials
                    && null != Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()) {
                url = Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()
                        + "/api/inventory/storefront/release";

            }

            LOGGER.info("release inventory URl:" + url);
            LOGGER.info(" Inventory release request body" + mapper.writeValueAsString(requestBody));

            ResponseEntity<InventoryBlockResponse> response = restTemplate.exchange(url, HttpMethod.POST,
                    requestBody, InventoryBlockResponse.class);

            LOGGER.info("response inventory release Body" + mapper.writeValueAsString(response.getBody()));
            if (response.getStatusCode() == HttpStatus.OK) {

                LOGGER.info("inventory release for:" + mapper.writeValueAsString(requestBody));

            }

        } catch (RestClientException | JsonProcessingException e) {

            LOGGER.error("exception occoured during release inventory:" + e.getMessage());
        }

    }

    /**
     * @param splitSalesOrder
     */
    public void updateStatusHistoryV3(SplitSalesOrder splitSalesOrder, boolean isOrder, boolean isProcessing, boolean isPendingPayment,
                                    boolean isCancel, boolean isPacked) {

        try {
            StatusChangeHistory statusChangeHistory = null;

            statusChangeHistory = statusChaneHistoryRepository.findBySplitOrderIncrementId(splitSalesOrder.getIncrementId());

            if (null == statusChangeHistory) {
                statusChangeHistory = new StatusChangeHistory();
                statusChangeHistory.setOrderId(splitSalesOrder.getSalesOrder().getEntityId().toString());
                statusChangeHistory.setOrderIncrementId(splitSalesOrder.getSalesOrder().getIncrementId());
                statusChangeHistory.setSplitOrderId(splitSalesOrder.getEntityId());
                statusChangeHistory.setSplitOrderIncrementId(splitSalesOrder.getIncrementId());
            }

            if (null != statusChangeHistory) {

                if (isOrder) {

                    statusChangeHistory.setCreateAt(new Timestamp(new Date().getTime()));
                    statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
                }
                if (isProcessing) {
                    statusChangeHistory.setProcessingDate(new Timestamp(new Date().getTime()));
                    statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

                }
                if (isPendingPayment) {
                    statusChangeHistory.setPendingPaymentDate(new Timestamp(new Date().getTime()));
                    statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

                }
                if (isCancel) {
                    statusChangeHistory.setCancelDate(new Timestamp(new Date().getTime()));
                    statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));

                }
                if (isPacked) {
                    statusChangeHistory.setPackedDate(new Timestamp(new Date().getTime()));
                    statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
                }
                statusChaneHistoryRepository.saveAndFlush(statusChangeHistory);

            }
        }catch(Exception ex) {

            LOGGER.error("exception occoured during update in history:"+ex.getMessage());
        }

    }

    /**
     * @param splitSalesOrder
     * @param mapSkuList
     * @return
     */
    private InventoryRequest setCancelledInventoryRequest(SplitSalesOrder splitSalesOrder, Map<String, BigDecimal> mapSkuList) {

        InventoryRequest request = new InventoryRequest();

        List<BlockInventory> inventories = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> mapEntrySet : mapSkuList.entrySet()) {

            BlockInventory inventory = new BlockInventory();

            inventory.setChannelSkuCode(mapEntrySet.getKey());
            inventory.setQuantity(mapEntrySet.getValue().toString());
            String warehouseId = splitSalesOrder.getSplitSalesOrderItems().stream()
                    .filter(e ->!e.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
                    .filter(e -> e.getSku() != null && e.getSku().equals(mapEntrySet.getKey()))
                    .map(SplitSalesOrderItem::getWarehouseLocationId)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .findFirst()
                    .orElseGet(() -> {
                        if (splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId() != null) {
                            return splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId().toString();
                        } else {
                            return null;
                        }
                    });
            inventory.setWarehouseId(warehouseId);
            inventories.add(inventory);

        }
        request.setStoreId(splitSalesOrder.getStoreId());
        request.setInventories(inventories);
        request.setReleaseType("cancel");
        return request;

    }

    /**
     * @param splitSalesOrder
     * @return
     */
    private InventoryRequest setInventoryRequest(SplitSalesOrder splitSalesOrder) {

        InventoryRequest request = new InventoryRequest();

        List<BlockInventory> inventories = new ArrayList<>();
        for (SplitSalesOrderItem item : splitSalesOrder.getSplitSalesOrderItems()) {
            SalesOrderItem salesItem = item.getSalesOrderItem();
            if (!(salesItem.getProductType().equals(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
                    && null != salesItem.getQtyCanceled() && null != salesItem.getQtyOrdered()) {

                BlockInventory inventory = new BlockInventory();

                inventory.setChannelSkuCode(salesItem.getSku());
                if (null != salesItem.getQtyCanceled()
                        && salesItem.getQtyCanceled().compareTo(salesItem.getQtyOrdered()) != 0) {

                    BigDecimal qytCancelled = salesItem.getQtyCanceled();
                    BigDecimal qtyOrdered = salesItem.getQtyOrdered();
                    BigDecimal actualQty = qtyOrdered.subtract(qytCancelled);
                    inventory.setQuantity(actualQty.toString());

                } else if (null != salesItem.getQtyOrdered()) {
                    inventory.setQuantity(salesItem.getQtyOrdered().toString());
                }
                //set warehouseId based on sku level warehouseId
                if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
                    inventory.setWarehouseId(salesItem.getWarehouseLocationId());
                } else if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId()) {
                    inventory.setWarehouseId(splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId().toString());
                }
                inventories.add(inventory);

            } else if (salesItem.getQtyCanceled() == null) {

                BlockInventory inventory = new BlockInventory();

                inventory.setChannelSkuCode(salesItem.getSku());

                inventory.setQuantity(salesItem.getQtyOrdered().toString());
                //set warehouseId based on sku level warehouseId
                if (StringUtils.isNotBlank(salesItem.getWarehouseLocationId())) {
                    inventory.setWarehouseId(salesItem.getWarehouseLocationId());
                } else if (null != splitSalesOrder.getSplitSubSalesOrder() && null != splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId()) {
                    inventory.setWarehouseId(splitSalesOrder.getSplitSubSalesOrder().getWarehouseLocationId().toString());
                }
                inventories.add(inventory);
            }
        }
        request.setStoreId(splitSalesOrder.getStoreId());
        request.setInventories(inventories);

        return request;

    }

    public void createOrderItemsV3(QuoteV7DTO quote, SalesOrder order, Stores store , boolean isRetryPayment) {
        if (!isRetryPayment) {
            extractOrderItemsFromQuotev3(quote, order, store);
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
            LOGGER.info("Order status before updating"+order.getIncrementId());
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

    public OTSOrderRequest buildOTSPayload(SalesOrder order, QuoteV7DTO quoteV7DTO) {
        OTSOrderRequest otsOrderRequest = new OTSOrderRequest();
        otsOrderRequest.setOp("create");
        otsOrderRequest.setParentOrderId(order.getEntityId());
        otsOrderRequest.setIncrementId(order.getIncrementId());
        otsOrderRequest.setCustomerId(order.getCustomerId());
        otsOrderRequest.setQuoteId(quoteV7DTO.getQuoteId());
        otsOrderRequest.setCustomerEmail(order.getCustomerEmail());
        List<StatusMessage> statuses = new ArrayList<>();
        Optional<String> paymentMethod = order.getSalesOrderPayment().stream()
                .map(SalesOrderPayment::getMethod).findFirst();

        statuses.add(new StatusMessage(
                "1.0",
                "Order Created",
                OffsetDateTime.now().toString()
        ));

        String currentTime = OffsetDateTime.now().toString();
        switch (order.getStatus().toLowerCase()) {
            case OrderConstants.PAYMENT_METHOD_COD ->
                    statuses.add(new StatusMessage("2.0", "Order placed successfully with COD", currentTime));
            case OrderConstants.PROCESSING_ORDER_STATUS ->
                    statuses.add(new StatusMessage("3.0", "Processing", currentTime));
            case OrderConstants.PENDING_PAYMENT_ORDER_STATUS ->
                    statuses.add(new StatusMessage("2.0", "Pending Payment", currentTime));
            case OrderConstants.FAILED_ORDER_STATUS ->
                    statuses.add(new StatusMessage("3.0", "Payment Failed", currentTime));
            default -> statuses.add(new StatusMessage("3.0", "Processing", currentTime));
        }

        Collection<SalesOrderItem> items = Optional.ofNullable(order)
                .map(SalesOrder::getSalesOrderItem)
                .map(col -> (Collection<SalesOrderItem>) col)
                .orElse(Collections.emptyList());
        List<SkuItem> skus = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE
                        .equalsIgnoreCase(item.getProductType()))
                .map(it -> {
                    SkuItem s = new SkuItem();
                    s.setSku(it.getSku());
                    s.setWarehosueId(it.getWarehouseLocationId());
                    s.setShipementMode("Local".equalsIgnoreCase(it.getShipmentType()) ? "Express" : it.getShipmentType());
                    s.setSellerId(it.getSellerId());
                    s.setSellername(it.getSellerName());
                    return s;
                }).collect(Collectors.toList());
        otsOrderRequest.setSkus(skus);
        otsOrderRequest.setStatusMessage(statuses);

        return otsOrderRequest;
    }

    public CustomerUpdateProfileResponse sendSplitCancelOrderSmsAndEMail(SplitSalesOrder splitSalesOrder, String totalCodCancelledAmount) {

        CustomerUpdateProfileResponse responseBody = new CustomerUpdateProfileResponse();
        String paymentMethod = null;
        if (CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesOrderPayments())) {
            for (SplitSalesOrderPayment payment : splitSalesOrder.getSplitSalesOrderPayments()) {
                paymentMethod = payment.getMethod();
            }
        }
        boolean isFullyCancellation = false;

        BigDecimal sumOrderedQty = splitSalesOrder.getSplitSalesOrderItems().stream()
                .filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
                .map(x -> x.getQtyOrdered()).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumOrderedCancelled = splitSalesOrder.getSplitSalesOrderItems().stream()
                .filter(e -> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
                .map(x -> x.getQtyCanceled()).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumOrderedQty.intValue() == sumOrderedCancelled.intValue()) {

            isFullyCancellation = true;
        }

        boolean isPrepaidOrder = getIsPrepidOrder(paymentMethod);
        String smsTemplate = getSmsTemplateName(isFullyCancellation, isPrepaidOrder);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

        try {

            RequestBody payload = new RequestBody();

            payload.setIncrementId(splitSalesOrder.getIncrementId());
            payload.setTemplate(smsTemplate);
            payload.setType(Constants.ORDER_ENTITY);
            if (null != totalCodCancelledAmount) {
                payload.setCodPartialCancelAmount(totalCodCancelledAmount);

            }
            String url = "";

            HttpEntity<RequestBody> requestBody = new HttpEntity<>(payload, requestHeaders);

            if (null != Constants.orderCredentials
                    && null != Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()) {
                url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + V1_REST_ORDERSMS;

            }

            LOGGER.info("URL SMS:" + url);

            LOGGER.info(" SMS request body" + mapper.writeValueAsString(requestBody));

            ResponseEntity<CustomerUpdateProfileResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, CustomerUpdateProfileResponse.class);

            LOGGER.info("response send sms Body" + mapper.writeValueAsString(response.getBody()));
            if (response.getStatusCode() == HttpStatus.OK) {

                LOGGER.info("sms cancel sent for:" + mapper.writeValueAsString(requestBody));

                responseBody = response.getBody();

            }

        } catch (RestClientException | JsonProcessingException e) {

            LOGGER.error("exception occoured during send cancel sms:" + e.getMessage());
            return responseBody;
        }

        return responseBody;
    }

    private Boolean getIsPrepidOrder(String method) {
        Boolean isPrepaidOrder = true;

        if (StringUtils.isNotBlank(method) && method.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {
            isPrepaidOrder = false;
        }

        return isPrepaidOrder;
    }

    private String getSmsTemplateName(Boolean isFullyCancellation, Boolean isPrepaidOrder) {

        String smsTemplate = null;
        if (isPrepaidOrder && isFullyCancellation) {

            smsTemplate = OrderConstants.SMS_TEMPLATE_PREPAID_FULLY_UNFULFILMENT;
        } else if (isPrepaidOrder && !isFullyCancellation) {

            smsTemplate = OrderConstants.SMS_TEMPLATE_PREPAID_PARTIAL_UNFULFILMENT;

        } else if (!isPrepaidOrder && isFullyCancellation) {

            smsTemplate = OrderConstants.SMS_TEMPLATE_COD_FULLY_UNFULFILMENT;

        } else if (!isPrepaidOrder && !isFullyCancellation) {

            smsTemplate = OrderConstants.SMS_TEMPLATE_COD_PARTIAL_UNFULFILMENT;
        }
        return smsTemplate;
    }

    public void updateV3OrderProductDetails(SalesOrder order) {
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

    private void updateProductAttributesFromQuoteV3(ProductEntityForQuoteV7DTO product, SalesOrderItem item){
        try{
            Map<String,Object> productAttributes = new HashMap<>();
            productAttributes.put("short_description", null!=product.getShortDescription()?product.getShortDescription():"");
            productAttributes.put("is_dangerous_product", product.getIsDangerousProduct());
            item.setProductAttributes(mapper.writeValueAsString(productAttributes));
        }catch (Exception e){
            LOGGER.error("[updateProductAttributesFromQuoteV3] Exception occurred while updating product attributes in order item: " + e.getMessage());
        }
    }

    private BigDecimal getLocalCodCharges(QuoteV7DTO quoteObject) {
        if (quoteObject.getShippingAmount() == null) {
            if (StringUtils.isBlank(quoteObject.getCodCharges())) {
                return BigDecimal.ZERO;
            }
            try {
                return new BigDecimal(quoteObject.getCodCharges());
            } catch (NumberFormatException e) {
                LOGGER.warn("Could not parse COD charges string: '" + quoteObject.getCodCharges() + "'", e);
                return BigDecimal.ZERO;
            }
        }

        return quoteObject.getShippingAmount().stream()
                .filter(entry ->  entry.getShipmentMode() != null && ("Local".equalsIgnoreCase(entry.getShipmentMode())
                        || "Express".equalsIgnoreCase(entry.getShipmentMode())))
                .map(entry -> {
                    if (StringUtils.isBlank(entry.getCodCharges())) {
                        return BigDecimal.ZERO;
                    }
                    try {
                        return new BigDecimal(entry.getCodCharges());
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Could not parse COD charges string: '" + entry.getCodCharges() + "'", e);
                        return BigDecimal.ZERO;
                    }
                })
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getGlobalCodCharges(QuoteV7DTO quoteObject) {
        if (quoteObject.getShippingAmount() == null) {
            return BigDecimal.ZERO;
        }

        return quoteObject.getShippingAmount().stream()
                .filter(entry ->  entry.getShipmentMode() != null && "Global".equalsIgnoreCase(entry.getShipmentMode()))
                .map(entry -> {
                    if (StringUtils.isBlank(entry.getCodCharges())) {
                        return BigDecimal.ZERO;
                    }
                    try {
                        return new BigDecimal(entry.getCodCharges());
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Could not parse COD charges string: '" + entry.getCodCharges() + "'", e);
                        return BigDecimal.ZERO;
                    }
                })
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
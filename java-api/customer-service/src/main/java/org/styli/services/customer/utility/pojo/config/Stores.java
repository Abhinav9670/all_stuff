package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stores implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -196389317581347746L;
	private String storeId;
	private String storeCode;
	private String storeLanguage;
	private String storeCurrency;
	private int showAddLowValueAmount;
	private BigDecimal shipmentChargesThreshold;
	private BigDecimal shipmentCharges;
	private BigDecimal codCharges;
	private BigDecimal taxPercentage;
	private int websiteId;
	private String websiteIdentifier;
	private String storeName;
	private String websiteCode;
	private String countryCode;
	private BigDecimal currencyConversionRate;

	private String termsAndUse;
	private String privecyPolicy;
	private String helpCentreAndFaq;
	private String contract;

	private BigDecimal customDutiesPercentage;
	private BigDecimal importFeePercentage;
	private BigDecimal minimumDutiesAmount;

	private String flagUrl;
	private Integer quoteProductMaxAddedQty;
	private BigDecimal importMaxFeePercentage;
	private BigDecimal catalogCurrencyConversionRate;
	
	private Boolean decimalPricing;

	private PhoneNumberValidation phoneNumberValidation;

	private int warehouseId;
	private String mapperTable;	
	private int rmaapplicableThreshold;
	
	private BigDecimal minimumFirstOrderValue;
	
	private boolean holdOrder;
	
	private boolean isPayfortAuthorized;
	private String shukranStoreCode;
	private Integer shukranWelcomeBonous;
	private String shukranCurrencyCode;
	private String shukranProfileMessage;
	private String shukranProfileDetailsMessage;
	private Boolean isShukranEnable;

	/**
	 * When pushing to GCP, only stores with isEnable true are pushed. Missing/null is treated as true.
	 */
	private Boolean isEnable;
}

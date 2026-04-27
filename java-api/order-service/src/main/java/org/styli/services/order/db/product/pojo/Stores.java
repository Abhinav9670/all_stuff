package org.styli.services.order.db.product.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    private String shukranStoreCode;
    private String shukranCurrencyCode;
    private String invoiceTerritory;
    private Boolean isShukranEnable= false;
    private BigDecimal shukranPointConversion;
    private int RMAApplicableThreshold;
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
    private Double refundDeduction;
    private boolean isSecondRefund;
    private BigDecimal customDutiesPercentage;
    private BigDecimal importFeePercentage;
    private BigDecimal importMaxFeePercentage;
    private BigDecimal minimumDutiesAmount;

    @JsonProperty("warehouseId")
    private String warehouseId;
    @JsonProperty("mapperTable")
    private String mapperTable;
    private boolean holdOrder;
    private String isPayfortAuthorized;
    private boolean enableApplepayholdOrder;
    private List<String> shipmentMode;
    private ShippingConfig shippingConfig;
    //Order split flag
    private boolean orderSplitFlag;
}
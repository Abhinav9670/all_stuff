package org.styli.services.order.db.product.pojo;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class CoreConfigDataServicePojo implements Serializable {

    private static final long serialVersionUID = 1L;

    StoreDetailsResponseDTO storeDetailsResponseDTO;

    String storeCurrency;

    String storeLanguage;

    BigDecimal storeShipmentChargesThreshold;

    BigDecimal storeShipmentCharges;

    BigDecimal codCharges;

    BigDecimal taxPercentage;

    BigDecimal RMAThresholdInHours;
    
    BigDecimal currencyConversionRate;
    
    BigDecimal catalogcurrencyConversionRate;
}
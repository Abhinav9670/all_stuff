package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;

import lombok.Data;

@Data
public class CoreConfigDataService implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    StoreDetailsResponseDTO storeDetails;

    String storeCurrency;

    String storeLanguage;

    int storeShipmentChargesThreshold;

    int storeShipmentCharges;

    int codCharges;

    int taxPercentage;

    int RMAThresholdInHours;
}

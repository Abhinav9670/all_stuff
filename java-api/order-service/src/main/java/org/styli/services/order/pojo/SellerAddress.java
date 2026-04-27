package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * POJO class representing Seller Address information
 * Contains pickup and dropoff addresses in both Arabic and English
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerAddress {

    @JsonProperty("PICKUP_ADDRESS_AR")
    private List<String> pickupAddressAr;

    @JsonProperty("PICKUP_ADDRESS_EN")
    private List<String> pickupAddressEn;

    @JsonProperty("DROPOFF_ADDRESS_AR")
    private List<String> dropoffAddressAr;

    @JsonProperty("DROPOFF_ADDRESS_EN")
    private List<String> dropoffAddressEn;
}


package org.styli.services.order.utility.consulValues;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromoValues {

    private PromoRedemptionValues promoRedemptionUrl;
    private FeatureBasedFlag featureBasedFlag;
}

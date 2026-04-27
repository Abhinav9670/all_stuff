package org.styli.services.order.utility.consulValues;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromoRedemptionValues {

    private boolean enabled;
    
    private boolean allowAllStores;
    
    private boolean allowInternalUsers;
    
    private List<Integer> allowedStores;
    
    private List<String> excludeEmailId;
    
    private String defaultValidateEndpoint;
    
    private String defaultRedemptionEndpoint;
    
    private String defaultRedemptionChangeStatusEndpoint;
    
    private String defaultCouponListEndpoint;
    
    private String defaultBankOffersEndpoint;
    
    private String validateEndpoint;
    
    private String redemptionEndpoint;
    
    private String redemptionChangeStatusEndpoint;
    
    private boolean cohortBasedCoupon;

}

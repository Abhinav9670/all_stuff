package org.styli.services.order.utility.consulValues;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.styli.services.order.utility.Constants;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsulValues {

    @JsonProperty(Constants.COD_CANCEL_MESSAGE)
    private Message codCancelMessage;
    @JsonProperty(Constants.PREPAID_CANCEL_MESSAGE)
    private Message prepaidCancelMessage;
    @JsonProperty(Constants.COD_RETURN_MESSAGE)
    private Message codReturnMessage;
    @JsonProperty(Constants.PREPAID_RETURN_MESSAGE)
    private Message prepaidReturnMessage;
    
    @JsonProperty(Constants.SMS_CHECK_NUMBER)
    private Message smsCheckNumber;

    @JsonProperty("otpVerificationThresholdVersion")
    private String otpVerificationThresholdVersion;

    @JsonProperty("otpVerificationInCreateOrder")
    private Boolean otpVerificationInCreateOrder;
    
    @JsonProperty("return_short_single_pick")
    private String returnShortSinglepickSms;
    
    @JsonProperty("return_short_multi_pick")
    private String returnShortMultipickSms;
    
    @JsonProperty("return_qc_failed_single_pick")  
    private String returnQcFailedSinglePick;
    
    @JsonProperty("return_qc_failed_multi_pick")
    private String returnQcFailedMultiplePick;
    
    @JsonProperty("return_short_qc_failed_single_pick")
    private String returnShortQcFailedSinglePick;
    
    @JsonProperty("return_short_qc_failed_multi_pick")
    private String returnShortQcFailedMultiplePic;
    
    
    @JsonProperty("return_short_single_pick_ar")
    private String returnShortSinglepickAR;
    
    @JsonProperty("return_short_multi_pick_ar")
    private String returnShortMultipickAR;
    
    @JsonProperty("return_qc_failed_single_pick_ar")  
    private String returnQcFailedSinglePickAr;
    
    @JsonProperty("return_qc_failed_multi_pick_ar")
    private String returnQcFailedMultiplePickAr;
    
    @JsonProperty("return_short_qc_failed_single_pick_ar")
    private String returnShortQcFailedSinglePickAr;
    
    @JsonProperty("return_short_qc_failed_multi_pick_ar")
    private String returnShortQcFailedMultiplePicAr;
    
    @JsonProperty("deleteCustomer")
    private DeleteCustomer deleteCustomer;
    
    @JsonProperty("payment_failed_threshold_version")
    private String paymentFailedThresholdVersion;
    
}

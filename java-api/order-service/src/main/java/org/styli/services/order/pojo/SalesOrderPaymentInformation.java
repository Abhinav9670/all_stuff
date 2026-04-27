package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Umesh, 11/05/2020
 * @project product-service
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesOrderPaymentInformation {

    @JsonProperty("card_number")
    public String cardNumber;

    @JsonProperty("payment_option")
    public String paymentOption;

    @JsonProperty("response_code")
    private String responseCode;

    @JsonProperty("response_message")
    private String responseMessage;
    
    @JsonProperty("customer_ip")
    private String customerIp;
    
    @JsonProperty("amount")
    private String amount;
    
    @JsonProperty("authorization_code")
    private String authorizationCode;
    
    @JsonProperty("merchant_reference")
    private String merchantReference;
    
	@JsonProperty("reconciliation_reference")
	private String reconciliationReference;	
    
    

}

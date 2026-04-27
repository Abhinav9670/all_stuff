package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayforDtfRequest {

	@JsonProperty("amount")
	private String amount;
	
	@JsonProperty("response_code")
	private String responseCode;
	
	@JsonProperty("card_number")
	private String cardNumber; 
	
	@JsonProperty("digital_wallet")
	private String digitalWallet; 
	
	@JsonProperty("signature")
	private String signature;
	
	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("order_description")
	private String orderDescription;
	
	@JsonProperty("payment_option")
	private String paymentOption;
	
	@JsonProperty("expiry_date")
	private String expireDate;
	
	@JsonProperty("customer_ip")
	private String customerIp;
	
	@JsonProperty("language")
	private String language;
		
	@JsonProperty("eci")
	private String eci;
	
	@JsonProperty("fort_id")
	private String fortId;
		
	@JsonProperty("command")
	private String command;
		
	@JsonProperty("response_message")
	private String responseMessage;
	
	@JsonProperty("merchant_reference")
	private String merchantReference;
	
	@JsonProperty("authorization_code")
	private String authorizationCode;
	
	@JsonProperty("token_name")
	private String tokenName;
	
	@JsonProperty("customer_email")
	private String customerEmail;
	
	@JsonProperty("merchant_extra1")
	private String merchantExtra;
	
	@JsonProperty("currency")
	private String currency;
	
	@JsonProperty("merchant_extra4")
	private String merchant;
		
	@JsonProperty("status")
	private String status;
	
	@JsonProperty("processor_response_code")
	private String processResponseCode;	
	
	
	@JsonProperty("remember_me")
	private String rememberMe;	
	
	@JsonProperty("reconciliation_reference")
	private String reconciliationReference;	
	
}

package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KwtCredentials {
	
	@JsonProperty("PAYFORT_KWT_AMOUNT_MULTIPLIER_HUNDRED")
	private String payfortKwtAmountMultiplier;
	
	@JsonProperty("PAYFORT_TOKEN_KWT_CARD_REQ_PASSPHRASE")
	private String payfortKwtHashCardReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_KWT_CARD_RES_PASSPHRASE")
	private String payfortKwtHashCardResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_KWT_CARD_ACCESS_CODE")
	private String payfortKwtCardAccessCode;

	@JsonProperty("PAYFORT_TOKEN_KWT_APPLE_ACCESS_CODE")
	private String payfortKwtApplePayAccessCode;

	@JsonProperty("PAYFORT_TOKEN_KWT_APPLE_REQ_PASSPHRASE")
	private String payfortKwtHashApplePayReqTokenPhrase;

	@JsonProperty("PAYFORT_TOKEN_KWT_APPLE_RES_PASSPHRASE")
	private String payfortKwtHashApplePayResTokenPhrase;
	
	@JsonProperty("PAYFORT_TOKEN_KWT_CARD_MERCHANT_IDENTIFIER")
	private String payfortKwtCardMerchantIdentifier;

}

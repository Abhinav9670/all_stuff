package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UaeCredentials {
	
	@JsonProperty("PAYFORT_UAE_AMOUNT_MULTIPLIER_HUNDRED")
	private String payfortuaeAmountMultiplier;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_APPLE_REQ_PASSPHRASE")
	private String payfortUaeHashAppleReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_APPLE_RES_PASSPHRASE")
	private String payfortUaeHashAppleResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_CARD_REQ_PASSPHRASE")
	private String payfortUaeHashCardReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_CARD_RES_PASSPHRASE")
	private String payfortUaeHashCardResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_APPLE_ACCESS_CODE")
	private String payfortUaeAppleAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_CARD_ACCESS_CODE")
	private String payfortUaeCardAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_CARD_MERCHANT_IDENTIFIER")
	private String payfortUaeCardMerchantIdentifier;
	
	@JsonProperty("PAYFORT_TOKEN_UAE_APPLE_MERCHANT_IDENTIFIER")
	private String payfortUaeAppleMerchantIdentifier;
}

package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KsaCredentials {
	
	@JsonProperty("PAYFORT_KSA_AMOUNT_MULTIPLIER_HUNDRED")
	private String payfortksaAmountMultiplier;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_APPLE_REQ_PASSPHRASE")
	private String payfortKsaHashAppleReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_APPLE_RES_PASSPHRASE")
	private String payfortKsaHashAppleResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_CARD_REQ_PASSPHRASE")
	private String payfortKsaHashCardReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_CARD_RES_PASSPHRASE")
	private String payfortKsaHashCardResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_APPLE_ACCESS_CODE")
	private String payfortKsaAppleAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_CARD_ACCESS_CODE")
	private String payfortKsaCardAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_CARD_MERCHANT_IDENTIFIER")
	private String payfortKsaCardMerchantIdentifier;
	
	@JsonProperty("PAYFORT_TOKEN_KSA_APPLE_MERCHANT_IDENTIFIER")
	private String payfortKsaAppleMerchantIdentifier;
}

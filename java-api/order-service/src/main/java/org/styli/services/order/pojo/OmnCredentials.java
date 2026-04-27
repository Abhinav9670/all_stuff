package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OmnCredentials {
	
	@JsonProperty("PAYFORT_OMN_AMOUNT_MULTIPLIER_HUNDRED")
	private String payfortOmnAmountMultiplier;
	
	@JsonProperty("PAYFORT_TOKEN_OMN_CARD_REQ_PASSPHRASE")
	private String payfortOmnHashCardReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_OMN_CARD_RES_PASSPHRASE")
	private String payfortOmnHashCardResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_OMN_CARD_ACCESS_CODE")
	private String payfortOmnCardAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_OMN_CARD_MERCHANT_IDENTIFIER")
	private String payfortOmnCardMerchantIdentifier;

	@JsonProperty("PAYFORT_TOKEN_OMN_APPLE_ACCESS_CODE")
	private String payfortOmnApplePayAccessCode;

	@JsonProperty("PAYFORT_TOKEN_OMN_APPLE_REQ_PASSPHRASE")
	private String payfortOmnHashApplePayReqTokenPhrase;

	@JsonProperty("PAYFORT_TOKEN_OMN_APPLE_MERCHANT_IDENTIFIER")
	private String payfortOmnAppleMerchantIdentifier;

}

package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BahCredentials {
	
	@JsonProperty("PAYFORT_BAH_AMOUNT_MULTIPLIER_HUNDRED")
	private String payfortBahAmountMultiplier;
	
	@JsonProperty("PAYFORT_TOKEN_BAH_CARD_REQ_PASSPHRASE")
	private String payfortBahHashCardReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_BAH_CARD_RES_PASSPHRASE")
	private String payfortBahHashCardResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_BAH_CARD_ACCESS_CODE")
	private String payfortBahCardAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_BAH_CARD_MERCHANT_IDENTIFIER")
	private String payfortBahCardMerchantIdentifier;

}

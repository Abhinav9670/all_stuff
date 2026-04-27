package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QatCredentials {
	
	@JsonProperty("PAYFORT_QAT_AMOUNT_MULTIPLIER_HUNDRED")
	private String payfortQatAmountMultiplier;
	
	@JsonProperty("PAYFORT_TOKEN_QAT_CARD_REQ_PASSPHRASE")
	private String payfortQatHashCardReqTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_QAT_CARD_RES_PASSPHRASE")
	private String payfortQatHashCardResTokenphrase;
	
	@JsonProperty("PAYFORT_TOKEN_QAT_CARD_ACCESS_CODE")
	private String payfortQatCardAccessCode;
	
	@JsonProperty("PAYFORT_TOKEN_QAT_CARD_MERCHANT_IDENTIFIER")
	private String payfortQatCardMerchantIdentifier;

}

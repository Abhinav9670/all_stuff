package org.styli.services.order.model.Order.cancel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayfortRefundResponse {

	@JsonProperty("response_code")
	private String responseCode;

	@JsonProperty("amount")
	private String amount;

	@JsonProperty("signature")
	private String signature;

	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;

	@JsonProperty("access_code")
	private String accessCode;

	@JsonProperty("order_description")
	private String orderDescription;

	@JsonProperty("language")
	private String language;

	@JsonProperty("fort_id")
	private String fortId;

	@JsonProperty("command")
	private String command;

	@JsonProperty("response_message")
	private String responseMessage;

	@JsonProperty("merchant_reference")
	private String merchantReference;

	@JsonProperty("currency")
	private String currency;

	@JsonProperty("status")
	private String status;

}
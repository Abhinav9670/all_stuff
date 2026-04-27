package org.styli.services.order.pojo;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRestrictionDetails {

	@JsonProperty("md_payfort_cc_vault")
	private BigDecimal mdpayfortccvault;
	
	@JsonProperty("md_payfort")
	private BigDecimal mdpayfoirt;
	
	@JsonProperty("cashondelivery")
	private BigDecimal cashondelivery;
	
	@JsonProperty("tabby_installments")
	private BigDecimal tabbyinstallment;
	
	@JsonProperty("tamara_installments_3")
	private BigDecimal tamarainstallment;
	
	@JsonProperty("apple_pay")
	private BigDecimal applepay;
	
	
}

package org.styli.services.order.pojo.zatca.bulk;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class EInvoicesListRes {
	@JsonProperty("invoiceNumber")
	private String invoiceNumber;
	
	@JsonProperty("errorCode")
	private String errorCode;
	
	@JsonProperty("errorMessage")
	private String errorMessage;
	
	@JsonProperty("einvoiceStatus")
	private String einvoiceStatus;
}

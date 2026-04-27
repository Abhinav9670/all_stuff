package org.styli.services.order.pojo.zatca.bulk;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ZatcaInvoiceBulkSingle {

	@JsonProperty("invoiceNumber")
	private String invoiceNumber;
	
	@JsonProperty("invoiceType")
    private String invoiceType;
	
	@JsonProperty("issueDate")
    private String issueDate;
	
	@JsonProperty("vat")
    private String vat;
}

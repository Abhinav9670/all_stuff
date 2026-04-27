package org.styli.services.order.pojo.zatca;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZatcaInvoiceResponse {

	@JsonProperty("QRCode")
	private String qrCode;
	
	@JsonProperty("UUID")
	private String UUID;
	
	@JsonProperty("Status")
	private String status;
	
	@JsonProperty("QrCodeStatus")
	private String qrCodeStatus;
	
	@JsonProperty("InvoiceStatus")
	private String invoiceStatus;
	
	@JsonProperty("ValidationsSuccess")
	private String validationsSuccess;
	
	@JsonProperty("UniqueId")
	private String uniqueId;
	
	@JsonProperty("Id")
	private String Id;
	
	@JsonProperty("ErrorList")
	private List<ZatcaErrorList> errorList;
	
	@JsonProperty("WarningList")
	private List<ZatcaErrorList> warningList;
}

package org.styli.services.order.pojo.zatca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZatcaConfig {

	@JsonProperty("baseUrl")
	private String baseUrl;
	
	@JsonProperty("techSupportEmail")
	private String techSupportEmail;
	
	@JsonProperty("bulkLimit")
	private Integer bulkLimit;
	
	@JsonProperty("clearTaxToken")
	private String clearTaxToken;
	
	@JsonProperty("vatNo")
	private String vatNo;

	@JsonProperty("DeviceId")
	private String DeviceId;

	@JsonProperty("InvoiceTypeCode")
	private InvoiceTypeCode InvoiceTypeCode;
	
	@JsonProperty("CreditTypeCode")
	private InvoiceTypeCode CreditTypeCode;
	
	@JsonProperty("ID")
	private ID ID;
	
	@Data
	public static class ID {
		private String en;
		private String ar;
	}

    @Data
    public static class InvoiceTypeCode {
        private String name;
        private String value;
    }

    @JsonProperty("AccountingSupplierParty")
	private AccountingSupplierParty AccountingSupplierParty;
    
    @JsonProperty("ShippingFeeText")
	private ID ShippingFeeText;
    
    @JsonProperty("DiscountText")
	private ID DiscountText;
    
    @JsonProperty("CODFeeText")
	private ID CODFeeText;
    
    @JsonProperty("DonationFeeText")
	private ID DonationFeeText;
    
    @JsonProperty("ImportFeeText")
   	private ID ImportFeeText;
    
    @JsonProperty("CustomHeaderTextInvoice")
   	private ID CustomHeaderTextInvoice;
    
    @JsonProperty("CustomHeaderTextCreditNote")
   	private ID CustomHeaderTextCreditNote;

	@JsonProperty("secondReturnZatcainvoice")
	private Boolean secondReturnZatcainvoice;

	@JsonProperty("codRtoZatca")
	private Boolean codRtoZatca;
    
    @JsonProperty("zatca_invoice_failed_list_months_ago")
	private Integer zatcaInvoiceFailedListMonthsAgo;
    
    @JsonProperty("zatca_creditmemo_failed_list_months_ago")
	private Integer zatcaCreditmemoFailedListMonthsAgo;
    
    
}

package org.styli.services.order.pojo.zatca;

import java.util.LinkedHashMap;
import java.util.List;


import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ZatcaInvoice {
	@JsonProperty("DeviceId")
	private String DeviceId;
	@JsonProperty("EInvoice")
	private EInvoice EInvoice;

	@Data
	public static class EInvoice {
		@JsonProperty("ProfileID")
		private String ProfileID;
		@JsonProperty("ID")
		private ID ID;
		@JsonProperty("InvoiceTypeCode")
		private InvoiceTypeCode InvoiceTypeCode;
		@JsonProperty("IssueDate")
		private String IssueDate;
		@JsonProperty("IssueTime")
		private String IssueTime;
		@JsonProperty("BillingReference")
		private List<BillingReference> BillingReference;
		@JsonProperty("DocumentCurrencyCode")
		private String DocumentCurrencyCode;
		@JsonProperty("TaxCurrencyCode")
		private String TaxCurrencyCode;
		@JsonProperty("AccountingSupplierParty")
		private AccountingSupplierParty AccountingSupplierParty;
		@JsonProperty("AccountingCustomerParty")
		private AccountingSupplierParty AccountingCustomerParty;
		@JsonProperty("PaymentMeans")
		private List<PaymentMeans> PaymentMeans;
		
		@Data
		public static class AccountingCustomerParty {
			@JsonProperty("Party")
			private LinkedHashMap<String, String> Party;
			
		}
		
		@JsonProperty("InvoiceLine")
		private List<InvoiceLine> InvoiceLine;
		@JsonProperty("AllowanceCharge")
		private List<AllowanceCharge> AllowanceCharge;
		@JsonProperty("TaxTotal")
		private List<TaxTotal> TaxTotal;
		@JsonProperty("LegalMonetaryTotal")
		private LegalMonetaryTotal LegalMonetaryTotal;
		@JsonProperty("Note")
		private Note Note;

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

		@Data
		public static class BillingReference {
			@JsonProperty("InvoiceDocumentReference")
			private InvoiceDocumentReference InvoiceDocumentReference;
			
			@Data
			public static class InvoiceDocumentReference {
				@JsonProperty("ID")
				private ID ID;

				@Data
				public static class ID {
					private String en;
					private String ar;
				}
			}
		}
		
		@Data
	    public static class Item {
			@JsonProperty("Name")
	        private Name Name;
			@JsonProperty("ClassifiedTaxCategory")
	        private ClassifiedTaxCategory ClassifiedTaxCategory;
	    }

	    @Data
	    public static class Name {
	        private String en;
	        private String ar;
	    }

	    @Data
	    public static class ClassifiedTaxCategory {
//	    	@JsonProperty("ID")
//	        private String ID;
	    	@JsonProperty("Percent")
	        private String Percent;
	    	@JsonProperty("TaxScheme")
	        private TaxScheme TaxScheme;
	    }

	    @Data
	    public static class TaxScheme {
	    	@JsonProperty("ID")
	        private String ID;
	    }

	    @Data
	    public static class Price {
	    	@JsonProperty("AllowanceCharge")
	        private AllowanceCharge AllowanceCharge;
	    	@JsonProperty("PriceAmount")
	        private PriceAmount PriceAmount;
	    	@JsonProperty("BaseQuantity")
	        private BaseQuantity BaseQuantity;
	    }

	    @Data
	    public static class PriceAmount {
	    	@JsonProperty("currencyID")
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class BaseQuantity {
	        private String unitCode;
	        private String value;
	    }

	    @Data
	    public static class InvoicedQuantity {
	        private String unitCode;
	        private String value;
	    }

	    @Data
	    public static class AllowanceCharge {
	        private boolean ChargeIndicator;
	        private BaseAmount BaseAmount;
	        private String MultiplierFactorNumeric;
	        private Amount Amount;
	        private AllowanceChargeReason AllowanceChargeReason;
	        private String AllowanceChargeReasonCode;
	    }

	    @Data
	    public static class BaseAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class Amount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class AllowanceChargeReason {
	        private String ar;
	        private String en;
	    }

	    @Data
	    public static class LineExtensionAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class TaxTotal {
	    	@JsonProperty("TaxAmount")
	        private TaxAmount TaxAmount;
	    	
	    	
	    	@JsonProperty("RoundingAmount")
	        private RoundingAmount RoundingAmount;
//	    	LinkedHashMap<String, String> RoundingAmount
	    	
	    	@JsonProperty("TaxSubtotal")
	        private List<TaxSubtotal> TaxSubtotal;
	    }

	    @Data
	    public static class TaxAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class RoundingAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class TaxSubtotal {
	    	@JsonProperty("TaxableAmount")
	        private TaxableAmount TaxableAmount;
	    	@JsonProperty("TaxAmount")
	        private TaxAmount TaxAmount;
	    	@JsonProperty("TaxCategory")
	        private TaxCategory TaxCategory;
	    }

	    @Data
	    public static class TaxableAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class TaxCategory {
	    	@JsonProperty("ID")
	        private String ID;
	    	@JsonProperty("Percent")
	        private String Percent;
	    	@JsonProperty("TaxScheme")
	        private TaxScheme TaxScheme;
	    	@JsonProperty("TaxExemptionReasonCode")
	        private String TaxExemptionReasonCode;
	    	@JsonProperty("TaxExemptionReason")
	        private TaxExemptionReason TaxExemptionReason;
	    }

	    @Data
	    public static class TaxExemptionReason {
	    	private String ar;
	        private String en;
	    }
	    
	    @Data
	    public static class LegalMonetaryTotal {
	    	@JsonProperty("LineExtensionAmount")
	        private LineExtensionAmount LineExtensionAmount;
	    	@JsonProperty("AllowanceTotalAmount")
	        private AllowanceTotalAmount AllowanceTotalAmount;
	    	@JsonProperty("TaxExclusiveAmount")
	        private TaxExclusiveAmount TaxExclusiveAmount;
	    	@JsonProperty("TaxInclusiveAmount")
	        private TaxInclusiveAmount TaxInclusiveAmount;
	    	@JsonProperty("PrepaidAmount")
	        private PrepaidAmount PrepaidAmount;
	    	@JsonProperty("PayableAmount")
	        private PayableAmount PayableAmount;
	    	
	    	@JsonProperty("PaybleRoundingAmount")
	        private PayableAmount PaybleRoundingAmount;
	    	
	    }
	    
	    @Data
	    public static class AllowanceTotalAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class TaxExclusiveAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class TaxInclusiveAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class PrepaidAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class PayableAmount {
	        private String currencyID;
	        private String value;
	    }

	    @Data
	    public static class PaymentMeans {
	    	@JsonProperty("PaymentMeansCode")
	        private String PaymentMeansCode;
	    	@JsonProperty("InstructionNote")
	        private InstructionNote InstructionNote;
//	    	@JsonProperty("PayeeFinancialAccount")
//	        private PayeeFinancialAccount PayeeFinancialAccount;
	    }

	    @Data
	    public static class InstructionNote {
	        private String en;
	        private String ar;
	    }

	    @Data
	    public static class PayeeFinancialAccount {
	    	@JsonProperty("PaymentNote")
	        private PaymentNote PaymentNote;
	    }

	    @Data
	    public static class PaymentNote {
	        private String en;
	        private String ar;
	    }

	    @Data
	    public static class Note {
	        private String en;
	        private String ar;
	    }
	}
	
	@JsonProperty("CustomFields")
	private LinkedHashMap<String, String> CustomFields;
}



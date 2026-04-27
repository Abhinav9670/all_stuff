package org.styli.services.order.pojo.zatca;

import java.util.LinkedHashMap;
import java.util.List;

import org.styli.services.order.pojo.zatca.ZatcaInvoice.EInvoice.PaymentMeans;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class InvoiceLine {
	@JsonProperty("ID")
	private String ID;
	@JsonProperty("Item")
	private Item Item;
	@JsonProperty("Price")
	private Price Price;
	@JsonProperty("InvoicedQuantity")
	private InvoicedQuantity InvoicedQuantity;
	@JsonProperty("AllowanceCharge")
	private List<AllowanceCharge>  AllowanceCharge;
	@JsonProperty("LineExtensionAmount")
	private LineExtensionAmount LineExtensionAmount;
	@JsonProperty("TaxTotal")
	private TaxTotal TaxTotal;
	
	@JsonProperty("CustomFields")
	private LinkedHashMap<String, String> CustomFields;

	@Data
	public static class Item {
		@JsonProperty("Name")
		private Name Name;
		@JsonProperty("ClassifiedTaxCategory")
		private ClassifiedTaxCategory ClassifiedTaxCategory;
		
		@JsonProperty("SellersItemIdentification")
		private SellersItemIdentification SellersItemIdentification;
		
		@JsonProperty("AdditionalItemProperty")
		private List<AdditionalItemProperty> AdditionalItemProperty;
		
		
		
		@Data
		public static class Name {
			private String en;
			private String ar;
		}

		@Data
		public static class ClassifiedTaxCategory {
			@JsonProperty("ID")
			private String ID;
			@JsonProperty("Percent")
			private String Percent;
			@JsonProperty("TaxScheme")
			private TaxScheme TaxScheme;
			
			@Data
			public static class TaxScheme {
				@JsonProperty("ID")
				private String ID;
			}
		}
		
		@Data
		public static class SellersItemIdentification {
			@JsonProperty("ID")
			private ID ID;
			
			@Data
			public static class ID {
				private String en;
				private String ar;
			}
		}
		
		@Data
		public static class AdditionalItemProperty {
			@JsonProperty("Name")
			private String Name;
			
			@JsonProperty("Value")
			private String Value;
		}

	}

	
	@Data
	public static class Price {
		@JsonProperty("PriceAllowanceCharge")
		private PriceAllowanceCharge AllowanceCharge;
		@JsonProperty("PriceAmount")
		private PriceAmount PriceAmount;
		@JsonProperty("BaseQuantity")
		private BaseQuantity BaseQuantity;

		@Data
		public static class PriceAllowanceCharge {
			@JsonProperty("ChargeIndicator")
			private boolean ChargeIndicator;
			@JsonProperty("BaseAmount")
			private BaseAmount BaseAmount;
			@JsonProperty("Amount")
			private Amount Amount;
		}
	}

	@Data
	public static class AllowanceCharge {
		@JsonProperty("ChargeIndicator")
		private boolean ChargeIndicator;
		@JsonProperty("BaseAmount")
		private BaseAmount BaseAmount;
		@JsonProperty("MultiplierFactorNumeric")
		private String MultiplierFactorNumeric;
		@JsonProperty("Amount")
		private Amount Amount;
		@JsonProperty("AllowanceChargeReason")
		private AllowanceChargeReason AllowanceChargeReason;
		@JsonProperty("AllowanceChargeReasonCode")
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
	public static class PriceAmount {
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
	}

	
}

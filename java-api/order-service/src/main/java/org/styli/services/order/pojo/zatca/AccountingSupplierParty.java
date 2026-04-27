package org.styli.services.order.pojo.zatca;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
public class AccountingSupplierParty {

	@JsonProperty("Party")
	private Party Party;

	@Data
	public static class Party {

		@JsonProperty("PartyLegalEntity")
		private PartyLegalEntity PartyLegalEntity;
		@JsonProperty("PartyTaxScheme")
		private PartyTaxScheme PartyTaxScheme;
		@JsonProperty("PartyIdentification")
		private PartyIdentification PartyIdentification;
		@JsonProperty("PostalAddress")
		private PostalAddress PostalAddress;

		@Data
		public static class PartyLegalEntity {

			@JsonProperty("RegistrationName")
			private RegistrationName RegistrationName;

			@Data
			public static class RegistrationName {
				private String en;
				private String ar;
			}
		}

		@Data
		public static class PartyTaxScheme {
			@JsonProperty("CompanyID")
			private String CompanyID;
			
			@JsonProperty("TaxScheme")
			private TaxScheme TaxScheme;

			@Data
			public static class TaxScheme {
				@JsonProperty("ID")
				private String ID;
			}
		}

		@Data
		public static class PartyIdentification {
			@JsonProperty("ID")
			private ID ID;

			@Data
			public static class ID {
				@JsonProperty("schemeID")
				private String schemeID;
				@JsonProperty("value")
				private String value;
			}
		}

		@Data
		public static class PostalAddress {

			@JsonProperty("StreetName")
			private StreetName StreetName;
			@JsonProperty("AdditionalStreetName")
			private AdditionalStreetName AdditionalStreetName;
			@JsonProperty("BuildingNumber")
			private BuildingNumber BuildingNumber;
			@JsonProperty("PlotIdentification")
			private PlotIdentification PlotIdentification;
			@JsonProperty("CityName")
			private CityName CityName;
			@JsonProperty("CitySubdivisionName")
			private CitySubdivisionName CitySubdivisionName;
			@JsonProperty("PostalZone")
			private String PostalZone;
			@JsonProperty("CountrySubentity")
			private CountrySubentity CountrySubentity;
			@JsonProperty("Country")
			private Country Country;

			@Data
			public static class StreetName {
				private String en;
				private String ar;
			}

			@Data
			public static class AdditionalStreetName {
				private String en;
				private String ar;
			}

			@Data
			public static class BuildingNumber {
				private String en;
				private String ar;
			}

			@Data
			public static class PlotIdentification {
				private String en;
				private String ar;
			}

			@Data
			public static class CityName {
				private String en;
				private String ar;
			}

			@Data
			public static class CitySubdivisionName {
				private String en;
				private String ar;
			}

			@Data
			public static class CountrySubentity {
				private String en;
				private String ar;
			}

			@Data
			public static class Country {
				@JsonProperty("IdentificationCode")
				private String IdentificationCode;
			}
		}
	}
}

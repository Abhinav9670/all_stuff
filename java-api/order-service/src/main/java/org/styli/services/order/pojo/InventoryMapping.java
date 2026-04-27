package org.styli.services.order.pojo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryMapping {

	@JsonProperty("warehouse_id")
	private String wareHouseId;

	@JsonProperty("seller_id")
	private String sellerId;

	@JsonProperty("seller_name")
	private String sellerName;
	
	private List<String> inventoryTable;
	
	@JsonProperty("WMS_WAREHOUSE_HEADER_USER_NAME")
	private String wmsHeaderUsrName;
	
	@JsonProperty("WMS_WAREHOUSE_HEADER_PASSWORD")
	private String wmsHeaderUsrPassword;

	@JsonProperty("INVOICE_ADDRESS_EN")
	private List<String> invoiceAddressEn;
	
	@JsonProperty("INVOICE_ADDRESS_AR")
	private List<String> invoiceAddressAr;
	
}

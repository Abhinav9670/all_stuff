package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerInventoryMapping {


	@JsonProperty("SELLER_ID")
	private String sellerId;

	@JsonProperty("seller_name")
	private String sellerName;

	@JsonProperty("warehouse_id")
	private String wareHouseId;

	@JsonProperty("seller_warehouse_id")
	private String sellerWareHouseId;

	@JsonProperty("PUSH_TO_WMS")
	private Boolean pushToWms;

	@JsonProperty("PUSH_ORDER_FOR_SKU")
	private Boolean pushOrderForSku;

	@JsonProperty("WMS_WAREHOUSE_HEADER_USER_NAME")
	private String wmsHeaderUsrName;
	
	@JsonProperty("WMS_WAREHOUSE_HEADER_PASSWORD")
	private String wmsHeaderUsrPassword;

	@JsonProperty("WMS_WAREHOUSE_BASE_URL")
	private String wmsWareHouseBaseUrl;

    @JsonProperty("WMS_WAREHOUSE_OUTWARD_ORDER")
    private String wmsWareHouseOutwardOrder;

    @JsonProperty("WMS_WAREHOUSE_ORDER_CANCEL")
    private String wmsWareHouseOrderCancel;

	@JsonProperty("PICKUP_INFO_NAME")
	private String pickupInfoName;

	@JsonProperty("PUSH_TO_SELLER_CENTRAL")
	private Boolean pushToSellerCentral;

	@JsonProperty("HAS_GLOBAL_SHIPMENT")
	private Boolean hasGlobalShipment;

	@JsonProperty("ACKNOWLEDGEMENT_SLA_HRS")
	private Integer acknowledgementSlaHrs;

	@JsonProperty("PACKED_SLA_HRS")
	private Integer packedSlaHrs;

	@JsonProperty("SHIPPED_SLA_HRS")
	private Integer shippedSlaHrs;

	@JsonProperty("warehouse_name")
	private String wareHouseName;

	@JsonProperty("default_ship_to")
	private String defaultShipTo;

	@JsonProperty("default_ship_to_warehouse_id")
	private String defaultShipToWarehouseId;

	@JsonProperty("default_fullfilment_by")
	private String defaultFullfilmentBy;

	@JsonProperty("ORDER_STATUS_GOVERANANCE")
	private Boolean orderStatusGoverance;

	@JsonProperty("MAX_ACKNOWLEDGEMENT_BUFFER")
	private Integer maxAcknowledgementBuffer;

	@JsonProperty("MAX_PACKED_BUFFER")
	private Integer maxPackedBuffer;

	@JsonProperty("MAX_SHIPPED_BUFFER")
	private Integer maxShippedBuffer;

	@JsonProperty("picked_up_by_styli")
	private Boolean pickedUpByStyli = false;
}

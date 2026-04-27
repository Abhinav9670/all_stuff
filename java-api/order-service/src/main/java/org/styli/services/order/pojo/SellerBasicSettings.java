package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * POJO class representing Seller Basic Settings
 * Contains seller identification, warehouse info, and push configurations
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerBasicSettings {

    @JsonProperty("PUSH_TO_WMS")
    private Boolean pushToWms;

    @JsonProperty("seller_name")
    private String sellerName;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("warehouse_name")
    private String warehouseName;

    @JsonProperty("default_ship_to")
    private String defaultShipTo;

    @JsonProperty("default_ship_to_warehouse_id")
    private String defaultShipToWarehouseId;

    @JsonProperty("PUSH_ORDER_FOR_SKU")
    private Boolean pushOrderForSku;

    @JsonProperty("HAS_GLOBAL_SHIPMENT")
    private Boolean hasGlobalShipment;

    @JsonProperty("PUSH_TO_SELLER_CENTRAL")
    private Boolean pushToSellerCentral;

    @JsonProperty("default_fullfilment_by")
    private String defaultFullfilmentBy;

    @JsonProperty("PICKED_UP_BY_STYLI")
    private Boolean pickedUpByStyli = false;
}


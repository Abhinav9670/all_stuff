package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnicommereceInventoryMapping {
    @JsonProperty("seller_id")
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

    @JsonProperty("CLIENT_ID")
    private String clientId;

    @JsonProperty("MERCHANT_ID")
    private String merchantId;

    @JsonProperty("SECURITY_KEY")
    private String securityKey;

    @JsonProperty("PICKUP_INFO_NAME")
    private String pickupInfoName;

    @JsonProperty("RETURN_INFO_NAME")
    private String returnInfoName;

    @JsonProperty("UC_BASE_URL")
    private String uCBaseUrl;

}


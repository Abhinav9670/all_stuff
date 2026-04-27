package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * POJO class representing Seller Configuration
 * Contains WMS warehouse settings and SLA configurations
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerConfiguration {

    @JsonProperty("PACKED_SLA_HRS")
    private Integer packedSlaHrs;

    @JsonProperty("SHIPPED_SLA_HRS")
    private Integer shippedSlaHrs;

    @JsonProperty("PICKUP_INFO_NAME")
    private String pickupInfoName;

    @JsonProperty("WMS_WAREHOUSE_BASE_URL")
    private String wmsWarehouseBaseUrl;

    @JsonProperty("ACKNOWLEDGEMENT_SLA_HRS")
    private Integer acknowledgementSlaHrs;

    @JsonProperty("WMS_WAREHOUSE_HEADER_PASSWORD")
    private String wmsWarehouseHeaderPassword;

    @JsonProperty("WMS_WAREHOUSE_HEADER_USER_NAME")
    private String wmsWarehouseHeaderUserName;

    @JsonProperty("ORDER_STATUS_GOVERNANCE")
    private Boolean orderStatusGovernance;

    @JsonProperty("WMS_WAREHOUSE_OUTWARD_ORDER")
    private String wmsWareHouseOutwardOrder;

    @JsonProperty("WMS_WAREHOUSE_ORDER_CANCEL")
    private String wmsWareHouseOrderCancel;

    @JsonProperty("MAX_PACKED_BUFFER")
    private Integer maxPackedBuffer;

    @JsonProperty("MAX_SHIPPED_BUFFER")
    private Integer maxShippedBuffer;

    @JsonProperty("MAX_ACKNOWLEDGEMENT_BUFFER")
    private Integer maxAcknowledgementBuffer;
}
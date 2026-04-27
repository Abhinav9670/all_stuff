package org.styli.services.order.pojo.increff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Request POJO for pushing back order to Increff system
 * Used when order is cancelled and linked back order items need to be sent to Increff
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncreffBackOrderPushRequest {

    @JsonProperty("locationCode")
    private String locationCode;

    @JsonProperty("orderCode")
    private String orderCode;

    @JsonProperty("status")
    private String status;

    @JsonProperty("orderTime")
    private String orderTime;

    @JsonProperty("orderItems")
    private List<BackOrderItem> orderItems;

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("partnerLocationCode")
    private String partnerLocationCode;

    @JsonProperty("onHold")
    private Boolean onHold;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("startProcessingTime")
    private String startProcessingTime;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("orderFlow")
    private String orderFlow;

    @JsonProperty("qcStatus")
    private String qcStatus;

    @JsonProperty("requiredBy")
    private String requiredBy;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BackOrderItem {

        @JsonProperty("channelSkuCode")
        private String channelSkuCode;

        @JsonProperty("orderItemCode")
        private String orderItemCode;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("sellingPricePerUnit")
        private Double sellingPricePerUnit;

        @JsonProperty("shippingChargePerUnit")
        private Double shippingChargePerUnit;
    }
}

package org.styli.services.order.pojo.whatsapp.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response DTO for mobile shipment details with Order ID
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MobileShipmentDetailResponse {
    private String incrementId;
    private String orderId;
    private String shipmentId;
    private String shipmentStatus;
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("shipmentStatus_ar")
    private String shipmentStatusAr;
    
    private String status;
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("status_ar")
    private String statusAr;
    private Integer stepValue;
    private Integer colorValue;
    private String estimatedDeliveryTime;
    private Integer deliveryDayCount;
    private String paymentMode;
    private String amount;
    private String shippingMobileNo;
    private String shippingAddress;
}


package org.styli.services.order.pojo.whatsapp.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Created on 27-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MobileOrderDetailResponse {
    private String incrementId;
    private String orderStatus;
    private String status;
    private Integer stepValue;
    private Integer colorValue;
    private String estimatedDeliveryTime;
    private Integer deliveryDayCount;
    private String paymentMode;
    private String amount;
    private String shippingTitle;
    private String shippingUrl;
    private String shippingMobileNo;
    private String shippingAddress;
    private List<ShipmentDetail> shipments;
    private Integer shipmentCount;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShipmentDetail {
        private String shipmentId;
        private String shipmentStatus;
        private String status;
        private Integer stepValue;
        private Integer colorValue;
        private String estimatedDeliveryTime;
        private Integer deliveryDayCount;
        private String paymentMode;
        private String amount;
        private String shippingMobileNo;
        private String shippingAddress;
        private String shippingUrl;
    }
}

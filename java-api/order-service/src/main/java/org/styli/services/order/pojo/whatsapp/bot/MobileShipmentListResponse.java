package org.styli.services.order.pojo.whatsapp.bot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;

/**
 * Response DTO for mobile shipment list with nested order and shipment IDs
 */
@Data
public class MobileShipmentListResponse {

    private Integer orderCount;

    private LinkedHashMap<String, String> idObject;

    private LinkedHashMap<String, ShipmentInfo> incrementIds;

    private String idsString;

    private String resultMode;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShipmentInfo {
        private String orderId;
        private String shipmentId;    // Single shipment ID (for normal orders or split orders with only one shipment type)
        private String shipmentId1;   // Local shipment ID (orderId-L1) - only when both local and global exist
        private String shipmentId2;   // Global shipment ID (orderId-G1) - only when both local and global exist
    }
}


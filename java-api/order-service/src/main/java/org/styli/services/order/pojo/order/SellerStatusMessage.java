package org.styli.services.order.pojo.order;

import lombok.Data;

@Data
public class SellerStatusMessage extends StatusMessage {

    private Integer sellerOrderId;
    private String sellerOrderIncrementId;

    public SellerStatusMessage(String statusId, String message, String timestamp, Integer sellerOrderId, String sellerOrderIncrementId) {
        super(statusId, message, timestamp);
        this.sellerOrderId = sellerOrderId;
        this.sellerOrderIncrementId = sellerOrderIncrementId;
    }
}
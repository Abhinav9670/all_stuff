package org.styli.services.order.pojo.response.V3;

import lombok.Data;
import org.styli.services.order.pojo.response.Order.OrderItem;
import org.styli.services.order.pojo.response.Order.OrderTracking;

import java.sql.Timestamp;
import java.util.List;

@Data
public class SplitOrderResponse {
    private Integer splitOrderCount;
    private Integer callToActionFlag;
    private int statusStepValue;
    private int statusColorStepValue;
    private String grandTotal;
    private String baseGrandTotal;
    private String shippingAmount;
    private String discountAmount;
    private String codCharges;
    private String subtotal;
    private String currency;
    private String itemCount;
    private String orderShipCount;
    private String status;
    private String statusLabel;
    private String shipmentMode;
    private Integer splitOrderId;
    private String splitIncrementId;
    private Timestamp estimatedDeliveryTime;
    private String remaingSLADays;
    private boolean slaExpired;
    private Timestamp newEstimatedDeliveryTime;
    private String shippingUrl;
    private List<OrderTracking> trackings;
    private List<OrderItem> products;
    private List<OrderItem> cancelProducts;
    private String canceledAt;
    private String deliveredAt;
    private boolean isRto;
    private String rtoStatus;
    private String rtoRefundAt;
    private String rtoRefundAmount;
}

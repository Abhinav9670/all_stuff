package org.styli.services.order.pojo.response.V3;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.pojo.response.Order.Payments;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class OrderResponseV3 extends OrderResponse {
    private Payments payments;
    private boolean splitOrder;
    private boolean qualifiedPurchase = false;
    private Integer totalSplitOrderCount;
    private String orderShipCount;
    private String remaingSLADays;
    private boolean slaExpired;
    private Timestamp newEstimatedDeliveryTime;
    private Integer callToActionFlag;
    private boolean rto;
    private String globalShippingAmount;

    private List<SplitOrderResponse> splitOrders;
 }
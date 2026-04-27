package org.styli.services.order.pojo.order;

import lombok.Data;

import java.util.List;

import org.styli.services.order.pojo.cancel.Reason;
import org.styli.services.order.pojo.response.Order.OrderAddress;
import org.styli.services.order.pojo.response.Order.RMAItem;

@Data
public class RMAOrderInitResponse {

    private List<Reason> reasons;
    private RMAItem item;

    private OrderAddress pickupAddress;

    private String paymentMethod;
    private String cardNumber;
    private String paymentOption;

    private String pickupSchedule;

}

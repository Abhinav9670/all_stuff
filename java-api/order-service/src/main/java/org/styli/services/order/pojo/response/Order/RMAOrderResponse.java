package org.styli.services.order.pojo.response.Order;

import lombok.Data;
import org.apache.http.annotation.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Umesh, 27/05/2020
 * @project product-service
 */

@Data
public class RMAOrderResponse {

    private String orderIncrementId;
    private String customerId;
    private String createdAt;
    private String deliveredAt;
    private Double returnAmountFee;
    private Boolean isSecondReturn;
    private String paymentMethod;
    private String cardNumber;
    private String paymentOption;
    private String currency;
    private String refundTrasactionNumber;
   

    private OrderAddress pickupAddress;
    private List<RMAItem> items = new ArrayList<>();

}

package org.styli.services.order.pojo.order;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

import org.styli.services.order.pojo.cancel.Reason;
import org.styli.services.order.pojo.response.Order.OrderAddress;
import org.styli.services.order.pojo.response.Order.RMAItem;

/**
 * @author Umesh, 29/05/2020
 * @project product-service
 */

@Data
public class RMAOrderInitV2Response {

    private List<Reason> reasons;
    private List<RMAItem> items;

    private OrderAddress pickupAddress;

    private String paymentMethod;
    private String cardNumber;
    private String paymentOption;

    private String pickupSchedule;
    
    private Boolean isRunningpickupExists=false;
    
    private Boolean isRunningDropOffExists=false;
    
    private String returnRequestId;

    private String orderIncrementId;
    private String customerId;

    /// Refund price breakdown values during return initiation.
    private String refundGrandTotal;
    private String refundStoreCreditTotal;
    private String refundPrepaidTotal;

    private List<BreakDownItem> returnBreakDown;
    private boolean returnChargeApplicable;
    private double returnAmountFee;
    private double returnAmountToBePay;
    private BigDecimal subTotal;
    private int rmaCount;
    private Integer orderId;
    private Boolean isShukranPaymentInOrder= false;
}

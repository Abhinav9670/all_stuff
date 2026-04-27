package org.styli.services.order.pojo.cancel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Umesh, 02/06/2020
 * @project product-service
 */

@Data
public class MagentoRefundOrderRequest {

    @JsonProperty("order_id")
    private Integer orderId;

    @JsonProperty("refunded_amount")
    private BigDecimal refundedAmount;

}

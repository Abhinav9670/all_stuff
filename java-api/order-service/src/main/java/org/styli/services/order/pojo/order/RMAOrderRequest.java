package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 29/05/2020
 * @project product-service
 */

@Data
public class RMAOrderRequest {

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    @Min(1)
    private Integer orderId;

    @NotNull
    @Min(1)
    private Integer parentOrderItemId;

    @NotNull
    @Min(1)
    private Integer returnQuantity;

    @NotNull
    @Min(1)
    private Integer storeId;

    private String reason;

    private Integer reasonId;

}

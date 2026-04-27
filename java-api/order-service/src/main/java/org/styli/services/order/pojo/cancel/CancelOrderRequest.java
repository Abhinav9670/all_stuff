package org.styli.services.order.pojo.cancel;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 14/05/2020
 * @project product-service
 */

@Data
public class CancelOrderRequest {

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    @Min(1)
    private Integer orderId;

    private Integer splitOrderId;

    @NotNull
    @Min(1)
    private Integer storeId;

    private String reason;
    private Integer reasonId;

}

package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author Umesh, 29/05/2020
 * @project product-service
 */

@Data
public class RMAOrderV2Request {

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    @Min(1)
    private Integer orderId;

    List<RMAOrderItemV2Request> items;

    @NotNull
    @Min(1)
    private Integer storeId;

    private Integer splitOrderId;

    private Boolean isDropOffRequest=false;

    private Boolean omsRequest = false;

    private String rmaPaymentMethod;

    private Double refundAmountDebited;

    private Double refundAmountCredited;

    private Double totalRefundAmount;

    private String dropOffDetails;

    private String cityName;

    private String cpId;

    private String returnIncPayfortId;

    private Double returnFeeAmount=0.0;
}
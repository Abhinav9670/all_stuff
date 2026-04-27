package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class OmsRtoCodRequest {
    @NotNull
    @Min(1)
    public Integer orderId;
}

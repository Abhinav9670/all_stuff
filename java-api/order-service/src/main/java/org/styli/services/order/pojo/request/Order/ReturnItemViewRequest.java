package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ReturnItemViewRequest {
    @NotNull
    public Integer requestItemId=0;
    @NotNull
    public int customerId;
    @NotNull
    public int requestId;
    @NotNull
    public String orderIncrementId;
    @NotNull
    public int storeId=1;
}

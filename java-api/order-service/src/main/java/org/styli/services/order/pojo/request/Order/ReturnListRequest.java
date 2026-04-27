package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class ReturnListRequest {
    private Integer customerId;

    public Integer storeId;

    @NotNull
    @Min(1)
    private Integer pageSize = 5;

    @NotNull
    @Min(0)
    private Integer offSet = 0;

    private String customerEmail;

    private boolean useArchive;

    private Integer websiteId;
}

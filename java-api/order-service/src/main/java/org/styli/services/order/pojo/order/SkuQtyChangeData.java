package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class SkuQtyChangeData {

    @NotNull
    private String sku;

    @NotNull @Min(1)
    private Integer oldQty;

    @NotNull @Min(0)
    private Integer newQty;
}

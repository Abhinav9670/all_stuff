package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class AddToQuoteProductsRequest {

    @NotNull
    @Min(1)
    private Integer parentProductId;

    @NotNull
    @Min(1)
    private String productId;

    @Min(1)
    private Integer sizeOptionId;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer quantity;

    private Boolean overrideQuantity;

    private String parentSku;
    private String sku;

}

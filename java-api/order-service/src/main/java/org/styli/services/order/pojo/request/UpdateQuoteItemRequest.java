package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class UpdateQuoteItemRequest {

    @NotNull
    @Min(1)
    private Integer parentQuoteItemId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    @Min(1)
    private Integer storeId;
}

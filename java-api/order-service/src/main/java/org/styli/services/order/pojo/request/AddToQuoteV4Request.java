package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author Umesh, 17/03/2020
 * @project product-service
 */

@Data
public class AddToQuoteV4Request {

    @Min(1)
    private Integer customerId;

    @Min(1)
    private Integer quoteId;

    @NotNull
    @Min(1)
    private Integer storeId;

    private String ipAddress;

    @Valid
    @NotEmpty
    private List<AddToQuoteProductsRequest> addToQuoteProductsRequests;

    private Integer source;

}

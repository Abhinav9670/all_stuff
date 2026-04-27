package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Sub-request for individual split orders within RMA request
 */
@Data
public class RMAOrderV2SubRequest {

    private Integer splitOrderId;

    @NotNull
    private List<RMAOrderItemV2Request> items;
} 
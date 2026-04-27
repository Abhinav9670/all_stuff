package org.styli.services.order.pojo.order;

import lombok.Data;


import javax.validation.constraints.NotNull;

/**
 * Represents an item in the return order for the Warehouse Management System (WMS).
 * 
 * 
 */

@Data
public class WMSReturnCancelItem {

    @NotNull
    private String channelSkuCode;

    @NotNull
    private String returnOrderItemCode;

}

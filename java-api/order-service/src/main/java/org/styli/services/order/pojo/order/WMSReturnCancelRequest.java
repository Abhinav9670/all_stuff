package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Represents a request to cancel return items in the Warehouse Management System (WMS).
 * 
 */

@Data
public class WMSReturnCancelRequest {

    @NotNull
    private String returnOrderCode;

    @NotNull
    private String orderCode;

    @NotNull
    private String locationCode;

    @NotNull
    @Min(1)
    private List<WMSReturnCancelItem> returnOrderItems;
    
    @Override
    public String toString() {
        return "WMSReturnCancelRequest{" +
                "returnOrderCode='" + returnOrderCode + '\'' +
                ", orderCode='" + orderCode + '\'' +
                ", locationCode='" + locationCode + '\'' +
                ", returnOrderItems=" + returnOrderItems +
                '}';
    }

}

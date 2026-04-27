package org.styli.services.order.pojo.request.Order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 14/05/2020
 * @project product-service
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReOrderRequest {

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    @Min(1)
    private Integer orderId;

    @NotNull
    @Min(1)
    private Integer storeId;

    private Integer source;

    private String ipAddress;

    /**
     * Optional: Split Order ID for split orders to identify Local (Express) or Global products.
     * The system will fetch the SplitSalesOrder and determine shipment type from its shipmentMode
     * or hasGlobalShipment flag. Only applicable for split orders, not required for normal orders.
     */
    private String splitOrderId;

}

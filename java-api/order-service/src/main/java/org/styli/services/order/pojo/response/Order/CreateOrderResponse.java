package org.styli.services.order.pojo.response.Order;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CreateOrderResponse {
    public String incrementId;
    public String orderId;
    public String grandTotal;
    public String quoteId;
    public String tabbyPaymentId;

    public boolean firstOrderByCustomer;
    public String payfortAuthorized;
}

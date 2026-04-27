package org.styli.services.order.model.SalesOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SellerOrderInfo {
    private Integer sellerOrderId;
    private String sellerOrderIncrementId;
}

package org.styli.services.order.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreferredPaymentData {

    private String paymentMethod;

    private Integer customerId;

    private Integer storeId;
}

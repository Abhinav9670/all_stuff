package org.styli.services.customer.pojo;

import lombok.Data;


import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class GetQuotePreferredPaymentMethodRequest {

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    private String preferredPayment; 
    
    @NotNull
    private Integer storeId;
    
}
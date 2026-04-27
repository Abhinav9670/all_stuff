package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class CreateRetryPaymentReplicaV4Request {


    private String quoteId;

    @NotNull
    @Min(1)
    private Integer storeId;
    
    private String payfortMerchantReference;

    private String failedPaymentMethod;
    
    private String paidStyliCredit;
}

package org.styli.services.order.pojo;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class AccountDeletionEligibleRequest {

    @NotNull @Min(1)
    private Integer customerId;

    @NotNull @Min(1)
    public Integer storeId;

    private String customerEmail;

}

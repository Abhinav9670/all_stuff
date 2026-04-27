package org.styli.services.customer.pojo.account;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 08/06/2022 - 11:42 AM
 */

@Data
public class AccountDeletionOTPRequest {

    @NotNull @Min(1)
    private Integer customerId;

    @NotNull @Min(1)
    public Integer storeId;

    private Boolean debugMode;

}

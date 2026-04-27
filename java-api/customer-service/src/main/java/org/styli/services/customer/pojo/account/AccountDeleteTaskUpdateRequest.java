package org.styli.services.customer.pojo.account;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 16/06/2022 - 12:00 PM
 */

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AccountDeleteTaskUpdateRequest {

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    private String task;

    private boolean status = false;
}

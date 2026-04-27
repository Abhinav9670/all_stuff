package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
public class UpgradeShukranTierActivityRequest {


    @NotNull(message = "Shukran link flag should not be null or empty")
    private Boolean shukranTierUpdgradeFlag;
    @NotNull(message = "Customer Id should not be null or empty")
    private Integer customerId;
    private String customerEmail;
    @NotNull(message = "Store Id should not be null or empty")
    private Integer storeId;

}

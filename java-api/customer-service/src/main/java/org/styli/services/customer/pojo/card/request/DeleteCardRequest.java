package org.styli.services.customer.pojo.card.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class DeleteCardRequest {

    @NotNull
    @Min(1)
    public Integer customerId;

    @NotNull
    @Min(1)
    public Integer id;

}

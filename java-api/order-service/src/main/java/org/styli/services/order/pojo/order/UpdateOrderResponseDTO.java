package org.styli.services.order.pojo.order;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class UpdateOrderResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private String response;
    private ErrorType error;

    private boolean firstOrderByCustomer;
}

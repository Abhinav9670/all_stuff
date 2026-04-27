package org.styli.services.order.pojo.cancel;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author Umesh, 28/05/2020
 * @project product-service
 */

@Data
public class CancelOrderInitResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private CancelOrderInitResponse response;
    private ErrorType error;

}

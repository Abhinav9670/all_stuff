package org.styli.services.order.pojo.order;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author Umesh, 29/05/2020
 * @project product-service
 */

@Data
public class RMAOrderInitResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private RMAOrderInitResponse response;
    private ErrorType error;

}

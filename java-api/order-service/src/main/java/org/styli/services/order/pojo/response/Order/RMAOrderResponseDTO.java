package org.styli.services.order.pojo.response.Order;

import lombok.Data;

import java.util.Map;

import org.styli.services.order.pojo.ErrorType;

/**
 * @author Umesh, 27/05/2020
 * @project product-service
 */

@Data
public class RMAOrderResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private Map<Integer, RMAOrderResponse> response;
    private Long totalCount;
    private ErrorType error;

}

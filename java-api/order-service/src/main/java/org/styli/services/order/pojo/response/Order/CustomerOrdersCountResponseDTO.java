package org.styli.services.order.pojo.response.Order;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author Umesh, 27/05/2020
 * @project product-service
 */

@Data
public class CustomerOrdersCountResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private OrderCountResponse response;
    private Long totalCount;
    private ErrorType error;
}

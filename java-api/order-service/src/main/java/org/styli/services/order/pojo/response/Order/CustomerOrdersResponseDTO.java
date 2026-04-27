package org.styli.services.order.pojo.response.Order;

import java.util.List;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
//@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CustomerOrdersResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private Long totalCount;
    private Integer totalPageSize;
    private List<OrderResponse> response;
    private List<ReferalOrderData> orderDetails;
    private ErrorType error;
}

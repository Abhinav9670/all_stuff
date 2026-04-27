package org.styli.services.order.pojo.response.Order;

import java.util.List;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class OrderResponseDTO {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private OrderResponse response;
    private List<OrderDetails> responseList;
    private ErrorType error;
    private boolean refund = false;
}

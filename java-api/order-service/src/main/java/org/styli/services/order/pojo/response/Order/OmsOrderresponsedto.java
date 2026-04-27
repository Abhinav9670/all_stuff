package org.styli.services.order.pojo.response.Order;

import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;

import lombok.Data;



@Data
public class OmsOrderresponsedto {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private OrdersDetailsResponsedto response;
    private ErrorType error;
}

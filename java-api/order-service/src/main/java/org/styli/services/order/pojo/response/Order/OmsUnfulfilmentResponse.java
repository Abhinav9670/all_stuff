package org.styli.services.order.pojo.response.Order;

import org.springframework.stereotype.Component;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;

import lombok.Data;



@Data
@Component
public class OmsUnfulfilmentResponse {
    private Boolean hasError = false;
    private String errorMessage="";
    
    private String totalCodCancelledAmount;
}

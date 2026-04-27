package org.styli.services.order.pojo.response;

import org.springframework.stereotype.Component;
import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
@Component
public class OmsOrderupdateresponse {

	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private UpdateOrderResponse response;
    private Integer orderId;
    private ErrorType error;
}

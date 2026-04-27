package org.styli.services.order.pojo.recreate;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;

@Data
public class RecreateOrderResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private RecreateOrderResponse response;
    private ErrorType error;
}

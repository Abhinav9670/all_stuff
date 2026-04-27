package org.styli.services.order.pojo.response.Order;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;

import java.util.Map;

@Data
public class ReturnItemViewResponseDTO {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private RMAItem itemData;
    private ErrorType error;
}

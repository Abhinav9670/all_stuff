package org.styli.services.order.pojo.order;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
public class RMAOrderInitV2ResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private RMAOrderInitV2Response response;
    private ErrorType error;

}

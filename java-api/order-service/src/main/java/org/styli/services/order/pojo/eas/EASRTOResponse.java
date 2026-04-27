package org.styli.services.order.pojo.eas;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
public class EASRTOResponse {

	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ErrorType error;
}

package org.styli.services.customer.pojo;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

@Data
public class GetLocationGoogleMapsResponse {

	private GetLocationGoogleMaps response;
	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ErrorType error;
}
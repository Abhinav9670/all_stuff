package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;

import org.styli.services.customer.utility.pojo.ErrorType;

import lombok.Data;

@Data
public class StoreDetailsResponseDTO implements Serializable{

    private static final long serialVersionUID = -4630730960478525875L;
	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private StoreDetailsResponse response;
    private ErrorType error;

}

package org.styli.services.order.db.product.pojo;

import java.io.Serializable;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class StoreDetailsResponseDTO implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private StoreDetailsResponse response;
    private ErrorType error;

}
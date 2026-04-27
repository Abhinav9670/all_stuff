package org.styli.services.customer.pojo;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class StoreDetailsResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private StoreDetailsResponse response;
    private ErrorType error;

}

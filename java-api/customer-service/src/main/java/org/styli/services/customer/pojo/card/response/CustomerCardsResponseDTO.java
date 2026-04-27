package org.styli.services.customer.pojo.card.response;

import lombok.Data;

import java.util.List;

import org.styli.services.customer.pojo.registration.response.ErrorType;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CustomerCardsResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private List<CustomerCard> response;
    private ErrorType error;

}

package org.styli.services.order.pojo.response;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CustomerStoreCreditResponse {

    private boolean status;

    private String statusCode;

    private String statusMsg;

    private CustomerStoreCredit response;

    private ErrorType error;
    
    private Integer customerId;

}

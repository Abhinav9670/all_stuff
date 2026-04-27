package org.styli.services.order.pojo.response;

import java.util.List;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CustomerStoreCreditResponseList {

    private boolean status;

    private String statusCode;

    private String statusMsg;

    private List<CustomerStoreCredit> response;

    private ErrorType error;
    

}

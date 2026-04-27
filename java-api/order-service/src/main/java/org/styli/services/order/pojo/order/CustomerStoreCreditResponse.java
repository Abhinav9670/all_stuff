package org.styli.services.order.pojo.order;

import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.response.CustomerStoreCredit;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.response.CustomerStoreCredit;

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

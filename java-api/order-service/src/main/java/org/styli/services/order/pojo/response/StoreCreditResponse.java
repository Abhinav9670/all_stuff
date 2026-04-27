package org.styli.services.order.pojo.response;

import lombok.Data;
import lombok.ToString;

import org.styli.services.order.pojo.order.StoreCredit;

/**
 * Created on 24-May-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@ToString
@Data
public class StoreCreditResponse {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private Integer customerId;
    private String referenceNumber;
    private Integer rowNumber;
    private String emailId;
    private StoreCredit actualRequest;
}

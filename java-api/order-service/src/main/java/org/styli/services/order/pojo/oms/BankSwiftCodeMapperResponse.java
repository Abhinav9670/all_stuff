package org.styli.services.order.pojo.oms;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;

import java.util.List;
import java.util.Map;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 04/01/2022 - 4:08 PM
 */

@Data
public class BankSwiftCodeMapperResponse {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private Map<String, List<BankSwiftCode>> response;
    private ErrorType error;

}

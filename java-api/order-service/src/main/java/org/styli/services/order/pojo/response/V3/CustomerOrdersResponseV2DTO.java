package org.styli.services.order.pojo.response.V3;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
//@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CustomerOrdersResponseV2DTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private Long totalCount;
    private Integer totalPageSize;
    private CustomerOrderListResponse response;
    private ErrorType error;
}

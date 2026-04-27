package org.styli.services.order.pojo.cancel;

import lombok.Data;

/**
 * @author Umesh, 02/06/2020
 * @project product-service
 */

@Data
public class MagentoAPIResponse {

    private Boolean status;
    private Integer statusCode;
    private String statusMsg;
}

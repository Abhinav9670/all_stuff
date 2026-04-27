package org.styli.services.order.model.rma;

import lombok.Data;

/**
 * @author Umesh, 02/06/2020
 * @project product-service
 */

@Data
public class MagentoReturnDropOffAPIResponse {

    private Boolean status;
    private Integer statusCode;
    private String statusMsg;
    private String waybill;
    private String label;


}

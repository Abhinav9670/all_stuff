package org.styli.services.order.pojo.quote.response;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * @author Umesh, 28/09/2020
 * @project product-service
 */

@Data
public class QuoteUpdateDTOV2 {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private String quoteId;
    private Integer customerId;
    private ErrorType error;

}

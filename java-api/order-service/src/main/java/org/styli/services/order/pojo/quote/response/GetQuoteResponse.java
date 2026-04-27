package org.styli.services.order.pojo.quote.response;

import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.QuoteDTO;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class GetQuoteResponse {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private QuoteDTO response;
    private ErrorType error;
    private String tabbyPaymentId;
}

package org.styli.services.order.pojo.quote.response;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.QuoteV7DTO;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class QuoteV7Response {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private QuoteV7DTO response;
    private ErrorType error;
    private String tabbyPaymentId;
}

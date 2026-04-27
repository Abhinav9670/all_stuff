package org.styli.services.order.pojo.response.Order;

import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;

import lombok.Data;
import org.styli.services.order.pojo.quote.response.QuoteV7Response;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CreateOrderResponseDTO {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private CreateOrderResponse response;
    private ErrorType error;
    private GetQuoteResponse quoteResponse;  // v6 quote response
    private QuoteV7Response quoteV7Response; // v7 quote response
    private boolean codOrder;
}

package org.styli.services.order.pojo.response.V3;

import lombok.Data;
import java.util.List;

@Data
public class GetShipmentV3Response {

    private String awbNumber;
    private String shippingLabelUrl;
    private String transporter;
    
    private boolean hasError;
    
    private String errorMessage="";
    private Boolean status;
    private String statusCode;
    
    private List<BoxLabelDetails> boxLabelDetails;

}
package org.styli.services.order.pojo.order;

import lombok.Data;

@Data
public class OmsRtoCodResponse {
    private boolean status;
    private String statusCode;
    private String statusMsg;
}

package org.styli.services.order.pojo.response.V3;

import lombok.Data;

@Data
public class GetShipmentV3ResponseBody {
    private String awbNumber;
    private String invoiceUrl;
    private String shippingLabelUrl;
    private String transporter;
}

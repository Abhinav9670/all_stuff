package org.styli.services.order.pojo.response.V3;

import lombok.Data;

@Data
public class Result {
    private Boolean status;
    private String waybill;
    private String reference_number;
    private Integer courier_partner_id;
    private String courier_name;
    private String security_key;
    private String sort_code;
    private String label;
    private String alphaAwb;
    
}

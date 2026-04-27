package org.styli.services.order.pojo.request.Order;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

@Data
public class TrackingRequest {
    
    public String waybill;
    public String increment_id;

}

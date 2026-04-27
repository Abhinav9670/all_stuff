package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.styli.services.order.pojo.order.SkuItem;

import java.time.Instant;
import java.util.List;

/**
 * Express shipment details for split order information
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpressShipment {
    
    private Integer splitOrderId;
    
    private String splitOrderIncrementId;

    private List<SkuItem> skus;
    
    private Instant createdate;
}

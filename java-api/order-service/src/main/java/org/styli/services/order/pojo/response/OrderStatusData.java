package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Order status data containing all order information
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderStatusData {
    
    @JsonProperty("_id")
    private String id;
    
    private List<StatusMessage> statusMessage;
    
    private Integer orderid;
    
    private Integer parentOrderId;
    
    private String incrementId;
    
    private String shipmentType;
    
    private Integer customerId;
    
    private String quoteId;
    
    private String customerEmail;
    
    @JsonProperty("express")
    private ExpressShipment express;
    
    @JsonProperty("global")
    private ExpressShipment global;
    
    private Instant updatedAt;
}

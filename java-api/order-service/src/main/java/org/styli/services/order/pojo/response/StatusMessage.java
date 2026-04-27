package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

/**
 * Status message representing order status updates with timestamp
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusMessage {
    
    private String statusId;
    
    private String message;
    
    private Instant timestamp;
}

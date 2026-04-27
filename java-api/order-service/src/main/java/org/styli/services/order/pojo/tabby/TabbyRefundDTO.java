package org.styli.services.order.pojo.tabby;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Tabby Refund Process payload
 * @author Chandan Behera
 *
 */
@Data
public class TabbyRefundDTO implements Serializable{
	
	private static final long serialVersionUID = 1L;

	private Integer orderId;
	
	private String amount;
    
    private String reason;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    private List<TabbyItems> items = new ArrayList<>();
}

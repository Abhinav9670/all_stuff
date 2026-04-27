package org.styli.services.order.pojo.response;

import java.util.List;

import org.springframework.stereotype.Component;
import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
@Component
public class OmsOrderoutboundresponse {

	private Boolean status;
    private String statusCode;
    private String statusMsg;
    private OrdershipmentResponse response;
    private Integer orderId;
    private ErrorType error;
	
	private String shipmentCode;
	
	private List<shipmentItem> shipmentItems;
	
	 private Boolean hasError = false;
	 private String errorMessage="";
	
}

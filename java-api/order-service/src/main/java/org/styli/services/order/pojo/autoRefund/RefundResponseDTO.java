package org.styli.services.order.pojo.autoRefund;

import java.util.List;

import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
public class RefundResponseDTO {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private Long totalCount;
	private Integer totalPageSize;
	private List<RtoOrderResponse> response;
	private ErrorType error;

}

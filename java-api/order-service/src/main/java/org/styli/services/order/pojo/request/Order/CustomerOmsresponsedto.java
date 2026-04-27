package org.styli.services.order.pojo.request.Order;

import java.util.List;

import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.response.Customer;

import lombok.Data;

@Data
public class CustomerOmsresponsedto {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private int totalCount;
	private Integer totalPageSize;
	private List<Customer> response;
	private ErrorType error;

}

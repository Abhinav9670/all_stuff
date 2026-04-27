package org.styli.services.order.pojo.response.Order;

import java.io.Serializable;
import java.util.List;

import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.request.Order.OrderStoreCredit;

import lombok.Data;

@Data
public class CreditHistoryResponse implements Serializable {

	/**
	* 
	*/
	private static final long serialVersionUID = -7260836215264725571L;
	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private List<OrderStoreCredit> response;
	private String returnableAmount;
	private String totalAmount;
	private ErrorType error;
}

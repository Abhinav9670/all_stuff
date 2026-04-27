package org.styli.services.order.pojo.response;

import java.util.List;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
public class BankCouponsresponse {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private List<BankCouponsresponseBody> response;
	private ErrorType error;
}

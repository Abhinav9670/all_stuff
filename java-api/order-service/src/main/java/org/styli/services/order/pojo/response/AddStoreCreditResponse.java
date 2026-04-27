package org.styli.services.order.pojo.response;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@ToString
@Data
public class AddStoreCreditResponse {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private List<StoreCreditResponse> response;
	private ErrorType error;
}

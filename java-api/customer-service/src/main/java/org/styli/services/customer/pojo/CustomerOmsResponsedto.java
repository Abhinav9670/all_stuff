package org.styli.services.customer.pojo;

import java.util.List;

import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.response.CustomerOmsResponse;

import lombok.Data;

@Component
@Data
public class CustomerOmsResponsedto {

	
	 private Boolean status;
	    private String statusCode;
	    private String statusMsg;
	    private int totalCount;
	    private Integer totalPageSize;
	    private List<CustomerOmsResponse> response;
	    private ErrorType error;
}

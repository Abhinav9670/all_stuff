package org.styli.services.order.pojo.response.external;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ReferralOrderListResponse implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5852358903012614341L;

	@JsonProperty("status_code")
	private Integer code;

	
	private Boolean status;

	private String message;

}

package org.styli.services.order.pojo.request;

import lombok.Data;

@Data
public class RequestBody {

	
	private String incrementId;
	
	private String type;
	
	private String template;
	
	private String rmaId;
	
	private String codPartialCancelAmount;
	
	private String cpId;
}

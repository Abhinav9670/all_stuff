package org.styli.services.order.pojo.eas;

import lombok.Data;

@Data
public class EASCoinUpdateResponse {

	 private Integer status;
	 
	 private String message;
	 
	 private StyliCoinUpdate response;
}

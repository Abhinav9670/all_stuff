package org.styli.services.customer.utility.pojo.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdrsmprResponse {

	private boolean status;
	
	private String statusCode;
	
	private SearchCityResponse response;
}

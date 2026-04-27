package org.styli.services.customer.pojo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class BigQuerySyncRequest {

	private boolean completeSync;
	
	private boolean status;
	
	private String message;
}

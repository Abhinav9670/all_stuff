package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class FirstFreeShipping {
	
		private String numOfDays;
		
		@JsonProperty("isActive")
		private boolean isActive;
		
		private String expireOn;
}


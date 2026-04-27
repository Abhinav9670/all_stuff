package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class FirstFreeShipping {

	private String numOfDays;
	
	@JsonProperty("isActive")
	private boolean isActive ;
}

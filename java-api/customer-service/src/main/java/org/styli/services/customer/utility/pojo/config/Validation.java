package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class Validation implements Serializable{

	private static final long serialVersionUID = -8941727821241133181L;
	private Boolean zeroInitialIndex = false; 
	private List<String> regex;
}

package org.styli.services.customer.utility.pojo.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class Environments implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3422937313904494229L;
	private String type;
	private String baseurl;
	private String apiurl;
	private List<Stores> stores;
}
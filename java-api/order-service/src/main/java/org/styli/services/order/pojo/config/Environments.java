package org.styli.services.order.pojo.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

import org.styli.services.order.db.product.pojo.Stores;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
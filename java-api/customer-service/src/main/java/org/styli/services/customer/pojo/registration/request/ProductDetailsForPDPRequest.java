package org.styli.services.customer.pojo.registration.request;

import java.io.Serializable;
import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ProductDetailsForPDPRequest implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private List<Integer> productIds;

	@NotNull
	private Integer storeId;

	private Boolean isBagRequest = false;

}

package org.styli.services.customer.pojo.registration.response.Product;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class ProductStatusResBody implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5643511087494469556L;

	private List<ProductValue> productStatus;

}

package org.styli.services.customer.pojo.registration.response.Product;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ProductValue implements Comparable<ProductValue>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 164504941562174856L;

	private String procuctId;

	private String sku;

	private String value;
	
	private boolean isAllSkuNonZero=false;

	public ProductValue(String id, String value, String sku) {

		this.procuctId = id;
		this.value = value;
		this.sku = sku;
	}

	@Override
	public int compareTo(ProductValue object) {
		return procuctId.compareTo(object.procuctId);
	}
}

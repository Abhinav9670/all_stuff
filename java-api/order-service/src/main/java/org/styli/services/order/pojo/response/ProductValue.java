package org.styli.services.order.pojo.response;

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

	private Integer procuctId;

	private String sku;

	private String value;

    private String warehouseId;

	public ProductValue(Integer id, String value, String sku) {

		this.procuctId = id;
		this.value = value;
		this.sku = sku;
	}

    public ProductValue(Integer id, String value, String sku, String warehouseId) {

        this.procuctId = id;
        this.value = value;
        this.sku = sku;
        this.warehouseId = warehouseId;
    }

	@Override
	public int compareTo(ProductValue object) {
		return procuctId.compareTo(object.procuctId);
	}
}

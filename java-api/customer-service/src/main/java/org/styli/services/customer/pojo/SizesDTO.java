package org.styli.services.customer.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class SizesDTO implements Serializable {

	private static final long serialVersionUID = 7152269042201489249L;
	private Integer sizeOptionId;

	private String label;

	private Integer productId;

	private String sku;

	private Integer price;

	private Integer specialPrice;

	private Integer quantity;
}

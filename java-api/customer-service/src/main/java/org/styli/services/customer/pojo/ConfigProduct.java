package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigProduct {

	private Integer productId;

	private String productName;

	private Boolean isVisiable;

	private String sku;

	private Integer price;

	private Integer specialPrice;

	private Integer quantity = 0;

	private Integer discount;

	private String size;

	private Integer sizeOrder;

	private Integer sizeOptionId;

	private Boolean productStatus;
}

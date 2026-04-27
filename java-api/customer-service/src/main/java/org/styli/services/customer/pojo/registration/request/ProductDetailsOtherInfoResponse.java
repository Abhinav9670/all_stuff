package org.styli.services.customer.pojo.registration.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.styli.services.customer.pojo.ConfigProduct;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailsOtherInfoResponse {

	private Integer productId;

	private String productName;

	private String sku;

	private String productType;

	private Integer storeId;

	private Boolean isReturnApplicable = false;

	private Integer price;

	private Integer specialPrice;

	private String superConfigAttributeName;

	private String imageUrl;

	private String brandName;

	private List<ConfigProduct> configProducts;

	private List<String> images;

	private Boolean productStatus;

}

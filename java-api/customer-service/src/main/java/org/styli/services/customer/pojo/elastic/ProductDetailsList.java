package org.styli.services.customer.pojo.elastic;

import java.io.Serializable;
import java.util.List;

import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductDetailsList implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("productDetailsList")
	private List<ProductDetailsResponseV4DTO> productDetails;

}

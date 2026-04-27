package org.styli.services.customer.pojo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.styli.services.customer.pojo.registration.response.Product.KeyValuePair;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CatalogProductEntityDTO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7061586981516522030L;

	private String id;

	private String categoryId;

	private String sku;

	private String name;

	private String productType;

	private PriceDetails prices;

	private ImageDetails images;

	private String currency;

	private String weight;

	private String size;

	private String quantity;

	private String colour; // swatch value storing here

	private String colourName;

	private Boolean isVisibleProduct = true;

	@JsonIgnore
	private Boolean notEnable = true;

	private String quantityStockStatus;

	private String taxClass;

	private Integer discount = 0;

	private String productDescription;

	@JsonIgnore
	private Integer price;

	// @JsonIgnore
	private Integer sortPrice = 0;

	@JsonIgnore
	private Integer specialPrice;

	private Integer sizeOptionId;

	private String sizeAndFit;

	private String sizeName;

	private String brand;

	private Integer wishListItemId;

	private String brandAndProductName;

	private String url;

	private List<SizesDTO> configSizes;

	private Integer sizeOrder;

	private List<CatalogProductEntityDTO> configProducts;

	private Map<String, List<KeyValuePair>> productFilterAttributes;

	private Boolean isReturnApplicable = false;

	private String superConfigAttributeName;

}

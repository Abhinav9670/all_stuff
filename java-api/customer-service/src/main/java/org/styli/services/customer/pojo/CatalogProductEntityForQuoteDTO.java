package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode
public class CatalogProductEntityForQuoteDTO {

	private String parentQuoteItemId;

	private String quoteItemId;

	private String parentProductId;

	private String productId;

	private String parentSku;

	private String sku;

	private String name;

	private String brandName;

	private PriceDetails prices;

	private ImageDetails images;

	private String size;

	private String quantity;

	@JsonIgnore
	private Boolean notEnable = true;

	private String quantityStock;

	private String discount;

	private String taxAmount;

	private String taxPercent;

	// from quote totals call from magento
	private String rowTotal;

	private String rowTotalWithDiscount;

	private String discountAmount;

	private String discountPercent;

	private String priceInclTax;

	private String rowTotalInclTax;

	private List<SizesDTO> sizes;

	private String price;

	private String discountTaxCompensationAmount;

}

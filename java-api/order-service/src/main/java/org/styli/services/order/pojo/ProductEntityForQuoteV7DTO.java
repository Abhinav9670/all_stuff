package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.styli.services.order.pojo.response.ImageDetails;
import org.styli.services.order.pojo.response.PriceDetails;
import org.styli.services.order.pojo.tax.TaxObject;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode
public class ProductEntityForQuoteV7DTO {

	private String parentQuoteItemId;

	private String quoteItemId;

	private String parentProductId;

	private String productId;

	private String parentSku;

	private String sku;

    private String variantSku;

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

	private String soldBy;

	private Boolean productStatus = true;

	private String superAttributeId;
	private String superAttributeValue;
	private String superAttributeLabel;
	
	private List<AppliedCouponValue> appliedCouponValue;

	private Boolean isMulin;
	
	private boolean isReturnApplicable = false;
	
	private String landedCost;
	
	@JsonProperty("isGift")
	private boolean isGift;
	
	private TaxObject taxObj;
	
	private String hsnCode;

	@JsonProperty("isReturnable")
	private boolean returnable;

	// shukran keys
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranEarn;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranBurn;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranBurnInCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranBurnInBaseCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranEarnInCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranEarnInBaseCurrency;
	@JsonProperty("l4_category")
	private String l4Category;
	private Boolean isSale=false;

	// Split order fields
	private String sellerId;
	private String sellerName;
	private String warehouseId;
	private String fulfillmentMode;
	private String fulfillmentType;
	private String countryCode;
	private String deliveryType;
	private String firstMileLocationId;
	private String midMileLocationId;
	private String lastmileWarehouseId;
	private String estimatedDate;
	private String minEstimatedDate;
	private String maxEstimatedDate;
	private String firstMileLocationName;
	private String midMileLocationName;
	private String lastmileWarehouseName;
	//SFP-657 Dangerous Goods values
	private Boolean isDangerousProduct = false;
	private String shortDescription;
    private String poPrice;
}

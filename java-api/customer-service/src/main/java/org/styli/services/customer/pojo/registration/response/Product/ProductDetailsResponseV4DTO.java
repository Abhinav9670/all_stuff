package org.styli.services.customer.pojo.registration.response.Product;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.styli.services.customer.pojo.ImageDetails;
import org.styli.services.customer.pojo.PriceDetails;
import org.styli.services.customer.pojo.SizesDTO;
import org.styli.services.customer.pojo.response.ProductInfoValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProductDetailsResponseV4DTO implements Serializable {

    private static final long serialVersionUID = -8169282442142029315L;
    private String id;

    private String sku;

    private String name;

    private String productType;

    private PriceDetails prices;

    private ImageDetails images;

    private String currency;

    private String weight;

    private String size;

    private String quantity="0";

    private String colour; // swatch value storing here

    private String colourName;

    private Boolean isVisibleProduct;

    @JsonIgnore
    private Boolean notEnable = true;

    private String quantityStockStatus;

    private String taxClass;

    private Integer discount;

    private String productDescription;

    private Integer sizeOptionId;

    private String sizeAndFit;

    private String sizeName;

    private String brand;

    private String wishListItemId;

    private String shipmentMode;

    private List<WarehouseDetails> warehouse_details;

    private String brandAndProductName;

    private String url;

    private List<SizesDTO> configSizes;

    private List<ProductDetailsResponseV4DTO> configProducts;

    private Map<String, List<KeyValuePair>> productFilterAttributes;

    private Categories categories;

    private String promoBadgeText;

    private String promoBgColor;

    private String promoTextColor;

    private Boolean returnCategoryRestriction;

    private String metaTitle;

    private String metaKeyword;

    private String metaDescription;

    private String source;

    private String priceSource;

    private Boolean isFlashSale;

    private FlashSaleV4DTO flashSale;
    
    private boolean outOfStock = false;
    
    private  Map<String,ProductInfoValue>  productInfos;

    private double wishlistPriceDrop;

    private boolean isGiftProduct = false;

    private Integer quantityAvailable;
}

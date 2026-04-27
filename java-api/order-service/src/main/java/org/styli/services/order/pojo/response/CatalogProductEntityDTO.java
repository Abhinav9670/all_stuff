package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.styli.services.order.pojo.KeyValuePair;
import org.styli.services.order.pojo.SizesDTO;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class CatalogProductEntityDTO implements Serializable {

    private static final long serialVersionUID = 8830778005811113747L;

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

    private String colour; //swatch value storing here

    private String colourName;

    private Boolean isVisibleProduct = true;

    @JsonIgnore
    private Boolean notEnable = true;

    private String quantityStockStatus;

    private String taxClass;


    private Integer discount = 0;

    private String productDescription;

    @JsonIgnore
    private Double price;

    //@JsonIgnore
    private Double sortPrice=0.0;

    @JsonIgnore
    private Double specialPrice;

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

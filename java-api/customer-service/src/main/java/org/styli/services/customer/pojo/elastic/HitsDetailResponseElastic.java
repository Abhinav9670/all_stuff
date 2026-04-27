package org.styli.services.customer.pojo.elastic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.styli.services.customer.pojo.registration.response.Product.Categories;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class HitsDetailResponseElastic implements Serializable {

    private static final long serialVersionUID = 3797888311032375799L;
    private String objectID;

    private String name;

    @JsonProperty("in_stock")
    private String inStock;

    private List<String> sku;

    private List<String> color;

    @JsonProperty("brand_name")
    private String brandName;

    @JsonProperty("meta_title")
    private String metaTitle;

    @JsonProperty("meta_description")
    private String metaDescription;

    @JsonProperty("sold_by")
    private String soldBy;

    @JsonProperty("promo_badge_text")
    private String promoBadge;

    @JsonProperty("promo_bg_color")
    private String promoBgColor;

    @JsonProperty("promo_text_color")
    private String promoTextColor;

    private String url;

    @JsonProperty("visibility_search")
    private String visibilitySearch;

    @JsonProperty("visibility_catalog")
    private String visibilityCatalog;

    @JsonProperty("type_id")
    private String typeId;

    @JsonProperty("ordered_qty")
    private String orderedQty;

    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("media_gallery")
    private List<String>  mediaGallery;

    @JsonProperty("discount_percentage")
    private Integer discountPercentage;

    @JsonProperty("flash_sale")
    private Boolean flashSale;

    private Map<String, PriceType> price;

    private Categories categories;

    @JsonProperty("categoryIds")
    private List<Integer> categoryIds;

    private String ranking;

    private Map<String, ProductAttributeElastic> productAttributeFilters;

    private List<ChildProductElastic> configProducts;

    private Boolean isDisabled;

    private Integer isReturnApplicable;
}

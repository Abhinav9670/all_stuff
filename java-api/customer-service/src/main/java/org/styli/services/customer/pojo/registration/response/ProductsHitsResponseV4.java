package org.styli.services.customer.pojo.registration.response;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.styli.services.customer.pojo.registration.response.Product.Categories;

import lombok.Data;

import javax.annotation.Generated;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Generated("com.robohorse.robopojogenerator")
public class ProductsHitsResponseV4 {

    Map<String, Object> attributes = new LinkedHashMap<>();

    @JsonAnySetter
    void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @JsonProperty("color")
    private List<String> color;

    @JsonProperty("images_data")
    private Object imagesData;

    @JsonProperty("algoliaLastUpdateAtCET")
    private String algoliaLastUpdateAtCET;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("discount_percentage")
    private Integer discountPercentage;

    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @JsonProperty("in_stock")
    private Integer inStock;

    @JsonProperty("visibility_catalog")
    private Integer visibilityCatalog;

    @JsonProperty("visibility_search")
    private Integer visibilitySearch;

    @JsonProperty("enabled_at")
    private String enabledAt;

    @JsonProperty("price")
    private Object price;

    @JsonProperty("ordered_qty")
    private Integer orderedQty;

    @JsonProperty("categories")
    private Categories categories;

    @JsonProperty("sku")
    private List<String> sku;

    @JsonProperty("type_id")
    private String typeId;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("rating_summary")
    private String ratingSummary;

    @JsonProperty("brand_name")
    private String brandName;

    @JsonProperty("url")
    private String url;

    @JsonProperty("categories_without_path")
    private List<String> categoriesWithoutPath;

    @JsonProperty("categoryIds")
    private List<String> categoryIds;

    // @JsonProperty("size")
    // private List<String> size;

    @JsonProperty("name")
    private String name;

    @JsonProperty("ranking")
    private Integer ranking;

    @JsonProperty("objectID")
    private String objectID;

    @JsonProperty("meta_title")
    private String metaTitle;

    @JsonProperty("meta_keyword")
    private String metaKeyword;

    @JsonProperty("meta_description")
    private String metaDescription;

    @JsonProperty("promo_badge_text")
    private String promoBadgeText;

}
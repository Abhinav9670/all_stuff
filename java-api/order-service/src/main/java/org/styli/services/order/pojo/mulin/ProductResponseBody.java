package org.styli.services.order.pojo.mulin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponseBody {

    private String _id;

    private String sku;

    @JsonProperty("is_return_applicable")
    private Boolean isReturnApplicable;

    private String sizeAttr;

    private List<Variant> variants;
    
    private Attribute attributes;

    @JsonProperty("media_gallery")
    private List<GalleryItem> mediaGallery;

    private Map<String, Object> productAttributes;
}

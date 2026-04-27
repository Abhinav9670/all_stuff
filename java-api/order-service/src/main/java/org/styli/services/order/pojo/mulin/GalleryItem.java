package org.styli.services.order.pojo.mulin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GalleryItem {
    private String value;

    @JsonProperty("media_type")
    private String mediaType;
}

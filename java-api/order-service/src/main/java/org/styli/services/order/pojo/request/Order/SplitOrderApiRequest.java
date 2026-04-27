package org.styli.services.order.pojo.request.Order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Split Order API calls containing parent order and split order IDs
 * 
 * @author API Helper
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitOrderApiRequest {

    @JsonProperty("parentOrderId")
    private Integer parentOrderId;

    @JsonProperty("splitOrderId")
    private Integer splitOrderId;
}

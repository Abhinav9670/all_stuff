package org.styli.services.order.pojo.response.Order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.styli.services.order.pojo.ErrorType;

/**
 * Response DTO for Split Order API calls
 * 
 * @author API Helper
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitOrderApiResponse {

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("statusCode")
    private String statusCode;

    @JsonProperty("statusMsg")
    private String statusMsg;

    @JsonProperty("error")
    private ErrorType error;

    @JsonProperty("data")
    private Object data;
}

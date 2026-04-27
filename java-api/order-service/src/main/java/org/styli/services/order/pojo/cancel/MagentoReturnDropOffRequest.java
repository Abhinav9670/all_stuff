package org.styli.services.order.pojo.cancel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Umesh, 02/06/2020
 * @project product-service
 */

@Data
public class MagentoReturnDropOffRequest {

    @JsonProperty("requestId")
    private Integer requestId;

  

}

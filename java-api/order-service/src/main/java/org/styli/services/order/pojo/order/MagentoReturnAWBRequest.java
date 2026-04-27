package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Umesh, 02/06/2020
 * @project product-service
 */

@Data
public class MagentoReturnAWBRequest {

    @JsonProperty("request_id")
    private Integer requestId;

}

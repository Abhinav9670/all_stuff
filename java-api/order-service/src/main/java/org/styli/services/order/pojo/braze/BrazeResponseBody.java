package org.styli.services.order.pojo.braze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 24/02/2022 - 1:35 PM
 */

@Data
public class BrazeResponseBody {

 @JsonProperty("events_processed")
 private Integer eventsProcessed;

 @JsonProperty("attributes_processed")
 private Integer attributesProcessed;

 private String message;
}

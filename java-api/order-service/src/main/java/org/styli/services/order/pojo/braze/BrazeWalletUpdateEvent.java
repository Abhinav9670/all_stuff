package org.styli.services.order.pojo.braze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 24/02/2022 - 10:17 AM
 */

@Data
public class BrazeWalletUpdateEvent {

    @JsonProperty("external_id")
    private String externalId;

    private String name;
    private String time;
    private BrazeWalletUpdateEventProperty properties;
}

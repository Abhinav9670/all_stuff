package org.styli.services.order.pojo.braze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 28/02/2022 - 10:55 AM
 */

@Data
public class BrazePushAttribute {

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("styli_credit")
    private BigDecimal styliCredit;

}

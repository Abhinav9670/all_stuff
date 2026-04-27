package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerShukranPoints {
    @JsonProperty("ProfileId")
    public String profileId;
    @JsonProperty("PointsBalance")
    public List<CustomerShukranPointsBalance> pointsBalance;
}

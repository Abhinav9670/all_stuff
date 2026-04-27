package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerShukranPointsBalance {
    @JsonProperty("PointAmount")
    public BigDecimal pointAmount;
    @JsonProperty("PointTypeShortDescription")
    public String pointTypeShortDescription;
    @JsonProperty("PointTypeId")
    public String pointTypeId;
    @JsonProperty("PointTypeCode")
    public String pointTypeCode;
}

package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.sql.Timestamp;
import java.util.Date;

@Data
public class RefundShukranBurnedBody {
    @JsonProperty("ProfileId")
    private String profileId;
    @JsonProperty("AdjustmentReasonCode")
    private String adjustmentReasonCode;
    @JsonProperty("AdjustmentComment")
    private String adjustmentComment;
    @JsonProperty("NumPoints")
    private Integer numPoints;
    @JsonProperty("ActivityDate")
    private String activityDate;
    @JsonProperty("JsonExternalData")
    private RefundShukranBurnedBodyData jsonExternalData;
}

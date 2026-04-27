package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response class for Order Tracking API
 * Contains tracking information including latest status and scan history
 * 
 * @author Order Service Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtsTrackingResponse {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("data")
    private OrderTrackingData data;

    /**
     * Inner class representing the tracking data
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderTrackingData {

        @JsonProperty("cp_name")
        private String cpName;

        @JsonProperty("reference_number")
        private String referenceNumber;

        @JsonProperty("waybill")
        private String waybill;

        @JsonProperty("latest_status")
        private TrackingStatus latestStatus;

        @JsonProperty("scans")
        private List<TrackingStatus> scans;

        @JsonProperty("is_rvp")
        private Boolean isRvp;
    }

    /**
     * Inner class representing tracking status information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackingStatus {

        @JsonProperty("statusId")
        private Integer statusId;

        @JsonProperty("message")
        private String message;

        @JsonProperty("timestamp")
        private String timestamp;
    }
}

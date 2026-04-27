package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MpsOrderCreateResponse {

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("data")
    private List<MpsOrderData> data;

    @JsonProperty("message")
    private String message;

    @JsonProperty("detail")
    private String detail;

    @Data
    public static class MpsOrderData {
        @JsonProperty("status")
        private Boolean status;

        @JsonProperty("alpha_awb")
        private String alphaAwb;

        @JsonProperty("cp_awb")
        private String cpAwb;

        @JsonProperty("url")
        private String url;

        @JsonProperty("message")
        private String message;

        @JsonProperty("error")
        private String error;

        @JsonProperty("label_data")
        private LabelData labelData;

        @JsonProperty("courier")
        private String courier;

        @JsonProperty("courierName")
        private String courierName;
    }

    @Data
    public static class LabelData {
        @JsonProperty("child_awb")
        private List<String> childAwb;
    }
}

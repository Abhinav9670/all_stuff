package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayfortPaymentCaptureStatusCheckRequest {

    @JsonProperty("query_command")
    private String queryCommand;

    @JsonProperty("access_code")
    private String accessCode;

    @JsonProperty("merchant_identifier")
    private String merchantIdentifier;

    @JsonProperty("merchant_reference")
    private String merchantReference;

    @JsonProperty("language")
    private String language;

    @JsonProperty("signature")
    private String signature;

}

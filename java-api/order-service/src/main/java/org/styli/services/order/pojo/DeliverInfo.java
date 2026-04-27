package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliverInfo {

    @JsonProperty("WH_NAME")
    private String customerName;

    @JsonProperty("WH_PHONE")
    private String customerPhone;

    @JsonProperty("WH_EMAIL")
    private String customerEmail;

    @JsonProperty("WH_ADDRESS")
    private String customerAddress;

    @JsonProperty("WH_CITY")
    private String customerCity;

    @JsonProperty("WH_POSTAL_CODE")
    private String customerPostalCode;
}

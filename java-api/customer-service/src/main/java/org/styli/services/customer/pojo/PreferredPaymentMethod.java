package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class PreferredPaymentMethod implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("storeId")
    private Integer storeId;

    @JsonProperty("method")
    private String method;
}

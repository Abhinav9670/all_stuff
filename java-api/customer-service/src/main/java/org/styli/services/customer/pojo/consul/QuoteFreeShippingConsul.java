package org.styli.services.customer.pojo.consul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteFreeShippingConsul {

    private FreeShipping freeShipping;

}

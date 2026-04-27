package org.styli.services.customer.pojo.consul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeShipping {

    private CountryEnabledDays sa;

    private CountryEnabledDays ae;

}

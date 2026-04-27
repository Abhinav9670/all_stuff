package org.styli.services.customer.utility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsulValues {

    private String inventoryBaseUrl;
    


}

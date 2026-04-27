package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LmdCommission {

    private String en;
    private String ar;
    private Double value;
    private Double ntdDiscount;
}

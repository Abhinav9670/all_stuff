package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class PaymentInformationCOD {

    @JsonProperty("method_title")
    public String methodTitle;

    @JsonProperty("instructions")
    public String instructions;

    @JsonProperty("customer_ip")
    private String customerIp;

}

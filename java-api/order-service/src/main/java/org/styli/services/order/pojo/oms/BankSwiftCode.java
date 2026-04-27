package org.styli.services.order.pojo.oms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 04/01/2022 - 4:11 PM
 */

@Data
public class BankSwiftCode {
    @JsonProperty("swift_code")
    private String swiftCode;

    @JsonProperty("en_name")
    private String enName;

    @JsonProperty("ar_name")
    private String arName;
}

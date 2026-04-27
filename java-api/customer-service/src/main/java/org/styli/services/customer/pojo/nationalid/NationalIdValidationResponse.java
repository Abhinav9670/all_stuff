package org.styli.services.customer.pojo.nationalid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for National ID / Passport document validation API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NationalIdValidationResponse {
    private Boolean status;
    private String errorCode;
    private String message;

    @JsonProperty("data")
    private NationalIdValidationData data;
}

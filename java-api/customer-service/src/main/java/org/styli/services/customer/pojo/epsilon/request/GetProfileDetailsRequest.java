package org.styli.services.customer.pojo.epsilon.request;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class GetProfileDetailsRequest implements Serializable {
    private String ProfileId;
    private String FieldName;
    private String Country;
    private String LookupValue;
}

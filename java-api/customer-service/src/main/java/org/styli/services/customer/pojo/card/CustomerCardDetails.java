package org.styli.services.customer.pojo.card;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerCardDetails {

    public String maskedCC;
    public String type;
    public String expirationDate;
    public String firstname;
    public String lastname;
    public String cardBin;
    public String storeId;

}

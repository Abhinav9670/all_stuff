package org.styli.services.customer.pojo.card.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Getter
@AllArgsConstructor
public enum PaymentMethodCodeENUM {
    MD_PAYFORT("md_payfort");

    public String value;

}

package org.styli.services.customer.pojo.card.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.sql.Timestamp;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CreateCardRequest {

    @NotNull
    @Min(1)
    public Integer customerId;

    @NotNull
    public String publicHash;

    @NotNull
    public PaymentMethodCodeENUM paymentMethodCode;

    @NotNull
    public TypeENUM type;

    @NotNull
    public Timestamp expiresAt;

    @NotNull
    public String cardToken;

    @NotNull
    public String cardMask;

    @NotNull
    public String cardType;

    @NotNull
    public String cardExp;

}

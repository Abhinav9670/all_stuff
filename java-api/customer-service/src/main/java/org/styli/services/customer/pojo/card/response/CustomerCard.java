package org.styli.services.customer.pojo.card.response;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CustomerCard {
    public Integer id;
    public Integer customerId;

    public String publicHash;
    public String paymentMethodCode;
    public String type;
    public String expiresAt;
    public String cardToken;

    public String cardMask;
    public String cardType;
    public String cardExp;

    private Integer active;
    private Integer visible;
    private String cardBin;
    private String storeId;
}

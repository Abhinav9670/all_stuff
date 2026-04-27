package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class PaymentInformationSavedCard {

    @JsonProperty("customer_id")
    public Integer customerId;

    @JsonProperty("public_hash")
    public String publicHash;

    @JsonProperty("method_title")
    public String methodTitle;

    @JsonProperty("customer_ip")
    public String customerIp;

    @JsonProperty("merchant_reference")
    public String merchantReference;

    @JsonProperty("authorization_code")
    public String authorizationCode;

    @JsonProperty("payment_option")
    public String paymentOption;

    @JsonProperty("card_number")
    public String cardNumber;

    @JsonProperty("amount")
    public BigDecimal amount;

}

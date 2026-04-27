package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class PaymentInformationNewCard {

    @JsonProperty("is_active_payment_token_enabler")
    public Boolean isActivePaymentTokenEnabler;

    @JsonProperty("token_name")
    public String tokenName;

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

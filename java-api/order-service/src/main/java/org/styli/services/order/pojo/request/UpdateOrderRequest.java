package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class UpdateOrderRequest {

    @NotNull @Min(1)
    public Integer orderId;

    @Min(1)
    public Integer customerId;

    public OrderStatusENUM status;

    public String message;

    public String publicHash;

    public Boolean isActivePaymentTokenEnabler;
    public String tokenName;

    public String customerIp;
    public String merchantReference;
    public String authorizationCode;
    public String paymentOption;
    public String cardNumber;
    public BigDecimal amount;

    @NotNull
    public String fortId;

}

package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ShukranTenderMapping {
    private String cashOnDelivery = "CASH";
    @JsonProperty("styli_wallet")
    private String styliWallet="STYLI_WALLET";
    @JsonProperty("payfort_cc")
    private String payfortCc= "CREDIT_CARD";
    @JsonProperty("apple_pay")
    private String applePay= "APPLE_PAY";
    private String tabby= "TABBY_INSTALLMENTS";
    @JsonProperty("tabby_pay_later")
    private String tabbyPayLater = "TABBY_PAYLATER";
    private String tamara= "TAMARA_INSTALLMENTS";
    @JsonProperty("shukran_payment")
    private String shukranPayment="POINTS";
}

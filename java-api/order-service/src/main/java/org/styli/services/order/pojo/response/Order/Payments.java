package org.styli.services.order.pojo.response.Order;

import lombok.Data;

@Data
public class Payments {
    private String paymentMethod;
    private String cardDetails;
    private String discountAmount;
    private String grandTotal;
    private String baseGrandTotal;
    private String shippingAmount;
    private String globalShippingAmount;
    private String codCharges;
    private String subtotal;
    private String currency;
    private String importFeesAmount;
    private String donationAmount;
    private int rmaCount;
    private String returnFee;
    private String coinToCurrency;
    private String coinToBaseCurrency;
    private boolean orderAlreadyExists;
    private boolean canRetryPayment;
    private String taxPercent;
}

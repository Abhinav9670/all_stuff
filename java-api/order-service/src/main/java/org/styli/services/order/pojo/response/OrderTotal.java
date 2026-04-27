package org.styli.services.order.pojo.response;

import java.math.BigDecimal;
import java.util.List;

import org.styli.services.order.pojo.DiscountData;

import lombok.Data;

@Data
public class OrderTotal {

	// shukran keys started
	private BigDecimal totalShukranBurnedPoints;
	private BigDecimal totalShukranBurnedValueInCurrency;
	private BigDecimal totalShukranBurnedValueInBaseCurrency;
	private Integer totalShukranBurnedPointsToShowInUI=0;
	private BigDecimal totalShukranBurnedValueInCurrencyInUI;
	private BigDecimal totalShukranBurnedValueInBaseCurrencyInUI;
	private BigDecimal totalShukranEarnedPoints;
	private BigDecimal totalShukranEarnedValueInCurrency;
	private BigDecimal totalShukranEarnedValueInBaseCurrency;
	private String shukranTierName;
	private String shukranPhoneNumber;
	// shukran keys ended

	private String discountAmount;
	
	private String couponDiscountAmount;
	
	private String grandTotal;
	
	private String baseGrandTotal;
	
	private String shippingAmount;
	
	private String codCharges;
	
	private String subtotal;

	private String subtotalInclTax;

	private String currency;
	
	private String importFeesAmount;
	
	private String baseImportFeesAmount;
	
	private String donationAmount;
	
	private String baseDonationAmount;
	
	private String baseDiscountAmount;
	
	private String baseCouponDiscountAmount;
	
	private String baseShippingAmount;
	
	private String baseCodCharges;
	
	private String baseSubtotal;

	private String baseSubtotalInclTax;

	private String baseCurrency;
	
	private String couponCode;
	
	private  List<DiscountData> discountData;
	
	private String StoreCreditAmount;
	
	private String baseStoreCreditAmount;
	
	private String taxAmount;
	
	private String totalPaid;
	
	private String baseTotalPaid;
	
	private String totalRefunded;

	private String invoicedAmount;

	private String baseInvoicedAmount;
	
	private String totalBaseRefunded;
	
	private String baseTaxAmount;
	
	// EAS coins in totals
	private int spendCoin = 0;
	
	private String coinToCurrency;
	
	private String coinToBaseCurrency;
	// EAS coins in totals
	
	// EAS refund coins
	private int refundedCoin = 0;
	
	private String refundedCoinToCurrency;
	
	private String refundedCoinToBaseCurrency;
	// EAS refund coins
	
	// EAS initial coins in totals
	private int initialSpendCoin = 0;
	
	private String initialCoinToCurrency;
	
	private String initialCoinToBaseCurrency;
	// EAS initial coins in totals
	
	private String codTaxCharges = "0";
	
	private String shippingTaxAmount = "0";
	
	private String giftVoucherAmount;
	
	private String refundedVoucherAmount;
	
	private String totalProductLevelDiscountExclTax;
	
	private String totalBaseProductLevelDiscountExclTax;
	
	private String totalPriceExclTax;
	
	private String totalBasePriceExclTax;
	
	private String totalCouponDiscountExclTax;
	
	private String totalBaseCouponDiscountExclTax;
	
	private BigDecimal taxFactor;
	
	private BigDecimal roundingAmount;
	
	private BigDecimal totalTaxableAmount = BigDecimal.ZERO;
	
	private BigDecimal baseTotalTaxableAmount = BigDecimal.ZERO;

	private String globalShippingAmount;

}

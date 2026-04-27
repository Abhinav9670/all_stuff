package org.styli.services.order.pojo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.styli.services.order.pojo.eas.EASQuoteSpend;

import lombok.Data;

/**
 * 
 * @author umeshkumarmahato <umesh.mahato@landmarkgroup.com>
 *
 */
@Data
public class QuoteDTO {

	private String storeId;
	private String quoteId;

	private String customerId;
	private String customerEmail;
	private String customerFirstname;
	private String customerLastname;
	private String customerDob;
	private String customerIsGuest;


	private String itemsCount;
	private String itemsQty;

	// [Sum for all products (Qty * Offer Price)]
	private String subtotal;
	private String subtotalInclTax;
	private String subtotalExclTax;
	private String subtotalWithDiscount;

//	private String autoPromoCode;
//	private BigDecimal autoPromoAmount;

	// After discount/promos/taxes/shipping charges
	private String grandTotal;

	// Added later
	private String baseGrandTotal;

	// [Sum for all products (Qty * Base Price)]
	private String total;

	// private String taxAmount;
	// Styli Discount (Sum of (Base Price - Offer Price))
	private String discount;
	// Coupon Code Discount
	private String couponDiscount;
	private String shippingThreshold;
	private String shippingMethod;
	private Boolean shippingWaived;
	private String shippingDescription;
	private String shippingAmount;
	private String shippingInclTax;
	private String selectedAddressId;
	private String selectedPaymentMethod;
	private Boolean isCouponApplicable;
	private String couponCodeApplied;
	private Integer couponSourceExternal;
	private String currency;
	private String storeCreditBalance;
	private String storeCreditApplied;

	private String autoCouponDiscount;
	private String autoCouponApplied;

	private String taxAmount;

	private String codCharges;

	private List<CatalogProductEntityForQuoteDTO> products;

	private String currencyConversionRate;

	private String converter;

	private AddressObject shippingAddress;
	
	private String importFeesAmount;
	
	private List<DiscountData> discountData;
	
	private Boolean isSuspectedCustomer;	
	private Boolean isWhitelistedCustomer;
	private String donationAmount;
	
	private int otpFlag;
	private PaymentRestriction paymentRestriction;
	
	private String tabbyPaymentId;
	
	private FirstFreeShipping firstFreeShipping;

	// EAS_CHANGES quote data for spend key for spend data
	private EASQuoteSpend coinDiscountData;
	
	private String bnplSessionAmount;
	
	private boolean retryPayment;

	private boolean freeOrder;
	
	private List<String> failedPaymentMethod = new ArrayList<>();

	// Shukran Keys
	private String profileId;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal totalShukranEarn;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal totalShukranBurn;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal totalShukranBurnValueInCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal totalShukranBurnValueInBaseCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal totalShukranEarnValueInCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal totalShukranEarnValueInBaseCurrency;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranAvailablePoint;
    private Boolean shukranLinkFlag=false;
	private Boolean isQualifyingPurchase=false;
	private String isCrossBorderFlag="N";
	private String shukranCardNumber;
	private String tierName;
	private Boolean isAvailableShukranChanged=false;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranBasicEarnPoint;
	@JsonDeserialize(using = BigDecimalDeserializer.class)
	private BigDecimal shukranBonousEarnPoint;
	private String customerPhoneNumber;
	private Integer isSplitOrder = 0;
	private Boolean isClubShipment=false;
}

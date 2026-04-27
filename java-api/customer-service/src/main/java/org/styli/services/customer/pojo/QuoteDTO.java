package org.styli.services.customer.pojo;

import java.util.List;

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

	private String itemsCount;
	private String itemsQty;

	// [Sum for all products (Qty * Offer Price)]
	private String subtotal;
	private String subtotalInclTax;
	private String subtotalExclTax;
	private String subtotalWithDiscount;

	// After discount/promos/taxes/shipping charges
	private String grandTotal;

	// Added later
	private String baseGrandTotal;

	// [Sum for all products (Qty * Base Price)]
	private String total;

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

	private String taxAmount;

	private String codCharges;

	private List<CatalogProductEntityForQuoteDTO> products;

	private String converter;

}

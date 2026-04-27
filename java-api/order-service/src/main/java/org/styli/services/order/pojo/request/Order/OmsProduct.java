package org.styli.services.order.pojo.request.Order;

import java.util.List;

import lombok.Data;
import org.styli.services.order.model.SalesOrder.SellerOrderInfo;
import org.styli.services.order.utility.OrderConstants;

@Data
public class OmsProduct {
	private String splitOrderIncrementId;

	private Integer splitOrderId;

	private Integer parentOrderItemId;

	private String parentProductId;

	private String name;
	
	private String nameAr;

	private String sku;

	private String price;

	private String originalPrice;

	private String discount;

	private String qty;

	private String qtyCanceled;

	private String qtyReturned;

	private String qtyReturnedInProcess;

	private String qtyInvoiced;

	private String qtyShipped;

	private String subtotal;

	private String taxAmount;

	private String taxPercent;

	private String rowTotal;

	private String rowTotalInclTax;

	private String finalPrice;

	private String baseFinalPrice;

	private String size;

	private String image;

	private String returnCategoryRestriction;

	private String availableNow;

	private String basePrice;

	private String baseOriginalPrice;

	private String baseDiscount;

	private String baseSubtotal;

	private String baseTaxAmount;

	private String baseRowTotal;

	private String baseRowTotalInclTax;

	private String taxPercentage;

	private String actualPrice;

	private boolean giftProduct;
	
	private String giftVoucherAmount;
	
	private String hsnCode;
	
	private List<OmsProductTax> taxObjects;
	
	private String unitPriceExclTax;
	
	private String subTotalExclTax;
	
	private String totalDiscountExclTaxProduct;
	
	private String taxablePriceProduct;
	
	private String totalTaxAmountProduct;
	
	private String totalPriceInclTaxProduct;

	private String brandName;

	private List<OrderHistory> histories;

	private Integer shipmentId;

	private String shipmentIncrementId;

	private String type = OrderConstants.PRODUCT_TYPE_LOCAL;

	private TrackingDetails  trackingDetails;

	private String sellerName;

	private String sellerId;

	private List<SellerOrderInfo> sellerOrderInfo;

	private Boolean hasSellerOrder = false;

	private Boolean isDangerousProduct = false;

	private String optionId;
}

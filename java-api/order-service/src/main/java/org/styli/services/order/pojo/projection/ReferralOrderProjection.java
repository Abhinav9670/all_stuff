package org.styli.services.order.pojo.projection;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Projection for referral order list query - only the columns used when building ReferalOrderData.
 */
public interface ReferralOrderProjection {

	Integer getCustomerId();

	Integer getEntityId();

	Integer getStoreId();

	Timestamp getCreatedAt();

	Timestamp getDeliveredAt();

	BigDecimal getGrandTotal();

	/** Store credit in order currency (use with grandTotal for same-currency total). */
	BigDecimal getAmstorecreditAmount();

	String getCustomerEmail();
}

/**
 * 
 */
package org.styli.services.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;

/**
 * @author manineemahapatra
 *
 */

@Service
public interface PaymentService {

	BNPLOrderUpdateResponse updatePayment(PaymentDTO paymentDTO, boolean isWebhook, String deviceId);

	List<BNPLOrderUpdateResponse> getPaymentUpdates(String deviceId);

	String refundPayment(Integer orderId);

	void updatePaymentOnReplica(String paymentId, String deviceId);
	
	public BNPLOrderUpdateResponse updatePaymentStatus(ProxyOrder order, String deviceId);
	
	public boolean capturePayment(SalesOrder order);

	public boolean capturePaymentV2(SplitSalesOrder splitSalesOrder);
	
	public boolean failProxyOrderByOrderId(String incrementId);
}

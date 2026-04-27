package org.styli.services.order.service;

import java.util.List;

import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.autoRefund.AutoRefundDTO;
import java.util.Map;

import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.autoRefund.AutoRefundDTO;
import org.styli.services.order.pojo.autoRefund.RefundResponseDTO;

public interface AutoRefundService {

	RefundResponseDTO rtoOrders(AutoRefundDTO autoRefundDTO);

	void intiateBulkRefund(List<String> incrementIds);

	void updateRtoAutoRefund(SalesOrder salesOrder, SplitSalesOrder splitSalesOrder, String status);

	RefundPaymentRespone updateStatusToInitiateRefund(List<String> incrementIds);

}

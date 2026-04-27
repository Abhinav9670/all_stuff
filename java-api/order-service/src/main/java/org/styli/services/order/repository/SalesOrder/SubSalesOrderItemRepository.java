package org.styli.services.order.repository.SalesOrder;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SubSalesOrderItem;

public interface SubSalesOrderItemRepository
		extends JpaRepository<SubSalesOrderItem, Integer>, JpaSpecificationExecutor<SubSalesOrderItem> {

	List<SubSalesOrderItem> findBySalesOrderItem(SalesOrderItem salesOrderItem);

	@Query(value = "SELECT discount FROM sub_sales_order_item WHERE parent_order_id = ?1 AND main_item_id = ?2 AND is_gift_voucher = true", nativeQuery = true)
	BigDecimal findDiscountByParentOrderIdAndMainItemId(int parentOrderId, int mainItemId);

	@Query(value = "SELECT discount FROM split_sub_sales_order_item WHERE parent_order_id = ?1 AND main_item_id = ?2 AND is_gift_voucher = true", nativeQuery = true)
	BigDecimal findSplitDiscountByParentOrderIdAndMainItemId(int parentOrderId, int mainItemId);

}
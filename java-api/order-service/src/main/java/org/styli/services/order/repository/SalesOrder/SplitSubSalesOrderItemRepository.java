package org.styli.services.order.repository.SalesOrder;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.model.sales.SplitSubSalesOrderItem;

public interface SplitSubSalesOrderItemRepository
		extends JpaRepository<SplitSubSalesOrderItem, Integer>, JpaSpecificationExecutor<SplitSubSalesOrderItem> {
			
	List<SplitSubSalesOrderItem> findBySplitSalesOrderItem(SplitSalesOrderItem splitSalesOrderItem);

@Query(value = "SELECT discount FROM split_sub_sales_order_item WHERE parent_order_id = :parentOrderId AND main_item_id = :mainItemId AND is_gift_voucher = true", nativeQuery = true)
	BigDecimal findDiscountByParentOrderIdAndMainItemId(@Param("parentOrderId") int parentOrderId, @Param("mainItemId") int mainItemId);

}
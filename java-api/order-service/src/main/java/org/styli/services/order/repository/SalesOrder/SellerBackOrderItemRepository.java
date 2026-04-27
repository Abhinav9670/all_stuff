package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SellerBackOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSellerOrder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Repository
public interface SellerBackOrderItemRepository extends JpaRepository<SellerBackOrderItem, Integer> {
    List<SellerBackOrderItem> findBySplitSellerOrderAndSku(SplitSellerOrder splitSellerOrder, String sku);
    
    List<SellerBackOrderItem> findByMainOrderAndStatusNot(SalesOrder mainOrder, String status);
    
    List<SellerBackOrderItem> findBySplitOrderAndStatusNot(SplitSalesOrder splitOrder, String status);

    List<SellerBackOrderItem> findBySplitSellerOrderAndStatusNot(SplitSellerOrder splitSellerOrder, String status);

    // Existence check aligned with unique key seller_back_order_item.ux_sboi_seller_main_sku
    boolean existsBySellerBackOrder_SellerIdAndMainOrder_EntityIdAndSku(Integer sellerId, Integer mainOrderEntityId, String sku);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value =
            "INSERT INTO seller_back_order_item " +
            "(back_order_id, seller_id, seller_order_id, main_order_id, split_order_id, sku, qty, price, status, shipment_code, asn_code, created_at) " +
            "VALUES (:backOrderId, :sellerOrderId, :mainOrderId, :splitOrderId, :sku, :qty, :price, :status, :shipmentCode, :asnCode, :createdAt) " +
            "ON DUPLICATE KEY UPDATE " +
            "price = VALUES(price), status = VALUES(status), asn_code = VALUES(asn_code), back_order_id = VALUES(back_order_id), qty = VALUES(qty), seller_order_id = VALUES(seller_order_id), split_order_id = VALUES(split_order_id), shipment_code = VALUES(shipment_code)", nativeQuery = true)
    int upsertItem(@Param("backOrderId") Integer backOrderId,
                   @Param("sellerOrderId") Integer sellerOrderId,
                   @Param("mainOrderId") Integer mainOrderId,
                   @Param("splitOrderId") Integer splitOrderId,
                   @Param("sku") String sku,
                   @Param("qty") Integer qty,
                   @Param("price") BigDecimal price,
                   @Param("status") String status,
                   @Param("shipmentCode") String shipmentCode,
                   @Param("asnCode") String asnCode,
                   @Param("createdAt") Timestamp createdAt);
}
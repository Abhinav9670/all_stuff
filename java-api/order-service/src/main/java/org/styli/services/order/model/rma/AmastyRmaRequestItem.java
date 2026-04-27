package org.styli.services.order.model.rma;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Amasty RMA Request Items Table
 */
@Entity
@Data
@Table(name = "amasty_rma_request_item")
public class AmastyRmaRequestItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, name = "request_item_id", nullable = false)
    private Integer requestItemId;

    @Column(name = "request_id", nullable = false)
    private Integer requestId;

    @Column(name = "order_item_id", nullable = false)
    private Integer orderItemId;

    @Column(name = "qty")
    private BigDecimal qty = BigDecimal.ZERO;

    @Column(name = "request_qty")
    private BigDecimal requestQty = BigDecimal.ZERO;

    @Column(name = "reason_id", nullable = false)
    private Integer reasonId;

    @Column(name = "condition_id", nullable = false)
    private Integer conditionId;

    @Column(name = "resolution_id", nullable = false)
    private Integer resolutionId;

    @Column(name = "item_status", nullable = false, columnDefinition = "SMALLINT")
    private Integer itemStatus = 0;
    
    @Column(name = "actual_qty_returned")
    private Integer actualQuantyReturned;
    
    @Column(name = "actual_refunded_amount")
    private BigDecimal actualRefundedAmount = BigDecimal.ZERO;;

    @ManyToOne
    @JoinColumn(name = "request_id", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private AmastyRmaRequest amastyRmaRequest;
    
    @Column(name = "qc_failed_qty", nullable = false, columnDefinition = "SMALLINT")
    private Integer qcFailedQty = 0;
    
    @Column(name = "gift_voucher_refunded_amount")
	private BigDecimal giftVoucherRefundedAmount = BigDecimal.ZERO;

}
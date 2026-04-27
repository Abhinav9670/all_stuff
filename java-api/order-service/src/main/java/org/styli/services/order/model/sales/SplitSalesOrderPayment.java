package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Split Sales Flat Order Payment
 */
@Data
@Entity
@Table(name = "split_sales_order_payment")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitSalesOrderPayment implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "parent_id", nullable = false, insertable = true, updatable = false)
    @JsonIgnore
    private SalesOrder salesOrder;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_parent_id", nullable = false)
    @JsonIgnore
    private SplitSalesOrder splitSalesOrder;

    @Column(name = "base_shipping_captured")
    private BigDecimal baseShippingCaptured;

    @Column(name = "shipping_captured")
    private BigDecimal shippingCaptured;

    @Column(name = "amount_refunded")
    private BigDecimal amountRefunded;

    @Column(name = "base_amount_paid")
    private BigDecimal baseAmountPaid;

    @Column(name = "amount_canceled")
    private BigDecimal amountCanceled;

    @Column(name = "base_amount_authorized")
    private BigDecimal baseAmountAuthorized;

    @Column(name = "base_amount_paid_online")
    private BigDecimal baseAmountPaidOnline;

    @Column(name = "base_amount_refunded_online")
    private BigDecimal baseAmountRefundedOnline;

    @Column(name = "base_shipping_amount")
    private BigDecimal baseShippingAmount;

    @Column(name = "shipping_amount")
    private BigDecimal shippingAmount;

    @Column(name = "amount_paid")
    private BigDecimal amountPaid;

    @Column(name = "amount_authorized")
    private BigDecimal amountAuthorized;

    @Column(name = "base_amount_ordered")
    private BigDecimal baseAmountOrdered;

    @Column(name = "base_shipping_refunded")
    private BigDecimal baseShippingRefunded;

    @Column(name = "shipping_refunded")
    private BigDecimal shippingRefunded;

    @Column(name = "base_amount_refunded")
    private BigDecimal baseAmountRefunded;

    @Column(name = "amount_ordered")
    private BigDecimal amountOrdered;

    @Column(name = "base_amount_canceled")
    private BigDecimal baseAmountCanceled;

    @Column(name = "quote_payment_id")
    private Integer quotePaymentId;

    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;

    @Column(name = "cc_exp_month")
    private String ccExpMonth;

    @Column(name = "cc_ss_start_year")
    private String ccSsStartYear;

    @Column(name = "echeck_bank_name")
    private String echeckBankName;

    @Column(name = "method")
    private String method;

    @Column(name = "cc_debug_request_body")
    private String ccDebugRequestBody;

    @Column(name = "cc_secure_verify")
    private String ccSecureVerify;

    @Column(name = "protection_eligibility")
    private String protectionEligibility;

    @Column(name = "cc_approval")
    private String ccApproval;

    @Column(name = "cc_last_4")
    private String ccLast4;

    @Column(name = "cc_status_description")
    private String ccStatusDescription;

    @Column(name = "echeck_type")
    private String echeckType;

    @Column(name = "cc_debug_response_serialized")
    private String ccDebugResponseSerialized;

    @Column(name = "cc_ss_start_month")
    private String ccSsStartMonth;

    @Column(name = "echeck_account_type")
    private String echeckAccountType;

    @Column(name = "last_trans_id")
    private String lastTransId;

    @Column(name = "cc_cid_status")
    private String ccCidStatus;

    @Column(name = "cc_owner")
    private String ccOwner;

    @Column(name = "cc_type")
    private String ccType;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cc_exp_year")
    private String ccExpYear;

    @Column(name = "cc_status")
    private String ccStatus;

    @Column(name = "echeck_routing_number")
    private String echeckRoutingNumber;

    @Column(name = "account_status")
    private String accountStatus;

    @Column(name = "anet_trans_method")
    private String anetTransMethod;

    @Column(name = "cc_debug_response_body")
    private String ccDebugResponseBody;

    @Column(name = "cc_ss_issue")
    private String ccSsIssue;

    @Column(name = "echeck_account_name")
    private String echeckAccountName;

    @Column(name = "cc_avs_status")
    private String ccAvsStatus;

    @Column(name = "cc_number_enc")
    private String ccNumberEnc;

    @Column(name = "cc_trans_id")
    private String ccTransId;

    @Column(name = "address_status")
    private String addressStatus;

    @Column(name = "additional_information", columnDefinition = "TEXT")
    private String additionalInformation;

}

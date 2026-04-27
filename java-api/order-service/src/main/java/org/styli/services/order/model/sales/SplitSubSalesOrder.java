package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

@Entity
@Table(name = "split_sub_sales_order")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitSubSalesOrder implements Serializable {

    private static final long serialVersionUID = -2772302493259600945L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, updatable = false, insertable = true)
    @JsonIgnore
    private SalesOrder salesOrder;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "split_order_id", nullable = false, updatable = false, insertable = true)
    @JsonIgnore
    private SplitSalesOrder splitSalesOrder;

    @Column(name = "shipment_mode")
    private String shipmentMode;

    @Column(name = "has_global_shipment")
    private Boolean hasGlobalShipment = false;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "external_coupon_redemption_tracking_id")
    private String externalCouponRedemptionTrackingId;

    // shukran keys started
    @Column(name="cross_border")
    private Boolean crossBorder= false;

    @Column(name="shukran_pr_transaction_net_total")
    private BigDecimal shukranPrTransactionNetTotal;

    @Column(name="shukran_basic_earn_point")
    private BigDecimal shukranBasicEarnPoint;

    @Column(name="shukran_bonus_earn_point")
    private BigDecimal shukranBonusEarnPoint;

    @Column(name="shukran_card_number")
    private String shukranCardNumber;

    @Column(name="tier_name")
    private String tierName;

    @Column(name="qualified_purchase")
    private Boolean qualifiedPurchase=false;

    @Column(name="shukran_store_code")
    private String shukranStoreCode;

    @Column(name="shukran_linked")
    private Boolean shukranLinked=false;

    @Column(name="quote_id")
    private String quoteId;

    @Column(name="total_shukran_returned")
    private Integer totalShukranReturned=0;

    @Column(name = "total_shukran_coins_earned")
    private BigDecimal totalShukranCoinsEarned;

    @Column(name = "total_shukran_coins_burned")
    private BigDecimal totalShukranCoinsBurned;

    @Column(name = "customer_profile_id")
    private String customerProfileId;

    @Column(name= "shukran_locked")
    private Integer shukranLocked;

    @Column(name= "total_shukran_burned_value_in_base_currency")
    private BigDecimal totalShukranBurnedValueInBaseCurrency;

    @Column(name= "total_shukran_burned_value_in_currency")
    private BigDecimal totalShukranBurnedValueInCurrency;

    @Column(name= "total_shukran_earned_value_in_base_currency")
    private BigDecimal totalShukranEarnedValueInBaseCurrency;

    @Column(name= "total_shukran_earned_value_in_currency")
    private BigDecimal totalShukranEarnedValueInCurrency;

    @Column(name="shukran_phone_number")
    private String shukranPhoneNumber;

    @Column(name = "shukran_tenders", columnDefinition = "TEXT")
    private String tenders;

    @Column(name="shukran_pr_successful")
    private Integer shukranPrSuccessful;

    // shukran keys ended

    @Column(name = "external_coupon_redemption_status")
    private Integer externalCouponRedemptionStatus;

    @Column(name = "external_auto_coupon_code")
    private String externalAutoCouponCode;

    @Column(name = "external_auto_coupon_amount")
    private BigDecimal externalAutoCouponAmount;

    @Column(name = "external_auto_coupon_base_amount")
    private BigDecimal externalAutoCouponBaseAmount;

    @Column(name = "client_platform")
    private String clientPlatform;

    @Column(name = "external_quote_id",columnDefinition = "BIGINT")
    private BigInteger externalQuoteId;

    @Column(name = "dtf_locked",columnDefinition = "SMALLINT")
    private Integer dtfLock;

    @Column(name = "query_locked",columnDefinition = "SMALLINT")
    private Integer queryLock;

    @Column(name = "review_required", columnDefinition = "SMALLINT")
    private Integer reviewRequired;

    @Column(name = "external_quote_status",columnDefinition = "SMALLINT")
    private Integer externalQuoteStatus;

    @Column(name = "promo_offers",columnDefinition = "TEXT")
    private String discountData;

    @Column(name = "faster_delivery",columnDefinition = "SMALLINT")
    private Integer fasterDelivery;

    @Column(name = "whitelisted_customer",columnDefinition = "SMALLINT")
    private Integer whiteListedCustomer;

    @Column(name = "donation_amount")
    private BigDecimal donationAmount;

    @Column(name = "base_donation_amount")
    private BigDecimal baseDonationAmount;

    @Column(name = "client_source")
    private String clientSource;

    @Column(name = "warehouse_id",columnDefinition = "SMALLINT")
    private Integer warehouseLocationId;

    @Column(name = "is_unfulfillment_order",columnDefinition = "SMALLINT")
    private Integer isUnfulfilmentOrder;

    @Column(name = "is_otp_verified",columnDefinition = "SMALLINT")
    private Integer otpVerified;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "extra_2")
    private String ratingStatus;

    @Column(name = "is_stylipost",columnDefinition = "SMALLINT",updatable = false ,insertable = false)
    private Integer isStyliPost;

    // EAS_CHANGES quote data for spend data 3 columns added
    @Column(name = "eas_coins",columnDefinition = "MEDIUMINT")
    private Integer easCoins;

    @Column(name = "eas_value_in_currency")
    private BigDecimal easValueInCurrency;

    @Column(name = "eas_value_in_base_currency")
    private BigDecimal easValueInBaseCurrency;

    @Column(name = "free_shipping_type_order")
    private Integer freeShipmentTypeOrder;

    @Column(name = "device_id")
    private String deviceId;

    // EAS_CHANGES quote data for spend data 3 columns added Not to be changed
    @Column(name = "initial_eas_coins",columnDefinition = "MEDIUMINT")
    private Integer initialEasCoins;

    @Column(name = "initial_eas_value_in_currency")
    private BigDecimal initialEasValueInCurrency;

    @Column(name = "initial_eas_value_in_base_currency")
    private BigDecimal initialEasValueInBaseCurrency;

    @Column(name = "retry_payment",columnDefinition = "SMALLINT")
    private Integer retryPayment;

    @Column(name = "order_expired_at")
    private Timestamp orderExpiredAt;

    @Column(name = "payment_pending_first_notification_at")
    private Timestamp firstNotificationAt;

    @Column(name = "payment_pending_second_notification_at")
    private Timestamp secondNotificationAt;

    @Column(name = "retry_payment_count",columnDefinition = "SMALLINT")
    private Integer retryPaymentCount;

    @Column(name = "retry_payment_count_threshold",columnDefinition = "SMALLINT")
    private Integer retryPaymentCountThreshold;

    @Column(name = "gift_voucher_refunded_amount")
    private BigDecimal giftVoucherRefundedAmount = BigDecimal.ZERO;


}

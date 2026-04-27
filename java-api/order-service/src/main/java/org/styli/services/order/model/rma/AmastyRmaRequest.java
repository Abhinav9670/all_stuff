package org.styli.services.order.model.rma;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

import org.springframework.http.ResponseEntity;
import org.styli.services.order.pojo.zatca.ZatcaInvoiceResponse;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * Amasty RMA Request Table
 */
@Entity
@Setter
@Getter
@Table(name = "amasty_rma_request")
public class AmastyRmaRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, name = "request_id", nullable = false)
    private Integer requestId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "store_id", nullable = false, columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "modified_at", nullable = false)
    private Timestamp modifiedAt;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    @Column(name = "customer_name", nullable = false, columnDefinition = "TEXT")
    private String customerName;

    @Column(name = "url_hash", nullable = false)
    private String urlHash;

    @Column(name = "manager_id", nullable = false)
    private Integer managerId;

    @Column(name = "custom_fields", nullable = false, columnDefinition = "TEXT")
    private String customFields;

    @Column(name = "rating", nullable = false, columnDefinition = "SMALLINT")
    private Integer rating;

    @Column(name = "rating_comment", columnDefinition = "TEXT")
    private String ratingComment;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "shipping_label")
    private String shippingLabel;

    @Column(name = "gcs_object_path", length = 1024)
    private String gcsObjectPath;

    @Column(name = "gcs_signed_url_expiry")
    private Timestamp gcsSignedUrlExpiry;

    @Column(name = "carrier_backup_url", length = 1024)
    private String carrierBackupUrl;

    @Column(name = "rma_inc_id", nullable = false)
    private String rmaIncId;

    @Column(name = "is_created_by_admin", nullable = false)
    private String isCreatedByAdmin;
    
    @Column(name = "return_type", nullable = false, columnDefinition = "SMALLINT")
    private Integer returnType;
    
    @Column(name = "is_short_pickedup", nullable = false, columnDefinition = "SMALLINT")
    private Integer shortPickup;

    @OneToMany(mappedBy = "amastyRmaRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<AmastyRmaRequestItem> amastyRmaRequestItems = new HashSet<>();
    
	//EAS Columns to coins start
	@Column(name = "eas_coins",columnDefinition = "MEDIUMINT")
	private Integer easCoins;

	@Column(name = "eas_value_in_currency")
	private BigDecimal easValueInCurrency;

	@Column(name = "eas_value_in_base_currency")
	private BigDecimal easValueInBaseCurrency;
	//EAS Columns to coins end
	
	@Column(name = "rma_payment_method", nullable = true)
	private String rmaPaymentMethod;
	
	@Column(name = "rma_payment_expire_on", nullable = true)
	private String rmaPaymentExpireOn;
	
	@Column(name = "gift_voucher_refunded_amount")
	private BigDecimal giftVoucherRefundedAmount = BigDecimal.ZERO;
	
	@Column(name = "dropOff_details", nullable = true) 
    private String dropOffDetails;

    @Column(name = "city_name", nullable = true) 
    private String cityName;

    @Column(name = "cp_id", nullable = true)
    private String cpId;

    @Column(name = "return_inc_payfort_id", nullable = true)
    private String returnIncPayfortId;

    @Column(name="return_fee", nullable = true)
    private Double returnFee=0.0;
    
    @Column(name = "zatca_details", columnDefinition = "TEXT",nullable = true)
    private String zatcaDetails;

    @Column(name = "zatca_qr_code", columnDefinition = "TEXT",nullable = true)
    private String zatcaQrCode;

    @Column(name="return_invoice_amount", nullable = true)
    private Double returnInvoiceAmount=0.0;

    // shukran keys started

    @Column(name="shukran_points_refunded")
    private BigDecimal shukranPointsRefunded;

    @Column(name="shukran_points_refunded_value_in_currency")
    private BigDecimal shukranPointsRefundedValueInCurrency;

    @Column(name="shukran_points_refunded_value_in_base_currency")
    private BigDecimal shukranPointsRefundedValueInBaseCurrency;

    @Column(name="shukran_rt_successful")
    private Boolean shukranRtSuccessful= false;

    @Column(name="shukran_refund_successful")
    private Boolean shukranRefundSuccessful=false;

    // Shukran keys ended

    @Column(name = "split_order_id", nullable = true)
    private Integer splitOrderId;

}
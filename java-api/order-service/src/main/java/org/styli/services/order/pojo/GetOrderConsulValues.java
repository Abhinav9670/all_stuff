package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;
import org.styli.services.order.pojo.consul.oms.base.AddressChangeAttributes;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.zatca.ZatcaConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetOrderConsulValues {

	@JsonProperty("block_shukran_second_refund")
	private Boolean blockShukranSecondRefund = true;

	@JsonProperty("enable_shukran_refund")
	private Boolean enableShukranRefund= true;

	@JsonProperty("shukran_tender_mappings")
	private ShukranTenderMapping shukranTenderMappings;

	@JsonProperty("shukran_base_url")
	private String shukranBaseUrl;

	@JsonProperty("shukran_return_adjustment_comment")
	private String shukranReturnAdjustmentComment;

	@JsonProperty("shukran_cod_item_name")
	private String shukranCodItemName= "NonTradingItemProduct";

	@JsonProperty("shukran_basic_item_name")
	private String shukranBasicItemName="BasicAccrual";

	@JsonProperty("shukran_lock_unlock_url")
	private String shukranLockUnlockUrl;

	@JsonProperty("shukran_return_adjustment_reason_code")
	private String shukranReturnAdjustmentReasonCode="GW30";

	@JsonProperty("shukran_return_url")
	private String shukranReturnUrl;

	@JsonProperty("get_customer_shukran_data_url")
	private String getCustomerShukranDataUrl;

	@JsonProperty("get_customer_points_description_to_match")
	private String getCustomerPointsDescriptionToMatch;

	@JsonProperty("shukran_unlock_order_time_limit_in_minutes")
	private Integer shukranUnlockOrderTimeLimitInMinutes=1440;

	@JsonProperty("shukran_cart_id_prefix")
	private String shukranCartIdPrefix;

	@JsonProperty("archive_order_last_number")
	private Integer archiveOrderLastNumber;	
	
	@JsonProperty("payfort")
	private PayfortDetails payfort;	
	
	@JsonProperty("wms")
	private WmsDetails wms;
	
	@JsonProperty("navik")
	private NavikDetails navik;
	
	@JsonProperty("inventory_mapping")
	private List<InventoryMapping> inventoryMapping;

	@JsonProperty("seller_inventory_mapping")
	private List<SellerInventoryMapping> sellerInventoryMapping;

	@JsonProperty("use_seller_config_from_db")
	private Boolean useSellerConfigFromDb;

    @JsonProperty("unicommerece_inventory_mapping")
    private List<UnicommereceInventoryMapping> unicommerceInventoryMapping;
	
	@JsonProperty("order_details")
	private OrderKeyDetails orderDetails;

	@JsonProperty("is_firebase_auth_enable")
	private boolean isFirebaseAuthEnable;

	@JsonProperty("is_internal_auth_enable")
	private boolean isInternalAuthEnable;

	@JsonProperty("is_external_auth_enable")
	private boolean isExternalAuthEnable;


	@JsonProperty("ORDER_CANCEL_AMOUNT_REFUND_STYLICREDIT")
	private int iscancelReturnToStyliCredit;

	@JsonProperty("orders_hours_ago")
	private Integer salesOrderUpdateCustomerIdHours;
	
	@JsonProperty("tabby")
	private TabbyDetails tabby;

	@JsonProperty("wallet_update_cc_email")
	private String walletUpdateCcEmail;
	
	@JsonProperty("tamara")
	private TamaraDetails tamara;
	
	@JsonProperty("wallet_update_process_flag")
	private boolean walletUpdateProcessFlag;

	@JsonProperty("bank_swift_codes")
	private Map<String, List<BankSwiftCode>> bankSwiftCodes;
	
	@JsonProperty("cahshfree")
	private CashfreeDetails cashfree;
	
	@JsonProperty("zatca_config")
	private ZatcaConfig zatcaConfig;

	@JsonProperty("block_eas")
	private Boolean blockEAS = false;

	@JsonProperty("styli_cash")
	private Boolean styliCash = true;

	@JsonProperty("addressChangeFlagMap")
	private LinkedHashMap<String, AddressChangeAttributes> addressChangeFlagMap = new LinkedHashMap<>();

	@JsonProperty("signed_url_expiry")
	private Integer signedUrlExpiry=15;

	@JsonProperty("useKafkaForPreferredPaymentFeature")
	private Boolean kafkaForPreferredPaymentFeature = true;

	@JsonProperty("non_ksa_seller_cancellation")
	private Boolean nonKsaSellerCancellation = false;

    @JsonProperty("global_shipment_cancellation_time")
    private Integer globalShipmentCancellationTime;

    @JsonProperty("lmdCommission")
    private List<LmdCommission> lmdCommission;

	@JsonProperty("isSignUpOtpEnabled")
	private String isSignUpOtpEnabled;

	@JsonProperty("minAppVersionReqdForOtpFeature")
	private String minAppVersionReqdForOtpFeature;

	@JsonProperty("storeIdsForOtpFeature")
	private List<Integer> storeIdsForOtpFeature;

	@JsonProperty("sku_batch_size_for_matching_orders")
	private Integer skuBatchSizeForMatchingOrders = 100;

    @JsonProperty("gcs_shipping_label_enabled")
    private Boolean gcsShippingLabelEnabled;

    @JsonProperty("gcs_shipping_label_bucket")
    private String gcsShippingLabelBucket;

    @JsonProperty("gcs_shipping_label_folder_prefix")
    private String gcsShippingLabelFolderPrefix;

    @JsonProperty("gcs_signed_url_expiry_minutes")
    private Integer gcsSignedUrlExpiryMinutes;

	@JsonProperty("order_status_flag")
	private Boolean orderStatusFlag;

    // ---------------- New Configs ----------------

    // currency: { multiplier: { "CNY-AED": 0.536, ... } }
    @JsonProperty("currency")
    private CurrencyConfig currency;

    // region_level_tax_percentage: { "SAR": 5, "KWD": 5, ... }
    @JsonProperty("region_level_tax_percentage")
    private Map<String, BigDecimal> regionLevelTaxPercentage;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class CurrencyConfig {
        @JsonProperty("multiplier")
        private Map<String, BigDecimal> multiplier;
    }
}

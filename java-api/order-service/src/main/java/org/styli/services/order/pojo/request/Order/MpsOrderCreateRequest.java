package org.styli.services.order.pojo.request.Order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Data;

import java.util.List;

@Data
public class MpsOrderCreateRequest {

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("customer_address_landmark")
    private String customerAddressLandmark;

    @JsonProperty("customer_address")
    private String customerAddress;

    @JsonProperty("customer_city")
    private String customerCity;

    @JsonProperty("customer_postal_code")
    private String customerPostalCode;

    @JsonProperty("customer_phone")
    private String customerPhone;

    @JsonProperty("customer_phone_code")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String customerPhoneCode;

    @JsonProperty("customer_country_code")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String customerCountryCode = "";

    @JsonProperty("customer_unit_number")
    private String customerUnitNumber;

    @JsonProperty("short_address")
    private String shortAddress;

    @JsonProperty("customer_additional_number")
    private String customerAdditionalNumber;

    @JsonProperty("customer_zip_code")
    private String customerZipCode;

    @JsonProperty("customer_street_name")
    private String customerStreetName;

    @JsonProperty("customer_building_number")
    private String customerBuildingNumber;

    @JsonProperty("custom_value")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer customValue = 1;

    @JsonProperty("custom_currency_code")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String customCurrencyCode = "";

    @JsonProperty("mode_of_transport")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String modeOfTransport = "";

    @JsonProperty("custom_clearance_mode")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String customClearanceMode = "";

    @JsonProperty("hs_code")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String hsCode = "";

    @JsonProperty("invoice_value")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double invoiceValue = 0.0;

    @JsonProperty("invoice_number")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String invoiceNumber = "";

    @JsonProperty("invoice_currency_code")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String invoiceCurrencyCode = "";

    @JsonProperty("cod_value")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double codValue = 0.0;

    @JsonProperty("cod_currency_code")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String codCurrencyCode = "";

    @JsonProperty("order_type")
    private String orderType;

    @JsonProperty("order_ref_number")
    private String orderRefNumber;

    @JsonProperty("delivery_type")
    private String deliveryType;

    @JsonProperty("invoice_date")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String invoiceDate = "";

    @JsonProperty("additional_order_date")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String additionalOrderDate = "";

    @JsonProperty("pickup_info_name")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String pickupInfoName = "";

    @JsonProperty("return_info_name")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String returnInfoName = "";

    @JsonProperty("sku_description")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String skuDescription = "";

    @JsonProperty("shipping_category")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String shippingCategory = "";

    @JsonProperty("delivery_instructions")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String deliveryInstructions = "";

    @JsonProperty("sku")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String sku = "";

    @JsonProperty("qty")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String qty = "";

    @JsonProperty("is_mps")
    private Boolean isMps = false;

    @JsonProperty("box_count")
    private Integer boxCount;

    @JsonProperty("shipment_details")
    private List<MpsShipmentDetail> shipmentDetails;

}

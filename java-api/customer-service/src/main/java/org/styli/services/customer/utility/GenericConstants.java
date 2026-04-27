package org.styli.services.customer.utility;

import java.util.Arrays;

import java.util.List;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

public class GenericConstants {

    public static final String REORDER_MODE_IS_REORDER = "reorder";
    public static final String REORDER_MODE_IS_RETRY_PAYMEWNT = "retry_payment";

    public static final String ERROR_500 = "Internal Server Error";
    public static final String ERROR_404 = "Incorrect Route";

    public static final String STORE_NAME = "Main Website\n" + "Main Website Store\n";

    public static final Integer DEFAULT_WEBSITE_STOCK_ID = 1;
    public static final Integer DEFAULT_CONFIG_STORE_ID = 0;

    public static final String APP_TYPE_ANDROID = "android";
    public static final String APP_TYPE_IOS = "ios";
    public static final String ENV_TYPE_ACTUAL = "actual";

    public static final String CONFIG_CURRENCY_OPTIONS_DEFAULT = "currency/options/default";
    public static final String CONFIG_CURRENCY_OPTIONS_BASE = "currency/options/base";
    public static final String CONFIG_GENERAL_LOCALE_CODE = "general/locale/code";
    public static final String CONFIG_SALES_SHIPPING_CHARGES_THRESHOLD = "carriers/clickpost/free_shipping_subtotal";
    public static final String CONFIG_SALES_RMA_PICKUP_THRESHOLD = "amrma/general/awb_pick_time";
    public static final String CONFIG_SALES_SHIPPING_CHARGES = "carriers/clickpost/price";
    public static final String CONFIG_SALES_COD_CHARGES = "payment/cashondelivery/fee";
    public static final String CONFIG_SALES_TAX_PERCENTAGE = "mobile/tax_config/tax_percentage";

    public static final String CONFIG_RESPONSE_X_FORWARDED_FOR = "X-FORWARDED-FOR";

    public static final String CONFIG_SA_EN_DEFAULT_COUNTRY = "Saudi Arabia";
    public static final String CONFIG_SA_AR_DEFAULT_COUNTRY = "السعودية";
    public static final String CONFIG_AE_EN_DEFAULT_COUNTRY = "United Arab Emirates";
    public static final String CONFIG_AE_AR_DEFAULT_COUNTRY = "الإمارات";
    public static final String CONFIG_SA_DEFAULT_MOBILE_CODE = "+966";
    public static final String CONFIG_AE_DEFAULT_MOBILE_CODE = "+971";

    public static final String HTTPS_HOST_CONSTANT = "https:";

    public static final List<String> bannedCitiesForDelivery = Arrays.asList("Ja'araneh", "Nwariah", "Shraie'E",
            "Shumeisi", "نواره", "جعرانه", "الشميس", "الشرائع");

    public static final String ALGOLIA_INDEX_TYPE_PRODUCTS = "products";
    public static final Integer ADMIN_STORE_ID = 0;

}

package org.styli.services.customer.utility.utility;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableList;

public class GenericConstants {

    public static final String APP_TYPE_ANDROID = "android";
    public static final String APP_TYPE_IOS = "ios";
    public static final String ENV_TYPE_ACTUAL = "actual";

    public static final String CONFIG_RESPONSE_X_FORWARDED_FOR = "X-FORWARDED-FOR";

    public static final String CONFIG_SA_EN_DEFAULT_COUNTRY = "Saudi Arabia";
    public static final String CONFIG_SA_AR_DEFAULT_COUNTRY = "السعودية";
    public static final String CONFIG_AE_EN_DEFAULT_COUNTRY = "United Arab Emirates";
    public static final String CONFIG_AE_AR_DEFAULT_COUNTRY = "الإمارات";
    public static final String CONFIG_KW_EN_DEFAULT_COUNTRY = "Kuwait";
    public static final String CONFIG_KW_AR_DEFAULT_COUNTRY = "الكويت‎";
    public static final String CONFIG_SA_DEFAULT_MOBILE_CODE = "+966";
    public static final String CONFIG_AE_DEFAULT_MOBILE_CODE = "+971";
    public static final String CONFIG_KW_DEFAULT_MOBILE_CODE = "+965";
    
    public static final String CONFIG_SA_FLAG_URL = "/flags/flag_sa.png";
    public static final String CONFIG_AE_FLAG_URL = "/flags/flag_ae.png";
    public static final String CONFIG_KW_FLAG_URL = "/flags/flag_kw.png";
    public static final String CONFIG_QA_FLAG_URL = "/flags/flag_qa.png";
    public static final String CONFIG_BH_FLAG_URL = "/flags/flag_bh.png";
    
    public static final String CONFIG_QA_DEFAULT_MOBILE_CODE = "+974";
    public static final String CONFIG_BH_DEFAULT_MOBILE_CODE = "+973";
    
    public static final String CONFIG_QA_EN_DEFAULT_COUNTRY = "Qatar";
    public static final String CONFIG_QA_AR_DEFAULT_COUNTRY = "دولة قطر";
    
    public static final String CONFIG_BH_EN_DEFAULT_COUNTRY = "Bahrain";
    public static final String CONFIG_BH_AR_DEFAULT_COUNTRY = "البحرين";
    
    public static final String CONFIG_OM_EN_DEFAULT_COUNTRY = "Oman";
    public static final String CONFIG_OM_AR_DEFAULT_COUNTRY = "عُمان";
    public static final String CONFIG_OM_DEFAULT_MOBILE_CODE = "+968";
    public static final String CONFIG_OM_FLAG_URL = "/flags/flag_om.png";

    // public static final String HTTPS_HOST_CONSTANT = "https:";
    //
    //
    //
    // public static final List<String> bannedCitiesForDelivery = Arrays.asList(
    // "Ja'araneh",
    // "Nwariah",
    // "Shraie'E",
    // "Shumeisi",
    // "نواره",
    // "جعرانه",
    // "الشميس",
    // "الشرائع"
    // );
    
    private static final Map<Integer, Boolean> isCurrencyDecimal = new  HashMap<Integer, Boolean>()
    {
        {
            put(1, false);
            put(3, false);
            put(4, true);
            put(5, false);
            put(7, true);
           
        };
    };
    
    public static final ImmutableList<String> REMOVE_STORE_CONSTANTS = 
    		  ImmutableList.of("15","17","19","21");

}
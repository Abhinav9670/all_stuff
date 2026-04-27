package org.styli.services.customer.utility.consul;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.customer.gateway.getters.MapValueGetter;
import org.styli.services.customer.pojo.TtlKeyValue;
import org.styli.services.customer.pojo.consul.DeleteCustomer;
import org.styli.services.customer.utility.*;

import java.util.*;


/**
 * Created on 01-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public class ServiceConfigs {
    private static final Log LOGGER = LogFactory.getLog(ServiceConfigs.class);

    public static final ObjectMapper mapper = new ObjectMapper();

    public static final String CONSUL_CUSTOMER_SERVICE_KEY =
            "java/customer-service/customerConsulKeys";

    private static final String ERROR_FETCHING_KALEYRA_FLAG = "Error fetching kaleyra flag: ";
    private static final String ERROR_FETCHING_WHATSAPP_FLAG = "Error fetching WhatsApp flag: ";
    private static final String ERROR_FETCHING_ROUTE_FLAG = "Error fetching route flag: ";
    private static final String ERROR_FETCHING_KAFKA_INFLUENCER_FLAG = "Error fetching kafka influencer flag: ";

    private static final ArrayList<ServiceConfigsListener> listeners = new ArrayList<>();
    public static Map<String, Object> consulServiceMap = new LinkedHashMap<>();
    private static final MapValueGetter mapValueGetter = new MapValueGetter() {};

    public static Map<String, Object> getConsulServiceMap() {
        return consulServiceMap;
    }

    public static void setConsulServiceMap(Map<String, Object> consulServiceMap) {
        ServiceConfigs.consulServiceMap = consulServiceMap;
        for (ServiceConfigsListener listener : listeners) {
            if (listener != null) {
                try {
                    listener.onConfigsUpdated(consulServiceMap);
                } catch (Exception e) {
                    LOGGER.error(
                            "ServiceConfigsListener error when trying to update "
                                    + consulServiceMap.getClass().getSimpleName()
                                    + ":\n"
                                    + e.toString());
                }
            }
        }
    }

    public static void addConfigListener(ServiceConfigsListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeConfigListener(ServiceConfigsListener listener) {
        if (listener != null && listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    public static Map<?, ?> getLimiterConfigs() {
        Map<?, ?> result = new LinkedHashMap<>();
        try {
            Object object = ServiceConfigs.consulServiceMap.get("limiter");
            if (object instanceof Map<?, ?>) {
                result = (Map<?, ?>) object;
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getTestEnvPhoneNo() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("testEnvPhoneNo");
            if (object != null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getOtpMaskEnabledForMsite() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("otpMaskingEnabledForMsite");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getGuestSessionsTrackingFlag() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("guest_sessions_logging");
            if(ObjectUtils.isNotEmpty(object)) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static DeleteCustomer getDeleteCustomer() {
        DeleteCustomer result = new DeleteCustomer();
        try {
            Object deleteCustomerObject = ServiceConfigs.consulServiceMap.get("deleteCustomer");
            String jsonString;
            if (deleteCustomerObject instanceof String) {
                jsonString = (String) deleteCustomerObject;
            } else {
                jsonString = Constants.JSON_MAPPER.writeValueAsString(deleteCustomerObject);
            }
            result = Constants.JSON_MAPPER.readValue(jsonString, DeleteCustomer.class);
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getOtpMaskEnabledForMobile() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("otpMaskingEnabledForMobile");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getOtpMessage(String key, String langCode) {
        String result = "";
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(langCode)) return result;
        try {
            Object otpMessagesObject = ServiceConfigs.consulServiceMap.get("otpMessages");
            if (otpMessagesObject instanceof Map<?, ?>) {
                Map<?, ?> otpMessages = (Map<?, ?>) otpMessagesObject;
                Object valuesObject = otpMessages.get(key);
                if (valuesObject instanceof Map<?, ?>) {
                    Map<?, ?> values = (Map<?, ?>) valuesObject;
                    Object messageObject = values.get(langCode);
                    if (messageObject != null) result = messageObject.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static FromEmail getFromEmail() {
        FromEmail result;
        try {
            Object fromEmailObject = ServiceConfigs.consulServiceMap.get("fromEmail");
            String jsonString;
            if (fromEmailObject instanceof String) {
                jsonString = (String) fromEmailObject;
            } else {
                jsonString = Constants.JSON_MAPPER.writeValueAsString(fromEmailObject);
            }
            result = Constants.JSON_MAPPER.readValue(jsonString, FromEmail.class);
        } catch (Exception e) {
            result = FromEmail.of("", new LinkedHashMap<>());
        }
        return result;
    }

    public static int getOtpLength() {
        return getOtpLength(4);
    }

    public static int getOtpLength(int fallback) {
        int result = fallback;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("otpLength");
            if (object != null) result = ((int) Double.parseDouble(object.toString()));
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static boolean forceResetOtp() {
        return forceResetOtp(false);
    }

    public static boolean forceResetOtp(boolean fallback) {
        boolean result = fallback;
        try {
            Boolean value =
                    mapValueGetter.getBooleanFromMap(
                            ServiceConfigs.consulServiceMap, "forceResetOtp", fallback);
            if (value != null) {
                result = value;
            }
        } catch (Exception e) {
            LOGGER.error(e);
            result = fallback;
        }
        return result;
    }

    public static String getUrl(String key) {
        String result = "";
        if (StringUtils.isEmpty(key)) return result;
        try {
            Object value = getConsulServiceMap().get("urls");
            if (value instanceof Map<?, ?>) {
                Map<?, ?> urls = (Map<?, ?>) value;
                if (urls.get(key) != null) result = urls.get(key).toString();
            }
        } catch (Exception e) {
            result = "";
        }
        return result;
    }

    public interface ServiceConfigsListener {

        void onConfigsUpdated(Map<String, Object> newConfigs);
    }

    public static String getRecaptchaEnabledForMSite() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("recaptchaEnabledForMSite");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getRecaptchaEnabledForMobile() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("recaptchaEnabledForMobile");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }
    public static String isRegistrationBlocked() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("blockRegistration");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getKaleyraKey() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraKey");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }
    public static String getKaleyraSenderId() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraSenderId");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }
    public static String getKaleyraOtpTemplateId() {
        String result = "";
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpTemplateId");
            if(object!=null) result = object.toString();
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getSignUpOtpEnabled() {
        String result = null;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isSignUpOtpEnabled");
            if (ObjectUtils.isNotEmpty(object)) {
                if(object!=null) result = object.toString();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static Boolean getIsEmailVerificationEnabled(Integer selectedStoreId) {
        Boolean result = false;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isEmailVerificationEnabled");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer && storeId.equals(selectedStoreId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static Boolean getIsMagicLinkEnabled(Integer selectedStoreId) {
        Boolean result = false;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isMagicLinkEnabled");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer && storeId.equals(selectedStoreId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static Boolean getIsEmailOtpEnabledV1(Integer selectedStoreId) {
        Boolean result = false;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isEmailOtpEnabledV1");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer && storeId.equals(selectedStoreId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static String getMinAppVersionReqdForOtpFeature() {
        String result = null;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("minAppVersionReqdForOtpFeature");
            if (ObjectUtils.isNotEmpty(object)) {
                result = object.toString();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static List<Integer> getStoreIdsForOtpFeature() {
        List<Integer> result = new ArrayList<>();
        try {

            Object object = ServiceConfigs.consulServiceMap.get("storeIdsForOtpFeature");


            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer) {
                        result.add((Integer) storeId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    public static List<Integer> getStoreIdsForEmailOtpFeature() {
        List<Integer> result = new ArrayList<>();
        try {

            Object object = ServiceConfigs.consulServiceMap.get("storeIdsForEmailOtpFeature");


            result = buildResultList(result,object);
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    /**
     * Store IDs for which Email OTP is enabled in /auth/v2/profile response only.
     * Consul key: storeIdsForEmailOtpFeatureProfileV2
     */
    public static List<Integer> getStoreIdsForEmailOtpFeatureProfileV2() {
        List<Integer> result = new ArrayList<>();
        try {
            Object object = ServiceConfigs.consulServiceMap.get("storeIdsForEmailOtpFeatureProfileV2");
            result = buildResultList(result, object);
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    /**
     * Whether email OTP in registration is enabled for the given store.
     * Config key: storeIdsForEmailOtpInRegistration (list of store IDs).
     */
    public static Boolean getIsEmailOtpInRegistrationEnabled(Integer selectedStoreId) {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("storeIdsForEmailOtpInRegistration");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer && storeId.equals(selectedStoreId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return false;
    }

    public static Boolean getEmailOtpEnableUpdateUser(Integer selectedStoreId) {
        Boolean result = false;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isEmailOtpEnableUpdateUser");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer && storeId.equals(selectedStoreId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }


    public static List<Integer> buildResultList(List<Integer> result, Object object) {
        if (object != null && object instanceof List<?>) {
            List<?> storeIds = (List<?>) object;
            for (Object storeId : storeIds) {
                if (storeId instanceof Integer) {
                    result.add((Integer) storeId);
                }
            }
        }
        return result;
    }

    public static String getWhatsappTemplateName() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppTemplate");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching WhatsApp template name: ", e);
        }
        return null;
    }
    public static boolean useRouteMobileSms() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("useRouteMobileSms");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }
    public static String getSourceForRouteMobileSMS() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("sourceForRouteMobileSMS");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching WhatsApp template name: ", e);
        }
        return null;
    }
    public static boolean getWhatsAppFlagForBH() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledBH");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }
    public static boolean getWhatsAppFlagForSA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledSA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }
    public static boolean getWhatsAppFlagForAE() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledAE");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }

    public static TtlKeyValue getTtls(String key) {
        TtlKeyValue result = new TtlKeyValue();
        try {
            Object object = ServiceConfigs.consulServiceMap.get("ttl");
            if (object instanceof Map<?, ?>) {
                Map<String, Map<?, ?>> ttls = (Map<String, Map<?, ?>>) object;
                result.setUnit(null!=ttls && null != ttls.get(key) ? String.valueOf(ttls.get(key).get("unit")): "MINUTES");
                result.setValue(null!=ttls && null != ttls.get(key) ? (Integer) ttls.get(key).get("value") : 5);
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }
    public static boolean getWhatsAppFlagForKW() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledKW");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }


    public static boolean getKafkaForInfluencerPortalFeature() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kafkaForInfluencerPortalFeature");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KAFKA_INFLUENCER_FLAG, e);
        }
        return false;
    }

    public static String getInfluencerPortalUrl() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerPortalUrl");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer portal url: ", e);
        }
        return null;
    }

    public static String getApiKey() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerApiKey");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer api key: ", e);
        }
        return null;
    }

    public static String getInfluencerTokenUrl() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerTokenUrl");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer token url", e);
        }
        return null;
    }

    public static String getInfluencerClientId() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerClientId");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer client id", e);
        }
        return null;
    }

    public static String getInfluencerClientSecret() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerClientSecret");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer client secret", e);
        }
        return null;
    }

    public static String getInfluencerTokenGrantType() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerTokenGrantType");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer token grant type", e);
        }
        return null;
    }

    public static String getInfluencerTokenScope() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerTokenScope");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer token scope", e);
        }
        return null;
    }

    public static boolean getInfluencerPortalFeatureFlagSA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerPortalFeatureFlagSA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer flag for SA: ", e);
        }
        return false;
    }
    public static boolean getInfluencerPortalFeatureFlagQA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerPortalFeatureFlagQA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer flag for QA: ", e);
        }
        return false;
    }
    public static boolean getInfluencerPortalFeatureFlagAE() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerPortalFeatureFlagAE");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer flag for AE: ", e);
        }
        return false;
    }
    public static boolean getInfluencerPortalFeatureFlagKW() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerPortalFeatureFlagKW");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer flag for KW: ", e);
        }
        return false;
    }
    public static boolean getInfluencerPortalFeatureFlagBH() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerPortalFeatureFlagBH");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer flag for BH: ", e);
        }
        return false;
    }

    public static boolean useKaleyraForBH() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledBH");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useKaleyraForSA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledSA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useKaleyraForAE() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledAE");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useKaleyraForKW() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledKW");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useKaleyraForIN() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledIN");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useKaleyraForQA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledQA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useKaleyraForOM() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("kaleyraOtpEnabledOM");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_KALEYRA_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForAE() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledAE");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForKW() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledKW");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForSA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledSA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForBH() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledBH");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForIN() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledIN");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForQA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledQA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean useRouteMobileForOM() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("routeOtpEnabledOM");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_ROUTE_FLAG, e);
        }
        return false;
    }

    public static boolean getWhatsAppFlagForQA() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledQA");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }

    public static boolean getWhatsAppFlagForIN() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledIN");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }

    public static boolean getWhatsAppFlagForOM() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("whatsAppOtpEnabledOM");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error(ERROR_FETCHING_WHATSAPP_FLAG, e);
        }
        return false;
    }

    public static boolean getInfluencerManualDateRangeFlag() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerManualDateRangeFlag");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer manual date range flag: ", e);
        }
        return false;
    }
    
    public static String getInfluencerManualStartDate() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerManualStartDate");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer manual start date: ", e);
        }
        return null;
    }
    
    public static String getInfluencerManualEndDate() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("influencerManualEndDate");
            if (ObjectUtils.isNotEmpty(object)) {
                return object.toString();
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching influencer manual end date: ", e);
        }
        return null;
    }

    public static Boolean getMagicLinkEnableUpdateUser(Integer selectedStoreId) {
        Boolean result = false;
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isMagicLinkEnableUpdateUser");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer && storeId.equals(selectedStoreId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    /**
     * Gets the list of store IDs for Shukran Linking/Delinking feature
     * @return List of enabled store IDs for Shukran Linking/Delinking
     */
    public static List<Integer> getShukranLinkingDelinkingEnabled() {
        List<Integer> result = new ArrayList<>();
        try {
            Object object = ServiceConfigs.consulServiceMap.get("isShukranLinkingDelinkingEnabled");
            if (object != null && object instanceof List<?>) {
                List<?> storeIds = (List<?>) object;
                for (Object storeId : storeIds) {
                    if (storeId instanceof Integer) {
                        result.add((Integer) storeId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error getting Shukran Linking/Delinking enabled store IDs: " + e);
        }
        return result;
    }

    public static boolean enableCustomerServiceErrorHandling() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("enableCustomerServiceErrorHandling");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching enableCustomerServiceErrorHandling flag: ", e);
        }
        return false;
    }

    /**
     * Gets the nudge configuration for address compliance
     * @return true if nudge is enabled, false otherwise (default: true)
     */
    public static boolean isAddressNudgeEnabled() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("addressNudgeConfig");
            if (object instanceof Map<?, ?>) {
                Map<?, ?> nudgeConfig = (Map<?, ?>) object;
                Object enabled = nudgeConfig.get("enabled");
                if (enabled != null) {
                    return Boolean.TRUE.equals(enabled) || Boolean.parseBoolean(enabled.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching address nudge enabled flag: ", e);
        }
        return true; // Default to enabled
    }

    /**
     * Gets the frequency in days for showing address nudge
     * @return number of days (default: 5)
     */
    public static int getAddressNudgeFrequencyDays() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("addressNudgeConfig");
            if (object instanceof Map<?, ?>) {
                Map<?, ?> nudgeConfig = (Map<?, ?>) object;
                Object showEveryXDays = nudgeConfig.get("showEveryXDays");
                if (showEveryXDays != null) {
                    if (showEveryXDays instanceof Number) {
                        return ((Number) showEveryXDays).intValue();
                    } else {
                        return Integer.parseInt(showEveryXDays.toString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching address nudge frequency days: ", e);
        }
        return 5; // Default to 5 days
    }

    /**
     * Gets the nudge message for address compliance
     * @return nudge message (default: "Per KSA govt guidelines, Short Address is mandatory")
     */
    public static String getAddressNudgeMessage() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("addressNudgeConfig");
            if (object instanceof Map<?, ?>) {
                Map<?, ?> nudgeConfig = (Map<?, ?>) object;
                Object message = nudgeConfig.get("message");
                if (message != null) {
                    return message.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching address nudge message: ", e);
        }
        return "Per KSA govt guidelines, Short Address is mandatory";
    }
    public static boolean enableLoginResponseFiltering() {
        try {
            Object object = ServiceConfigs.consulServiceMap.get("enableLoginResponseFiltering");
            if (ObjectUtils.isNotEmpty(object)) {
                return Boolean.TRUE.equals(object) || Boolean.parseBoolean(object.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching enableLoginResponseFiltering flag: ", e);
        }
        return false;
    }
    
}

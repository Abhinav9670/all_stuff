package org.styli.services.customer.service.impl;


import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupBucketObject;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupRequest;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.ConfigService;
import org.styli.services.customer.service.WhatsappService;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.SignupConstants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.pojo.config.Stores;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Created on 05-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Component
@Scope("singleton")
public class WhatsappServiceImpl implements WhatsappService, ServiceConfigs.ServiceConfigsListener {

    private static final String SIGNUP_REPLY_MESSAGE = "signup_reply_message";

	private static final Log LOGGER = LogFactory.getLog(WhatsappServiceImpl.class);

    private static final Key encryptionKey = getKey();

    @Autowired
    ConfigService configService;

    @Autowired
    RedisHelper redisHelper;


    final private LinkedHashMap<String, String> en_translations = new LinkedHashMap<String, String>();
    
    final private LinkedHashMap<String, String> ar_translations = new LinkedHashMap<String, String>();


    @PostConstruct
    public void init() {
        ServiceConfigs.addConfigListener(this);
        this.onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
    }

    @PreDestroy
    public void destroy() {
        ServiceConfigs.removeConfigListener(this);
    }

    @Override
    public void onConfigsUpdated(Map<String, Object> newConfigs) {
        if(MapUtils.isNotEmpty(newConfigs) && newConfigs.get("whatsappSignup") instanceof Map<?, ?>) {
            Map<?, ?> whatsappSignupMap = (Map<?, ?>) newConfigs.get("whatsappSignup");
            if(whatsappSignupMap.get(SIGNUP_REPLY_MESSAGE) instanceof Map<?, ?>) {
                Map<?, ?> signupReplyMessageMap = (Map<?, ?>) whatsappSignupMap.get(SIGNUP_REPLY_MESSAGE);
                if(signupReplyMessageMap.get("en") != null &&
                        StringUtils.isNotEmpty(signupReplyMessageMap.get("en").toString())) {
                    en_translations.put(SIGNUP_REPLY_MESSAGE, (String) signupReplyMessageMap.get("en"));
                }
                if(signupReplyMessageMap.get("ar") != null &&
                        StringUtils.isNotEmpty(signupReplyMessageMap.get("ar").toString())) {
                    ar_translations.put(SIGNUP_REPLY_MESSAGE, (String) signupReplyMessageMap.get("ar"));
                }
            }

        } else {
            en_translations.clear();
            ar_translations.clear();
        }
    }

    @Override
    public GenericApiResponse<WhatsappSignupResponse> createWhatsappSignupLink(
            WhatsappSignupRequest requestBody, Map<String, Object> requestHeader) {
        GenericApiResponse<WhatsappSignupResponse> response = new GenericApiResponse<WhatsappSignupResponse>();

        try {
            final List<Stores> stores = Constants.getStoresList();

            Stores store = getStoreFromNumber(requestBody.getMobileNumber(), stores);

            /**
             * Valid mobileNumber check
             */
            if(StringUtils.isBlank(requestBody.getMobileNumber()) ||
                    !CommonUtility.isPossibleNumber(requestBody.getMobileNumber(), null) || store == null) {
                response.setStatus(false);
                response.setStatusCode("202");
                response.setStatusMsg(
                        (StringUtils.isBlank(requestBody.getMobileNumber()))
                                ? "mobileNumber cannot be empty or blank!" : "invalid mobile no!");
                return response;
            }

            String formattedNumber = getFormatterNumber(requestBody.getMobileNumber(), store);

            final long now = Instant.now().toEpochMilli();

            final String cacheKey = createCR32Hash(formattedNumber+ "."
                    + configService.getFirstExternalAuthBearerToken());

            WhatsappSignupBucketObject bucketObject = null;
            boolean needToWriteCache = false;
            try {
                Object object = redisHelper.get(
                        Constants.WHATSAPP_SIGNUP_CACHE_NAME, cacheKey, WhatsappSignupBucketObject.class);
                if(object instanceof WhatsappSignupBucketObject &&
                        ((WhatsappSignupBucketObject) object).getExpiresAt() != null &&
                        now <= ((WhatsappSignupBucketObject) object).getExpiresAt()) {
                    bucketObject = (WhatsappSignupBucketObject) object;
                }
            } catch (Exception e) {
            }

            if(bucketObject == null ) {
                needToWriteCache = true;
                bucketObject = new WhatsappSignupBucketObject();
                bucketObject.setMobileNo(formattedNumber);
                final  String name = ( (StringUtils.isNotEmpty(requestBody.getName()))
                        ? requestBody.getName() : "" );
                final String[] nameChunks = name.split(" ");
                final String firstName = (nameChunks.length > 0)? nameChunks[0] : "";
                final String lastName = (nameChunks.length > 0)
                        ? String.join(" ", Arrays.copyOfRange(nameChunks, 1, nameChunks.length)) : "";
                bucketObject.setFirstName(firstName);
                bucketObject.setLastName(lastName);
                bucketObject.setOriginAt(now);
                bucketObject.setUpdatedAt(now);
                TtlMode.WHATSAPP_SIGNUP.getValue();

                bucketObject.setExpiresAt(now + getExpiryPeriodMilli());
                bucketObject.setRequestCount(1);
            }

            final String code;
            if(needToWriteCache || StringUtils.isEmpty(bucketObject.getCode())) {
                needToWriteCache = true;
                String nowString = String.valueOf(now);
                nowString = nowString.substring(nowString.length() - 4);
                code = nowString.substring(0, 2) + "." + cacheKey + "." + nowString.substring(2);
                bucketObject.setCode(code);
            } else {
                code = bucketObject.getCode();
            }

            final String whatsappToken = createToken(code);


            if(StringUtils.isBlank(whatsappToken)) {
                response.setStatus(false);
                response.setStatusCode("204");
                response.setStatusMsg("Could not create the token!");
                return response;
            }

            if(needToWriteCache) {
                final boolean cachePutStatus = redisHelper.put(
                        Constants.WHATSAPP_SIGNUP_CACHE_NAME, cacheKey, bucketObject, TtlMode.WHATSAPP_SIGNUP);
                if(!cachePutStatus) {
                    response.setStatus(false);
                    response.setStatusCode("205");
                    response.setStatusMsg("Failed to save token!");
                    return response;
                }
            }

            final WhatsappSignupResponse responsePayload = new WhatsappSignupResponse();
            responsePayload.setToken(whatsappToken);

            String signupUrl = "";
            if(ObjectUtils.isNotEmpty(Constants.loginCredentials.getWhatsappSignupUrl())) {
                signupUrl =
                        Constants.loginCredentials.getWhatsappSignupUrl().replace("{{token}}",
                                URLEncoder.encode(whatsappToken, StandardCharsets.UTF_8.toString()));
                responsePayload.setUrl(signupUrl);
            }

            String message = "";
            try {
                if(StringUtils.isNotBlank(en_translations.get(SIGNUP_REPLY_MESSAGE))) {
                    message = en_translations.get(SIGNUP_REPLY_MESSAGE).replace("{{url}}", signupUrl);
                }
            } catch (Exception e) {
                LOGGER.error(e);
                message = "";
            }

            responsePayload.setMessage(message);


            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("Success!");
            response.setResponse(responsePayload);

        } catch (Exception e) {
            LOGGER.error(e);
            response.setStatus(false);
            response.setStatusCode("201");
            response.setStatusMsg("Something went wrong!");
            return response;
        }


        return response;
    }

    @Override
    public WhatsappSignupBucketObject getValidPayloadFromToken(String token) {
        WhatsappSignupBucketObject payloadObject = null;
        try {
            final String decryptedToken;
            final String[] decryptedTokenChunk;
            if(ObjectUtils.isNotEmpty(token) &&
                    StringUtils.isNotEmpty(decryptedToken = decryptValue(token)) &&
                    (decryptedTokenChunk = decryptedToken.split("\\.")).length == 3) {
                final long now = Instant.now().toEpochMilli();

                Object object = redisHelper.get(
                        Constants.WHATSAPP_SIGNUP_CACHE_NAME, decryptedTokenChunk[1], WhatsappSignupBucketObject.class);
                final WhatsappSignupBucketObject receivedObject;
                if( object instanceof WhatsappSignupBucketObject &&
                        decryptedToken.equalsIgnoreCase(
                                (receivedObject = (WhatsappSignupBucketObject) object).getCode()) &&
                        ObjectUtils.isNotEmpty(receivedObject.getExpiresAt()) && now <= receivedObject.getExpiresAt()) {
                    payloadObject = receivedObject;
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return payloadObject;
    }

    @Override
    public boolean clearToken(String token) {
        try {
            final String decryptedToken;
            final String[] decryptedTokenChunk;
            if(ObjectUtils.isNotEmpty(token) &&
                    StringUtils.isNotEmpty(decryptedToken = decryptValue(token)) &&
                    (decryptedTokenChunk = decryptedToken.split("\\.")).length == 3) {
                Object object = redisHelper.remove(Constants.WHATSAPP_SIGNUP_CACHE_NAME, decryptedTokenChunk[1]);
                return object != null;
            }
        } catch (Exception e) {
            LOGGER.error("error clearToken(): " + e.getMessage());
        }
        return false;
    }

    private String getFormatterNumber(String mobileNumber, Stores store) {
        String result = mobileNumber;
        try {
            if(StringUtils.isNotBlank(mobileNumber) && store != null &&
                    StringUtils.isNotBlank(store.getCountryCode())) {
                result = result.trim()
                        .replace(" ", "")
                        .replace("-", "")
                        .replaceFirst(Pattern.quote(store.getCountryCode()), "");
                result = store.getCountryCode() + " " + result;
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }


    private Stores getStoreFromNumber(String mobileNumber, List<Stores> stores) {
        Stores result = null;
        try {
            if(StringUtils.isNotBlank(mobileNumber) && CollectionUtils.isNotEmpty(stores)) {
                String number = mobileNumber.trim()
                        .replace(" ", "")
                        .replace("-", "");
                number = ((number.startsWith("+")) ? number : "+" + number);
                for (final Stores store : stores) {
                    if(StringUtils.isNotBlank(store.getCountryCode()) &&
                            number.startsWith(store.getCountryCode()) &&
                            number.substring(store.getCountryCode().length()).length() >= 8) {
                        result = store;
                        break;
                    }
                }

            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    private long getExpiryPeriodMilli() {
        return TtlMode.WHATSAPP_SIGNUP.getTimeUnit().toMillis(TtlMode.WHATSAPP_SIGNUP.getValue());
    }


    private String createToken(String code) {
        String result = "";
        if (StringUtils.isBlank(code))
            return result;
        try {
            result = encryptValue(code);
        } catch (Exception e) {
            LOGGER.error("createToken error: "+ e.getMessage());
        }

        return result;
    }

    private static Key getKey() {
        Key result = null;
        try {
            byte[] key = "bm90VG9TaGFyZVFXRVI="
                    .getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            result = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            LOGGER.error("Failed to create Encryption key: " + e.getMessage());
        }
        return result;
    }

    private String encryptValue(String input) {
        String result = "";
        try {
            if(encryptionKey != null && ObjectUtils.isNotEmpty(input)) {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
                result = Hex.encodeHexString(cipher.doFinal(input.getBytes("UTF-8")));;
            }
        } catch (Exception e) {
            LOGGER.error("encryptValue error: " + e.getMessage());
        }
        return result;
    }

    private String decryptValue(String input) {
        String result = "";
        try {
            if(encryptionKey != null && ObjectUtils.isNotEmpty(input)) {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
                cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
                result = new String(cipher.doFinal(Hex.decodeHex(input)));
            }
        } catch (Exception e) {
            LOGGER.error("decryptValue error: " + e.getMessage());
        }
        return result;
    }

    private String createCR32Hash(String input) {
        String result = "";
        try {
            byte[] salt = "bXlDUjMyU2FsdFF3ZXJ0eQ==".getBytes();
            ByteBuffer bbuffer = ByteBuffer.allocate(salt.length + input.length());
            bbuffer.put(salt);
            bbuffer.put(input.getBytes());

            //your CRC32 class
            CRC32 crc = new CRC32();
            crc.update(bbuffer.array());
            result = Long.toHexString(crc.getValue());
        } catch (Exception e) {
            LOGGER.error("createCR32Hash error: " + e.getMessage());
        }
        return result;
    }
}

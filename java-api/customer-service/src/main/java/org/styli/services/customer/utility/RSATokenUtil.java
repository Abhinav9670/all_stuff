package org.styli.services.customer.utility;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class RSATokenUtil {

    private RSATokenUtil() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated.");
    }

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING";

    // Token field keys
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_PHONE = "phoneNumber";
    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_STORE_ID = "storeId";
    public static final String FIELD_COUNTRY_CODE = "countryCode";
    public static final String FIELD_EXPIRES_AT = "expiresAt";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String encrypt(String plainText, PublicKey publicKey) throws RSATokenException {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RSATokenException("Encryption failed", e);
        }
    }

    public static String decrypt(String encryptedText, PrivateKey privateKey) throws RSATokenException {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RSATokenException("Decryption failed", e);
        }
    }

    public static String buildTokenWithExpiry(String email, String mobile, String firstName, String lastName, long validityMillis, Integer storeId) throws RSATokenSerializationException {
        try {
            if (storeId == null) {
                throw new IllegalArgumentException("Store ID cannot be null");
            }
            String countryCode = Constants.checkStoreIds(storeId);
            RsaTokenPayload payload = new RsaTokenPayload();
            payload.setEmail(email);
            payload.setMobile(mobile);
            payload.setFirstName(firstName);
            payload.setLastName(lastName);
            payload.setCountryCode(countryCode);
            payload.setStoreId(storeId);
            payload.setExpiresAt(System.currentTimeMillis() + validityMillis);
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RSATokenSerializationException("Failed to serialize RSA token payload", e);
        }
    }

    public static RsaTokenPayload parseToken(String decryptedToken) {
        try {
            return OBJECT_MAPPER.readValue(decryptedToken, RsaTokenPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token format", e);
        }
    }

    public static boolean isTokenExpired(String decryptedToken) {
        try {
            RsaTokenPayload payload = parseToken(decryptedToken);
            return System.currentTimeMillis() > payload.getExpiresAt();
        } catch (Exception e) {
            return true;
        }
    }

    public static Map<String, Object> extractCustomerDetails(String decryptedToken) {
        RsaTokenPayload payload = parseToken(decryptedToken);
        Map<String, Object> map = new HashMap<>();
        map.put(FIELD_EMAIL, payload.getEmail());
        map.put(FIELD_PHONE, payload.getMobile());
        map.put(FIELD_FIRST_NAME, payload.getFirstName());
        map.put(FIELD_LAST_NAME, payload.getLastName());
        map.put(FIELD_COUNTRY_CODE, payload.getCountryCode());
        map.put(FIELD_STORE_ID, payload.getStoreId());
        map.put(FIELD_EXPIRES_AT, Instant.ofEpochMilli(payload.getExpiresAt()).toString());
        return map;
    }
}

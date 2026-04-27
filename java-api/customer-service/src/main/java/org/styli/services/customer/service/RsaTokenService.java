package org.styli.services.customer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.styli.services.customer.pojo.EncryptedTokenResponse;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.utility.RSAKeyProvider;
import org.styli.services.customer.utility.RSATokenUtil;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
public class RsaTokenService {

    @Autowired
    private RSAKeyProvider rsaKeyProvider;

    private static final long DEFAULT_TOKEN_EXPIRY_MILLIS = 48L * 60 * 60 * 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(RsaTokenService.class);

    public EncryptedTokenResponse attachEncryptedRsaToken(String email, String phoneNumber, String firstName,
            String lastName, Integer storeId) {
        try {

            long tokenValidity = getTokenValidityMillisFromConsul();
            LOGGER.info("tokenValidity : " + tokenValidity);
            long expiry = System.currentTimeMillis() + tokenValidity;
            String token = RSATokenUtil.buildTokenWithExpiry(email, phoneNumber, firstName, lastName, tokenValidity,
                    storeId);
            String encrypted = RSATokenUtil.encrypt(token, rsaKeyProvider.getPublicKey());
            String isoExpiry = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(expiry));

            EncryptedTokenResponse response = new EncryptedTokenResponse();
            response.setToken(encrypted);
            response.setExpiry(isoExpiry);
            return response;
        } catch (Exception e) {
            LOGGER.info("RSA Encryption failed" + e);
            return null;
        }
    }

    public long getTokenValidityMillisFromConsul() {
        Object val = ServiceConfigs.consulServiceMap.get("influencerTokenExpiryInMinutes");

        if (val instanceof Integer integer) {
            return integer * 60_000L;
        } else if (val instanceof Long longVal) {
            return longVal * 60_000L;
        } else if (val instanceof String str) {
            try {
                return Long.parseLong(str) * 60_000L;
            } catch (NumberFormatException ignored) {
                LOGGER.warn("Invalid influencerTokenExpiryInMinutes format: {}", val);
            }
        }
        LOGGER.info("Using default token expiry value " + DEFAULT_TOKEN_EXPIRY_MILLIS);
        return DEFAULT_TOKEN_EXPIRY_MILLIS;
    }

    public boolean isInfluencerPortalFeatureEnabled(Integer storeId) {
        if (storeId == null)
            return false;

        if (Arrays.asList(1, 3).contains(storeId)) { // KSA
            return Boolean.TRUE.equals(ServiceConfigs.getInfluencerPortalFeatureFlagSA());
        } else if (Arrays.asList(7, 11).contains(storeId)) { // UAE
            return Boolean.TRUE.equals(ServiceConfigs.getInfluencerPortalFeatureFlagAE());
        } else if (Arrays.asList(12, 13).contains(storeId)) { // KW
            return Boolean.TRUE.equals(ServiceConfigs.getInfluencerPortalFeatureFlagKW());
        } else if (Arrays.asList(15, 17).contains(storeId)) { // QA
            return Boolean.TRUE.equals(ServiceConfigs.getInfluencerPortalFeatureFlagQA());
        } else if (Arrays.asList(19, 21).contains(storeId)) { // BH
            return Boolean.TRUE.equals(ServiceConfigs.getInfluencerPortalFeatureFlagBH());
        }
        return false;
    }

}

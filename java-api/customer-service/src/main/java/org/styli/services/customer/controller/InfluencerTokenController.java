package org.styli.services.customer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.styli.services.customer.pojo.RefreshTokenRequest;
import org.styli.services.customer.pojo.registration.request.Customer;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.InfluencerApiService;
import org.styli.services.customer.service.RsaTokenService;
import org.styli.services.customer.utility.RSATokenException;
import org.styli.services.customer.utility.RSATokenUtil;
import org.styli.services.customer.utility.RSAKeyProvider;
import org.styli.services.customer.utility.RsaTokenPayload;
import org.styli.services.customer.service.impl.ConfigServiceImpl;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import javax.annotation.Resource;
import javax.validation.Valid;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/rest/customer/influencer")
public class InfluencerTokenController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfluencerTokenController.class);

    // Constants
    private static final String SUCCESS = "success";
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";
    private static final String DATA = "data";
    private static final String TOKEN = "token";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String INVALID_TOKEN = "INVALID_TOKEN";
    private static final String TOKEN_IS_INVALID_MSG = "Token is missing, invalid, or expired.";
    private static final String WRONG_HEADERS = "Header Token is wrong.";

    @Resource
    private RSAKeyProvider rsaKeyProvider;

    @Resource
    private RsaTokenService rsaTokenService;

    @Autowired
    InfluencerApiService apiService;

    @Autowired
    ConfigServiceImpl configServiceImpl;

    @Autowired
    CustomerV4Service customerV4Service;

    @Value("${customer.jwt.flag}")
    String jwtFlag;

    @Autowired
    Client client;

    @PostMapping("/validate-rsa-token")
    public ResponseEntity<?> validateToken(
            @RequestHeader(value = "authorization-token", required = false) String authorizationHeader,
            @RequestBody Map<String, String> payload) {

        LOGGER.info("/validate-rsa-token : Inside Influencer Validate Token.");
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
            LOGGER.info("Missing authorization-token header.");
            return unauthorized(INVALID_TOKEN, TOKEN_IS_INVALID_MSG, "Missing authorization-token header.");
        }
        String authToken = authorizationHeader.trim();
        try {
            boolean isAuthorized = configServiceImpl.checkAuthorizationExternal(authToken);
            if (!isAuthorized) {
                LOGGER.info("Authorization failed via ConfigService.");
                return unauthorized(INVALID_TOKEN, WRONG_HEADERS, "Authorization failed.");
            }
        } catch (Exception ex) {
            LOGGER.info("Authorization verification error: " + ex);
            return unauthorized(INVALID_TOKEN, TOKEN_IS_INVALID_MSG, "Authorization verification failed.");
        }
        // Step 2: Token Payload Extraction and Expiry Check
        String encryptedToken = payload.get(TOKEN);
        if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
            return unauthorized(INVALID_TOKEN, TOKEN_IS_INVALID_MSG, null);
        }

        try {
            PrivateKey privateKey = rsaKeyProvider.getPrivateKey();
            LOGGER.info("/validate-rsa-token : Calling decrypt function");
            String decrypted = RSATokenUtil.decrypt(encryptedToken, privateKey);

            if (RSATokenUtil.isTokenExpired(decrypted)) {
                RsaTokenPayload tokenPayload = RSATokenUtil.parseToken(decrypted);
                long expiry = tokenPayload.getExpiresAt();
                String expiredAt = Instant.ofEpochMilli(expiry).toString();
                return unauthorized(INVALID_TOKEN, TOKEN_IS_INVALID_MSG, "Access token expired at " + expiredAt);
            }

            Map<String, Object> data = RSATokenUtil.extractCustomerDetails(decrypted);

            Map<String, Object> success = new HashMap<>();
            success.put(SUCCESS, true);
            success.put(STATUS, 200);
            success.put(MESSAGE, "Token is valid.");
            success.put(DATA, data);

            LOGGER.info("/validate-rsa-token : Exiting Influencer Validate Token.");
            return ResponseEntity.ok(success);

        } catch (RSATokenException e) {
            LOGGER.info("RSA token decryption failed" + e);
            return decryptFailed(INVALID_TOKEN, TOKEN_IS_INVALID_MSG, "Decryption failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.info("Unexpected error during token validation" + e);
            return unauthorized(INVALID_TOKEN, TOKEN_IS_INVALID_MSG, "Unexpected error: " + e.getMessage());
        }
    }

    private ResponseEntity<Object> decryptFailed(String code, String message, String details) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put(MESSAGE, message);
        if (details != null) {
            error.put("details", details);
        }

        Map<String, Object> response = new HashMap<>();
        response.put(SUCCESS, false);
        response.put(STATUS, 400);
        response.put(MESSAGE, message);
        response.put(ERROR, error);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @PostMapping("/refresh-rsa-token")
    public ResponseEntity<?> refreshToken(@RequestHeader Map<String, String> requestHeaders,
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        LOGGER.info("/refresh-rsa-token : Inside refresh Validate Token.");
        try {
            String jwtToken = requestHeaders.getOrDefault(TOKEN, requestHeaders.get("jwttoken"));
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                return badRequest("JWT token is missing from headers.");
            }

            if (!isValidJwt(requestHeaders, refreshTokenRequest)) {
                return badRequest("JWT token is invalid.");
            }

            String encryptedToken = refreshTokenRequest != null ? refreshTokenRequest.getToken() : null;
            String responseToken;
            long expiryTime;

            if (encryptedToken != null && !encryptedToken.trim().isEmpty()) {
                PrivateKey privateKey = rsaKeyProvider.getPrivateKey();
                String decrypted = RSATokenUtil.decrypt(encryptedToken, privateKey);
                RsaTokenPayload payload1 = RSATokenUtil.parseToken(decrypted);

                String email = payload1.getEmail();
                String mobile = payload1.getMobile();
                String firstName = payload1.getFirstName();
                String lastName = payload1.getLastName();
                Integer storeId = payload1.getStoreId();
                long originalExpiry = payload1.getExpiresAt();

                boolean isExpired = System.currentTimeMillis() > originalExpiry;
                if (isExpired) {
                    long tokenValidity = rsaTokenService.getTokenValidityMillisFromConsul();
                    expiryTime = System.currentTimeMillis() + tokenValidity;

                    String newToken = RSATokenUtil.buildTokenWithExpiry(
                            email, mobile, firstName, lastName, tokenValidity, storeId);
                    responseToken = RSATokenUtil.encrypt(newToken, rsaKeyProvider.getPublicKey());
                } else {
                    responseToken = encryptedToken;
                    expiryTime = originalExpiry;
                }
            } else {
                CustomerEntity customer = client.findByEntityId(refreshTokenRequest.getCustomerId());
                if (customer == null) {
                    return badRequest("Customer not found for given ID.");
                }

                String email = customer.getEmail();
                String mobile = customer.getPhoneNumber();
                String firstName = customer.getFirstName();
                String lastName = customer.getLastName();
                Integer storeId = customer.getStoreId();

                long tokenValidity = rsaTokenService.getTokenValidityMillisFromConsul();
                expiryTime = System.currentTimeMillis() + tokenValidity;

                String newToken = RSATokenUtil.buildTokenWithExpiry(email, mobile, firstName, lastName, tokenValidity,
                        storeId);
                responseToken = RSATokenUtil.encrypt(newToken, rsaKeyProvider.getPublicKey());
            }

            Map<String, Object> data = new HashMap<>();
            data.put(TOKEN, responseToken);
            data.put(EXPIRES_AT, Instant.ofEpochMilli(expiryTime).toString());

            Map<String, Object> success = new HashMap<>();
            success.put(SUCCESS, true);
            success.put(STATUS, 200);
            success.put(MESSAGE, "Token refreshed successfully.");
            success.put(DATA, data);

            LOGGER.info("/refresh-rsa-token : Exiting Influencer Refresh Token.");
            return ResponseEntity.ok(success);

        } catch (Exception e) {
            LOGGER.info("Failed to refresh token" + e);
            return badRequest("Token is invalid or corrupted.");
        }
    }

    private boolean isValidJwt(Map<String, String> requestHeaders, RefreshTokenRequest refreshTokenRequest) {
        try {
            validateJwtToken(requestHeaders, refreshTokenRequest);
            return true;
        } catch (Exception e) {
            LOGGER.error("JWT token validation failed: ", e);
            return false;
        }
    }


    private void validateJwtToken(Map<String, String> requestHeaders,
            RefreshTokenRequest refreshTokenRequest) {
        if ("1".equals(jwtFlag)) {
            customerV4Service.authenticateCheck(requestHeaders, refreshTokenRequest.getCustomerId());
        }
    }

    @GetMapping("/fetch-influencer-data")
    public ResponseEntity<Map<String, Object>> fetchAndPublishForLast24Hours(
            @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

        Map<String, Object> response = new HashMap<>();

        if (configServiceImpl.checkAuthorizationInternal(authorizationToken)) {
            boolean useManualDateRange = ServiceConfigs.getInfluencerManualDateRangeFlag();
            String dateRangeInfo = useManualDateRange ? "manual date range from Consul" : "last 24 hours";
            
            LOGGER.info("Triggered fetch-and-publish for " + dateRangeInfo + ".");
            apiService.fetchEmailsAndPublishToPubSub();

            response.put(STATUS, true);
            response.put(MESSAGE, "Emails fetched (" + dateRangeInfo + ") and published.");
            return ResponseEntity.ok(response);
        } else {
            response.put(STATUS, false);
            response.put(MESSAGE, "You're not authenticated to perform the operation.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    private ResponseEntity<Object> unauthorized(String code, String message, String details) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put(MESSAGE, message);
        if (details != null) {
            error.put("details", details);
        }

        Map<String, Object> response = new HashMap<>();
        response.put(SUCCESS, false);
        response.put(STATUS, 401);
        response.put(MESSAGE, message);
        response.put(ERROR, error);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    private ResponseEntity<Object> badRequest(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put(MESSAGE, message);

        Map<String, Object> response = new HashMap<>();
        response.put(SUCCESS, false);
        response.put(STATUS, 400);
        response.put(ERROR, error);
        return ResponseEntity.badRequest().body(response);
    }
}

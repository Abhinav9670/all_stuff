package org.styli.services.customer.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.styli.services.customer.helper.EmailHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.pojo.EncryptedTokenResponse;
import org.styli.services.customer.pojo.MagicLinkRequest;
import org.styli.services.customer.pojo.MagicLinkResponse;
import org.styli.services.customer.pojo.MagiclinkValidationRequest;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.LoginType;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.MagicLinkService;
import org.styli.services.customer.service.RsaTokenService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.TokenUtility;

import java.time.Instant;
import java.util.*;
import java.util.Date;

/**
 * @author Biswabhusan Pradhan
 * @project customer-service
 */
@Service
public class MagicLinkServiceImpl implements MagicLinkService {

    @Autowired
    EmailHelper emailHelper;

    @Autowired
    TokenUtility tokenUtility;

    @Autowired
    JwtValidator jwtValidator;

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    RedisHelper redisHelper;

    @Autowired
    CustomerV4Service customerV4Service;

    @Value("${secret.react.java.api}")
    private String secretReactJavaApi;

    @Autowired
    private RsaTokenService rsaTokenService;

    @Autowired
    private PasswordHelper passwordHelper;

    @Value("${customer.jwt.flag}")
    String jwtFlag;

    private static final Log LOGGER = LogFactory.getLog(MagicLinkServiceImpl.class);

    static final String CACHE_NAME = "otp-bucket";

    @Override
    public MagicLinkResponse createAndSendMagicLink(MagicLinkRequest request, String magicLinkBaseUrl,
            String mailSubject, String mailContent) {
        LOGGER.info("Triggering magiclink for email: " + request.getEmail());
        MagicLinkResponse response = new MagicLinkResponse();
        CustomerEntity customer = customerEntityRepository.findByEmail(request.getEmail());
        String username = "";
        if (customer != null) {
            String firstName = customer.getFirstName() != null ? customer.getFirstName() : "";
            String lastName = customer.getLastName() != null ? customer.getLastName() : "";
            if (!firstName.isEmpty() || !lastName.isEmpty()) {
                username = String.join(" ", firstName, lastName).trim();
            } else {
                username = "Customer ";
            }
        }
        if (!request.getType().isBlank() && request.getType().equalsIgnoreCase(Constants.VALIDATE) && customer != null
                && customer.getIsEmailVerified() != null && customer.getIsEmailVerified()) {
            LOGGER.info("Email address already verified : " + request.getEmail());
            response.setEmail(request.getEmail());
            response.setStatusMessage("Email address already verified.");
            response.setStatus(false);
            response.setStatusCode(202);
            return response;
        }

        LinkedHashMap<String, String> additionalParams = request.getAdditionalParams() != null
                ? request.getAdditionalParams()
                : new LinkedHashMap<>();
        String additionalParamsString = "";
        for (Map.Entry<String, String> entry : additionalParams.entrySet()) {
            additionalParamsString = additionalParamsString.concat("&" + entry.getKey() + "=" + entry.getValue());
        }
        String token = tokenUtility.createToken(request.getEmail());
        String magicLink = magicLinkBaseUrl.replace("{{store-id}}", request.getStore().toLowerCase())
                .replace("{{lang-code}}", request.getLangCode())
                .replace("{{type}}", request.getType())
                .replace("{{username}}", username)
                .replace("{{email}}", request.getEmail())
                .replace("{{redirectionPage}}", request.getRedirectUrl()).replace("{{token}}", token)
                .concat(additionalParamsString);

        mailContent = mailContent.replace("{{username}}", username)
                .replace("{{email}}", request.getEmail())
                .replace("{{magic-link}}", magicLink);

        boolean emailSent = emailHelper.sendEmail(request.getEmail(), "user", mailContent,
                EmailHelper.CONTENT_TYPE_HTML, mailSubject, request.getLangCode());

        String statusMessage = emailSent ? "Verification mail has been sent to your email."
                : "Failed to send verification mail to your email.";
        response.setEmail(request.getEmail());
        response.setStatus(emailSent);
        response.setStatusCode(emailSent ? 200 : 202);
        response.setStatusMessage(statusMessage);
        LOGGER.info(statusMessage + " : " + request.getEmail());
        return response;
    }

    @Override
    public MagicLinkResponse validateMagicLink(MagiclinkValidationRequest magicLinkRequestBody,
            Map<String, String> requestHeader) {
        MagicLinkResponse response = new MagicLinkResponse();
        String token = magicLinkRequestBody.getToken();
        String type = magicLinkRequestBody.getType();
        CustomerLoginV4Response loginResponse = new CustomerLoginV4Response();
        Customer customerLogin = null;
        JwtUser jwtUser = jwtValidator.validate(token);
        if (jwtUser.getJwtError() != null) {
            response.setStatus(false);
            response.setStatusCode(202);
            response.setStatusMessage("Request token has expired!");
            LOGGER.info("Request token has expired!");
            return response;
        }

        long now = Instant.now().toEpochMilli();
        long expiry = jwtUser.getExpiry().getTime();
        String email = jwtUser.getUserId();

        if (now > expiry) {
            response.setStatus(false);
            response.setStatusCode(202);
            response.setStatusMessage("Request token has expired!");
            LOGGER.info("Request token has expired!");
            return response;
        }
        String headerEmail = requestHeader.get("x-header-token");

        if (email != null && headerEmail != null) {
            CustomerEntity customer = customerEntityRepository.findByEmail(headerEmail);
            if (customer != null) {
                // Email validation: Check if email from x-header-token matches email in database
                if (StringUtils.isNotBlank(headerEmail) && !headerEmail.equalsIgnoreCase(email)) {
                    LOGGER.info("Email validation failed: Header email [" + headerEmail + "] doesn't match token email [" + email + "]");
                    
                    // For validate type, generate new JWT token with DB email and customer ID
                    if (Constants.VALIDATE.equalsIgnoreCase(type)) {
                        try {
                            // Generate JWT token directly with customer ID
                            String jwtTokenWithCustomerId = passwordHelper.generateToken(email,
                                    String.valueOf(java.time.Instant.now().toEpochMilli()), // code
                                customer.getEntityId(), // customer ID from database
                                false // refresh token flag
                            );
                            
                            if (jwtTokenWithCustomerId != null && !jwtTokenWithCustomerId.isEmpty()) {
                                // Save email verification status to Redis for validate type
                                saveVerificationStatusInRedis(email, "true");
                                LOGGER.info("Saved email verification status to Redis for validate type: " + email);
                                
                                response.setNewJwtToken(jwtTokenWithCustomerId);
                                response.setStatus(true);
                                response.setStatusCode(200);
                                response.setStatusMessage("New JWT token generated with DB email and customer ID.");
                                response.setEmail(email);
                                LOGGER.info("Generated JWT token with DB email and customer ID for validate type: " + customer.getEntityId());
                                return response;
                            } else {
                                LOGGER.error("Failed to generate JWT token for validate type - token is null or empty");
                                response.setStatus(false);
                                response.setStatusCode(500);
                                response.setStatusMessage("Failed to generate JWT token. Please try again.");
                                return response;
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error generating JWT token for validate type when emails don't match: " + e.getMessage());
                            response.setStatus(false);
                            response.setStatusCode(500);
                            response.setStatusMessage("Failed to generate JWT token. Please try again.");
                            return response;
                        }
                    }
                    
                    response.setStatus(false);
                    response.setStatusCode(400);
                    response.setStatusMessage("Email ID doesn't match");
                    return response;
                }
                
                if (null != customer.getIsEmailVerified() && customer.getIsEmailVerified()
                        && !Constants.LOGIN.equalsIgnoreCase(type)) {
                    response.setEmail(email);
                    response.setStatusCode(200);
                    response.setStatusMessage("Email address already verified.");
                    response.setStatus(true);
                    LOGGER.info("Email address already verified : " + email);
                    return response;
                } else if (Constants.LOGIN.equalsIgnoreCase(type)) {
                    if (customer.getIsEmailVerified() == null || !customer.getIsEmailVerified()) {
                        customer.setIsEmailVerified(true);
                        customerEntityRepository.save(customer);
                        redisHelper.put("customer_verification", email, true);
                    }
                    loginResponse = doLoginTaskEmail(email, true, requestHeader);
                    LOGGER.info(loginResponse.getStatusMsg() + " : " + email);
                    if (!"200".equals(loginResponse.getStatusCode()) || loginResponse.getResponse() == null) {
                        response.setStatus(loginResponse.isStatus());
                        response.setStatusCode(Integer.valueOf(loginResponse.getStatusCode()));
                        response.setStatusMessage(loginResponse.getStatusMsg());
                        return response;
                    } else {
                        customerLogin = loginResponse.getResponse().getCustomer();

                        // Set isInfluencer from CustomerEntity
                        if (customerLogin != null) {
                            customerLogin.setIsInfluencer(Boolean.TRUE.equals(customer.getIsInfluencer()));
                        }

                        response.setCustomer(customerLogin);
                        response.setStatus(loginResponse.isStatus());
                        response.setStatusCode(Integer.valueOf(loginResponse.getStatusCode()));
                        response.setStatusMessage(loginResponse.getStatusMsg());

                        if ("200".equals(loginResponse.getStatusCode()) && customerLogin != null) {
                            Integer storeIdFromPayload = customerLogin.getStoreId();

                            if (customerLogin.getEmail() != null && customerLogin.getMobileNumber() != null
                                    && customerLogin.getFirstName() != null
                                    && rsaTokenService.isInfluencerPortalFeatureEnabled(storeIdFromPayload)) {

                                EncryptedTokenResponse tokenResponse = rsaTokenService.attachEncryptedRsaToken(
                                        customerLogin.getEmail(), customerLogin.getMobileNumber(),
                                        customerLogin.getFirstName(),
                                        customerLogin.getLastName() != null ? customerLogin.getLastName() : "",
                                        storeIdFromPayload);

                                if (tokenResponse != null) {
                                    response.setEncryptedRsaToken(tokenResponse.getToken());
                                    response.setEncryptedRsaTokenExpiry(tokenResponse.getExpiry());
                                }
                            }
                        }
                        return response;
                    }
                } else {
                    customer.setIsEmailVerified(true);
                    customerEntityRepository.save(customer);
                    redisHelper.put("customer_verification", email, true);

                    response.setStatusCode(200);
                    response.setStatus(true);
                    response.setStatusMessage("Email verified and customer data updated.");
                    response.setEmail(email);
                    
                    return response;
                }
            } else {
                LOGGER.info("Validate Email : " + email);
                saveVerificationStatusInRedis(email, "true");
                
                response.setEmail(email);
                response.setStatusCode(200);
                response.setStatusMessage("Email verified for new user.");
                response.setStatus(true);
                
                return response;
            }
        } else {
            response.setStatus(false);
            response.setStatusCode(404);
            response.setStatusMessage("Email not found.");
        }

        return response;
    }

    public CustomerLoginV4Response doLoginTaskEmail(String email, Boolean isUserConsentProvided,
            Map<String, String> requestHeader) {
        CustomerLoginV4Response response = null;
        try {
            LOGGER.info("magiclink validate : doLoginTaskEmail: " + email);
            CustomerLoginV4Request v4Request = new CustomerLoginV4Request();
            v4Request.setIsOtpVerified(true);
            v4Request.setUseridentifier(email);
            v4Request.setLoginType(LoginType.EMAIL_LOGIN);
            v4Request.setPassword(secretReactJavaApi);
            if (isUserConsentProvided != null) {
                v4Request.setIsUserConsentProvided(isUserConsentProvided);
            }
            response = customerV4Service.getCustomerLoginV4Details(v4Request, requestHeader);
        } catch (Exception e) {
            LOGGER.error("Exception in doLoginTaskEmail for email: " + email, e);
        }
        return response;
    }

    public void saveVerificationStatusInRedis(String userIdentifier, String isVerified) {
        String key = userIdentifier;

        LOGGER.info("saveVerificationStatusInRedis: Saving verification status for identifier: " + key
                + "with status isVerified :" + isVerified);

        try {
            boolean success = redisHelper.put(CACHE_NAME, key, isVerified, TtlMode.OTP_REDIS);

            if (success) {
                LOGGER.info(
                        "saveVerificationStatusInRedis: Verification status saved successfully for identifier: " + key);
            } else {
                LOGGER.info("saveVerificationStatusInRedis: Failed to save verification status for identifier: " + key);
            }
        } catch (Exception e) {
            LOGGER.error("saveVerificationStatusInRedis: Error saving verification status for identifier: " + e);
        }
    }

}

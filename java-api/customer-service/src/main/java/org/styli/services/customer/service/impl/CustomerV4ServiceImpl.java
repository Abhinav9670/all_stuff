package org.styli.services.customer.service.impl;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.Valid;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.client.json.Json;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.exception.BadRequestException;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.helper.ExternalQuoteHelper;
import org.styli.services.customer.helper.LoginCapchaHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.pojo.CustomerOmsResponsedto;
import org.styli.services.customer.pojo.CustomerRequestBody;
import org.styli.services.customer.pojo.CustomerVerificationStatusResponse;
import org.styli.services.customer.pojo.FirstFreeShipping;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.epsilon.EpsilonBucketObject;
import org.styli.services.customer.pojo.epsilon.response.DeleteShukranResponse;
import org.styli.services.customer.pojo.epsilon.response.ShukranProfileData;
import org.styli.services.customer.pojo.epsilon.request.LinkShukranRequest;
import org.styli.services.customer.pojo.epsilon.request.UpgradeShukranTierActivityRequest;
import org.styli.services.customer.pojo.epsilon.response.BuildUpgradeShukranTierActivityResponse;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.epsilon.response.EnrollmentResponse;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.webhook.ShukranWebhookRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookResponse;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerPasswordRequest;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.request.CustomerValidityCheckRequest;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.MobileNumberUpdateAcknowledgmentRequest;
import org.styli.services.customer.pojo.registration.request.WhatsAppOtpRequest;
import org.styli.services.customer.pojo.registration.response.*;
import org.styli.services.customer.pojo.response.CustomerOmsResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Address.CustomerAddressEntityVarcharRepository;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerGridFlatRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.ExternalServiceAdapter;
import org.styli.services.customer.service.WhatsappService;
import org.styli.services.customer.service.impl.Address.DeleteAddress;
import org.styli.services.customer.service.impl.Address.GetAddress;
import org.styli.services.customer.service.impl.Address.SaveAddress;
import org.styli.services.customer.utility.Constants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.pojo.config.ShukranNextTier;
import org.styli.services.customer.utility.pojo.config.ShukranQualifingPurchase;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.Stores;

@Component
@Scope("singleton")
public class CustomerV4ServiceImpl implements CustomerV4Service,  ServiceConfigs.ServiceConfigsListener {

    private static final Log LOGGER = LogFactory.getLog(CustomerV4ServiceImpl.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private  Map<?, ?> saveEmailTranslation = new LinkedHashMap();

    public static final String BAD_REQUEST = "Bad Request!!";

    @Autowired
    PasswordHelper passwordHelper;

    @Autowired
    Client client;

    @Autowired
    LoginCapchaHelper loginCapchaHelper;

    @Autowired
    RedisHelper redisHelper;

    @Value("${magento.base.url}")
    private String magentoBaseUrl;

    @Value("${secret.react.java.api}")
    private String secretReactJavaApi;

    @Value("${consul.ip.address}")
    String consulIpAddress;

    @Value("${env}")
    String env;

    @Value("${customer.jwt.flag}")
    String jwtFlag;

    @Value("${vm.url}")
    private String vmUrl;

    @Value("${recaptcha.secret.key}")
    private String recaptchaSecretKey;

    @Value("${google.recaptcha.verify.url}")
    private String recaptchaVerifyUrl;

    @Autowired
    ElasticProductHelperV5 elasticProductHelperV5;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    CustomerAddressEntityRepository customerAddressEntityRepository;

    @Autowired
    GetCustomer getCustomer;

    @Autowired
    StaticComponents staticComponents;

    @Autowired
    CustomerGridFlatRepository customerGridFlatRepository;

    @Autowired
    CustomerAddressEntityVarcharRepository customerAddressEntityVarcharRepository;

    @Autowired
    private JwtValidator validator;

    @Autowired
    private SaveCustomer saveCustomer;

    @Autowired
    AddWishlist addWishlist;

    @Autowired
    ValidateUser validateUser;

	@Autowired
    LoginUser loginUser;

    @Autowired
    GoogleSigninHelper googleSigninHelper;


    @Autowired
    UpdateUser updateUser;

    @Autowired
    private SaveAddress saveAddress;

    @Autowired
    private GetAddress getAddress;

    @Autowired
    WhatsappService whatsappService;

    @Autowired
    SequenceCustomerEntityRepository sequenceCustomerEntityRepository;

    @Autowired
	@Qualifier("gccMongoTemplate")
	private MongoTemplate mongoGccTemplate;

    @Autowired
    private ExternalServiceAdapter externalServiceAdapter;

    @Autowired
    private ExternalQuoteHelper externalQuoteHelper;

    private ShukranQualifingPurchase shukranQualifingPurchase;
    private ShukranNextTier shukranNextTier;
    private boolean redundantShukranProfileApiCall = false;
    private boolean disablePhoneNumberProfileAPI = false;

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
        if (ObjectUtils.isNotEmpty(newConfigs.get("redundantShukranProfileApiCall"))) {
            redundantShukranProfileApiCall = (boolean) newConfigs.get("redundantShukranProfileApiCall");
        } else {
            redundantShukranProfileApiCall = false;
        }
        if (ObjectUtils.isNotEmpty(newConfigs.get("disablePhoneNumberProfileAPI"))) {
            disablePhoneNumberProfileAPI = (boolean) newConfigs.get("disablePhoneNumberProfileAPI");
        } else {
            disablePhoneNumberProfileAPI = false;
        }
        if(MapUtils.isNotEmpty(newConfigs) && newConfigs.get("save_email_message") instanceof Map<?, ?>) {
            saveEmailTranslation = (Map<?, ?>) newConfigs.get("save_email_message");
        }

        StoreConfigResponse storeConfigResponse = Constants.StoreConfigResponse;
        shukranQualifingPurchase = storeConfigResponse.getShukranQualifingPurchase();
        shukranNextTier = storeConfigResponse.getShukranNextTier();
    }

    @Override
    @Transactional
    public CustomerV4RegistrationResponse saveV4Customer(CustomerV4Registration customerInfoRequest,
                                                         Map<String, String> requestHeader) throws CustomerException {

        CustomerV4RegistrationResponse response =  saveCustomer.saveCustomer(customerInfoRequest,
                requestHeader,
                passwordHelper,
                client,
                jwtFlag);
        try{
            // If SHUKRAN Enable - TRUE
            // Add shukranWelcomeBonous
            if (isValidRegistrationResponse(response) && isShukranEnabledForRegResponse(response)) {
                Integer storeId = response.getResponse().getCustomer().getStoreId();
                response.getResponse().getCustomer().setShukranWelcomeBonous(buildShukranWelcomeBonous(storeId));
            }
        } catch (Exception e) {
            LOGGER.error("Exception in adding shukran welcome bonus for customer  : "+customerInfoRequest.getCustomerInfo().getCustomer().getEmail(),e);
        }

        return response;
    }

    /**
     * Validates the customer registration response to ensure it contains all the required data.
     *
     **/
    private boolean isValidRegistrationResponse(CustomerV4RegistrationResponse response) {
        return response != null &&
                Objects.equals(response.getStatusCode(), "200") &&
                response.getResponse() != null &&
                response.getResponse().getCustomer() != null &&
                response.getResponse().getCustomer().getStoreId() != null;
    }

    /**
     * Checks if Shukran is enabled for the customer in the provided response.
     *
     * This method determines whether Shukran is enabled for a specific store
     * based on the `storeId` parameter. If the `storeId` parameter is null,
     * the method retrieves the store ID from the customer information in the response.
     **/
    private boolean isShukranEnabledForRegResponse(CustomerV4RegistrationResponse response) {
        Integer storeId = response.getResponse().getCustomer().getStoreId();
        return checkIsShukranEnableForStoreId(storeId);
    }

    /**
     * Retrieves the Shukran Welcome Bonus for a specific store based on the store ID.
     *
     * This method filters the list of stores by matching the provided storeId to the store's ID,
     * and then retrieves the Shukran Welcome Bonus associated with that store. If the store is found,
     * the corresponding bonus is returned. If no matching store is found, the method returns 0 as the default value.
     * @param storeId The ID of the store for which the Shukran Welcome Bonus is to be retrieved.
     * @return The Shukran Welcome Bonus for the specified store, or 0 if no matching store is found.
     */
    private Integer buildShukranWelcomeBonous(Integer storeId) {
        return Constants.getStoresList().stream()
                .filter(store -> storeId.equals(Integer.parseInt(store.getStoreId())))
                .map(Stores::getShukranWelcomeBonous)
                .findFirst()
                .orElse(0);
    }

    /**
    *
    */
    @Override
    @Transactional(readOnly = true)
    public CustomerExistResponse validateUser(CustomerQueryReq customerExitsReq,
    		Map<String, String> requestHeader) {

        return validateUser.validate(customerExitsReq, requestHeader, client);
    }

    /**
     * @throws CustomerException
     *
     */
    @Override
    @Transactional
    public CustomerLoginV4Response getCustomerLoginV4Details(CustomerLoginV4Request customerLoginRequest,
                                                             Map<String, String> requestHeader) throws CustomerException {

        if(customerLoginRequest != null) {
            return loginUser.login(customerLoginRequest,
                    requestHeader,
                    passwordHelper,
                    client,
                    secretReactJavaApi,
                    jwtFlag,
                    loginCapchaHelper,
                    googleSigninHelper,
                    whatsappService,
                    saveCustomer);
        }else{
            CustomerLoginV4Response customerLoginV4Response = new CustomerLoginV4Response();
            customerLoginV4Response.setStatus(false);
            customerLoginV4Response.setStatusMsg("Bad Request");
            customerLoginV4Response.setStatusCode("400");
            return customerLoginV4Response;
        }

    }

    @Override
    @Transactional
    public CustomerUpdateProfileResponse updateCustomer(CustomerUpdateProfileRequest customerInfoRequest,
            Map<String, String> requestHeader) {
        //API-4104 Don't update phone number in update Profile API
        if (disablePhoneNumberProfileAPI) {
            LOGGER.info("In UpdateCustomer:  disablePhoneNumberProfileAPI: " + disablePhoneNumberProfileAPI);
            LOGGER.info("In UpdateCustomer:  phoneNumber before disablePhoneNumberProfileUpdate: " + customerInfoRequest.getMobileNumber());
            customerInfoRequest = updateUser.disablePhoneNumberProfileUpdate(customerInfoRequest,client);
            LOGGER.info("In UpdateCustomer:  phoneNumber after disablePhoneNumberProfileUpdate: " + customerInfoRequest.getMobileNumber());
        }
        CustomerUpdateProfileResponse profileUpdateResponse = updateUser.update(customerInfoRequest, client, requestHeader.get(Constants.deviceId));;
        //If valid customer response, update shukran profile
        if (isValidCustomerResponse(profileUpdateResponse)) {
            //Find complete customer info to get storeId and shukran link flag
            CustomerUpdateProfileResponse customerResponse = getCustomerData(customerInfoRequest);
            ObjectMapper objectMapper= new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            try {
                LOGGER.info("customer response in update customer " + objectMapper.writeValueAsString(customerResponse));
                boolean shukranLinkFlag = customerResponse.getResponse().getCustomer().isShukranLinkFlag();
                Customer customer = customerResponse.getResponse().getCustomer();
                try {
                    if(StringUtils.isNotBlank(customer.getMobileNumber()) && StringUtils.isNotEmpty(customer.getMobileNumber())) {
                        if (shukranLinkFlag) {
                            customerInfoRequest.setMobileNumber(customer.getMobileNumber());
                            ResponseEntity<String> epsilonUpdateProfileResponse = externalServiceAdapter.updateEpsilonProfile(customerInfoRequest, customer.getProfileId());
                            LOGGER.info("Epsilon API call Update Profile response : " + epsilonUpdateProfileResponse);
                            if (epsilonUpdateProfileResponse.getStatusCode().is2xxSuccessful()) {
                                LOGGER.info("Successfully updated epsilon profile for customerId : " + customerInfoRequest.getCustomerId());
                            } else {
                                LOGGER.error("Error in updating epsilon profile for customerId : " + customerInfoRequest.getCustomerId());
                            }
                            profileUpdateResponse.getResponse().setShukranLinkFlag(true);
                            profileUpdateResponse.getResponse().setShukranProfileExists(true);
                            profileUpdateResponse.getResponse().setProfileId(customer.getProfileId());
                        } else {
                            ResponseEntity<String> shukranProfileResponse = externalServiceAdapter.getEpsilonProfile(customer.getMobileNumber(), customer.getStoreId());
                            LOGGER.info("Shukran get profile API call response : " + shukranProfileResponse);
                            if (shukranProfileResponse.getStatusCode().is2xxSuccessful()) {
                                JSONObject profileJson = new JSONObject(shukranProfileResponse.getBody());
                                profileUpdateResponse.getResponse().setShukranLinkFlag(false);
                                profileUpdateResponse.getResponse().setShukranProfileExists(true);
                                profileUpdateResponse.getResponse().setProfileId(getJsonString(profileJson, Constants.PROFILE_ID));
                            }
                        }
                    }
                    // Update customer phoneNumber in customer quote if it exists
                    updateQuoteWithPhoneNumber(customer, requestHeader);
                } catch (Exception e) {
                    LOGGER.error("Exception in Updating epsilon profile for customer" + customerInfoRequest.getCustomerId(), e);
                    buildDefaultShukranData(profileUpdateResponse, shukranLinkFlag, customer);
                }
            }catch (JsonProcessingException ex){
                LOGGER.info("Json Processing Exception "+ ex.getMessage());
            }
        }

        return profileUpdateResponse;
    }
    private CustomerUpdateProfileResponse buildDefaultShukranData(CustomerUpdateProfileResponse profileUpdateResponse,
                                                                  boolean shukranLinkFlag,Customer customer) {
        if (shukranLinkFlag) {
            profileUpdateResponse.getResponse().setShukranLinkFlag(true);
            profileUpdateResponse.getResponse().setShukranProfileExists(true);
            profileUpdateResponse.getResponse().setProfileId(customer.getProfileId());
        }
        return profileUpdateResponse;
    }
    private void updateQuoteWithPhoneNumber(Customer customer,Map<String, String> requestHeader) {
        String mobileNumber = customer.getMobileNumber();
        if (mobileNumber != null && !mobileNumber.isEmpty()) {
            getAndupdateQuoteInfo(customer.getCustomerId(), customer.getStoreId(), mobileNumber, requestHeader);
        }
    }
    private CustomerUpdateProfileResponse getCustomerData(CustomerUpdateProfileRequest customerUpdateProfileRequest) {
        return getCustomer.get(customerUpdateProfileRequest.getCustomerId(), client, jwtFlag, null);
    }

    @Override
    @Transactional
    public CustomerUpdateProfileResponse getCustomerDetails(CustomerRequestBody request, Map<String, String> requestHeader) {
        try {
            CustomerUpdateProfileResponse response = getCustomer.get(request.getCustomerId(), client, jwtFlag, request.getCustomerEmail(), null, customerAddressEntityRepository);
            //If SHUKRAN ENABLE is not  TRUE or get customer is not SUCCESS  return response
            if (!isValidCustomerResponse(response) || !isShukranEnabledForCustomerResponse(response,request.getStoreId())) {
                response.getResponse().setIsShukranEnable(false);
                return response;
            }
                String mobileNo = response.getResponse().getCustomer().getMobileNumber();
                Integer storeId = null != request.getStoreId() ? request.getStoreId() : response.getResponse().getCustomer().getStoreId();
                try {
                    response.getResponse().setIsShukranEnable(true);
                    if(StringUtils.isNotBlank(mobileNo) && StringUtils.isNotEmpty(mobileNo)) {
                        ResponseEntity<String> shukranProfileResponse = externalServiceAdapter.getEpsilonProfile(mobileNo, storeId);
                        LOGGER.info("Shukran get profile API call response : " + shukranProfileResponse);
                        if (shukranProfileResponse.getStatusCode().is2xxSuccessful()) {
                            JSONObject profileJson = new JSONObject(shukranProfileResponse.getBody());
                            LOGGER.info("Successfully fetched epsilon profile for mobileNo : " + mobileNo + " and customer : " + request.getCustomerId());
                            ShukranProfileData shukranProfileData = buildShukranProfileData(profileJson, storeId);
                            response.getResponse().setShukranProfileData(shukranProfileData);
                            //Show pop up logic
                            response.getResponse().setShowPopup(false);
                            String tierName = response.getResponse().getCustomer().getTierName();
                            String shukranTierName = shukranProfileData.getTierName();
                            String tierActivity = shukranProfileData.getTierActivity();
                            boolean isTierNameValid = tierName != null && !tierName.isEmpty() && !tierName.isBlank();
                            boolean isTierNameDifferent = isTierNameValid && !tierName.equalsIgnoreCase(shukranTierName);
                            boolean isUpgradeActivity = "upgrade".equalsIgnoreCase(tierActivity);

                            if (isTierNameDifferent && isUpgradeActivity) {
                                response.getResponse().setShowPopup(true);
                            } else {
                                // Save tier activity in customer entity
                                getCustomer.setShukranTierActivity(
                                        request.getCustomerId(),
                                        client,
                                        tierActivity,
                                        shukranTierName,
                                        shukranProfileData
                                );
                            }

                            //Shukran profile exists
                            response.getResponse().setShukranProfileExists(true);
                            response.getResponse().setShukranLinkFlag(response.getResponse().getCustomer().isShukranLinkFlag());
                        } else {
                            if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                                LOGGER.info("[enableCustomerServiceErrorHandling] Epsilon profile fetch returned non-2xx status. MobileNo: " + mobileNo + ", CustomerId: " + request.getCustomerId() + ", Status: " + shukranProfileResponse.getStatusCode());
                            } else {
                                LOGGER.error("Error in fetching epsilon profile for mobileNo : " + mobileNo + " belongs customer : " + request.getCustomerId());
                            }
                            response.getResponse().setShukranProfileExists(false);
                            response.getResponse().setShukranLinkFlag(false);
                            getCustomer.setShukranLinkFlag(request.getCustomerId(), client, false, null, null);
                        }
                    }
                } catch (HttpClientErrorException e) {
                    if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                        LOGGER.info("[enableCustomerServiceErrorHandling] Shukran Get profile API client Error (4xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
                    } else {
                        LOGGER.error("Shukran Get profile API client Error (4xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
                    }
                    //Shukran profile not exists
                    response.getResponse().setShukranProfileExists(false);
                    response.getResponse().setShukranLinkFlag(false);
                    // If UNAUTHORIZED set ShukranAPIFailing true
                    if (e.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
                        response = buildGetCustomerFailedResponse(response);
                    }
                } catch (HttpServerErrorException e) {
                    // Handle 5XX errors (Server Errors)
                    if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                        LOGGER.info("[enableCustomerServiceErrorHandling] Shukran Get profile API Server Error (5xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
                    } else {
                        LOGGER.error("Shukran Get profile API Server Error (5xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
                    }
                    response = buildGetCustomerFailedResponse(response);
                } catch (RestClientException e) {
                    if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                        LOGGER.info("[enableCustomerServiceErrorHandling] Shukran Get profile General : RestTemplate Exception: " + e.getMessage());
                    } else {
                        LOGGER.error("Shukran Get profile General : RestTemplate Exception: " + e.getMessage(), e);
                    }
                    response = buildGetCustomerFailedResponse(response);
                } catch (Exception e) {
                    response = buildGetCustomerFailedResponse(response);
                    if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                        LOGGER.info("[enableCustomerServiceErrorHandling] Exception in fetching epsilon profile for mobileNo : " + mobileNo + " of customer : " + request.getCustomerId() + ": " + e.getMessage());
                    } else {
                        LOGGER.error("Exception in fetching epsilon profile for mobileNo : " + mobileNo + " of customer : " + request.getCustomerId(), e);
                    }
                }

            return response;
        } catch (RuntimeException e) {
            LOGGER.info("Error In V2 Profile1 "+ e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private CustomerUpdateProfileResponse buildGetCustomerFailedResponse(CustomerUpdateProfileResponse response) {
        //Shukran profile not exists
        response.getResponse().setShukranAPIFailing(true);
        if (response.getResponse().getCustomer().isShukranLinkFlag()) {
            response.getResponse().setShukranProfileExists(true);
            response.getResponse().setShukranLinkFlag(true);
            response.getResponse().setShukranProfileData(response.getResponse().getCustomer().getShukranProfileData());
        } else {
            response.getResponse().setShukranProfileExists(false);
            response.getResponse().setShukranLinkFlag(false);
        }

        return response;
    }

    private ShukranProfileData buildShukranProfileData(JSONObject profileJson,Integer storeId) {
        Integer shukranQualifingPurchaseCount = buildShukranQualifingPurchase(getJsonString(profileJson, "TierName"));
        Integer shukranWelcomeBonous =buildShukranWelcomeBonous(storeId);
        ShukranProfileData shukranProfileData = ShukranProfileData.builder()
                .profileId(getJsonString(profileJson, Constants.PROFILE_ID))
                .tierCode(getJsonString(profileJson, "TierCode"))
                .tierExpiryDate(getJsonString(profileJson, "TierExpiryDate"))
                .tierName(getJsonString(profileJson, "TierName"))
                .tierStartDate(getJsonString(profileJson, "TierStartDate"))
                .cardNumber(getJsonString(profileJson, Constants.CARD_NUMBER))
                .qualifyingTranxCount(getJsonString(profileJson, "QualifyingTranxCount"))
                .lastEvaluateDate(getJsonString(profileJson, "LastEvaluateDate"))
                .availablePoints(getJsonDouble(profileJson, Constants.JSON_EXTERNAL_DATA, "AvailablePoints", 0.00))
                .availablePointsCashValue(getJsonDouble(profileJson, Constants.JSON_EXTERNAL_DATA, "AvailablePointsCashValue", 0.00))
                .previousTierExpiredDate(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "PreviousTierEndDate"))
                .previousTierName(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "PreviousTierName"))
                .tierActivity(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "TierActivity"))
                .tierNudgeFlag(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "TIER_NUDGE_FLAG"))
                .reqQualTxnForNxtTier(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "ReqQualTxnForNxtTier"))
                .language(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "Language"))
                .memberCity(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "MemberCity"))
                .consentstatus(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "Consentstatus"))
                .isLMSLinked(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "IsLMSLinked"))
                .consentSource(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "ConsentSource"))
                .consentProvided(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "ConsentProvided"))
                .retTierQualTxn(getJsonNestedString(profileJson, Constants.JSON_EXTERNAL_DATA, "RetTierQualTxn"))
                .shukranQualifingPurchase(shukranQualifingPurchaseCount)
                .shukranWelcomeBonous(shukranWelcomeBonous)
                .build();
        String shukranProfileMessage = buildShukranProfileMessage(shukranProfileData,storeId);
        String shukranProfileDetailsMessage = buildShukranProfileDetailsMessage(shukranProfileData,storeId);
        shukranProfileData.setShukranProfileMessage(shukranProfileMessage);
        shukranProfileData.setShukranProfileDetailsMessage(shukranProfileDetailsMessage);
        String action = Constants.MAINTAIN;
        if (null!=shukranProfileData.getTierNudgeFlag()
                && !shukranProfileData.getTierNudgeFlag().isEmpty() && shukranProfileData.getTierNudgeFlag().toUpperCase().contains(Constants.UPGRADE)) {
            action = Constants.UNLOCK;
        }
        //If tiername is classic , action should be UNLOCK by default
        if(shukranProfileData.getTierName().equalsIgnoreCase(Constants.CLASSIC)) {
            action = Constants.UNLOCK;
        }
        //If tiername is platinum , action should be MAINTAIN by default
        if(shukranProfileData.getTierName().equalsIgnoreCase(Constants.PLATINUM)) {
            action = Constants.MAINTAIN;
        }
        shukranProfileData.setAction(action);
        String nextTier = buildNextTier(shukranProfileData.getTierName());
        //If tiername is platinum , nextTier should be platinum by default
        if(shukranProfileData.getTierName().equalsIgnoreCase(Constants.PLATINUM)) {
            nextTier = Constants.PLATINUM.toLowerCase();
        }
        shukranProfileData.setNextTier(nextTier);
        return shukranProfileData;
    }

    private String buildNextTier(String tierName) {
        String nextTier = "";
        if (null != shukranNextTier) {
            tierName = tierName.toLowerCase();
            switch (tierName) {
                // If classic next tier will be sivler
                case "classic":
                    nextTier = shukranNextTier.getClassic();
                    break;
                // If classic next tier will be gold
                case "silver":
                    nextTier = shukranNextTier.getSilver();
                    break;
                // If classic next tier will be platinum
                case "gold":
                    nextTier = shukranNextTier.getGold();
                    break;
                default:
                    break;
            }
        }

        return nextTier;
    }

    private String buildShukranProfileMessage(ShukranProfileData shukranProfileData,Integer storeId) {
        // Validation for input parameters
        if (shukranProfileData == null || storeId == null) {
            return ""; // Return empty if required data is missing
        }
        // Get default profile message for the store
        String shukranProfileMessage = Constants.getStoresList().stream()
                .filter(store -> storeId.equals(Integer.parseInt(store.getStoreId())))
                .map(Stores::getShukranProfileMessage)
                .findFirst()
                .orElse("");
        // Set default values
        String activity = Constants.MAINTAIN;
        String qualifyPurchase = Optional.ofNullable(shukranProfileData.getRetTierQualTxn()).orElse("");
        String dateStr = Optional.ofNullable(shukranProfileData.getTierExpiryDate()).orElse("");

        // Update values if the activity is "UPGRADE"
        String tierName = Optional.ofNullable(shukranProfileData.getTierName()).orElse("");
        if (null!=shukranProfileData.getTierNudgeFlag()
                && !shukranProfileData.getTierNudgeFlag().isEmpty() && shukranProfileData.getTierNudgeFlag().toUpperCase().contains(Constants.UPGRADE)) {
            activity = Constants.UNLOCK;
            qualifyPurchase = Optional.ofNullable(shukranProfileData.getReqQualTxnForNxtTier()).orElse("");
            dateStr = Optional.ofNullable(shukranProfileData.getLastEvaluateDate()).orElse("");
            String[] nudgeWords = shukranProfileData.getTierNudgeFlag().split(" ");
            tierName = nudgeWords[nudgeWords.length - 1];
        }
        // Validate and format date string
        String formattedDate = "";
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            formattedDate = zonedDateTime.format(outputFormatter);
        } catch (Exception e) {
            // Log error and set a default empty string for formattedDate if date parsing fails
            formattedDate = "";
        }

        // Replace placeholders in the profile message
        shukranProfileMessage = shukranProfileMessage.replace("{QP}", qualifyPurchase);
        shukranProfileMessage = shukranProfileMessage.replace("{DATE}", formattedDate);
        shukranProfileMessage = shukranProfileMessage.replace("{ACTIVITY}", activity);
        shukranProfileMessage = shukranProfileMessage.replace("{TIER_NAME}",tierName);

        return shukranProfileMessage;
    }

    private String buildShukranProfileDetailsMessage(ShukranProfileData shukranProfileData,Integer storeId) {
        // Validation for input parameters
        if (shukranProfileData == null || storeId == null) {
            return "";
        }
        // Get default profile details message for the store
        String shukranProfileDetailsMessage = Constants.getStoresList().stream()
                .filter(store -> storeId.equals(Integer.parseInt(store.getStoreId())))
                .map(Stores::getShukranProfileDetailsMessage)
                .findFirst()
                .orElse("");

        // Set default values
        String activity = Constants.MAINTAIN;
        String qualifyPurchase = Optional.ofNullable(shukranProfileData.getRetTierQualTxn()).orElse("");

        // Update values if the activity is "UPGRADE"
        String tierName = Optional.ofNullable(shukranProfileData.getTierName()).orElse("");
        if (null!=shukranProfileData.getTierNudgeFlag()
                && !shukranProfileData.getTierNudgeFlag().isEmpty() && shukranProfileData.getTierNudgeFlag().toUpperCase().contains(Constants.UPGRADE)) {
            activity = Constants.UNLOCK;
            qualifyPurchase = Optional.ofNullable(shukranProfileData.getReqQualTxnForNxtTier()).orElse("");
            String[] nudgeWords = shukranProfileData.getTierNudgeFlag().split(" ");
            tierName = nudgeWords[nudgeWords.length - 1];
        }

        // Replace placeholders in the profile message
        shukranProfileDetailsMessage = shukranProfileDetailsMessage.replace("{QP}", qualifyPurchase);
        shukranProfileDetailsMessage = shukranProfileDetailsMessage.replace("{ACTIVITY}", activity);
        shukranProfileDetailsMessage = shukranProfileDetailsMessage.replace("{TIER_NAME}",tierName);

        return shukranProfileDetailsMessage;
    }

    private Integer buildShukranQualifingPurchase(String tierName) {
        Integer qualifyCount = 0;
        if (null != shukranQualifingPurchase) {
            tierName = tierName.toLowerCase();
            switch (tierName) {
                case "silver":
                    qualifyCount = shukranQualifingPurchase.getSilver();
                    break;
                case "gold":
                    qualifyCount = shukranQualifingPurchase.getGold();
                    break;
                case "classic":
                    qualifyCount = shukranQualifingPurchase.getClassic();
                    break;
                case "platinum":
                    qualifyCount = shukranQualifingPurchase.getPlatinum();
                    break;
                default:
                    break;
            }
        }

        return qualifyCount;
    }

    private String getJsonString(JSONObject jsonObject, String key) {
        return jsonObject.optString(key, "");
    }


    private String getJsonNestedString(JSONObject jsonObject, String parentKey, String key) {
        JSONObject nestedObject = jsonObject.optJSONObject(parentKey);
        return (nestedObject != null) ? nestedObject.optString(key, "") : "";
    }

    private double getJsonDouble(JSONObject jsonObject, String parentKey, String key, double defaultValue) {
        JSONObject nestedObject = jsonObject.optJSONObject(parentKey);
        return (nestedObject != null) ? nestedObject.optDouble(key, defaultValue) : defaultValue;
    }

    @Override
    @Transactional
    public CustomerRestPassResponse changePassword(CustomerPasswordRequest passwordReset) {

        ChangePassword changePassword = new ChangePassword();
        return changePassword.reset(passwordReset, client, passwordHelper);

    }

    /**
     *
     */
    @Override
    @Transactional
    public CustomerRestPassResponse resetCustomerPassword(String email, Integer storeId,
            Map<String, String> requestHeader) throws CustomerException {

        ResetPassword resetPassword = new ResetPassword();
        return resetPassword.reset(email, storeId, requestHeader, client, magentoBaseUrl);

    }

    @Override
    @Transactional
    public WhatsAppOptResponse getWhatsAppOtp(Map<String, String> httpServletRequest, WhatsAppOtpRequest request) {
        WhatsAppOpt whatsappOpt = new WhatsAppOpt();
        return whatsappOpt.opt(httpServletRequest, request, client);
    }

    /**
     *
     */
    @Override
    @Transactional
    public CustomerWishlistResponse saveUpdateV4OneWishList(CustomerWishListRequest customerWishListReq,
    		Map<String, String> requestHeader,
            boolean isSave) {

        return addWishlist.add(customerWishListReq, isSave, client, requestHeader, elasticProductHelperV5, restTemplate, vmUrl);


    }

    /**
     *
     */
    @Override
    @Transactional
    public CustomerWishlistResponse removeWishList(CustomerWishListRequest customerWishListReq) {

        RemoveFromWishlist removeFromWishlist = new RemoveFromWishlist();
        return removeFromWishlist.remove(customerWishListReq, client);

    }

    /**
     *
     */
    @Override
    @Transactional(readOnly = true)
    public CustomerWishlistResponse getWishList(Integer customerId, Integer storeId, boolean standalone) {

        GetWishlistIds getWishlistIds = new GetWishlistIds();
        return getWishlistIds.get(customerId, storeId, standalone, client, env, elasticProductHelperV5, restTemplate, vmUrl);

    }

    @Override
    @Transactional
    public CustomerAddreesResponse saveAddress(CustomerAddrees customerAddRequest, boolean isSave,
                                               Map<String, String> requestHeader) {
        return saveAddress.saveUpdate(customerAddRequest, isSave, customerEntityRepository,
                customerAddressEntityRepository, staticComponents, customerGridFlatRepository,
                customerAddressEntityVarcharRepository, consulIpAddress, env, passwordHelper, saveEmailTranslation,requestHeader);
    }

    @Override
    @Transactional
    public CustomerAddreesResponse deleteAddress(CustomerAddrees customerAddRequest) {
        DeleteAddress deleteAddress = new DeleteAddress();
        return deleteAddress.delete(customerAddRequest, customerEntityRepository, customerAddressEntityRepository);
    }

    @Override
    @Transactional
    public CustomerAddreesResponse getAddress(Integer customerId,Map<String, String> requestHeader) {
        return getAddress.get(customerId, customerEntityRepository, customerAddressEntityRepository, staticComponents,requestHeader
        		,jwtFlag,client);
    }

    /**
     *
     * @param addressId
     * @param customerId
     * @return CustomerAddreesResponse
     */
    @Override
    @Transactional
    public CustomerAddreesResponse getAddressById(Integer addressId, Integer customerId) {
        return getAddress.getById(addressId, customerId, customerEntityRepository, customerAddressEntityRepository,
                staticComponents);
    }

	@Override
	public Boolean authenticateCheck(@RequestHeader Map<String, String> requestHeader, Integer customerId) {


		Boolean authenticate = false;
		LOGGER.info("customer id:" + customerId);
		if(null != customerId) {

			String jwtToken = null;
			String headerEmail = null;

			for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
	            String k = entry.getKey();
	            String v = entry.getValue();
	           // LOGGER.info("Key: " + k + ", Value: " + v);

	            if ("Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {

	            	 jwtToken = v.substring(4);

	            }

	            if ("X-Header-Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {

	            	String trimEmail  = v;

	            	 headerEmail = getEmailFromHeader(trimEmail);

	            }
	        }
			Boolean jwtRefreshTokenFlag = false ;
			JwtUser jwtUser = validator.validate(jwtToken);
			if(null != jwtUser) {
				jwtRefreshTokenFlag = jwtUser.getRefreshToken();
			}
			boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader,jwtRefreshTokenFlag);
			
			if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
					|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
				headerEmail = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
			}

			handleJwtUser(customerId, headerEmail, jwtUser);

		}
		return authenticate;
	}

	private void handleJwtUser(Integer customerId, String headerEmail, JwtUser jwtUser) {
		if (jwtUser == null) {

			throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
		} else if(null != jwtUser.getCustomerId() && ( !customerId.equals(jwtUser.getCustomerId()))){

			throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

		}else if(null != headerEmail  && null !=jwtUser.getUserId()  && ! headerEmail.equalsIgnoreCase(jwtUser.getUserId())){

			throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

		}else if(null != customerId && null == jwtUser.getCustomerId()&& Constants.IS_JWT_TOKEN_ENABLE){

			throw new BadRequestException("403", "Exception", Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

		}
	}

	private String getEmailFromHeader(String inputEmail) {
		String result = inputEmail;
		try {
			 String[] chunks = inputEmail.split("_");
			if (chunks != null && chunks.length > 1) {
				ArrayList<String> chunksList = new ArrayList<>(Arrays.asList(chunks));
				for (int i = (chunksList.size() - 1); i > (-1); i--) {
					final String item = chunksList.get(i);
					if (StringUtils.isNumericSpace(item)) {
						chunksList.remove(i);
					} else {
						break;
					}
				}
				String value = String.join("_", chunksList);
				if (value != null) {
					result = value.trim();
				} else {
					result = "";
				}
			}
		} catch (Exception e) {
			result = inputEmail;
		}
		return result;
	}

	@Override
	public CustomerCheckvalidityResponse customerValidityCheck(@Valid CustomerValidityCheckRequest request,Map<String, String> requestHeader) {
		
		CustomerCheckvalidityResponse response = new CustomerCheckvalidityResponse();
		CustomerEntity customerEntity = customerEntityRepository.findByEntityIdAndEmail(request.getCustomerId(), request.getCustomerEmail());
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		if(null != customerEntity) {

			CustomerValidityResponseBody responseBody = new CustomerValidityResponseBody();
			String userId = null;
			if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
					|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
				userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
			}else {
				userId = customerEntity.getEmail();
				refreshTokenFlag = false;
			}
			responseBody.setJwtToken(passwordHelper.generateToken(userId,
                    String.valueOf(new Date().getTime()),customerEntity.getEntityId(),refreshTokenFlag));
			responseBody.setCustomerEmail(customerEntity.getEmail());
			responseBody.setCustomerId(customerEntity.getEntityId());
			response.setResponse(responseBody);

			response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("Customer is valid!");

		}else {

			 response.setStatus(false);
             response.setStatusCode("201");
             response.setStatusMsg("Customer is not valid!");
		}
		return response;
	}

	@Override
    @Transactional
    public String getPhoneNumberByEmailId(String emailId) {

		String phoneNumber = null;
		CustomerEntity customerEntity = customerEntityRepository.findByEmail(emailId.toLowerCase());
		if(null != customerEntity) {
			phoneNumber = customerEntity.getPhoneNumber();
		}
		return phoneNumber;
    }

	@Override
	public RecaptchaVerifyResponse verifyRecaptcha(Map<String, String> requestHeader,
			@Valid RecaptchaVerifyRequest request, String customerIp) {

		RecaptchaVerifyResponse resp = new RecaptchaVerifyResponse();
		HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

        RecaptchaGoogleVerifyRequest recaptchaGoogleVerifyReq = new RecaptchaGoogleVerifyRequest();
        recaptchaGoogleVerifyReq.setResponse(request.getToken());
        if(StringUtils.isNotBlank(customerIp) && StringUtils.isNotEmpty(customerIp) && customerIp.length()<45) {
            recaptchaGoogleVerifyReq.setRemoteip(customerIp);
        }
        recaptchaGoogleVerifyReq.setSecret(recaptchaSecretKey);

        HttpEntity<RecaptchaGoogleVerifyRequest> requestBody = new HttpEntity<>(recaptchaGoogleVerifyReq, requestHeaders);

        ResponseEntity<RecaptchaGoogleVerifyResponse> response;
        try {

            response = restTemplate.exchange(recaptchaVerifyUrl + "?secret=" + recaptchaSecretKey + "&response=" + request.getToken() + "&remoteip=localhost", HttpMethod.POST, requestBody, RecaptchaGoogleVerifyResponse.class);
            RecaptchaGoogleVerifyResponse body = response.getBody();
            if(null != body) {
            	LOGGER.info("Recaptcha Response: " + mapper.writeValueAsString(body));
            	if (body.isSuccess()) {

                	resp.setStatusCode("200");
                	resp.setStatusMsg("SUCCESS");
                	resp.setStatus(true);

                }else {
                	resp.setStatusCode("204");
     	            resp.setStatus(false);
     	            if(null != body.getErrorCodes() && body.getErrorCodes().isEmpty())
     	            	resp.setStatusMsg("Error in Google Recaptcha Verify api - " + body.getErrorCodes().get(0));
     	            return resp;
                }
            }
        } catch(RestClientException | JsonProcessingException e) {
        	LOGGER.error(e.getMessage());
            resp.setStatusCode("203");
            resp.setStatus(false);
            resp.setStatusMsg("Exception in rest call to google recaptcha verify");
            return resp;

        }
		return resp;
	}

	@Override
	public void validateDeletedUser(Integer customerId) {

		CustomerEntity customer = customerEntityRepository.findByEntityId(customerId);
		if (null != customer && customer.getIsActive() == 2) {

			throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
		}
	}
	@Override
	public Integer getRegistrationIncrementId() throws CustomerException {

		SequenceCustomerEntity sequenceCustomerEntity = new SequenceCustomerEntity();

		sequenceCustomerEntity = sequenceCustomerEntityRepository.saveAndFlush(sequenceCustomerEntity);

		return sequenceCustomerEntity.getSequenceValue().intValue();

	}

	@Override
	public FirstFreeShipping setFreeShipping(String createdAt, Integer storeId) {
		try {
		Boolean freeShipping = false;
		FirstFreeShipping firstFreeShipping = new FirstFreeShipping();
		long timeDiff = 0;

		final DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);


		if (null != Constants.freeShipping) {
			LOGGER.info("setFirstFreeShipping : Processing free shipping for storeId: {}" +storeId);
			String storename = Constants.checkStoreIds(storeId);
			String expireInDays = "0";

			if (null != storename && storename.equalsIgnoreCase("sa")) {

				expireInDays = Constants.freeShipping.getFreeShipping().getSa().getExpireInDays();
				LOGGER.info("setFirstFreeShipping : Expire in days: {}" +expireInDays + "for store" +storename);

			} else if (null != storename && storename.equalsIgnoreCase("ae")) {

				expireInDays = Constants.freeShipping.getFreeShipping().getAe().getExpireInDays();
				LOGGER.info("setFirstFreeShipping : Expire in days: {}" +expireInDays + "for store" +storename);
			}
			

			Date createdDate = new Date();

			try {
	            LOGGER.info("setFirstFreeShipping : Parsing createdAt date: {}" +createdAt);
	            createdDate = parseDate(createdAt);
	            LOGGER.info("setFirstFreeShipping : Successfully parsed createdAt date: {}" +createdDate);
	        } catch (IllegalArgumentException e) {
	            LOGGER.error("setFirstFreeShipping : Error during date parse for estimated date. createdAt: {}" +createdAt +  "Exception: {}" +e.getMessage());
	            return null;	
	        }
			Calendar created = Calendar.getInstance();
			created.setTime(createdDate);
			created.add(Calendar.DATE, Integer.parseInt(expireInDays));

			Date currentDate = new Date();
			SimpleDateFormat formatter = new SimpleDateFormat(
				    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
			formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

			if (currentDate.before(created.getTime())) {
				freeShipping = true;
				timeDiff = created.getTimeInMillis() - currentDate.getTime();
				LOGGER.info("setFirstFreeShipping : Free shipping is active. Time left (ms): {}" +timeDiff);
				
			} else {
				timeDiff = currentDate.getTime() - created.getTimeInMillis();
				LOGGER.info("setFirstFreeShipping : Free shipping expired. Time exceeded (ms): {}" +timeDiff);
			}
			long daysDiff = TimeUnit.MILLISECONDS.toDays(timeDiff);

			int days = (int) daysDiff;

			firstFreeShipping.setActive(freeShipping);
			firstFreeShipping.setNumOfDays(String.valueOf(daysDiff));

			Calendar now = Calendar.getInstance();
			now.add(Calendar.DAY_OF_MONTH, days);
			firstFreeShipping.setExpireOn(formatter.format(now.getTime()));

		}

		return firstFreeShipping;
		}
		catch(Exception e) {
			LOGGER.info("setFirstFreeShipping : Error occured in calculation of free shipping: {}" +e);
			return null;
		}
	}

	/**
	 * Find referral customers
	 */
	@Override
	public CustomerOmsResponsedto findReferralCustomers(List<Integer> customerIds) {
		CustomerOmsResponsedto response = new CustomerOmsResponsedto();

		Criteria criteria = Criteria.where("entityId").in(customerIds).and("referralUser").is(1);
		Query query = Query.query(criteria);
		List<CustomerEntity> customers = mongoGccTemplate.find(query, CustomerEntity.class);

		if (customers.isEmpty()) {
			response.setStatus(false);
			response.setStatusMsg("There is no referral customers found.");
		}
		List<CustomerOmsResponse> customersDto = customers.stream().map(cust -> {
			CustomerOmsResponse customerDto = new CustomerOmsResponse();
			customerDto.setCustomerId(cust.getEntityId());
			customerDto.setCustomerEmail(cust.getEmail());
			customerDto.setMobileNumber(cust.getPhoneNumber());
			customerDto.setIsReferral(
					(Objects.nonNull(cust.getReferralUser()) && cust.getReferralUser() == 1) ? "true" : "false");
			return customerDto;
		}).collect(Collectors.toList());
		response.setResponse(customersDto);
		response.setStatus(true);
		response.setStatusMsg("success");
		return response;
	}

	@Override
	public CustomerProfileResponse findCustomerById(String customerIds) {
		CustomerProfileResponse response = new CustomerProfileResponse();
		try {
			CustomerEntity customerEntity = customerEntityRepository.findByEntityId(Integer.valueOf(customerIds));
			Customer customerDTO = getCustomer.getSavedCustomerInfo(customerEntity);
			response.setCustomer(customerDTO);
			response.setStatus(true);
			response.setUserMessage("success");
		} catch (Exception e) {
			LOGGER.error("Error in fetching customer details. ", e);
			response.setStatus(false);
			response.setUserMessage("Error in fetching customer details");
		}
		return response;
	}

    @Override
    public EnrollmentResponse enrollShukranAccount(ShukranEnrollmentRequest shukranEnrollmentRequest, Map<String, String> requestHeader) {
        CustomerUpdateProfileResponse customerUpdateProfileResponse = fetchCustomerProfile(shukranEnrollmentRequest.getCustomerId());

        // If not valid response OR shukran not enabled
        if (!isValidCustomerResponse(customerUpdateProfileResponse) || !isShukranEnabledForCustomerResponse(customerUpdateProfileResponse,shukranEnrollmentRequest.getStoreId())) {
            return handleIneligibleCustomer(customerUpdateProfileResponse);
        }

        try {
            if (isShukranAlreadyEnrolled(customerUpdateProfileResponse)) {
                return buildAlreadyEnrolledResponse();
            }
            if (redundantShukranProfileApiCall) {
                LOGGER.info("In enrollShukranAccount : redundantShukranProfileApiCall: "+redundantShukranProfileApiCall);
                //Check already shukran profile exists or not before calling shukran enroll API
                String mobileNo = customerUpdateProfileResponse.getResponse().getCustomer().getMobileNumber();
                if (isRecentlyProcessed(mobileNo)) {
                    LOGGER.info("In enrollShukranAccount : isRecentlyProcessed: true");
                    return buildAlreadyEnrolledResponse();
                }
            }

            return processShukranEnrollment(shukranEnrollmentRequest, customerUpdateProfileResponse, requestHeader);
        } catch (Exception e) {
            return handleEnrollmentException(shukranEnrollmentRequest.getCustomerId(), e);
        }
    }

    private boolean isRecentlyProcessed(String mobileNo) {
        EpsilonBucketObject bucketObject = getBucketObject(mobileNo);
        if (bucketObject == null) {
            LOGGER.info("In isRecentlyProcessed : No bucket object found for mobile number: " + mobileNo);
            return false;
        }
        long createdAt = bucketObject.getCreatedAt();
        if (createdAt <= 0) {
            LOGGER.warn("In isRecentlyProcessed : Invalid createdAt timestamp for mobile: " + mobileNo + ", createdAt: " + createdAt);
            return false;
        }
        boolean isRecent = (Instant.now().toEpochMilli() - createdAt) < 60000;
        LOGGER.info("In isRecentlyProcessed : Mobile: " + mobileNo + ", isRecentlyProcessed for shukran enroll: " + isRecent);

        return isRecent;
    }

    private CustomerUpdateProfileResponse fetchCustomerProfile(Integer customerId) {
        return getCustomer.get(customerId, client, jwtFlag, null);
    }

    private EnrollmentResponse handleIneligibleCustomer(CustomerUpdateProfileResponse response) {
        LOGGER.error("Shukran enroll failed for customer & get statuscode: "+response.getStatusCode());
        ErrorType error = new ErrorType();
        error.setErrorCode("204");
        error.setErrorMessage("Shukran enroll failed for customer");
        return buildEnrollmentResponse(error, true, "Shukran enroll failed for customer", "201");
    }

    private boolean isShukranAlreadyEnrolled(CustomerUpdateProfileResponse response) {
        return response.getResponse().getCustomer().isShukranLinkFlag();
    }

    private EnrollmentResponse buildAlreadyEnrolledResponse() {
        ErrorType error = new ErrorType();
        error.setErrorCode("205");
        error.setErrorMessage("Shukran account already enrolled for customer");
        LOGGER.error("Shukran account enrolled already for customer");
        return buildEnrollmentResponse(error, true, "Shukran account enrolled already for customer", "201");
    }

    private EnrollmentResponse processShukranEnrollment(ShukranEnrollmentRequest shukranEnrollmentRequest,
                                                        CustomerUpdateProfileResponse customerResponse,
                                                        Map<String, String> requestHeader) {
        if (redundantShukranProfileApiCall) {
            LOGGER.info("In processShukranEnrollment : redundantShukranProfileApiCall: " + redundantShukranProfileApiCall);
            String mobileNo = customerResponse.getResponse().getCustomer().getMobileNumber();
            //Save into redis
            EpsilonBucketObject bucketObject = getBucketObject(mobileNo);
            long now = Instant.now().toEpochMilli();
            if (bucketObject == null) {
                bucketObject = new EpsilonBucketObject();
            }
            bucketObject.setMobileNo(mobileNo);
            bucketObject.setCreatedAt(now);
            redisHelper.put(CACHE_NAME, mobileNo, bucketObject, TtlMode.SHUKRAN_REDIS);
            LOGGER.info("In processShukranEnrollment : Added to redis : " + bucketObject);
        }
        ResponseEntity<String> responseEntity = externalServiceAdapter.createShukranAccount(
                shukranEnrollmentRequest,
                customerResponse.getResponse().getCustomer()
        );

        LOGGER.info("Enrollment Shukran account API call response: " + responseEntity);

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return handleSuccessfulEnrollment(shukranEnrollmentRequest, customerResponse, responseEntity, requestHeader);
        } else {
            return handleEnrollmentError(shukranEnrollmentRequest.getCustomerId(), responseEntity.getBody());
        }
    }

    private EnrollmentResponse handleSuccessfulEnrollment(ShukranEnrollmentRequest shukranEnrollmentRequest,
                                                          CustomerUpdateProfileResponse customerResponse,
                                                          ResponseEntity<String> responseEntity,
                                                          Map<String, String> requestHeader) {
        LOGGER.info("Successfully enrolled Shukran account for customer: " + shukranEnrollmentRequest.getCustomerId());

        JSONObject enrollData = new JSONObject(responseEntity.getBody());
        String profileId = enrollData.optString(Constants.PROFILE_ID, "");
        String cardNumber = enrollData.optString(Constants.CARD_NUMBER, "");

        EnrollmentResponse response = buildEnrollmentResponse(null, true, "Successfully enrolled Shukran account for customer", "200");
        response.setProfileId(profileId);
        response.setCardNumber(cardNumber);
        getCustomer.setShukranLinkFlag(shukranEnrollmentRequest.getCustomerId(), client, true, profileId, cardNumber);

        return response;
    }

    private EnrollmentResponse handleEnrollmentError(Integer customerId, String errorMessage) {
        LOGGER.error("Error in enrolling Shukran account for customer: " + customerId);
        ErrorType error = new ErrorType();
        error.setErrorCode("204");
        error.setErrorMessage(errorMessage);
        return buildEnrollmentResponse(error, true, "Enrolling Shukran account for customer failed", "201");
    }

    private EnrollmentResponse handleEnrollmentException(Integer customerId, Exception e) {
        LOGGER.error("Exception in enrolling Shukran profile for customer: " + customerId, e);
        ErrorType error = new ErrorType();
        error.setErrorCode("500");
        error.setErrorMessage(e.getMessage());
        return buildEnrollmentResponse(error, false, "Exception in enrolling Shukran account for customer", "500");
    }

    private void getAndupdateQuoteInfo (Integer customerId,Integer storeId,String phoneNumber,Map<String, String> requestHeader
                                        ) {
        try {
            JSONObject quoteDataJson = new JSONObject();
            //for fetching quote details
            quoteDataJson.put("customerId", customerId);
            quoteDataJson.put("storeId", storeId);

            //Get Quote Info of customer
            ResponseEntity<String> getQuoteResponseEntity= externalQuoteHelper.fetchQuote(quoteDataJson, getHeaderValue(requestHeader,Constants.TOKEN), getHeaderValue(requestHeader,"x-header-token"), getHeaderValue(requestHeader,"x-client-version"), getHeaderValue(requestHeader,"x-source"));
            LOGGER.info("Get quote info response: "+getQuoteResponseEntity+ " of customer Id : "+customerId);
            if (getQuoteResponseEntity.getStatusCode().is2xxSuccessful()) {
                LOGGER.info("Successfully fectched quote details for customer Id : " + customerId);
                JSONObject quoteResponse = new JSONObject(getQuoteResponseEntity.getBody());
                String quoteId = Optional.ofNullable(quoteResponse.optJSONObject("response"))
                        .map(response -> response.optString("quoteId", null))
                        .orElse(null);
                LOGGER.info("Quote id found for customer Id : " + customerId+ " and quote id :"+quoteId);
                if (null != quoteId) {
                    quoteDataJson.put("phoneNumber", phoneNumber);
                    quoteDataJson.put("quoteId", quoteId);

                    //Update Quote info of customer with profileId,cardNumber and mobileNumber
                    getQuoteResponseEntity = externalQuoteHelper.updateQuote(quoteDataJson, getHeaderValue(requestHeader, Constants.TOKEN), getHeaderValue(requestHeader, "x-header-token"), getHeaderValue(requestHeader, "x-client-version"), getHeaderValue(requestHeader, "x-source"));
                    LOGGER.info("Update quote info response: " + getQuoteResponseEntity + " of customer Id: " + customerId);
                    if (getQuoteResponseEntity.getStatusCode().is2xxSuccessful()) {
                        LOGGER.info("Updated quote details for customer Id : " + customerId);
                    } else {
                        LOGGER.error("Error in updating quote info for customer Id : " + customerId);
                    }
                }
            } else {
                LOGGER.error("Error in fetching open quote for customer Id : " + customerId);
            }

        } catch (Exception e) {
            LOGGER.error("Exception in fetching open quote for customer Id : " + customerId,e);
        }
    }

    private String getHeaderValue(Map<String, String> requestHeader, String key) {
        for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            LOGGER.info("Key: " + k + ", Value: " + v);
            if (key.equalsIgnoreCase(k) && null != v && v.length() > 3) {
                return v;
            }
        }
        return null;
    }

    private EnrollmentResponse buildEnrollmentResponse (ErrorType error,boolean status,String statusMsg,String statusCode) {
        return EnrollmentResponse.builder().error(error).status(status).statusMsg(statusMsg).statusCode(statusCode).build();
    }

    @Override
    public EnrollmentResponse linkShukranAccount(LinkShukranRequest linkShukranRequest, Map<String, String> requestHeader) {
        EnrollmentResponse enrollmentResponse = new EnrollmentResponse();
        CustomerUpdateProfileResponse customerUpdateProfileResponse =  getCustomer.get(linkShukranRequest.getCustomerId(), client, jwtFlag , null);
        //If SHUKRAN ENABLE - TRUE & get customer is SUCCESS , link shukran account
        //if (isValidCustomerResponse(customerUpdateProfileResponse) && isShukranEnabledForCustomerResponse(customerUpdateProfileResponse,null)) {
            String mobileNo = customerUpdateProfileResponse.getResponse().getCustomer().getMobileNumber();
            Integer storeId = customerUpdateProfileResponse.getResponse().getCustomer().getStoreId();
            try {
                ResponseEntity<String> shukranProfileResponse = externalServiceAdapter.getEpsilonProfile(mobileNo,storeId);
                LOGGER.info("Get Shukran profile API call response: " + shukranProfileResponse);
                if (!shukranProfileResponse.getStatusCode().is2xxSuccessful()) {
                    return handleProfileNotFound(linkShukranRequest);
                }
                LOGGER.info("Successfully fetched Shukran profile for mobileNo: " + mobileNo);
                ResponseEntity<String> linkShukranResponse = externalServiceAdapter.linkShukranAccount(linkShukranRequest.getProfileId());
                LOGGER.info("Link Shukran profile API call response: " + linkShukranResponse);
                if (!linkShukranResponse.getStatusCode().is2xxSuccessful()) {
                    return handleLinkingError(linkShukranRequest);
                }
                JSONObject linkData = new JSONObject(linkShukranResponse.getBody());
                String profileId = linkData.optString(Constants.PROFILE_ID, "");

                LOGGER.info("Successfully linked Shukran profile for customer: " + linkShukranRequest.getCustomerId());
                getCustomer.setShukranLinkFlag(linkShukranRequest.getCustomerId(), client, linkShukranRequest.getShukranLinkFlag(), profileId, null);
                if (linkShukranRequest.getShukranLinkFlag())
                    enrollmentResponse = buildEnrollmentResponse(null, true, "Successfully linked Shukran account for customer", "200");
                else
                    enrollmentResponse = buildEnrollmentResponse(null, true, "Successfully unlinked Shukran account for customer", "200");
            } catch (Exception e) {
                return handleException(linkShukranRequest, e);
            }
//        }else {
//            LOGGER.error("Error in linking shukran account for customer : " + linkShukranRequest.getCustomerId() + " get customer statuscode: "+customerUpdateProfileResponse.getStatusCode());
//            ErrorType error = createError("204", "Shukran linking failed for customer " + linkShukranRequest.getCustomerId());
//            enrollmentResponse = buildEnrollmentResponse(error, true, "Shukran linking failed for customer", "201");
//        }

        return enrollmentResponse;
    }

    private EnrollmentResponse handleProfileNotFound(LinkShukranRequest linkShukranRequest) {
        LOGGER.error("Error linking Shukran account for customer: " + linkShukranRequest.getCustomerId());
        ErrorType error = createError("204", "Shukran profile does not exist for customer " + linkShukranRequest.getCustomerId());
        return buildEnrollmentResponse(error, true, "Error linking Shukran account", "201");
    }

    private EnrollmentResponse handleLinkingError(LinkShukranRequest linkShukranRequest) {
        LOGGER.error("Error in linking Shukran account for customer: " + linkShukranRequest.getCustomerId());
        ErrorType error = createError("204", "Linking Shukran account failed for customer " + linkShukranRequest.getCustomerId());
        return buildEnrollmentResponse(error, true, "Linking Shukran account failed", "201");
    }

    private EnrollmentResponse handleException(LinkShukranRequest linkShukranRequest, Exception e) {
        LOGGER.error("Exception in fetching epsilon profile for customer: " + linkShukranRequest.getCustomerId(), e);
        ErrorType error = createError("500", "Exception in linking Shukran account for customer " + linkShukranRequest.getCustomerId());
        return buildEnrollmentResponse(error, true, "Exception in linking Shukran account", "201");
    }

    private ErrorType createError(String code, String message) {
        ErrorType error = new ErrorType();
        error.setErrorCode(code);
        error.setErrorMessage(message);
        return error;
    }

    private BuildUpgradeShukranTierActivityResponse upgradeShukranTierActivityResponse(ErrorType error, boolean status, String statusMsg, String statusCode) {
        return BuildUpgradeShukranTierActivityResponse.builder().error(error).status(status).statusMsg(statusMsg).statusCode(statusCode).build();
    }

    public BuildUpgradeShukranTierActivityResponse shukranUpgradeTierActivity(Map<String, String> requestHeader, UpgradeShukranTierActivityRequest requestBody) {
        CustomerUpdateProfileResponse response =  getCustomer.get(requestBody.getCustomerId(), client, jwtFlag , null);
        BuildUpgradeShukranTierActivityResponse buildupgradeshukrantieractivityresponse = new BuildUpgradeShukranTierActivityResponse();
        //If SHUKRAN ENABLE - TRUE & get customer is SUCCESS , upgrade shukran tier activity
        if (isValidCustomerResponse(response) && isShukranEnabledForCustomerResponse(response,requestBody.getStoreId())) {
            try {
                String mobileNo = response.getResponse().getCustomer().getMobileNumber();
                Integer storeId = response.getResponse().getCustomer().getStoreId();
                ResponseEntity<String> shukranProfileResponse = externalServiceAdapter.getEpsilonProfile(mobileNo,storeId);
                //Save tier upgrade in DB if shukranTierUpdgradeFlag is true
                if (shukranProfileResponse.getStatusCode().is2xxSuccessful() && requestBody.getShukranTierUpdgradeFlag()) {
                    JSONObject profileJson = new JSONObject(shukranProfileResponse.getBody());
                    ShukranProfileData shukranProfileData = buildShukranProfileData(profileJson,storeId);
                    String tierName = shukranProfileData.getTierName();
                    if (shukranProfileData.getTierActivity().equalsIgnoreCase("upgrade")) {
                        response.getResponse().getCustomer().setTierName(tierName);
                        response.getResponse().setShowPopup(false);
                        //Update in database tierName
                        getCustomer.setShukranTierActivity(requestBody.getCustomerId(),client,shukranProfileData.getTierActivity(),tierName,shukranProfileData);
                        buildupgradeshukrantieractivityresponse.setStatusMsg("Tier upgraded");
                        buildupgradeshukrantieractivityresponse.setStatus(true);
                        buildupgradeshukrantieractivityresponse.setStatusCode("200");
                        buildupgradeshukrantieractivityresponse.setError(null);
                    }else{
                        ErrorType err=new ErrorType();
                        err.setErrorMessage(Constants.TIER_NOT_UPGRADED);
                        err.setErrorCode("204");
                        buildupgradeshukrantieractivityresponse = upgradeShukranTierActivityResponse(err, false
                                , Constants.TIER_NOT_UPGRADED, "201");

                    }
                } else {
                    ErrorType err = new ErrorType();
                    err.setErrorMessage(Constants.TIER_NOT_UPGRADED);
                    err.setErrorCode("204");
                    buildupgradeshukrantieractivityresponse = upgradeShukranTierActivityResponse(err, false
                            , Constants.TIER_NOT_UPGRADED, "201");
                }
            } catch (Exception e) {
                ErrorType err=new ErrorType();
                err.setErrorMessage(e.getMessage());
                err.setErrorCode("204");
                buildupgradeshukrantieractivityresponse = upgradeShukranTierActivityResponse(err, false
                        , Constants.TIER_NOT_UPGRADED, "500");
                LOGGER.error("Exception in fetching epsilon profile for  customer id : " + requestBody.getCustomerId(), e);
            }
        }else{
            LOGGER.error("Error in fetching shukran account for customer : " + requestBody.getCustomerId() + " & customer statuscode: "+response.getStatusCode());
            buildupgradeshukrantieractivityresponse = upgradeShukranTierActivityResponse(response.getError(), false
                    , "Error in fetching shukran account for customer", "201");
        }


        return buildupgradeshukrantieractivityresponse;
    }

	@Override
	public CustomerVerificationStatusResponse getCustomerById(String customerId) {
	    CustomerVerificationStatusResponse response = new CustomerVerificationStatusResponse();
	    try {
	        CustomerEntity customerEntity = customerEntityRepository.findByEntityId(Integer.valueOf(customerId));
	        
	        if (customerEntity != null) {
	            LOGGER.info("Customer Verification Status: Customer data found for customerId: {}" +customerId);
	            response.setIsMobileVerified(customerEntity.getIsMobileVerified());
	            response.setStatus(true);
	            response.setStatusCode("200");
	            response.setIsEmailVerified(customerEntity.getIsEmailVerified());
	        } else {
	            LOGGER.info("Customer Verification Status: Customer not found for customerId: {}" +customerId);
	            response.setStatus(false);
	            response.setStatusCode("404");
	            response.setIsMobileVerified(false);
	            response.setIsEmailVerified(false);
	        }
	    } catch (NumberFormatException e) {
	        LOGGER.info("Customer Verification Status: Invalid customerId format: {}");
	        response.setStatus(false);
            response.setStatusCode("400");
	        response.setIsMobileVerified(false);
	        response.setIsEmailVerified(false);
	    } catch (Exception e) {
	        LOGGER.info("Customer Verification Status: Error in fetching customer details for customerId: {}");
	        response.setStatus(false);
            response.setStatusCode("500");
	        response.setIsMobileVerified(false);
	        response.setIsEmailVerified(false);
	    }
	    return response;
	}

    @Override
    public DeleteShukranResponse deleteShukranAccount(Integer customerId) {
        DeleteShukranResponse deleteShukranResponse = null;
        try {
            LOGGER.info("In deleting shukran data from customer : "+customerId);
            CustomerUpdateProfileResponse customerUpdateProfileResponse =  getCustomer.get(customerId, client, jwtFlag , null);
            // If get customer is SUCCESS , delete shukran account
            if (Objects.equals(customerUpdateProfileResponse.getStatusCode(), "200")) {
                //Delete shukran data in database
                getCustomer.deleteShukranData(customerId,client);
                LOGGER.info("Successfully deleted shukran data from customer : "+customerId);
                deleteShukranResponse = buildDeleteShukranResponse(null,true,Constants.SUCCESS_MSG,"200");
            } else {
                LOGGER.error("Error in deleting shukran data for customer : " + customerId);
                ErrorType error = createError("204", "Deleting Shukran data failed for customer " + customerId);
                deleteShukranResponse = buildDeleteShukranResponse(error,false,"Deleting Shukran data failed for customer","201");
            }
        } catch(Exception e) {
            LOGGER.error("Exception in deleting shukran info for customer : " + customerId + " exception : "+e.getMessage());
            ErrorType error = createError("500", "Deleting Shukran data failed for customer " + customerId);
            deleteShukranResponse = buildDeleteShukranResponse(error,false,"Deleting Shukran data failed for customer","201");
        }

        return deleteShukranResponse;
    }

    private DeleteShukranResponse buildDeleteShukranResponse (ErrorType error,boolean status,String statusMsg,String statusCode) {
        return DeleteShukranResponse.builder().error(error).status(status).statusMsg(statusMsg).statusCode(statusCode).build();
    }

    private Boolean checkIsShukranEnableForStoreId(Integer storeId) {
        // Ensure the stores list and storeId are not null
        List<Stores> storesList = Constants.getStoresList();
        if (storesList == null || storeId == null) {
            return false; // Return false if storesList or storeId is null
        }

        Optional<Stores> matchedStore = storesList.stream()
                .filter(store -> store != null && isValidStoreId(store, storeId))
                .findFirst();

        // Return the value if present, otherwise return false
        return matchedStore.map(Stores::getIsShukranEnable).orElse(false);
    }

    private boolean isValidStoreId(Stores store, Integer storeId) {
        if (store.getStoreId() == null) {
            return false;
        }
        try {
            return storeId.equals(Integer.parseInt(store.getStoreId()));
        } catch (NumberFormatException e) {
            return false; // Skip stores with invalid storeId
        }
    }


    /**
     * Validates the customer registration response to ensure it contains all the required data.
     *
     **/
    private boolean isValidCustomerResponse(CustomerUpdateProfileResponse response) {
        return response != null &&
                Objects.equals(response.getStatusCode(), "200") &&
                response.getResponse() != null &&
                response.getResponse().getCustomer() != null;
    }

    /**
     * Checks if Shukran is enabled for the customer in the provided response.
     *
     * This method determines whether Shukran is enabled for a specific store
     * based on the `storeId` parameter. If the `storeId` parameter is null,
     * the method retrieves the store ID from the customer information in the response.
     **/
    private boolean isShukranEnabledForCustomerResponse(CustomerUpdateProfileResponse response,Integer storeId) {
        return checkIsShukranEnableForStoreId(null!=storeId?storeId:response.getResponse().getCustomer().getStoreId());
    }

    private EpsilonBucketObject getBucketObject(String phoneNo) {
        EpsilonBucketObject result = null;
        if (StringUtils.isEmpty(phoneNo))
            return result;
        result = setMobileNumber(phoneNo);
        return result;
    }

    private EpsilonBucketObject setMobileNumber(String phoneNo) {
        EpsilonBucketObject result;
        try {
            result = (EpsilonBucketObject) redisHelper.get(CACHE_NAME, phoneNo, EpsilonBucketObject.class);
            if (result != null) {
                result.setMobileNo(phoneNo);
            }
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    private Date parseDate(String dateStr) {
        String[] formats = {
            "yyyy-MM-dd HH:mm:ss",
            "EEE MMM dd HH:mm:ss z yyyy"
        };

        for (String format : formats) {
            try {
                return new SimpleDateFormat(format, Locale.ENGLISH).parse(dateStr);
            } catch (ParseException ignored) {
            }
        }

        throw new IllegalArgumentException("setFirstFreeShipping : Unparseable date: " + dateStr);
    }

    @Override
    public ShukranWebhookResponse handleShukranPhoneUnlinkWebhook(ShukranWebhookRequest webhookRequest) {
        LOGGER.info("Processing Shukran phone unlink webhook for mobile number: "+webhookRequest.getMobileNumber()+", action: "+webhookRequest.getAction());
        
        try {
            // Validate action is "remove"
            if (!webhookRequest.isUnlinkOperation()) {
                LOGGER.warn("Invalid action received: "+webhookRequest.getAction()+". Expected 'remove'");
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("Invalid action. Expected 'remove'")
                        .phoneNumber(webhookRequest.getPrimaryPhoneNumber())
                        .errorCode("400")
                        .errorMessage("Invalid action")
                        .build();
            }

            // Get primary phone number
            String phoneNumber = webhookRequest.getPrimaryPhoneNumber();
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                LOGGER.warn("No phone number provided in webhook request");
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("No phone number provided")
                        .errorCode("400")
                        .errorMessage("Phone number is required")
                        .build();
            }
            
            // Find customer by phone number
            CustomerEntity customer = client.findByPhoneNumber(phoneNumber);
            
            if (customer == null) {
                LOGGER.warn("No customer found for phone number: "+phoneNumber+" (cleaned: "+phoneNumber+")");
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("No customer found for the provided phone number")
                        .phoneNumber(phoneNumber)
                        .errorCode("404")
                        .errorMessage("Customer not found")
                        .build();
            }

            // Check if customer has Shukran linked
            if (Boolean.FALSE.equals(customer.getShukranLinkFlag())) {
                LOGGER.warn("Customer "+customer.getEntityId()+" does not have Shukran linked. ShukranLinkFlag: "+customer.getShukranLinkFlag());
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("Customer does not have Shukran linked")
                        .customerId(customer.getEntityId())
                        .phoneNumber(phoneNumber)
                        .errorCode("400")
                        .errorMessage("Shukran not linked")
                        .build();
            }

            // Remove only the phone number from Shukran account while keeping account linked
            getCustomer.removePhoneNumberFromShukranAccount(customer.getEntityId(), client);
            
            LOGGER.info("Successfully removed phone number from Shukran account for customer: "+customer.getEntityId()+" with phone number: "+phoneNumber);

            return ShukranWebhookResponse.builder()
                    .success(true)
                    .message("Successfully removed phone number from Shukran account and set isMobileNumberRemoved flag")
                    .customerId(customer.getEntityId())
                    .phoneNumber(phoneNumber)
                    .unlinkedAt(java.time.LocalDateTime.now().toString())
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error processing Shukran phone unlink webhook for mobile number: "+webhookRequest.getMobileNumber(), e);
            return ShukranWebhookResponse.builder()
                    .success(false)
                    .message("Error processing webhook request")
                    .phoneNumber(webhookRequest.getPrimaryPhoneNumber())
                    .errorCode("500")
                    .errorMessage("Internal server error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Handles Shukran phone number update webhook
     * Updates customer phone number using loyalty card number
     * 
     * @param webhookRequest The webhook request containing phone update details
     * @return ShukranWebhookResponse with update result
     */
    @Override
    public ShukranWebhookResponse handleShukranPhoneUpdateWebhook(ShukranWebhookRequest webhookRequest) {
        LOGGER.info("Processing Shukran phone update webhook for loyalty card: "+webhookRequest.getLoyaltyCardNumber()+", mobile number: "+webhookRequest.getMobileNumber()+", action: "+webhookRequest.getAction());

        try {
            // Validate action is "update"
            if (!webhookRequest.isUpdateOperation()) {
                LOGGER.warn("Invalid action received: "+webhookRequest.getAction()+". Expected 'update'");
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("Invalid action. Expected 'update'")
                        .cardNumber(String.valueOf(webhookRequest.getCardNo()))
                        .errorCode("400")
                        .errorMessage("Invalid action")
                        .build();
            }

            // Get primary phone number
            String phoneNumber = webhookRequest.getPrimaryPhoneNumber();
            if ((phoneNumber == null || phoneNumber.trim().isEmpty()) && (webhookRequest.getCardNo()==null || String.valueOf(webhookRequest.getCardNo()).trim().isEmpty())) {
                LOGGER.warn("No phone number or card number provided in webhook request");
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("No phone number provided")
                        .cardNumber(String.valueOf(webhookRequest.getCardNo()))
                        .errorCode("400")
                        .errorMessage("Phone number or card number is required")
                        .build();
            }

            // Format phone number to add space between country code and number (e.g., +966768756035 -> +966 768756035)
            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                phoneNumber = formatPhoneNumberWithSpace(phoneNumber);
                LOGGER.info("Formatted phone number for Shukran webhook: " + phoneNumber);
            }

            // Find customer by card number
            String cardNumber = String.valueOf(webhookRequest.getCardNo());
            CustomerEntity customer = null;

            if (webhookRequest.getCardNo()!=null && !webhookRequest.getCardNo().toString().trim().isEmpty()) {
                // First try to find customer using direct cardNumber field
                customer = client.findByCardNumber(cardNumber);
                
                // If not found, try to find customer using cardNumber in ShukranProfileData nested object
                if (customer == null) {
                    LOGGER.info("Customer not found with direct cardNumber field, trying to find in shukranProfileData.cardNumber for cardNumber: " + cardNumber);
                    try {
                        Criteria criteria = Criteria.where("shukranProfileData.cardNumber").is(cardNumber);
                        Query query = Query.query(criteria);
                        List<CustomerEntity> customers = mongoGccTemplate.find(query, CustomerEntity.class);
                        if (customers != null && !customers.isEmpty()) {
                            customer = customers.get(0);
                            LOGGER.info("Customer found using shukranProfileData.cardNumber for cardNumber: " + cardNumber + ", customerId: " + customer.getEntityId());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error querying customer by shukranProfileData.cardNumber for cardNumber: " + cardNumber, e);
                    }
                }
            }

            if (customer == null) {
                LOGGER.warn("No customer found for card number: "+ cardNumber);
                return ShukranWebhookResponse.builder()
                        .success(false)
                        .message("No customer found for the provided card number")
                        .cardNumber(String.valueOf(webhookRequest.getCardNo()))
                        .errorCode("404")
                        .errorMessage("Customer not found")
                        .build();
            }

            // Store old phone number for response
            String oldPhoneNumber = customer.getPhoneNumber();

            // Use handlePhoneNumberUpdate function to properly handle phone number transfer
            LOGGER.info("Calling handlePhoneNumberUpdate for Shukran webhook phone update for customer: " + customer.getEntityId());
            
            // Create CustomerUpdateProfileRequest object for handlePhoneNumberUpdate
            CustomerUpdateProfileRequest customerUpdateRequest = new CustomerUpdateProfileRequest();
            customerUpdateRequest.setCustomerId(customer.getEntityId());
            customerUpdateRequest.setMobileNumber(phoneNumber);
            customerUpdateRequest.setIsMobileNumberChanged(true);
            
            // Call handlePhoneNumberUpdate to handle phone number transfer logic
            try {
                updateUser.handlePhoneNumberUpdate(customerUpdateRequest, customer, client);
                LOGGER.info("Successfully called handlePhoneNumberUpdate for Shukran webhook customer: " + customer.getEntityId());
            } catch (Exception e) {
                LOGGER.error("Error calling handlePhoneNumberUpdate for Shukran webhook customer: " + customer.getEntityId() + ". Error: " + e.getMessage(), e);
                throw new RuntimeException("Failed to update phone number using handlePhoneNumberUpdate" +e);
            }
            
            // Set additional Shukran-specific flags after successful phone number update
            customer.setIsMobileNumberRemoved(false);
            customer.setMobileNumberUpdateMessageAcknowledged(false);
            customer.setShukranLinkFlag(true);
            customer.setMobileNumberUpdated(true);
            customer.setUpdatedAt(new Date());
            
            // Save the updated customer
            client.saveAndFlushMongoCustomerDocument(customer);

            LOGGER.info("Successfully updated phone number for customer: "+customer.getEntityId()+" with card number: "+cardNumber+" from "+oldPhoneNumber+" to "+phoneNumber);

            return ShukranWebhookResponse.builder()
                    .success(true)
                    .message("Successfully updated phone number")
                    .customerId(customer.getEntityId())
                    .cardNumber(String.valueOf(webhookRequest.getCardNo()))
                    .oldPhoneNumber(oldPhoneNumber)
                    .newPhoneNumber(phoneNumber)
                    .updatedAt(java.time.Instant.now().toString())
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error processing Shukran phone update webhook for card number: "+webhookRequest.getCardNo(), e);
            return ShukranWebhookResponse.builder()
                    .success(false)
                    .message("Error processing webhook request")
                    .cardNumber(String.valueOf(webhookRequest.getLoyaltyCardNumber()))
                    .errorCode("500")
                    .errorMessage("Internal server error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Formats phone number to add space between country code and number
     * Example: +966768756035 -> +966 768756035
     * If phone number already has the correct format, returns it as is
     * 
     * @param phoneNumber The phone number to format
     * @return Formatted phone number with space between country code and number
     */
    private String formatPhoneNumberWithSpace(String phoneNumber) {
        if (StringUtils.isBlank(phoneNumber)) {
            return phoneNumber;
        }

        String trimmedPhone = phoneNumber.trim();

        // If phone number doesn't start with +, return as is
        if (!trimmedPhone.startsWith("+")) {
            return trimmedPhone;
        }

        // Check if phone number already has a space after the country code
        // Pattern: + followed by 1-4 digits, then a space, then the rest
        if (trimmedPhone.matches("\\+[0-9]{1,4}\\s+.*")) {
            LOGGER.info("Phone number already has space format, returning as is: " + trimmedPhone);
            return trimmedPhone;
        }

        // If phone number starts with + and has length > 4, format it
        if (trimmedPhone.length() > 4) {
            // Extract country code (first 4 characters for +966, but can handle 1-4 digits)
            // For Saudi Arabia (+966), we take first 4 characters
            // For other countries, we can adjust the logic if needed
            String countryCode = trimmedPhone.substring(0, 4); // +966
            String restOfNumber = trimmedPhone.substring(4);   // 768756035

            // Check if restOfNumber already starts with a space (shouldn't happen, but safety check)
            if (restOfNumber.startsWith(" ")) {
                LOGGER.info("Phone number rest already has leading space, returning as is: " + trimmedPhone);
                return trimmedPhone;
            }

            String formattedNumber = countryCode + " " + restOfNumber;
            LOGGER.info("Formatted phone number from " + trimmedPhone + " to " + formattedNumber);
            return formattedNumber;
        }

        // If phone number is too short, return as is
        return trimmedPhone;
    }

    @Override
    public MobileNumberUpdateAcknowledgmentResponse updateMobileNumberUpdateMessageAcknowledged(
            MobileNumberUpdateAcknowledgmentRequest request, Map<String, String> requestHeader) {
        
        LOGGER.info("Updating mobile number update message acknowledgment for customerId: " + 
                   request.getCustomerId() + ", acknowledged: " + request.getAcknowledged());
        
        try {
            // Find the customer by ID
            CustomerEntity customer = client.findByEntityId(request.getCustomerId());
            
            if (customer == null) {
                LOGGER.error("Customer not found for customerId: " + request.getCustomerId());
                return new MobileNumberUpdateAcknowledgmentResponse(
                    false, "404", "Customer not found", null);
            }
            
            // Update the acknowledgment flag
            customer.setMobileNumberUpdateMessageAcknowledged(!request.getAcknowledged());
            customer.setMobileNumberUpdated(request.getAcknowledged());
            customer.setUpdatedAt(new Date());
            
            // Save the updated customer
            client.saveAndFlushMongoCustomerDocument(customer);
            
            LOGGER.info("Successfully updated mobile number update message acknowledgment for customerId: " + 
                       request.getCustomerId() + " to " + request.getAcknowledged());
            
            return new MobileNumberUpdateAcknowledgmentResponse(
                true, "200", "Successfully updated acknowledgment status", request.getAcknowledged());
                
        } catch (Exception e) {
            LOGGER.error("Error updating mobile number update message acknowledgment for customerId: " + 
                        request.getCustomerId(), e);
            return new MobileNumberUpdateAcknowledgmentResponse(
                false, "500", "Internal server error: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional
    public org.styli.services.customer.pojo.GenericApiResponse<String> recordNudgeSeen(
            CustomerRequestBody request, Map<String, String> requestHeader) {
        
        org.styli.services.customer.pojo.GenericApiResponse<String> response = 
            new org.styli.services.customer.pojo.GenericApiResponse<>();
        
        try {
            // Find the customer by ID
            CustomerEntity customer = client.findByEntityId(request.getCustomerId());
            
            if (customer == null) {
                LOGGER.error("Customer not found for customerId: " + request.getCustomerId());
                response.setStatus(false);
                response.setStatusCode("404");
                response.setStatusMsg("Customer not found");
                return response;
            }
            
            // Update the nudge seen timestamp
            customer.setNudgeSeenTime(new Date());
            customer.setUpdatedAt(new Date());
            client.saveAndFlushCustomerEntity(customer);
            
            LOGGER.info("Nudge seen timestamp recorded for customerId: " + request.getCustomerId());
            
            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("Nudge seen timestamp recorded successfully");
            response.setResponse("Success");
            
        } catch (Exception e) {
            LOGGER.error("Error recording nudge seen timestamp for customerId: " + 
                        (request != null ? request.getCustomerId() : "null"), e);
            response.setStatus(false);
            response.setStatusCode("500");
            response.setStatusMsg("Internal server error: " + e.getMessage());
        }
        
        return response;
    }
}

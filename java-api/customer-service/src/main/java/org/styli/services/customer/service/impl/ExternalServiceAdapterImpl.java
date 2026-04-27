package org.styli.services.customer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.styli.services.customer.component.ConsulComponent;
import org.styli.services.customer.pojo.epsilon.request.*;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerResponse;
import org.styli.services.customer.redis.GlobalRedisHelper;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.service.ExternalServiceAdapter;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.pojo.config.ShukranCountryCodeMapper;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.Stores;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Scope("singleton")
public class ExternalServiceAdapterImpl implements ExternalServiceAdapter, ServiceConfigs.ServiceConfigsListener {

    private static final Log LOGGER = LogFactory.getLog(ExternalServiceAdapterImpl.class);

    private String shukranSource = "";
    private String shukranEnrollmentConceptCode = "";
    private String shukranEnrollmentCommonCode = "";
    private String shukranEnrollmentStoreCode = "";
    private String shukranEnrollChannelCode = "";
    private String globalRedisKey = "";
    private Integer shukranGatewayTimeoutInSeconds = 3; // default value 3
    private String shukranProgramCode = "";
    private String shukranSourceApplication = "";
    private List<ShukranCountryCodeMapper> shukranCountyCodeMapper;


    private static final String GET_PROFILE_SUB_URL = "/api/v1/infrastructure/scripts/GetProfileDetailsWithTier_V2/invoke";
    private static final String UPDATE_PROFILE_SUB_URL = "/api/v1/infrastructure/scripts/UpdateProfileInfo/invoke";
    private static final String ENROLLMENT_SUB_URL = "/api/v2/profiles";
    private static final String PROCESS_ECOM_SUB_URL = "/api/v1/infrastructure/scripts/ProcessEcommDetails/invoke";
    private static final String LOG_STORE_ID_SUFFIX = ", StoreId: ";
    private static final String LOG_ERROR_RESPONSE_SUFFIX = ". Returning error response instead of throwing exception.";

    @Value("${env}")
    String env;

    private RestTemplate restTemplate;

    @Value("${epsilon.base.url}")
    private String epsilonBaseUrl;

    @Autowired
    GlobalRedisHelper globalRedisHelper;

    @PostConstruct
    public void init() {
        ServiceConfigs.addConfigListener(this);
        this.onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
        restTemplate = createRestTemplateWithConnectionPooling();
    }

    private RestTemplate createRestTemplateWithConnectionPooling() {
        // Configure connection pooling to handle connection reuse and prevent exhaustion
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100); // Maximum total connections
        connectionManager.setDefaultMaxPerRoute(20); // Maximum connections per route
        
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(30L, java.util.concurrent.TimeUnit.SECONDS)
                .evictExpiredConnections()
                .build();
        
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        int timeoutInMillis = shukranGatewayTimeoutInSeconds * 1000;
        factory.setConnectTimeout(timeoutInMillis);
        factory.setReadTimeout(timeoutInMillis);
        factory.setConnectionRequestTimeout(timeoutInMillis);
        
        return new RestTemplate(factory);
    }

    @PreDestroy
    public void destroy() {
        ServiceConfigs.removeConfigListener(this);
    }

    private String getAccessToken() {
        return Optional.ofNullable((JSONObject) globalRedisHelper.getGloabalRedis(globalRedisKey, JSONObject.class))
                .map(token -> token.optString("access_token"))
                .orElse("");
    }

    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "OAuth " + accessToken);
        headers.add("Accept-Language", "en-US");
        headers.add("Program-Code", shukranProgramCode);
        headers.add("Source-Application", shukranSourceApplication);
        return headers;
    }

    private URI buildUri(String subUrl) {
        try {
            return new URI(epsilonBaseUrl + subUrl);
        } catch (URISyntaxException e) {
            LOGGER.error("Invalid URI syntax: " + epsilonBaseUrl + subUrl, e);
            return null;
        }
    }

    @Override
    public ResponseEntity<String> getEpsilonProfile(String mobileNo,Integer storeId) {
        String accessToken = getAccessToken();
        LOGGER.info("Access token before invoking epsilon API: " + accessToken);
        RequestEntity<GetProfileDetailsRequest> requestEntity = createProfileRequestEntity(mobileNo,storeId, accessToken);
        LOGGER.info("Get Shukran profile requestEntity: "+requestEntity);
        
        try {
            return restTemplate.exchange(requestEntity, String.class);
        } catch (ResourceAccessException e) {
            if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                if (e.getCause() instanceof org.apache.http.NoHttpResponseException) {
                    LOGGER.info("[enableCustomerServiceErrorHandling] Epsilon service at " + epsilonBaseUrl + " failed to respond. This may indicate service overload, network issues, or connection pool exhaustion. MobileNo: " + mobileNo + LOG_STORE_ID_SUFFIX + storeId + LOG_ERROR_RESPONSE_SUFFIX);
                } else if (e.getCause() instanceof java.net.SocketTimeoutException) {
                    LOGGER.info("[enableCustomerServiceErrorHandling] Epsilon service request timed out after " + shukranGatewayTimeoutInSeconds + " seconds. MobileNo: " + mobileNo + LOG_STORE_ID_SUFFIX + storeId + LOG_ERROR_RESPONSE_SUFFIX);
                } else {
                    LOGGER.info("[enableCustomerServiceErrorHandling] Epsilon service resource access exception. MobileNo: " + mobileNo + LOG_STORE_ID_SUFFIX + storeId + LOG_ERROR_RESPONSE_SUFFIX);
                }
                // Return error response instead of throwing to prevent duplicate logging by Spring error handlers
                return new ResponseEntity<>("{}", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            throw e;
        } catch (RestClientException e) {
            if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                LOGGER.info("[enableCustomerServiceErrorHandling] Epsilon service RestClientException. MobileNo: " + mobileNo + LOG_STORE_ID_SUFFIX + storeId + LOG_ERROR_RESPONSE_SUFFIX);
                // Return error response instead of throwing to prevent duplicate logging by Spring error handlers
                return new ResponseEntity<>("{}", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            throw e;
        }
    }

    private RequestEntity<GetProfileDetailsRequest> createProfileRequestEntity(String mobileNo,Integer storeId, String accessToken) {
        try {
            GetProfileDetailsRequest request = GetProfileDetailsRequest.builder()
                    .Country(buildShukranCurrencyCode(storeId,mobileNo))
                    .FieldName(Constants.PHONE_NUMBER).LookupValue(mobileNo.replaceAll("\\D", "")).build();
            ObjectMapper objectMapper = new ObjectMapper();
            LOGGER.info("Profile Request Data "+ objectMapper.writeValueAsString(request));
            return new RequestEntity<>(request, buildHeaders(accessToken), HttpMethod.POST, buildUri(GET_PROFILE_SUB_URL));
        } catch (Exception e) {
            LOGGER.info("Error Creating Profile Request "+ e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<String> createShukranAccount(ShukranEnrollmentRequest shukranEnrollmentRequest, Customer customer) {
        String accessToken = getAccessToken();
        RequestEntity<String> requestEntity = createEnrollmentRequestEntity(shukranEnrollmentRequest, customer, accessToken);
        LOGGER.info("Create shukran member enrollment requestEntity: "+requestEntity);
        return restTemplate.exchange(requestEntity, String.class);
    }

    private RequestEntity<String> createEnrollmentRequestEntity(ShukranEnrollmentRequest shukranEnrollmentRequest, Customer customer, String accessToken) {
        JSONObject enrollmentJson = buildEnrollmentRequestInJson(shukranEnrollmentRequest, customer);
        LOGGER.info("Create shukran member enrollment enrollmentJson : "+enrollmentJson);
        return new RequestEntity<>(enrollmentJson.toString(), buildHeaders(accessToken), HttpMethod.POST, buildUri(ENROLLMENT_SUB_URL));
    }

    private JSONObject buildEnrollmentRequestInJson(ShukranEnrollmentRequest shukranEnrollmentRequest, Customer customer) {
        String languageCode = buildLanguageCode(shukranEnrollmentRequest.getStoreId());
        JSONObject enrollmentJSON = new JSONObject();

        // Add primitive fields
        enrollmentJSON.put("FirstName", null!=customer.getFirstName() && !customer.getFirstName().isEmpty() && !customer.getFirstName().isBlank()?customer.getFirstName():".");
        enrollmentJSON.put("LastName", null!=customer.getLastName() && !customer.getLastName().isEmpty() && !customer.getLastName().isBlank()?customer.getLastName():".");
        enrollmentJSON.put("Gender", Optional.ofNullable(customer.getGender()).map(g -> g == 1 ? "M" : "F").orElse(""));
        //API-3973 Send birthdate as blank to shukran
        enrollmentJSON.put("BirthDate", "");
        enrollmentJSON.put("SourceCode", shukranSource);
        enrollmentJSON.put("EnrollmentStoreCode", shukranEnrollmentConceptCode + shukranEnrollmentStoreCode + buildStoreCode(shukranEnrollmentRequest.getStoreId()));
        enrollmentJSON.put("LanguageCode", languageCode);
        enrollmentJSON.put("JoinDate", LocalDateTime.now().toString());
        enrollmentJSON.put("EnrollChannelCode", shukranEnrollChannelCode);

        // Add Emails array
        JSONArray emailsArray = new JSONArray();
        JSONObject emailObject = new JSONObject();
        emailObject.put("EmailAddress", customer.getEmail());
        emailsArray.put(emailObject);
        enrollmentJSON.put("Emails", emailsArray);

        // Add Phones array
        JSONArray phonesArray = new JSONArray();
        JSONObject phoneObject = new JSONObject();
        phoneObject.put(Constants.PHONE_NUMBER, customer.getMobileNumber().replaceAll("\\D", ""));
        phonesArray.put(phoneObject);
        enrollmentJSON.put("Phones", phonesArray);

        // Add JsonExternalData object
        JSONObject jsonExternalData = new JSONObject();
        jsonExternalData.put("EnrollmentInvoiceNumber", buildEnrollmentInvoiceNumber(shukranEnrollmentRequest));
        jsonExternalData.put("LMG_PDPL_Flag", "Y");
        jsonExternalData.put("PH_OTPVerified", "Y");

        JSONObject consentObject = new JSONObject();
        consentObject.put("ProvidedDate", LocalDateTime.now().toString());
        consentObject.put("Source", shukranSource);
        consentObject.put("Status", "Consent Provided");
        jsonExternalData.put("Consent", consentObject);
        enrollmentJSON.put("JsonExternalData", jsonExternalData);

        return enrollmentJSON;
    }

    private EnrollmentAPIRequest buildEnrollmentAPIRequest(ShukranEnrollmentRequest shukranEnrollmentRequest, Customer customer) {
        String languageCode = buildLanguageCode(shukranEnrollmentRequest.getStoreId());
        List<MemberPhone> phones = List.of(MemberPhone.builder().PhoneNumber(customer.getMobileNumber().replaceAll("\\D", "")).build());
        List<MemberEmail> emails = List.of(MemberEmail.builder().EmailAddress(customer.getEmail()).build());

        return EnrollmentAPIRequest.builder()
                .FirstName(customer.getFirstName())
                .LastName(null!=customer.getLastName() && !customer.getLastName().isEmpty() && !customer.getLastName().isBlank()?customer.getLastName():".")
                .Gender(Optional.ofNullable(customer.getGender()).map(g -> g == 1 ? "M" : "F").orElse(""))
                .BirthDate(Optional.ofNullable(customer.getDob()).map(Object::toString).orElse(""))
                .Phones(phones)
                .Emails(emails)
                .SourceCode(shukranSource)
                .EnrollmentStoreCode(shukranEnrollmentConceptCode + shukranEnrollmentStoreCode + buildStoreCode(shukranEnrollmentRequest.getStoreId()))
                .LanguageCode(languageCode)
                .JoinDate(LocalDateTime.now().toString())
                .EnrollChannelCode(shukranEnrollChannelCode)
                .JsonExternalData(buildJsonExternalData(shukranEnrollmentRequest))
                .build();
    }

    private JsonExternalData buildJsonExternalData(ShukranEnrollmentRequest shukranEnrollmentRequest) {
        return JsonExternalData.builder()
                .PhOTPVerified("Y")
                .LMG_PDPL_Flag("Y")
                .Consent(buildConsent())
                .EnrollmentInvoiceNumber(buildEnrollmentInvoiceNumber(shukranEnrollmentRequest))
                .build();
    }

    private MemberConsent buildConsent() {
        return MemberConsent.builder()
                .ProvidedDate(LocalDateTime.now().toString())
                .Source(shukranSource)
                .Status("Consent Provided")
                .build();
    }

    private String buildEnrollmentInvoiceNumber(ShukranEnrollmentRequest request) {
        return shukranEnrollmentConceptCode + shukranEnrollmentCommonCode + buildStoreCode(request.getStoreId()) + LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private String buildLanguageCode(Integer storeId) {
        return Constants.getStoresList().stream()
                .filter(store -> storeId.equals(Integer.parseInt(store.getStoreId())))
                .map(Stores::getStoreLanguage)
                .map(String::toUpperCase)
                .findFirst()
                .orElse("EN_US");
    }

    private String buildStoreCode(Integer storeId) {
        return Constants.getStoresList().stream()
                .filter(store -> storeId.equals(Integer.parseInt(store.getStoreId())))
                .map(Stores::getShukranStoreCode)
                .map(String::toUpperCase)
                .findFirst()
                .orElse("");
    }

    private String buildShukranCurrencyCode(Integer storeId,String mobileNo) {
        try {
            String currencyCode="SAU";
            Stores store = Constants.getStoresList().stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId))
                    .findAny().orElse(null);
            if(store != null && StringUtils.isNotEmpty(store.getShukranCurrencyCode()) && StringUtils.isNotBlank(store.getShukranCurrencyCode())){
                currencyCode = store.getShukranCurrencyCode().toUpperCase();
            } else {
                    Optional<ShukranCountryCodeMapper> codeMapper = null!=shukranCountyCodeMapper? shukranCountyCodeMapper.stream().filter(shukranCountyCodeMapper1 -> mobileNo.contains(shukranCountyCodeMapper1.getCode())).findFirst()
                            :null;
                    if (codeMapper.isPresent()) {
                        currencyCode = codeMapper.get().getCountry();
                    }
            }
            LOGGER.info("Currency Code Data"+ currencyCode);
            return currencyCode;
        } catch (Exception e) {
            LOGGER.info("Error Creating Shukran Currency Code"+ e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onConfigsUpdated(Map<String, Object> newConfigs) {
        if (ObjectUtils.isNotEmpty(newConfigs.get("shukranGatewayTimeoutInSeconds"))) {
            shukranGatewayTimeoutInSeconds = (Integer) newConfigs.get("shukranGatewayTimeoutInSeconds");
        } else {
            shukranGatewayTimeoutInSeconds = 3;
        }

        StoreConfigResponse storeConfigResponse = Constants.StoreConfigResponse;
        shukranSource = Optional.ofNullable(storeConfigResponse.getShukranSource()).orElse("");
        shukranEnrollmentConceptCode = Optional.ofNullable(storeConfigResponse.getShukranEnrollmentConceptCode()).orElse("");
        shukranEnrollmentCommonCode = Optional.ofNullable(storeConfigResponse.getShukranEnrollmentCommonCode()).orElse("");
        shukranEnrollmentStoreCode= Optional.ofNullable(storeConfigResponse.getShukarnEnrollmentStoreCode()).orElse("");
        shukranEnrollChannelCode = Optional.ofNullable(storeConfigResponse.getShukranEnrollChannelCode()).orElse("");
        globalRedisKey = Optional.ofNullable(storeConfigResponse.getGlobalRedisKey()).orElse("epsilon-bucket:epsilon-token");
        shukranProgramCode = Optional.ofNullable(storeConfigResponse.getShukranProgramCode()).orElse("SHUKRAN");
        shukranSourceApplication = Optional.ofNullable(storeConfigResponse.getShukranSourceApplication()).orElse("STYLISHOP.COM");
        shukranCountyCodeMapper = storeConfigResponse.getShukranCountryCodeMapper();

    }

    @Override
    public ResponseEntity<String> updateEpsilonProfile(CustomerUpdateProfileRequest customerInfoRequest,String profileId) {
        String accessToken = getAccessToken();
        RequestEntity<String> requestEntity = updateProfileHttpEntity(customerInfoRequest,profileId, accessToken);
        LOGGER.info("Update epsilon profile requestEntity: "+requestEntity);
        return restTemplate.exchange(requestEntity, String.class);
    }

    @Override
    public ResponseEntity<String> linkShukranAccount(String profileId) {
        String accessToken = getAccessToken();
        RequestEntity<String> requestEntity = linkShukranHttpEntity(profileId, accessToken);
        LOGGER.info("link shukran profile requestEntity: "+requestEntity);
        return restTemplate.exchange(requestEntity, String.class);
    }


    @NotNull
    private RequestEntity<String> updateProfileHttpEntity(CustomerUpdateProfileRequest customerInfoRequest,String profileId, String accessToken){
        JSONObject request = new JSONObject();
        if(StringUtils.isNotBlank(profileId)) {
            request.put("ProfileId", profileId);
        }
        if(StringUtils.isNotBlank(customerInfoRequest.getFirstName())) {
            request.put("FirstName", customerInfoRequest.getFirstName());
        }
        if(StringUtils.isNotBlank(customerInfoRequest.getLastName())) {
            request.put("LastName",customerInfoRequest.getLastName());
        }
        if(StringUtils.isNotBlank(customerInfoRequest.getMobileNumber())) {
            request.put(Constants.PHONE_NUMBER,customerInfoRequest.getMobileNumber().replaceAll("[^\\d]", ""));
        }
        if(null!=customerInfoRequest.getGender()) {
            request.put("Gender",Optional.of(customerInfoRequest.getGender()).map(g -> g == 1 ? "M" : "F").orElse(""));
        }
        if(Objects.nonNull(customerInfoRequest.getDob())) {
            Date dob = customerInfoRequest.getDob();
            // Convert Date to LocalDate
            LocalDate localDate = dob.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            // Format LocalDate to String in YYYY-MM-DD format
            String formattedDate = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            request.put("BirthDate",formattedDate);
        }

        return new RequestEntity<>(request.toString(),buildHeaders(accessToken),HttpMethod.POST,buildUri(UPDATE_PROFILE_SUB_URL),String.class);
    }

    @NotNull
    private RequestEntity<String> linkShukranHttpEntity(String profileId, String accessToken){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        JSONObject request = new JSONObject();
        if(StringUtils.isNotBlank(profileId)) {
            request.put("ProfileId", profileId);
        }
        request.put("LMSLinkingFlg", "Y");
        request.put("LMSDate", LocalDateTime.now().format(formatter));

        return new RequestEntity<>(request.toString(),buildHeaders(accessToken),HttpMethod.POST,buildUri(PROCESS_ECOM_SUB_URL),String.class);
    }
}

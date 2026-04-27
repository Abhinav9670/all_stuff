package org.styli.services.customer.utility.helper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.pojo.config.BaseConfig;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;
import org.styli.services.customer.utility.pojo.request.AddressMapperRequest;
import org.styli.services.customer.utility.pojo.request.AdrsmprResponse;
import org.styli.services.customer.utility.pojo.request.SearchCityResponse;
import org.styli.services.customer.utility.service.ConfigService;
import org.styli.services.customer.utility.service.ConfigServiceV2;
import org.styli.services.customer.utility.utility.UtilityConstant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project utility-service
 * @created 17/05/2022 - 1:11 PM
 */

@Component
public class AddressMapperHelperV2 {

    private static final Log LOGGER = LogFactory.getLog(AddressMapperHelperV2.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${adrsmpr.base.url}")
    String adrsmprBaseUrl;

    @Autowired
    AddressMapperHelper addressMapperHelper;
    @Autowired
    ConfigServiceV2 configServiceV2;
    @Value("${gcp.certificate.path}")
    String gcpCertificatePath;
    @Value("${gcp.project.id}")
    String gcpProjectId;
    @Value("${gcp.bucket.name}")
    String gcpBucketName;
    @Value("${env}")
    String env;
    @Value("${gcp.object.name}")
    String gcpObjectName;
    @Autowired
    ConfigService configService;
    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;
    
    @Value("${region}")
    private String region;

    /**
     * @param code               String
     * @param httpServletRequest HttpServletRequest
     * @return Object
     * <p>
     * pushToConsul (addressMapper)
     * upgradeVersion (in database)
     * pushToConsul (store config)
     *
     * error code 201: adrsmprBaseUrl not found!
     * error code 202: Exception during address mapper request!
     * error code 203: Response body empty from address mapper!
     * error code 204: Something went wrong while upgrading version!
     * error code 205: Error converting map to string for address mapper push!
     * error code 206: Exception during rest call!
     * error code 207: Unknown exception!
     */
    public String updateAddressMapperDataToConsul(String code, HttpServletRequest httpServletRequest) {
        LOGGER.info("Address Change : Inside updateAddressMapperDataToConsul");
        try {

            if (StringUtils.isBlank(adrsmprBaseUrl)) {
                String message = "adrsmprBaseUrl not found!";
                LOGGER.error(message);
                return UtilityConstant.responseMap(false, "201", message, null);
            }

            String url = adrsmprBaseUrl + "/api/address/json/legacy";
            HttpHeaders requestHeaders = UtilityConstant.basicHeaders();

            AddressMapperRequest payload = new AddressMapperRequest();
            payload.setCountry(UtilityConstant.convertAdrsMprCode(code));
            HttpEntity<AddressMapperRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
            Map<String, Object> parameters = new HashMap<>();

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    builder.buildAndExpand(parameters).toUri(), HttpMethod.POST, requestBody, Map.class);
            Map result = responseEntity.getBody();
            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                String message = "Exception during address mapper request!";
                LOGGER.error(message);
                return UtilityConstant.responseMap(false, "202", message, null);
            }

            if (ObjectUtils.isEmpty(responseEntity.getBody())) {
                String message = "Response body empty from address mapper!";
                LOGGER.error(message);
                return UtilityConstant.responseMap(false, "203", message, null);
            }
            if(result != null) {
            	result = (Map) result.get("response");
            }
            String response = mapper.writeValueAsString(result);

            if (response != null && code != null) {
                LOGGER.info("Address Change : Inside updateAddressMapperDataToConsul calling sendToConsul");
                addressMapperHelper.sendToConsul(response, code);
                LOGGER.info("Address Change : Inside updateAddressMapperDataToConsul calling saveDataToGCP");
                addressMapperHelper.saveDataToGCP(response, code);
            } else {
                LOGGER.info("Address Change : response or code is null. Skipping saveDataToGCP and sendToConsul calls.");
            }
            String version = addressMapperHelper.upgradeVersion();
            if (StringUtils.isEmpty(version)) {
                String message = "Something went wrong while upgrading version!";
                LOGGER.error(message);
                return UtilityConstant.responseMap(false, "204", message, null);
            }

            StoreConfigResponse configFromConsul = Constants.getConsulConfigResponse();
            configFromConsul.setDbVersion(version);
            String consulString = new ObjectMapper().writeValueAsString(configFromConsul);
            configService.sendToConsul(consulString);
            LOGGER.info("consul pushed successfully");

            String message = "Successfully finished address mapper push operation!";
            LOGGER.info(message);
            return UtilityConstant.responseMap(true, "200", message, result);

        } catch (JsonProcessingException jpe) {
            String message = "Error converting map to string for address mapper push! ";
            LOGGER.error(message + jpe.getMessage());
            return UtilityConstant.responseMap(false, "205", message, null);
        } catch (RestClientException rce) {
            String message = "Exception during rest call! ";
            LOGGER.error(message + rce.getMessage());
            return UtilityConstant.responseMap(false, "206", message, null);
        } catch (Exception e) {
            String message = "Unknown exception! ";
            LOGGER.error(message + e.getMessage());
            return UtilityConstant.responseMap(false, "207", message, null);
        }

    }

    /**
     * @return StoreConfigResponseDTO
     *
     * fetch consul store config & GCP store config
     * compare version
     * if(mismatch) fetch all consul addressMapper and pushToGCP (addressMapper)
     * pushToGCP (store config with new version)
     *
     * error code 201: addressMapperPublishCronFlow false
     * error code 202: GCP IOException!
     * error code 203: Something went wrong!
     * error code 204: Store config missing!
     */
    public StoreConfigResponseDTO pushConsulAddressMapperToGCP() {
        StoreConfigResponseDTO resp = new StoreConfigResponseDTO();

        BaseConfig config = Constants.baseConfig;
        if (!config.isAddressMapperPublishCronFlow()) {
            String message = "addressMapperPublishCronFlow false for pushConsulAddressMapperToConsul!";
            LOGGER.error(message);
            resp.setStatus(false);
            resp.setStatusMsg(message);
            resp.setStatusCode("201");
            return resp;
        }

        try {

            StoreConfigResponse configFromConsul = Constants.getConsulConfigResponse();
            if (ObjectUtils.isEmpty(configFromConsul)) {
                String message = "Store config missing!";
                LOGGER.error(message);
                resp.setStatus(false);
                resp.setStatusMsg(message);
                resp.setStatusCode("204");
                return resp;
            }


            Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(gcpCertificatePath));
            Storage storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(gcpProjectId).build().getService();
            String fullPath = env + "/new_" + gcpObjectName;
            Blob blob = storage.get(gcpBucketName, fullPath);

            boolean needToPush = false;
            String consulVersion = configFromConsul.getDbVersion();
            String gcpVersion;
            if (ObjectUtils.isNotEmpty(blob)) {
                String fileContent = new String(blob.getContent());
                Gson g = new Gson();
                StoreConfigResponseDTO storeConfigResponseFromGCP = g.fromJson(fileContent, StoreConfigResponseDTO.class);
                StoreConfigResponse configFromGCP = storeConfigResponseFromGCP.getResponse();
                gcpVersion = configFromGCP.getDbVersion();

                if (consulVersion.equals(gcpVersion)) {
                    LOGGER.info("Consul version equals GCP version!");
                } else {
                    LOGGER.info("Consul version not equals GCP version!");
                    needToPush = true;
                }
            } else {
                LOGGER.info("GCP blob not found for addressMapper!");
                needToPush = true;
                gcpVersion = "No version";
            }

            LOGGER.info("needToPush is " + needToPush);
            if (needToPush) {
                pushAllAddressMapper();
                pushStoreConfigs(configFromConsul);
            }

            String message = "Successfully completed GCP push process for addressMapper! Consul version: "
                    + consulVersion
                    + " GCP version: "
                    + gcpVersion;
            resp.setStatus(true);
            resp.setStatusMsg(message);
            resp.setStatusCode("200");
            return resp;


        } catch (IOException ioe) {
            String message = "GCP IOException!";
            LOGGER.error(message, ioe);
            resp.setStatus(false);
            resp.setStatusMsg(message);
            resp.setStatusCode("202");
            return resp;
        } catch (Exception e) {
            String message = "Something went wrong!";
            LOGGER.error(message, e);
            resp.setStatus(false);
            resp.setStatusMsg(message);
            resp.setStatusCode("203");
            return resp;
        }
    }

    private void pushAllAddressMapper() {
        Map<String, String> consulAddressMapperData = Constants.getAddressMapper();
        if (MapUtils.isNotEmpty(consulAddressMapperData)) {
            for (Map.Entry<String, String> entry : consulAddressMapperData.entrySet()) {
                LOGGER.info("Found consul addressMapper data for " + entry.getKey());
                addressMapperHelper.saveDataToGCP(entry.getValue(), entry.getKey());
            }
        }
        LOGGER.info("consul addressMapper data pushed to GCP");
    }

    private void pushStoreConfigs(StoreConfigResponse configFromConsul) {
        StoreConfigResponseDTO resp = new StoreConfigResponseDTO();
        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("Success!");
        resp.setResponse(configFromConsul);
        String objectNameNew = "new_" + gcpObjectName;
        configService.saveDataToGCP(resp, objectNameNew);
        configService.saveOldDataToGCP(resp);
        LOGGER.info("GCP pushed successfully");
    }
    
    /**
     * Search City / Pincode
     * @param searchKey
     */
	public SearchCityResponse searchCity(String searchKey) {
		try {
			if (StringUtils.isBlank(adrsmprBaseUrl)) {
				return null;
			}
			String url = adrsmprBaseUrl + "/api/address/search/city/" + searchKey;
			HttpHeaders requestHeaders = UtilityConstant.basicHeaders();
			HttpEntity<AddressMapperRequest> requestBody = new HttpEntity<>(null, requestHeaders);

			ResponseEntity<AdrsmprResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestBody,
					AdrsmprResponse.class);

			AdrsmprResponse body = responseEntity.getBody();
			if (responseEntity.getStatusCode() != HttpStatus.OK || Objects.isNull(body) || !body.isStatus()) {
				LOGGER.error("Exception during address mapper request!");
				return null;
			}
			return body.getResponse();
		} catch (Exception e) {
			String message = "Error converting map to string for address mapper push! ";
			LOGGER.error(message + e.getMessage());
		}
		return null;
	}

}

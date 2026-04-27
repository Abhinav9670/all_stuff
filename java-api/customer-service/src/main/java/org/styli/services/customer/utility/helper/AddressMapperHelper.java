package org.styli.services.customer.utility.helper;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.customer.component.ConsulComponent;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.pojo.request.AddressMapperRequest;
import org.styli.services.customer.utility.service.ConfigServiceV2;
import org.styli.services.customer.utility.utility.UtilityConstant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;

/**
 * @author Umesh, 24/09/2020
 * @project product-service
 */

@Component
public class AddressMapperHelper {

  private static final Log LOGGER = LogFactory.getLog(AddressMapperHelper.class);

  @Autowired
  @Qualifier("withoutEureka")
  private RestTemplate restTemplate;

  @Value("${magento.base.url}")
  String magentoBaseUrl;

  @Value("${adrsmpr.base.url}")
  String adrsmprBaseUrl;

  @Value("${consul.ip.address}")
  String consulIpAddress;

  @Value("${gcp.project.id}")
  String gcpProjectId;

  @Value("${gcp.bucket.name}")
  String gcpBucketName;

  @Value("${gcp.object.name}")
  String gcpObjectName;

  @Value("${env}")
  String env;

  @Value("${gcp.certificate.path}")
  String gcpCertificatePath;

  @Value("${order.ribbon.listOfServers}")
  private String orderServiceBaseUrl;

  @Value("${db.version}")
  private String dbVersion;

  @Value("${adrsmpr.base.flag}")
  String addressMapperFlag;
  
  @Autowired
  ConsulComponent consulComponent;
  
  @Value("${region}")
  private String region;

  
  @Autowired
ConfigServiceV2 configServiceV2;
  
  private static final ObjectMapper mapper = new ObjectMapper();

  public <T> String getAddress(String code, boolean toPush, HttpServletRequest httpServletRequest) {
    String returnResult = null;
    String url;

    LOGGER.info("Address Change: getAddress called with code: {}, toPush: {}" +code);

    if ("1".equals(addressMapperFlag)) {
      url = adrsmprBaseUrl + "/api/address/json/legacy";
      LOGGER.info("Address Change: Address Mapper URL constructed: {}" +url);

      try {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

        AddressMapperRequest payload = new AddressMapperRequest();
        payload.setCountry(UtilityConstant.convertAdrsMprCode(code));
        HttpEntity<AddressMapperRequest> requestBody = new HttpEntity<>(payload, requestHeaders);
        Map<String, Object> parameters = new HashMap<>();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        ResponseEntity<Map> responseEntity = restTemplate.exchange(
                builder.buildAndExpand(parameters).toUri(), HttpMethod.POST, requestBody, Map.class);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
          Map result = responseEntity.getBody();
          LOGGER.info("Address Change: Address Mapper response fetched successfully!!");

          if (result != null) {
            result = (Map) result.get(Constants.RESPONSE_KEY);
          }

          String response = mapper.writeValueAsString(result);
          if (toPush) {
            if (!"IN".equals(region)) {
              LOGGER.info("Address Change: Sending data to consul and gcp!!");
              sendToConsul(response, code);
              saveDataToGCP(response, code);
            }
            String version = upgradeVersion();
            if (StringUtils.isNotEmpty(version)) {
              configServiceV2.getStoreV2Configs(httpServletRequest, false, true);
              LinkedHashMap<String, Object> map = new LinkedHashMap<>();
              map.put(Constants.STATUS_KEY, true);
              map.put(Constants.STATUS_CODE_KEY, "200");
              map.put(Constants.STATUS_MSG_KEY, "success");
              map.put(Constants.RESPONSE_KEY, result);
              returnResult = mapper.writeValueAsString(map);
            } else {
              LinkedHashMap<String, Object> map = new LinkedHashMap<>();
              map.put(Constants.STATUS_KEY, false);
              map.put(Constants.STATUS_CODE_KEY, "203");
              map.put(Constants.STATUS_MSG_KEY, "Something went wrong while upgrading version!");
              returnResult = mapper.writeValueAsString(map);
            }
          } else {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put(Constants.STATUS_KEY, true);
            map.put(Constants.STATUS_CODE_KEY, "200");
            map.put(Constants.STATUS_MSG_KEY, "success");
            map.put(Constants.RESPONSE_KEY, result);
            returnResult = mapper.writeValueAsString(map);
          }
        } else {
          LinkedHashMap<String, Object> map = new LinkedHashMap<>();
          map.put(Constants.STATUS_KEY, false);
          map.put(Constants.STATUS_CODE_KEY, "202");
          map.put(Constants.STATUS_MSG_KEY, "Exception during address mapper request!");
          returnResult = mapper.writeValueAsString(map);
        }
      } catch (RestClientException | JsonProcessingException e) {
        LOGGER.error("Address Change: Error getting address mapper for country " + code + " ,exception: " + e.getMessage());
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(Constants.STATUS_KEY, false);
        map.put(Constants.STATUS_CODE_KEY, "202");
        map.put(Constants.STATUS_MSG_KEY, "Exception during address mapper request!");
        try {
          returnResult = mapper.writeValueAsString(map);
        } catch (Exception em) {
          LOGGER.error("Address Change: Error while writing address {}", e.getCause());
        }
      }

    } else {
      url = magentoBaseUrl + "/en/rest/V1/address/country-code/" + code;
      LOGGER.info("Address Change: Magento URL constructed: {}" +url);

      try {
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        String body = exchange.getBody();

        String response = mapper.writeValueAsString(body);
        if (toPush) {
          LOGGER.info("Address Change: Else condition : Sending data to consul and gcp!!");
          sendToConsul(response, code);
          saveDataToGCP(response, code);
        }
        return response;
      } catch (RestClientException | JsonProcessingException e) {
        return null;
      }
    }
    return returnResult;
  }


  public String upgradeVersion() {
    String result = null;
    try {

      String newVersion = getAddressDbVersion();
      if(StringUtils.isEmpty(newVersion)) {
        newVersion = dbVersion;
      }
      Integer newVersionValue;
      if(StringUtils.isNotEmpty(newVersion)) {
        newVersion = newVersion.replace(".", "");
        try{
          newVersionValue = Integer.parseInt(newVersion);
        } catch (Exception e) {
          newVersionValue = 0;
        }
      } else  {
        newVersionValue = 0;
      }
      newVersionValue = newVersionValue + 1;

      newVersion = newVersionValue.toString();
      if(newVersion.length()==1) {
        newVersion = newVersion+"00";
      } else if(newVersion.length()==2) {
        newVersion = newVersion+"0";
      }
      ArrayList<String> versionChunk = new ArrayList<>(Arrays.asList(newVersion.split("")));
      versionChunk.add(versionChunk.size()-2, ".");
      versionChunk.add(versionChunk.size()-1, ".");
      String[] buffer = new String[versionChunk.size()];
      newVersion = String.join("", versionChunk.toArray(buffer));
      Boolean saveCompleted = saveAddressDbVersion(newVersion);
      if(saveCompleted!=null && saveCompleted) {
        result = newVersion;
      }
	} catch (Exception e) {
		LOGGER.error("Error while gettiing address version", e);
	}
    return result;
  }

  public void sendToConsul(String responseAsString, String code) {
    try {
    	Consul consul = consulComponent.getConsul();
    	KeyValueClient client = consul.keyValueClient();
    	client.putValue("addressMapper_" + code + "_" + env, responseAsString);
      LOGGER.info("Address Change: consul pushed successfully for address mapper");
    } catch (Exception e) {
      LOGGER.error("CONSUL_LOG_EXCEPTION", e);
    }

  }

  public void saveDataToGCP(String jsonStr, String code) {
    LOGGER.info("Address Change : Initiating saveDataToGCP with code: {}" +code);
    Bucket bucket = null;
    Storage storage = null;
    try {
      LOGGER.info("Address Change: Loading credentials from GCP certificate path: {}" +gcpCertificatePath);
      Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(gcpCertificatePath));
      storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(gcpProjectId).build().getService();
      bucket = storage.get(gcpBucketName);
      LOGGER.info("Address Change: Connected to GCP bucket: {}" +gcpBucketName);

      byte[] bytes = jsonStr.getBytes(UTF_8);
      LOGGER.info("Address Change: JSON string converted to byte array.");

      Map<String, String> newMetadata = new HashMap<>();
      newMetadata.put("Cache-Control", "no-cache,max-age=86400");

      if (bucket != null) {
        String fullPath = env + "/address_" + code + ".json";
        LOGGER.info("Address Change: Generated full path for the blob: {}" +fullPath);
        Blob blob = storage.get(gcpBucketName, fullPath);
        if (blob != null) {
          if (blob.exists()) {
            blob.toBuilder().setMetadata(newMetadata).build().update();
            WritableByteChannel channel = blob.writer();
            channel.write(ByteBuffer.wrap(bytes));
            channel.close();
          } else {
            bucket.create(fullPath, bytes, "application/json");
            storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
          }
        } else {
          bucket.create(fullPath, bytes, "application/json");
          storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
          LOGGER.info("Address Change: Blob created successfully at path: {}" +fullPath);
        }
      }
    } catch (Exception e) {
      LOGGER.error("GCP_EXCEPTION", e);
    }
  }


  public String getAddressDbVersion() {
    String result = null;
    try {

      HttpHeaders requestHeaders = new HttpHeaders();
      requestHeaders.setContentType(MediaType.APPLICATION_JSON);
      requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

      HttpEntity<String> requestBody = new HttpEntity<>(requestHeaders);
      String url = "http://"+orderServiceBaseUrl+"/getaddressdbversion";
      Map<String, Object> parameters = new HashMap<>();

      UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

      ResponseEntity<Map> responseBody = restTemplate.exchange(
              builder.buildAndExpand(parameters).toUri(), HttpMethod.GET, requestBody,
              Map.class);
      Map body = responseBody.getBody();
      if (responseBody.getStatusCode() == HttpStatus.OK && body != null) {
        result = (body.get("version")!=null)?body.get("version").toString():null;
      }

    } catch (Exception e) {
      LOGGER.info("getAddressDbVersion error! ", e);
      result = null;
    }
    return result;
  }

  public Boolean saveAddressDbVersion(String newVersion) {
    if(StringUtils.isEmpty(newVersion))
      return false;
    Boolean result = false;
    try {

      HttpHeaders requestHeaders = new HttpHeaders();
      requestHeaders.setContentType(MediaType.APPLICATION_JSON);
      requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

      HttpEntity requestBody = new HttpEntity<>(requestHeaders);
      String url = "http://"+orderServiceBaseUrl+"/saveaddressdbversion/{addressversion}";
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("addressversion", newVersion);

      UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

      ResponseEntity<Map> responseBody = restTemplate.exchange(
              builder.buildAndExpand(parameters).toUri(), HttpMethod.GET, requestBody,
              Map.class);
      Map body = responseBody.getBody();
      if (responseBody.getStatusCode() == HttpStatus.OK && body != null) {
        result = (body.get(Constants.STATUS_KEY)!=null && body.get(Constants.STATUS_KEY).equals(true));
      }
    } catch (Exception e) {
      LOGGER.info("saveAddressDbVersion error! ", e);
    }
    return result;
  }
}

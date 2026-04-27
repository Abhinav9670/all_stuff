package org.styli.services.customer.utility.service.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.customer.component.ConsulComponent;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.client.OrderClient;
import org.styli.services.customer.utility.exception.NotFoundException;
import org.styli.services.customer.utility.pojo.ErrorType;
import org.styli.services.customer.utility.pojo.config.AppEnvironments;
import org.styli.services.customer.utility.pojo.config.Environments;
import org.styli.services.customer.utility.pojo.config.PhoneNumberValidation;
import org.styli.services.customer.utility.pojo.config.Store;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.styli.services.customer.utility.pojo.config.Validation;
import org.styli.services.customer.utility.service.ConfigService;
import org.styli.services.customer.utility.utility.GenericConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;

@Component
public class ConfigUtilityServiceImpl implements ConfigService {

	private static final String GCP_EXCEPTION = "GCP_EXCEPTION";

	private static final String AR = "(.*)ar(.*)";

	private static final String HINTNUMBER = "50123456";

	private static final String APPLICATION_JSON = "application/json";

	private static final String SECONDVALIDATIONREGEX = "(^([1-9]){1}([0-9]{7})$)";

	private static final String VALIDATIONREGEX = "(^(?:[0])?([0-9]{9})$)";

private static final Log LOGGER = LogFactory.getLog(ConfigUtilityServiceImpl.class);

  @Autowired
  @Qualifier("withoutEureka")
  private RestTemplate restTemplate;

  @Value("${magento.base.url}")
  private String magentoBaseUrl;

  @Value("${api.base.url}")
  private String apiBaseUrl;

  @Value("${db.version}")
  private String dbVersion;

  @Value("${android.version}")
  private String androidVersion;

  @Value("${android.update.required}")
  private Boolean androidUpdateRequired;

  @Value("${ios.version}")
  private String iosVersion;

  @Value("${ios.update.required}")
  private Boolean iosUpdateRequired;

  @Value("${secret.react.java.api}")
  private String secretCode;

  @Value("${mobile.last.force.update.version}")
  private String lastUpdatedVersion;

  @Value("${maintenance.mode}")
  private Integer maintenanceMode;

  @Value("${gcp.project.id}")
  String gcpProjectId;

  @Value("${gcp.bucket.name}")
  String gcpBucketName;

  @Value("${gcp.object.name}")
  String gcpObjectName;

  @Value("${env}")
  String env;

  @Value("${order.ribbon.listOfServers}")
  private String orderServiceBaseUrl;

  @Value("${gcp.certificate.path}")
  String gcpCertificatePath;

  @Value("${consul.ip.address}")
  String consulIpAddress;

  @Value("${gcp.bucket.url}")
  String gcpBucketUrl;

  @Value("${adrsmpr.base.flag}")
  String addressMapperFlag;

  @Autowired
  OrderClient orderClient;
  
  @Value("${consul.token}")
  private String consulToken;
  
  @Autowired
  ConsulComponent consulComponent;
  
  @Value("${region}")
  private String region;

  private List<AppEnvironments> getAppEnvironments() {
    List<AppEnvironments> appEnvironmentsList = new ArrayList<>();

    int index = 0;

    appEnvironmentsList.add(index++, new AppEnvironments(GenericConstants.APP_TYPE_ANDROID, androidUpdateRequired,
        androidVersion, lastUpdatedVersion));
    appEnvironmentsList.add(index++,
        new AppEnvironments(GenericConstants.APP_TYPE_IOS, iosUpdateRequired, iosVersion, lastUpdatedVersion));

    return appEnvironmentsList;
  }

  private String parseNullStr(Object val) {
    return (val == null) ? null : String.valueOf(val);
  }

  @Override
  @Transactional(readOnly = true)
  public StoreConfigResponseDTO getStoreV1Configs(HttpServletRequest httpServletRequest, boolean toPush) {

    StoreConfigResponseDTO resp = new StoreConfigResponseDTO();

    try {

      StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
      if ("1".equals(addressMapperFlag)) {
        String newDbVersion = getAddressDbVersion();
        if (StringUtils.isEmpty(newDbVersion)) {
          newDbVersion = dbVersion;
        }
        storeConfigResponse.setDbVersion(newDbVersion);
      } else {
        storeConfigResponse.setDbVersion(dbVersion);
      }
      storeConfigResponse.setEnvironments(getV1Environments());
      storeConfigResponse.setAppEnvironments(getAppEnvironments());
      if (httpServletRequest.getRemoteAddr() != null) {
        storeConfigResponse.setRemoteAddr(httpServletRequest.getRemoteAddr());
      }

      if (httpServletRequest.getHeader(GenericConstants.CONFIG_RESPONSE_X_FORWARDED_FOR) != null) {
        storeConfigResponse
            .setXForwardedFor(httpServletRequest.getHeader(GenericConstants.CONFIG_RESPONSE_X_FORWARDED_FOR));
      }

      if (maintenanceMode != null)
        storeConfigResponse.setMaintenanceMode(maintenanceMode);

      resp.setStatus(true);
      resp.setStatusCode("200");
      resp.setStatusMsg("Success!");
      resp.setResponse(storeConfigResponse);
      /// Save data to GCP
      String objectNameNew = "new_"+gcpObjectName;

      if (toPush) {

          /**for new buckets **/
          saveDataToGCP(resp,objectNameNew);
          LOGGER.info("saved new bucket successfully");

          String consulString = new ObjectMapper().writeValueAsString(resp.getResponse());
          sendToConsul(consulString);

        /** for old buckets 3 countries SA,AE,KW**/
    	  LOGGER.info("push true:");
        saveOldDataToGCP(resp);


      }
      return resp;
    } catch (DataAccessException | NotFoundException e) {
      resp.setStatus(false);
      resp.setStatusCode("201");
      resp.setStatusMsg("Failed!");
      ErrorType errorType = new ErrorType();
      errorType.setErrorCode("201");
      errorType.setErrorMessage(e.getMessage());
      resp.setError(errorType);
      return resp;
    } catch (Exception e) {
      LOGGER.error(GCP_EXCEPTION, e);
      return resp;

    }
  }

	public void saveOldDataToGCP(StoreConfigResponseDTO resp) {
		if (CollectionUtils.isNotEmpty(resp.getResponse().getEnvironments())) {

			StoreConfigResponseDTO cloneRes = new StoreConfigResponseDTO();
			BeanUtils.copyProperties(resp, cloneRes);
			List<Stores> storeList = resp.getResponse().getEnvironments().get(0).getStores();

			if (CollectionUtils.isNotEmpty(storeList)) {

				List<Stores> removeStoreList = new ArrayList<Stores>();
				for (Stores store : storeList) {
					if (!GenericConstants.REMOVE_STORE_CONSTANTS.contains(store.getStoreId()))
						removeStoreList.add(store);
				}

				LOGGER.info("removeStoreList:" + removeStoreList);
				if (CollectionUtils.isNotEmpty(removeStoreList)) {

					cloneRes.getResponse().getEnvironments().get(0).getStores().clear();

					cloneRes.getResponse().getEnvironments().get(0).getStores().addAll(removeStoreList);
					saveDataToGCP(cloneRes, gcpObjectName);
					LOGGER.info("saved old bucket successfully");
				}

			}
		}
	}

  public List<Environments> getV1Environments() throws NotFoundException {
    List<Environments> environmentsList = new ArrayList<>();

    List<Stores> storesArray = new ArrayList<>();

    List<Store> storesList = orderClient.findAllStores();
    for (Store storeObj : storesList) {

      if (!storeObj.getStoreId().equals(Constants.ADMIN_STORE_ID)) {
        Stores stores = new Stores();
        stores.setStoreId(parseNullStr(storeObj.getStoreId()));
        stores.setCurrencyConversionRate(orderClient.getCurrencyConversionRate(storeObj.getWebSiteId()));
        stores.setCatalogCurrencyConversionRate(orderClient.getCatalogCurrencyConversionRate(storeObj.getWebSiteId()));
        stores.setStoreCode(parseNullStr(storeObj.getCode()));
        stores.setStoreLanguage(orderClient.getStoreLanguage(storeObj.getStoreId()));
        stores.setStoreCurrency(orderClient.getStoreCurrency(storeObj.getStoreId()));
        stores.setShipmentChargesThreshold(orderClient.getStoreShipmentChargesThreshold(storeObj.getWebSiteId()));
        stores.setShipmentCharges(orderClient.getStoreShipmentCharges(storeObj.getWebSiteId()));
        stores.setCodCharges(orderClient.getCodCharges(storeObj.getWebSiteId()));
        stores.setTaxPercentage(orderClient.getTaxPercentage(storeObj.getWebSiteId()));
        stores.setWebsiteId(storeObj.getWebSiteId());

        stores.setCustomDutiesPercentage(orderClient.getCustomDutiesPercentage(storeObj.getWebSiteId()));
        stores.setImportFeePercentage(orderClient.getImportFeePercentage(storeObj.getWebSiteId()));
        stores.setMinimumDutiesAmount(orderClient.getMinimumDutyFee(storeObj.getWebSiteId()));
        stores.setQuoteProductMaxAddedQty(orderClient.getQuoteProductMaxQty(storeObj.getWebSiteId()));
        stores.setImportMaxFeePercentage(orderClient.getimportMaxfeepercenatge(storeObj.getWebSiteId()));
        String websiteCode = null;
        stores.setWebsiteCode(websiteCode);

        String storeCode = storeObj.getCode();
        if (storeCode != null) {
          if (storeCode.contains("_")) {
            storeCode = storeCode.replace("_", "/");
          } else {
            storeCode = websiteCode + "/" + storeCode;
          }
        }
        stores.setWebsiteIdentifier(storeCode);

        switch (storeObj.getStoreId()) {
        case 1:
        case 3: {
          if (stores.getStoreLanguage().matches(AR)) {
            stores.setStoreName(GenericConstants.CONFIG_SA_AR_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/ar/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/ar/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/ar/helpcentre");
            stores.setContract("https://stylishop.com/ar/contact");
          } else {
            stores.setStoreName(GenericConstants.CONFIG_SA_EN_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/en/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/en/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/en/helpcentre");
            stores.setContract("https://stylishop.com/en/contact");
          }
          stores.setCountryCode(GenericConstants.CONFIG_SA_DEFAULT_MOBILE_CODE);
          stores.setFlagUrl(gcpBucketUrl + GenericConstants.CONFIG_SA_FLAG_URL);
          stores.setDecimalPricing(false);

          PhoneNumberValidation phnValidation = new PhoneNumberValidation();
          phnValidation.setMaxLength(10);
          phnValidation.setActualLength(9);
          phnValidation.setLableHintNumber("501234567");

          List<Validation> validationList = new ArrayList<>();

          List<String> regexList = new ArrayList<>();
          Validation validation = new Validation();
          regexList.add("(^(?:[0])?([0-9]{10})$)");
          validation.setRegex(regexList);
          validation.setZeroInitialIndex(true);

          Validation phnSecondValidation = new Validation();
          List<String> secondRegexList = new ArrayList<>();
          secondRegexList.add("(^([1-9]){1}([0-9]{8})$)");
          phnSecondValidation.setRegex(secondRegexList);
          phnSecondValidation.setZeroInitialIndex(false);

          validationList.add(validation);
          validationList.add(phnSecondValidation);

          phnValidation.setValidation(validationList);
          stores.setPhoneNumberValidation(phnValidation);

          break;
        }
        case 7:
        case 11: {
          if (stores.getStoreLanguage().matches(AR)) {
            stores.setStoreName(GenericConstants.CONFIG_AE_AR_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/ae_ar/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/ae_ar/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/ae_ar/helpcentre");
            stores.setContract("https://stylishop.com/ae_ar/contact");
          } else {
            stores.setStoreName(GenericConstants.CONFIG_AE_EN_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/ae_en/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/ae_en/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/ae_en/helpcentre");
            stores.setContract("https://stylishop.com/ae_en/contact");
          }
          stores.setCountryCode(GenericConstants.CONFIG_AE_DEFAULT_MOBILE_CODE);
          stores.setFlagUrl(gcpBucketUrl + GenericConstants.CONFIG_AE_FLAG_URL);
          stores.setDecimalPricing(false);

          PhoneNumberValidation phnValidation = new PhoneNumberValidation();
          phnValidation.setMaxLength(10);
          phnValidation.setActualLength(9);
          phnValidation.setLableHintNumber("501234567");

          List<Validation> validationList = new ArrayList<>();

          List<String> regexList = new ArrayList<>();
          Validation validation = new Validation();
          regexList.add("(^(?:[0])?([0-9]{10})$)");
          validation.setRegex(regexList);
          validation.setZeroInitialIndex(true);

          Validation phnSecondValidation = new Validation();
          List<String> secondRegexList = new ArrayList<>();
          secondRegexList.add("(^([1-9]){1}([0-9]{8})$)");
          phnSecondValidation.setRegex(secondRegexList);
          phnSecondValidation.setZeroInitialIndex(false);

          validationList.add(validation);
          validationList.add(phnSecondValidation);

          phnValidation.setValidation(validationList);
          stores.setPhoneNumberValidation(phnValidation);

          break;
        }
        case 12:
        case 13: {
          if (stores.getStoreLanguage().matches(AR)) {
            stores.setStoreName(GenericConstants.CONFIG_KW_AR_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/kw_ar/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/kw_ar/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/kw_ar/helpcentre");
            stores.setContract("https://stylishop.com/kw_ar/contact");
          } else {
            stores.setStoreName(GenericConstants.CONFIG_KW_EN_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/kw_en/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/kw_en/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/kw_en/helpcentre");
            stores.setContract("https://stylishop.com/kw_en/contact");
          }
          stores.setCountryCode(GenericConstants.CONFIG_KW_DEFAULT_MOBILE_CODE);
          stores.setFlagUrl(gcpBucketUrl + GenericConstants.CONFIG_KW_FLAG_URL);
          stores.setDecimalPricing(true);

          PhoneNumberValidation phnValidation = new PhoneNumberValidation();
          phnValidation.setMaxLength(9);
          phnValidation.setActualLength(8);
          phnValidation.setLableHintNumber(HINTNUMBER);

          List<Validation> validationList = new ArrayList<>();

          List<String> regexList = new ArrayList<>();
          Validation validation = new Validation();
          regexList.add(VALIDATIONREGEX);
          validation.setRegex(regexList);
          validation.setZeroInitialIndex(true);

          Validation phnSecondValidation = new Validation();
          List<String> secondRegexList = new ArrayList<>();
          secondRegexList.add(SECONDVALIDATIONREGEX);
          phnSecondValidation.setRegex(secondRegexList);
          phnSecondValidation.setZeroInitialIndex(false);

          validationList.add(validation);
          validationList.add(phnSecondValidation);

          phnValidation.setValidation(validationList);
          stores.setPhoneNumberValidation(phnValidation);

          break;
        }

        case 15:
        case 17: {
          if (stores.getStoreLanguage().matches(AR)) {
            stores.setStoreName(GenericConstants.CONFIG_QA_AR_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/qa_ar/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/qa_ar/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/qa_ar/helpcentre");
            stores.setContract("https://stylishop.com/kw_ar/contact");
          } else {
            stores.setStoreName(GenericConstants.CONFIG_QA_EN_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/qa_en/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/qa_en/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/qa_en/helpcentre");
            stores.setContract("https://stylishop.com/qa_en/contact");
          }
          stores.setCountryCode("");
          stores.setFlagUrl(gcpBucketUrl + GenericConstants.CONFIG_QA_FLAG_URL);
          stores.setDecimalPricing(false);

          PhoneNumberValidation phnValidation = new PhoneNumberValidation();
          phnValidation.setMaxLength(9);
          phnValidation.setActualLength(8);
          phnValidation.setLableHintNumber(HINTNUMBER);

          List<Validation> validationList = new ArrayList<>();

          List<String> regexList = new ArrayList<>();
          Validation validation = new Validation();
          regexList.add(VALIDATIONREGEX);
          validation.setRegex(regexList);
          validation.setZeroInitialIndex(true);

          Validation phnSecondValidation = new Validation();
          List<String> secondRegexList = new ArrayList<>();
          secondRegexList.add(SECONDVALIDATIONREGEX);
          phnSecondValidation.setRegex(secondRegexList);
          phnSecondValidation.setZeroInitialIndex(false);

          validationList.add(validation);
          validationList.add(phnSecondValidation);

          phnValidation.setValidation(validationList);
          stores.setPhoneNumberValidation(phnValidation);

          break;
        }
        case 19:
        case 21: {
          if (stores.getStoreLanguage().matches(AR)) {
            stores.setStoreName(GenericConstants.CONFIG_BH_AR_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/bh_ar/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/bh_ar/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/bh_ar/helpcentre");
            stores.setContract("https://stylishop.com/bh_ar/contact");
          } else {
            stores.setStoreName(GenericConstants.CONFIG_BH_EN_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/bh_en/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/bh_en/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/bh_en/helpcentre");
            stores.setContract("https://stylishop.com/bh_en/contact");
          }
          stores.setCountryCode(GenericConstants.CONFIG_BH_DEFAULT_MOBILE_CODE);
          stores.setFlagUrl(gcpBucketUrl + GenericConstants.CONFIG_BH_FLAG_URL);
          stores.setDecimalPricing(true);

          PhoneNumberValidation phnValidation = new PhoneNumberValidation();
          phnValidation.setMaxLength(9);
          phnValidation.setActualLength(8);
          phnValidation.setLableHintNumber(HINTNUMBER);

          List<Validation> validationList = new ArrayList<>();

          List<String> regexList = new ArrayList<>();
          Validation validation = new Validation();
          regexList.add(VALIDATIONREGEX);
          validation.setRegex(regexList);
          validation.setZeroInitialIndex(true);

          Validation phnSecondValidation = new Validation();
          List<String> secondRegexList = new ArrayList<>();
          secondRegexList.add(SECONDVALIDATIONREGEX);
          phnSecondValidation.setRegex(secondRegexList);
          phnSecondValidation.setZeroInitialIndex(false);

          validationList.add(validation);
          validationList.add(phnSecondValidation);

          phnValidation.setValidation(validationList);
          stores.setPhoneNumberValidation(phnValidation);
          break;
        }
        case 23:
        case 25: {
          if (stores.getStoreLanguage().matches(AR)) {
            stores.setStoreName(GenericConstants.CONFIG_OM_AR_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/om_ar/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/om_ar/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/om_ar/helpcentre");
            stores.setContract("https://stylishop.com/om_ar/contact");
          } else {
            stores.setStoreName(GenericConstants.CONFIG_OM_EN_DEFAULT_COUNTRY);
            stores.setTermsAndUse("https://stylishop.com/om_en/legal/user-agreement");
            stores.setPrivecyPolicy("https://stylishop.com/om_en/legal/privacy");
            stores.setHelpCentreAndFaq("https://stylishop.com/om_en/helpcentre");
            stores.setContract("https://stylishop.com/om_en/contact");
          }
          stores.setCountryCode(GenericConstants.CONFIG_OM_DEFAULT_MOBILE_CODE);
          stores.setFlagUrl(gcpBucketUrl + GenericConstants.CONFIG_OM_FLAG_URL);
          stores.setDecimalPricing(true);

          PhoneNumberValidation phnValidation = new PhoneNumberValidation();
          phnValidation.setMaxLength(9);
          phnValidation.setActualLength(8);
          phnValidation.setLableHintNumber(HINTNUMBER);

          List<Validation> validationList = new ArrayList<>();

          List<String> regexList = new ArrayList<>();
          Validation validation = new Validation();
          regexList.add(VALIDATIONREGEX);
          validation.setRegex(regexList);
          validation.setZeroInitialIndex(true);

          Validation phnSecondValidation = new Validation();
          List<String> secondRegexList = new ArrayList<>();
          secondRegexList.add(SECONDVALIDATIONREGEX);
          phnSecondValidation.setRegex(secondRegexList);
          phnSecondValidation.setZeroInitialIndex(false);

          validationList.add(validation);
          validationList.add(phnSecondValidation);

          phnValidation.setValidation(validationList);
          stores.setPhoneNumberValidation(phnValidation);
          break;
        }
        default:
          break;
        }

        storesArray.add(stores);
      }
    }

    int index = 0;
    environmentsList.add(index,
        new Environments(GenericConstants.ENV_TYPE_ACTUAL, magentoBaseUrl, apiBaseUrl, storesArray));

    return environmentsList;
  }

  public List<Integer> getAnotherStoreIds(Integer storeId) {

    List<Integer> anotherStoreIdList = new ArrayList<>();
    Integer websiteId = null;

    Store store = orderClient.findStoreByStoreId(storeId);

    websiteId = store.getWebSiteId();
    List<Store> storeIds = orderClient.findByWebsiteId(websiteId);
    if (CollectionUtils.isNotEmpty(storeIds)) {

      List<Integer> storeIdList = storeIds.stream().filter(e -> !Objects.equals(e.getStoreId(), storeId)).map(Store::getStoreId)
          .collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(storeIdList)) {

        anotherStoreIdList.addAll(storeIdList);
      }
    }

    return anotherStoreIdList;

  }

  public void saveDataToGCP(StoreConfigResponseDTO resp, String objectName ) {
    Bucket bucket = null;
    Storage storage = null;
    try {
      Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(gcpCertificatePath));
      storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(gcpProjectId).build().getService();
      bucket = storage.get(gcpBucketName);

      ObjectMapper Obj = new ObjectMapper();
      String jsonStr = Obj.writeValueAsString(resp);
      LOGGER.info("JSON STR ::: " + jsonStr);
      byte[] bytes = jsonStr.getBytes(UTF_8);

      Map<String, String> newMetadata = new HashMap<>();
      newMetadata.put("Cache-Control", "public,max-age=1800");
      if (bucket != null) {
        String fullPath = env + "/" + (region.equalsIgnoreCase("IN") ? "in-" : "")  + objectName;
        LOGGER.info("fullPath ::: " + fullPath);
        Blob blob = storage.get(gcpBucketName, fullPath);
        if (blob != null) {
          if (blob.exists()) {
            LOGGER.info("updating blob******");
            blob.toBuilder().setMetadata(newMetadata).build().update();
            WritableByteChannel channel = blob.writer();
            channel.write(ByteBuffer.wrap(bytes));
            channel.close();
          } else {
            LOGGER.info("creating blob******");
            bucket.create(fullPath, bytes, APPLICATION_JSON);
            storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
          }
        } else {
          LOGGER.info("creating in second else  blob******");
          bucket.create(fullPath, bytes, APPLICATION_JSON);
          storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
        }
      }
    } catch (Exception e) {
      LOGGER.error(GCP_EXCEPTION, e);
    }

  }

  public void sendToConsul(String responseAsString) {
    try {
    	Consul consul = consulComponent.getConsul();
    	KeyValueClient client = consul.keyValueClient();
    	client.putValue("appConfig_" + env, responseAsString);
      LOGGER.info("consul pushed successfully");
    } catch (Exception e) {
      LOGGER.error("CONSUL_LOG_EXCEPTION", e);
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
      String url = "http://" + orderServiceBaseUrl + "/getaddressdbversion";
      Map<String, Object> parameters = new HashMap<>();

      UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

      ResponseEntity<Map> responseBody = restTemplate.exchange(builder.buildAndExpand(parameters).toUri(),
          HttpMethod.GET, requestBody, Map.class);
      Map body = responseBody.getBody();
      if (body!= null && responseBody.getStatusCode() == HttpStatus.OK) {
        result = (body.get("version") != null) ? body.get("version").toString()
            : null;
      }
    } catch (Exception e) {
      LOGGER.info("getAddressDbVersion error! ", e);
      result = null;
    }
    return result;
  }
  
	public void saveAllDataToGCP(String objectName) {
		Bucket bucket = null;
		Storage storage = null;
		try {
			Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(gcpCertificatePath));
			storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(gcpProjectId).build()
					.getService();
			bucket = storage.get(gcpBucketName);

			String objectPathNameIN = env + "/" + "in-new_" + gcpObjectName;
			
			String objectPathNameGCC = env + "/" + "new_" + gcpObjectName;
			
			
			Blob blobIN = storage.get(gcpBucketName, objectPathNameIN);
			if(Objects.isNull(blobIN)) {
				LOGGER.info("Bucket File not Exist" + objectPathNameIN);
				return;
			}
			Blob blobGCC = storage.get(gcpBucketName, objectPathNameGCC);
			if(Objects.isNull(blobGCC)) {
				LOGGER.info("Bucket File not Exist" + objectPathNameGCC);
				return;
			}
			
			String fileContentIN = new String(blobIN.getContent());
			String fileContentGCC = new String(blobGCC.getContent());
			
			StoreConfigResponseDTO storeConfigResponseDTOGCC = convertfromJson(fileContentGCC);
			
			StoreConfigResponseDTO storeConfigResponseDTOIN = convertfromJson(fileContentIN);
			
			if (Objects.isNull(storeConfigResponseDTOGCC) || Objects.isNull(storeConfigResponseDTOIN)) {
				LOGGER.error("storeConfigResponseDTOGCC ::: storeConfigResponseDTOIN Null");
				return;
			}
			
			List<Stores> storesIN = storeConfigResponseDTOIN.getResponse().getEnvironments().get(0).getStores();

			List<Stores> storesGCC = storeConfigResponseDTOGCC.getResponse().getEnvironments().get(0).getStores();
			
			for (Stores storeObj : storesIN) {
				storesGCC.add(storeObj);
			}

			Collections.sort(storesGCC, new Comparator<Stores>() {
				public int compare(Stores p1, Stores p2) {
					return Integer.valueOf(p1.getWebsiteId()).compareTo(p2.getWebsiteId());
				}
			});

			storeConfigResponseDTOGCC.getResponse().getEnvironments().get(0).setStores(storesGCC);

			// Exclude brazeWishlistToken from being saved to GCP
			if (storeConfigResponseDTOGCC.getResponse() != null) {
				storeConfigResponseDTOGCC.getResponse().setBrazeWishlistToken(StringUtils.EMPTY);
				LOGGER.info("saveAllDataToGCP: Excluded brazeWishlistToken from GCP save");
			}

			ObjectMapper Obj = new ObjectMapper();
			String jsonStr = Obj.writeValueAsString(storeConfigResponseDTOGCC);
			LOGGER.info("All Json Path:::" + objectName);
			LOGGER.info("All Store JSON:::" + jsonStr);
			byte[] bytes = jsonStr.getBytes(UTF_8);

			Map<String, String> newMetadata = new HashMap<>();
			newMetadata.put("Cache-Control", "public,max-age=1800");

			if (bucket != null) {
				String fullPath = env + "/" + objectName;
				Blob blobwrite = storage.get(gcpBucketName, fullPath);
				if (blobwrite != null) {
					if (blobwrite.exists()) {
						LOGGER.info("updating all json blob******");
						blobwrite.toBuilder().setMetadata(newMetadata).build().update();
						WritableByteChannel channel = blobwrite.writer();
						channel.write(ByteBuffer.wrap(bytes));
						channel.close();
					} else {
						LOGGER.info("creating all json blob******");
						bucket.create(fullPath, bytes, APPLICATION_JSON);
						storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
					}
				} else {
					LOGGER.info("creating in second else  all json blob******");
					bucket.create(fullPath, bytes, APPLICATION_JSON);
					storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
				}
			}
		} catch (Exception e) {
			LOGGER.error(GCP_EXCEPTION, e);
		}

	}

	private StoreConfigResponseDTO convertfromJson(String fileContentGCC) {
		StoreConfigResponseDTO storeConfigResponseDTOGCC = null;
		try {
			storeConfigResponseDTOGCC = new Gson().fromJson(fileContentGCC, StoreConfigResponseDTO.class);
		} catch (final Exception e) {
			LOGGER.error("GCP_EXCEPTION GCC readValue", e);
			return storeConfigResponseDTOGCC;
		}
		return storeConfigResponseDTOGCC;
	}

}
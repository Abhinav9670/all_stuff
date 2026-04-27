package org.styli.services.customer.service.impl.Address;

import java.text.Collator;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.component.GcpStorage;
import org.styli.services.customer.helper.NationalIdHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.model.CustomerGridFlat;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressBody;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.response.ProvinceResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Address.CustomerAddressEntityVarcharRepository;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerGridFlatRepository;
import org.styli.services.customer.service.impl.AsyncService;
import org.styli.services.customer.service.impl.SaveCustomer;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.UtilityCustomerConatant;
import org.styli.services.customer.utility.helper.AddressMapperHelperV2;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.styli.services.customer.utility.pojo.request.SearchCityResponse;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.OtpService;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.helper.AddressNudgeUtility;

@Component
public class SaveAddress {
    Map<Integer, String> attributeMap;
    
    private static final String NAME_AR = "name_ar";
    private static final String NAME_EN = "name_en";
    
    private static final Log LOGGER = LogFactory.getLog(SaveAddress.class);
    
	@Value("${region}")
    private String region;
	
	@Autowired
	private AddressMapperHelperV2 addressMapperHelperV2;
	
	@Autowired
	private AsyncService asyncService;
	
	@Autowired
	OtpService otpService;
	
	@Autowired
	SaveCustomer saveCustomer;
	
	@Autowired
	Client client;

	@Autowired
	@Qualifier("restTemplateBuilder")
	RestTemplate restTemplate;

	@Autowired
	protected RedisHelper redisHelper;

	@Autowired
	private AddressTransactionService addressTransactionService;

	@Autowired
	private NationalIdHelper nationalIdHelper;

	@Autowired
	private GcpStorage gcpStorage;


//	private final String addressMapperBaseUrl = ServiceConfigs.getUrl("addressMapperBaseUrl");
	@Value("${adrsmpr.base.url}")
	private String addressMapperBaseUrl;

	private static final int VERIFIED = 1;
	
    public CustomerAddreesResponse saveUpdate(CustomerAddrees customerAddRequest, boolean isSave,
            CustomerEntityRepository customerEntityRepository,
            CustomerAddressEntityRepository customerAddressEntityRepository, StaticComponents staticComponents,
            CustomerGridFlatRepository customerGridFlatRepository,
            CustomerAddressEntityVarcharRepository customerAddressEntityVarcharRepository, String consulIpAddress,
            String env, PasswordHelper passwordHelper, Map<?, ?> saveEmailTranslation,Map<String, String> requestHeader) {
        CustomerAddreesResponse response = new CustomerAddreesResponse();
        CustomerAddressBody responseBody = new CustomerAddressBody();
        CustomerAddressEntity customerAddres = new CustomerAddressEntity();
        CustomerEntity customer = null;
        Boolean isMobileVerifiedInRedis = false;
        Boolean phoneVerifiedAtAddress = false;
        
        List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		System.out.println("add/address: : Store IDs enabled for OTP feature: " + storeIds);

		Integer storeIdFromPayload = customerAddRequest.getStoreId();

		boolean isStoreValid = storeIds.contains(storeIdFromPayload);
		
		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();

        StringBuilder sbAddress = new StringBuilder();
        boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);

        String minAppVersion = ServiceConfigs.getMinAppVersionReqdForOtpFeature();
        try {
        	
        	if(null  == customerAddRequest.getCustomerId()) {
        		
                  response.setStatus(false);
                  response.setStatusCode("209");
                  response.setStatusMsg("customer id is missing!!");
                  
                  return response;
        	}

            if (null != customerAddRequest.getCustomerId()
                    && !customerEntityRepository.existsById(customerAddRequest.getCustomerId())
                    || (!isSave && null != customerAddRequest.getAddressId()
                            && !customerAddressEntityRepository.existsById(customerAddRequest.getAddressId()))) {

                String msg = null;

                msg = setMessage(isSave);
                response.setStatus(false);
                response.setStatusCode("201");
                response.setStatusMsg(msg);

                return response;
            }
            if (StringUtils.isNotBlank(customerAddRequest.getMobileNumber())) {

            	String str=  Constants.MOBILE_NUMBER_VALIDATION_REGEX;


                if (!Pattern.compile(str).matcher(customerAddRequest.getMobileNumber()).matches()) {
                      
                	 response.setStatus(false);
                     response.setStatusCode("208");
                     response.setStatusMsg("invalid mobile number format!");
                     return response;
                   } 

            }

            customer = customerEntityRepository.findByEntityId(customerAddRequest.getCustomerId());

            if (null != customer) {
            	final boolean needAlternateEmail =
						(StringUtils.isNotEmpty(customer.getEmail())
								&& customer.getEmail().matches(Constants.WHATSAPP_EMAIL_REGEX));

				Stores currentStore = null;
            	String lang = "en";

				if(CollectionUtils.isNotEmpty(Constants.getStoresList()) && customerAddRequest.getStoreId() != null) {
					currentStore = Constants.getStoresList().stream().findFirst().filter(e ->
							e!=null && StringUtils.isNotEmpty(e.getStoreId()) &&
									e.getStoreId().equals(customerAddRequest.getStoreId().toString()))
							.orElse(null);
					lang = ((currentStore != null &&
							StringUtils.isNotEmpty(currentStore.getStoreLanguage()) &&
							StringUtils.isNotEmpty(currentStore.getStoreLanguage().split("_")[0]))
							? currentStore.getStoreLanguage().split("_")[0] : lang);
				}

				if(needAlternateEmail && StringUtils.isNotBlank(customerAddRequest.getEmail())) {
					boolean emailChanged = false;
					String message = "Invalid Email address!";
					message = (MapUtils.isNotEmpty(saveEmailTranslation) &&
							ObjectUtils.isNotEmpty(saveEmailTranslation.get(lang)))
							? saveEmailTranslation.get(lang).toString() : message;
					try{
						CustomerEntity existingCustomer = customerEntityRepository
								.findByEmail(customerAddRequest.getEmail());
						if(existingCustomer != null && existingCustomer.getEntityId() != null) {
							response.setStatus(false);
							response.setStatusCode("205");
							response.setStatusMsg(message);
							response.setResponse(null);
							return response;
						} else {
							customer.setEmail(customerAddRequest.getEmail());
							customerEntityRepository.save(customer);
							emailChanged = true;
							Customer cust = new Customer();
							cust.setWhatsApp(true);
							cust.setMobileNumber(customer.getPhoneNumber());
							cust.setFirstName(customer.getFirstName());
							cust.setLastName(customer.getLastName());
							cust.setCustomerId(customer.getEntityId());
							asyncService.asyncSalesOrdersUpdateCustId(cust);
						}
						
					} catch (Exception e) {
						response.setStatus(false);
						response.setStatusCode("206");
						response.setStatusMsg(message);
						response.setResponse(null);
						return response;
					}
					if(emailChanged) {
						try {
							String userId = null;
							if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
									|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
								userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
							}else {
								userId = customer.getEmail();
								refreshTokenFlag = false;
							}
							customerAddRequest.setJwtToken(passwordHelper.generateToken(userId,
									String.valueOf(new Date().getTime()), customer.getEntityId(),refreshTokenFlag));
						} catch (Exception e) {
							LOGGER.error("save address jwt error : " + e.getMessage());
						}
					}
				}

                attributeMap = staticComponents.getAttrMap();

                setEntityId(customerAddRequest, isSave, customerAddres);
                customerAddres.setParentId(customerAddRequest.getCustomerId());
                customerAddres.setFirstname(customerAddRequest.getFirstName());
                customerAddres.setLastName(customerAddRequest.getLastName());
                if (StringUtils.isNotBlank(customerAddRequest.getCountry())
                        && customerAddRequest.getCountry().length() <= 2) {

                    customerAddres.setCountryId(customerAddRequest.getCountry());

                } else {

                    response.setStatus(false);
                    response.setStatusCode("201");
                    response.setStatusMsg("Invalid Country ID");

                    return response;
                }


                // Validate shortAddress format if provided
                if (StringUtils.isNotBlank(customerAddRequest.getShortAddress())) {
                    if (!customerAddRequest.getShortAddress().matches("^[A-Za-z]{4}[0-9]{4}$")) {
                        response.setStatus(false);
                        response.setStatusCode("400");
                        response.setStatusMsg("Short address must have first 4 alphabets followed by 4 digits");
                        return response;
                    }
                }

                if(null == customerAddRequest.getLatitude() &&
                		null == customerAddRequest.getLongitude() &&
                		!processProvinceCityAndArea(customerAddRequest, response))
                	return response;
                
                setCityRegionStreet(customerAddRequest, customerAddres, sbAddress);

                customerAddres.setStreet(sbAddress.toString());
                customerAddres.setTelephone(customerAddRequest.getMobileNumber());
                customerAddres.setUpdatedAt(new java.util.Date());
                customerAddres.setIsActive(1);
                
                customerAddres.setFormattedAddress(customerAddRequest.getFormattedAddress());
                customerAddres.setLatitude(customerAddRequest.getLatitude());
                customerAddres.setLongitude(customerAddRequest.getLongitude());
                
                // Set new national address format fields
                customerAddres.setUnitNumber(customerAddRequest.getUnitNumber());
                customerAddres.setPostalCode(customerAddRequest.getPostalCode());
                customerAddres.setShortAddress(customerAddRequest.getShortAddress());

                setNationalIdOnAddress(customerAddRequest, customerAddres);
                
                if("true".equalsIgnoreCase(customerAddRequest.getIsSignUpOtpEnabled()) && (saveCustomer.isVersionGreaterOrEqual(customerAddRequest.getClientVersion(), minAppVersion) || customerAddRequest.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE))) {
                     
                List<CustomerAddressEntity> customerAddressList = customerAddressEntityRepository.findAllByCustomerId(customerAddRequest.getCustomerId());

				if (customerAddressList != null && !customerAddressList.isEmpty()) {
					LOGGER.info("add/address: Found " + customerAddressList.size()
							+ " address(es) for customer with mobile number: " + customerAddRequest.getMobileNumber());

					phoneVerifiedAtAddress = customerAddressList.stream().filter(Objects::nonNull)
							.anyMatch(address -> customerAddRequest.getMobileNumber().equals(address.getTelephone())
									&& Integer.valueOf(VERIFIED).equals(address.getIsMobileVerified()));

					if (phoneVerifiedAtAddress) {
						LOGGER.info("add/address: Mobile number " + customerAddRequest.getMobileNumber()
								+ " is verified at an address.");
					} else {
						LOGGER.info("add/address: sendOtp : Mobile number " + customerAddRequest.getMobileNumber()
								+ " is not verified at any address.");
					}
				}

				boolean phoneVerifiedAtAccount = false;
				if (customerAddRequest.getMobileNumber().equals(customer.getPhoneNumber())) {
					phoneVerifiedAtAccount = Boolean.TRUE.equals(customer.getIsMobileVerified());
					LOGGER.info("add/address: Mobile number " + customerAddRequest.getMobileNumber()
							+ (phoneVerifiedAtAccount ? " is verified" : " is not verified")
							+ " at the account level.");
				}

				if (customerAddRequest.getMobileNumber() != null) {
					LOGGER.info("add/address: Verifying mobile number: " + customerAddRequest.getMobileNumber());
					isMobileVerifiedInRedis = otpService.getVerificationStatusFromRedis(customerAddRequest.getMobileNumber());
					LOGGER.info("add/address: Mobile verification status for " + customerAddRequest.getMobileNumber()
							+ " is " + isMobileVerifiedInRedis);
				}

				boolean phoneVerified = phoneVerifiedAtAccount || phoneVerifiedAtAddress || isMobileVerifiedInRedis;

				if (phoneVerified) {
	                customerAddres.setIsMobileVerified(1);
	                customerAddRequest.setIsMobileVerified(true);
	                LOGGER.info("add/address: Mobile is verified. Setting isMobileVerified to 1.");
	                if (customerAddRequest.getMobileNumber() != null && customer.getPhoneNumber() != null) {
	                    String requestMobile = customerAddRequest.getMobileNumber().replaceAll("\\s", "").replace("+966", "");
	                    String customerPhone = customer.getPhoneNumber().replaceAll("\\s", "");
	                    LOGGER.info("Customer Add Request Mobile Number: {}" +requestMobile);
    					LOGGER.info("Customer Phone Number: {}" +customerPhone);
	                    if (requestMobile.equals(customerPhone)) {
	                        LOGGER.info("add/address: MongoDB mobile verified as true. Same number is present in the account.");
	                        customer.setIsMobileVerified(true);
	                    }
	                }
	            } else {
					customerAddRequest.setIsMobileVerified(false);
					if("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) {
						LOGGER.info(
								"add/address: Mobile is not verified or verification status is null. Setting isMobileVerified to 0.");
						responseBody.setAddress(customerAddRequest);
						response.setResponse(responseBody);
						response.setStatus(true);
						response.setStatusCode("213");
						response.setStatusMsg("Something went wrong!");
						return response;
					}
				}
			}
                
            client.saveAndFlushCustomerEntity(customer);    

            CustomerAddressEntity newCustAddressObject = addressTransactionService.saveAddressInSeparateTransaction(customerAddres, response, customerAddressEntityRepository);
            if (newCustAddressObject == null) {
                // Error response already set in saveAddressInSeparateTransaction
                return response;
            }

            createAddressEntityVarchar(customerAddRequest, customerAddressEntityVarcharRepository,
					newCustAddressObject);

            checkIfDefaultAddressAndProcess(customerAddRequest, customerEntityRepository,
					customerGridFlatRepository, customer, newCustAddressObject);
            customerAddRequest.setAddressId(newCustAddressObject.getEntityId());

            // Re-evaluate address compliance status after save/update
            // This ensures isAddressCompliance is accurate based on ALL addresses, not just the current one
            // Handles cases where shortAddress is removed or multiple addresses exist
            List<CustomerAddressEntity> allAddresses = customerAddressEntityRepository.findAllByCustomerId(customerAddRequest.getCustomerId());
            AddressNudgeUtility.updateAddressComplianceStatus(customer, allAddresses, client);
            
            // Check all addresses for KSA compliance and set ksaAddressCompliant flag in response
            boolean allAddressesCompliant = true;
            if (allAddresses != null && !allAddresses.isEmpty()) {
                for (CustomerAddressEntity addressEntity : allAddresses) {
                    if (!addressEntity.getEntityId().equals(customerAddRequest.getAddressId()) && addressEntity.getIsActive() != null && addressEntity.getIsActive().equals(1)) {
                        // Use the street field as streetAddress
                        String streetAddress = addressEntity.getStreet();
                        if (!isAddressKsaCompliant(addressEntity.getShortAddress(), streetAddress, addressEntity.getUnitNumber(), addressEntity.getPostalCode())) {
                            allAddressesCompliant = false;
                            break;
                        }
                    }
                }
            }
            // Set ksaAddressCompliant: true only if there are active addresses AND all are compliant
			boolean isCurrentAddressCompliant = isAddressKsaCompliant(
			    customerAddRequest.getShortAddress(), 
			    customerAddRequest.getStreetAddress(), 
			    customerAddRequest.getUnitNumber(), 
			    customerAddRequest.getPostalCode()
			);
            responseBody.setKsaAddressCompliant(allAddressesCompliant && isCurrentAddressCompliant);
			customerAddRequest.setKsaAddressCompliant(isCurrentAddressCompliant);
            // Note: customer entity is saved in updateAddressComplianceStatus, so we don't need to save again here
            responseBody.setAddress(customerAddRequest);
            response.setResponse(responseBody);
            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("SUCCESS");

        } else {

            response.setStatus(false);
            response.setStatusCode("201");
            response.setStatusMsg("Invalid Customer ID");
        }

    } catch (DataAccessException exception) {

        ErrorType error = new ErrorType();

        error.setErrorCode("400");

        error.setErrorMessage(exception.getMessage());

        response.setStatus(false);
        response.setStatusCode("204");
        response.setStatusMsg("ERROR");
        response.setError(error);
    } catch (JSONException e1) {
    	LOGGER.error("save address Jos error : " + e1.getMessage());
    }
    response.setIsSignUpOtpEnabled(("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) ? "true" : "false");
    return response;
}

	private String setMessage(boolean isSave) {
		String msg;
		if (isSave) {

		    msg = "Invalid Customer ID";

		} else {

		    msg = "Invalid Address/Customer ID";
		}
		return msg;
	}

	private void setEntityId(CustomerAddrees customerAddRequest, boolean isSave, CustomerAddressEntity customerAddres) {
		if (!isSave && null != customerAddRequest.getAddressId()) {

		    customerAddres.setEntityId(customerAddRequest.getAddressId());
		} else {

		    customerAddres.setCreatedAt(new java.util.Date());

		}
	}

	private void setNationalIdOnAddress(CustomerAddrees customerAddRequest, CustomerAddressEntity customerAddres) {
		if (customerAddRequest.getNationalId() == null) {
			return;
		}
		customerAddres.setNationalIdType(customerAddRequest.getNationalId().getType());
		String filePath = customerAddRequest.getNationalId().getFilePath();
		if (filePath != null && !filePath.isBlank()) {
			String finalPath = gcpStorage.moveNationalIdFromTempToPermanent(filePath);
			String pathToStore = (finalPath != null) ? finalPath : filePath;
			customerAddres.setNationalIdImageData(nationalIdHelper.encryptNationalIdDetails(pathToStore));
			if (finalPath != null) {
				customerAddRequest.getNationalId().setFilePath(finalPath);
			}
		} else {
			customerAddres.setNationalIdImageData(null);
		}
		String nationalIdNumber = customerAddRequest.getNationalId().getNumber();
		customerAddres.setNationalIdNumber(nationalIdNumber != null ? nationalIdHelper.encryptNationalIdDetails(nationalIdNumber) : null);
		customerAddres.setNationalIdExpirationDate(customerAddRequest.getNationalId().getExpirationDate());
	}

	private void checkIfDefaultAddressAndProcess(CustomerAddrees customerAddRequest,
			CustomerEntityRepository customerEntityRepository, CustomerGridFlatRepository customerGridFlatRepository,
			CustomerEntity customer, CustomerAddressEntity newCustAddressObject) {
		if (customerAddRequest.isDefaultAddress() && null != newCustAddressObject) {

		    customer.setDefaultShipping(newCustAddressObject.getEntityId());
		    customer.setDefaultBilling(newCustAddressObject.getEntityId());
		    customerEntityRepository.save(customer);

		    savecustomerGridFlat(newCustAddressObject, customer.getEntityId(), customerGridFlatRepository);

		} else {

		    if (null != customer && null == customer.getDefaultShipping()
		            && null == customer.getDefaultBilling() && null != newCustAddressObject) {

		        customer.setDefaultShipping(newCustAddressObject.getEntityId());
		        customer.setDefaultBilling(newCustAddressObject.getEntityId());
		        customerEntityRepository.save(customer);

		        savecustomerGridFlat(newCustAddressObject, customer.getEntityId(), customerGridFlatRepository);

		    }

		}
	}

	private void createAddressEntityVarchar(CustomerAddrees customerAddRequest,
			CustomerAddressEntityVarcharRepository customerAddressEntityVarcharRepository,
			CustomerAddressEntity newCustAddressObject) {
		
		if(null != newCustAddressObject) {
			CustomerAddressEntityVarchar customerAddressEntityVarchar = new CustomerAddressEntityVarchar();
	
			CustomerAddressEntityVarchar createaddressEntityVar = null;
	
			Integer areaAttributeId = attributeMap.entrySet().stream()
			        .filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_AREA_ATTRIBUTE))
			        .map(Map.Entry::getKey).findFirst().orElse(0);
	
			Integer landmarkAttributeId = attributeMap.entrySet().stream()
			        .filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_LANDMARK_ATTRIBUTE))
			        .map(Map.Entry::getKey).findFirst().orElse(0);
	
			createaddressEntityVar = customerAddressEntityVarcharRepository
			        .findByEntityIdAndAttributeId(newCustAddressObject.getEntityId(), landmarkAttributeId);
	
			if (null != createaddressEntityVar) {
	
			    customerAddressEntityVarchar.setValueId(createaddressEntityVar.getValueId());
			}
	
			customerAddressEntityVarchar.setEntityId(newCustAddressObject.getEntityId());
			customerAddressEntityVarchar.setAttributeId(landmarkAttributeId);
			customerAddressEntityVarchar.setValue(customerAddRequest.getLandMark());
	
			customerAddressEntityVarcharRepository.saveAndFlush(customerAddressEntityVarchar);
	
			createaddressEntityVar = customerAddressEntityVarcharRepository
			        .findByEntityIdAndAttributeId(newCustAddressObject.getEntityId(), areaAttributeId);
	
			customerAddressEntityVarchar = new CustomerAddressEntityVarchar();
	
			if (null != createaddressEntityVar) {
	
			    customerAddressEntityVarchar.setValueId(createaddressEntityVar.getValueId());
			}
	
			customerAddressEntityVarchar.setEntityId(newCustAddressObject.getEntityId());
			customerAddressEntityVarchar.setAttributeId(areaAttributeId);
			setCustomerAddressVarcharValue(customerAddRequest, customerAddressEntityVarchar);
	
			customerAddressEntityVarcharRepository.saveAndFlush(customerAddressEntityVarchar);
		}
	}

	private void setCustomerAddressVarcharValue(CustomerAddrees customerAddRequest,
			CustomerAddressEntityVarchar customerAddressEntityVarchar) {
		if (StringUtils.isNotBlank(customerAddRequest.getArea())
		        && customerAddRequest.getArea().contains("\\")) {
		    customerAddressEntityVarchar.setValue(customerAddRequest.getArea().replace("\\", ""));
		    customerAddRequest.setArea(customerAddRequest.getArea().replace("\\", ""));

		} else if (StringUtils.isNotBlank(customerAddRequest.getArea())) {

		    customerAddressEntityVarchar.setValue(customerAddRequest.getArea());

		} else {
		    /** in case area is blank setting city as area **/
		    customerAddressEntityVarchar.setValue(customerAddRequest.getCity());
		}
	}

	private void setCityRegionStreet(CustomerAddrees customerAddRequest, CustomerAddressEntity customerAddres,
			StringBuilder sbAddress) {
		if (StringUtils.isNotBlank(customerAddRequest.getCity())
		        && customerAddRequest.getCity().contains("\\")) {

		    customerAddres.setCity(customerAddRequest.getCity().replace("\\", ""));
		    customerAddRequest.setCity(customerAddRequest.getCity().replace("\\", ""));

		} else {

		    customerAddres.setCity(customerAddRequest.getCity());
		}

		customerAddres.setRegion(customerAddRequest.getRegion());

		// Check if regionId is null or empty
		if (StringUtils.isBlank(customerAddRequest.getRegionId()) || customerAddRequest.getRegionId()==null ||
				customerAddRequest.getRegionId().equalsIgnoreCase("null")) {
			try {
				// Call external API to get region-to-regionId map
				Map regionMap = fetchRegionMapFromApi(customerAddRequest.getCountry(), customerAddRequest.getRegion());

				LOGGER.warn("Region map from address mapper: " + regionMap);

				// Get regionId from the map using the region name
				String regionId = regionMap.get("id") != null ? regionMap.get("id").toString() : null;

				// Set the regionId if found
				if (StringUtils.isNotBlank(regionId)) {
					customerAddres.setRegionId(regionId);
				} else {
					LOGGER.warn("RegionId not found for region: " + customerAddRequest.getRegion());
				}
			} catch (Exception e) {
				LOGGER.error("Error while fetching regionId from external API: " + e.getMessage(), e);
			}
		} else {
			customerAddres.setRegionId(customerAddRequest.getRegionId());
		}


		setSbAddress(customerAddRequest, sbAddress);
	}

	private void setSbAddress(CustomerAddrees customerAddRequest, StringBuilder sbAddress) {
		if (StringUtils.isNotBlank(customerAddRequest.getBuildingNumber())) {

		    sbAddress.append(customerAddRequest.getBuildingNumber()).append("\n");

		}
		if (StringUtils.isNotBlank(customerAddRequest.getStreetAddress())) {

		    sbAddress.append(customerAddRequest.getStreetAddress());
		}
	}

	private boolean processProvinceCityAndArea(CustomerAddrees customerAddRequest, CustomerAddreesResponse response )
			throws JSONException {
		
		if("IN".equals(region)) {
			return validateAddress(customerAddRequest, response);
		}
		
		String mapper = Constants.getAddressMapper(customerAddRequest.getCountry().toLowerCase());
		LOGGER.info("mapper string before area check:"+mapper);
		boolean areaCorrect = false;
		boolean isArabic = false;
		if (mapper != null && StringUtils.isNotEmpty(mapper)) {
		    
		    JSONObject jsonObj = new JSONObject(mapper);
		    JSONObject province = jsonObj.getJSONObject("provinces");
		    Iterator<?> provinceIterator = province.keys();
		    while (provinceIterator.hasNext()) {
		        Object provinceKey = provinceIterator.next();
		        JSONObject provinceValue = province.getJSONObject(provinceKey.toString());
		        if (provinceKey.toString().toLowerCase().trim()
		                .equals(customerAddRequest.getCountry().toLowerCase())) {
		            JSONObject provinceNext = provinceValue;
		            Iterator<?> provinceNextIterator = provinceNext.keys();
		            
		            areaCorrect = setProvince(customerAddRequest, response, areaCorrect, provinceNext,
							provinceNextIterator,isArabic);

		        }

		    }
		    	
		    
		}
		if (null != response) {
			if (StringUtils.isNoneEmpty(response.getStatusMsg())) {

				response.setStatusMsg(response.getStatusMsg());
				response.setStatusMsg("212");
			} else {
				if (null != customerAddRequest.getStoreId()
						&& Constants.arabicStores.contains(customerAddRequest.getStoreId())) {
					response.setStatusMsg(Constants.getAddressvalidation().getRegionValidateMsgAr());
				} else {

					response.setStatusMsg(Constants.getAddressvalidation().getRegionValidateMsgEn());
				}
			}
		}
		
		return areaCorrect;
	}

	private boolean validateAddress(CustomerAddrees addr, CustomerAddreesResponse response) {
		try {
			SearchCityResponse res = addressMapperHelperV2.searchCity(addr.getArea());

			if (Objects.isNull(res)) {
				response.setStatusMsg("Error In getting address details.");
				return false;
			}

			if (StringUtils.equalsIgnoreCase(res.getCity(), addr.getCity())
					&& StringUtils.equalsIgnoreCase(res.getCountry(), addr.getCountry())
					&& StringUtils.equalsIgnoreCase(res.getProvince(), addr.getRegion())) {
				return true;
			} else {
				response.setStatusMsg("Provided Country, Province, City name is incorrect.");
				return false;
			}
		} catch (Exception e) {
			response.setStatusMsg("There is and error In getting address details.");
			LOGGER.error("Error In Validating Address By Pincode : ", e);
		}
		return false;
	}

	private boolean setProvince(CustomerAddrees customerAddRequest, CustomerAddreesResponse response,
			boolean areaCorrect, JSONObject provinceNext, Iterator<?> provinceNextIterator,boolean isArabic) throws JSONException {
		while (provinceNextIterator.hasNext()) {
		    Object provinceNextKey = provinceNextIterator.next();
		    JSONObject provinceNextValue = provinceNext.getJSONObject(provinceNextKey.toString());
		    if (StringUtils.isNoneBlank(customerAddRequest.getRegion()) 
		            && provinceNextValue.get("name").toString().trim()
		                    .equals(customerAddRequest.getRegion().trim())
		            || StringUtils.isNoneBlank(customerAddRequest.getRegion()) 
		              && provinceNextValue.get(NAME_AR).toString().trim()
		                    .equals(customerAddRequest.getRegion().trim())) {
		    	if(provinceNextValue.get(NAME_AR).toString().trim()
		                    .equals(customerAddRequest.getRegion().trim())) {
		    		isArabic = true;
		    	}
		        JSONObject city = provinceNextValue.getJSONObject("cities");
		        Iterator<?> iterator = city.keys();
		        while (iterator.hasNext()) {
		            Object cityKey = iterator.next();
		            JSONObject cityValue = city.getJSONObject(cityKey.toString());
		            areaCorrect = setAreaCorrect(customerAddRequest, response, areaCorrect, cityValue,isArabic);
		        }
		    }
		    
		}
		return areaCorrect;
	}

	private boolean setAreaCorrect(CustomerAddrees customerAddRequest, CustomerAddreesResponse response,
			boolean areaCorrect, JSONObject cityValue , boolean isArabic) throws JSONException {
		if (null != customerAddRequest.getCity()
		        && cityValue.get(NAME_EN).toString().trim()
		                .equals(customerAddRequest.getCity().trim())
		        || cityValue.get(NAME_AR).toString().trim()
		                .equals(customerAddRequest.getCity().trim())) {
			if(cityValue.get(NAME_AR).toString().trim()
		                .equals(customerAddRequest.getCity().trim())) {
			}
		    areaCorrect = setAreaForCustomerAddress(customerAddRequest, areaCorrect,
					cityValue,response);
		} else {
			response.setStatus(false);
		    response.setStatusCode("212");
		    if(null != response && StringUtils.isNoneEmpty(response.getStatusMsg())) {
		    	
		    	response.setStatusMsg(response.getStatusMsg());	
		    }else {
		    	 if(null != customerAddRequest.getStoreId()
				    		&& Constants.arabicStores.contains(customerAddRequest.getStoreId())) {
				    	response.setStatusMsg(Constants.getAddressvalidation().getCityValidateMsgAr());	
				    }else {
				    	
				    	response.setStatusMsg(Constants.getAddressvalidation().getCityValidateMsgEn());
				    }
		    }
		   
		}
		return areaCorrect;
	}

	private boolean setAreaForCustomerAddress(CustomerAddrees customerAddRequest, boolean areaCorrect,
			JSONObject cityValue,CustomerAddreesResponse response) throws JSONException {
		if(Objects.nonNull(cityValue)) {
		JSONObject area = cityValue.getJSONObject("area");
		Iterator<?> areaIterator = area.keys();
		while (areaIterator.hasNext()) {
		    Object areaKey = areaIterator.next();
		    JSONObject areaValue = area.getJSONObject(areaKey.toString());
		    if (customerAddRequest.getArea() != null 
		            && ((areaValue.get(NAME_EN) != null && areaValue.get(NAME_EN).toString().trim().equals(customerAddRequest.getArea().trim()))
		            || (areaValue.get(NAME_AR) != null && areaValue.get(NAME_AR).toString().trim().equals(customerAddRequest.getArea().trim())))) {
		        areaCorrect = true;
		        break;
		    }
		}
		}
		if (!areaCorrect) {
		    response.setStatus(false);
		    response.setStatusCode("212");
		    if(null != customerAddRequest.getStoreId()
		    		&& Constants.arabicStores.contains(customerAddRequest.getStoreId())) {
		    	response.setStatusMsg(Constants.getAddressvalidation().getAreaValidateMsgAr());	
		    }else {
		    	
		    	response.setStatusMsg(Constants.getAddressvalidation().getAreaValidateMsgEn());
		    }

		}
		return areaCorrect;
		
	}

    private void savecustomerGridFlat(CustomerAddressEntity newCustAddressObject, Integer customerId,
            CustomerGridFlatRepository customerGridFlatRepository) {

        CustomerGridFlat customerGridFlat = customerGridFlatRepository.findByEntityId(customerId);

        if (null != customerGridFlat) {

            customerGridFlat.setBillingCountryId(newCustAddressObject.getCountryId());
            customerGridFlat.setBillingCity(newCustAddressObject.getCity());
            customerGridFlat.setBillingCompany(newCustAddressObject.getCompany());
            customerGridFlat.setBillingFirstname(newCustAddressObject.getFirstname());
            customerGridFlat.setBillingLastname(newCustAddressObject.getLastName());
            customerGridFlat.setBillingRegion(newCustAddressObject.getRegion());
            customerGridFlat.setBillingTelephone(newCustAddressObject.getTelephone());
            customerGridFlat.setBillingPostcode(newCustAddressObject.getPostcode());
            customerGridFlat.setBillingStreet(newCustAddressObject.getStreet());

            StringBuilder sb = new StringBuilder();

            if (StringUtils.isNotBlank(newCustAddressObject.getStreet())) {

                sb.append(" ").append(newCustAddressObject.getStreet());
            }

            sb.append(" ").append(newCustAddressObject.getCity());

            sb.append(" ").append(newCustAddressObject.getRegion());

            customerGridFlat.setShippingFull(sb.toString());
            customerGridFlat.setBillingFull(sb.toString());

            customerGridFlatRepository.save(customerGridFlat);
        }

    }


	private Map fetchRegionMapFromApi(String countryCode, String region) {
		// Replace with the actual API URL
		String apiUrl = addressMapperBaseUrl + "/api/address/provinceData";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

		try {
			// Check Redis for cached province list
			List<Map<String, String>> cachedProvinces =(List<Map<String, String>>) redisHelper.get("provinceList", countryCode, List.class);
			if (cachedProvinces != null && !cachedProvinces.isEmpty()) {
				LOGGER.info("Province list found in Redis for country: " + countryCode);
				return cachedProvinces.stream()
						.filter(province -> (region.equals(province.get("name")) || areArabicStringsEqual(region, province.get("name_ar"))))
						.findFirst()
						.orElse(new HashMap<>());
			}

			LOGGER.info("Address mapper base url: " + addressMapperBaseUrl);

			// Make the API call and parse the response
			ResponseEntity<ProvinceResponse> response = restTemplate.exchange(
					apiUrl+"?country="+countryCode,
					HttpMethod.GET,
					new HttpEntity<>(requestHeaders),
					ProvinceResponse.class
			);

			if (response.getStatusCode() == HttpStatus.OK) {
				ProvinceResponse provinceResponse = response.getBody();
				if (provinceResponse != null && provinceResponse.getResponse() != null) {
					// Save the province list to Redis
					redisHelper.put("provinceList", countryCode, provinceResponse.getResponse());
					LOGGER.info("Province list saved to Redis for country: " + countryCode);

					// Find the map where the region matches
					return provinceResponse.getResponse().stream()
							.filter(province -> region.equals(province.getName()))
							.findFirst()
							.map(province -> {
								Map<String, Object> provinceMap = new HashMap<>();
								provinceMap.put("id", province.getId());
								provinceMap.put("name", province.getName());
								provinceMap.put("name_ar", province.getName_ar());
								return provinceMap;
							})
							.orElse(new HashMap<>());
				}
			} else {
				LOGGER.error("Failed to fetch region map. HTTP Status: " + response.getStatusCode());
			}
		} catch (Exception e) {
			LOGGER.error("Error while calling external API: " + e.getMessage(), e);
		}


		return new HashMap();
	}

	private static boolean areArabicStringsEqual(String str1, String str2) {
		if (str1 == null || str2 == null) {
			return false;
		}

		// Create a Collator for Arabic locale
		Collator collator = Collator.getInstance(new Locale("ar"));
		collator.setStrength(Collator.PRIMARY); // Ignore case and diacritics

		// Compare the strings
		return collator.compare(str1, str2) == 0;
	}

	/**
	 * Checks if an address is KSA compliant.
	 * An address is compliant if:
	 * - shortAddress is present, OR
	 * - All three fields (streetAddress, unitNumber, and postalCode) are present
	 * 
	 * @param shortAddress The short address field
	 * @param streetAddress The street address field
	 * @param unitNumber The unit number field
	 * @param postalCode The postal code field
	 * @return true if address is KSA compliant, false otherwise
	 */
	private static boolean isAddressKsaCompliant(String shortAddress, String streetAddress, String unitNumber, String postalCode) {
		// Check if shortAddress is present
		if (StringUtils.isNotBlank(shortAddress)) {
			return true;
		}
		
		// Check if all three fields (streetAddress, unitNumber, and postalCode) are present
		return StringUtils.isNotBlank(streetAddress) 
		    && StringUtils.isNotBlank(unitNumber) 
		    && StringUtils.isNotBlank(postalCode);
	}
	}
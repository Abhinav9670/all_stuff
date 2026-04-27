package org.styli.services.customer.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.styli.services.customer.helper.NationalIdHelper;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;
import org.styli.services.customer.pojo.address.response.NationalId;
import org.styli.services.customer.pojo.epsilon.response.ShukranProfileData;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.UtilityCustomerConatant;
import org.styli.services.customer.pojo.PreferredPaymentMethod;
import org.styli.services.customer.utility.helper.AddressNudgeUtility;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Collections;

@Component
public class GetCustomer {

    @Autowired
    private NationalIdHelper nationalIdHelper;
    	
	 private static final Log LOGGER = LogFactory.getLog(GetCustomer.class);

    public CustomerUpdateProfileResponse get(Integer customerId, Client client,
                                             String jwtFlag , String customerEmail) {
        return this.get(customerId, client, jwtFlag, customerEmail, null, null);
    }

    public CustomerUpdateProfileResponse get(Integer customerId, Client client,
                                             String jwtFlag , String customerEmail, String phoneNumber) {
        return this.get(customerId, client, jwtFlag, customerEmail, phoneNumber, null);
    }

    public CustomerUpdateProfileResponse get(Integer customerId, Client client,
                                             String jwtFlag , String customerEmail, String phoneNumber,
                                             CustomerAddressEntityRepository customerAddressEntityRepository) {
        CustomerUpdateProfileResponse response = new CustomerUpdateProfileResponse();

        CustomerProfileResponse responseBody = new CustomerProfileResponse();
        CustomerEntity customerEntity = null;
        try {
        	if(null != customerId) {
        	    customerEntity = client.findByEntityId(customerId);
        	}else if(StringUtils.isNotBlank(customerEmail) ) {
        	    customerEntity = client.findByEmail(customerEmail);
        	} else if (StringUtils.isNotBlank(phoneNumber)) {
                customerEntity = client.findByPhoneNumber(phoneNumber);
            }
			if (null != customerEntity) {

				if ("1".equals(jwtFlag) && null == customerEntity.getJwtToken()) {

					LOGGER.info("JWT token is null:" + customerEntity.getEntityId());

					customerEntity .setJwtToken(0);
					client.saveAndFlushCustomerEntity(customerEntity);

					responseBody.setStatus(false);
					responseBody.setUserMessage("OLD JWT Token!");
					response.setResponse(responseBody);
					response.setStatus(true);
					response.setStatusCode("201");
					response.setStatusMsg(Constants.SUCCESS_MSG);

					return response;

				}
				if ("1".equals(jwtFlag) && (null == customerEntity.getJwtToken()
						|| (null != customerEntity.getJwtToken() && customerEntity.getJwtToken().equals(0)))) {

					LOGGER.info("JWT token not changed with zero:" + customerEntity.getEntityId());

					responseBody.setStatus(false);
					responseBody.setUserMessage("OLD JWT Token!");
					response.setResponse(responseBody);
					response.setStatus(true);
					response.setStatusCode("201");
					response.setStatusMsg(Constants.SUCCESS_MSG);

					return response;

				}
                Customer customer = getSavedCustomerInfo(customerEntity);
                customer.setIsInfluencer(Boolean.TRUE.equals(customerEntity.getIsInfluencer()));
				responseBody.setCustomer(customer);
				responseBody.setStatus(true);

				if (null != customerEntity.getDefaultShipping()) {

					CustomerAddressEntity customerAddressEntity = client
							.findAddressByEntityId(customerEntity.getDefaultShipping());

					if (null != customerAddressEntity) {

						responseBody.setDefaultAddress(
								setCustomerAddress(customerAddressEntity, customerEntity.getDefaultShipping(), client));
					}
				}

				// Check address compliance and set addressComplianceShowNudge flag
				AddressNudgeUtility.checkAddressComplianceAndSetNudge(customerEntity, client, responseBody, customerAddressEntityRepository);

				response.setResponse(responseBody);
				response.setStatus(true);
				response.setStatusCode("200");
				response.setStatusMsg("Fetched Successfully!!");

			} else {

                responseBody.setStatus(false);
                responseBody.setUserMessage("Invalid CustomerId");
                response.setResponse(responseBody);

                response.setStatus(true);
                response.setStatusCode("201");
                response.setStatusMsg(Constants.SUCCESS_MSG);
            }

        } catch (DataAccessException exception) {

            ErrorType error = new ErrorType();
            error.setErrorCode("204");
            error.setErrorMessage(exception.getMessage());
            response.setError(error);
            response.setStatus(false);
            response.setStatusCode("500");
            response.setStatusMsg("ERROR !!");
        }

        return response;
    }

    /**
     * @param savedCustomer
     * @return
     */
    public Customer getSavedCustomerInfo(CustomerEntity savedCustomer) {
    	
        Customer customer = new Customer();
        DateFormat dtFormarmater =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String lastName = savedCustomer.getLastName();
        if (lastName == null || lastName.trim().isEmpty() || lastName.equals(".")) {
            customer.setLastName(" ");
        } else {
            customer.setLastName(lastName);
        }
        customer.setCustomerId(savedCustomer.getEntityId());
        customer.setFirstName(savedCustomer.getFirstName());
        customer.setEmail(savedCustomer.getEmail());
        customer.setMobileNumber(savedCustomer.getPhoneNumber());
        customer.setGroupId(savedCustomer.getGroupId());
        customer.setCreatedIn(savedCustomer.getCreatedIn());
        customer.setCreatedAt(dtFormarmater.format(savedCustomer.getCreatedAt()));
        customer.setUpdatedAt(dtFormarmater.format(savedCustomer.getUpdatedAt()));
        customer.setGender(savedCustomer.getGender());
        customer.setDob(savedCustomer.getDob());
        customer.setAgeGroupId(savedCustomer.getAgeGroupId());
        setCustomerSignUpBy(customer, savedCustomer);
		setCustomerCurrentSignUpBy(customer, savedCustomer);
		customer.setLastSignedInTimestamp(savedCustomer.getLastSignedInTimestamp());
        customer.setPasswordAvailable(ObjectUtils.isNotEmpty(savedCustomer.getPasswordHash()));
        customer.setStoreId(savedCustomer.getStoreId());
        customer.setIsActive(savedCustomer.getIsActive());
        customer.setNeedAlternateEmail(StringUtils.isNotEmpty(customer.getEmail())
                && customer.getEmail().matches(Constants.WHATSAPP_EMAIL_REGEX));
        if(null != savedCustomer.getJwtToken()) {
        	customer.setJwtFlag(savedCustomer.getJwtToken());
        }

        if(null != savedCustomer.getLoginHistories()){
            customer.setLoginHistories(savedCustomer.getLoginHistories());
        }

        // Mobile number removed flag
        customer.setIsMobileNumberRemoved(Optional.ofNullable(savedCustomer.getIsMobileNumberRemoved()).orElse(false));
        customer.setIsMobileNumberUpdated(Optional.ofNullable(savedCustomer.getMobileNumberUpdated()).orElse(false));

        // Mobile number update message acknowledgment flag
        customer.setMobileNumberUpdateMessageAcknowledged(Optional.ofNullable(savedCustomer.getMobileNumberUpdateMessageAcknowledged()).orElse(false));

        if(null != savedCustomer.getWhatsappOptn()) {
        	if(savedCustomer.getWhatsappOptn() == 1) {
        		customer.setWhatsAppoptn(true);
        	}
        	else
        		customer.setWhatsAppoptn(false);
        } else {
        	customer.setWhatsAppoptn(false);
        }
        if(savedCustomer.getCustomerBlocked() != null){
            customer.setCustomerBlocked(savedCustomer.getCustomerBlocked());
        }
        customer.setIsReferral(savedCustomer.getReferralUser() != null ? true : false);
        if (Objects.nonNull(savedCustomer.getDob())) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
			customer.setDobString(simpleDateFormat.format(savedCustomer.getDob()));
		}
		if (Objects.isNull(savedCustomer.getIsPhoneNumberVerified())) {
			customer.setIsVerifyMobileNumber(true);
		} else {
			customer.setIsVerifyMobileNumber(savedCustomer.getIsPhoneNumberVerified());
		}
		
		customer.setIsMobileVerified(savedCustomer.getIsMobileVerified() != null && savedCustomer.getIsMobileVerified());
	    customer.setIsEmailVerified(savedCustomer.getIsEmailVerified() != null && savedCustomer.getIsEmailVerified());	
        
        // Check shukran link flag
        customer.setShukranLinkFlag(Optional.ofNullable(savedCustomer.getShukranLinkFlag()).orElse(false));

        // Shukran profile Id
        customer.setProfileId(Optional.ofNullable(savedCustomer.getProfileId()).orElse(""));
        // Shukran card number
        customer.setCardNumber(Optional.ofNullable(savedCustomer.getCardNumber()).orElse(""));
        // Shukran tier activity
        customer.setTierActivity(Optional.ofNullable(savedCustomer.getTierActivity()).orElse(""));
        // Shukran tier name
        customer.setTierName(Optional.ofNullable(savedCustomer.getTierName()).orElse(""));
        // Shukran profile data
        customer.setShukranProfileData(null!=savedCustomer.getShukranProfileData()?savedCustomer.getShukranProfileData():null);
       //preferred payment data
        copyPreferredPaymentMethod(savedCustomer, customer);
        return customer;
    }

	private void setCustomerCurrentSignUpBy(Customer customer, CustomerEntity savedCustomer) {
		if(savedCustomer.getSignedInNowUsing() == null || savedCustomer.getSignedInNowUsing() == 0) {
			customer.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_EMAIL);
		}else if(savedCustomer.getSignedInNowUsing() == 1) {
			customer.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_GOOGLE);
		}else if(savedCustomer.getSignedInNowUsing() == 2) {
			customer.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_APPLE);
		}else if(savedCustomer.getSignedInNowUsing() == 3) {
			customer.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_WHATSAPP);
		}
	}

	private void setCustomerSignUpBy(Customer customer, CustomerEntity savedCustomer) {
		if(savedCustomer.getSignedUpUsing() == null || savedCustomer.getSignedUpUsing() == 0) {
			customer.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_EMAIL);
		}else if(savedCustomer.getSignedUpUsing() == 1) {
			customer.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_GOOGLE);
		}else if(savedCustomer.getSignedUpUsing() == 2) {
			customer.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_APPLE);
		}else if(savedCustomer.getSignedUpUsing() == 3) {
			customer.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_WHATSAPP);
		}
	}

	/**
     * @param addressEntity
     * @param shiftingAddressId
     * @return CustomerAddrees
     */
    private CustomerAddrees setCustomerAddress(CustomerAddressEntity addressEntity, Integer shiftingAddressId,
            Client client) {
        CustomerAddrees address = new CustomerAddrees();

        address.setCustomerId(addressEntity.getParentId());
        address.setAddressId(addressEntity.getEntityId());
        address.setFirstName(addressEntity.getFirstname());
        address.setLastName(addressEntity.getLastName());
        address.setCountry(addressEntity.getCountryId());
        address.setCity(addressEntity.getCity());
        address.setRegion(addressEntity.getRegion());
        address.setRegionId(addressEntity.getRegionId());
        setBuildingOrStreet(addressEntity, address);

        CustomerAddressEntityVarchar area = null;
        CustomerAddressEntityVarchar landMark = null;

        setMobileNumber(addressEntity, client, LOGGER, address);

        Set<CustomerAddressEntityVarchar> addressVarList = addressEntity.getCustomerAddressEntityVarchar();

        if (null != client.getAttrMap()) {

            Integer areaAttributeId = client.getAttrMap().entrySet().stream()
                    .filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_AREA_ATTRIBUTE))
                    .map(Map.Entry::getKey).findFirst().orElse(0);

            Integer landmarkAttributeId = client.getAttrMap().entrySet().stream()
                    .filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_LANDMARK_ATTRIBUTE))
                    .map(Map.Entry::getKey).findFirst().orElse(0);

            if (null != addressVarList) {

                area = addressVarList.stream()
                        .filter(x -> null != x.getAttributeId() && areaAttributeId.equals(x.getAttributeId())).findAny()
                        .orElse(null);

                landMark = addressVarList.stream()
                        .filter(x -> null != x.getAttributeId() && landmarkAttributeId.equals(x.getAttributeId()))
                        .findAny().orElse(null);

            }

            if (null != addressVarList ) {

                area = addressVarList.stream()
                        .filter(x -> null != x.getAttributeId() && areaAttributeId.equals(x.getAttributeId())).findAny()
                        .orElse(null);

                landMark = addressVarList.stream()
                        .filter(x -> null != x.getAttributeId() && landmarkAttributeId.equals(x.getAttributeId()))
                        .findAny().orElse(null);

            }

        }

        setAreaLandmarkInAddress(addressEntity, shiftingAddressId, address, area, landMark);

        // Set new national address format fields
        address.setUnitNumber(addressEntity.getUnitNumber());
        address.setPostalCode(addressEntity.getPostalCode());
        address.setShortAddress(addressEntity.getShortAddress());


        if(null != addressEntity.getNationalIdNumber()) {
            NationalId nationalId = new NationalId();
            nationalId.setNumber(nationalIdHelper.decryptNationalIdDetails(addressEntity.getNationalIdNumber()));
            nationalId.setType(addressEntity.getNationalIdType());
            nationalId.setExpirationDate(addressEntity.getNationalIdExpirationDate());
            address.setNationalId(nationalId);
        }

        return address;
    }

	private void setBuildingOrStreet(CustomerAddressEntity addressEntity, CustomerAddrees address) {
		if (null != addressEntity.getStreet() && ArrayUtils.isNotEmpty(addressEntity.getStreet().split("\n"))) {

            address.setBuildingNumber(addressEntity.getStreet().split("\n")[0]);

            if (addressEntity.getStreet().split("\n").length > 1) {

                address.setStreetAddress(addressEntity.getStreet().split("\n")[1]);

            }

        }
	}

	private void setAreaLandmarkInAddress(CustomerAddressEntity addressEntity, Integer shiftingAddressId,
			CustomerAddrees address, CustomerAddressEntityVarchar area, CustomerAddressEntityVarchar landMark) {
		if (null != area) {

            address.setArea(area.getValue());

        }
        if (null != landMark) {

            address.setLandMark(landMark.getValue());

        }

        if (null != shiftingAddressId && shiftingAddressId.equals(addressEntity.getEntityId())) {

            address.setDefaultAddress(true);

        } else {

            address.setDefaultAddress(false);
        }
	}

	private void setMobileNumber(CustomerAddressEntity addressEntity, Client client, Log LOGGER,
			CustomerAddrees address) {
		if (StringUtils.isNotBlank(addressEntity.getTelephone())
                && !addressEntity.getTelephone().matches("[+]{1}[0-9]{3}\\s{1}.*")
                && addressEntity.getTelephone().length() > 4) {

            /** This change for those phone does not have space **/

            String countryCode = addressEntity.getTelephone().substring(0, 4);

            String telPhoneNum = addressEntity.getTelephone().substring(4);

            LOGGER.info("countryCode:" + countryCode);
            LOGGER.info("telPhoneNum:" + telPhoneNum);

            StringBuilder sb = new StringBuilder();

            LOGGER.info(sb.append(countryCode).append(" ").append(telPhoneNum));

            addressEntity.setTelephone(sb.toString());

            client.saveAndFlushAddressEntity(addressEntity);

            address.setMobileNumber(addressEntity.getTelephone());

        } else {

            address.setMobileNumber(addressEntity.getTelephone());
        }
	}

    public void setShukranLinkFlag(Integer customerId, Client client,boolean shukranLinkFlag,String profileId,String cardNumber) {
        CustomerEntity customerEntity = null;
        
        try {
            if(null != customerId) {
                customerEntity = client.findByEntityId(customerId);
            }
            if (null != customerEntity) {
                customerEntity.setShukranLinkFlag(shukranLinkFlag);
                if(null!=profileId && !profileId.equalsIgnoreCase(""))
                    customerEntity.setProfileId(profileId);
                if(null!=cardNumber && !cardNumber.equalsIgnoreCase(""))
                    customerEntity.setCardNumber(cardNumber);
                customerEntity.setUpdatedAt(new Date());
                client.saveAndFlushCustomerEntity(customerEntity);
            }
        } catch (DataAccessException exception) {
            LOGGER.error("Error in updating shukranLinkFlag for customer: "+customerId,exception);
        }
    }

    public void setShukranTierActivity(Integer customerId, Client client, String tierActivity, String tierName, ShukranProfileData shukranProfileData) {
        CustomerEntity customerEntity = null;
        try {
            if(null != customerId) {
                customerEntity = client.findByEntityId(customerId);
            }
            if (null != customerEntity) {
                if(null!=tierActivity)
                    customerEntity.setTierActivity(tierActivity);
                if(null!=tierName)
                    customerEntity.setTierName(tierName);
                if(null!=shukranProfileData)
                    customerEntity.setShukranProfileData(shukranProfileData);
                customerEntity.setUpdatedAt(new Date());
                client.saveAndFlushCustomerEntity(customerEntity);
            }
        } catch (DataAccessException exception) {
            LOGGER.error("Error in saving shukran tier activity for customer: "+customerId,exception);
        }
    }

    public void deleteShukranData(Integer customerId, Client client) {
        CustomerEntity customerEntity = null;
        try {
            if (null != customerId) {
                customerEntity = client.findByEntityId(customerId);
            }
            if (null != customerEntity) {
                customerEntity.setShukranLinkFlag(false);
                customerEntity.setProfileId(null);
                customerEntity.setTierActivity(null);
                customerEntity.setTierName(null);
                customerEntity.setCardNumber(null);
                customerEntity.setUpdatedAt(new Date());
                client.saveAndFlushCustomerEntity(customerEntity);
            }
        } catch (DataAccessException exception) {
            LOGGER.error("Error in updating(delete) shukran data for customer: "+customerId,exception);
        }
    }

    /**
     * Removes the phone number from customer profile and delinks the Shukran account.
     */
    public void removePhoneNumberFromShukranAccount(Integer customerId, Client client) {
        CustomerEntity customerEntity = null;
        try {
            if (null != customerId) {
                customerEntity = client.findByEntityId(customerId);
            }
            if (null != customerEntity) {
                // Only clear the phone number, keep all Shukran data intact
                customerEntity.setPhoneNumber(null);
                customerEntity.setMobileNumber(null);
                customerEntity.setIsMobileNumberRemoved(true);
                customerEntity.setIsMobileVerified(false);
                customerEntity.setShukranLinkFlag(false);
                customerEntity.setMobileNumberUpdated(false);
                customerEntity.setUpdatedAt(new Date());
                client.saveAndFlushMongoCustomerDocument(customerEntity);
                LOGGER.info("Successfully removed phone number from Shukran account for customer: "+customerId+" and set isMobileNumberRemoved flag");
            }
        } catch (DataAccessException exception) {
            LOGGER.error("Error in removing phone number from Shukran account for customer",exception);
        }
    }
    
    private void copyPreferredPaymentMethod(CustomerEntity savedCustomer, Customer customer) {
        List<PreferredPaymentMethod> methods = savedCustomer.getPreferredPaymentMethod();

        if (methods != null && !methods.isEmpty()) {
            customer.setPreferredPaymentMethod(methods);
        } else {
            customer.setPreferredPaymentMethod(Collections.emptyList());
        }
    }

}

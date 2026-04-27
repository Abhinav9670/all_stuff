package org.styli.services.customer.service.impl;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.OtpService;
import org.styli.services.customer.service.impl.SaveCustomer;
import org.styli.services.customer.service.impl.OtpServiceImpl;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;



@Component
public class UpdateUser implements ServiceConfigs.ServiceConfigsListener {
	Map<Integer, String> attributeMap;
	private boolean disablePhoneNumberProfileAPI = false;

	@Autowired
	EasCustomerService easCustomerService;

	@Autowired
	AsyncService asyncService;

	@Autowired
	OtpService otpService;

	@Autowired
	SaveCustomer saveCustomer;
    
    @Autowired
    OtpServiceImpl otpServiceImpl;
    
    public static final String BAD_REQUEST = "Bad Request!!";
    
    private static final Log LOGGER = LogFactory.getLog(UpdateUser.class);

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
		Object configValue = newConfigs.get("disablePhoneNumberProfileAPI");
		disablePhoneNumberProfileAPI = Boolean.parseBoolean(String.valueOf(configValue));
	}

	public CustomerUpdateProfileResponse update(CustomerUpdateProfileRequest customerUpdate,Client client, String deviceId) {
		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();

		//For Phone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("update: : Store IDs enabled for OTP feature: " + storeIds);
		//For Email - Update User specific flag
		Boolean isEmailOtpEnabledUpdateUser = ServiceConfigs.getEmailOtpEnableUpdateUser(customerUpdate.getStoreId());

		List<Integer> emailStoreIds = ServiceConfigs.getStoreIdsForEmailOtpFeature();
		LOGGER.info("update/profile: : Email Store IDs enabled for Email OTP feature (Update User): " + emailStoreIds);

		boolean emailOtpEnabledV1 = ServiceConfigs.getIsEmailOtpEnabledV1(customerUpdate.getStoreId());

		Integer storeIdFromPayload = customerUpdate.getStoreId();
		//For phone
		boolean isStoreValid = storeIds.contains(storeIdFromPayload);
		//For Email
		boolean isEmailStoreValid = emailStoreIds.stream().anyMatch(id -> id.equals(storeIdFromPayload));

		CustomerUpdateProfileResponse response = new CustomerUpdateProfileResponse();

		CustomerProfileResponse responseBody = new CustomerProfileResponse();

		String minAppVersion = ServiceConfigs.getMinAppVersionReqdForOtpFeature();

		boolean isMobileNotVerified = Boolean.FALSE.equals(customerUpdate.getIsMobileVerified());
		boolean isVersionValid = saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(), minAppVersion);
		boolean isSourceMsite = Constants.SOURCE_MSITE.equalsIgnoreCase(customerUpdate.getSource());
		boolean isEmailNotVerified = Boolean.FALSE.equals(customerUpdate.getIsEmailVerified());

		try {

			if (null == customerUpdate.getCustomerId()) {

				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg(BAD_REQUEST);

				return response;

			}
			if (StringUtils.isNotBlank(customerUpdate.getMobileNumber())
					&& !customerUpdate.getIsReferral()) {

				String str=  Constants.MOBILE_NUMBER_VALIDATION_REGEX;


				if (!Pattern.compile(str).matcher(customerUpdate.getMobileNumber()).matches()) {

					response.setStatus(false);
					response.setStatusCode("208");
					response.setStatusMsg("invalid mobile number format!");
					return response;
				}

			}

			if(customerUpdate.getOmsRequest()) {
				CustomerEntity customerEntity = client.findByEntityId(customerUpdate.getCustomerId());
				if(ObjectUtils.isNotEmpty(customerEntity) && ObjectUtils.isNotEmpty(customerEntity.getEntityId())) {
//                    Check for mobile number
					if(StringUtils.isNotBlank(customerUpdate.getMobileNumber())) {
						CustomerEntity customerExistsByMobileNumber = client.findByPhoneNumber(customerUpdate.getMobileNumber());
						if(ObjectUtils.isNotEmpty(customerExistsByMobileNumber)
								&& ObjectUtils.isNotEmpty(customerExistsByMobileNumber.getEntityId())
								&& !customerExistsByMobileNumber.getEntityId().equals(customerUpdate.getCustomerId()) ) {
							response.setStatus(false);
							response.setStatusCode("201");
							response.setStatusMsg(BAD_REQUEST);

							responseBody.setUserMessage("Mobile Number already in use!");
							responseBody.setStatus(false);
							response.setResponse(responseBody);
							return response;
						}
					}

//                    Check for email id
					if(StringUtils.isNotBlank(customerUpdate.getEmail())) {
						CustomerEntity customerExistsByEmail = client.findByEmail(customerEntity.getEmail());
						CustomerEntity newCustomerByEmail = client.findByEmail(customerUpdate.getEmail());
						if(ObjectUtils.isNotEmpty(customerExistsByEmail)
								&& ObjectUtils.isNotEmpty(newCustomerByEmail)
								&& !customerExistsByEmail.getEmail().equalsIgnoreCase(newCustomerByEmail.getEmail()) ) {
							response.setStatus(false);
							response.setStatusCode("201");
							response.setStatusMsg(BAD_REQUEST);

							responseBody.setUserMessage("Email already in use!");
							responseBody.setStatus(false);
							response.setResponse(responseBody);
							return response;

						}else if(null != customerExistsByEmail
								&& null != customerUpdate.getEmail()
								&& !(customerUpdate.getEmail().equalsIgnoreCase(customerExistsByEmail.getEmail()))) {

							Customer savedCustomer = new Customer();
							savedCustomer.setEmail(customerEntity.getEmail());
							savedCustomer.setUpdatedEmail(customerUpdate.getEmail());
							savedCustomer.setUpdateEmail(true);
							savedCustomer.setCustomerId(customerExistsByEmail.getEntityId());
							asyncService.asyncSalesOrdersUpdateCustId(savedCustomer);
						}
					}

					if(Objects.nonNull(customerUpdate.getDob())) {
						customerEntity.setDob(customerUpdate.getDob());
                    }
                    customerEntity.setEmail(customerUpdate.getEmail());
                    setAndSaveCustomerEntity(customerUpdate, client, customerEntity);
                    customerEntity.setCustomerBlocked(customerUpdate.getCustomerBlocked());
                    
                    
					try {
						if (storeIdFromPayload != null && isStoreValid
								&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())) {

							boolean isMobileNumberMatching = customerUpdate.getMobileNumber().replaceAll("\\s+|-", "")
									.equals(customerEntity.getPhoneNumber().replaceAll("\\s+|-", ""));

							if (customerUpdate.getMobileNumber() != null
									&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())
									&& (saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(),
											minAppVersion)
											|| customerUpdate.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE))) {
								String mobileNumber = customerUpdate.getMobileNumber();
								LOGGER.info("update/profile: Verifying mobile number: " + mobileNumber);

								Boolean mobileVerificationStatus = otpService
										.getVerificationStatusFromRedis(mobileNumber);

						if (Boolean.TRUE.equals(mobileVerificationStatus)) {
							customerEntity.setIsMobileVerified(true);
							customerUpdate.setIsMobileVerified(true);
							customerEntity.setPhoneNumber(mobileNumber);

							// Reset the mobile number removed flag when a new phone number is added
							customerEntity.setIsMobileNumberRemoved(false);
							LOGGER.info(
									"update/profile: Updating mobile number in DB as redis verification status is true for "
											+ customerUpdate.getMobileNumber());
							otpServiceImpl.validateAndMarkMobileVerifiedInAddressDB(
									customerUpdate.getCustomerId(), mobileNumber);
						} else {
									customerUpdate.setIsMobileVerified(false);
									LOGGER.info("update/profile: Setting mobile  Verified as false for "
											+ customerUpdate.getMobileNumber());
								}

								LOGGER.info("update/profile: Mobile verification status for " + mobileNumber + "is : "
										+ mobileVerificationStatus);
							} else {
								customerUpdate.setIsMobileVerified(false);
							}

							LOGGER.info("update/profile: Checking if sign-up OTP is enabled: {}"
									+ customerUpdate.getIsSignUpOtpEnabled());
							LOGGER.info("update/profile: Checking if mobile is not verified: {}"
									+ customerUpdate.getIsMobileVerified());
							LOGGER.info("update/profile: Checking if version is greater or equal to min app version: {}"
									+ saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(),
											minAppVersion));
							LOGGER.info("update/profile: Checking if source is MSITE: {}"
									+ customerUpdate.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE));

							if ("true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled()) && isMobileNotVerified
									&& !isMobileNumberMatching && (isVersionValid || isSourceMsite)) {
								LOGGER.info("update/profile: All conditions met. OTP validation failed for mobile.");
								response.setStatus(false);
								response.setStatusCode("213");
								response.setStatusMsg("OTP validation failed for mobile");
								return response;
							}

						} else {
							if (customerUpdate.getMobileNumber() != null && !customerUpdate.getMobileNumber().isEmpty()) {
								customerEntity.setPhoneNumber(customerUpdate.getMobileNumber());

								// Set mobile number update flags for non-OTP updates
								customerEntity.setIsMobileNumberRemoved(false);
							}
						}

						if (storeIdFromPayload != null && (isEmailStoreValid || emailOtpEnabledV1 || Boolean.TRUE.equals(isEmailOtpEnabledUpdateUser))
								&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())) {
							if (customerUpdate.getEmail() != null
									&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())
									&& (saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(),
									minAppVersion)
									|| customerUpdate.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE))) {
								LOGGER.info("update/profile: Verifying email number: {}" + customerUpdate.getEmail());
								Boolean emailVerificationStatus = otpService
										.getVerificationStatusFromRedis(customerUpdate.getEmail());
								if (Boolean.TRUE.equals(emailVerificationStatus)) {
									customerEntity.setIsEmailVerified(true);
									customerUpdate.setIsEmailVerified(true);
									customerEntity.setEmail(customerUpdate.getEmail());
									LOGGER.info("update/profile: Email verification status for "
											+ customerUpdate.getEmail() + "is : " + emailVerificationStatus);
								} else {
									customerUpdate.setIsEmailVerified(false);
									LOGGER.info("update/profile: Setting email  Verified as false for "
											+ customerUpdate.getMobileNumber());
								}
							} else {
								customerUpdate.setIsEmailVerified(false);
							}

							if (customerUpdate.getEmail() != null && customerEntity.getEmail() != null
									&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())
									&& isEmailNotVerified && (isVersionValid || isSourceMsite)) {
								LOGGER.info("update/profile: All conditions met. OTP validation failed for email.");
								response.setStatus(false);
								response.setStatusCode("214");
								response.setStatusMsg("OTP validation failed for email");
								return response;
							}
						} else {
						    if (customerUpdate.getEmail() != null && !customerUpdate.getEmail().isEmpty()) {
						        customerEntity.setEmail(customerUpdate.getEmail());
						    }
						}

					} catch (Exception e) {
						response.setStatus(false);
						response.setStatusCode("204");
						response.setStatusMsg("ERROR while updating profile!!");
					}

					CustomerEntity updatedEntity = client.saveAndFlushCustomerEntity(customerEntity);
					setResponseBody(customerUpdate, response, responseBody, updatedEntity, deviceId);

					response.setStatus(true);
					response.setStatusCode("200");
					response.setStatusMsg("Successfully Updated!!");
					return response;

				}

			}
            
            CustomerEntity customerEntity = client.findByEntityId(customerUpdate.getCustomerId());


			if (StringUtils.isBlank(customerUpdate.getMobileNumber())
					&& !customerUpdate.getIsReferral()) {

				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg(BAD_REQUEST);

				return response;
			}
			if (StringUtils.isNotBlank(customerUpdate.getMobileNumber())
					&& !customerUpdate.getIsReferral()) {

				String str=  Constants.MOBILE_NUMBER_VALIDATION_REGEX;


				if (!Pattern.compile(str).matcher(customerUpdate.getMobileNumber()).matches()) {

					response.setStatus(false);
					response.setStatusCode("208");
					response.setStatusMsg("invalid mobile number format!");
					return response;
				}

				// Handle phone number update when isMobileNumberChanged is true
				if (!disablePhoneNumberProfileAPI && (Boolean.TRUE.equals(customerUpdate.getIsMobileNumberChanged()))) {
					LOGGER.info("update/profile: Phone number change detected for customer: " + customerUpdate.getCustomerId());
					handlePhoneNumberUpdate(customerUpdate, customerEntity, client);
				}

			}
//            if(StringUtils.isNotBlank(customerUpdate.getFirstName())
//            		&& !customerUpdate.getIsReferral()) {
//
//            	String str=  Constants.CUSTOMER_NAME_VALIDATION_REGEX;
//
//
//                if (!Pattern.compile(str).matcher(customerUpdate.getFirstName().trim()).matches()) {
//
//                	 response.setStatus(false);
//                     response.setStatusCode("208");
//                     response.setStatusMsg("invalid Customer first Name");
//                     return response;
//                   }
//
//            }if(StringUtils.isNotBlank(customerUpdate.getLastName())
//            		&& !customerUpdate.getIsReferral()) {
//
//            	String str=  Constants.CUSTOMER_NAME_VALIDATION_REGEX;
//
//
//                if (!Pattern.compile(str).matcher(customerUpdate.getLastName().trim()).matches()) {
//
//                	 response.setStatus(false);
//                     response.setStatusCode("208");
//                     response.setStatusMsg("invalid Customer last Name!");
//                     return response;
//                   }
//
//            }

			if(!customerUpdate.getIsReferral()) {

				if (null != customerEntity && null != customerEntity.getEntityId() && null != customerUpdate.getMobileNumber()) {

					CustomerEntity customerExists = client.findByPhoneNumber(customerUpdate.getMobileNumber());

					if (null != customerExists && null != customerExists.getEntityId()
							&& !customerExists.getEntityId().equals(customerUpdate.getCustomerId())) {

						// PHONE NUMBER EXISTS WITH ANOTHER CUSTOMER
						// Check if this is a phone number change request with OTP verification
						if (!disablePhoneNumberProfileAPI && (Boolean.TRUE.equals(customerUpdate.getIsMobileNumberChanged()))) {
							
							LOGGER.info("Phone number transfer request detected for customer: " + customerUpdate.getCustomerId() + 
									   " to number: " + customerUpdate.getMobileNumber() + 
									   " (currently owned by customer: " + customerExists.getEntityId() + ")");
							
							// Check OTP verification status
							Boolean mobileVerificationStatus = otpService.getVerificationStatusFromRedis(customerUpdate.getMobileNumber());
							
							if (Boolean.TRUE.equals(mobileVerificationStatus)) {
								// OTP VERIFIED - Proceed with phone number transfer
								LOGGER.info("Phone number transfer: OTP verified for " + customerUpdate.getMobileNumber() + 
										   ". Proceeding with transfer from customer " + customerExists.getEntityId() + 
										   " to customer " + customerUpdate.getCustomerId());
								
								try {
									handlePhoneNumberUpdate(customerUpdate, customerEntity, client);
									LOGGER.info("Phone number transfer completed successfully for customer: " + customerUpdate.getCustomerId());
								} catch (Exception e) {
									LOGGER.error("Error during phone number transfer for customer: " + customerUpdate.getCustomerId(), e);
									response.setStatus(false);
									response.setStatusCode("500");
									response.setStatusMsg("Error during phone number transfer");
									responseBody.setUserMessage("Failed to transfer phone number. Please try again.");
									responseBody.setStatus(false);
									response.setResponse(responseBody);
									return response;
								}
							} else {
								// OTP NOT VERIFIED - Return error
								LOGGER.info("Phone number transfer: OTP not verified for " + customerUpdate.getMobileNumber() + 
										   ". Requesting OTP verification.");
								
								response.setStatus(false);
								response.setStatusCode("213");
								response.setStatusMsg("OTP validation required for phone number transfer");
								responseBody.setUserMessage("Please verify the new phone number via OTP before transfer");
								responseBody.setStatus(false);
								response.setResponse(responseBody);
								return response;
							}
						} else {
							// NOT A PHONE NUMBER CHANGE REQUEST - Block duplicate
							LOGGER.info("Duplicate phone number detected for customer: " + customerUpdate.getCustomerId() + 
									   " attempting to use number: " + customerUpdate.getMobileNumber() + 
									   " (owned by customer: " + customerExists.getEntityId() + ")");
							
							response.setStatus(false);
							response.setStatusCode("201");
							response.setStatusMsg(BAD_REQUEST);
							responseBody.setUserMessage("Mobile Number Already Used by Another Customer");
							responseBody.setStatus(false);
							response.setResponse(responseBody);
							return response;
						}

					}

				} else {

					response.setStatus(false);
					response.setStatusCode("201");
					response.setStatusMsg("Invalid Customer ID!!");

					return response;
				}
			}


        	if(customerUpdate.getIsReferral() && ObjectUtils.isNotEmpty(customerEntity)
        			&& null != customerEntity.getReferralUser()) {
        		if(1 == customerEntity.getReferralUser()) {
        			response.setStatus(true);
                    response.setStatusCode("200");
                    response.setStatusMsg("Already Updated Referral for this user!!");
                    
                    return response;
        		} else {
        			customerEntity.setReferralUser(1);
                    customerEntity.setUpdatedAt(new Date());
        		}
        	}else if(customerUpdate.getIsReferral() && ObjectUtils.isNotEmpty(customerEntity)) {
        		customerEntity.setReferralUser(1);
                customerEntity.setUpdatedAt(new Date());
        	}
        	
        	try {
				//For Mobile
				if (storeIdFromPayload != null && isStoreValid
						&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())) {


					boolean isMobileNumberMatching = false;
					if(StringUtils.isNotEmpty(customerEntity.getPhoneNumber()) && StringUtils.isNotBlank(customerEntity.getPhoneNumber())) {
						isMobileNumberMatching= customerUpdate.getMobileNumber().replaceAll("\\s+|-", "")
								.equals(customerEntity.getPhoneNumber().replaceAll("\\s+|-", ""));
					}

					if (customerUpdate.getMobileNumber() != null
							&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())
							&& (saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(), minAppVersion)
									|| customerUpdate.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE))) {
						String mobileNumber = customerUpdate.getMobileNumber();
						LOGGER.info("update/profile: Verifying mobile number: " + mobileNumber);

						Boolean mobileVerificationStatus = otpService.getVerificationStatusFromRedis(mobileNumber);

						if (Boolean.TRUE.equals(mobileVerificationStatus)) {
							customerEntity.setIsMobileVerified(true);
							customerUpdate.setIsMobileVerified(true);
							customerEntity.setPhoneNumber(mobileNumber);
							// Reset the mobile number removed flag when a new phone number is added
							customerEntity.setIsMobileNumberRemoved(false);
							LOGGER.info(
									"update/profile: Updating mobile number in DB as redis verification status is true for "
											+ customerUpdate.getMobileNumber());
							otpServiceImpl.validateAndMarkMobileVerifiedInAddressDB(customerUpdate.getCustomerId(),
									mobileNumber);
						} else {
							customerUpdate.setIsMobileVerified(false);
							LOGGER.info("update/profile: Setting mobile  Verified as false for "
									+ customerUpdate.getMobileNumber());
						}

						LOGGER.info("update/profile: Mobile verification status for " + mobileNumber + "is : "
								+ mobileVerificationStatus);
					} else {
						customerUpdate.setIsMobileVerified(false);
					}

					LOGGER.info("update/profile: Checking if sign-up OTP is enabled: {}"
							+ customerUpdate.getIsSignUpOtpEnabled());
					LOGGER.info("update/profile: Checking if mobile is not verified: {}"
							+ customerUpdate.getIsMobileVerified());
					LOGGER.info("update/profile: Checking if version is greater or equal to min app version: {}"
							+ saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(), minAppVersion));

					if ("true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled()) && isMobileNotVerified
							&& !isMobileNumberMatching && (isVersionValid || isSourceMsite)) {

						LOGGER.info("update/profile: All conditions met. OTP validation failed for mobile.");
						response.setStatus(false);
						response.setStatusCode("213");
						response.setStatusMsg("OTP validation failed for mobile");
						return response;
					}
				} else {
				    if (customerUpdate.getMobileNumber() != null && !customerUpdate.getMobileNumber().isEmpty()) {
				    	customerEntity.setPhoneNumber(customerUpdate.getMobileNumber());
				    	// Set mobile number update flags for non-OTP updates
				    	customerEntity.setIsMobileNumberRemoved(false);
				    }
				}
				//For Email
				if (storeIdFromPayload != null && (isEmailStoreValid || emailOtpEnabledV1 || isEmailOtpEnabledUpdateUser)
						&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())) {
					if (customerUpdate.getEmail() != null
							&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled())
							&& (saveCustomer.isVersionGreaterOrEqual(customerUpdate.getClientVersion(), minAppVersion)
							|| customerUpdate.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE))) {
						LOGGER.info("update/profile: Verifying email number: {}" + customerUpdate.getEmail());
						Boolean emailVerificationStatus = otpService
								.getVerificationStatusFromRedis(customerUpdate.getEmail());
						if (Boolean.TRUE.equals(emailVerificationStatus)) {
							customerEntity.setIsEmailVerified(true);
							customerUpdate.setIsEmailVerified(true);
							customerEntity.setEmail(customerUpdate.getEmail());
							LOGGER.info("update/profile: Email verification status for " + customerUpdate.getEmail()
									+ "is : " + emailVerificationStatus);
						} else {
							customerUpdate.setIsEmailVerified(false);
							LOGGER.info("update/profile: Setting email  Verified as false for "
									+ customerUpdate.getMobileNumber());
						}
					} else {
						customerUpdate.setIsEmailVerified(false);
					}
					if (customerUpdate.getEmail() != null && customerEntity.getEmail() != null
							&& "true".equalsIgnoreCase(customerUpdate.getIsSignUpOtpEnabled()) && isEmailNotVerified
							&& (isVersionValid || isSourceMsite)) {
						LOGGER.info("update/profile: All conditions met. OTP validation failed for email.");
						response.setStatus(false);
						response.setStatusCode("214");
						response.setStatusMsg("OTP validation failed for email");
						return response;
					}
				} else {
					if (customerUpdate.getEmail() != null && !customerUpdate.getEmail().isEmpty()) {
						customerEntity.setEmail(customerUpdate.getEmail());
					}
				}

			} catch (Exception e) {
				response.setStatus(false);
				response.setStatusCode("204");
				response.setStatusMsg("ERROR while updating profile!!");
			}

			setAndSaveCustomerEntity(customerUpdate, client, customerEntity);
			CustomerEntity updatedEntity = client.saveAndFlushCustomerEntity(customerEntity);

			setResponseBody(customerUpdate, response, responseBody, updatedEntity,deviceId);

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Successfully Updated!!");

		} catch (DataAccessException exception) {

			ErrorType error = new ErrorType();

			error.setErrorCode("400");
			error.setErrorMessage(exception.getMessage());

			response.setStatus(false);
			response.setStatusCode("204");
			response.setStatusMsg("ERROR!!");
			response.setError(error);
		}
		response.setIsSignUpOtpEnabled(("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) ? "true" : "false");
		return response;

	}

	private void setResponseBody(CustomerUpdateProfileRequest customerUpdate, CustomerUpdateProfileResponse response,
								 CustomerProfileResponse responseBody, CustomerEntity updatedEntity,String deviceId) {
		try {

			responseBody.setCustomer(setSavedCustomerInfo(updatedEntity, customerUpdate.getMobileNumber(),
					customerUpdate.getAgeGroupId()));
			responseBody.getCustomer().setMobileNumber(updatedEntity.getPhoneNumber());
			responseBody.getCustomer().setCustomerBlocked(updatedEntity.getCustomerBlocked() != null ? updatedEntity.getCustomerBlocked()
					: customerUpdate.getCustomerBlocked());
			responseBody.getCustomer().setIsReferral(customerUpdate.getIsReferral());
			if (null!=updatedEntity.getIsMobileVerified())
				responseBody.getCustomer().setIsMobileVerified(updatedEntity.getIsMobileVerified());
			if (null!=updatedEntity.getIsEmailVerified())
				responseBody.getCustomer().setIsEmailVerified(updatedEntity.getIsEmailVerified());
			if (null!=updatedEntity.getIsMobileNumberRemoved())
				responseBody.getCustomer().setIsMobileNumberRemoved(updatedEntity.getIsMobileNumberRemoved());
			responseBody.setStatus(true);
			responseBody.setUserMessage("Updated Successfully !!");
			// Call to eas service
			if (Objects.isNull(Constants.StoreConfigResponse.getDisabledServices())
					|| !Constants.StoreConfigResponse.getDisabledServices().isEarnDisabled()) {
				easCustomerService.profileUpdatedCompleted(updatedEntity,deviceId);
			}
			response.setResponse(responseBody);

		} catch (Exception e) {
			LOGGER.error("Error in setResponseBody: ", e);
			throw new RuntimeException("Error while processing the update profile request", e);
		}
	}

	private void setAndSaveCustomerEntity(CustomerUpdateProfileRequest customerUpdate, Client client,
										  CustomerEntity customerEntity) {
		customerEntity.setFirstName(customerUpdate.getFirstName());
		customerEntity.setLastName(customerUpdate.getLastName());
		if (null != customerUpdate.getGender()) {

			customerEntity.setGender(customerUpdate.getGender());
		}
		customerEntity.setUpdatedAt(new Date());

		if (null != customerUpdate.getAgeGroupId()) {
			customerEntity.setAgeGroupId(customerUpdate.getAgeGroupId());
		}

		if (Objects.nonNull(customerUpdate.getDob())) {
			customerEntity.setDob(customerUpdate.getDob());
		}

		customerEntity.setWhatsappOptn(customerUpdate.getWhatsAppoptn() ? 1 : 0);

		CustomerEntity updatedEntity1 = client.saveAndFlushCustomerEntity(customerEntity);
		LOGGER.info("update/profile: setAndSaveCustomerEntity : CustomerEntity successfully updated: {}" +updatedEntity1);
	}

	/**
	 * @param savedCustomer
	 * @param phone
	 * @return
	 */
	private Customer setSavedCustomerInfo(CustomerEntity savedCustomer, String phone, Integer ageFGroup) {
		Customer customer = new Customer();
		customer.setCustomerId(savedCustomer.getEntityId());
		customer.setFirstName(savedCustomer.getFirstName());
		customer.setLastName(savedCustomer.getLastName());
		customer.setEmail(savedCustomer.getEmail());
		customer.setMobileNumber(savedCustomer.getPhoneNumber());
		customer.setGroupId(savedCustomer.getGroupId());
		customer.setCreatedIn(savedCustomer.getCreatedIn());
		customer.setCreatedAt(savedCustomer.getCreatedAt().toString());
		customer.setUpdatedAt(savedCustomer.getUpdatedAt().toString());
		customer.setGender(savedCustomer.getGender());
		customer.setDob(savedCustomer.getDob());
		customer.setAgeGroupId(ageFGroup);
		if (Objects.nonNull(savedCustomer.getDob())) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
			customer.setDobString(simpleDateFormat.format(savedCustomer.getDob()));
		}
		// set customer store id
		customer.setStoreId(savedCustomer.getStoreId());
		// isMobileVerified
		customer.setIsMobileVerified(Optional.ofNullable(savedCustomer.getIsMobileVerified()).orElse(false));
		// isEmailVerified
		customer.setIsEmailVerified(Optional.ofNullable(savedCustomer.getIsEmailVerified()).orElse(false));
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
		// Mobile number removed flag
		customer.setIsMobileNumberRemoved(Optional.ofNullable(savedCustomer.getIsMobileNumberRemoved()).orElse(false));
		LOGGER.info("update/profile: setSavedCustomerInfo : Customer info set: {}" +customer);

		return customer;
	}

	/**
	 * Disables the ability to update the phone number in the customer profile.
	 *
	 * This method checks if the provided customer information request contains a valid mobile number
	 * and verifies if the customer associated with the mobile number has their phone number verified
	 * and is linked to a Shukran account. If all conditions are met, the method returns true,
	 * indicating that the phone number profile update should be disabled.
	 */
	public CustomerUpdateProfileRequest disablePhoneNumberProfileUpdate(CustomerUpdateProfileRequest customerInfoRequest, Client client) {
		if (customerInfoRequest == null || StringUtils.isBlank(customerInfoRequest.getMobileNumber())) {
			return customerInfoRequest;
		}

		CustomerEntity customerExistsById = client.findByEntityId(customerInfoRequest.getCustomerId());
		if (customerExistsById != null
				&& Boolean.TRUE.equals(customerExistsById.getIsMobileVerified())
				&& Boolean.TRUE.equals(customerExistsById.getShukranLinkFlag())
				&& !customerInfoRequest.getMobileNumber().equalsIgnoreCase(customerExistsById.getPhoneNumber())) {
			customerInfoRequest.setMobileNumber(customerExistsById.getPhoneNumber());//re setting to old mobile number
			return customerInfoRequest;
		}

		return customerInfoRequest;
	}

	/**
	 * Handles phone number update when a customer changes their phone number.
	 * Logic:
	 * 1. First check if the phone number is already associated with another account
	 * 2. If YES - do transfer operations (remove from other account, assign to current customer)
	 * 3. If NO - just update the phone number for the current customer
	 * 4. Update address database with new phone number
	 */
	@Transactional
	public void handlePhoneNumberUpdate(CustomerUpdateProfileRequest customerUpdate, CustomerEntity currentCustomer, Client client) {
		try {
			String newPhoneNumber = customerUpdate.getMobileNumber();
			LOGGER.info("handlePhoneNumberUpdate: Processing phone number change for customer: " + customerUpdate.getCustomerId() + " to new number: " + newPhoneNumber);

			// CHECK: Compare phone number from payload with existing phone number in DB
			String currentPhoneNumber = currentCustomer.getPhoneNumber();
			if (newPhoneNumber != null && currentPhoneNumber != null) {
				// Normalize phone numbers by removing spaces and hyphens for comparison
				String normalizedNewPhone = newPhoneNumber.replaceAll("\\s+|-", "");
				String normalizedCurrentPhone = currentPhoneNumber.replaceAll("\\s+|-", "");
				
				if (normalizedNewPhone.equalsIgnoreCase(normalizedCurrentPhone)) {
					LOGGER.info("handlePhoneNumberUpdate: Phone number in payload (" + newPhoneNumber + ") is same as current phone number in DB for customer: " + customerUpdate.getCustomerId() + ". Skipping update operations.");
					return; // Exit early if phone numbers are the same
				}
			} else if (newPhoneNumber == null && currentPhoneNumber == null) {
				LOGGER.info("handlePhoneNumberUpdate: Both new and current phone numbers are null for customer: " + customerUpdate.getCustomerId() + ". Skipping update operations.");
				return; // Exit early if both are null
			}

			// FIRST CHECK: Is the phone number already associated with another customer?
			CustomerEntity existingCustomerWithPhone = client.findByPhoneNumber(newPhoneNumber);
			
			if (existingCustomerWithPhone != null && 
				!existingCustomerWithPhone.getEntityId().equals(customerUpdate.getCustomerId())) {
				
				// YES - Phone number is associated with another account - PERFORM TRANSFER
				LOGGER.info("handlePhoneNumberUpdate: Phone number " + newPhoneNumber + " is already associated with customer: " + existingCustomerWithPhone.getEntityId() + ". Performing transfer operations.");
				
				// Step 1: Remove phone number from the existing customer and set isMobileVerified to false
				String previousOwnerId = existingCustomerWithPhone.getEntityId().toString();
				existingCustomerWithPhone.setPhoneNumber(null);
				existingCustomerWithPhone.setIsMobileVerified(false);
				
				// Step 2: Delete shukranProfileData object when removing phone number
				if (existingCustomerWithPhone.getShukranProfileData() != null) {
					LOGGER.info("handlePhoneNumberUpdate: Deleting shukranProfileData for customer: " + previousOwnerId + " as phone number is being transferred");
					existingCustomerWithPhone.setShukranProfileData(null);
					existingCustomerWithPhone.setShukranLinkFlag(false);
					existingCustomerWithPhone.setProfileId(null);
					existingCustomerWithPhone.setCardNumber(null);
					existingCustomerWithPhone.setTierActivity(null);
					existingCustomerWithPhone.setTierName(null);
				}
				
				existingCustomerWithPhone.setUpdatedAt(new Date());
				client.saveAndFlushCustomerEntity(existingCustomerWithPhone);
				
				LOGGER.info("handlePhoneNumberUpdate: Successfully removed phone number and shukranProfileData from customer: " + previousOwnerId + " and set isMobileVerified to false");
				
			} else {
				// NO - Phone number is not associated with another account
				LOGGER.info("handlePhoneNumberUpdate: Phone number " + newPhoneNumber + " is not associated with another account. Proceeding with simple update.");
			}

			// Step 3: Assign the phone number to the current customer
			currentCustomer.setPhoneNumber(newPhoneNumber);
			currentCustomer.setIsMobileVerified(true);
			currentCustomer.setUpdatedAt(new Date());
			client.saveAndFlushCustomerEntity(currentCustomer);
			
			LOGGER.info("handlePhoneNumberUpdate: Successfully assigned phone number " + newPhoneNumber + " to customer: " + customerUpdate.getCustomerId() + " and set isMobileVerified to true");
			
			// Step 4: Update address database for the new owner
			try {
				otpServiceImpl.validateAndMarkMobileVerifiedInAddressDB(customerUpdate.getCustomerId(), newPhoneNumber);
				LOGGER.info("handlePhoneNumberUpdate: Successfully updated address database for new owner: " + customerUpdate.getCustomerId());
			} catch (Exception e) {
				LOGGER.warn("handlePhoneNumberUpdate: Failed to update address database for new owner: " + customerUpdate.getCustomerId() + ". Error: " + e.getMessage());
			}
			
			// Note: OTP verification status in Redis will automatically expire after TTL (10 minutes)
			
		} catch (Exception e) {
			LOGGER.error("handlePhoneNumberUpdate: Error during phone number update for customer: " + customerUpdate.getCustomerId() + ". Error: " + e.getMessage(), e);
			throw e; // Re-throw to handle in calling method
		}
	}

}
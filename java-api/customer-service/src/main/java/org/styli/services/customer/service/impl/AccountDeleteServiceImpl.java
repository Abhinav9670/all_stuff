package org.styli.services.customer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.helper.AccountHelper;
import org.styli.services.customer.helper.EmailHelper;
import org.styli.services.customer.helper.SmsHelper;
import org.styli.services.customer.model.DeleteCustomersEventsEntity;
import org.styli.services.customer.pojo.DeleteCustomerEntity;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.account.*;
import org.styli.services.customer.pojo.consul.DeleteCustomer;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomerEntityRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomersEventsRepository;
import org.styli.services.customer.service.AccountDeleteService;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 10/06/2022 - 12:33 PM
 */

@Component
public class AccountDeleteServiceImpl implements AccountDeleteService {

     private static final Logger LOGGER = LoggerFactory.getLogger(AccountDeleteServiceImpl.class);
    
    private static final String REVOKE_TOKEN_LOG = "delete/status/update customerId: {} status: {}";
    
    @Autowired
    CustomerEntityRepository customerEntityRepository;
    @Autowired
    DeleteCustomerEntityRepository deleteCustomerEntityRepository;
	@Autowired
	DeleteCustomersEventsRepository deleteCustomersEventsRepository;
    @Autowired
    Client client;
    @Autowired
    AccountHelper accountHelper;
    @Autowired
    RedisHelper redisHelper;
    @Autowired
    SmsHelper smsHelper;
    @Autowired
    EmailHelper emailHelper;
	@Autowired
	CustomerV4ServiceImpl customerV4Service;
    @Value("${env}")
    private String env;

    @Value("${order.ribbon.listOfServers}")
    private String orderServiceBaseUrl;

    @Value("${auth.internal.header.bearer.token}")
    private String internalAuthBearerToken;

    @Value("${earn.base.url}")
	private String easBaseUrl;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;
    
    @Autowired
    private IosSigninHelper iosSigninHelper;

    /**
     * @param request      AccountDeletionOTPRequest
     * @param tokenHeader  String
     * @param xHeaderToken String
     * @return AccountDeleteResponse
     * <p>
     * error code 201: Customer not found!
     * error code 202: Store not found!
     * error code 203: Failed to save otp!
     * error code 204: OTP Message not configured!
     * error code 205: OTP could not be sent to Email!
     */
    @Override
    public AccountDeleteResponse sendOTP(AccountDeletionOTPRequest request, String tokenHeader, String xHeaderToken) {
        AccountDeleteResponse resp = new AccountDeleteResponse();

        CustomerEntity customerEntity = customerEntityRepository.findByEntityId(request.getCustomerId());
        if (ObjectUtils.isEmpty(customerEntity)) {
            resp.setStatus(false);
            resp.setStatusMsg("Customer not found!");
            resp.setStatusCode("201");
            return resp;
        }

        List<Stores> stores = client.getStoresArray();
        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
                .findAny().orElse(null);

        if (ObjectUtils.isEmpty(store) ||
                ObjectUtils.isEmpty(store.getStoreCode()) ||
                ObjectUtils.isEmpty(store.getStoreCurrency())) {
            resp.setStatus(false);
            resp.setStatusCode("202");
            resp.setStatusMsg("Store not found!");
            return resp;
        }

        String langCode = CommonUtility.getLanguageCode(store);
        int unicode = ("ar".equalsIgnoreCase(langCode)) ? 1 : 0;

        String customerId = request.getCustomerId().toString();
        String phoneNumber = null;
		if (ObjectUtils.isNotEmpty(customerEntity.getPhoneNumber())
				&& CommonUtility.isPossibleNumber(customerEntity.getPhoneNumber(), store)) {
			phoneNumber = customerEntity.getPhoneNumber();
		}

        OtpBucketObject bucketObject = accountHelper.getBucketObject(Constants.DELETE_CUSTOMER_OTP_CACHE_NAME, customerId);
        long now = Instant.now().toEpochMilli();
        if (ObjectUtils.isEmpty(bucketObject)) {
            bucketObject = new OtpBucketObject();
            bucketObject.setOriginAt(now);
            bucketObject.setCreateCount(0);
        }
        String otp = accountHelper.generateSafeOtp(bucketObject, now);
        long expireAt = now + TtlMode.OTP_VALID.getTimeUnit().toMillis(TtlMode.OTP_VALID.getValue());
        bucketObject.setCustomerId(customerId);
        bucketObject.setOtp(otp);
        bucketObject.setCreatedAt(now);
        bucketObject.setExpiresAt(expireAt);

        if (bucketObject.getCreateCount() == null || bucketObject.getCreateCount() < 1)
            bucketObject.setCreateCount(1);
        LOGGER.info("Starting otp put to redis!");
        boolean success =
                redisHelper.put(Constants.DELETE_CUSTOMER_OTP_CACHE_NAME,
                        bucketObject.getCustomerId(),
                        bucketObject,
                        TtlMode.OTP_REDIS);
        LOGGER.info("Otp put to redis ended with success: {}", success);

        if (!success) {
            resp.setStatus(false);
            resp.setStatusCode("203");
            resp.setStatusMsg("Failed to save otp!");
            return resp;
        }

        String message = accountHelper.getOtpMessage(langCode);
        String emailMessage = accountHelper.getEmailMessage(langCode);
        if (StringUtils.isBlank(message)) {
            resp.setStatus(false);
            resp.setStatusCode("204");
            resp.setStatusMsg("OTP Message not configured!");
            return resp;
        }

		if (ObjectUtils.isNotEmpty(request.getDebugMode()) && request.getDebugMode().booleanValue()
				&& StringUtils.isNotEmpty(env) && !"live".equalsIgnoreCase(env) && !"prod".equalsIgnoreCase(env)) {
			resp.setOtpData(bucketObject);
		}

        emailMessage = emailMessage.replace("{{otp}}", bucketObject.getOtp());
        boolean emailOtpResponse = sendOtpEmail(customerEntity, emailMessage, langCode);
        if (!emailOtpResponse) {
            resp.setStatus(false);
            resp.setStatusCode("205");
            resp.setStatusMsg("OTP could not be sent to Email!");
            return resp;
        }

        message = message.replace("{{otp}}", bucketObject.getOtp());
        if (StringUtils.isNotBlank(phoneNumber)) smsHelper.sendSMS(phoneNumber, message, unicode, false);

        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg(Constants.SUCCESS_MSG);
        return resp;
    }

    private boolean sendOtpEmail(CustomerEntity customerEntity, String message, String langCode) {

        return emailHelper.sendEmail(customerEntity.getEmail(),
                customerEntity.getFirstName(),
                message,
                EmailHelper.CONTENT_TYPE_HTML,
                accountHelper.getEmailSubject(langCode),
                langCode
        );
    }

    /**
	 *
	 * @param request AccountDeletionRequest
	 * @return AccountDeleteResponse
	 *
	 * error code 201: Customer not found!
	 * error code 203: Delete customer record was not found!
	 * error code 204: Customer already deleted!
	 * error code 205: Customer already submitted for delete!!
	 * error code 206: Customer already active!
	 * error code 207: OTP Object not found for this customer!
	 * error code 208: Otp has expired!
	 * error code 209: Incorrect OTP requested!
	 * error code 500: Error occurred while delete customer request
	 */
	@Override
	@Transactional
	public AccountDeleteResponse deleteOrWithdrawCustomerAccount(AccountDeletionRequest request) {

		AccountDeleteResponse resp = new AccountDeleteResponse();
		try {

			CustomerEntity customerEntity = customerEntityRepository.findByEntityId(request.getCustomerId());
			if (ObjectUtils.isEmpty(customerEntity)) {
				resp.setStatus(false);
				resp.setStatusMsg("Customer not found!");
				resp.setStatusCode("201");
				return resp;
			}

			if (customerEntity.getIsActive().equals(CustomerStatus.DELETED.getValue())) {
				resp.setStatus(false);
				resp.setStatusMsg("Customer already deleted!");
				resp.setStatusCode("204");
				return resp;
			}

			if (ObjectUtils.isNotEmpty(request.getRequestType())) {

				DeleteCustomerEntity deleteCustomerEntity = deleteCustomerEntityRepository
						.findByCustomerId(request.getCustomerId());

				if (request.getRequestType().equalsIgnoreCase("submit")) {

					if (customerEntity.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
						resp.setStatus(false);
						resp.setStatusMsg("Customer already submitted for delete!");
						resp.setStatusCode("205");
						return resp;
					}

					/** OTP validation */
					OtpBucketObject bucketObject = getOtpForThisCustomer(request);
					if(null == bucketObject || ObjectUtils.isEmpty(bucketObject)) {
						resp.setStatus(false);
						resp.setStatusMsg("OTP Object not found for this customer!");
						resp.setStatusCode("207");
						return resp;
					}
					long now = Instant.now().toEpochMilli();
					if (now > bucketObject.getExpiresAt()) {
						resp.setStatus(false);
						resp.setStatusCode("208");
						resp.setStatusMsg("Otp has expired!");
						return resp;
					}
					if (!request.getOtp().equals(bucketObject.getOtp())) {
						resp.setStatus(false);
						resp.setStatusCode("209");
						resp.setStatusMsg("Incorrect OTP requested!");
						return resp;
					}

					customerEntity.setIsActive(CustomerStatus.DELETE_REQUESTED.getValue());
					customerEntity.setUpdatedAt(new Date());
					customerEntityRepository.save(customerEntity);

					deleteCustomerEntity = deleteCustomerEntityRepository.findByCustomerId(request.getCustomerId());
					if (ObjectUtils.isNotEmpty(deleteCustomerEntity)) {
						updateForDeleteCustomer(deleteCustomerEntity, request);

					} else {
						deleteCustomerEntity = new DeleteCustomerEntity();
						updateForDeleteCustomer(deleteCustomerEntity, request);
						deleteCustomerEntity.setCustomerId(request.getCustomerId());
					}

				} else if (request.getRequestType().equalsIgnoreCase("withdraw")) {

					if (customerEntity.getIsActive().equals(CustomerStatus.ACTIVE.getValue())) {
						resp.setStatus(false);
						resp.setStatusMsg("Customer already active!");
						resp.setStatusCode("206");
						return resp;
					}

					customerEntity.setIsActive(CustomerStatus.ACTIVE.getValue());
					customerEntity.setUpdatedAt(new Date());
					customerEntityRepository.save(customerEntity);

					if (ObjectUtils.isNotEmpty(deleteCustomerEntity)) {
						deleteCustomerEntity.setMarkedForDelete(0);
						deleteCustomerEntity.setTtlTime(null);
						deleteCustomerEntity.setWithdrawnAt(new Date());

					} else {
						resp.setStatus(false);
						resp.setStatusMsg("Delete customer record was not found");
						resp.setStatusCode("203");
						return resp;
					}
				}
				deleteCustomerEntity.setCustomerEmail(customerEntity.getEmail());
				deleteCustomerEntityRepository.saveAndFlush(deleteCustomerEntity);

				resp.setStatus(true);
				resp.setStatusCode("200");
				resp.setStatusMsg(Constants.SUCCESS_MSG);
			}
		} catch (Exception e) {
			resp.setStatus(false);
			resp.setStatusMsg("Error occurred while delete customer request");
			resp.setStatusCode("500");
			return resp;
		}
		return resp;
	}

	private OtpBucketObject getOtpForThisCustomer(AccountDeletionRequest request) {

		OtpBucketObject bucketObject = accountHelper.getBucketObject(
				Constants.DELETE_CUSTOMER_OTP_CACHE_NAME,
				request.getCustomerId().toString());

		if(ObjectUtils.isEmpty(bucketObject)
				|| ObjectUtils.isEmpty(bucketObject.getExpiresAt())
				|| StringUtils.isEmpty(bucketObject.getOtp())) {
			return null;
		} else return bucketObject;

	}

	private void updateForDeleteCustomer(DeleteCustomerEntity deleteCustomerEntity, AccountDeletionRequest request) {

		deleteCustomerEntity.setMarkedForDelete(1);
		Date currentDate = new Date();
		DeleteCustomer deleteCustomer = ServiceConfigs.getDeleteCustomer();
		if(null != deleteCustomer) {
			deleteCustomerEntity.setTtlTime(DateUtils.addHours(currentDate, deleteCustomer.getTtlCustomerAccount()));
		}
		deleteCustomerEntity.setRequestedAt(currentDate);
		deleteCustomerEntity.setReason(request.getReason());
	}

	@Override
	public AccountDeletionEligibleResponse checkAccountDeletionEligiblity(AccountDeletionEligibleRequest request,
			String tokenHeader, String xHeaderToken) throws JsonProcessingException {

		AccountDeletionEligibleResponse body = null;
		try {
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
			
			if (null != internalAuthBearerToken && internalAuthBearerToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(internalAuthBearerToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList) && !authTokenList.isEmpty()) {
					requestHeaders.add("authorization-token", authTokenList.get(0));
				}
			}

			HttpEntity<AccountDeletionEligibleRequest> requestBody = new HttpEntity<>(request, requestHeaders);
			String url = "http://" + orderServiceBaseUrl + "/rest/order/delete/eligible";

			LOGGER.info("account eligible order requst URL: {}", url);
			ResponseEntity<AccountDeletionEligibleResponse> orderresp = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					AccountDeletionEligibleResponse.class);

			LOGGER.info("account eligible order response body:, {}", orderresp.getStatusCode() );
			body = orderresp.getBody();

			if(null != body) {
				body.setStyliCoin(getStyliCoins(request, tokenHeader, xHeaderToken));
				body.setEligible(checkEligilibilty(body));
			}
		}catch (Exception e) {
			LOGGER.error("Exception occurred in account deletion eligibility check: {}", e.getMessage());
		}

		return body;
	}

	@Override
	public AccountDeleteResponse processDeleteRequestsCleanup() {
		AccountDeleteResponse resp = new AccountDeleteResponse();
		List<DeleteCustomerEntity> requests = accountHelper.getDeleteRequestsForCleanup();

		if(requests.isEmpty()) {
			LOGGER.info("No records found in delete_customers for cleanup!");
		} else {
			LOGGER.info("Records found in delete_customers for cleanup! {}", requests.size());
			for(DeleteCustomerEntity deleteCustomerEntity: requests) {
				List<DeleteCustomersEventsEntity> events =
						deleteCustomersEventsRepository.findByCustomerId(deleteCustomerEntity.getCustomerId());
				long falseCount = events.stream().filter(e-> e.getStatus().equals(0)).count();
				if(falseCount == 0) {
					CustomerEntity customerEntity = customerEntityRepository.findByEntityId(deleteCustomerEntity.getCustomerId());
					if(ObjectUtils.isNotEmpty(deleteCustomerEntity) && ObjectUtils.isNotEmpty(customerEntity)) {
						deleteCustomerEntity.setCompletedAt(new Date());
						deleteCustomerEntity.setCustomerEmail(customerEntity.getEmail());
						deleteCustomerEntityRepository.saveAndFlush(deleteCustomerEntity);
					}
				}
			}
		}

		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg(Constants.SUCCESS_MSG);
		return resp;
	}

	@Override
	public AccountDeleteResponse processDeleteRequests() {

		AccountDeleteResponse resp = new AccountDeleteResponse();
		List<DeleteCustomerEntity> requests = accountHelper.getDeleteRequests();

		if(requests.isEmpty()) {
			LOGGER.info("No records found in delete_customers to process!");
		} else {
			List<Integer> customerIds = requests
					.stream()
					.map(DeleteCustomerEntity::getCustomerId)
					.collect(Collectors.toList());
			LOGGER.info("Records found in delete_customers to process! {}, Customer ID's: {} ", requests.size(), customerIds);
			List<CustomerEntity> customers = customerEntityRepository.findAllByEntityIdIn(customerIds);
			for(CustomerEntity customer: customers) {
					processCustomerDelete(customer, requests);
			}
		}

		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg(Constants.SUCCESS_MSG);
		return resp;
	}

	/**
	 *
	 * @param request AccountDeleteTaskUpdateRequest
	 * @return AccountDeleteResponse
	 *
	 * error code 201: No events found for this customerId!
	 */
	@Override
	@Transactional
	public AccountDeleteResponse processStatusUpdates(AccountDeleteTaskUpdateRequest request) {
		AccountDeleteResponse resp = new AccountDeleteResponse();
		List<DeleteCustomersEventsEntity> events = deleteCustomersEventsRepository.findByCustomerId(request.getCustomerId());

		if(events.isEmpty()) {
			resp.setStatus(false);
			resp.setStatusMsg("No events found for this customerId!");
			resp.setStatusCode("201");
			return resp;
		}

		DeleteCustomersEventsEntity event = events.stream().filter(e -> e.getTask().equals(request.getTask()))
				.findFirst().orElse(null);
		if(event != null && ObjectUtils.isNotEmpty(event)) {
			event.setStatus(request.isStatus() ? 1: 0);
			event.setUpdatedAt(new Date());
			deleteCustomersEventsRepository.saveAndFlush(event);
		}
		long falseCount = events.stream().filter(e-> e.getStatus().equals(0)).count();
		if(falseCount == 0) {
			DeleteCustomerEntity deleteCustomerEntity = deleteCustomerEntityRepository.findByCustomerId(request.getCustomerId());
			CustomerEntity customerEntity = customerEntityRepository.findByEntityId(request.getCustomerId());

			if(ObjectUtils.isNotEmpty(deleteCustomerEntity) && ObjectUtils.isNotEmpty(customerEntity)) {
				deleteCustomerEntity.setCompletedAt(new Date());
				deleteCustomerEntity.setCustomerEmail(customerEntity.getEmail());
				deleteCustomerEntityRepository.saveAndFlush(deleteCustomerEntity);
			}

		}

		resp.setStatus(true);
		resp.setStatusMsg(Constants.SUCCESS_MSG);
		resp.setStatusCode("200");
		return resp;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void processCustomerDelete(CustomerEntity customer, List<DeleteCustomerEntity> requests) {

	    try {
	        String email = "deleted_user_" + customer.getEntityId() + "@stylishop.com";
	        LOGGER.info("processCustomerDelete email created: {}" +email);

	            accountHelper.handleCustomerGridFlat(email, customer);
	            LOGGER.info("processCustomerDelete customer_grid_flat done!");
	       
	            accountHelper.handleCustomerAddressEntity(customer);
	            LOGGER.info("processCustomerDelete customer_address_entity done!");
	        
	            accountHelper.handleCustomerEntity(email, customer);
	            LOGGER.info("processCustomerDelete new_customer_entity done!");
	       
	            accountHelper.handleDeleteCustomerEntity(requests, customer);
	            LOGGER.info("processCustomerDelete delete_customers done!");
	        
	            customerV4Service.deleteShukranAccount(customer.getEntityId());
	            LOGGER.info("processCustomerDelete delete_shukran_account done!");
	       
	            processDeleteCustomerEvents(customer);
	            LOGGER.info("processCustomerDelete delete_customers_events done!");
	       
	            accountHelper.handleKafkaPush(customer);
	            LOGGER.info("processCustomerDelete kafka push done!");
	       

	    } catch (Exception e) {
	        LOGGER.info("Error occurred while deleting customers' data! ID: {} Error: {}" +customer.getEntityId());
	    }
	}
	
	private void processDeleteCustomerEvents(CustomerEntity customer) {
	    try {
	        accountHelper.handleDeleteCustomerEventsRows(customer);
	    } catch (Exception e) {
	        // Only log the message, NOT the exception object to prevent error monitoring systems
	        // (like Elastic APM) from detecting and classifying this as an error
	        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	        if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
	            LOGGER.info("[enableCustomerServiceErrorHandling] Error in handleDeleteCustomerEventsRows for customerId: {} - {}", customer.getEntityId(), errorMsg);
	        } else {
	            LOGGER.info("Error in handleDeleteCustomerEventsRows for customerId: {} - {}", customer.getEntityId(), errorMsg);
	        }
	    }
	}
	
	private boolean checkEligilibilty(AccountDeletionEligibleResponse response) {

		return !(response.isOrders() || response.isReturns() || response.isStylicredit());
	}

	private boolean getStyliCoins(AccountDeletionEligibleRequest request, String tokenHeader, String xHeaderToken) {

		boolean styliCoinsPresent = false;

		StyliCoinsRequest styliCoinsRequest = new StyliCoinsRequest();
		styliCoinsRequest.setCustomerId(request.getCustomerId());
		styliCoinsRequest.setStoreId(request.getStoreId());

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add("Token", tokenHeader);
		requestHeaders.add("x-header-token", xHeaderToken);

		HttpEntity<StyliCoinsRequest> requestBody = new HttpEntity<>(styliCoinsRequest, requestHeaders);
		String url = easBaseUrl + "/api/v1/userCoinSummary";

		try {
			LOGGER.info("eas requst URL: {}", url);
			ResponseEntity<StyliCoinsResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					StyliCoinsResponse.class);

			StyliCoinsResponse body = response.getBody();

			if (response.getStatusCode() == HttpStatus.OK && null != body && null != body.getData()
					&& null != body.getData().getCustomerInfo()
					&& body.getData().getCustomerInfo().getCoinAvailable() > 0) {
				styliCoinsPresent = true;
			}
		} catch (RestClientException e) {
			LOGGER.error("Exception occurred  during REST call: {}", e.getMessage());
		} catch (Exception ex) {
			LOGGER.error("exception occurred during styli coins call: {}", ex.getMessage());
		}
		return styliCoinsPresent;
	}

	/**
	 * Revoke the apple authentication
	 */
	@Override
	@Transactional
	public void revokeAppleAuth(Customer customer) {
		String taskType = "apple-revoke";
		Integer customerId = customer.getCustomerId();
		try {
			AccountDeleteTaskUpdateRequest request;
			CustomerEntity cust = customerEntityRepository.findByEntityId(customerId);
			if (Objects.isNull(cust)) {
				LOGGER.info("Customer : {} didn't found to revoke the apple auth access.", customerId);
				request = new AccountDeleteTaskUpdateRequest(customerId, taskType, true);
				LOGGER.info(REVOKE_TOKEN_LOG, customerId, true);
				processStatusUpdates(request);
				return;
			}
			if (Objects.nonNull(cust.getSignedInNowUsing()) && cust.getSignedInNowUsing() == 2
					&& Objects.nonNull(cust.getRefreshToken())) {
				boolean revokeToken = iosSigninHelper.revokeToken(cust.getRefreshToken());
				if(revokeToken) {
					cust.setRefreshToken(null);
					cust.setUpdatedAt(new Date());
					customerEntityRepository.save(cust);	
				}
				request = new AccountDeleteTaskUpdateRequest(customerId, taskType, revokeToken);
				LOGGER.info(REVOKE_TOKEN_LOG, customerId, revokeToken);
			} else {
				LOGGER.info(REVOKE_TOKEN_LOG, customerId, true);
				request = new AccountDeleteTaskUpdateRequest(customerId, taskType, true);
			}
			processStatusUpdates(request);
		} catch (Exception e) {
			LOGGER.error("Error in revoking deleted customer", e);
			LOGGER.info(REVOKE_TOKEN_LOG, customerId, false);
			processStatusUpdates(new AccountDeleteTaskUpdateRequest(customerId, taskType, false));
		}
	}

}
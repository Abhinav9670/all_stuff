package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.transaction.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerAddressEntity;
import org.styli.services.order.model.Customer.CustomerAddressEntityVarchar;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.request.CustomerRequestBody;
import org.styli.services.order.pojo.request.Order.CustomerOmsresponsedto;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.CustomerAddrees;
import org.styli.services.order.pojo.response.CustomerProfileResponse;
import org.styli.services.order.pojo.response.CustomerStoreCredit;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponse;
import org.styli.services.order.pojo.response.CustomerUpdateProfileResponse;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.service.CustomerService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.UtilityCustomerConatant;

@Component
public class CustomerServiceImpl implements CustomerService {

    private static final Log LOGGER = LogFactory.getLog(CustomerServiceImpl.class);

    @Autowired
    StaticComponents staticComponents;

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    CustomerAddressEntityRepository customerAddressEntityRepository;

    @Autowired
    AmastyStoreCreditRepository amastyStoreCreditRepository;

    @Autowired
    AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
    
    @Autowired
    OrderHelper orderHelper;
    
    @Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

    @Override
    @Transactional
	public CustomerUpdateProfileResponse getCustomerDetails(Integer customerId, Map<String, String> requestHeader) {
		CustomerUpdateProfileResponse response = new CustomerUpdateProfileResponse();
		CustomerProfileResponse responseBody = new CustomerProfileResponse();
		try {
			CustomerEntity customerEntity = orderHelper.getCustomerDetails(customerId,null);
			Integer ageGroupId = null;
			Boolean whatsappOptn = false;
			String mobileNumber = null;
			if (null != customerEntity) {
				responseBody.setCustomer(getSavedCustomerInfo(customerEntity, mobileNumber, ageGroupId, whatsappOptn));
				responseBody.setStatus(true);
				if (null != customerEntity.getDefaultShipping()) {
					CustomerAddressEntity customerAddressEntity = customerAddressEntityRepository
							.findByEntityId(customerEntity.getDefaultShipping());
					if (null != customerAddressEntity) {
						responseBody.setDefaultAddress(
								setCustomerAddress(customerAddressEntity, customerEntity.getDefaultShipping()));
					}
				}
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
				response.setStatusMsg("Success!");
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
     * @param addressEntity
     * @param shiftingAddressId
     * @return CustomerAddrees
     */
	private CustomerAddrees setCustomerAddress(CustomerAddressEntity addressEntity, Integer shiftingAddressId) {
		CustomerAddrees address = new CustomerAddrees();
		address.setCustomerId(addressEntity.getParentId());
		address.setAddressId(addressEntity.getEntityId());
		address.setFirstName(addressEntity.getFirstname());
		address.setLastName(addressEntity.getLastName());
		address.setCountry(addressEntity.getCountryId());
		address.setCity(addressEntity.getCity());
		address.setRegion(addressEntity.getRegion());
		address.setRegionId(addressEntity.getRegionId());
		if (null != addressEntity.getStreet() && ArrayUtils.isNotEmpty(addressEntity.getStreet().split("\n"))) {
			address.setBuildingNumber(addressEntity.getStreet().split("\n")[0]);
			if (addressEntity.getStreet().split("\n").length > 1) {
				address.setStreetAddress(addressEntity.getStreet().split("\n")[1]);
			}
		}
		CustomerAddressEntityVarchar area = null;
		CustomerAddressEntityVarchar landMark = null;

		if (StringUtils.isNotBlank(addressEntity.getTelephone())
				&& !addressEntity.getTelephone().matches("[+]{1}[0-9]{3}\\s{1}.*")
				&& addressEntity.getTelephone().length() > 4) {
			/** This change for those phone does not have space **/
			StringBuilder sb = new StringBuilder();
			addressEntity.setTelephone(sb.toString());
			customerAddressEntityRepository.saveAndFlush(addressEntity);
			address.setMobileNumber(addressEntity.getTelephone());
		} else {
			address.setMobileNumber(addressEntity.getTelephone());
		}
		prepareAddress(addressEntity, shiftingAddressId, address, area, landMark);
		return address;
	}

	private void prepareAddress(CustomerAddressEntity addressEntity, Integer shiftingAddressId, CustomerAddrees address,
			CustomerAddressEntityVarchar area, CustomerAddressEntityVarchar landMark) {
		Set<CustomerAddressEntityVarchar> addressVarList = addressEntity.getCustomerAddressEntityVarchar();
		if (null != staticComponents.getAttrMap()) {
			Integer areaAttributeId = staticComponents.getAttrMap().entrySet().stream()
					.filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_AREA_ATTRIBUTE))
					.map(Map.Entry::getKey).findFirst().orElse(0);
			Integer landmarkAttributeId = staticComponents.getAttrMap().entrySet().stream()
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
			if (null != addressVarList) {
				area = addressVarList.stream()
						.filter(x -> null != x.getAttributeId() && areaAttributeId.equals(x.getAttributeId())).findAny()
						.orElse(null);
				landMark = addressVarList.stream()
						.filter(x -> null != x.getAttributeId() && landmarkAttributeId.equals(x.getAttributeId()))
						.findAny().orElse(null);
			}
		}
		if (null != area) {
			address.setArea(area.getValue());
		}
		if (null != landMark) {
			address.setLandMark(landMark.getValue());
		}
		address.setDefaultAddress(null != shiftingAddressId && shiftingAddressId.equals(addressEntity.getEntityId()));
	}

	/**
	 * @param savedCustomer
	 * @param phone
	 * @return
	 */
	private Customer getSavedCustomerInfo(CustomerEntity savedCustomer, String phone, Integer ageFGroup,
			Boolean whatsAppOptn) {
		Customer customer = new Customer();
		customer.setCustomerId(savedCustomer.getEntityId());
		customer.setFirstName(savedCustomer.getFirstName());
		customer.setLastName(savedCustomer.getLastName());
		customer.setEmail(savedCustomer.getEmail());
		customer.setMobileNumber(phone);
		customer.setGroupId(savedCustomer.getGroupId());
		customer.setCreatedIn(savedCustomer.getCreatedIn());
		customer.setCreatedAt(savedCustomer.getCreatedAt().toString());
		customer.setUpdatedAt(savedCustomer.getUpdatedAt().toString());
		customer.setGender(savedCustomer.getGender());
		customer.setDob(savedCustomer.getDob());
		customer.setAgeGroupId(ageFGroup);
		customer.setWhatsAppoptn(whatsAppOptn);
		return customer;
	}

	private Integer parseNullInteger(String val) {
		return org.apache.commons.lang3.StringUtils.isNoneBlank(val) ? Integer.parseInt(val) : null;
	}

	@Override
	public void deductStoreCreditV2(QuoteDTO quoteObject, SalesOrder order, Stores store,
			BigDecimal amastyBaseStoreBalance) throws NotFoundException {
		if (quoteObject.getCustomerId() == null)
			return;

		try {
			Integer customerId = parseNullInteger(quoteObject.getCustomerId());
			if (quoteObject.getStoreCreditApplied() != null) {
				BigDecimal requestedStoreCreditAmount = new BigDecimal(quoteObject.getStoreCreditApplied());
				BigDecimal convertedRequestedValue = requestedStoreCreditAmount
						.multiply(store.getCurrencyConversionRate()).setScale(4, RoundingMode.HALF_UP);
				BigDecimal subtractedValue = amastyBaseStoreBalance.subtract(convertedRequestedValue).setScale(4,
						RoundingMode.HALF_UP);
				if (subtractedValue.intValue() == 0) {
					subtractedValue = new BigDecimal(0);
				}
				List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository
						.findByCustomerId(customerId);
				int newCustomerHistoryId = 1;
				if (CollectionUtils.isNotEmpty(histories)) {
					AmastyStoreCreditHistory lastHistory = histories.get(histories.size() - 1);
					newCustomerHistoryId = lastHistory.getCustomerHistoryId() + 1;
				}
				AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
				history.setCustomerHistoryId(newCustomerHistoryId);
				history.setCustomerId(customerId);
				history.setDeduct(1);
				history.setDifference(convertedRequestedValue);
				history.setStoreCreditBalance(subtractedValue);
				history.setAction(4);
				history.setAction(4);
				history.setActionData("[\"" + order.getIncrementId() + "\"]");
				history.setMessage(null);
				history.setCreatedAt(new Timestamp(new Date().getTime()));
				history.setStoreId(Integer.parseInt(quoteObject.getStoreId()));
				amastyStoreCreditHistoryRepository.saveAndFlush(history);
				List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository.findByCustomerId(customerId);
				AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
				if (amastyStoreCredit != null) {
					amastyStoreCredit.setStoreCredit(subtractedValue);
					if (null != amastyStoreCredit.getReturnableAmount()) {
						amastyStoreCredit
								.setReturnableAmount(amastyStoreCredit.getReturnableAmount().min(subtractedValue));
					}
					amastyStoreCreditRepository.saveAndFlush(amastyStoreCredit);
				}
			}

		} catch (Exception e) {
			LOGGER.error("exception occoured during deduct store cdredit:" + e.getMessage());
			throw new NotFoundException(e.getMessage());
		}
	}

    @Override
    @Transactional
	public CustomerStoreCreditResponse getCustomerStoreCredit(Integer customerId) {
		CustomerStoreCreditResponse customerStoreCreditResponse = new CustomerStoreCreditResponse();
		List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository.findByCustomerId(customerId);
		AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
		if (amastyStoreCredit != null) {
			CustomerStoreCredit customerStoreCredit = new CustomerStoreCredit();
			customerStoreCredit.setStoreCredit(amastyStoreCredit.getStoreCredit());
			customerStoreCreditResponse.setResponse(customerStoreCredit);
			customerStoreCreditResponse.setStatus(true);
			customerStoreCreditResponse.setStatusCode("200");
			customerStoreCreditResponse.setStatusMsg("Store credit fetched successfully!");
			return customerStoreCreditResponse;
		} else {
			customerStoreCreditResponse.setStatus(false);
			customerStoreCreditResponse.setStatusCode("201");
			customerStoreCreditResponse.setStatusMsg("No store credit wallet found for customer!");
			return customerStoreCreditResponse;
		}
	}

	@Override
	public List<Customer> findReferralCustomers(List<Integer> customerIds) {
		String url = "";
		if (null != Constants.orderCredentials
				&& null != Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl()) {
			url = Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl()
					+ "/rest/customer/oms/referrals";

		}
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity<?> requestBody = new HttpEntity<>(customerIds, requestHeaders);

		try {
			ResponseEntity<CustomerOmsresponsedto> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					CustomerOmsresponsedto.class);
			CustomerOmsresponsedto body = response.getBody();
			if(response.getStatusCodeValue() == 200 && Objects.nonNull(body)) {
				return body.getResponse();
			}
		} catch (Exception e) {
			LOGGER.error("Error in finding referral customers. Error ", e);
		}
		return new ArrayList<>();
	}

 
}

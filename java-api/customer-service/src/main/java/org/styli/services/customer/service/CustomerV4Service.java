package org.styli.services.customer.service;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.pojo.CustomerOmsResponsedto;
import org.styli.services.customer.pojo.CustomerRequestBody;
import org.styli.services.customer.pojo.CustomerVerificationStatusResponse;
import org.styli.services.customer.pojo.FirstFreeShipping;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.epsilon.request.LinkShukranRequest;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.epsilon.response.DeleteShukranResponse;
import org.styli.services.customer.pojo.epsilon.response.EnrollmentResponse;
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
import org.styli.services.customer.pojo.registration.response.CustomerCheckvalidityResponse;
import org.styli.services.customer.pojo.registration.response.CustomerExistResponse;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.MobileNumberUpdateAcknowledgmentResponse;
import org.styli.services.customer.pojo.registration.response.RecaptchaVerifyRequest;
import org.styli.services.customer.pojo.registration.response.RecaptchaVerifyResponse;
import org.styli.services.customer.pojo.registration.response.WhatsAppOptResponse;
import org.styli.services.customer.pojo.epsilon.request.UpgradeShukranTierActivityRequest;
import org.styli.services.customer.pojo.epsilon.response.BuildUpgradeShukranTierActivityResponse;
import org.styli.services.customer.pojo.GenericApiResponse;

@Service
public interface CustomerV4Service {

	static final String CACHE_NAME = "shukran-bucket";

	static final String PROVINCE_LIST_CACHE = "provinceList";

	CustomerV4RegistrationResponse saveV4Customer(CustomerV4Registration customerRegistration,
			Map<String, String> requestHeader) throws CustomerException;

	CustomerExistResponse validateUser(CustomerQueryReq customerExitsReq, Map<String, String> requestHeader);

	public CustomerLoginV4Response getCustomerLoginV4Details(CustomerLoginV4Request customerLoginRequest,
			Map<String, String> requestHeader) throws CustomerException;

	CustomerUpdateProfileResponse updateCustomer(CustomerUpdateProfileRequest customerInfoRequest,
			Map<String, String> requestHeader);

	CustomerUpdateProfileResponse getCustomerDetails(CustomerRequestBody request, Map<String, String> requestHeader);

	CustomerRestPassResponse changePassword(CustomerPasswordRequest passwordReset);

	CustomerRestPassResponse resetCustomerPassword(String email, Integer storeId, Map<String, String> requestHeader)
			throws CustomerException;

	WhatsAppOptResponse getWhatsAppOtp(Map<String, String> httpServletRequest, WhatsAppOtpRequest request);

	CustomerWishlistResponse removeWishList(CustomerWishListRequest customerWishList);

	CustomerWishlistResponse getWishList(Integer customerId, Integer storeId, boolean standalone);

	CustomerWishlistResponse saveUpdateV4OneWishList(CustomerWishListRequest customerWishList, Map<String, String> requestHeader, boolean isSave);

	CustomerAddreesResponse saveAddress(CustomerAddrees customerAddRequest, boolean isSave, Map<String, String> requestHeader);

	CustomerAddreesResponse deleteAddress(CustomerAddrees customerAddRequest);

	CustomerAddreesResponse getAddress(Integer customerId,Map<String, String> requestHeader);

	CustomerAddreesResponse getAddressById(Integer addressId, Integer customerId);
	
	Boolean authenticateCheck(@RequestHeader Map<String, String> requestHeader, Integer customerId);

	CustomerCheckvalidityResponse customerValidityCheck(@Valid CustomerValidityCheckRequest request,Map<String, String> requestHeader);

	String getPhoneNumberByEmailId(String emailId);
	
	RecaptchaVerifyResponse verifyRecaptcha(Map<String, String> requestHeader, @Valid RecaptchaVerifyRequest request, String customerIp);
	
	Integer getRegistrationIncrementId() throws CustomerException ;

	void validateDeletedUser(Integer customerId);

	FirstFreeShipping setFreeShipping(String createdAt, Integer storeId);
	
	public CustomerOmsResponsedto findReferralCustomers(List<Integer> customerIds);
	
	CustomerProfileResponse findCustomerById(String customerIds);

	EnrollmentResponse enrollShukranAccount(ShukranEnrollmentRequest shukranEnrollmentRequest,
											Map<String, String> requestHeader);
	EnrollmentResponse linkShukranAccount(LinkShukranRequest linkShukranRequest,
										  Map<String, String> requestHeader);
	DeleteShukranResponse deleteShukranAccount(Integer customerId);
	BuildUpgradeShukranTierActivityResponse shukranUpgradeTierActivity(Map<String,String> requestHeader, UpgradeShukranTierActivityRequest requestBody);
	CustomerVerificationStatusResponse getCustomerById(@Valid String customerId);
	
	/**
	 * Handles Shukran phone number unlink webhook
	 * Called when a phone number is unlinked from one Shukran account and linked to another
	 */
    ShukranWebhookResponse handleShukranPhoneUnlinkWebhook(ShukranWebhookRequest webhookRequest);

    /**
     * Handles Shukran phone number update webhook
     * Updates customer phone number using loyalty card number
     */
    ShukranWebhookResponse handleShukranPhoneUpdateWebhook(ShukranWebhookRequest webhookRequest);

    /**
     * Updates the mobile number update message acknowledgment flag for a customer
     */
    MobileNumberUpdateAcknowledgmentResponse updateMobileNumberUpdateMessageAcknowledged(
            MobileNumberUpdateAcknowledgmentRequest request, Map<String, String> requestHeader);

    /**
     * Records the timestamp when user dismisses the address nudge
     */
    GenericApiResponse<String> recordNudgeSeen(
            CustomerRequestBody request, Map<String, String> requestHeader);
}

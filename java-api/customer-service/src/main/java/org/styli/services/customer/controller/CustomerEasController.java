package org.styli.services.customer.controller;

import java.util.Map;
import java.util.Objects;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.customer.pojo.eas.EarnCustomerProfileResponse;
import org.styli.services.customer.pojo.eas.EarnCustomerUpdateProfileRequest;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.impl.EasCustomerService;
import org.styli.services.customer.utility.Constants;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/rest/customer/eas")
@Api(value = "/rest/customer/eas", produces = "application/json")
public class CustomerEasController {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerEasController.class);

	@Autowired
	CustomerV4Service customerV4Service;

	@Autowired
	EasCustomerService easCustomerService;

	@Value("${customer.jwt.flag}")
	String jwtFlag;

	@PostMapping("auth/profile/update")
	public EarnCustomerProfileResponse updateProfile(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody EarnCustomerUpdateProfileRequest earnCustomerUpdateProfileRequest) {

		EarnCustomerProfileResponse earnCustomerProfileResponse = new EarnCustomerProfileResponse();
		if (Objects.nonNull(Constants.StoreConfigResponse.getDisabledServices())
				&& Constants.StoreConfigResponse.getDisabledServices().isEarnDisabled()) {
			LOGGER.info(Constants.EARN_SERVICE_OFF);
			earnCustomerProfileResponse.setStatusCode("202");
			earnCustomerProfileResponse.setStatus(false);
			earnCustomerProfileResponse.setStatusMsg(Constants.EARN_SERVICE_OFF);
			return earnCustomerProfileResponse;
		}
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, earnCustomerUpdateProfileRequest.getCustomerId());
		}

		if (null != earnCustomerUpdateProfileRequest) {
			earnCustomerProfileResponse = easCustomerService.update(earnCustomerUpdateProfileRequest, requestHeader);
		}
		return earnCustomerProfileResponse;
	}
	
	/*
	 * @PostMapping("get/profile/{customerId}") public CustomerUpdateProfileResponse
	 * getProfileInfoByCustomerId(@RequestHeader Map<String, String>
	 * requestHeader, @PathVariable Integer customerId,
	 * 
	 * @RequestHeader(value = "authorization-token", required = false) String
	 * authorizationToken) { CustomerUpdateProfileResponse response = null;
	 * 
	 * if (Constants.orderCredentials.isInternalAuthEnable()) {
	 * if(easCustomerService.checkAuthorization(authorizationToken)) {
	 * 
	 * // CustomerEntity customerEntity = client.findByEntityId(customerId); } }
	 * return response; 
	 * }
	 */
}

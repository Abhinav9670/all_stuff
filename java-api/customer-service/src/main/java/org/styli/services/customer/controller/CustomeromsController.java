package org.styli.services.customer.controller;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.customer.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.customer.db.product.config.firebase.FirebaseUser;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.pojo.*;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.service.BigQuerySyncService;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.CustomerV5Service;
import org.styli.services.customer.service.SalesOrderService;
import org.styli.services.customer.service.impl.ConfigServiceImpl;
import org.styli.services.customer.utility.Constants;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;

@RestController
@RequestMapping("/rest/customer/")
@Api(value = "/rest/customer/", produces = "application/json")
public class CustomeromsController {

	@Autowired
	CustomerV5Service customerV5Service;

	@Autowired
	SalesOrderService salesOrderService;
	
	@Value("${customer.jwt.flag}")
    String jwtFlag;
	
	@Autowired
	CustomerV4Service customerV4Service;
	
	@Autowired
    private FirebaseAuthentication firebaseAuthentication;
	
	@Autowired
	private BigQuerySyncService bigQuerySyncService;
	
	@Autowired
	ConfigServiceImpl configServiceImpl; 

	  @ApiImplicitParams({
	      @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	  @PostMapping("oms/list")
	  // @CrossOrigin(origins = "*")
	  public CustomerOmsResponsedto customerListall(@RequestHeader Map<String, String> httpRequestHeadrs,
			  @RequestBody @Valid CustomerOmslistrequest request,HttpServletRequest req) {

		  if(Constants.orderCredentials.isFirebaseAuthEnable()) {
			  
			  firebaseAuthentication.verifyToken(req);
		      Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		      if (obj instanceof FirebaseUser) {
		    	  return customerV5Service.customerOmslist(request);
		    
		      }else {
		    	  CustomerOmsResponsedto resp = new CustomerOmsResponsedto();
		          resp.setStatus(false);
		          resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
		          return resp;
	
		      }
		  } else {
			  return customerV5Service.customerOmslist(request);
		  }

	  }

	@PostMapping("oms/delete/customer/login/history")
	public CustomerOMSDeleteLoginHistoryResponse customerDeleteLoginHistory(@RequestHeader Map<String, String> httpRequestHeadrs,
												  @RequestBody @Valid CustomerOMSDeleteLoginHistoryRequest request, HttpServletRequest req) {

		if(Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return customerV5Service.customerOmsDeleteLogin(request);

			}else {
				CustomerOMSDeleteLoginHistoryResponse resp = new CustomerOMSDeleteLoginHistoryResponse();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;

			}
		} else {
			return customerV5Service.customerOmsDeleteLogin(request);
		}

	}
	  
	  /**
	 * @param httpRequestHeadrs
	 * @param request
	 * @return
	 */
	@ApiImplicitParams({
	      @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	  @PostMapping("oms/details")
	  public CustomerUpdateProfileResponse customerDetails(
			  @RequestHeader Map<String, String> httpRequestHeadrs,
			  @RequestBody @Valid CustomerDetailsRequest request,
			  HttpServletRequest req,
			  @RequestHeader(value = "authorization-token", required = false) String authorizationToken
			  ) {

		if(Constants.orderCredentials.isFirebaseAuthEnable()) {
			
			  firebaseAuthentication.verifyToken(req);
		      Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		      if (obj instanceof FirebaseUser) {
		    	  
		    	  return customerV5Service.customerDetails(request,httpRequestHeadrs);
		    
		      }else {
	    	  
				if(StringUtils.isNotEmpty(authorizationToken) ) {
					if (configServiceImpl.checkAuthorizationInternal(authorizationToken)) {
						return customerV5Service.customerDetails(request,httpRequestHeadrs);
					}
				}
				CustomerUpdateProfileResponse resp = new CustomerUpdateProfileResponse();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			    	  
		    }
		} else {
			return customerV5Service.customerDetails(request,httpRequestHeadrs);
		}

	  }
	  
	  /**
		 *
		 * @param addressId
		 * @param customerId
		 * @return CustomerAddreesResponse
		 */
		@PostMapping("oms/address/details")
		public CustomerAddreesResponse getAddressById(@RequestHeader Map<String, String> requestHeader,
				@RequestBody @Valid CustomerDetailsRequest requeest, HttpServletRequest req) {
			CustomerAddreesResponse resp = null;

			if(Constants.orderCredentials.isFirebaseAuthEnable()) {
				
				firebaseAuthentication.verifyToken(req);
				Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
				if (obj instanceof FirebaseUser) {
					Integer addressId = requeest.getAddressId();
					Integer customerId = requeest.getCustomerId();
					resp = customerV4Service.getAddressById(addressId, customerId);
	
					return resp;
				} else {
	
					resp = new CustomerAddreesResponse();
					resp.setStatus(false);
					resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
					return resp;
				}
			} else {
				Integer addressId = requeest.getAddressId();
				Integer customerId = requeest.getCustomerId();
				resp = customerV4Service.getAddressById(addressId, customerId);

				return resp;
			}

		}
		
		/**
		 *
		 * 
		 * @param customerId
		 * @return CustomerAddreesResponse
		 */
		@PostMapping("oms/address/list")
		public CustomerAddreesResponse getAddressList(@RequestHeader Map<String, String> requestHeader,
				@RequestBody @Valid CustomerDetailsRequest requeest,HttpServletRequest req) {

			if(Constants.orderCredentials.isFirebaseAuthEnable()) {
				
				firebaseAuthentication.verifyToken(req);
				Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
				if (obj instanceof FirebaseUser) {
			
					return customerV4Service.getAddress(requeest.getCustomerId(),requestHeader);
				}else {
	
					CustomerAddreesResponse resp = new CustomerAddreesResponse();
					resp.setStatus(false);
					resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
					return resp;
				}
			} else {
				return customerV4Service.getAddress(requeest.getCustomerId(),requestHeader);
			}

		}
		
		/**
		 *
		 * 
		 * @param customerId
		 * @return CustomerAddreesResponse
		 */
		@PutMapping("oms/update")
		public CustomerUpdateProfileResponse omsUpdateCustomerProfile(@RequestHeader Map<String, String> requestHeader,
				@Valid @RequestBody CustomerUpdateProfileRequest updateProfileReq , HttpServletRequest req) {

			CustomerUpdateProfileResponse response = null;
			
			if(Constants.orderCredentials.isFirebaseAuthEnable()) {
				
				firebaseAuthentication.verifyToken(req);
				Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
				if (obj instanceof FirebaseUser) {
					response = customerV4Service.updateCustomer(updateProfileReq, requestHeader);
				}else {
	
					CustomerUpdateProfileResponse resp = new CustomerUpdateProfileResponse();
					resp.setStatus(false);
					resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
					return resp;
				}
			} else {
				response = customerV4Service.updateCustomer(updateProfileReq, requestHeader);
			}

			return response;
		}
		
		
		/**
		 * @param requestHeader
		 * @param customerAddRequest
		 * @return update add response
		 */
		@PutMapping("oms/address/update")
		public CustomerAddreesResponse updateAddress(@RequestHeader Map<String, String> requestHeader,
				@Valid @RequestBody CustomerAddrees customerAddRequest, HttpServletRequest req) {

			CustomerAddreesResponse response = null;

			if(Constants.orderCredentials.isFirebaseAuthEnable()) {
				
				firebaseAuthentication.verifyToken(req);
				Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
				if (obj instanceof FirebaseUser) {
					response = customerV4Service.saveAddress(customerAddRequest, false, requestHeader);
				} else {
	
					CustomerAddreesResponse resp = new CustomerAddreesResponse();
					resp.setStatus(false);
					resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
					return resp;
				}
			} else {
				response = customerV4Service.saveAddress(customerAddRequest, false, requestHeader);
			}

			return response;
		}
		
		
		/**
		 * @param requestHeader
		 * @param customerAddRequest
		 * @return update add response
		 * @throws CustomerException 
		 */
		@PostMapping("oms/registration/incrementid")
		public String getIncrementId(@RequestHeader Map<String, String> requestHeader,
				@RequestHeader(value = "authorization-token", required = false) String authorizationToken)
				throws CustomerException {

			String incrementId = null;
			Integer incrementIdInt = null;
			if (configServiceImpl.checkAuthorizationInternal(authorizationToken)) {
				incrementIdInt = customerV4Service.getRegistrationIncrementId();
			}
			if (null != incrementIdInt) {
				incrementId = incrementIdInt.toString();
			}
			return incrementId;
		}
		
		@PostMapping("oms/referrals")
		public CustomerOmsResponsedto findReferralCustomers(@RequestBody List<Integer> customerIds){
				return customerV4Service.findReferralCustomers(customerIds);
		}
		
		@GetMapping("oms/find/{customerId}")
		public ResponseEntity<CustomerProfileResponse> findCustomerByEntityId(@PathVariable String customerId,
				@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
			if (configServiceImpl.checkAuthorizationInternal(authorizationToken)) {
				CustomerProfileResponse customer = customerV4Service.findCustomerById(customerId);
				return ResponseEntity.status(HttpStatus.OK).body(customer);
			} else {
				CustomerProfileResponse response = new CustomerProfileResponse();
				response.setStatus(false);
				response.setUserMessage("You're not authenticated to make this request.");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
			}

		}
		@PostMapping("oms/sync/bigquery")
		public ResponseEntity<BigQuerySyncRequest> syncCustomersToBigQuery(
				@RequestHeader(value = "authorization-token", required = false) String authorizationToken,
				@RequestBody BigQuerySyncRequest request) {
			if (configServiceImpl.checkAuthorizationInternal(authorizationToken)) {
				if (request.isCompleteSync()) {
					bigQuerySyncService.completeSync();
				} else {
					bigQuerySyncService.incrementalSync();
				}
				request.setStatus(true);
				request.setMessage("Sync completed.");
				return ResponseEntity.ok().body(request);
			} else {
				request.setStatus(false);
				request.setMessage("You're not authenticated to perform the operation.");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(request);
			}
		}

		@PostMapping("internal/wishlist/view")
		public CustomerWishlistResponse getWishlistForCustomer(@RequestBody @Valid CustomerWishlistV5Request request,
				@RequestHeader(value = "authorization-token", required = true) String authorizationToken) {
			if (configServiceImpl.checkAuthorizationInternal(authorizationToken)) {
				return customerV5Service.getWishList(request);
			}
			else {
				CustomerWishlistResponse resp = new CustomerWishlistResponse();
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				resp.setStatusMsg("You're not authenticated to perform the operation.");
				return resp;
			}
		}
}

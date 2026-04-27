package org.styli.services.order.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.autoRefund.AutoRefundDTO;
import org.styli.services.order.pojo.autoRefund.RefundResponseDTO;
import org.styli.services.order.service.AutoRefundService;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@RestController
@RequestMapping("/rest/order/refund")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoRefundController {

	@Autowired
	private AutoRefundService autoRefundService;
	
	@Autowired
	ConfigService configService;

	@PostMapping("/list")
	public RefundResponseDTO refundOrderList(@RequestBody AutoRefundDTO autoRefundDTO) {
		return autoRefundService.rtoOrders(autoRefundDTO);
	}

	@PostMapping("/bulkrefund")
	public RefundPaymentRespone initiateRefund(@RequestBody AutoRefundDTO autoRefundDTO,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		RefundPaymentRespone response;
		autoRefundService.updateStatusToInitiateRefund(autoRefundDTO.getIncrementIds());
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				
				response = new RefundPaymentRespone();
				response.setStatusMsg(HttpStatus.UNAUTHORIZED.toString());
				return response;
			}
			response = autoRefundService.updateStatusToInitiateRefund(autoRefundDTO.getIncrementIds());
			autoRefundService.intiateBulkRefund(autoRefundDTO.getIncrementIds());
		} else {
			response = autoRefundService.updateStatusToInitiateRefund(autoRefundDTO.getIncrementIds());
			autoRefundService.intiateBulkRefund(autoRefundDTO.getIncrementIds());
		}
		return response;
	}
}

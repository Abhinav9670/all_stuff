package org.styli.services.order.controller;

import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.pojo.eas.EASRTOResponse;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.utility.Constants;

import io.swagger.annotations.Api;

@RestController

@RequestMapping("/rest/order/")
@Api(value = "/rest/order/", produces = "application/json")
public class EasController {

	private static final Log LOGGER = LogFactory.getLog(EasController.class);

	@Autowired
	EASServiceImpl eASServiceImpl;

	@Autowired
	ConfigService configService;

	@GetMapping("/eas/processRTOOrders")
	public EASRTOResponse processRTOOrders(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		LOGGER.info("EAS processRTOOrders EndPoints");
		EASRTOResponse eASRTOResponse = new EASRTOResponse();
		if (Objects.nonNull(Constants.disabledServices)
				&& Constants.disabledServices.isEarnDisabled()) {
			LOGGER.info(Constants.EARN_SERVICE_OFF);
			eASRTOResponse.setStatusCode("202");
			eASRTOResponse.setStatus(false);
			eASRTOResponse.setStatusMsg(Constants.EARN_SERVICE_OFF);
			return eASRTOResponse;
		}
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				LOGGER.info("EAS processRTOOrders start");
				eASServiceImpl.processRTOOrders();
				eASRTOResponse.setStatus(true);
				eASRTOResponse.setStatusCode(HttpStatus.OK.toString());
				return eASRTOResponse;
			} else {
				LOGGER.info("EAS processRTOOrders authorizationToken Fails");
				eASRTOResponse.setStatus(false);
				eASRTOResponse.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return eASRTOResponse;
			}
		} else {
			LOGGER.info("EAS processRTOOrders start");
			eASServiceImpl.processRTOOrders();
			eASRTOResponse.setStatus(true);
			eASRTOResponse.setStatusCode(HttpStatus.OK.toString());
			return eASRTOResponse;
		}

	}
}

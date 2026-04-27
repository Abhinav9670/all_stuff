package org.styli.services.customer.logging.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LoggingServiceImplTest extends AbstractTestNGSpringContextTests {

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@InjectMocks
	private LoggingServiceImpl loggingService;

	@BeforeMethod
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testLogRequest() {
		MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
		httpServletRequest.setMethod("POST");
		httpServletRequest.setRequestURI("/test");
		httpServletRequest.addHeader("Content-Type", "application/json");
		httpServletRequest.setParameter("param1", "value1");

		loggingService.logRequest(httpServletRequest, "requestBody");

		// verify that LOGGER.info was called with the expected argument
	}

	@Test
	public void testLogResponse() {
		MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
		httpServletRequest.setMethod("POST");
		httpServletRequest.setRequestURI("/test");
		httpServletRequest.addHeader("Content-Type", "application/json");

		MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
		httpServletResponse.addHeader("header1", "value1");

		loggingService.logResponse(httpServletRequest, httpServletResponse, "responseBody");

		// verify that LOGGER.info was called with the expected argument
	}
}

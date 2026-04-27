package org.styli.services.customer.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.pojo.consul.Message;
import org.styli.services.customer.utility.consul.FromEmail;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

public class EmailHelperTest extends AbstractTestNGSpringContextTests {

	@Value("${sendgrid.key}")
	private String sendgridKey;

	private static final List<Integer> SUCCESS_CODES = Arrays.asList(200, 202);

	private static final String VALID_EMAIL_ADDRESS_REGEX = "[a-zA-Z0-9._-][a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}";
	@InjectMocks
	private EmailHelper emailHelper;
	@Mock
	private SendGrid sendGrid;
	private ObjectMapper mapper;

	@InjectMocks
	private ServiceConfigs serviceConfigs;

	@BeforeMethod
	public void setup() {
		MockitoAnnotations.initMocks(this);
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		Message msg = new Message();
		msg.setEn("default message");
		FromEmail dm = new FromEmail();
		dm.setMail("test mail");

		LinkedHashMap<String, String> liststr = new LinkedHashMap();
		liststr.put("en", "en name");
		liststr.put("ar", "ar name");
		dm.setName(liststr);

		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("fromEmail", dm);

		ReflectionTestUtils.setField(serviceConfigs, "consulServiceMap", newConfigs);
	}

	@Test
	public void testSendEmail() throws Exception {
		// given
		String to = "test@example.com";
		String toName = "Test User";
		String content = "Test Content";
		String contentType = "text/plain";
		String subject = "Test Subject";
		String langCode = "en";

		Email fromEmail = new Email("testfrom@example.com", "From User");
		Email toEmail = new Email(to, toName);
		Content contentObj = new Content(contentType, content);
		Mail mail = new Mail(fromEmail, subject, toEmail, contentObj);
		Request request = new Request();
		request.setMethod(Method.POST);
		request.setEndpoint("mail/send");
		request.setBody(mail.build());
		Response response = new Response();
		response.setStatusCode(200);

		// when(serviceConfigs.getFromEmail()).thenReturn(new
		// FromEmail("testfrom@example.com", "From User"));

		when(sendGrid.api(any())).thenReturn(response);
		// when(response.getStatusCode()).thenReturn(200);

		// when
		boolean result = emailHelper.sendEmail(to, toName, content, contentType, subject, langCode);

		// then
		verify(sendGrid).api(any(Request.class));
		Assert.assertTrue(result);
	}

	@Test
	public void testValidateEmail() throws Exception {
		// given
		String validEmail = "test@example.com";
		String invalidEmail = "invalid_email";
		String emptyEmail = "";

		// when
		boolean validResult = EmailHelper.validateEmail(validEmail);
		boolean invalidResult = EmailHelper.validateEmail(invalidEmail);
		boolean emptyResult = EmailHelper.validateEmail(emptyEmail);

		// then
		Assert.assertTrue(validResult);
		Assert.assertFalse(invalidResult);
		Assert.assertFalse(invalidResult);

	}
}
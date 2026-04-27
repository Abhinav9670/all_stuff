package org.styli.services.customer.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.styli.services.customer.helper.EmailHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.pojo.MagicLinkRequest;
import org.styli.services.customer.pojo.MagicLinkResponse;
import org.styli.services.customer.pojo.MagiclinkValidationRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.utility.TokenUtility;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class MagicLinkServiceImplTest {

    @Mock
    private EmailHelper emailHelper;

    @Mock
    private TokenUtility tokenUtility;

    @Mock
    private CustomerEntityRepository customerEntityRepository;

    @InjectMocks
    @Spy
    private MagicLinkServiceImpl magicLinkService;

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private RedisHelper redisHelper;

    @Mock
    private CustomerV4Service customerV4Service;


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateAndSendMagicLink() {
        MagicLinkRequest request = new MagicLinkRequest();
        request.setEmail("test@example.com");
        request.setType("validate");
        request.setStore("store");
        request.setLangCode("en");
        request.setRedirectUrl("https://qa.stylifashion.com/sa/en/account");

        CustomerEntity customerEntity = new CustomerEntity();
        customerEntity.setEmail("test@example.com");
        customerEntity.setIsEmailVerified(false);

        String magicLinkBaseUrl = "https://qa-api.stylifashion.com/rest/customer/magiclink";
        String mailSubject = "Verify your email";
        String mailContent = "Please click on the below link to verify your email.";


        when(customerEntityRepository.findByEmail("test@example.com")).thenReturn(customerEntity);
        when(tokenUtility.createToken("test@example.com")).thenReturn("token");

        when(emailHelper.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        MagicLinkResponse response = magicLinkService.createAndSendMagicLink(request, magicLinkBaseUrl, mailSubject, mailContent);

        assertTrue(response.isStatus());
        assertEquals(200, response.getStatusCode());
        assertEquals("Verification mail has been sent to your email.", response.getStatusMessage());
    }

    @Test
    void testValidateMagicLink() {
        MagiclinkValidationRequest request = new MagiclinkValidationRequest();
        request.setToken("token");
        request.setType("login");

        JwtUser jwtUser = new JwtUser();
        jwtUser.setUserId("test@example.com");
        jwtUser.setExpiry(Date.from(Instant.now().plusSeconds(3600)));

        when(jwtValidator.validate("token")).thenReturn(jwtUser);

        CustomerEntity customerEntity = new CustomerEntity();
        customerEntity.setEmail("test@example.com");
        customerEntity.setIsEmailVerified(true);

        when(customerEntityRepository.findByEmail("test@example.com")).thenReturn(customerEntity);

        CustomerLoginV4Response loginResponse = new CustomerLoginV4Response();
        loginResponse.setStatus(true);
        loginResponse.setStatusCode("200");
        loginResponse.setStatusMsg("Success");

        doReturn(loginResponse).when(magicLinkService).doLoginTaskEmail(anyString(), anyBoolean(), anyMap());

        MagicLinkResponse response = magicLinkService.validateMagicLink(request, Map.of());

        assertTrue(response.isStatus());
        assertEquals(200, response.getStatusCode());
        assertEquals("Success", response.getStatusMessage());
    }
}
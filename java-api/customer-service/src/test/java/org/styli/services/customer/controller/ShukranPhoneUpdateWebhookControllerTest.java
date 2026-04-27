package org.styli.services.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.webhook.ShukranWebhookRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookResponse;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.CustomerV4Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
public class ShukranPhoneUpdateWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerV4Service customerV4Service;

    @MockBean
    private Client client;

    @Autowired
    private ObjectMapper objectMapper;

    private ShukranWebhookRequest request;
    private ShukranWebhookResponse response;

    @BeforeEach
    void setUp() {
        request = ShukranWebhookRequest.builder()
                .mobileNumber("+96890901909")
                .action("update")
                .loyaltyCardNumber(1200000522414150L)
                .cardNo(1200000522414150L)
                .phone("+96890901909")
                .build();
    }

    @Test
    public void testHandleShukranPhoneUpdateWebhook_Success() throws Exception {
        // Given
        response = ShukranWebhookResponse.builder()
                .success(true)
                .message("Successfully updated phone number")
                .customerId(123)
                .cardNumber("1200000522414150L")
                .oldPhoneNumber("+96890901908")
                .newPhoneNumber("+96890901909")
                .updatedAt("2024-01-01T10:00:00")
                .build();

        when(customerV4Service.handleShukranPhoneUpdateWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully updated phone number"))
                .andExpect(jsonPath("$.customerId").value(123))
                .andExpect(jsonPath("$.loyaltyCardNumber").value("1200000522414150L"))
                .andExpect(jsonPath("$.oldPhoneNumber").value("+96890901908"))
                .andExpect(jsonPath("$.newPhoneNumber").value("+96890901909"));
    }

    @Test
    public void testHandleShukranPhoneUpdateWebhook_InvalidAction() throws Exception {
        // Given
        request.setAction("invalid");
        response = ShukranWebhookResponse.builder()
                .success(false)
                .message("Invalid action. Expected 'update'")
                .cardNumber("1200000522414150L")
                .errorCode("400")
                .errorMessage("Invalid action")
                .build();

        when(customerV4Service.handleShukranPhoneUpdateWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid action. Expected 'update'"))
                .andExpect(jsonPath("$.errorCode").value("400"));
    }

    @Test
    public void testHandleShukranPhoneUpdateWebhook_CustomerNotFound() throws Exception {
        // Given
        response = ShukranWebhookResponse.builder()
                .success(false)
                .message("No customer found for the provided card number")
                .cardNumber("1200000522414150L")
                .errorCode("404")
                .errorMessage("Customer not found")
                .build();

        when(customerV4Service.handleShukranPhoneUpdateWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No customer found for the provided card number"))
                .andExpect(jsonPath("$.errorCode").value("404"));
    }

    @Test
    public void testHandleShukranPhoneUpdateWebhook_NoPhoneNumber() throws Exception {
        // Given
        request.setMobileNumber(null);
        request.setPhone(null);
        request.setCardNo(null);
        response = ShukranWebhookResponse.builder()
                .success(false)
                .message("No phone number provided")
                .cardNumber(null)
                .errorCode("400")
                .errorMessage("Phone number or card number is required")
                .build();

        when(customerV4Service.handleShukranPhoneUpdateWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No phone number provided"))
                .andExpect(jsonPath("$.errorCode").value("400"));
    }

    @Test
    public void testHandleShukranPhoneUpdateWebhook_ServerError() throws Exception {
        // Given
        response = ShukranWebhookResponse.builder()
                .success(false)
                .message("Error processing webhook request")
                .cardNumber("1200000522414150L")
                .errorCode("500")
                .errorMessage("Internal server error: Database connection failed")
                .build();

        when(customerV4Service.handleShukranPhoneUpdateWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error processing webhook request"))
                .andExpect(jsonPath("$.errorCode").value("500"));
    }
}
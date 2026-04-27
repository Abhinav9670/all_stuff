package org.styli.services.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.styli.services.customer.pojo.webhook.ShukranWebhookRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookResponse;
import org.styli.services.customer.service.CustomerV4Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
public class ShukranWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerV4Service customerV4Service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testHandleShukranPhoneUnlinkWebhook_Success() throws Exception {
        // Given
        ShukranWebhookRequest request = ShukranWebhookRequest.builder()
                .mobileNumber("+966411055731")
                .action("remove")
                .phone("+966411055731")
                .build();

        ShukranWebhookResponse response = ShukranWebhookResponse.builder()
                .success(true)
                .message("Successfully removed phone number from Shukran account and set isMobileNumberRemoved flag")
                .customerId(123)
                .phoneNumber("+966411055731")
                .unlinkedAt("2024-01-01T10:00:00")
                .build();

        when(customerV4Service.handleShukranPhoneUnlinkWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-unlink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully removed phone number from Shukran account and set isMobileNumberRemoved flag"))
                .andExpect(jsonPath("$.customerId").value(123))
                .andExpect(jsonPath("$.phoneNumber").value("+966411055731"));
    }

    @Test
    public void testHandleShukranPhoneUnlinkWebhook_InvalidAction() throws Exception {
        // Given
        ShukranWebhookRequest request = ShukranWebhookRequest.builder()
                .mobileNumber("+966411055731")
                .action("invalid")
                .phone("+966411055731")
                .build();

        ShukranWebhookResponse response = ShukranWebhookResponse.builder()
                .success(false)
                .message("Invalid action. Expected 'remove'")
                .phoneNumber("+966411055731")
                .errorCode("400")
                .errorMessage("Invalid action")
                .build();

        when(customerV4Service.handleShukranPhoneUnlinkWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-unlink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid action. Expected 'remove'"))
                .andExpect(jsonPath("$.errorCode").value("400"))
                .andExpect(jsonPath("$.phoneNumber").value("+966411055731"));
    }

    @Test
    public void testHandleShukranPhoneUnlinkWebhook_NoPhoneNumber() throws Exception {
        // Given
        ShukranWebhookRequest request = ShukranWebhookRequest.builder()
                .mobileNumber(null)
                .action("remove")
                .phone(null)
                .build();

        ShukranWebhookResponse response = ShukranWebhookResponse.builder()
                .success(false)
                .message("No phone number provided")
                .errorCode("400")
                .errorMessage("Phone number is required")
                .build();

        when(customerV4Service.handleShukranPhoneUnlinkWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-unlink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No phone number provided"))
                .andExpect(jsonPath("$.errorCode").value("400"));
    }

    @Test
    public void testHandleShukranPhoneUnlinkWebhook_ServerError() throws Exception {
        // Given
        ShukranWebhookRequest request = ShukranWebhookRequest.builder()
                .mobileNumber("+966411055731")
                .action("remove")
                .phone("+966411055731")
                .build();

        ShukranWebhookResponse response = ShukranWebhookResponse.builder()
                .success(false)
                .message("Error processing webhook request")
                .phoneNumber("+966411055731")
                .errorCode("500")
                .errorMessage("Internal server error: Database connection failed")
                .build();

        when(customerV4Service.handleShukranPhoneUnlinkWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-unlink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error processing webhook request"))
                .andExpect(jsonPath("$.errorCode").value("500"));
    }

    @Test
    public void testHandleShukranPhoneUnlinkWebhook_CustomerNotFound() throws Exception {
        // Given
        ShukranWebhookRequest request = ShukranWebhookRequest.builder()
                .mobileNumber("+966999999999")
                .action("remove")
                .phone("+966999999999")
                .build();

        ShukranWebhookResponse response = ShukranWebhookResponse.builder()
                .success(false)
                .message("No customer found for the provided phone number")
                .phoneNumber("+966999999999")
                .errorCode("404")
                .errorMessage("Customer not found")
                .build();

        when(customerV4Service.handleShukranPhoneUnlinkWebhook(any(ShukranWebhookRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/rest/customer/webhook/shukran/phone-unlink")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No customer found for the provided phone number"))
                .andExpect(jsonPath("$.errorCode").value("404"))
                .andExpect(jsonPath("$.phoneNumber").value("+966999999999"));
    }
}
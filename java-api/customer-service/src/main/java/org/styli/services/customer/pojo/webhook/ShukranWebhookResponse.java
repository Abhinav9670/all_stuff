package org.styli.services.customer.pojo.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified response model for Shukran webhooks
 * Supports both phone unlink and phone update operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShukranWebhookResponse {
    private Boolean success;
    private String message;
    
    @JsonProperty("customer_id")
    private Integer customerId;
    
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    // Fields specific to unlink operations
    @JsonProperty("unlinked_at")
    private String unlinkedAt;
    
    // Fields specific to update operations
    @JsonProperty("card_number")
    private String cardNumber;
    
    @JsonProperty("old_phone_number")
    private String oldPhoneNumber;
    
    @JsonProperty("new_phone_number")
    private String newPhoneNumber;
    
    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("status_code")
    private String statusCode;

    @JsonProperty("status_message")
    private String statusMessage;
    
    // Error fields
    @JsonProperty("error_code")
    private String errorCode;
    
    @JsonProperty("error_message")
    private String errorMessage;

    public boolean isSuccess() {
        return this.success;
    }
}
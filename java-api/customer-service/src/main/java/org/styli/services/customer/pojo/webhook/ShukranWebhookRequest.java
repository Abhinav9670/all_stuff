package org.styli.services.customer.pojo.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Unified request model for Shukran webhooks
 * Supports both phone unlink and phone update operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShukranWebhookRequest {

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @NotNull(message = "Action should not be null")
    @NotBlank(message = "Action should not be blank")
    @JsonProperty("action")
    private String action;

    @JsonProperty("phone")
    private String phone;

    // Optional fields for phone update operations
    @JsonProperty("loyaltyCardNumber")
    private Long loyaltyCardNumber;

    @JsonProperty("card_no")
    private Long cardNo;

    /**
     * Checks if this is an unlink operation
     * @return true if action is "remove"
     */
    public boolean isUnlinkOperation() {
        return "remove".equalsIgnoreCase(action);
    }

    /**
     * Checks if this is an update operation
     * @return true if action is "update"
     */
    public boolean isUpdateOperation() {
        return "update".equalsIgnoreCase(action);
    }

    /**
     * Gets the primary phone number (mobileNumber takes precedence over phone)
     * @return the primary phone number
     */
    public String getPrimaryPhoneNumber() {
        if (mobileNumber != null && !mobileNumber.trim().isEmpty()) {
            return mobileNumber;
        }
        return phone;
    }
}
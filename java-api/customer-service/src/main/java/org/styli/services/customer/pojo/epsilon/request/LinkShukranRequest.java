package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LinkShukranRequest {
    @NotNull(message = "Customer Id should not be null or empty")
    private Integer customerId;
    @NotNull(message = "Profile Id should not be null or empty")
    private String profileId; // From Get profile API response - shukranProfileData - profileId
    @NotNull(message = "ShukranLinkFlag should not be null or empty")
    private Boolean shukranLinkFlag;
}


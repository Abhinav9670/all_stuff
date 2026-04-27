package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

import javax.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class JsonExternalData {
    private String CashierID;
    @NotBlank(message = "EnrollChannelCode should not be null or empty")
    private String EnrollmentInvoiceNumber;
    private String LandLineNumber;
    private String Nationality;
    @NotBlank(message = "EnrollChannelCode should not be null or empty")
    private String PhOTPVerified;
    private String ActivatedIn;
    private String ActivatedLanguage;
    private String ActivationDate;
    private String LMG_PDPL_Flag;
    private MemberConsent Consent;
}


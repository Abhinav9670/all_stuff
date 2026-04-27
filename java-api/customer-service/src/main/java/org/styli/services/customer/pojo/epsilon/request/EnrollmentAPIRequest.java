package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EnrollmentAPIRequest {
    @NotBlank(message = "Firstname should not be null or empty")
    private String FirstName;
    @NotBlank(message = "LastName should not be null or empty")
    private String LastName;
    private String City;
    private String Gender;
    private String BirthDate;
    private String Prefix;
    private List<MemberAddress> Addresses;
    private List<MemberEmail> Emails;
    @Valid
    private List<MemberPhone> Phones;
    @NotBlank(message = "SourceCode should not be null or empty")
    private String SourceCode;
    private String EnrollmentStoreCode;
    private String LanguageCode;
    private String JoinDate;
    private String CardNumber;
    @NotBlank(message = "EnrollChannelCode should not be null or empty")
    private String EnrollChannelCode;
    @Valid
    private JsonExternalData JsonExternalData;
}


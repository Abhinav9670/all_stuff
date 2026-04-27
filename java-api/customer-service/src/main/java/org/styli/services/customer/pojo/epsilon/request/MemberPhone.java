package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

import javax.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MemberPhone {
    @NotBlank(message = "PhoneNumber should not be null or empty")
    private String PhoneNumber;
}

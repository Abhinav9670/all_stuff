package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString

public class MemberConsent {
    private String ProvidedDate;
    private String Source;
    private String Status;
    private String Segment;
}

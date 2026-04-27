package org.styli.services.customer.pojo.epsilon.response;

import lombok.*;
import org.styli.services.customer.pojo.registration.response.ErrorType;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildUpgradeShukranTierActivityResponse {

    private boolean status;

    private String statusCode;

    private String statusMsg;

    private ErrorType error;
}
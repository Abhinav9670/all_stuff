package org.styli.services.customer.pojo;

import lombok.Data;
/**
 * @author Biswabhusan Pradhan
 * @project customer-service
 */
@Data
public class MagiclinkValidationRequest {
    String type;
    String token;
}

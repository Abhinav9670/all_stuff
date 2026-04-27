package org.styli.services.customer.pojo.nationalid;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data payload for National ID / Passport validation response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NationalIdValidationData {
    private String customerId;
    private String idNumber;
    private String expiryDate;
    private String fullName;
    private String filePath;
}

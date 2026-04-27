package org.styli.services.customer.pojo;


import lombok.Data;
import org.styli.services.customer.pojo.registration.response.Customer;

/**
 * @author Biswabhusan Pradhan
 * @project customer-service
 */
@Data
public class MagicLinkResponse {
    private String email;
    private boolean status;
    private String statusMessage;
    private Integer statusCode;
    private Customer customer;
    private String encryptedRsaToken;
    private String encryptedRsaTokenExpiry;
    private String newJwtToken;

}

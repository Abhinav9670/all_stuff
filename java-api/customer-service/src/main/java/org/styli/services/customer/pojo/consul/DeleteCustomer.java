package org.styli.services.customer.pojo.consul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 10/06/2022 - 11:11 AM
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteCustomer {

    private Email email;

    private Message otpMessage;

    private Integer ttlCustomerAccount;

    private List<String> tasks;

}

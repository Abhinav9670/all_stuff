package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class CustomerOMSDeleteLoginHistoryResponse {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
}

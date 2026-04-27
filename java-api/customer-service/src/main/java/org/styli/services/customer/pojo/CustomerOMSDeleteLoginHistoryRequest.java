package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class CustomerOMSDeleteLoginHistoryRequest {
    private Integer customerId;
    private String[] deviceIds;
}

package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class RefreshTokenRequest
{
    private Integer customerId;
    private String token;

}


package org.styli.services.order.pojo.request;

import javax.validation.constraints.NotNull;

import lombok.Data;



@Data
public class GetPromosRequest {

	@NotNull
    private Integer storeId;
    private String customerId;
    private String customerEmail;

}

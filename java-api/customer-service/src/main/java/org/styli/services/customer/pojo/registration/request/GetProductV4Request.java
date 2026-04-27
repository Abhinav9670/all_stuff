package org.styli.services.customer.pojo.registration.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 23/03/2020
 * @project product-service
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetProductV4Request {

    public String productId;


    public Integer storeId;
    
    private String sku;
}

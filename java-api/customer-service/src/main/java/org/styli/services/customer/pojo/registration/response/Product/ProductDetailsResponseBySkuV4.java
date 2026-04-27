package org.styli.services.customer.pojo.registration.response.Product;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

/**
 * @author Umesh, 29/03/2020
 * @project product-service
 */

@Data
public class ProductDetailsResponseBySkuV4 {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ProductDetailsResponseBySkuBodyV4 response;
    private ErrorType error;

}

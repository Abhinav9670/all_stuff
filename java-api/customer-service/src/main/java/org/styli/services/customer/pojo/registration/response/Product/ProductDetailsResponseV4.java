package org.styli.services.customer.pojo.registration.response.Product;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

/**
 * @author Umesh, 23/03/2020
 * @project product-service
 */

@Data
public class ProductDetailsResponseV4 {

    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ProductDetailsResponseV4DTO response;
    private ErrorType error;

}

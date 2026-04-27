package org.styli.services.customer.pojo.registration.response.Product;

import lombok.Data;

import java.util.List;

/**
 * @author Umesh, 29/03/2020
 * @project product-service
 */

@Data
public class ProductDetailsResponseBySkuBodyV4 {

    List<ProductDetailsResponseV4DTO> productDetailsList;
}

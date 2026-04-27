package org.styli.services.customer.helper;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.CatalogProductEntityDTO;
import org.styli.services.customer.pojo.PriceDetails;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;

/**
 * @author Umesh, 23/03/2020
 * @project product-service
 */

@Component
public class AlgoliaProductHelperV4 {

    private String parseNullStr(Object val) {
        return (val == null) ? null : String.valueOf(val);
    }

    public ProductDetailsResponseV4DTO convertOldToV4Response(CatalogProductEntityDTO catalogProductEntityDTO) {
        ProductDetailsResponseV4DTO productDetailsResponseV4DTO = new ProductDetailsResponseV4DTO();

        productDetailsResponseV4DTO.setSource("native");

        productDetailsResponseV4DTO.setId(parseNullStr(catalogProductEntityDTO.getId()));
        productDetailsResponseV4DTO.setSku(parseNullStr(catalogProductEntityDTO.getSku()));
        productDetailsResponseV4DTO.setName(parseNullStr(catalogProductEntityDTO.getName()));
        PriceDetails priceDetails = new PriceDetails();
        if (catalogProductEntityDTO.getPrices() != null) {
            priceDetails.setPrice(catalogProductEntityDTO.getPrices().getPrice());
            priceDetails.setSpecialPrice(catalogProductEntityDTO.getPrices().getSpecialPrice());
        }
        productDetailsResponseV4DTO.setPrices(priceDetails);
        productDetailsResponseV4DTO.setImages(catalogProductEntityDTO.getImages());
        productDetailsResponseV4DTO.setCurrency(parseNullStr(catalogProductEntityDTO.getCurrency()));
        productDetailsResponseV4DTO.setColourName(parseNullStr(catalogProductEntityDTO.getColourName()));
        productDetailsResponseV4DTO.setIsVisibleProduct(catalogProductEntityDTO.getIsVisibleProduct());
        productDetailsResponseV4DTO.setDiscount(catalogProductEntityDTO.getDiscount());
        productDetailsResponseV4DTO.setBrand(parseNullStr(catalogProductEntityDTO.getBrand()));
        productDetailsResponseV4DTO.setUrl(parseNullStr(catalogProductEntityDTO.getUrl()));
        productDetailsResponseV4DTO.setProductType(parseNullStr(catalogProductEntityDTO.getProductType()));
        List<ProductDetailsResponseV4DTO> configProducts = new ArrayList<>();

        /** null check if config value **/
        if (CollectionUtils.isNotEmpty(catalogProductEntityDTO.getConfigProducts())) {

            for (CatalogProductEntityDTO childProduct : catalogProductEntityDTO.getConfigProducts()) {
                ProductDetailsResponseV4DTO configProduct = new ProductDetailsResponseV4DTO();
                configProduct.setId(parseNullStr(childProduct.getId()));
                configProduct.setSku(parseNullStr(childProduct.getSku()));
                PriceDetails prices = new PriceDetails();
                if (childProduct.getPrices() != null) {
                    prices.setPrice(childProduct.getPrices().getPrice());
                    prices.setSpecialPrice(childProduct.getPrices().getSpecialPrice());
                }
                configProduct.setPrices(prices);
                configProduct.setSize(parseNullStr(childProduct.getSize()));
                configProduct.setQuantity(parseNullStr(childProduct.getQuantity()));
                configProduct.setDiscount(childProduct.getDiscount());
                configProducts.add(configProduct);
            }
        }

        productDetailsResponseV4DTO.setConfigProducts(configProducts);

        productDetailsResponseV4DTO.setProductFilterAttributes(catalogProductEntityDTO.getProductFilterAttributes());

        productDetailsResponseV4DTO.setReturnCategoryRestriction(catalogProductEntityDTO.getIsReturnApplicable());

        return productDetailsResponseV4DTO;
    }
}

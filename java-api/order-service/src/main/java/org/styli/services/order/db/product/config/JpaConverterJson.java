package org.styli.services.order.db.product.config;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.springframework.stereotype.Component;
import org.styli.services.order.model.ProductId;

@Converter(autoApply = true)
@Component
public class JpaConverterJson implements AttributeConverter<ProductId, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ProductId attribute) {
        return null;
    }

    @Override
    public ProductId convertToEntityAttribute(Integer dbData) {
        return null;
    }

    // @Override
    // public Integer convertToDatabaseColumn(ProductId attribute) {
    // return attribute.getProductId();
    // }
    //
    // @Override
    // public ProductId convertToEntityAttribute(Integer dbData) {
    //
    // System.out.println("cnvert!!!!!!!!!");
    // return new ProductId(dbData);
    // }

    // private final static ObjectMapper objectMapper = new ObjectMapper();
    // @Auto

    // @Override
    // public ProductId convertToEntityAttribute(Integer dbData) {
    //
    // System.out.println("converttttt:::::");
    //
    // return new ProductId(dbData);
    //
    // }
    //
    // @Override
    // public Integer convertToDatabaseColumn(ProductId attribute) {
    // return null;
    // }

}

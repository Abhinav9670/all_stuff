package org.styli.services.order.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.order.pojo.SellerAddress;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * JPA Converter for SellerAddress
 * Converts between JSON string in database and SellerAddress object
 */
@Converter
public class SellerAddressConverter implements AttributeConverter<SellerAddress, String> {
    
    private static final Log LOGGER = LogFactory.getLog(SellerAddressConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(SellerAddress attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error converting SellerAddress to JSON string", e);
            return null;
        }
    }

    @Override
    public SellerAddress convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, SellerAddress.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error converting JSON string to SellerAddress", e);
            return null;
        }
    }
}


package org.styli.services.order.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.order.pojo.SellerConfiguration;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * JPA Converter for SellerConfiguration
 * Converts between JSON string in database and SellerConfiguration object
 */
@Converter
public class SellerConfigurationConverter implements AttributeConverter<SellerConfiguration, String> {
    
    private static final Log LOGGER = LogFactory.getLog(SellerConfigurationConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(SellerConfiguration attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error converting SellerConfiguration to JSON string", e);
            return null;
        }
    }

    @Override
    public SellerConfiguration convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, SellerConfiguration.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error converting JSON string to SellerConfiguration", e);
            return null;
        }
    }
}


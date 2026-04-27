package org.styli.services.order.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.order.pojo.SellerBasicSettings;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * JPA Converter for SellerBasicSettings
 * Converts between JSON string in database and SellerBasicSettings object
 */
@Converter
public class SellerBasicSettingsConverter implements AttributeConverter<SellerBasicSettings, String> {
    
    private static final Log LOGGER = LogFactory.getLog(SellerBasicSettingsConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(SellerBasicSettings attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error converting SellerBasicSettings to JSON string", e);
            return null;
        }
    }

    @Override
    public SellerBasicSettings convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, SellerBasicSettings.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error converting JSON string to SellerBasicSettings", e);
            return null;
        }
    }
}


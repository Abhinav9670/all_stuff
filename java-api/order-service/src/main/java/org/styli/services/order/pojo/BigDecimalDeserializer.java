package org.styli.services.order.pojo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

public class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        String value = p.getText();

        // Handle "NaN" value here
        if ("NaN".equals(value)) {
            return BigDecimal.ZERO;  // or return null, depending on your needs
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid format for BigDecimal: " + value);
        }
    }
}


package org.styli.services.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.styli.services.order.service.impl.CommonServiceImpl;

import java.io.IOException;

@Service
public class RedisService {
    private static final Log LOGGER = LogFactory.getLog(RedisService.class);

    @Autowired
    private RedissonClient redissonClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Retrieve single JSON data stored directly as a key-value pair
    public <T> T getData(String key, Class<T> valueType) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
            String jsonData = bucket.get();
            if (jsonData != null) {
                return objectMapper.readValue(jsonData, valueType);
            } else {
                LOGGER.warn("No data found in Redis for key: " + key);
                return null;
            }
        }  catch (JsonProcessingException e){
            LOGGER.error("Error deserializing JSON to object", e);
            return null;
        }
    }

}

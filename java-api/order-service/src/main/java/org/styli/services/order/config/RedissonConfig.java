package org.styli.services.order.config;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.styli.services.order.service.impl.CommonServiceImpl;

@Configuration
public class RedissonConfig {
    private static final Log LOGGER = LogFactory.getLog(RedissonConfig.class);
    @Value("${global.redis.host}")
    private String redisHost;

    @Value("${global.redis.port}")
    private String redisPort;

    @Value("${global.redis.password}")
    private String redisPassword;

    @Value("${global.redis.ssl:false}")
    private boolean useSSL;

    @Bean
    public RedissonClient redissonClient() {
        if (StringUtils.isEmpty(redisHost) || StringUtils.isEmpty(redisPort)) {
            throw new IllegalArgumentException("Redis host and port must not be null or empty");
        }

        Config config = new Config();
        String redisUrl = (useSSL ? "rediss://" : "redis://") + redisHost + ":" + redisPort;
        config.useSingleServer().setAddress(redisUrl);

        if (StringUtils.isNotEmpty(redisPassword) && StringUtils.isNotBlank(redisPassword)) {
            config.useSingleServer().setPassword(redisPassword);
            LOGGER.info("Using password authentication for Redis");
        }

        LOGGER.info("Attempting to connect to Redis at {}"+ redisUrl);

        try {
            RedissonClient redissonClient = Redisson.create(config);
            LOGGER.info("Successfully connected to Redis at {}"+ redisUrl);
            return redissonClient;
        } catch (Exception e) {
            LOGGER.error("Failed to connect to Redis at {}: {}"+ redisUrl+ e.getMessage());

            throw e;
        }
    }

}
